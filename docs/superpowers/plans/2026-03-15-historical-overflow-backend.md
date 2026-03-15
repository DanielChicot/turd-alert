# Historical Overflow Backend Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a backend service that polls all 10 UK water company APIs every 15 minutes, stores snapshots in Postgres, and serves historical data via REST.

**Architecture:** Two new Gradle modules (`db` and `backend`) added to the existing KMP project. The `shared` module gets a JVM target so the backend can reuse existing API client code. Deployed as two Docker containers (backend JAR + Postgres) on a Synology NAS.

**Tech Stack:** Kotlin/JVM, Ktor Server 3.1.1, Exposed 0.57.0, Flyway, Postgres 16, HikariCP, Testcontainers, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-03-15-historical-overflow-data-design.md`

**Scope:** Backend only. App integration (hybrid fetching, historical UI) is a separate plan.

---

## File Map

**Modified:**
- `gradle/libs.versions.toml` — add server/db/test dependencies
- `settings.gradle.kts` — include `:db` and `:backend`
- `shared/build.gradle.kts` — add `jvm()` target

**Created (shared JVM actuals):**
- `shared/src/jvmMain/kotlin/com/chicot/turdalert/Platform.jvm.kt`
- `shared/src/jvmMain/kotlin/com/chicot/turdalert/map/Directions.jvm.kt`

**Created (db module):**
- `db/build.gradle.kts`
- `db/src/main/kotlin/com/chicot/turdalert/db/DatabaseFactory.kt`
- `db/src/main/kotlin/com/chicot/turdalert/db/tables/SitesTable.kt`
- `db/src/main/kotlin/com/chicot/turdalert/db/tables/ReadingsTable.kt`
- `db/src/main/kotlin/com/chicot/turdalert/db/repository/SiteRepository.kt`
- `db/src/main/kotlin/com/chicot/turdalert/db/repository/ReadingRepository.kt`
- `db/src/main/kotlin/com/chicot/turdalert/db/repository/StatsCalculator.kt`
- `db/src/main/kotlin/com/chicot/turdalert/db/partition/PartitionManager.kt`
- `db/src/main/resources/db/migration/V1__create_sites_table.sql`
- `db/src/main/resources/db/migration/V2__create_readings_table.sql`
- `db/src/test/kotlin/com/chicot/turdalert/db/TestDatabase.kt`
- `db/src/test/kotlin/com/chicot/turdalert/db/repository/SiteRepositoryTest.kt`
- `db/src/test/kotlin/com/chicot/turdalert/db/repository/ReadingRepositoryTest.kt`
- `db/src/test/kotlin/com/chicot/turdalert/db/partition/PartitionManagerTest.kt`

**Created (backend module):**
- `backend/build.gradle.kts`
- `backend/src/main/kotlin/com/chicot/turdalert/backend/Application.kt`
- `backend/src/main/kotlin/com/chicot/turdalert/backend/poller/Poller.kt`
- `backend/src/main/kotlin/com/chicot/turdalert/backend/api/Models.kt`
- `backend/src/main/kotlin/com/chicot/turdalert/backend/api/OverflowRoutes.kt`
- `backend/src/main/kotlin/com/chicot/turdalert/backend/api/HistoryRoutes.kt`
- `backend/src/main/kotlin/com/chicot/turdalert/backend/api/WorstOffendersRoutes.kt`
- `backend/src/main/kotlin/com/chicot/turdalert/backend/api/HealthRoutes.kt`
- `backend/src/main/kotlin/com/chicot/turdalert/backend/api/StatsCalculator.kt`
- `backend/src/main/resources/logback.xml`
- `backend/src/test/kotlin/com/chicot/turdalert/backend/poller/PollerTest.kt`
- `backend/src/test/kotlin/com/chicot/turdalert/backend/api/OverflowRoutesTest.kt`
- `backend/src/test/kotlin/com/chicot/turdalert/backend/api/HistoryRoutesTest.kt`
- `backend/src/test/kotlin/com/chicot/turdalert/backend/api/WorstOffendersRoutesTest.kt`
- `backend/src/test/kotlin/com/chicot/turdalert/backend/api/HealthRoutesTest.kt`
- `backend/src/test/kotlin/com/chicot/turdalert/backend/TestApp.kt`

**Created (Docker):**
- `Dockerfile`
- `docker-compose.yml`
- `.env.example`

---

## Chunk 1: Project Setup & Database Layer

### Task 1: Add dependencies to version catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add new versions and libraries**

Add to `[versions]`:

```toml
exposed = "0.57.0"
hikari = "6.2.1"
flyway = "10.22.0"
postgresql = "42.7.4"
logback = "1.5.12"
testcontainers = "1.20.4"
```

Add to `[libraries]`:

```toml
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-cio = { module = "io.ktor:ktor-server-cio", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-server-test-host = { module = "io.ktor:ktor-server-test-host", version.ref = "ktor" }
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
exposed-java-time = { module = "org.jetbrains.exposed:exposed-java-time", version.ref = "exposed" }
hikari = { module = "com.zaxxer:HikariCP", version.ref = "hikari" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-postgresql = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgresql" }
logback = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
testcontainers-junit = { module = "org.testcontainers:junit-jupiter", version.ref = "testcontainers" }
```

Add to `[plugins]`:

```toml
shadow = { id = "com.gradleup.shadow", version = "8.3.5" }
```

- [ ] **Step 2: Verify catalog parses**

Run: `./gradlew help`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "Add backend dependencies to version catalog"
```

---

### Task 2: Add JVM target to shared module

**Files:**
- Modify: `shared/build.gradle.kts`
- Create: `shared/src/jvmMain/kotlin/com/chicot/turdalert/Platform.jvm.kt`
- Create: `shared/src/jvmMain/kotlin/com/chicot/turdalert/map/Directions.jvm.kt`

- [ ] **Step 1: Add jvm target and dependencies to shared/build.gradle.kts**

Add `jvm()` after the `androidTarget` block. Add `jvmMain` dependencies:

```kotlin
jvm()
```

In the `sourceSets` block, add:

```kotlin
jvmMain.dependencies {
    implementation(libs.ktor.client.cio)
}
```

- [ ] **Step 2: Create JVM actual for platform()**

File: `shared/src/jvmMain/kotlin/com/chicot/turdalert/Platform.jvm.kt`

```kotlin
package com.chicot.turdalert

actual fun platform(): String = "JVM"
```

- [ ] **Step 3: Create JVM actual for openDirections()**

File: `shared/src/jvmMain/kotlin/com/chicot/turdalert/map/Directions.jvm.kt`

```kotlin
package com.chicot.turdalert.map

actual fun openDirections(latitude: Double, longitude: Double) {
    throw UnsupportedOperationException("Directions not supported on JVM")
}
```

- [ ] **Step 4: Verify JVM target compiles**

Run: `./gradlew :shared:jvmJar`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add shared/build.gradle.kts shared/src/jvmMain/
git commit -m "Add JVM target to shared module for backend reuse"
```

---

### Task 3: Create db module

**Files:**
- Create: `db/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create db/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    kotlin("jvm")
}

dependencies {
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Add db module to settings.gradle.kts**

Add at the end:

```kotlin
include(":db")
```

- [ ] **Step 3: Verify module resolves**

Run: `./gradlew :db:dependencies`
Expected: BUILD SUCCESSFUL (may warn about no sources yet)

- [ ] **Step 4: Commit**

```bash
git add db/build.gradle.kts settings.gradle.kts
git commit -m "Add db module with Exposed, Flyway, and Postgres dependencies"
```

---

### Task 4: Create backend module skeleton

**Files:**
- Create: `backend/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create backend/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":db"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.logback)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.jdbc)
    testImplementation(libs.exposed.java.time)
    testImplementation(libs.hikari)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.postgresql)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("all")
    manifest {
        attributes("Main-Class" to "com.chicot.turdalert.backend.ApplicationKt")
    }
}
```

- [ ] **Step 2: Add backend module to settings.gradle.kts**

Add:

```kotlin
include(":backend")
```

- [ ] **Step 3: Verify module resolves**

Run: `./gradlew :backend:dependencies`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/build.gradle.kts settings.gradle.kts
git commit -m "Add backend module with Ktor server and shadow JAR config"
```

---

### Task 5: Flyway migrations

**Files:**
- Create: `db/src/main/resources/db/migration/V1__create_sites_table.sql`
- Create: `db/src/main/resources/db/migration/V2__create_readings_table.sql`

- [ ] **Step 1: Create sites table migration**

File: `db/src/main/resources/db/migration/V1__create_sites_table.sql`

```sql
create table sites (
    company     text not null,
    site_id     text not null,
    site_name   text,
    watercourse text,
    latitude    double precision not null,
    longitude   double precision not null,

    primary key (company, site_id)
);

create index idx_sites_location on sites (latitude, longitude);
```

- [ ] **Step 2: Create readings table migration**

File: `db/src/main/resources/db/migration/V2__create_readings_table.sql`

```sql
create table readings (
    company      text not null,
    site_id      text not null,
    polled_at    timestamptz not null,
    status       smallint not null,
    status_start timestamptz,

    primary key (company, site_id, polled_at),
    foreign key (company, site_id) references sites(company, site_id)
) partition by range (polled_at);
```

- [ ] **Step 3: Commit**

```bash
git add db/src/main/resources/
git commit -m "Add Flyway migrations for sites and readings tables"
```

---

### Task 6: Exposed table definitions

**Files:**
- Create: `db/src/main/kotlin/com/chicot/turdalert/db/tables/SitesTable.kt`
- Create: `db/src/main/kotlin/com/chicot/turdalert/db/tables/ReadingsTable.kt`

- [ ] **Step 1: Create SitesTable**

```kotlin
package com.chicot.turdalert.db.tables

import org.jetbrains.exposed.sql.Table

object SitesTable : Table("sites") {
    val company = text("company")
    val siteId = text("site_id")
    val siteName = text("site_name").nullable()
    val watercourse = text("watercourse").nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")

    override val primaryKey = PrimaryKey(company, siteId)
}
```

- [ ] **Step 2: Create ReadingsTable**

```kotlin
package com.chicot.turdalert.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object ReadingsTable : Table("readings") {
    val company = text("company")
    val siteId = text("site_id")
    val polledAt = timestampWithTimeZone("polled_at")
    val status = short("status")
    val statusStart = timestampWithTimeZone("status_start").nullable()

    override val primaryKey = PrimaryKey(company, siteId, polledAt)
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :db:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add db/src/main/kotlin/
git commit -m "Add Exposed table definitions for sites and readings"
```

---

### Task 7: DatabaseFactory

**Files:**
- Create: `db/src/main/kotlin/com/chicot/turdalert/db/DatabaseFactory.kt`

- [ ] **Step 1: Create DatabaseFactory**

```kotlin
package com.chicot.turdalert.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int = 5
)

fun connectAndMigrate(config: DatabaseConfig): Database {
    val dataSource = hikariDataSource(config)
    migrate(dataSource)
    return Database.connect(dataSource)
}

private fun hikariDataSource(config: DatabaseConfig): HikariDataSource =
    HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password
        maximumPoolSize = config.maxPoolSize
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
    })

