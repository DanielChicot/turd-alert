package com.chicot.turdalert.db.repository

import com.chicot.turdalert.db.TestDatabase
import com.chicot.turdalert.db.partition.PartitionManager
import com.chicot.turdalert.db.tables.SitesTable
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingRepositoryTest {

    private val db = TestDatabase.database
    private val readingRepo = ReadingRepository()
    private val siteRepo = SiteRepository()
    private val partitionManager = PartitionManager()

    private val baseTime = OffsetDateTime.of(2026, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC)
    private val testSite = SiteRow("thames", "S001", "Test Site", "River Thames", 51.5, -0.1)

    @BeforeTest
    fun setup() {
        transaction(db) {
            partitionManager.ensurePartitions(baseTime)
            TransactionManager.current().exec("DELETE FROM readings")
            SitesTable.deleteAll()
            siteRepo.upsertSites(listOf(testSite))
        }
    }

    @Test
    fun `insertReadings writes rows`() {
        transaction(db) {
            readingRepo.insertReadings(
                listOf(ReadingRow("thames", "S001", baseTime, 0))
            )
            val history = readingRepo.siteHistory("thames", "S001", baseTime.minusDays(1), baseTime.plusDays(1))
            assertEquals(1, history.size)
        }
    }

    @Test
    fun `insertReadings is idempotent`() {
        transaction(db) {
            val reading = ReadingRow("thames", "S001", baseTime, 1)
            readingRepo.insertReadings(listOf(reading))
            readingRepo.insertReadings(listOf(reading))
            val history = readingRepo.siteHistory("thames", "S001", baseTime.minusDays(1), baseTime.plusDays(1))
            assertEquals(1, history.size)
        }
    }

    @Test
    fun `latestReadings returns most recent reading per site`() {
        transaction(db) {
            val earlier = baseTime.minusHours(1)
            val later = baseTime
            readingRepo.insertReadings(
                listOf(
                    ReadingRow("thames", "S001", earlier, 0),
                    ReadingRow("thames", "S001", later, 1)
                )
            )
            val results = readingRepo.latestReadings(50.0, 53.0, -1.0, 1.0)
            assertEquals(1, results.size)
            assertEquals(later, results.first().polledAt)
            assertEquals(1.toShort(), results.first().status)
            assertEquals("Test Site", results.first().siteName)
        }
    }

    @Test
    fun `siteHistory returns readings in time range`() {
        transaction(db) {
            val readings = (0L until 10L).map { i ->
                ReadingRow("thames", "S001", baseTime.plusHours(i), 0)
            }
            readingRepo.insertReadings(readings)

            val subset = readingRepo.siteHistory(
                "thames", "S001",
                baseTime.plusHours(2), baseTime.plusHours(5)
            )
            assertEquals(4, subset.size)
            assertEquals(baseTime.plusHours(2), subset.first().polledAt)
            assertEquals(baseTime.plusHours(5), subset.last().polledAt)
        }
    }

    @Test
    fun `siteStats computes discharge statistics`() {
        transaction(db) {
            val readings = listOf(
                ReadingRow("thames", "S001", baseTime, 0),
                ReadingRow("thames", "S001", baseTime.plusHours(1), 1),
                ReadingRow("thames", "S001", baseTime.plusHours(2), 1),
                ReadingRow("thames", "S001", baseTime.plusHours(3), 0),
                ReadingRow("thames", "S001", baseTime.plusHours(4), 1)
            )
            readingRepo.insertReadings(readings)

            val stats = readingRepo.siteStats(
                "thames", "S001",
                baseTime.minusDays(1), baseTime.plusDays(1)
            )
            assertEquals(3, stats.dischargingReadings)
            assertEquals(5, stats.totalReadings)
            assertEquals(baseTime.plusHours(4), stats.lastDischargeAt)
        }
    }
}
