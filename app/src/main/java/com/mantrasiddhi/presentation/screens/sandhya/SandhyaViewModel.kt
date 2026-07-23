/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.sandhya

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.R
import com.mantrasiddhi.data.location.GeocodingService
import com.mantrasiddhi.data.location.LocationProvider
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.data.preferences.SandhyaSettings
import com.mantrasiddhi.data.sandhya.SandhyaScheduler
import com.mantrasiddhi.domain.model.SandhyaLog
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.model.SandhyaRegion
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

/** State of the free-text location search in the "Change location" dialog. */
sealed interface LocationSearchState {
    data object Idle : LocationSearchState
    data object Searching : LocationSearchState
    data class Results(val places: List<GeocodingService.GeocodedPlace>) : LocationSearchState
    data object NoResults : LocationSearchState
    data object Error : LocationSearchState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SandhyaViewModel @Inject constructor(
    private val preferences: SandhyaPreferences,
    private val scheduler: SandhyaScheduler,
    private val logRepository: SandhyaLogRepository,
    private val locationProvider: LocationProvider,
    private val geocodingService: GeocodingService,
    getCurrentUserUseCase: GetCurrentUserUseCase
) : ViewModel() {

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    /** Transient string-resource messages (e.g. location unavailable). */
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    val uiState: StateFlow<SandhyaUiState> = preferences.settings
        .flatMapLatest { settings ->
            logRepository.observeLogs(settings.userId).map { logs -> buildState(settings, logs) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SandhyaUiState())

    init {
        // Attribute Sandhya logs to the active profile, then clean up stale reminders.
        viewModelScope.launch {
            val user = getCurrentUserUseCase().value
                ?: getCurrentUserUseCase().filterNotNull().first()
            preferences.setUserId(user.id)
            val todayKey = SandhyaDateKey.of(LocalDate.now(preferences.current().zone()))
            logRepository.sweepMissed(user.id, todayKey)
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEnabled(enabled)
            scheduler.scheduleAll()
        }
    }

    fun selectRegion(region: SandhyaRegion) {
        viewModelScope.launch {
            preferences.setRegion(region)
            scheduler.scheduleAll()
        }
    }

    private val _searchState = MutableStateFlow<LocationSearchState>(LocationSearchState.Idle)

    /** Free-text location search results for the "Change location" dialog. */
    val searchState: StateFlow<LocationSearchState> = _searchState.asStateFlow()

    /** Geocodes [query] into candidate places for the search dialog. */
    fun searchLocations(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _searchState.value = LocationSearchState.Searching
            runCatching { geocodingService.search(query) }
                .onSuccess { places ->
                    _searchState.value = if (places.isEmpty()) {
                        LocationSearchState.NoResults
                    } else {
                        LocationSearchState.Results(places)
                    }
                }
                .onFailure {
                    _searchState.value = LocationSearchState.Error
                }
        }
    }

    /** Resets the search dialog state (on dismiss/reopen). */
    fun clearLocationSearch() {
        _searchState.value = LocationSearchState.Idle
    }

    /**
     * Applies a searched place as the Sandhya region. Stored self-contained
     * (name + lat/lon); a blank zoneId means the device timezone is used, the same
     * convention as the GPS path.
     */
    fun selectSearchedPlace(place: GeocodingService.GeocodedPlace) {
        selectRegion(
            SandhyaRegion(
                id = SEARCHED_REGION_ID,
                displayName = place.displayName,
                latitude = place.latitude,
                longitude = place.longitude,
                zoneId = ""
            )
        )
        clearLocationSearch()
    }

    fun useMyLocation() {
        viewModelScope.launch {
            val latLon = locationProvider.currentLatLon()
            if (latLon == null) {
                _messages.tryEmit(R.string.sandhya_location_denied)
                return@launch
            }
            preferences.setRegion(
                SandhyaRegion(
                    id = SandhyaRegion.GPS_ID,
                    displayName = "", // shown via sandhya_region_current
                    latitude = latLon.first,
                    longitude = latLon.second,
                    zoneId = "" // device timezone
                )
            )
            scheduler.scheduleAll()
        }
    }

    /** Marks today's [period] as performed, creating the row if the cue hasn't fired. */
    fun markObservedToday(period: SandhyaPeriod) {
        viewModelScope.launch {
            val settings = preferences.current()
            val zone = settings.zone()
            val today = LocalDate.now(zone)
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
            logRepository.logNotified(settings.userId, period, eventMillis, dateKey)
            logRepository.markObserved(settings.userId, period, dateKey, System.currentTimeMillis())
        }
    }

    fun locationPermissionGranted(): Boolean = locationProvider.hasPermission()

    private fun buildState(settings: SandhyaSettings, logs: List<SandhyaLog>): SandhyaUiState {
        val zone = settings.zone()
        val today = LocalDate.now(zone)
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
                reminderTime = times.sunrise?.minusMinutes(GetSandhyaAdherenceLead),
                observedToday = observedTodayPeriods.contains(SandhyaPeriod.PRATAH)
            ),
            SandhyaTodayItem(
                period = SandhyaPeriod.MADHYAHNA,
                eventLabelRes = R.string.sandhya_event_noon,
                eventTime = times.solarNoon,
                reminderTime = times.solarNoon.minusMinutes(GetSandhyaAdherenceLead),
                observedToday = observedTodayPeriods.contains(SandhyaPeriod.MADHYAHNA)
            ),
            SandhyaTodayItem(
                period = SandhyaPeriod.SAYAM,
                eventLabelRes = R.string.sandhya_event_sunset,
                eventTime = times.sunset,
                reminderTime = times.sunset?.minusMinutes(GetSandhyaAdherenceLead),
                observedToday = observedTodayPeriods.contains(SandhyaPeriod.SAYAM)
            )
        )

        return SandhyaUiState(
            loading = false,
            enabled = settings.enabled,
            region = settings.region,
            today = items,
            adherence = GetSandhyaAdherenceUseCase.compute(logs, today, zone)
        )
    }

    private companion object {
        const val GetSandhyaAdherenceLead = 5L

        /** Region id used for a place chosen via free-text search. */
        const val SEARCHED_REGION_ID = "__searched__"
    }
}
