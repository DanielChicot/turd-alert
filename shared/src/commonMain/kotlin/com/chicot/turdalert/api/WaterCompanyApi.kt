package com.chicot.turdalert.api

import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import com.chicot.turdalert.model.overlaps
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

private fun BoundingBox.toArcGisGeometry(): String =
    "$minLon,$minLat,$maxLon,$maxLat"

internal sealed interface WaterCompanyApi {
    val companyName: String
    suspend fun fetchOverflows(client: HttpClient, bounds: BoundingBox): List<OverflowPoint>
}

internal data class ArcGisCompany(
    override val companyName: String,
    val root: String,
    val resource: String,
    val limit: Int,
    val isSouthWestWater: Boolean = false
) : WaterCompanyApi {

    override suspend fun fetchOverflows(client: HttpClient, bounds: BoundingBox): List<OverflowPoint> =
        if (isSouthWestWater) fetchSouthWestWater(client, bounds) else fetchStandard(client, bounds)

    private suspend fun fetchStandard(client: HttpClient, bounds: BoundingBox): List<OverflowPoint> =
        paginateArcGis(limit) { offset ->
            client.get("$root$resource") {
                parameter("outFields", "*")
                parameter("where", "1=1")
                parameter("geometry", bounds.toArcGisGeometry())
                parameter("geometryType", "esriGeometryEnvelope")
                parameter("spatialRel", "esriSpatialRelIntersects")
                parameter("inSR", "4326")
                parameter("f", "json")
                parameter("resultOffset", offset)
                parameter("resultRecordCount", limit)
            }.body<ArcGisResponse>()
                .features
                .map { it.attributes }
        }.mapNotNull { attrs ->
            val lat = attrs.latitude ?: return@mapNotNull null
            val lon = attrs.longitude ?: return@mapNotNull null
            val id = attrs.id ?: return@mapNotNull null
            OverflowPoint(
                id = id,
                latitude = lat,
                longitude = lon,
                status = arcGisStatus(attrs.status),
                watercourse = normaliseWatercourse(attrs.receivingWaterCourse),
                siteName = attrs.siteName ?: id,
                statusStart = attrs.statusStart ?: attrs.latestEventStart,
                company = companyName
            )
        }

    private suspend fun fetchSouthWestWater(client: HttpClient, bounds: BoundingBox): List<OverflowPoint> =
        paginateArcGis(limit) { offset ->
            client.get("$root$resource") {
                parameter("outFields", "*")
                parameter("where", "1=1")
                parameter("geometry", bounds.toArcGisGeometry())
                parameter("geometryType", "esriGeometryEnvelope")
                parameter("spatialRel", "esriSpatialRelIntersects")
                parameter("inSR", "4326")
                parameter("f", "json")
                parameter("resultOffset", offset)
                parameter("resultRecordCount", limit)
            }.body<SouthWestWaterResponse>()
                .features
                .map { it.attributes }
        }.mapNotNull { attrs ->
            val lat = attrs.latitude ?: return@mapNotNull null
            val lon = attrs.longitude ?: return@mapNotNull null
            OverflowPoint(
                id = attrs.id ?: "SWW-${lat}-${lon}",
                latitude = lat,
                longitude = lon,
                status = arcGisStatus(attrs.status),
                watercourse = normaliseWatercourse(attrs.receivingWaterCourse),
                siteName = attrs.siteName ?: "Unknown",
                statusStart = attrs.statusStart,
                company = companyName
            )
        }
}

internal data object ThamesWaterApi : WaterCompanyApi {
    override val companyName: String = "Thames Water"

    private const val BASE_URL = "https://api.thameswater.co.uk/opendata/v2/discharge/status"
    private const val PAGE_SIZE = 1000

    private const val SERVICE_AREA_LAT_MIN = 51.0
    private const val SERVICE_AREA_LAT_MAX = 52.2
    private const val SERVICE_AREA_LON_MIN = -2.2
    private const val SERVICE_AREA_LON_MAX = 0.6

    private val serviceArea = BoundingBox(
        minLat = SERVICE_AREA_LAT_MIN,
        maxLat = SERVICE_AREA_LAT_MAX,
        minLon = SERVICE_AREA_LON_MIN,
        maxLon = SERVICE_AREA_LON_MAX
    )

    private fun inServiceArea(bounds: BoundingBox): Boolean =
        bounds.overlaps(serviceArea)

