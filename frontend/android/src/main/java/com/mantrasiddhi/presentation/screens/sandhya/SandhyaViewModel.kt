/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.sandhya

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.data.preferences.SandhyaSettings
import com.mantrasiddhi.data.sandhya.SandhyaScheduler
import com.mantrasiddhi.domain.model.SandhyaCount
import com.mantrasiddhi.domain.model.SandhyaMinimums
import com.mantrasiddhi.domain.repository.SandhyaCountRepository
import kotlinx.coroutines.flow.*
import com.mantrasiddhi.domain.model.SandhyaLog
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.model.SandhyaStatus
import com.mantrasiddhi.domain.repository.SandhyaLogRepository
import com.mantrasiddhi.domain.solar.SolarCalculator
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.usecase.GetSandhyaAdherenceUseCase
import com.mantrasiddhi.domain.util.SandhyaDateKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SandhyaViewModel @Inject constructor(
    private val preferences: SandhyaPreferences,
    private val scheduler: SandhyaScheduler,
    private val notifier: com.mantrasiddhi.data.sandhya.SandhyaNotifier,
    private val logRepository: SandhyaLogRepository,
    private val countRepository: SandhyaCountRepository,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val calendarTime: com.mantrasiddhi.domain.util.CalendarTime = com.mantrasiddhi.domain.util.CalendarTime()
) : ViewModel() {

    private var selectedOwner: Long? = null
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    val uiState: StateFlow<SandhyaUiState> = getCurrentUserUseCase()
        .distinctUntilChangedBy { it?.id }.flatMapLatest { user ->
        if (selectedOwner != user?.id) { selectedOwner = user?.id; selectedDate.value = null }
        if (user == null) return@flatMapLatest flowOf(SandhyaUiState())
        preferences.settings.distinctUntilChanged().flatMapLatest { saved ->
            val settings = saved.copy(userId = user.id)
            channelFlow {
                val logs = logRepository.observeLogs(user.id).distinctUntilChanged()
                    .shareIn(this, SharingStarted.WhileSubscribed(0), 1)
                val days = calendarTime.days(settings::zone).onEach { (today, _) ->
                    logRepository.sweepMissed(user.id, SandhyaDateKey.of(today))
                }.shareIn(this, SharingStarted.WhileSubscribed(0), 1)
                val base = combine(logs, countRepository.observeCounts(user.id), selectedDate,
                    days, preferences.minimums(user.id).distinctUntilChanged()) { entries, counts, date, calendar, minimums ->
                    buildState(settings, entries, counts, date, minimums, calendar.first)
                }
                val reminders = combine(logs, preferences.deliveryChanges(user.id)) { entries, _ -> entries }
                    .flatMapLatest { entries ->
                        var nextTransition = Long.MAX_VALUE
                        calendarTime.deadlines(next = { now ->
                            minOf(com.mantrasiddhi.domain.util.CalendarTime.nextMidnight(now, settings.zone()),
                                nextTransition.takeIf { it > now } ?: Long.MAX_VALUE)
                        }).map { now -> scheduler.upcoming(settings, now, entries).also { planned ->
                            // Late preparation schedules immediately. Its next display transition is
                            // the two-minute cutoff, not another synthetic now + one second.
                            nextTransition = planned.values.minOfOrNull {
                                if (it.triggerMillis == now + 1_000L) it.eventMillis - 120_000L else it.triggerMillis
                            } ?: Long.MAX_VALUE
                        } }
                    }
                combine(base, reminders, countRepository.observeDraft(user.id)) { state, upcoming, draft ->
                    state.copy(nextReminders = upcoming, draft = draft)
                }.collect { send(it) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), SandhyaUiState())

    fun selectDate(date: LocalDate?) {
        viewModelScope.launch {
            if (date == null || !date.isAfter(LocalDate.now(preferences.current().zone()))) selectedDate.value = date
        }
    }

    /** Marks today's [period] as performed, creating the row if the cue hasn't fired. */
    fun markObservedToday(period: SandhyaPeriod) {
        viewModelScope.launch {
            val userId = getCurrentUserUseCase().value?.id ?: return@launch
            val settings = preferences.current()
            val zone = settings.zone()
            val today = selectedDate.value ?: LocalDate.now(zone)
            if (today != LocalDate.now(zone)) return@launch
            val dateKey = SandhyaDateKey.of(today)
            val epochs = SolarCalculator.computeEpochMillis(
                today, settings.region.latitude, settings.region.longitude, zone
            )
            val eventMillis = when (period) {
                SandhyaPeriod.PRATAH -> epochs.sunriseUtcMillis
                SandhyaPeriod.MADHYAHNA -> epochs.solarNoonUtcMillis
                SandhyaPeriod.SAYAM -> epochs.sunsetUtcMillis
            } ?: System.currentTimeMillis()
            // Ensure a row exists (no-op if already present), then flip it to OBSERVED.
            logRepository.logNotified(userId, period, eventMillis, dateKey)
            logRepository.markObserved(userId, period, dateKey, System.currentTimeMillis())
            notifier.cancel(period)
        }
    }


    private var solarCache: Pair<Triple<LocalDate, com.mantrasiddhi.domain.model.SandhyaRegion, java.time.ZoneId>, com.mantrasiddhi.domain.solar.SolarTimes>? = null
    private var adherenceCache: Pair<Triple<List<SandhyaLog>, LocalDate, java.time.ZoneId>, com.mantrasiddhi.domain.model.SandhyaAdherence>? = null

    private fun buildState(settings: SandhyaSettings, logs: List<SandhyaLog>, counts: List<SandhyaCount>, selected: LocalDate?, minimums: SandhyaMinimums, currentDate: LocalDate): SandhyaUiState {
        com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("sandhya.base.build")
        val zone = settings.zone()
        val today = selected?.takeUnless { it.isAfter(currentDate) } ?: currentDate
        val todayKey = SandhyaDateKey.of(today)
        val observedTodayPeriods = logs
            .filter { it.dateKey == todayKey && it.status == SandhyaStatus.OBSERVED }
            .map { it.period }
            .toSet()

        val calendarKey = Triple(today, settings.region, zone)
        if (solarCache?.first != calendarKey) solarCache = calendarKey to SolarCalculator.computeLocal(
            today, settings.region.latitude, settings.region.longitude, zone)
        val times = solarCache!!.second
        val adherenceKey = Triple(logs, currentDate, zone)
        if (adherenceCache?.first != adherenceKey) adherenceCache = adherenceKey to
            GetSandhyaAdherenceUseCase.compute(logs, currentDate, zone)
        val items = listOf(
            SandhyaTodayItem(
                period = SandhyaPeriod.PRATAH,
                eventLabelRes = R.string.sandhya_event_sunrise,
                eventTime = times.sunrise,
                reminderTime = times.sunrise?.minusMinutes(com.mantrasiddhi.data.sandhya.SandhyaContract.LEAD_MINUTES),
                observedToday = observedTodayPeriods.contains(SandhyaPeriod.PRATAH)
            ),
            SandhyaTodayItem(
                period = SandhyaPeriod.MADHYAHNA,
                eventLabelRes = R.string.sandhya_event_noon,
                eventTime = times.solarNoon,
                reminderTime = times.solarNoon.minusMinutes(com.mantrasiddhi.data.sandhya.SandhyaContract.LEAD_MINUTES),
                observedToday = observedTodayPeriods.contains(SandhyaPeriod.MADHYAHNA)
            ),
            SandhyaTodayItem(
                period = SandhyaPeriod.SAYAM,
                eventLabelRes = R.string.sandhya_event_sunset,
                eventTime = times.sunset,
                reminderTime = times.sunset?.minusMinutes(com.mantrasiddhi.data.sandhya.SandhyaContract.LEAD_MINUTES),
                observedToday = observedTodayPeriods.contains(SandhyaPeriod.SAYAM)
            )
        )

        return SandhyaUiState(
            loading = false,
            enabled = settings.enabled,
            reminderZoneId = zone.id,
            preparationEnabled = settings.preparationEnabled,
            eventEnabled = settings.eventEnabled,
            region = settings.region,
            today = items.map { item -> item.copy(
                count = counts.firstOrNull { it.dateKey == todayKey && it.period == item.period }?.quantity,
                minimumCount = minimums[item.period]) },
            date = today,
            isToday = today == currentDate,
            counts = counts,
            adherence = adherenceCache!!.second
        )
    }

}
