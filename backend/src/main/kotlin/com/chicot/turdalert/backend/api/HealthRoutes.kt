package com.chicot.turdalert.backend.api

import com.chicot.turdalert.backend.poller.Poller
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.healthRoutes(database: Database, startTime: Long, pollerProvider: () -> Poller?) {
    get("/api/v1/health") {
        val poller = pollerProvider()
        val dbReachable = try {
            transaction(database) { exec("SELECT 1") }
            true
        } catch (_: Exception) {
            false
        }

        call.respond(
            HealthResponse(
                status = if (dbReachable) "ok" else "degraded",
                lastPollAt = poller?.lastPollAt?.toString(),
                lastPollDurationMs = poller?.lastPollDurationMs ?: 0,
                companiesPolled = poller?.lastCompaniesPolled ?: 0,
                companiesFailed = poller?.lastCompaniesFailed ?: 0,
                databaseReachable = dbReachable,
                uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000
            )
        )
    }
}
