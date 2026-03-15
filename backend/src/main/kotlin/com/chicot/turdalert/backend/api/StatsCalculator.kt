package com.chicot.turdalert.backend.api

import com.chicot.turdalert.db.repository.ReadingRow
import com.chicot.turdalert.db.repository.SiteRow

fun SiteRow.toSiteResponse(): SiteResponse =
    SiteResponse(
        id = siteId,
        siteName = siteName,
        watercourse = watercourse,
        company = company,
        latitude = latitude,
        longitude = longitude
    )

fun ReadingRow.toTimelineEntry(): TimelineEntry =
    TimelineEntry(
        timestamp = polledAt.toString(),
        status = status.toInt()
    )

fun statsResponse(readings: List<ReadingRow>): StatsResponse {
    if (readings.isEmpty()) {
        return StatsResponse(
            totalDischargeHours = 0.0,
            eventCount = 0,
            longestEventHours = 0.0,
            percentDischarging = 0.0,
            lastDischargeAt = null
        )
    }

    val dischargingCount = readings.count { it.status > 0 }
    val totalDischargeHours = dischargingCount * 15.0 / 60.0

    val eventCount = readings
        .zipWithNext()
        .count { (prev, curr) -> prev.status <= 0 && curr.status > 0 } +
        if (readings.first().status > 0) 1 else 0

    val longestRun = readings
        .fold(Pair(0, 0)) { (current, longest), reading ->
            if (reading.status > 0) Pair(current + 1, maxOf(longest, current + 1))
            else Pair(0, longest)
        }.second
    val longestEventHours = longestRun * 15.0 / 60.0

    val percentDischarging = if (readings.isNotEmpty()) {
        dischargingCount.toDouble() / readings.size * 100.0
    } else 0.0

    val lastDischargeAt = readings
        .filter { it.status > 0 }
        .maxByOrNull { it.polledAt }
        ?.polledAt
        ?.toString()

    return StatsResponse(
        totalDischargeHours = totalDischargeHours,
        eventCount = eventCount,
        longestEventHours = longestEventHours,
        percentDischarging = percentDischarging,
        lastDischargeAt = lastDischargeAt
    )
}
