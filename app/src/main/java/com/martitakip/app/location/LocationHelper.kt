package com.martitakip.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-shot ("event based") location capture.
 *
 * Design rules that keep the battery alive:
 *  - No [android.app.Service], no `requestLocationUpdates`, no wake locks.
 *  - A single [CurrentLocationRequest] is issued per user tap and is cancelled
 *    as soon as the coroutine is cancelled.
 *  - The whole call is bounded by [TIMEOUT_MS]; if no fix arrives the caller
 *    stores the swap with `null` coordinates instead of blocking the UI.
 *
 * A last-known fix from the platform [LocationManager] is used as an instant
 * fallback when it is fresh enough, which is what makes the tap feel immediate
 * in the field.
 */
class LocationHelper(private val context: Context) {

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    /** True when at least one of the two location permissions is granted. */
    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isLocationEnabled(): Boolean = runCatching {
        locationManager?.isLocationEnabled == true
    }.getOrDefault(false)

    /**
     * Captures a single coordinate pair.
     *
     * Order of preference:
     *  1. A very recent last-known fix (< [LAST_KNOWN_MAX_AGE_MS]) - instant.
     *  2. A fresh high-accuracy [CurrentLocationRequest] bounded by [TIMEOUT_MS].
     *  3. Any last-known fix, however old, as a last resort.
     *
     * Returns `null` on missing permission, disabled GPS, timeout, or error.
     * Never throws: a failed fix must not lose the swap.
     */
    @SuppressLint("MissingPermission") // guarded by hasPermission()
    suspend fun getCurrentLocation(
        timeoutMs: Long = TIMEOUT_MS,
        maxAgeMs: Long = LAST_KNOWN_MAX_AGE_MS,
    ): SwapCoordinate? {
        if (!hasPermission()) return null

        // 1. Fast path: a recent cached fix avoids spinning up the GPS chip.
        lastKnownLocation()?.let { cached ->
            val age = System.currentTimeMillis() - cached.time
            if (cached.hasAccuracy() && age in 0..maxAgeMs) {
                return SwapCoordinate(cached.latitude, cached.longitude, cached.accuracy)
            }
        }

        // 2. A single fused request with a hard deadline.
        val fresh = withTimeoutOrNull(timeoutMs) {
            runCatching { requestCurrentLocation() }.getOrNull()
        }
        if (fresh != null) {
            return SwapCoordinate(fresh.latitude, fresh.longitude, fresh.accuracy)
        }

        // 3. Stale cache beats no coordinates at all for heat-map purposes.
        return lastKnownLocation()?.let { stale ->
            SwapCoordinate(stale.latitude, stale.longitude, stale.accuracy)
        }
    }

    /**
     * Requests exactly one high-accuracy fix. The returned coroutine is
     * cancellable: cancelling it removes the underlying location request.
     */
    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(TIMEOUT_MS)
                .setMaxUpdateAgeMillis(MAX_UPDATE_AGE_MS)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .build()

            // A cancellation token is the supported way to abort a single
            // in-flight fix; cancelling the coroutine cancels the request so
            // the GPS chip can power down immediately.
            val cancellation = CancellationTokenSource()
            val task = fusedClient.getCurrentLocation(request, cancellation.token)

            task.addOnSuccessListener { location ->
                if (continuation.isActive) continuation.resume(location)
            }
            task.addOnFailureListener {
                // Resume with null instead of throwing: the caller treats a
                // missing fix as "save without coordinates".
                if (continuation.isActive) continuation.resume(null)
            }
            task.addOnCanceledListener {
                if (continuation.isActive) continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                cancellation.cancel()
            }
        }

    /**
     * Best cached fix available from any provider. Uses [LocationManager]
     * directly because it works even when Play Services is temporarily
     * unavailable (e.g. a device with an outdated GMS build).
     */
    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): Location? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val manager = locationManager ?: return@withContext null

        val providers = buildList {
            runCatching { manager.getProviders(true) }.getOrDefault(emptyList()).forEach(::add)
            // Ensure the common ones are attempted even if disabled right now.
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach {
                if (!contains(it)) add(it)
            }
        }

        var best: Location? = null
        for (provider in providers) {
            val candidate = try {
                manager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            } ?: continue
            val currentBest = best
            if (currentBest == null || candidate.time > currentBest.time) {
                best = candidate
            }
        }
        best
    }

    /** Rough distance in metres between two coordinates (Haversine). */
    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    companion object {
        /** Hard ceiling for a single capture - the UI must never wait longer. */
        const val TIMEOUT_MS = 6_000L

        /** A cached fix younger than this is reused instantly. */
        const val LAST_KNOWN_MAX_AGE_MS = 120_000L

        /** Play Services will not return a fix older than this. */
        private const val MAX_UPDATE_AGE_MS = 60_000L

        /** Permissions requested by the tracker screen. */
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}

/** Immutable coordinate result of a single capture. */
data class SwapCoordinate(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f,
) {
    val isValid: Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0 && !(latitude == 0.0 && longitude == 0.0)
}
