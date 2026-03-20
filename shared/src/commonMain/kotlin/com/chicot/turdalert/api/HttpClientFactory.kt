package com.chicot.turdalert.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal object HttpClientFactory {
    fun create(): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }
}

fun createOverflowRepository(): OverflowFetcher =
    OverflowRepository(HttpClientFactory.create())

fun createHybridOverflowRepository(backendUrl: String): OverflowFetcher {
    val client = HttpClientFactory.create()
    val direct = OverflowRepository(client)
    return HybridOverflowRepository(backendUrl, client, direct)
}

fun createSiteHistoryClient(backendUrl: String): SiteHistoryClient =
    SiteHistoryClient(backendUrl, HttpClientFactory.create())
