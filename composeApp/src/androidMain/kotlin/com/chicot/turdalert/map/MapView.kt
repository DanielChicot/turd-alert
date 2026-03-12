package com.chicot.turdalert.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.chicot.turdalert.location.Coordinates
import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

private fun DischargeStatus.markerHue(): Float = when (this) {
    DischargeStatus.DISCHARGING -> BitmapDescriptorFactory.HUE_RED
    DischargeStatus.NOT_DISCHARGING -> BitmapDescriptorFactory.HUE_GREEN
    DischargeStatus.OFFLINE -> BitmapDescriptorFactory.HUE_VIOLET
}

@Composable
actual fun MapView(
    overflows: List<OverflowPoint>,
    userLocation: Coordinates,
    bounds: BoundingBox,
    onMarkerClick: (OverflowPoint) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier
) {
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(bounds) {
        val latLngBounds = LatLngBounds(
            LatLng(bounds.minLat, bounds.minLon),
            LatLng(bounds.maxLat, bounds.maxLon)
        )
        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(latLngBounds, 64))
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        onMapClick = { onMapClick() }
    ) {
        Circle(
            center = LatLng(userLocation.latitude, userLocation.longitude),
            radius = 40.0,
            fillColor = androidx.compose.ui.graphics.Color(0x554285F4),
            strokeColor = androidx.compose.ui.graphics.Color(0xFF4285F4),
            strokeWidth = 2f
        )

        overflows.forEach { overflow ->
            Marker(
                state = rememberMarkerState(
                    key = overflow.id,
                    position = LatLng(overflow.latitude, overflow.longitude)
                ),
                title = overflow.siteName,
                icon = BitmapDescriptorFactory.defaultMarker(overflow.status.markerHue()),
                onClick = {
                    onMarkerClick(overflow)
                    true
                }
            )
        }
    }
}
