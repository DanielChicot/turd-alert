package com.chicot.turdalert.backend.api

import com.chicot.turdalert.backend.TestApp
import com.chicot.turdalert.db.repository.ReadingRow
import com.chicot.turdalert.db.repository.SiteRow
import com.chicot.turdalert.db.tables.SitesTable
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryRoutesTest {

    private val database = TestApp.database
    private val siteRepo = TestApp.siteRepository
    private val readingRepo = TestApp.readingRepository

    private val now = OffsetDateTime.now(ZoneOffset.UTC)

    private fun Application.configureTest() {
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        routing { historyRoutes(database, siteRepo, readingRepo) }
    }

    @BeforeTest
    fun setup() {
        TestApp.ensurePartitions()
        transaction(database) {
            TransactionManager.current().exec("DELETE FROM readings")
            SitesTable.deleteAll()
        }
    }

    @Test
    fun `history endpoint returns site history with stats`() = testApplication {
        application { configureTest() }

        transaction(database) {
            siteRepo.upsertSites(
                listOf(SiteRow("thames", "S001", "Test CSO", "River Thames", 51.5, -0.1))
            )
            val readings = (0L until 8L).map { i ->
                ReadingRow("thames", "S001", now.minusHours(8 - i), if (i in 2L..4L) 1 else 0)
            }
            readingRepo.insertReadings(readings)
        }

        val response = client.get("/api/v1/sites/thames/S001/history?days=1")
        assertEquals(HttpStatusCode.OK, response.status)

        val history = Json.decodeFromString<HistoryResponse>(response.bodyAsText())
        assertEquals("S001", history.site.id)
        assertEquals("thames", history.site.company)
        assertEquals("Test CSO", history.site.siteName)
        assertTrue(history.stats.totalDischargeHours > 0)
        assertEquals(1, history.stats.eventCount)
        assertTrue(history.timeline.isNotEmpty())
    }

    @Test
    fun `history endpoint returns 404 for unknown site`() = testApplication {
        application { configureTest() }

        val response = client.get("/api/v1/sites/unknown/X999/history")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
