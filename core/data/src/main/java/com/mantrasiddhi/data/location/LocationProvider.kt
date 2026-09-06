/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Best-effort, privacy-light coarse location for the optional "Use my location"
 * path. Requests a single current fix (no continuous updates), falling back to the
 * last known location. Returns null if permission is missing or no fix is available.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val locationManager: LocationManager? =
        ContextCompat.getSystemService(context, LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** Latitude/longitude as a pair, or null if unavailable. */
    suspend fun currentLatLon(): Pair<Double, Double>? {
        if (!hasPermission()) return null
        val manager = locationManager ?: return null

        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }

        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) {
            requestCurrent(manager, provider)
        }
        val location = fresh ?: lastKnown(manager)
        return location?.let { it.latitude to it.longitude }
    }

    private suspend fun requestCurrent(
        manager: LocationManager,
        provider: String
    ): android.location.Location? =
        suspendCancellableCoroutine { cont ->
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    null as android.os.CancellationSignal?,
                    ContextCompat.getMainExecutor(context),
                    androidx.core.util.Consumer { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                )
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun lastKnown(manager: LocationManager): android.location.Location? = try {
        listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    } catch (_: SecurityException) {
        null
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 8_000L
    }
}
