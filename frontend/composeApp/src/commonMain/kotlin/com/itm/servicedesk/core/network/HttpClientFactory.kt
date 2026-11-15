package com.itm.servicedesk.core.network

import com.itm.servicedesk.core.storage.SessionEvent
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.core.storage.SessionStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val DefaultApiJson: Json =
    Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

private val PUBLIC_API_PATHS =
    setOf(
        "/api/v1/health",
        "/api/v1/auth/login",
        "/api/v1/auth/register",
    )

fun createHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    sessionStorage: SessionStorage,
    sessionEventBus: SessionEventBus,
    json: Json = DefaultApiJson,
): HttpClient {
    val client =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }
            defaultRequest {
                url(baseUrl)
                header(HttpHeaders.Accept, ContentType.Application.Json)
            }
        }

    client.plugin(HttpSend).intercept { request ->
        val path = request.url.encodedPath
        val isPublicRoute = PUBLIC_API_PATHS.any { publicPath -> path == publicPath }

        if (!isPublicRoute) {
            val token = sessionStorage.getToken()
            if (!token.isNullOrBlank()) {
                request.headers[HttpHeaders.Authorization] = "Bearer $token"
            }
        }

        val call = execute(request)

        if (!isPublicRoute && call.response.status == HttpStatusCode.Unauthorized) {
            sessionStorage.clearToken()
            sessionEventBus.emit(SessionEvent.SessionExpired)
        }

        call
    }

    return client
}

private const val REQUEST_TIMEOUT_MS = 15_000L
private const val CONNECT_TIMEOUT_MS = 10_000L
private const val SOCKET_TIMEOUT_MS = 15_000L
