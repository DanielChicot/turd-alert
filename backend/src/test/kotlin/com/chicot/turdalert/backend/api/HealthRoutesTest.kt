package com.chicot.turdalert.backend.api

import com.chicot.turdalert.backend.TestApp
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthRoutesTest {

    private val database = TestApp.database

    private fun Application.configureTest() {
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        routing { healthRoutes(database, System.currentTimeMillis(), { null }) }
    }

    @Test
    fun `health endpoint returns ok when database is reachable`() = testApplication {
        application { configureTest() }

        val response = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("ok", body.status)
        assertTrue(body.databaseReachable)
        assertEquals(0, body.companiesPolled)
        assertEquals(0, body.companiesFailed)
        assertEquals(0L, body.lastPollDurationMs)
    }
}
