package com.chicot.turdalert.db.partition

import com.chicot.turdalert.db.TestDatabase
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class PartitionManagerTest {

    private val db = TestDatabase.database
    private val partitionManager = PartitionManager()

    private fun partitionNames(): List<String> =
        TransactionManager.current().exec(
            """
            SELECT inhrelid::regclass::text AS partition_name
            FROM pg_catalog.pg_inherits
            WHERE inhparent = 'readings'::regclass
            """.trimIndent()
        ) { rs ->
            buildList {
                while (rs.next()) {
                    add(rs.getString("partition_name"))
                }
            }
        } ?: emptyList()

    @Test
    fun `ensurePartitions creates current and future month partitions`() {
        val june2026 = OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        transaction(db) {
            partitionManager.ensurePartitions(june2026)
            val names = partitionNames()
            assertContains(names, "readings_2026_06")
            assertContains(names, "readings_2026_07")
            assertContains(names, "readings_2026_08")
        }
    }

    @Test
    fun `ensurePartitions is idempotent`() {
        val june2026 = OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        transaction(db) {
            partitionManager.ensurePartitions(june2026)
            partitionManager.ensurePartitions(june2026)
            val names = partitionNames()
            assertTrue(names.count { it == "readings_2026_06" } == 1)
        }
    }
}
