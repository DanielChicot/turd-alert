package com.chicot.turdalert.backend.poller

import com.chicot.turdalert.api.OverflowRepository
import com.chicot.turdalert.db.partition.PartitionManager
import com.chicot.turdalert.db.repository.ReadingRepository
import com.chicot.turdalert.db.repository.ReadingRow
import com.chicot.turdalert.db.repository.SiteRepository
import com.chicot.turdalert.db.repository.SiteRow
import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val UK_BOUNDS = BoundingBox(minLat = 49.9, maxLat = 60.9, minLon = -8.2, maxLon = 1.8)
private const val POLL_INTERVAL_MS = 15 * 60 * 1000L

fun DischargeStatus.toDbStatus(): Short = when (this) {
    DischargeStatus.DISCHARGING -> 1
    DischargeStatus.NOT_DISCHARGING -> 0
    DischargeStatus.RECENT_DISCHARGE -> 0
    DischargeStatus.OFFLINE -> -1
}

fun OverflowPoint.toSiteRow(): SiteRow = SiteRow(
    company = company,
    siteId = id,
    siteName = siteName,
    watercourse = watercourse,
    latitude = latitude,
    longitude = longitude
)

fun OverflowPoint.toReadingRow(polledAt: OffsetDateTime): ReadingRow = ReadingRow(
    company = company,
    siteId = id,
    polledAt = polledAt,
    status = status.toDbStatus(),
    statusStart = statusStart?.let {
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC)
    }
)

class Poller(
    private val overflowRepository: OverflowRepository,
    private val siteRepository: SiteRepository,
    private val readingRepository: ReadingRepository,
    private val partitionManager: PartitionManager,
    private val database: Database
) {

    private val logger = LoggerFactory.getLogger(Poller::class.java)

    var lastPollAt: OffsetDateTime? = null
        private set
    var lastPollDurationMs: Long = 0
        private set
    var lastCompaniesPolled: Int = 0
        private set
    var lastCompaniesFailed: Int = 0
        private set

    suspend fun runForever() {
        while (true) {
            transaction(database) {
                partitionManager.ensurePartitions(OffsetDateTime.now(ZoneOffset.UTC))
            }
            pollOnce()
            val now = System.currentTimeMillis()
            val delayMs = ((now / POLL_INTERVAL_MS) + 1) * POLL_INTERVAL_MS - now
            logger.info("Next poll in ${delayMs / 1000}s")
            delay(delayMs)
        }
    }

    suspend fun pollOnce() {
        val startMs = System.currentTimeMillis()
        val polledAt = OffsetDateTime.now(ZoneOffset.UTC)
        logger.info("Polling all water companies")

        val overflows = overflowRepository.allOverflows(UK_BOUNDS)
        val companies = overflows.map { it.company }.toSet()
        val sites = overflows.map { it.toSiteRow() }
        val readings = overflows.map { it.toReadingRow(polledAt) }

        transaction(database) {
            siteRepository.upsertSites(sites)
            readingRepository.insertReadings(readings)
        }

        val durationMs = System.currentTimeMillis() - startMs
        lastPollAt = polledAt
        lastPollDurationMs = durationMs
        lastCompaniesPolled = companies.size
        lastCompaniesFailed = 0

        logger.info("Polled ${overflows.size} overflows from ${companies.size} companies in ${durationMs}ms")
    }
}
