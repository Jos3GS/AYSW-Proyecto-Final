package com.itm.servicedesk.core.network

import com.itm.servicedesk.core.storage.SessionEvent
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.core.storage.SessionStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeSessionStorage(
    initialToken: String? = null,
) : SessionStorage {
    private var token: String? = initialToken

    override suspend fun saveToken(token: String) {
        this.token = token
    }

    override suspend fun getToken(): String? = token

    override suspend fun clearToken() {
        token = null
    }
}

class HttpClientAuthTest {
    @Test
    fun `injects Bearer token on protected routes`() =
        runTest {
            val storage = FakeSessionStorage(initialToken = "jwt-secret-token")
            val eventBus = SessionEventBus()
            var capturedAuthHeader: String? = null

            val engine =
                MockEngine { request ->
                    capturedAuthHeader = request.headers[HttpHeaders.Authorization]
                    respond(
                        content = """{"ok":true}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client =
                createHttpClient(
                    engine = engine,
                    baseUrl = "http://localhost:8080",
                    sessionStorage = storage,
                    sessionEventBus = eventBus,
                )

            val response = client.get("/api/v1/requests")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Bearer jwt-secret-token", capturedAuthHeader)
            client.close()
        }

    @Test
    fun `does not inject Bearer token on public health and auth routes`() =
        runTest {
            val storage = FakeSessionStorage(initialToken = "jwt-secret-token")
            val eventBus = SessionEventBus()
            val capturedAuthHeaders = mutableListOf<String?>()

            val engine =
                MockEngine { request ->
                    capturedAuthHeaders.add(request.headers[HttpHeaders.Authorization])
                    respond(
                        content = """{}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client =
                createHttpClient(
                    engine = engine,
                    baseUrl = "http://localhost:8080",
                    sessionStorage = storage,
                    sessionEventBus = eventBus,
                )

            client.get("/api/v1/health")
            client.get("/api/v1/auth/login")
            client.get("/api/v1/auth/register")

            assertEquals(listOf<String?>(null, null, null), capturedAuthHeaders)
            client.close()
        }

    @Test
    fun `clears session and emits SessionExpired on 401 from protected route`() =
        runTest {
            val storage = FakeSessionStorage(initialToken = "expired-jwt")
            val eventBus = SessionEventBus()

            val engine =
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "timestamp": "2026-11-14T11:24:00-05:00",
                              "status": 401,
                              "error": "UNAUTHORIZED",
                              "message": "Token expirado",
                              "path": "/api/v1/requests",
                              "details": []
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client =
                createHttpClient(
                    engine = engine,
                    baseUrl = "http://localhost:8080",
                    sessionStorage = storage,
                    sessionEventBus = eventBus,
                )

            val eventDeferred = async { eventBus.events.first() }
            val response = client.get("/api/v1/requests")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertNull(storage.getToken())
            assertEquals(SessionEvent.SessionExpired, eventDeferred.await())
            client.close()
        }
}
