package com.chicot.turdalert.api

import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.OverflowPoint
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class OverflowRepository(private val client: HttpClient) {

    private val apis: List<WaterCompanyApi> = listOf(
        ArcGisCompany(
            companyName = "Southern Water",
            root = "https://services-eu1.arcgis.com/XxS6FebPX29TRGDJ/arcgis/rest/services/",
            resource = "Southern_Water_Storm_Overflow_Activity/FeatureServer/0/query",
            limit = 1000
        ),
        ArcGisCompany(
            companyName = "Anglian Water",
            root = "https://services3.arcgis.com/VCOY1atHWVcDlvlJ/arcgis/rest/services/",
            resource = "stream_service_outfall_locations_view/FeatureServer/0/query",
            limit = 1000
        ),
        ArcGisCompany(
            companyName = "United Utilities",
            root = "https://services5.arcgis.com/5eoLvR0f8HKb7HWP/arcgis/rest/services/",
            resource = "United_Utilities_Storm_Overflow_Activity/FeatureServer/0/query",
            limit = 2000
        ),
        ArcGisCompany(
            companyName = "Severn Trent",
            root = "https://services1.arcgis.com/NO7lTIlnxRMMG9Gw/arcgis/rest/services/",
            resource = "Severn_Trent_Water_Storm_Overflow_Activity/FeatureServer/0/query",
            limit = 2000
        ),
        ArcGisCompany(
            companyName = "Yorkshire Water",
            root = "https://services-eu1.arcgis.com/1WqkK5cDKUbF0CkH/arcgis/rest/services/",
            resource = "Yorkshire_Water_Storm_Overflow_Activity/FeatureServer/0/query",
            limit = 2000
        ),
        ArcGisCompany(
            companyName = "Northumbrian Water",
            root = "https://services-eu1.arcgis.com/MSNNjkZ51iVh8yBj/arcgis/rest/services/",
            resource = "Northumbrian_Water_Storm_Overflow_Activity_2_view/FeatureServer/0/query",
            limit = 2000
        ),
        ArcGisCompany(
            companyName = "South West Water",
            root = "https://services-eu1.arcgis.com/OMdMOtfhATJPcHe3/arcgis/rest/services/",
            resource = "NEH_outlets_PROD/FeatureServer/0/query",
            limit = 1000,
            isSouthWestWater = true
        ),
        ArcGisCompany(
            companyName = "Wessex Water",
            root = "https://services.arcgis.com/3SZ6e0uCvPROr4mS/arcgis/rest/services/",
            resource = "Wessex_Water_Storm_Overflow_Activity/FeatureServer/0/query",
            limit = 2000
        ),
        ThamesWaterApi,
        WelshWaterApi
    )

    suspend fun allOverflows(bounds: BoundingBox): List<OverflowPoint> =
        coroutineScope {
            apis.map { api ->
                async {
                    try {
                        api.fetchOverflows(client, bounds)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
}
