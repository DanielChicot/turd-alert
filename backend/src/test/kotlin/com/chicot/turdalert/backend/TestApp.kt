package com.chicot.turdalert.backend

import com.chicot.turdalert.db.DatabaseConfig
import com.chicot.turdalert.db.connectAndMigrate
import com.chicot.turdalert.db.partition.PartitionManager
import com.chicot.turdalert.db.repository.ReadingRepository
import com.chicot.turdalert.db.repository.SiteRepository
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.PostgreSQLContainer
import java.time.OffsetDateTime
import java.time.ZoneOffset

object TestApp {
    private val container = PostgreSQLContainer("postgres:16-alpine").apply {
        withDatabaseName("turdalert_test")
        withUsername("test")
        withPassword("test")
        start()
    }

    val database: Database = connectAndMigrate(
        DatabaseConfig(url = container.jdbcUrl, user = container.username, password = container.password)
    )

    val siteRepository = SiteRepository()
    val readingRepository = ReadingRepository()
    val partitionManager = PartitionManager()

    fun ensurePartitions(referenceTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)) {
        org.jetbrains.exposed.sql.transactions.transaction(database) {
            partitionManager.ensurePartitions(referenceTime)
        }
    }
}
