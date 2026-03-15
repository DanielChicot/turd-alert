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

class WorstOffendersRoutesTest {

    private val database = TestApp.database
    private val siteRepo = TestApp.siteRepository
    private val readingRepo = TestApp.readingRepository

    private val now = OffsetDateTime.now(ZoneOffset.UTC)
    private val centerLat = 51.5
    private val centerLon = -0.1

    private fun Application.configureTest() {
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        routing { worstOffendersRoutes(database, siteRepo, readingRepo) }
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
    fun `worst offenders returns sites ranked by discharge hours`() = testApplication {
        application { configureTest() }

        transaction(database) {
            siteRepo.upsertSites(
                listOf(
                    SiteRow("thames", "NEAR1", "Near Site 1", "River Thames", 51.501, -0.101),
                    SiteRow("thames", "NEAR2", "Near Site 2", "River Lea", 51.502, -0.102),
                    SiteRow("thames", "FAR", "Far Site", "River Severn", 55.0, -3.0)
                )
            )

            val near1Readings = (0L until 10L).map { i ->
                ReadingRow("thames", "NEAR1", now.minusHours(10 - i), if (i < 6) 1 else 0)
            }
            val near2Readings = (0L until 10L).map { i ->
                ReadingRow("thames", "NEAR2", now.minusHours(10 - i), if (i < 3) 1 else 0)
            }
            val farReadings = (0L until 10L).map { i ->
                ReadingRow("thames", "FAR", now.minusHours(10 - i), 1)
            }

            readingRepo.insertReadings(near1Readings + near2Readings + farReadings)
        }

        val response = client.get("/api/v1/sites/worst-offenders?lat=$centerLat&lon=$centerLon&radius=5&days=30")
        assertEquals(HttpStatusCode.OK, response.status)

        val offenders = Json.decodeFromString<List<WorstOffenderResponse>>(response.bodyAsText())
        assertEquals(2, offenders.size)
        assertEquals("NEAR1", offenders[0].site.id)
        assertEquals("NEAR2", offenders[1].site.id)
        assertTrue(offenders[0].stats.totalDischargeHours > offenders[1].stats.totalDischargeHours)
    }

    @Test
    fun `worst offenders returns 400 when lat or lon missing`() = testApplication {
        application { configureTest() }

        val response = client.get("/api/v1/sites/worst-offenders")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `worst offenders excludes far away sites`() = testApplication {
        application { configureTest() }

        transaction(database) {
            siteRepo.upsertSites(
                listOf(SiteRow("thames", "FAR", "Far Site", "River Severn", 55.0, -3.0))
            )
            readingRepo.insertReadings(
                listOf(ReadingRow("thames", "FAR", now.minusHours(1), 1))
            )
        }

        val response = client.get("/api/v1/sites/worst-offenders?lat=$centerLat&lon=$centerLon&radius=1&days=30")
        val offenders = Json.decodeFromString<List<WorstOffenderResponse>>(response.bodyAsText())
        assertTrue(offenders.isEmpty())
    }
}
