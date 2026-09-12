package com.mantrasiddhi.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.location.GeocodingService
import com.mantrasiddhi.data.location.LocationProvider
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.domain.model.SandhyaRegion
import com.mantrasiddhi.resources.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the free-text location search in the "Change location" dialog. */
sealed interface LocationSearchState {
    data object Idle : LocationSearchState
    data object Searching : LocationSearchState
    data class Results(val places: List<GeocodingService.GeocodedPlace>) : LocationSearchState
    data object NoResults : LocationSearchState
    data object Error : LocationSearchState
}

@HiltViewModel
class SandhyaLocationViewModel @Inject constructor(
    private val preferences: SandhyaPreferences,
    private val locationProvider: LocationProvider,
    private val geocodingService: GeocodingService
) : ViewModel() {
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    fun selectRegion(region: SandhyaRegion) {
        viewModelScope.launch {
            preferences.setRegion(region)
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
        }
    }

    fun locationPermissionGranted(): Boolean = locationProvider.hasPermission()
    private companion object { const val SEARCHED_REGION_ID = "__searched__" }
}
