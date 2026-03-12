package com.chicot.turdalert.api

import kotlinx.serialization.Serializable

@Serializable
internal data class ThamesWaterResponse(
    val items: List<ThamesWaterItem> = emptyList()
)

@Serializable
internal data class ThamesWaterItem(
    val locationName: String? = null,
    val permitNumber: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val receivingWaterCourse: String? = null,
    val alertStatus: String? = null,
    val statusChanged: String? = null
)
