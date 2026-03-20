package com.chicot.turdalert.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

@Serializable
data class WorstOffenderResult(
    val site: SiteInfo,
    val stats: SiteStatsResponse
)

class WorstOffendersClient(
    private val backendUrl: String,
    private val client: HttpClient
) {
    suspend fun worstOffenders(
        lat: Double,
        lon: Double,
        radiusMiles: Int = 10,
        days: Int = 30
    ): List<WorstOffenderResult> =
        try {
            client.get("$backendUrl/api/v1/sites/worst-offenders") {
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("radius", radiusMiles)
                parameter("days", days)
            }.body()
        } catch (_: Exception) {
            emptyList()
        }

    suspend fun nationalWorstOffenders(days: Int = 30): List<WorstOffenderResult> =
        try {
            client.get("$backendUrl/api/v1/sites/worst-offenders/national") {
                parameter("days", days)
            }.body()
        } catch (_: Exception) {
            emptyList()
        }
}
