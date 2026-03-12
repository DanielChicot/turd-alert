package com.chicot.turdalert.domain

import com.chicot.turdalert.location.Coordinates
import com.chicot.turdalert.model.OverflowPoint
import com.chicot.turdalert.util.distanceMiles

private const val DEFAULT_RADIUS_MILES = 1.0

fun List<OverflowPoint>.withinRadius(
    location: Coordinates,
    radiusMiles: Double = DEFAULT_RADIUS_MILES
): List<OverflowPoint> =
    filter { point ->
        distanceMiles(
            location.latitude, location.longitude,
            point.latitude, point.longitude
        ) <= radiusMiles
    }.sortedBy { point ->
        distanceMiles(
            location.latitude, location.longitude,
            point.latitude, point.longitude
        )
    }
