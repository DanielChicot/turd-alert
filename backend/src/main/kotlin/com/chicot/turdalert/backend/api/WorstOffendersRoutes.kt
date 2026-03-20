package com.chicot.turdalert.backend.api

import com.chicot.turdalert.db.repository.ReadingRepository
import com.chicot.turdalert.db.repository.SiteRepository
import com.chicot.turdalert.db.repository.SiteRow
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_MILES = 3959.0
private const val MILES_PER_DEG_LAT = 69.0
private const val MILES_PER_DEG_LON_AT_55N = 39.0
private const val MAX_RESULTS = 20

fun Route.worstOffendersRoutes(
    database: Database,
    siteRepository: SiteRepository,
    readingRepository: ReadingRepository
) {
    get("/api/v1/sites/worst-offenders/national") {
        val days = (call.request.queryParameters["days"]?.toIntOrNull() ?: 30).coerceAtMost(90)
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 10).coerceAtMost(50)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val from = now.minusDays(days.toLong())

        val results = transaction(database) {
            readingRepository.topOffendersNational(from, limit).map { ranked ->
                val totalHours = ranked.dischargingReadings * 15.0 / 60.0
                val pct = if (ranked.totalReadings > 0)
                    ranked.dischargingReadings.toDouble() / ranked.totalReadings * 100.0 else 0.0
                WorstOffenderResponse(
                    site = SiteResponse(
                        id = ranked.siteId,
                        siteName = ranked.siteName,
                        watercourse = ranked.watercourse,
                        company = ranked.company,
                        latitude = ranked.latitude,
                        longitude = ranked.longitude
                    ),
                    stats = StatsResponse(
                        totalDischargeHours = totalHours,
                        eventCount = 0,
                        longestEventHours = 0.0,
                        percentDischarging = pct,
                        lastDischargeAt = ranked.lastDischargeAt?.toString()
                    )
                )
            }
        }

        call.respond(results)
    }

    get("/api/v1/sites/worst-offenders") {
        val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
        val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()

        if (lat == null || lon == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing required parameters: lat, lon")
            return@get
        }

        val radius = call.request.queryParameters["radius"]?.toDoubleOrNull() ?: 1.0
        val days = (call.request.queryParameters["days"]?.toIntOrNull() ?: 30).coerceAtMost(90)

        val latDelta = radius / MILES_PER_DEG_LAT
        val lonDelta = radius / MILES_PER_DEG_LON_AT_55N

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val from = now.minusDays(days.toLong())

        val results = transaction(database) {
            siteRepository.sitesInBounds(lat - latDelta, lat + latDelta, lon - lonDelta, lon + lonDelta)
                .filter { haversineDistance(lat, lon, it.latitude, it.longitude) <= radius }
                .map { site ->
                    val readings = readingRepository.siteHistory(site.company, site.siteId, from, now)
                    val stats = statsResponse(readings)
                    WorstOffenderResponse(site = site.toSiteResponse(), stats = stats)
                }
                .filter { it.stats.eventCount > 0 }
                .sortedByDescending { it.stats.totalDischargeHours }
                .take(MAX_RESULTS)
        }

        call.respond(results)
    }
}

private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_MILES * asin(sqrt(a))
}

private fun SiteRow.withinRadius(centerLat: Double, centerLon: Double, radiusMiles: Double): Boolean =
    haversineDistance(centerLat, centerLon, latitude, longitude) <= radiusMiles
