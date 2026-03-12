package com.chicot.turdalert.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ArcGisResponse(
    val features: List<ArcGisFeature> = emptyList()
)

@Serializable
internal data class ArcGisFeature(
    val attributes: ArcGisAttributes
)

@Serializable
internal data class ArcGisAttributes(
    @SerialName("Id") val id: String? = null,
    @SerialName("Latitude") val latitude: Double? = null,
    @SerialName("Longitude") val longitude: Double? = null,
    @SerialName("Status") val status: Int? = null,
    @SerialName("ReceivingWaterCourse") val receivingWaterCourse: String? = null,
    @SerialName("SiteName") val siteName: String? = null,
    @SerialName("StatusStart") val statusStart: Long? = null,
    @SerialName("LatestEventStart") val latestEventStart: Long? = null
)

@Serializable
internal data class SouthWestWaterResponse(
    val features: List<SouthWestWaterFeature> = emptyList()
)

@Serializable
internal data class SouthWestWaterFeature(
    val attributes: SouthWestWaterAttributes
)

@Serializable
internal data class SouthWestWaterAttributes(
    @SerialName("Id") val id: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: Int? = null,
    val receivingWaterCourse: String? = null,
    @SerialName("SiteName") val siteName: String? = null,
    val statusStart: Long? = null
)
