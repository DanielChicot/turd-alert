package com.chicot.turdalert.backend.api

import com.chicot.turdalert.db.repository.LatestReading
import com.chicot.turdalert.db.repository.ReadingRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun Route.overflowRoutes(database: Database, readingRepository: ReadingRepository) {
    get("/api/v1/overflows") {
        val minLat = call.request.queryParameters["minLat"]?.toDoubleOrNull()
        val maxLat = call.request.queryParameters["maxLat"]?.toDoubleOrNull()
        val minLon = call.request.queryParameters["minLon"]?.toDoubleOrNull()
        val maxLon = call.request.queryParameters["maxLon"]?.toDoubleOrNull()

        if (minLat == null || maxLat == null || minLon == null || maxLon == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing required parameters: minLat, maxLat, minLon, maxLon")
            return@get
        }

        val readings = transaction(database) {
            readingRepository.latestReadings(minLat, maxLat, minLon, maxLon)
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        call.respond(readings.map { it.toOverflowResponse(now) })
    }
}

private fun LatestReading.toOverflowResponse(now: OffsetDateTime): OverflowResponse {
    val statusString = when {
        status > 0 -> "DISCHARGING"
        status == 0.toShort() && statusStart != null &&
            Duration.between(statusStart, now).toHours() < 1 -> "RECENT_DISCHARGE"
        status == 0.toShort() -> "NOT_DISCHARGING"
        else -> "OFFLINE"
    }

    return OverflowResponse(
        id = siteId,
        latitude = latitude,
        longitude = longitude,
        status = statusString,
        watercourse = watercourse ?: "",
        siteName = siteName ?: "",
        statusStart = statusStart?.toInstant()?.toEpochMilli(),
        company = company
    )
}
