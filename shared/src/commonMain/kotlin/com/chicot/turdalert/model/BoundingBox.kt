package com.chicot.turdalert.model

import com.chicot.turdalert.location.Coordinates

data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

fun cameraBounds(
    points: List<OverflowPoint>,
    location: Coordinates,
    paddingDegrees: Double = 0.005
): BoundingBox {
    val allLats = points.map { it.latitude } + location.latitude
    val allLons = points.map { it.longitude } + location.longitude
    return BoundingBox(
        minLat = allLats.min() - paddingDegrees,
        maxLat = allLats.max() + paddingDegrees,
        minLon = allLons.min() - paddingDegrees,
        maxLon = allLons.max() + paddingDegrees
    )
}
