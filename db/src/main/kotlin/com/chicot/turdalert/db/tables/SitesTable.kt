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
