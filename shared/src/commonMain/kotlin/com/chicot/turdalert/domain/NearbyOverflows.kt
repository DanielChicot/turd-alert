package com.chicot.turdalert.domain

import com.chicot.turdalert.location.Coordinates
import com.chicot.turdalert.model.OverflowPoint
import com.chicot.turdalert.util.distanceMiles

private const val DEFAULT_RADIUS_MILES = 1.0
private const val MIN_RESULTS = 5

fun List<OverflowPoint>.nearbyOverflows(
    location: Coordinates,
    radiusMiles: Double = DEFAULT_RADIUS_MILES,
    minCount: Int = MIN_RESULTS
): List<OverflowPoint> {
    val sorted = sortedBy { point ->
        distanceMiles(location.latitude, location.longitude, point.latitude, point.longitude)
    }
    val withinRadius = sorted.filter { point ->
        distanceMiles(location.latitude, location.longitude, point.latitude, point.longitude) <= radiusMiles
    }
    return if (withinRadius.size >= minCount) withinRadius
    else sorted.take(minCount)
}
