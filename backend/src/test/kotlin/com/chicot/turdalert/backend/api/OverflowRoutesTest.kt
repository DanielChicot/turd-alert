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

class OverflowRoutesTest {

    private val database = TestApp.database
    private val siteRepo = TestApp.siteRepository
    private val readingRepo = TestApp.readingRepository

    private val baseTime = OffsetDateTime.of(2026, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC)

    private fun Application.configureTest() {
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        routing { overflowRoutes(database, readingRepo) }
    }

    @BeforeTest
    fun setup() {
        TestApp.ensurePartitions(baseTime)
        TestApp.ensurePartitions()
        transaction(database) {
            TransactionManager.current().exec("DELETE FROM readings")
            SitesTable.deleteAll()
        }
    }

    @Test
    fun `overflows endpoint returns sites in bounds`() = testApplication {
        application { configureTest() }

        transaction(database) {
            siteRepo.upsertSites(
                listOf(
                    SiteRow("thames", "S001", "Riverside CSO", "River Thames", 51.5, -0.1),
                    SiteRow("thames", "S002", "Upstream CSO", "River Lea", 51.6, -0.2)
                )
            )
            readingRepo.insertReadings(
                listOf(
                    ReadingRow("thames", "S001", baseTime, 1),
                    ReadingRow("thames", "S002", baseTime, 0)
                )
            )
        }

        val response = client.get("/api/v1/overflows?minLat=51.0&maxLat=52.0&minLon=-1.0&maxLon=1.0")
        assertEquals(HttpStatusCode.OK, response.status)

        val overflows = Json.decodeFromString<List<OverflowResponse>>(response.bodyAsText())
        assertEquals(2, overflows.size)

        val discharging = overflows.first { it.id == "S001" }
        assertEquals("DISCHARGING", discharging.status)
        assertEquals("Riverside CSO", discharging.siteName)

        val notDischarging = overflows.first { it.id == "S002" }
        assertEquals("NOT_DISCHARGING", notDischarging.status)
    }

    @Test
    fun `overflows endpoint returns 400 when parameters missing`() = testApplication {
        application { configureTest() }

        val response = client.get("/api/v1/overflows")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `overflows endpoint derives RECENT_DISCHARGE status`() = testApplication {
        application { configureTest() }

        val recentStop = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30)

        transaction(database) {
            siteRepo.upsertSites(
                listOf(SiteRow("thames", "S003", "Recent CSO", "River Thames", 51.5, -0.1))
            )
            readingRepo.insertReadings(
                listOf(ReadingRow("thames", "S003", recentStop, 0, recentStop))
            )
        }

        val response = client.get("/api/v1/overflows?minLat=51.0&maxLat=52.0&minLon=-1.0&maxLon=1.0")
        val overflows = Json.decodeFromString<List<OverflowResponse>>(response.bodyAsText())
        val recent = overflows.first { it.id == "S003" }
        assertEquals("RECENT_DISCHARGE", recent.status)
    }
}