private fun migrate(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :db:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add db/src/main/kotlin/com/chicot/turdalert/db/DatabaseFactory.kt
git commit -m "Add DatabaseFactory with HikariCP and Flyway migration"
```

---

### Task 8: Test infrastructure

**Files:**
- Create: `db/src/test/kotlin/com/chicot/turdalert/db/TestDatabase.kt`

- [ ] **Step 1: Create shared test database utility**

```kotlin
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
```

- [ ] **Step 2: Verify test infrastructure compiles**

Run: `./gradlew :db:compileTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add db/src/test/
git commit -m "Add Testcontainers-based test database utility"
```

---

### Task 9: SiteRepository + tests

**Files:**
- Create: `db/src/main/kotlin/com/chicot/turdalert/db/repository/SiteRepository.kt`
- Create: `db/src/test/kotlin/com/chicot/turdalert/db/repository/SiteRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

File: `db/src/test/kotlin/com/chicot/turdalert/db/repository/SiteRepositoryTest.kt`

```kotlin
package com.chicot.turdalert.db.repository

import com.chicot.turdalert.db.TestDatabase
import com.chicot.turdalert.db.tables.SitesTable
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SiteRepositoryTest {
    private val db = TestDatabase.database
    private val repository = SiteRepository()

    @BeforeTest
    fun cleanup() {
        transaction(db) {
            exec("DELETE FROM readings")
            SitesTable.deleteAll()
        }
    }

    @Test
    fun `upsertSites inserts new sites`() {
        val sites = listOf(
            SiteRow("THAMES", "P001", "Site A", "River Thames", 51.5, -0.1),
            SiteRow("SOUTHERN", "P002", "Site B", "River Test", 50.9, -1.4)
        )

        transaction(db) { repository.upsertSites(sites) }

        val rows = transaction(db) { SitesTable.selectAll().toList() }
        assertEquals(2, rows.size)
    }

    @Test
    fun `upsertSites updates existing site metadata`() {
        val original = listOf(SiteRow("THAMES", "P001", "Old Name", "Old River", 51.5, -0.1))
        val updated = listOf(SiteRow("THAMES", "P001", "New Name", "New River", 51.6, -0.2))

        transaction(db) { repository.upsertSites(original) }
        transaction(db) { repository.upsertSites(updated) }

        val row = transaction(db) { SitesTable.selectAll().single() }
        assertEquals("New Name", row[SitesTable.siteName])
        assertEquals(51.6, row[SitesTable.latitude])
    }

    @Test
    fun `sitesInBounds returns sites within bounding box`() {
        val sites = listOf(
            SiteRow("THAMES", "P001", "Inside", "River", 51.5, -0.1),
            SiteRow("THAMES", "P002", "Outside", "River", 55.0, -3.0)
        )

        transaction(db) { repository.upsertSites(sites) }

        val result = transaction(db) { repository.sitesInBounds(51.0, 52.0, -1.0, 1.0) }
        assertEquals(1, result.size)
        assertEquals("P001", result.first().siteId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :db:test --tests "*.SiteRepositoryTest"`
Expected: Compilation error — `SiteRepository` and `SiteRow` don't exist

- [ ] **Step 3: Implement SiteRepository**

File: `db/src/main/kotlin/com/chicot/turdalert/db/repository/SiteRepository.kt`

```kotlin
package com.chicot.turdalert.db.repository

import com.chicot.turdalert.db.tables.SitesTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

data class SiteRow(
    val company: String,
    val siteId: String,
    val siteName: String?,
    val watercourse: String?,
    val latitude: Double,
    val longitude: Double
)

class SiteRepository {

    fun upsertSites(sites: List<SiteRow>) {
        sites.forEach { site ->
            SitesTable.upsert {
                it[company] = site.company
                it[siteId] = site.siteId
                it[siteName] = site.siteName
                it[watercourse] = site.watercourse
                it[latitude] = site.latitude
                it[longitude] = site.longitude
            }
        }
    }

    fun findSite(company: String, siteId: String): SiteRow? =
        SitesTable.selectAll()
            .where { (SitesTable.company eq company) and (SitesTable.siteId eq siteId) }
            .map { it.toSiteRow() }
            .singleOrNull()

    fun sitesInBounds(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<SiteRow> =
        SitesTable.selectAll()
            .where {
                (SitesTable.latitude greaterEq minLat) and
                    (SitesTable.latitude lessEq maxLat) and
                    (SitesTable.longitude greaterEq minLon) and
                    (SitesTable.longitude lessEq maxLon)
            }
            .map { it.toSiteRow() }
}

private fun org.jetbrains.exposed.sql.ResultRow.toSiteRow() = SiteRow(
    company = this[SitesTable.company],
    siteId = this[SitesTable.siteId],
    siteName = this[SitesTable.siteName],
    watercourse = this[SitesTable.watercourse],
    latitude = this[SitesTable.latitude],
    longitude = this[SitesTable.longitude]
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :db:test --tests "*.SiteRepositoryTest"`
Expected: 3 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add db/src/main/kotlin/com/chicot/turdalert/db/repository/SiteRepository.kt \
        db/src/test/kotlin/com/chicot/turdalert/db/repository/SiteRepositoryTest.kt
git commit -m "Add SiteRepository with upsert and spatial query"
```

---

### Task 10: ReadingRepository + tests

**Files:**
- Create: `db/src/main/kotlin/com/chicot/turdalert/db/repository/ReadingRepository.kt`
- Create: `db/src/test/kotlin/com/chicot/turdalert/db/repository/ReadingRepositoryTest.kt`

- [ ] **Step 1: Write failing tests**

File: `db/src/test/kotlin/com/chicot/turdalert/db/repository/ReadingRepositoryTest.kt`

```kotlin
package com.chicot.turdalert.db.repository

import com.chicot.turdalert.db.TestDatabase
import com.chicot.turdalert.db.partition.PartitionManager
import com.chicot.turdalert.db.tables.ReadingsTable
import com.chicot.turdalert.db.tables.SitesTable
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingRepositoryTest {
    private val db = TestDatabase.database
    private val siteRepo = SiteRepository()
    private val readingRepo = ReadingRepository()
    private val partitionManager = PartitionManager()

    private val now = OffsetDateTime.of(2026, 3, 15, 12, 0, 0, 0, ZoneOffset.UTC)

    @BeforeTest
    fun setup() {
        transaction(db) {
            exec("DELETE FROM readings")
            SitesTable.deleteAll()
            partitionManager.ensurePartitions(now)
        }
        transaction(db) {
            siteRepo.upsertSites(
                listOf(SiteRow("THAMES", "P001", "Test Site", "River Thames", 51.5, -0.1))
            )
        }
    }

    @Test
    fun `insertReadings writes rows`() {
        val readings = listOf(
            ReadingRow("THAMES", "P001", now, 1, now.minusHours(2))
        )

        transaction(db) { readingRepo.insertReadings(readings) }

        val count = transaction(db) {
            ReadingsTable.selectAll().where {
                (ReadingsTable.company eq "THAMES") and (ReadingsTable.siteId eq "P001")
            }.count()
        }
        assertEquals(1, count)
    }

    @Test
    fun `insertReadings is idempotent`() {
        val readings = listOf(
            ReadingRow("THAMES", "P001", now, 1, now.minusHours(2))
        )

        transaction(db) { readingRepo.insertReadings(readings) }
        transaction(db) { readingRepo.insertReadings(readings) }

        val count = transaction(db) {
            ReadingsTable.selectAll().where {
                (ReadingsTable.company eq "THAMES") and (ReadingsTable.siteId eq "P001")
            }.count()
        }
        assertEquals(1, count)
    }

    @Test
    fun `latestReadings returns most recent reading per site`() {
        val t1 = now.minusMinutes(30)
        val t2 = now

        transaction(db) {
            readingRepo.insertReadings(listOf(
                ReadingRow("THAMES", "P001", t1, 0, null),
                ReadingRow("THAMES", "P001", t2, 1, t2.minusMinutes(5))
            ))
        }

        val latest = transaction(db) {
            readingRepo.latestReadings(51.0, 52.0, -1.0, 1.0)
        }

        assertEquals(1, latest.size)
        assertEquals(1, latest.first().status)
        assertEquals(t2, latest.first().polledAt)
    }

    @Test
    fun `siteHistory returns readings in time range`() {
        val readings = (0 until 10).map { i ->
            ReadingRow("THAMES", "P001", now.minusMinutes(i.toLong() * 15), if (i < 3) 1 else 0, null)
        }

        transaction(db) { readingRepo.insertReadings(readings) }

        val history = transaction(db) {
            readingRepo.siteHistory("THAMES", "P001", now.minusHours(3), now)
        }

        assertEquals(10, history.size)
    }

    @Test
    fun `siteStats computes discharge statistics`() {
        val readings = listOf(
            ReadingRow("THAMES", "P001", now.minusMinutes(60), 1, now.minusMinutes(60)),
            ReadingRow("THAMES", "P001", now.minusMinutes(45), 1, now.minusMinutes(60)),
            ReadingRow("THAMES", "P001", now.minusMinutes(30), 0, now.minusMinutes(30)),
            ReadingRow("THAMES", "P001", now.minusMinutes(15), 0, now.minusMinutes(30)),
            ReadingRow("THAMES", "P001", now, 0, now.minusMinutes(30))
        )

        transaction(db) { readingRepo.insertReadings(readings) }

        val stats = transaction(db) {
            readingRepo.siteStats("THAMES", "P001", now.minusHours(2), now)
        }

        assertEquals(2, stats.dischargingReadings)
        assertEquals(5, stats.totalReadings)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :db:test --tests "*.ReadingRepositoryTest"`
Expected: Compilation error — `ReadingRepository`, `ReadingRow`, `PartitionManager` don't exist

- [ ] **Step 3: Create PartitionManager first (needed by test setup)**

File: `db/src/main/kotlin/com/chicot/turdalert/db/partition/PartitionManager.kt`

```kotlin
package com.chicot.turdalert.db.partition

import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class PartitionManager {

    private val formatter = DateTimeFormatter.ofPattern("yyyy_MM")

    fun ensurePartitions(referenceTime: OffsetDateTime, monthsAhead: Int = 2) {
        val baseMonth = YearMonth.from(referenceTime)
        (0..monthsAhead).forEach { offset ->
            val month = baseMonth.plusMonths(offset.toLong())
            createPartitionIfNotExists(month)
        }
    }

    private fun createPartitionIfNotExists(month: YearMonth) {
        val name = "readings_${month.format(formatter)}"
        val from = month.atDay(1)
        val to = month.plusMonths(1).atDay(1)
        val sql = """
            CREATE TABLE IF NOT EXISTS $name
            PARTITION OF readings
            FOR VALUES FROM ('$from') TO ('$to')
        """.trimIndent()
        TransactionManager.current().exec(sql)
    }
}
```

- [ ] **Step 4: Implement ReadingRepository**

File: `db/src/main/kotlin/com/chicot/turdalert/db/repository/ReadingRepository.kt`

```kotlin
package com.chicot.turdalert.db.repository

import com.chicot.turdalert.db.tables.ReadingsTable
import com.chicot.turdalert.db.tables.SitesTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.time.OffsetDateTime

data class ReadingRow(
    val company: String,
    val siteId: String,
    val polledAt: OffsetDateTime,
    val status: Short,
    val statusStart: OffsetDateTime?
) {
    constructor(company: String, siteId: String, polledAt: OffsetDateTime, status: Int, statusStart: OffsetDateTime?)
        : this(company, siteId, polledAt, status.toShort(), statusStart)
}

data class SiteStats(
    val dischargingReadings: Long,
    val totalReadings: Long,
    val lastDischargeAt: OffsetDateTime?
)

data class LatestReading(
    val company: String,
    val siteId: String,
    val siteName: String?,
    val watercourse: String?,
    val latitude: Double,
    val longitude: Double,
    val polledAt: OffsetDateTime,
    val status: Short,
    val statusStart: OffsetDateTime?
)

class ReadingRepository {

    fun insertReadings(readings: List<ReadingRow>) {
        readings.forEach { r ->
            ReadingsTable.insertIgnore {
                it[company] = r.company
                it[siteId] = r.siteId
                it[polledAt] = r.polledAt
                it[status] = r.status
                it[statusStart] = r.statusStart
            }
        }
    }

    fun latestReadings(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<LatestReading> {
        val sql = """
            SELECT DISTINCT ON (r.company, r.site_id)
                r.company, r.site_id, s.site_name, s.watercourse,
                s.latitude, s.longitude, r.polled_at, r.status, r.status_start
            FROM readings r
            JOIN sites s ON r.company = s.company AND r.site_id = s.site_id
            WHERE s.latitude BETWEEN ? AND ?
              AND s.longitude BETWEEN ? AND ?
            ORDER BY r.company, r.site_id, r.polled_at DESC
        """.trimIndent()

        val conn = TransactionManager.current().connection.connection as java.sql.Connection
        val result = mutableListOf<LatestReading>()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setDouble(1, minLat)
            stmt.setDouble(2, maxLat)
            stmt.setDouble(3, minLon)
            stmt.setDouble(4, maxLon)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result.add(LatestReading(
                        company = rs.getString("company"),
                        siteId = rs.getString("site_id"),
                        siteName = rs.getString("site_name"),
                        watercourse = rs.getString("watercourse"),
                        latitude = rs.getDouble("latitude"),
                        longitude = rs.getDouble("longitude"),
                        polledAt = rs.getObject("polled_at", OffsetDateTime::class.java),
                        status = rs.getShort("status"),
                        statusStart = rs.getObject("status_start", OffsetDateTime::class.java)
                    ))
                }
            }
        }
        return result
    }

    fun siteHistory(
        company: String,
        siteId: String,
        from: OffsetDateTime,
        to: OffsetDateTime
    ): List<ReadingRow> =
        ReadingsTable.selectAll()
            .where {
                (ReadingsTable.company eq company) and
                    (ReadingsTable.siteId eq siteId) and
                    (ReadingsTable.polledAt greaterEq from) and
                    (ReadingsTable.polledAt lessEq to)
            }
            .orderBy(ReadingsTable.polledAt, SortOrder.ASC)
            .map { row ->
                ReadingRow(
                    company = row[ReadingsTable.company],
                    siteId = row[ReadingsTable.siteId],
                    polledAt = row[ReadingsTable.polledAt],
                    status = row[ReadingsTable.status],
                    statusStart = row[ReadingsTable.statusStart]
                )
            }

    fun siteStats(
        company: String,
        siteId: String,
        from: OffsetDateTime,
        to: OffsetDateTime
    ): SiteStats {
        val readings = siteHistory(company, siteId, from, to)
        val discharging = readings.count { it.status == 1.toShort() }
        val lastDischarge = readings
            .filter { it.status == 1.toShort() }
            .maxByOrNull { it.polledAt }
            ?.polledAt

        return SiteStats(
            dischargingReadings = discharging.toLong(),
            totalReadings = readings.size.toLong(),
            lastDischargeAt = lastDischarge
        )
    }
}

```

Note: `latestReadings` uses raw SQL with Postgres-specific `DISTINCT ON` via JDBC `PreparedStatement` for correct parameter binding. This is the most efficient way to get the latest reading per site.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :db:test --tests "*.ReadingRepositoryTest"`
Expected: 5 tests PASSED

- [ ] **Step 6: Commit**

```bash
git add db/src/main/kotlin/com/chicot/turdalert/db/repository/ReadingRepository.kt \
        db/src/main/kotlin/com/chicot/turdalert/db/partition/PartitionManager.kt \
        db/src/test/kotlin/com/chicot/turdalert/db/repository/ReadingRepositoryTest.kt
git commit -m "Add ReadingRepository with insert, history, stats, and latest queries"
```

---

### Task 11: PartitionManager tests

**Files:**
- Create: `db/src/test/kotlin/com/chicot/turdalert/db/partition/PartitionManagerTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.chicot.turdalert.db.partition

import com.chicot.turdalert.db.TestDatabase
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertTrue

class PartitionManagerTest {
    private val db = TestDatabase.database
    private val manager = PartitionManager()

    @Test
    fun `ensurePartitions creates current and future month partitions`() {
        val ref = OffsetDateTime.of(2026, 6, 15, 0, 0, 0, 0, ZoneOffset.UTC)

        transaction(db) { manager.ensurePartitions(ref) }

        val partitions = transaction(db) { existingPartitions() }
        assertTrue(partitions.contains("readings_2026_06"))
        assertTrue(partitions.contains("readings_2026_07"))
        assertTrue(partitions.contains("readings_2026_08"))
    }

    @Test
    fun `ensurePartitions is idempotent`() {
        val ref = OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC)

        transaction(db) { manager.ensurePartitions(ref) }
        transaction(db) { manager.ensurePartitions(ref) }

        val partitions = transaction(db) { existingPartitions() }
        assertTrue(partitions.contains("readings_2026_09"))
    }

    private fun existingPartitions(): List<String> {
        val result = mutableListOf<String>()
        TransactionManager.current().exec(
            """SELECT inhrelid::regclass::text AS partition_name
               FROM pg_catalog.pg_inherits
               WHERE inhparent = 'readings'::regclass"""
        ) { rs ->
            while (rs.next()) {
                result.add(rs.getString("partition_name"))
            }
        }
        return result
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew :db:test --tests "*.PartitionManagerTest"`
Expected: 2 tests PASSED

- [ ] **Step 3: Commit**

```bash
git add db/src/test/kotlin/com/chicot/turdalert/db/partition/PartitionManagerTest.kt
git commit -m "Add PartitionManager tests"
```

---

## Chunk 2: Poller, REST API & Docker

### Task 12: Poller + tests

**Files:**
- Create: `backend/src/main/kotlin/com/chicot/turdalert/backend/poller/Poller.kt`
- Create: `backend/src/test/kotlin/com/chicot/turdalert/backend/poller/PollerTest.kt`

- [ ] **Step 1: Write failing test**

File: `backend/src/test/kotlin/com/chicot/turdalert/backend/poller/PollerTest.kt`

```kotlin
package com.chicot.turdalert.backend.poller

import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PollerTest {

    @Test
    fun `toSiteRow maps OverflowPoint correctly`() {
        val point = OverflowPoint(
            id = "P001",
            latitude = 51.5,
            longitude = -0.1,
            status = DischargeStatus.DISCHARGING,
            watercourse = "River Thames",
            siteName = "Test CSO",
            statusStart = 1710500000000,
            company = "Thames Water"
        )

        val row = point.toSiteRow()

        assertEquals("Thames Water", row.company)
        assertEquals("P001", row.siteId)
        assertEquals("Test CSO", row.siteName)
        assertEquals(51.5, row.latitude)
    }

    @Test
    fun `toReadingRow maps status correctly`() = runTest {
        val point = OverflowPoint(
            id = "P001",
            latitude = 51.5,
            longitude = -0.1,
            status = DischargeStatus.DISCHARGING,
            watercourse = "River Thames",
            siteName = "Test CSO",
            statusStart = 1710500000000,
            company = "Thames Water"
        )

        val row = point.toReadingRow(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))

        assertEquals(1, row.status.toInt())
    }

    @Test
    fun `statusToShort maps all statuses`() {
        assertEquals(1, DischargeStatus.DISCHARGING.toDbStatus().toInt())
        assertEquals(0, DischargeStatus.NOT_DISCHARGING.toDbStatus().toInt())
        assertEquals(0, DischargeStatus.RECENT_DISCHARGE.toDbStatus().toInt())
        assertEquals(-1, DischargeStatus.OFFLINE.toDbStatus().toInt())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.PollerTest"`
Expected: Compilation error — `toSiteRow`, `toReadingRow`, `toDbStatus` don't exist

- [ ] **Step 3: Implement Poller**

File: `backend/src/main/kotlin/com/chicot/turdalert/backend/poller/Poller.kt`

```kotlin
package com.chicot.turdalert.backend.poller

import com.chicot.turdalert.api.OverflowRepository
import com.chicot.turdalert.db.partition.PartitionManager
import com.chicot.turdalert.db.repository.ReadingRepository
import com.chicot.turdalert.db.repository.ReadingRow
import com.chicot.turdalert.db.repository.SiteRepository
import com.chicot.turdalert.db.repository.SiteRow
import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val UK_BOUNDS = BoundingBox(
    minLat = 49.9,
    maxLat = 60.9,
    minLon = -8.2,
    maxLon = 1.8
)

private const val POLL_INTERVAL_MS = 15 * 60 * 1000L

class Poller(
    private val overflowRepository: OverflowRepository,
    private val siteRepository: SiteRepository,
    private val readingRepository: ReadingRepository,
    private val partitionManager: PartitionManager,
    private val database: Database
) {
    private val log = LoggerFactory.getLogger(Poller::class.java)

    var lastPollAt: OffsetDateTime? = null
        private set
    var lastPollDurationMs: Long = 0
        private set
    var lastCompaniesPolled: Int = 0
        private set
    var lastCompaniesFailed: Int = 0
        private set

    suspend fun runForever() {
        while (true) {
            transaction(database) {
                partitionManager.ensurePartitions(OffsetDateTime.now(ZoneOffset.UTC))
            }
            pollOnce()
            delayUntilNextSlot()
        }
    }

    suspend fun pollOnce() {
        val start = System.currentTimeMillis()
        val polledAt = OffsetDateTime.now(ZoneOffset.UTC)

        log.info("Starting poll cycle at {}", polledAt)

        val overflows = try {
            overflowRepository.allOverflows(UK_BOUNDS)
        } catch (e: Exception) {
            log.error("Fatal error during poll", e)
            lastCompaniesFailed = 10
            return
        }

        log.info("Fetched {} overflow points", overflows.size)

        transaction(database) {
            siteRepository.upsertSites(overflows.map { it.toSiteRow() })
            readingRepository.insertReadings(overflows.map { it.toReadingRow(polledAt) })
        }

        lastPollAt = polledAt
        lastPollDurationMs = System.currentTimeMillis() - start
        lastCompaniesPolled = 10
        lastCompaniesFailed = 0

        log.info("Poll cycle complete in {}ms, {} readings inserted", lastPollDurationMs, overflows.size)
    }

    private suspend fun delayUntilNextSlot() {
        val now = System.currentTimeMillis()
        val next = ((now / POLL_INTERVAL_MS) + 1) * POLL_INTERVAL_MS
        val wait = next - now
        log.info("Next poll in {}ms", wait)
        delay(wait)
    }
}

fun DischargeStatus.toDbStatus(): Short = when (this) {
    DischargeStatus.DISCHARGING -> 1
    DischargeStatus.NOT_DISCHARGING -> 0
    DischargeStatus.RECENT_DISCHARGE -> 0
    DischargeStatus.OFFLINE -> -1
}

fun OverflowPoint.toSiteRow() = SiteRow(
    company = company,
    siteId = id,
    siteName = siteName,
    watercourse = watercourse,
    latitude = latitude,
    longitude = longitude
)

fun OverflowPoint.toReadingRow(polledAt: OffsetDateTime) = ReadingRow(
    company = company,
    siteId = id,
    polledAt = polledAt,
    status = status.toDbStatus(),
    statusStart = statusStart?.let {
        OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneOffset.UTC)
    }
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :backend:test --tests "*.PollerTest"`
Expected: 3 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/chicot/turdalert/backend/poller/ \
        backend/src/test/kotlin/com/chicot/turdalert/backend/poller/
git commit -m "Add Poller with OverflowPoint-to-DB mapping and scheduling loop"
```

---

### Task 13: API response models

**Files:**
- Create: `backend/src/main/kotlin/com/chicot/turdalert/backend/api/Models.kt`

- [ ] **Step 1: Create response models**

```kotlin
package com.chicot.turdalert.backend.api

import kotlinx.serialization.Serializable

@Serializable
data class OverflowResponse(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val status: String,
    val watercourse: String,
    val siteName: String,
    val statusStart: Long? = null,
    val company: String
)

@Serializable
data class SiteResponse(
    val id: String,
    val siteName: String?,
    val watercourse: String?,
    val company: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class StatsResponse(
    val totalDischargeHours: Double,
    val eventCount: Int,
    val longestEventHours: Double,
    val percentDischarging: Double,
    val lastDischargeAt: String?
)

@Serializable
data class HistoryResponse(
    val site: SiteResponse,
    val stats: StatsResponse,
    val timeline: List<TimelineEntry>
)

@Serializable
data class TimelineEntry(
    val timestamp: String,
    val status: Int
)

@Serializable
data class WorstOffenderResponse(
    val site: SiteResponse,
    val stats: StatsResponse
)

@Serializable
data class HealthResponse(
    val status: String,
    val lastPollAt: String?,
    val lastPollDurationMs: Long,
    val companiesPolled: Int,
    val companiesFailed: Int,
    val databaseReachable: Boolean,
    val uptimeSeconds: Long
)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :backend:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/chicot/turdalert/backend/api/Models.kt
git commit -m "Add API response models"
```

---

### Task 14: Test app utility + Health endpoint

**Files:**
- Create: `backend/src/test/kotlin/com/chicot/turdalert/backend/TestApp.kt`
- Create: `backend/src/main/kotlin/com/chicot/turdalert/backend/api/HealthRoutes.kt`
- Create: `backend/src/test/kotlin/com/chicot/turdalert/backend/api/HealthRoutesTest.kt`
- Create: `backend/src/main/resources/logback.xml`

- [ ] **Step 1: Create logback.xml**

File: `backend/src/main/resources/logback.xml`

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

- [ ] **Step 2: Create TestApp utility**

File: `backend/src/test/kotlin/com/chicot/turdalert/backend/TestApp.kt`

```kotlin
package com.chicot.turdalert.backend

import com.chicot.turdalert.db.DatabaseConfig
import com.chicot.turdalert.db.connectAndMigrate
import com.chicot.turdalert.db.partition.PartitionManager
import com.chicot.turdalert.db.repository.ReadingRepository
import com.chicot.turdalert.db.repository.SiteRepository
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
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
        DatabaseConfig(
            url = container.jdbcUrl,
            user = container.username,
            password = container.password
        )
    )

    val siteRepository = SiteRepository()
    val readingRepository = ReadingRepository()
    val partitionManager = PartitionManager()

    fun ApplicationTestBuilder.installJson() {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            json(Json { prettyPrint = true })
        }
    }

    fun ensurePartitions() {
        org.jetbrains.exposed.sql.transactions.transaction(database) {
            partitionManager.ensurePartitions(OffsetDateTime.now(ZoneOffset.UTC))
        }
    }
}
```

- [ ] **Step 3: Write health route test**

File: `backend/src/test/kotlin/com/chicot/turdalert/backend/api/HealthRoutesTest.kt`

```kotlin
package com.chicot.turdalert.backend.api

