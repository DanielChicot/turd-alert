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
            createPartitionIfNotExists(baseMonth.plusMonths(offset.toLong()))
        }
    }

    private fun createPartitionIfNotExists(month: YearMonth) {
        val name = "readings_${month.format(formatter)}"
        val from = month.atDay(1)
        val to = month.plusMonths(1).atDay(1)
        TransactionManager.current().exec(
            "CREATE TABLE IF NOT EXISTS $name PARTITION OF readings FOR VALUES FROM ('$from') TO ('$to')"
        )
    }
}
