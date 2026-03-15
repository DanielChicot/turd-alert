package com.chicot.turdalert.db.repository

import com.chicot.turdalert.db.TestDatabase
import com.chicot.turdalert.db.tables.SitesTable
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SiteRepositoryTest {

    private val db = TestDatabase.database
    private val repo = SiteRepository()

    @BeforeTest
    fun cleanup() {
        transaction(db) {
            TransactionManager.current().exec("DELETE FROM readings")
            SitesTable.deleteAll()
        }
    }

    @Test
    fun `upsertSites inserts new sites`() {
        transaction(db) {
            repo.upsertSites(
                listOf(
                    SiteRow("thames", "S001", "Site One", "River Thames", 51.5, -0.1),
                    SiteRow("thames", "S002", "Site Two", "River Lea", 51.6, -0.2)
                )
            )
            assertEquals(2, SitesTable.selectAll().count())
        }
    }

    @Test
    fun `upsertSites updates existing site metadata`() {
        transaction(db) {
            repo.upsertSites(
                listOf(SiteRow("thames", "S001", "Old Name", "Old River", 51.5, -0.1))
            )
            repo.upsertSites(
                listOf(SiteRow("thames", "S001", "New Name", "New River", 52.0, -0.5))
            )

            val site = repo.findSite("thames", "S001")
            assertNotNull(site)
            assertEquals("New Name", site.siteName)
            assertEquals("New River", site.watercourse)
            assertEquals(52.0, site.latitude)
            assertEquals(-0.5, site.longitude)
            assertEquals(1, SitesTable.selectAll().count())
        }
    }

    @Test
    fun `sitesInBounds returns sites within bounding box`() {
        transaction(db) {
            repo.upsertSites(
                listOf(
                    SiteRow("thames", "IN", "Inside", null, 51.5, -0.1),
                    SiteRow("thames", "OUT", "Outside", null, 55.0, 2.0)
                )
            )
            val results = repo.sitesInBounds(51.0, 52.0, -1.0, 0.0)
            assertEquals(1, results.size)
            assertEquals("IN", results.first().siteId)
        }
    }

    @Test
    fun `findSite returns site by composite key`() {
        transaction(db) {
            repo.upsertSites(
                listOf(SiteRow("thames", "S001", "Target", "River Thames", 51.5, -0.1))
            )
            val site = repo.findSite("thames", "S001")
            assertNotNull(site)
            assertEquals("Target", site.siteName)
            assertEquals("thames", site.company)
            assertEquals("S001", site.siteId)
        }
    }

    @Test
    fun `findSite returns null for unknown site`() {
        transaction(db) {
            assertNull(repo.findSite("nonexistent", "X999"))
        }
    }
}
