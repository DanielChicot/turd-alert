package com.chicot.turdalert.location

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidLocationProvider(private val context: Context) : LocationProvider {

    override suspend fun currentLocation(): Coordinates? {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val cached = cachedLocation(locationManager)
        if (cached != null) return cached

        return withTimeoutOrNull(15_000) { freshLocation(locationManager) }
    }

    private fun cachedLocation(locationManager: LocationManager): Coordinates? =
        try {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .filter { locationManager.isProviderEnabled(it) }
                .mapNotNull { locationManager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.toCoordinates()
        } catch (_: SecurityException) {
            null
        }

    private suspend fun freshLocation(locationManager: LocationManager): Coordinates? =
        suspendCancellableCoroutine { continuation ->
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                else -> {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    continuation.resume(location.toCoordinates())
                }

                @Deprecated("Required for API < 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }

            try {
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: SecurityException) {
                continuation.resume(null)
            }
        }

    private fun Location.toCoordinates() = Coordinates(latitude = latitude, longitude = longitude)
}
