package com.chicot.turdalert.backend.api

import kotlinx.serialization.Serializable

@Serializable
data class OverflowResponse(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val status: String,
    val watercourse: String,
    val siteName: String,
    val statusStart: Long? = null,
    val company: String
)

@Serializable
data class SiteResponse(
    val id: String,
    val siteName: String?,
    val watercourse: String?,
    val company: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class StatsResponse(
    val totalDischargeHours: Double,
    val eventCount: Int,
    val longestEventHours: Double,
    val percentDischarging: Double,
    val lastDischargeAt: String?
)

@Serializable
data class HistoryResponse(
    val site: SiteResponse,
    val stats: StatsResponse,
    val timeline: List<TimelineEntry>
)

@Serializable
data class TimelineEntry(
    val timestamp: String,
    val status: Int
)

@Serializable
data class WorstOffenderResponse(
    val site: SiteResponse,
    val stats: StatsResponse
)

@Serializable
data class HealthResponse(
    val status: String,
    val lastPollAt: String?,
    val lastPollDurationMs: Long,
    val companiesPolled: Int,
    val companiesFailed: Int,
    val databaseReachable: Boolean,
    val uptimeSeconds: Long
)
