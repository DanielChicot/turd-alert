package com.chicot.turdalert.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

private class LocationDelegate(
    private val continuation: Continuation<Coordinates?>,
    private val onComplete: () -> Unit
) : NSObject(), CLLocationManagerDelegateProtocol {

    private var resumed = false

    private fun complete(coords: Coordinates?) {
        if (!resumed) {
            resumed = true
            onComplete()
            continuation.resume(coords)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.firstOrNull() as? CLLocation
        complete(
            location?.coordinate?.useContents {
                Coordinates(latitude = latitude, longitude = longitude)
            }
        )
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        complete(null)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse,
            kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
            kCLAuthorizationStatusDenied,
            kCLAuthorizationStatusRestricted -> complete(null)
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
        }
    }
}

class IosLocationProvider : LocationProvider {

    private var activeDelegate: LocationDelegate? = null
    private var activeManager: CLLocationManager? = null

    override suspend fun currentLocation(): Coordinates? =
        suspendCancellableCoroutine { continuation ->
            dispatch_async(dispatch_get_main_queue()) {
                val manager = CLLocationManager()
                manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
                activeManager = manager

                val delegate = LocationDelegate(continuation) {
                    activeDelegate = null
                    activeManager = null
                }
                activeDelegate = delegate
                manager.delegate = delegate

                continuation.invokeOnCancellation {
                    activeDelegate = null
                    activeManager?.delegate = null
                    activeManager = null
                }
            }
        }
}
