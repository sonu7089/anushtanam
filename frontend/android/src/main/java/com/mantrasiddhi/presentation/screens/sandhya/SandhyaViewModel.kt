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
import com.mantrasiddhi.domain.model.SandhyaCountDraft
import com.mantrasiddhi.domain.model.SandhyaMinimums
import com.mantrasiddhi.domain.repository.SandhyaCountRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val getCurrentUserUseCase: GetCurrentUserUseCase
) : ViewModel() {

    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val uiState: StateFlow<SandhyaUiState> = getCurrentUserUseCase().flatMapLatest { user ->
        if (user == null) return@flatMapLatest kotlinx.coroutines.flow.flowOf(SandhyaUiState())
        combine(preferences.settings, preferences.minimums(user.id)) { settings, minimums -> settings to minimums }
            .flatMapLatest { (settings, minimums) ->
                combine(logRepository.observeLogs(user.id), countRepository.observeCounts(user.id),
                    countRepository.observeDraft(user.id), selectedDate, clock) { logs, counts, draft, date, _ ->
                    buildState(settings, logs, counts, draft, date, minimums)
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SandhyaUiState())

    init {
        viewModelScope.launch {
            getCurrentUserUseCase().filterNotNull().collect { user ->
                preferences.setUserId(user.id)
                scheduler.scheduleAll()
                selectedDate.value = null
                logRepository.sweepMissed(user.id, SandhyaDateKey.of(LocalDate.now(preferences.current().zone())))
            }
        }
    }

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
            scheduler.scheduleAll()
        }
    }


    private suspend fun buildState(settings: SandhyaSettings, logs: List<SandhyaLog>, counts: List<SandhyaCount>, draft: SandhyaCountDraft?, selected: LocalDate?, minimums: SandhyaMinimums): SandhyaUiState {
        val zone = settings.zone()
        val today = selected ?: LocalDate.now(zone)
        val todayKey = SandhyaDateKey.of(today)
        val observedTodayPeriods = logs
            .filter { it.dateKey == todayKey && it.status == SandhyaStatus.OBSERVED }
            .map { it.period }
            .toSet()

        val times = SolarCalculator.computeLocal(
            today, settings.region.latitude, settings.region.longitude, zone
        )
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
            nextReminders = scheduler.upcoming(settings.copy(userId = getCurrentUserUseCase().value?.id ?: 0L)),
            reminderZoneId = zone.id,
            preparationEnabled = settings.preparationEnabled,
            eventEnabled = settings.eventEnabled,
            region = settings.region,
            today = items.map { item -> item.copy(
                count = counts.firstOrNull { it.dateKey == todayKey && it.period == item.period }?.quantity,
                minimumCount = minimums[item.period]) },
            date = today,
            isToday = today == LocalDate.now(zone),
            counts = counts,
            draft = draft,
            adherence = GetSandhyaAdherenceUseCase.compute(logs, LocalDate.now(zone), zone)
        )
    }

}
