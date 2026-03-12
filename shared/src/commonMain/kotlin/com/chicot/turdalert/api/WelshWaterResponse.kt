package com.chicot.turdalert.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class WelshWaterResponse(
    val features: List<WelshWaterFeature> = emptyList()
)

@Serializable
internal data class WelshWaterFeature(
    val attributes: WelshWaterAttributes
)

@Serializable
internal data class WelshWaterAttributes(
    @SerialName("asset_name") val assetName: String? = null,
    @SerialName("permit_number") val permitNumber: String? = null,
    @SerialName("discharge_x_location") val dischargeX: Double? = null,
    @SerialName("discharge_y_location") val dischargeY: Double? = null,
    @SerialName("Receiving_Water") val receivingWater: String? = null,
    val status: String? = null,
    @SerialName("start_date_time_discharge") val startDateTime: String? = null
)
