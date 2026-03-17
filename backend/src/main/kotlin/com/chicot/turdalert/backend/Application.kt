package com.chicot.turdalert.backend

import com.chicot.turdalert.api.createOverflowRepository
import com.chicot.turdalert.backend.api.healthRoutes
import com.chicot.turdalert.backend.api.historyRoutes
import com.chicot.turdalert.backend.api.overflowRoutes
import com.chicot.turdalert.backend.api.worstOffendersRoutes
import com.chicot.turdalert.backend.poller.Poller
import com.chicot.turdalert.db.DatabaseConfig
import com.chicot.turdalert.db.connectAndMigrate
import com.chicot.turdalert.db.partition.PartitionManager
import com.chicot.turdalert.db.repository.ReadingRepository
import com.chicot.turdalert.db.repository.SiteRepository
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

fun main() {
    val startTime = System.currentTimeMillis()

    val dbConfig = DatabaseConfig(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/turdalert",
        user = System.getenv("DATABASE_USER") ?: "turdalert",
        password = System.getenv("DATABASE_PASSWORD") ?: "turdalert"
    )

    val database = connectAndMigrate(dbConfig)
    val siteRepository = SiteRepository()
    val readingRepository = ReadingRepository()
    val partitionManager = PartitionManager()

    val overflowRepository = createOverflowRepository()
    val poller = Poller(overflowRepository, siteRepository, readingRepository, partitionManager, database)

    embeddedServer(CIO, port = 8080) {
        install(CallLogging)
        install(ContentNegotiation) {
            json(Json { prettyPrint = false })
        }

        routing {
            healthRoutes(database, startTime) { poller }
            overflowRoutes(database, readingRepository)
            historyRoutes(database, siteRepository, readingRepository)
            worstOffendersRoutes(database, siteRepository, readingRepository)
        }

        launch { poller.runForever() }
    }.start(wait = true)
}
