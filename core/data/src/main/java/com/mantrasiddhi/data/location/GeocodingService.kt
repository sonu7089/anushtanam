/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Free-text place search backed by the platform [Geocoder] (no API key; needs
 * network + Play-services geocoding). Used by Sandhya Vandan so the user can type
 * any town/place instead of picking from a fixed city list.
 */
@Singleton
class GeocodingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** One geocoded search hit. */
    data class GeocodedPlace(
        val displayName: String,
        val latitude: Double,
        val longitude: Double
    )

    /** True when the device has a geocoder backend at all (some AOSP builds don't). */
    fun isAvailable(): Boolean = Geocoder.isPresent()

    /**
     * Resolves [query] to up to [maxResults] places, best matches first. Returns an
     * empty list on no matches; throws on backend failure (typically no network) so
     * the caller can distinguish "nothing found" from "search failed".
     */
    suspend fun search(query: String, maxResults: Int = 6): List<GeocodedPlace> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || !isAvailable()) return emptyList()

        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = suspendCancellableCoroutine<List<Address>> { cont ->
            geocoder.getFromLocationName(trimmed, maxResults, object : Geocoder.GeocodeListener {
                override fun onGeocode(results: MutableList<Address>) {
                    if (cont.isActive) cont.resume(results)
                }

                override fun onError(errorMessage: String?) {
                    if (cont.isActive) {
                        cont.resumeWith(Result.failure(RuntimeException(errorMessage ?: "Geocoding failed")))
                    }
                }
            })
        }

        return addresses.mapNotNull { it.toPlace() }.distinctBy { it.displayName }
    }

    /** Builds a friendly "Locality, State, Country" label; null when unnameable. */
    private fun Address.toPlace(): GeocodedPlace? {
        val name = listOfNotNull(
            locality ?: subAdminArea ?: featureName,
            adminArea,
            countryName
        ).distinct().joinToString(", ")
        if (name.isBlank()) return null
        return GeocodedPlace(displayName = name, latitude = latitude, longitude = longitude)
    }
}
