package com.chicot.turdalert.location

data class Coordinates(
    val latitude: Double,
    val longitude: Double
)

interface LocationProvider {
    suspend fun currentLocation(): Coordinates?
}
