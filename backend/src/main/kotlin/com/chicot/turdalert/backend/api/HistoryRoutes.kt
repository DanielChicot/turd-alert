package com.chicot.turdalert.backend.api

import com.chicot.turdalert.db.repository.ReadingRepository
import com.chicot.turdalert.db.repository.ReadingRow
import com.chicot.turdalert.db.repository.SiteRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

fun Route.historyRoutes(
    database: Database,
    siteRepository: SiteRepository,
    readingRepository: ReadingRepository
) {
    get("/api/v1/sites/{company}/{siteId}/history") {
        val company = call.parameters["company"]!!
        val siteId = call.parameters["siteId"]!!
        val days = (call.request.queryParameters["days"]?.toIntOrNull() ?: 30).coerceAtMost(90)

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val from = now.minusDays(days.toLong())

        val (site, readings) = transaction(database) {
            val site = siteRepository.findSite(company, siteId)
            val readings = site?.let {
                readingRepository.siteHistory(company, siteId, from, now)
            } ?: emptyList()
            Pair(site, readings)
        }

        if (site == null) {
            call.respond(HttpStatusCode.NotFound, "Site not found")
            return@get
        }

        val timeline = if (days > 7) downsample(readings) else readings.map { it.toTimelineEntry() }

        call.respond(
            HistoryResponse(
                site = site.toSiteResponse(),
                stats = statsResponse(readings),
                timeline = timeline
            )
        )
    }
}

private fun downsample(readings: List<ReadingRow>): List<TimelineEntry> =
    readings
        .groupBy { it.polledAt.truncatedTo(ChronoUnit.HOURS) }
        .toSortedMap()
        .map { (hour, group) ->
            TimelineEntry(
                timestamp = hour.toString(),
                status = group.maxOf { it.status }.toInt()
            )
        }