    override suspend fun fetchOverflows(client: HttpClient, bounds: BoundingBox): List<OverflowPoint> {
        if (!inServiceArea(bounds)) return emptyList()
        return paginate { offset ->
            client.get(BASE_URL) {
                parameter("limit", PAGE_SIZE)
                parameter("offset", offset)
            }.body<ThamesWaterResponse>().items
        }.mapNotNull { item ->
            val x = item.x ?: return@mapNotNull null
            val y = item.y ?: return@mapNotNull null
            val id = item.permitNumber ?: return@mapNotNull null
            val (lat, lon) = osgbToWgs84(x, y)
            OverflowPoint(
                id = id,
                latitude = lat,
                longitude = lon,
                status = thamesStatus(item.alertStatus),
                watercourse = item.receivingWaterCourse ?: "Unknown",
                siteName = item.locationName ?: "Unknown",
                statusStart = null,
                company = companyName
            )
        }.filter { point ->
            point.latitude in bounds.minLat..bounds.maxLat &&
                point.longitude in bounds.minLon..bounds.maxLon
        }
    }

    private suspend fun paginate(fetch: suspend (Int) -> List<ThamesWaterItem>): List<ThamesWaterItem> {
        val results = mutableListOf<ThamesWaterItem>()
        var offset = 0
        while (true) {
            val page = fetch(offset)
            if (page.isEmpty()) break
            results.addAll(page)
            offset += PAGE_SIZE
        }
        return results
    }
}

internal data object WelshWaterApi : WaterCompanyApi {
    override val companyName: String = "Welsh Water"

    private const val URL =
        "https://services3.arcgis.com/KLNF7YxtENPLYVey/arcgis/rest/services/Spill_Prod/FeatureServer/0/query"

    override suspend fun fetchOverflows(client: HttpClient, bounds: BoundingBox): List<OverflowPoint> =
        client.get(URL) {
            parameter("where", "1=1")
            parameter("outFields", "*")
            parameter("geometry", bounds.toArcGisGeometry())
            parameter("geometryType", "esriGeometryEnvelope")
            parameter("spatialRel", "esriSpatialRelIntersects")
            parameter("inSR", "4326")
            parameter("f", "json")
        }.body<WelshWaterResponse>()
            .features
            .mapNotNull { feature ->
                val attrs = feature.attributes
                val x = attrs.dischargeX ?: return@mapNotNull null
                val y = attrs.dischargeY ?: return@mapNotNull null
                val id = attrs.permitNumber ?: return@mapNotNull null
                val (lat, lon) = osgbToWgs84(x, y)
                OverflowPoint(
                    id = id,
                    latitude = lat,
                    longitude = lon,
                    status = welshStatus(attrs.status),
                    watercourse = attrs.receivingWater ?: "Unknown",
                    siteName = attrs.assetName ?: "Unknown",
                    statusStart = null,
                    company = companyName
                )
            }
}

private fun arcGisStatus(status: Int?): DischargeStatus = when (status) {
    1 -> DischargeStatus.DISCHARGING
    0 -> DischargeStatus.NOT_DISCHARGING
    else -> DischargeStatus.OFFLINE
}

private fun thamesStatus(status: String?): DischargeStatus = when {
    status.equals("Discharging", ignoreCase = true) -> DischargeStatus.DISCHARGING
    status.equals("Not discharging", ignoreCase = true) -> DischargeStatus.NOT_DISCHARGING
    else -> DischargeStatus.OFFLINE
}

private fun welshStatus(status: String?): DischargeStatus = when {
    status.equals("Overflow Operating", ignoreCase = true) -> DischargeStatus.DISCHARGING
    status == null -> DischargeStatus.OFFLINE
    status.startsWith("Overflow Not Operating", ignoreCase = true) -> DischargeStatus.NOT_DISCHARGING
    else -> DischargeStatus.OFFLINE
}

private fun normaliseWatercourse(value: String?): String = when {
    value.isNullOrBlank() -> "Unknown"
    value == "#N/A" -> "Unknown"
    else -> value
}

private suspend fun <T> paginateArcGis(limit: Int, fetch: suspend (Int) -> List<T>): List<T> {
    val results = mutableListOf<T>()
    var offset = 0
    while (true) {
        val page = fetch(offset)
        if (page.isEmpty()) break
        results.addAll(page)
        if (page.size < limit) break
        offset += limit
    }
    return results
}
