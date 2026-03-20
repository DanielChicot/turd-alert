package com.chicot.turdalert.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

@Serializable
data class SiteHistoryResponse(
    val site: SiteInfo,
    val stats: SiteStatsResponse,
    val timeline: List<TimelineEntry>
)

@Serializable
data class SiteInfo(
    val id: String,
    val siteName: String?,
    val watercourse: String?,
    val company: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class SiteStatsResponse(
    val totalDischargeHours: Double,
    val eventCount: Int,
    val longestEventHours: Double,
    val percentDischarging: Double,
    val lastDischargeAt: String?
)

@Serializable
data class TimelineEntry(
    val timestamp: String,
    val status: Int
)

class SiteHistoryClient(
    private val backendUrl: String,
    private val client: HttpClient
) {
    suspend fun siteHistory(company: String, siteId: String, days: Int = 30): SiteHistoryResponse? =
        try {
            client.get("$backendUrl/api/v1/sites/${company.encodeURLPathPart()}/${siteId.encodeURLPathPart()}/history") {
                parameter("days", days)
            }.body()
        } catch (_: Exception) {
            null
        }
}
