package com.chicot.turdalert.db.repository

import com.chicot.turdalert.db.tables.ReadingsTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection
import java.time.OffsetDateTime

data class ReadingRow(
    val company: String,
    val siteId: String,
    val polledAt: OffsetDateTime,
    val status: Short,
    val statusStart: OffsetDateTime?
) {
    constructor(
        company: String,
        siteId: String,
        polledAt: OffsetDateTime,
        status: Int,
        statusStart: OffsetDateTime? = null
    ) : this(company, siteId, polledAt, status.toShort(), statusStart)
}

data class LatestReading(
    val company: String,
    val siteId: String,
    val siteName: String?,
    val watercourse: String?,
    val latitude: Double,
    val longitude: Double,
    val polledAt: OffsetDateTime,
    val status: Short,
    val statusStart: OffsetDateTime?
)

data class SiteStats(
    val dischargingReadings: Long,
    val totalReadings: Long,
    val lastDischargeAt: OffsetDateTime?
)

class ReadingRepository {

    fun insertReadings(readings: List<ReadingRow>) {
        readings.forEach { reading ->
            ReadingsTable.insertIgnore {
                it[company] = reading.company
                it[siteId] = reading.siteId
                it[polledAt] = reading.polledAt
                it[status] = reading.status
                it[statusStart] = reading.statusStart
            }
        }
    }

    fun latestReadings(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double
    ): List<LatestReading> {
        val sql = """
            SELECT DISTINCT ON (s.company, s.site_id)
                s.company, s.site_id, s.site_name, s.watercourse,
                s.latitude, s.longitude,
                r.polled_at, r.status, r.status_start
            FROM sites s
            JOIN readings r ON s.company = r.company AND s.site_id = r.site_id
            WHERE s.latitude >= ? AND s.latitude <= ?
              AND s.longitude >= ? AND s.longitude <= ?
              AND r.polled_at >= now() - interval '6 hours'
            ORDER BY s.company, s.site_id, r.polled_at DESC
        """.trimIndent()

        val conn = TransactionManager.current().connection.connection as Connection
        return conn.prepareStatement(sql).use { stmt ->
            stmt.setDouble(1, minLat)
            stmt.setDouble(2, maxLat)
            stmt.setDouble(3, minLon)
            stmt.setDouble(4, maxLon)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            LatestReading(
                                company = rs.getString("company"),
                                siteId = rs.getString("site_id"),
                                siteName = rs.getString("site_name"),
                                watercourse = rs.getString("watercourse"),
                                latitude = rs.getDouble("latitude"),
                                longitude = rs.getDouble("longitude"),
                                polledAt = rs.getObject("polled_at", OffsetDateTime::class.java),
                                status = rs.getShort("status"),
                                statusStart = rs.getObject("status_start", OffsetDateTime::class.java)
                            )
                        )
                    }
                }
            }
        }
    }

    fun siteHistory(
        company: String, siteId: String, from: OffsetDateTime, to: OffsetDateTime
    ): List<ReadingRow> =
        ReadingsTable.selectAll()
            .where {
                (ReadingsTable.company eq company) and
                    (ReadingsTable.siteId eq siteId) and
                    (ReadingsTable.polledAt greaterEq from) and
                    (ReadingsTable.polledAt lessEq to)
            }
            .orderBy(ReadingsTable.polledAt)
            .map { row ->
                ReadingRow(
                    company = row[ReadingsTable.company],
                    siteId = row[ReadingsTable.siteId],
                    polledAt = row[ReadingsTable.polledAt],
                    status = row[ReadingsTable.status],
                    statusStart = row[ReadingsTable.statusStart]
                )
            }

    data class RankedSite(
        val company: String,
        val siteId: String,
        val siteName: String?,
        val watercourse: String?,
        val latitude: Double,
        val longitude: Double,
        val dischargingReadings: Int,
        val totalReadings: Int,
        val lastDischargeAt: OffsetDateTime?
    )

    fun topOffendersNational(from: OffsetDateTime, limit: Int = 10): List<RankedSite> {
        val sql = """
            SELECT s.company, s.site_id, s.site_name, s.watercourse, s.latitude, s.longitude,
                   count(*) FILTER (WHERE r.status > 0) AS discharging_readings,
                   count(*) AS total_readings,
                   max(r.polled_at) FILTER (WHERE r.status > 0) AS last_discharge_at
            FROM readings r
            JOIN sites s ON r.company = s.company AND r.site_id = s.site_id
            WHERE r.polled_at >= ?
            GROUP BY s.company, s.site_id, s.site_name, s.watercourse, s.latitude, s.longitude
            HAVING count(*) FILTER (WHERE r.status > 0) > 0
            ORDER BY discharging_readings DESC
            LIMIT ?
        """.trimIndent()

        val conn = TransactionManager.current().connection.connection as java.sql.Connection
        return conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, from)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(RankedSite(
                            company = rs.getString("company"),
                            siteId = rs.getString("site_id"),
                            siteName = rs.getString("site_name"),
                            watercourse = rs.getString("watercourse"),
                            latitude = rs.getDouble("latitude"),
                            longitude = rs.getDouble("longitude"),
                            dischargingReadings = rs.getInt("discharging_readings"),
                            totalReadings = rs.getInt("total_readings"),
                            lastDischargeAt = rs.getObject("last_discharge_at", OffsetDateTime::class.java)
                        ))
                    }
                }
            }
        }
    }

    fun siteStats(
        company: String, siteId: String, from: OffsetDateTime, to: OffsetDateTime
    ): SiteStats {
        val history = siteHistory(company, siteId, from, to)
        val dischargingReadings = history.count { it.status > 0 }
        val lastDischargeAt = history.filter { it.status > 0 }.maxByOrNull { it.polledAt }?.polledAt
        return SiteStats(
            dischargingReadings = dischargingReadings.toLong(),
            totalReadings = history.size.toLong(),
            lastDischargeAt = lastDischargeAt
        )
    }
}