import com.chicot.turdalert.backend.TestApp
import com.chicot.turdalert.backend.TestApp.configureTestApp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthRoutesTest {

    @Test
    fun `health endpoint returns ok`() = testApplication {
        application {
            TestApp.run { installJson() }
            routing {
                healthRoutes(
                    database = TestApp.database,
                    startTime = System.currentTimeMillis(),
                    pollerProvider = { null }
                )
            }
        }

        val response = client.get("/api/v1/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val health = Json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("ok", health.status)
        assertTrue(health.databaseReachable)
    }
}
```

- [ ] **Step 4: Implement health route**

File: `backend/src/main/kotlin/com/chicot/turdalert/backend/api/HealthRoutes.kt`

```kotlin
package com.chicot.turdalert.backend.api

import com.chicot.turdalert.backend.poller.Poller
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.healthRoutes(
    database: Database,
    startTime: Long,
    pollerProvider: () -> Poller?
) {
    get("/api/v1/health") {
        val poller = pollerProvider()
        val dbReachable = try {
            transaction(database) { exec("SELECT 1") }
            true
        } catch (_: Exception) {
            false
        }

        call.respond(HealthResponse(
            status = if (dbReachable) "ok" else "degraded",
            lastPollAt = poller?.lastPollAt?.toString(),
            lastPollDurationMs = poller?.lastPollDurationMs ?: 0,
            companiesPolled = poller?.lastCompaniesPolled ?: 0,
            companiesFailed = poller?.lastCompaniesFailed ?: 0,
            databaseReachable = dbReachable,
            uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000
        ))
    }
}
```

- [ ] **Step 5: Run test**

Run: `./gradlew :backend:test --tests "*.HealthRoutesTest"`
Expected: 1 test PASSED

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/ backend/src/test/
git commit -m "Add health endpoint with database and poller status"
```

---

### Task 15: Overflows endpoint + test

**Files:**
- Create: `backend/src/main/kotlin/com/chicot/turdalert/backend/api/OverflowRoutes.kt`
- Create: `backend/src/test/kotlin/com/chicot/turdalert/backend/api/OverflowRoutesTest.kt`

- [ ] **Step 1: Write failing test**

File: `backend/src/test/kotlin/com/chicot/turdalert/backend/api/OverflowRoutesTest.kt`

```kotlin
package com.chicot.turdalert.backend.api

import com.chicot.turdalert.backend.TestApp
import com.chicot.turdalert.backend.TestApp.configureTestApp
import com.chicot.turdalert.db.repository.ReadingRow
import com.chicot.turdalert.db.repository.SiteRow
import com.chicot.turdalert.db.tables.ReadingsTable
import com.chicot.turdalert.db.tables.SitesTable
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OverflowRoutesTest {
    private val now = OffsetDateTime.now(ZoneOffset.UTC)

    @BeforeTest
    fun setup() {
        TestApp.ensurePartitions()
        transaction(TestApp.database) {
            exec("DELETE FROM readings")
            SitesTable.deleteAll()
        }
        transaction(TestApp.database) {
            TestApp.siteRepository.upsertSites(listOf(
                SiteRow("THAMES", "P001", "CSO Alpha", "River Thames", 51.5, -0.1),
                SiteRow("SOUTHERN", "P002", "CSO Beta", "River Test", 50.9, -1.4)
            ))
            TestApp.readingRepository.insertReadings(listOf(
                ReadingRow("THAMES", "P001", now, 1, now.minusHours(1)),
                ReadingRow("SOUTHERN", "P002", now, 0, null)
            ))
        }
    }

    @Test
    fun `overflows returns sites within bounding box`() = testApplication {
        application {
            TestApp.run { installJson() }
            routing {
                overflowRoutes(TestApp.database, TestApp.readingRepository)
            }
        }

        val response = client.get("/api/v1/overflows?minLat=51.0&maxLat=52.0&minLon=-1.0&maxLon=1.0")

        assertEquals(HttpStatusCode.OK, response.status)
        val overflows = Json.decodeFromString<List<OverflowResponse>>(response.bodyAsText())
        assertEquals(1, overflows.size)
        assertEquals("DISCHARGING", overflows.first().status)
    }

    @Test
    fun `overflows returns 400 when parameters missing`() = testApplication {
        application {
            TestApp.run { installJson() }
            routing {
                overflowRoutes(TestApp.database, TestApp.readingRepository)
            }
        }

        val response = client.get("/api/v1/overflows")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.OverflowRoutesTest"`
Expected: Compilation error

- [ ] **Step 3: Implement overflows route**

File: `backend/src/main/kotlin/com/chicot/turdalert/backend/api/OverflowRoutes.kt`

```kotlin
package com.chicot.turdalert.backend.api

import com.chicot.turdalert.db.repository.LatestReading
import com.chicot.turdalert.db.repository.ReadingRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

private const val ONE_HOUR_SECONDS = 3600L

fun Route.overflowRoutes(database: Database, readingRepository: ReadingRepository) {
    get("/api/v1/overflows") {
        val minLat = call.request.queryParameters["minLat"]?.toDoubleOrNull()
        val maxLat = call.request.queryParameters["maxLat"]?.toDoubleOrNull()
        val minLon = call.request.queryParameters["minLon"]?.toDoubleOrNull()
        val maxLon = call.request.queryParameters["maxLon"]?.toDoubleOrNull()

        if (minLat == null || maxLat == null || minLon == null || maxLon == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing required parameters: minLat, maxLat, minLon, maxLon")
            return@get
        }

        val readings = transaction(database) {
            readingRepository.latestReadings(minLat, maxLat, minLon, maxLon)
        }

        call.respond(readings.map { it.toOverflowResponse() })
    }
}

private fun LatestReading.toOverflowResponse(): OverflowResponse {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val statusString = when {
        status == 1.toShort() -> "DISCHARGING"
        status == 0.toShort() && statusStart != null &&
            java.time.Duration.between(statusStart, now).seconds <= ONE_HOUR_SECONDS -> "RECENT_DISCHARGE"
        status == 0.toShort() -> "NOT_DISCHARGING"
        else -> "OFFLINE"
    }

    return OverflowResponse(
        id = siteId,
        latitude = latitude,
        longitude = longitude,
        status = statusString,
        watercourse = watercourse ?: "Unknown",
        siteName = siteName ?: siteId,
        statusStart = statusStart?.toInstant()?.toEpochMilli(),
        company = company
    )
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :backend:test --tests "*.OverflowRoutesTest"`
Expected: 2 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/chicot/turdalert/backend/api/OverflowRoutes.kt \
        backend/src/test/kotlin/com/chicot/turdalert/backend/api/OverflowRoutesTest.kt
git commit -m "Add /api/v1/overflows endpoint with RECENT_DISCHARGE derivation"
```

---

### Task 16: History endpoint + test

**Files:**
- Create: `backend/src/main/kotlin/com/chicot/turdalert/backend/api/HistoryRoutes.kt`
- Create: `backend/src/test/kotlin/com/chicot/turdalert/backend/api/HistoryRoutesTest.kt`

- [ ] **Step 1: Write failing test**

File: `backend/src/test/kotlin/com/chicot/turdalert/backend/api/HistoryRoutesTest.kt`

```kotlin
package com.chicot.turdalert.backend.api

import com.chicot.turdalert.backend.TestApp
import com.chicot.turdalert.backend.TestApp.configureTestApp
import com.chicot.turdalert.db.repository.ReadingRow
import com.chicot.turdalert.db.repository.SiteRow
import com.chicot.turdalert.db.tables.SitesTable
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryRoutesTest {
    private val now = OffsetDateTime.now(ZoneOffset.UTC)

    @BeforeTest
    fun setup() {
        TestApp.ensurePartitions()
        transaction(TestApp.database) {
            exec("DELETE FROM readings")
            SitesTable.deleteAll()
        }
        transaction(TestApp.database) {
            TestApp.siteRepository.upsertSites(listOf(
                SiteRow("THAMES", "P001", "CSO Alpha", "River Thames", 51.5, -0.1)
            ))
            val readings = (0 until 20).map { i ->
                ReadingRow("THAMES", "P001", now.minusMinutes(i.toLong() * 15),
                    if (i < 5) 1 else 0, null)
            }
            TestApp.readingRepository.insertReadings(readings)
        }
    }

    @Test
    fun `history returns site stats and timeline`() = testApplication {
        application {
            TestApp.run { installJson() }
            routing {
                historyRoutes(TestApp.database, TestApp.siteRepository, TestApp.readingRepository)
            }
        }

        val response = client.get("/api/v1/sites/THAMES/P001/history?days=1")

        assertEquals(HttpStatusCode.OK, response.status)
        val history = Json.decodeFromString<HistoryResponse>(response.bodyAsText())
        assertEquals("P001", history.site.id)
        assertEquals("THAMES", history.site.company)
        assertTrue(history.timeline.isNotEmpty())
        assertTrue(history.stats.totalDischargeHours >= 0)
    }

    @Test
    fun `history returns 404 for unknown site`() = testApplication {
        application {
            TestApp.run { installJson() }
            routing {
                historyRoutes(TestApp.database, TestApp.siteRepository, TestApp.readingRepository)
            }
        }

        val response = client.get("/api/v1/sites/FAKE/FAKE001/history")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
```

- [ ] **Step 2: Implement history route**

File: `backend/src/main/kotlin/com/chicot/turdalert/backend/api/HistoryRoutes.kt`

```kotlin
package com.chicot.turdalert.backend.api

import com.chicot.turdalert.db.repository.ReadingRepository
import com.chicot.turdalert.db.repository.ReadingRow
import com.chicot.turdalert.db.repository.SiteRepository
import com.chicot.turdalert.db.repository.SiteRow
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val MAX_DAYS = 90L
private const val DOWNSAMPLE_THRESHOLD_DAYS = 7L
private const val POLL_INTERVAL_MINUTES = 15.0

fun Route.historyRoutes(
    database: Database,
    siteRepository: SiteRepository,
    readingRepository: ReadingRepository
) {
    get("/api/v1/sites/{company}/{siteId}/history") {
        val company = call.parameters["company"]!!
        val siteId = call.parameters["siteId"]!!
        val days = call.request.queryParameters["days"]?.toLongOrNull()?.coerceAtMost(MAX_DAYS) ?: 30L

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val from = now.minusDays(days)

        val (site, readings) = transaction(database) {
            val s = siteRepository.findSite(company, siteId)
            val r = if (s != null) readingRepository.siteHistory(company, siteId, from, now) else emptyList()
            s to r
        }

        if (site == null) {
            call.respond(HttpStatusCode.NotFound, "Site not found")
            return@get
        }

        val timeline = if (days > DOWNSAMPLE_THRESHOLD_DAYS) {
            downsample(readings)
        } else {
            readings
        }

        val stats = computeStats(readings)

        call.respond(HistoryResponse(
            site = site.toSiteResponse(),
            stats = stats,
            timeline = timeline.map { it.toTimelineEntry() }
        ))
    }
}

private fun computeStats(readings: List<ReadingRow>): StatsResponse {
    val discharging = readings.count { it.status == 1.toShort() }
    val total = readings.size
    val totalDischargeHours = discharging * POLL_INTERVAL_MINUTES / 60.0
    val percentDischarging = if (total > 0) (discharging.toDouble() / total) * 100 else 0.0

    val events = countEvents(readings)
    val longestEvent = longestEventReadings(readings) * POLL_INTERVAL_MINUTES / 60.0
    val lastDischarge = readings
        .filter { it.status == 1.toShort() }
        .maxByOrNull { it.polledAt }
        ?.polledAt

    return StatsResponse(
        totalDischargeHours = totalDischargeHours,
        eventCount = events,
        longestEventHours = longestEvent,
        percentDischarging = percentDischarging,
        lastDischargeAt = lastDischarge?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    )
}

private fun countEvents(readings: List<ReadingRow>): Int {
    var count = 0
    var wasDischarging = false
    readings.forEach { r ->
        val isDischarging = r.status == 1.toShort()
        if (isDischarging && !wasDischarging) count++
        wasDischarging = isDischarging
    }
    return count
}

private fun longestEventReadings(readings: List<ReadingRow>): Int {
    var longest = 0
    var current = 0
    readings.forEach { r ->
        if (r.status == 1.toShort()) {
            current++
            if (current > longest) longest = current
        } else {
            current = 0
        }
    }
    return longest
}

private fun downsample(readings: List<ReadingRow>): List<ReadingRow> =
    readings.groupBy { it.polledAt.withMinute(0).withSecond(0).withNano(0) }
        .map { (_, group) ->
            group.maxBy { it.status }
        }
        .sortedBy { it.polledAt }

private fun SiteRow.toSiteResponse() = SiteResponse(
    id = siteId,
    siteName = siteName,
    watercourse = watercourse,
    company = company,
    latitude = latitude,
    longitude = longitude
)

private fun ReadingRow.toTimelineEntry() = TimelineEntry(
    timestamp = polledAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    status = status.toInt()
)
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :backend:test --tests "*.HistoryRoutesTest"`
Expected: 2 tests PASSED

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/chicot/turdalert/backend/api/HistoryRoutes.kt \
        backend/src/test/kotlin/com/chicot/turdalert/backend/api/HistoryRoutesTest.kt
git commit -m "Add /api/v1/sites/{company}/{siteId}/history with stats and timeline"
```

---

### Task 17: Worst offenders endpoint + test

**Files:**
- Create: `backend/src/main/kotlin/com/chicot/turdalert/backend/api/WorstOffendersRoutes.kt`
- Create: `backend/src/test/kotlin/com/chicot/turdalert/backend/api/WorstOffendersRoutesTest.kt`

- [ ] **Step 1: Write failing test**

File: `backend/src/test/kotlin/com/chicot/turdalert/backend/api/WorstOffendersRoutesTest.kt`

```kotlin
package com.chicot.turdalert.backend.api

import com.chicot.turdalert.backend.TestApp
import com.chicot.turdalert.backend.TestApp.configureTestApp
import com.chicot.turdalert.db.repository.ReadingRow
import com.chicot.turdalert.db.repository.SiteRow
import com.chicot.turdalert.db.tables.SitesTable
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorstOffendersRoutesTest {
    private val now = OffsetDateTime.now(ZoneOffset.UTC)

    @BeforeTest
    fun setup() {
        TestApp.ensurePartitions()
        transaction(TestApp.database) {
            exec("DELETE FROM readings")
            SitesTable.deleteAll()
        }
        transaction(TestApp.database) {
            TestApp.siteRepository.upsertSites(listOf(
                SiteRow("THAMES", "P001", "Heavy Discharger", "River Thames", 51.5, -0.1),
                SiteRow("THAMES", "P002", "Light Discharger", "River Thames", 51.51, -0.09),
                SiteRow("SOUTHERN", "P003", "Far Away", "River Test", 50.0, -3.0)
            ))
            val heavyReadings = (0 until 20).map { i ->
                ReadingRow("THAMES", "P001", now.minusMinutes(i.toLong() * 15), 1, null)
            }
            val lightReadings = (0 until 20).map { i ->
                ReadingRow("THAMES", "P002", now.minusMinutes(i.toLong() * 15),
                    if (i < 3) 1 else 0, null)
            }
            TestApp.readingRepository.insertReadings(heavyReadings + lightReadings)
        }
    }

    @Test
    fun `worst offenders returns ranked sites`() = testApplication {
        application {
            TestApp.run { installJson() }
            routing {
                worstOffendersRoutes(
                    TestApp.database, TestApp.siteRepository, TestApp.readingRepository
                )
            }
        }

        val response = client.get("/api/v1/sites/worst-offenders?lat=51.5&lon=-0.1&radius=5&days=1")

        assertEquals(HttpStatusCode.OK, response.status)
        val offenders = Json.decodeFromString<List<WorstOffenderResponse>>(response.bodyAsText())
        assertEquals(2, offenders.size)
        assertTrue(offenders[0].stats.totalDischargeHours >= offenders[1].stats.totalDischargeHours)
    }

    @Test
    fun `worst offenders excludes sites outside radius`() = testApplication {
        application {
            TestApp.run { installJson() }
            routing {
                worstOffendersRoutes(
                    TestApp.database, TestApp.siteRepository, TestApp.readingRepository
                )
            }
        }

        val response = client.get("/api/v1/sites/worst-offenders?lat=51.5&lon=-0.1&radius=1&days=1")

        assertEquals(HttpStatusCode.OK, response.status)
        val offenders = Json.decodeFromString<List<WorstOffenderResponse>>(response.bodyAsText())
        offenders.forEach { o ->
            assertTrue(o.site.company != "SOUTHERN" || o.site.id != "P003")
        }
    }
}
```

- [ ] **Step 2: Implement worst offenders route**

File: `backend/src/main/kotlin/com/chicot/turdalert/backend/api/WorstOffendersRoutes.kt`

```kotlin
package com.chicot.turdalert.backend.api

import com.chicot.turdalert.db.repository.ReadingRepository
import com.chicot.turdalert.db.repository.SiteRepository
import com.chicot.turdalert.db.repository.SiteRow
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_MILES = 3958.8
private const val DEGREES_PER_MILE_LAT = 1.0 / 69.0
private const val DEGREES_PER_MILE_LON_AT_55 = 1.0 / 39.0
private const val MAX_RESULTS = 20

fun Route.worstOffendersRoutes(
    database: Database,
    siteRepository: SiteRepository,
    readingRepository: ReadingRepository
) {
    get("/api/v1/sites/worst-offenders") {
        val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
        val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()
        val radius = call.request.queryParameters["radius"]?.toDoubleOrNull() ?: 1.0
        val days = call.request.queryParameters["days"]?.toLongOrNull()?.coerceAtMost(90) ?: 30

        if (lat == null || lon == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing required parameters: lat, lon")
            return@get
        }

        val minLat = lat - radius * DEGREES_PER_MILE_LAT
        val maxLat = lat + radius * DEGREES_PER_MILE_LAT
        val minLon = lon - radius * DEGREES_PER_MILE_LON_AT_55
        val maxLon = lon + radius * DEGREES_PER_MILE_LON_AT_55

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val from = now.minusDays(days)

        val offenders = transaction(database) {
            siteRepository.sitesInBounds(minLat, maxLat, minLon, maxLon)
                .filter { haversineDistance(lat, lon, it.latitude, it.longitude) <= radius }
                .map { site ->
                    val readings = readingRepository.siteHistory(site.company, site.siteId, from, now)
                    site to readings
                }
                .filter { (_, readings) -> readings.any { it.status == 1.toShort() } }
                .map { (site, readings) ->
                    WorstOffenderResponse(
                        site = site.toSiteResponse(),
                        stats = computeStats(readings)
                    )
                }
                .sortedByDescending { it.stats.totalDischargeHours }
                .take(MAX_RESULTS)
        }

        call.respond(offenders)
    }
}

private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_MILES * c
}

private fun SiteRow.toSiteResponse() = SiteResponse(
    id = siteId,
    siteName = siteName,
    watercourse = watercourse,
    company = company,
    latitude = latitude,
    longitude = longitude
)
```

Note: `computeStats`, `countEvents`, `longestEventReadings`, `toSiteResponse`, and `toTimelineEntry` are shared between HistoryRoutes and WorstOffendersRoutes. During implementation, extract these to `backend/src/main/kotlin/com/chicot/turdalert/backend/api/StatsCalculator.kt` as package-level functions rather than duplicating.

- [ ] **Step 3: Run tests**

Run: `./gradlew :backend:test --tests "*.WorstOffendersRoutesTest"`
Expected: 2 tests PASSED

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/chicot/turdalert/backend/api/WorstOffendersRoutes.kt \
        backend/src/test/kotlin/com/chicot/turdalert/backend/api/WorstOffendersRoutesTest.kt
git commit -m "Add /api/v1/sites/worst-offenders ranked by discharge hours"
```

---

### Task 18: Application entry point

**Files:**
- Create: `backend/src/main/kotlin/com/chicot/turdalert/backend/Application.kt`

- [ ] **Step 1: Implement Application.kt**

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :backend:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Build shadow JAR**

Run: `./gradlew :backend:shadowJar`
Expected: BUILD SUCCESSFUL, produces `backend/build/libs/backend-all.jar`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/chicot/turdalert/backend/Application.kt
git commit -m "Add Application entry point wiring poller and API routes"
```

---

### Task 19: Docker setup

**Files:**
- Create: `Dockerfile`
- Create: `docker-compose.yml`
- Create: `.env.example`

- [ ] **Step 1: Create Dockerfile**

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY backend/build/libs/backend-all.jar /app/backend.jar
ENTRYPOINT ["java", "-Xmx256m", "-jar", "/app/backend.jar"]
```

- [ ] **Step 2: Create docker-compose.yml**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: turdalert
      POSTGRES_USER: turdalert
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    command:
      - "postgres"
      - "-c"
      - "shared_buffers=128MB"
      - "-c"
      - "max_wal_size=1GB"
      - "-c"
      - "checkpoint_completion_target=0.9"
      - "-c"
      - "wal_buffers=16MB"
      - "-c"
      - "synchronous_commit=off"

  backend:
    build: .
    restart: unless-stopped
    depends_on:
      - postgres
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/turdalert
      DATABASE_USER: turdalert
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "8080:8080"

volumes:
  postgres-data:
```

- [ ] **Step 3: Create .env.example**

```
POSTGRES_PASSWORD=changeme
```

- [ ] **Step 4: Add .env to .gitignore**

Append to `.gitignore`:

```
.env
```

- [ ] **Step 5: Verify Docker build**

Run: `./gradlew :backend:shadowJar && docker build -t turd-alert-backend .`
Expected: Successfully built image

- [ ] **Step 6: Commit**

```bash
git add Dockerfile docker-compose.yml .env.example .gitignore
git commit -m "Add Docker Compose deployment for Synology NAS"
```

---

### Task 20: End-to-end smoke test

- [ ] **Step 1: Start the stack locally**

```bash
cp -f .env.example .env
docker compose up -d
```

- [ ] **Step 2: Wait for startup and check health**

```bash
sleep 10
curl -sS http://localhost:8080/api/v1/health | python3 -m json.tool
```

Expected: `"status": "ok"`, `"databaseReachable": true`

- [ ] **Step 3: Wait for first poll cycle and verify data**

```bash
sleep 120
curl -sS "http://localhost:8080/api/v1/overflows?minLat=51.0&maxLat=52.0&minLon=-1.0&maxLon=1.0" | python3 -m json.tool | head -20
```

Expected: JSON array with overflow points in the London area

- [ ] **Step 4: Verify history endpoint**

Pick a site ID from the overflows response:

```bash
curl -sS "http://localhost:8080/api/v1/sites/Thames%20Water/SOME_ID/history?days=1" | python3 -m json.tool
```

Expected: HistoryResponse with at least one timeline entry

- [ ] **Step 5: Tear down**

```bash
docker compose down
```

- [ ] **Step 6: Run full test suite**

```bash
./gradlew test
```

Expected: All tests PASSED

- [ ] **Step 7: Commit any fixes**

```bash
git add -A
git commit -m "Fix issues found during smoke test"
```
