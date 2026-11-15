package com.itm.servicedesk.data.repository

import com.itm.servicedesk.core.network.createHttpClient
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.core.storage.SessionStorage
import com.itm.servicedesk.data.mapper.ErrorMapper
import com.itm.servicedesk.data.mapper.HealthMapper
import com.itm.servicedesk.data.remote.KtorSystemHealthRemoteDataSource
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class NoOpSessionStorage : SessionStorage {
    override suspend fun saveToken(token: String) = Unit

    override suspend fun getToken(): String? = null

    override suspend fun clearToken() = Unit
}

class SystemHealthRepositoryTest {
    private fun createRepository(engine: MockEngine): SystemHealthRepositoryImpl {
        val client =
            createHttpClient(
                engine = engine,
                baseUrl = "http://localhost:8080",
                sessionStorage = NoOpSessionStorage(),
                sessionEventBus = SessionEventBus(),
            )
        return SystemHealthRepositoryImpl(
            remoteDataSource = KtorSystemHealthRemoteDataSource(client),
            healthMapper = HealthMapper(),
            errorMapper = ErrorMapper(),
        )
    }

    @Test
    fun `returns Success ServiceHealth when backend returns 200 OK`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("/api/v1/health", request.url.encodedPath)
                    respond(
                        content =
                            """
                            {
                              "status": "UP",
                              "service": "itm-service-desk-backend",
                              "database": "UP",
                              "timestamp": "2026-11-14T11:24:00-05:00"
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val repository = createRepository(engine)

            val result = repository.checkHealth()

            val success = assertIs<AppResult.Success<*>>(result)
            val health = assertIs<com.itm.servicedesk.domain.model.ServiceHealth>(success.data)
            assertTrue(health.isServiceUp)
            assertTrue(health.isDatabaseUp)
            assertEquals("itm-service-desk-backend", health.serviceName)
        }

    @Test
    fun `returns Failure ServiceUnavailable when backend returns 503`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "timestamp": "2026-11-14T11:24:00-05:00",
                              "status": 503,
                              "error": "SERVICE_UNAVAILABLE",
                              "message": "El servicio de base de datos no se encuentra disponible temporalmente.",
                              "path": "/api/v1/health",
                              "details": []
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.ServiceUnavailable,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val repository = createRepository(engine)

            val result = repository.checkHealth()

            val failure = assertIs<AppResult.Failure>(result)
            val error = assertIs<DomainError.ServiceUnavailable>(failure.error)
            assertEquals(
                "El servicio de base de datos no se encuentra disponible temporalmente.",
                error.userMessage,
            )
        }

    @Test
    fun `returns Failure NetworkUnavailable when transport throws IOException`() =
        runTest {
            val engine =
                MockEngine {
                    throw IOException("Connection refused")
                }

            val repository = createRepository(engine)

            val result = repository.checkHealth()

            val failure = assertIs<AppResult.Failure>(result)
            assertIs<DomainError.NetworkUnavailable>(failure.error)
        }
}
