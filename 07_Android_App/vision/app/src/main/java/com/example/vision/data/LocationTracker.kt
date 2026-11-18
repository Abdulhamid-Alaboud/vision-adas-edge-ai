package com.example.vision.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Pure location sample representation for hardware-independent coordinate selection and unit testing.
 */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMs: Long,
    val provider: String
)

/**
 * Tracks real-time device coordinates via Android's native LocationManager (GPS + Fused + Network),
 * operating completely offline with high-accuracy filtering and no synthetic hardcoded coordinates.
 */
class LocationTracker(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @Volatile
    private var bestSample: LocationSample? = null

    @Volatile
    private var isListening: Boolean = false

    private val locationListener = LocationListener { location ->
        updateWithLocation(location)
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        if (!hasLocationPermission() || locationManager == null) return

        // Always refresh the best known cached fix first
        refreshLastKnownLocations()

        if (isListening) return
        try {
            val providersToRegister = buildList {
                if (isProviderUsable(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isProviderUsable(LocationManager.FUSED_PROVIDER)) {
                    add(LocationManager.FUSED_PROVIDER)
                }
                if (isProviderUsable(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            }

            for (provider in providersToRegister) {
                locationManager.requestLocationUpdates(
                    provider,
                    MIN_UPDATE_INTERVAL_MS,
                    MIN_UPDATE_DISTANCE_M,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            // Trigger an immediate fresh single fix on API 30+ so initial trip detections get accurate coordinates
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val activePrimary = providersToRegister.firstOrNull()
                if (activePrimary != null) {
                    locationManager.getCurrentLocation(
                        activePrimary,
                        null,
                        context.mainExecutor
                    ) { loc ->
                        if (loc != null) updateWithLocation(loc)
                    }
                }
            }

            isListening = providersToRegister.isNotEmpty()
            Log.i(TAG, "Location updates started on providers: $providersToRegister")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register location updates: ${e.message}")
        }
    }

    fun stopUpdates() {
        if (!isListening || locationManager == null) return
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove location updates: ${e.message}")
        } finally {
            isListening = false
        }
    }

    /**
     * Returns the current validated (latitude, longitude), or (0.0, 0.0) if no valid fix is available yet.
     */
    fun getCurrentCoordinates(): Pair<Double, Double> {
        if (hasLocationPermission() && bestSample == null) {
            refreshLastKnownLocations()
        }
        val sample = bestSample
        return if (sample != null && isValidCoordinate(sample.latitude, sample.longitude)) {
            Pair(sample.latitude, sample.longitude)
        } else {
            Pair(0.0, 0.0)
        }
    }

    fun hasValidFix(): Boolean {
        val sample = bestSample ?: return false
        return isValidCoordinate(sample.latitude, sample.longitude)
    }

    @SuppressLint("MissingPermission")
    private fun refreshLastKnownLocations() {
        val lm = locationManager ?: return
        if (!hasLocationPermission()) return

        val candidateProviders = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }

        for (provider in candidateProviders) {
            try {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) {
                    updateWithLocation(loc)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun isProviderUsable(provider: String): Boolean {
        val lm = locationManager ?: return false
        return try {
            lm.allProviders.contains(provider) && lm.isProviderEnabled(provider)
        } catch (_: Exception) {
            false
        }
    }

    private fun updateWithLocation(location: Location) {
        val sample = LocationSample(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 999f,
            timeMs = location.time,
            provider = location.provider ?: "unknown"
        )
        if (isBetterSample(sample, bestSample)) {
            bestSample = sample
        }
    }

    companion object {
        private const val TAG = "LocationTracker"
        private const val MIN_UPDATE_INTERVAL_MS = 1500L
        private const val MIN_UPDATE_DISTANCE_M = 1.5f
        private const val SIGNIFICANT_TIME_DELTA_MS = 30_000L
        private const val STALE_GPS_WINDOW_MS = 120_000L

        fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
            if (latitude.isNaN() || longitude.isNaN()) return false
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
            return !(abs(latitude) < 1e-6 && abs(longitude) < 1e-6)
        }

        fun isBetterSample(candidate: LocationSample, currentBest: LocationSample?): Boolean {
            if (!isValidCoordinate(candidate.latitude, candidate.longitude)) return false
            if (currentBest == null) return true

            val timeDelta = candidate.timeMs - currentBest.timeMs
            val isSignificantlyNewer = timeDelta > SIGNIFICANT_TIME_DELTA_MS
            val isSignificantlyOlder = timeDelta < -SIGNIFICANT_TIME_DELTA_MS
            val isNewer = timeDelta > 0L

            if (isSignificantlyNewer) {
                val keepAccurateGpsOverVeryCoarse =
                    candidate.accuracyMeters > 150f &&
                        currentBest.provider == LocationManager.GPS_PROVIDER &&
                        currentBest.accuracyMeters <= 30f &&
                        timeDelta < STALE_GPS_WINDOW_MS
                return !keepAccurateGpsOverVeryCoarse
            }
            if (isSignificantlyOlder) return false

            val accuracyDelta = candidate.accuracyMeters - currentBest.accuracyMeters
            val isMoreAccurate = accuracyDelta < 0f
            val isSameOrBetterAccuracy = accuracyDelta <= 0f
            val isNotMuchWorse = accuracyDelta <= 25f
            val isFromGps = candidate.provider == LocationManager.GPS_PROVIDER
            val isSameProvider = candidate.provider == currentBest.provider

            return when {
                isMoreAccurate -> true
                isNewer && isSameOrBetterAccuracy -> true
                isNewer && isNotMuchWorse && (isSameProvider || isFromGps) -> true
                else -> false
            }
        }
    }
}
