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
