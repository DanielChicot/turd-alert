package com.chicot.turdalert.model

import com.chicot.turdalert.location.Coordinates

data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

private const val DEGREES_PER_MILE_LAT = 1.0 / 69.0
private const val DEGREES_PER_MILE_LON_AT_55 = 1.0 / 39.0

fun BoundingBox.overlaps(other: BoundingBox): Boolean =
    minLat <= other.maxLat && maxLat >= other.minLat &&
        minLon <= other.maxLon && maxLon >= other.minLon

fun Coordinates.toBoundingBox(radiusMiles: Double = 5.0): BoundingBox {
    val dLat = radiusMiles * DEGREES_PER_MILE_LAT
    val dLon = radiusMiles * DEGREES_PER_MILE_LON_AT_55
    return BoundingBox(
        minLat = latitude - dLat,
        maxLat = latitude + dLat,
        minLon = longitude - dLon,
        maxLon = longitude + dLon
    )
}

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
