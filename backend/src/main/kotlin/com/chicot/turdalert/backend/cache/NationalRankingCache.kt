package com.chicot.turdalert.backend.cache

import com.chicot.turdalert.backend.api.SiteResponse
import com.chicot.turdalert.backend.api.StatsResponse
import com.chicot.turdalert.backend.api.WorstOffenderResponse
import com.chicot.turdalert.db.repository.ReadingRepository
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

private const val REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L
private const val DAYS = 7
private const val LIMIT = 10

class NationalRankingCache(
    private val readingRepository: ReadingRepository,
    private val database: Database
) {
    private val log = LoggerFactory.getLogger(NationalRankingCache::class.java)
    private val cached = AtomicReference<List<WorstOffenderResponse>>(emptyList())

    fun current(): List<WorstOffenderResponse> = cached.get()

    suspend fun refreshForever() {
        while (true) {
            refresh()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    private fun refresh() {
        log.info("Refreshing national ranking cache")
        val start = System.currentTimeMillis()
        val from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(DAYS.toLong())

        val results = transaction(database) {
            readingRepository.topOffendersNational(from, LIMIT).map { ranked ->
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

        cached.set(results)
        log.info("National ranking cache refreshed in {}ms, {} entries", System.currentTimeMillis() - start, results.size)
    }
}
