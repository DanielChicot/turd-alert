package com.chicot.turdalert.db.repository

import com.chicot.turdalert.db.tables.SitesTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
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
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double
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

private fun ResultRow.toSiteRow() = SiteRow(
    company = this[SitesTable.company],
    siteId = this[SitesTable.siteId],
    siteName = this[SitesTable.siteName],
    watercourse = this[SitesTable.watercourse],
    latitude = this[SitesTable.latitude],
    longitude = this[SitesTable.longitude]
)
