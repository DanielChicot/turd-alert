package com.chicot.turdalert.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.chicot.turdalert.location.Coordinates
import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSSelectorFromString
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPinAnnotationView
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UITapGestureRecognizer
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
private class OverflowAnnotation(
    val overflow: OverflowPoint
) : MKPointAnnotation() {
    init {
        setCoordinate(CLLocationCoordinate2DMake(overflow.latitude, overflow.longitude))
        setTitle(overflow.siteName)
    }
}

private class MapDelegate(
    private val onMarkerClick: (OverflowPoint) -> Unit,
    private val onMapClick: () -> Unit,
    private val onViewportChanged: (BoundingBox) -> Unit
) : NSObject(), MKMapViewDelegateProtocol {

    @ObjCAction
    fun handleMapTap() {
        onMapClick()
    }

    override fun mapView(
        mapView: MKMapView,
        viewForAnnotation: MKAnnotationProtocol
    ): MKAnnotationView? {
        if (viewForAnnotation is OverflowAnnotation) {
            val id = "overflow"
            val view = mapView.dequeueReusableAnnotationViewWithIdentifier(id) as? MKPinAnnotationView
                ?: MKPinAnnotationView(annotation = viewForAnnotation, reuseIdentifier = id)
            view.annotation = viewForAnnotation
            view.pinTintColor = when (viewForAnnotation.overflow.status) {
                DischargeStatus.DISCHARGING -> platform.UIKit.UIColor.redColor
                DischargeStatus.NOT_DISCHARGING -> platform.UIKit.UIColor.greenColor
                DischargeStatus.OFFLINE -> platform.UIKit.UIColor.grayColor
            }
            view.canShowCallout = false
            return view
        }
        return null
    }

    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val annotation = didSelectAnnotationView.annotation
        if (annotation is OverflowAnnotation) {
            onMarkerClick(annotation.overflow)
        }
        mapView.deselectAnnotation(annotation, animated = false)
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        val region = mapView.region
        val center = region.center
        val span = region.span
        onViewportChanged(
            BoundingBox(
                minLat = center.latitude - span.latitudeDelta / 2.0,
                maxLat = center.latitude + span.latitudeDelta / 2.0,
                minLon = center.longitude - span.longitudeDelta / 2.0,
                maxLon = center.longitude + span.longitudeDelta / 2.0
            )
        )
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun MapView(
    overflows: List<OverflowPoint>,
    userLocation: Coordinates,
    bounds: BoundingBox?,
    onMarkerClick: (OverflowPoint) -> Unit,
    onMapClick: () -> Unit,
    onViewportChanged: (BoundingBox) -> Unit,
    modifier: Modifier
) {
    val delegate = remember(onMarkerClick, onMapClick, onViewportChanged) {
        MapDelegate(onMarkerClick, onMapClick, onViewportChanged)
    }

    UIKitView(
        factory = {
            MKMapView().apply {
                this.delegate = delegate
                showsUserLocation = true
                val tapGesture = UITapGestureRecognizer(
                    target = delegate,
                    action = NSSelectorFromString("handleMapTap")
                )
                addGestureRecognizer(tapGesture)
            }
        },
        update = { mapView ->
            mapView.removeAnnotations(mapView.annotations)

            overflows.forEach { overflow ->
                mapView.addAnnotation(OverflowAnnotation(overflow))
            }

            if (bounds != null) {
                val centerLat = (bounds.minLat + bounds.maxLat) / 2.0
                val centerLon = (bounds.minLon + bounds.maxLon) / 2.0
                val spanLat = (bounds.maxLat - bounds.minLat) * 1.2
                val spanLon = (bounds.maxLon - bounds.minLon) * 1.2
                val region = MKCoordinateRegionMake(
                    CLLocationCoordinate2DMake(centerLat, centerLon),
                    MKCoordinateSpanMake(spanLat, spanLon)
                )
                mapView.setRegion(region, animated = true)
            }
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative
        ),
        modifier = modifier
    )
}
