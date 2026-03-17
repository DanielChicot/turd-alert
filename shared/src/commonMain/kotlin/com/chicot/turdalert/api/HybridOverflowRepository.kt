package com.chicot.turdalert.api

import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

@Serializable
private data class BackendOverflow(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val status: String,
    val watercourse: String,
    val siteName: String,
    val statusStart: Long? = null,
    val company: String
)

class HybridOverflowRepository(
    private val backendUrl: String,
    private val client: HttpClient,
    private val fallback: OverflowFetcher
) : OverflowFetcher {

    override suspend fun allOverflows(bounds: BoundingBox): List<OverflowPoint> =
        try {
            fetchFromBackend(bounds)
        } catch (_: Exception) {
            fallback.allOverflows(bounds)
        }

    private suspend fun fetchFromBackend(bounds: BoundingBox): List<OverflowPoint> =
        client.get("$backendUrl/api/v1/overflows") {
            parameter("minLat", bounds.minLat)
            parameter("maxLat", bounds.maxLat)
            parameter("minLon", bounds.minLon)
            parameter("maxLon", bounds.maxLon)
        }.body<List<BackendOverflow>>()
            .map { it.toOverflowPoint() }
}

private fun BackendOverflow.toOverflowPoint() = OverflowPoint(
    id = id,
    latitude = latitude,
    longitude = longitude,
    status = when (status) {
        "DISCHARGING" -> DischargeStatus.DISCHARGING
        "RECENT_DISCHARGE" -> DischargeStatus.RECENT_DISCHARGE
        "NOT_DISCHARGING" -> DischargeStatus.NOT_DISCHARGING
        else -> DischargeStatus.OFFLINE
    },
    watercourse = watercourse,
    siteName = siteName,
    statusStart = statusStart,
    company = company
)
