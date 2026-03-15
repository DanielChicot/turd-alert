package com.chicot.turdalert.db

import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.PostgreSQLContainer

object TestDatabase {
    private val container = PostgreSQLContainer("postgres:16-alpine").apply {
        withDatabaseName("turdalert_test")
        withUsername("test")
        withPassword("test")
        start()
    }

    val database: Database by lazy {
        connectAndMigrate(
            DatabaseConfig(
                url = container.jdbcUrl,
                user = container.username,
                password = container.password
            )
        )
    }
}
