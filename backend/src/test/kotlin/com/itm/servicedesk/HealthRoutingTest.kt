package com.itm.servicedesk

import com.itm.servicedesk.config.DatabaseFactory
import com.itm.servicedesk.domain.builder.AppConfigBuilder
import com.itm.servicedesk.domain.repository.HealthRepository
import com.itm.servicedesk.repository.ExposedHealthRepository
import com.itm.servicedesk.routing.dto.ApiErrorDto
import com.itm.servicedesk.routing.dto.HealthResponseDto
import com.itm.servicedesk.service.HealthService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.sql.SQLTransientConnectionException
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealthRoutingTest : IntegrationTestBase() {
    @Serializable
    private data class EchoRequest(
        val name: String,
    )

    @Test
    fun `GET health returns 200 UP with America Bogota timestamp and seeds coordinator`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanUsersTable(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            val response = client.get("/api/v1/health")
            assertEquals(HttpStatusCode.OK, response.status)

            val dto = Json.decodeFromString<HealthResponseDto>(response.bodyAsText())
            assertEquals("UP", dto.status)
            assertEquals("itm-service-desk-backend", dto.service)
            assertEquals("UP", dto.database)
            assertTrue(dto.timestamp.endsWith("-05:00"))
        }

    @Test
    fun `CORS headers allow configured origins and reject unknown origins`() =
        testApplication {
            val appConfig =
                buildTestAppConfig(
                    corsAllowedOrigins = listOf("http://localhost:8081"),
                )
            application {
                configureModule(appConfig, seedOnStartup = false)
            }

            val validCors =
                client.get("/api/v1/health") {
                    header(HttpHeaders.Origin, "http://localhost:8081")
                }
            assertEquals(HttpStatusCode.OK, validCors.status)
            assertEquals(
                "http://localhost:8081",
                validCors.headers[HttpHeaders.AccessControlAllowOrigin],
            )

            val preflight =
                client.options("/api/v1/health") {
                    header(HttpHeaders.Origin, "http://localhost:8081")
                    header(HttpHeaders.AccessControlRequestMethod, "PATCH")
                    header(HttpHeaders.AccessControlRequestHeaders, "Authorization, Content-Type")
                }
            assertEquals(HttpStatusCode.OK, preflight.status)

            val invalidCors =
                client.get("/api/v1/health") {
                    header(HttpHeaders.Origin, "https://evil.example.com")
                }
            assertNull(invalidCors.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `StatusPages maps 404 400 malformed json 403 blocked IP and 500 without leaking internals`() =
        testApplication {
            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig, seedOnStartup = false)
                routing {
                    post("/api/v1/test-json") {
                        val body = call.receive<EchoRequest>()
                        call.respond(HttpStatusCode.OK, body)
                    }
                    get("/api/v1/test-boom") {
                        error("jdbc:postgresql://secret-password-leak SELECT * FROM users")
                    }
                }
            }

            val notFoundResp = client.get("/api/v1/unknown-route")
            assertEquals(HttpStatusCode.NotFound, notFoundResp.status)
            val notFoundErr = Json.decodeFromString<ApiErrorDto>(notFoundResp.bodyAsText())
            assertEquals(404, notFoundErr.status)
            assertEquals("NOT_FOUND", notFoundErr.error)
            assertEquals("/api/v1/unknown-route", notFoundErr.path)

            val malformedResp =
                client.post("/api/v1/test-json") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "ok", "unexpectedField": true}""")
                }
            assertEquals(HttpStatusCode.BadRequest, malformedResp.status)
            val malformedErr = Json.decodeFromString<ApiErrorDto>(malformedResp.bodyAsText())
            assertEquals(400, malformedErr.status)

            val invalidSyntaxResp =
                client.post("/api/v1/test-json") {
                    contentType(ContentType.Application.Json)
                    setBody("""{not-valid-json""")
                }
            assertEquals(HttpStatusCode.BadRequest, invalidSyntaxResp.status)

            val blockedIpResp =
                client.get("/api/v1/health") {
                    header("X-Forwarded-For", "198.51.100.25")
                }
            assertEquals(HttpStatusCode.Forbidden, blockedIpResp.status)
            val forbiddenErr = Json.decodeFromString<ApiErrorDto>(blockedIpResp.bodyAsText())
            assertEquals(403, forbiddenErr.status)
            assertEquals("FORBIDDEN", forbiddenErr.error)

            val internalErrResp = client.get("/api/v1/test-boom")
            assertEquals(HttpStatusCode.InternalServerError, internalErrResp.status)
            val internalErrBody = internalErrResp.bodyAsText()
            val internalErr = Json.decodeFromString<ApiErrorDto>(internalErrBody)
            assertEquals(500, internalErr.status)
            assertEquals("INTERNAL_ERROR", internalErr.error)
            assertFalse(internalErrBody.contains("secret-password-leak"))
            assertFalse(internalErrBody.contains("SELECT * FROM users"))
            assertFalse(internalErrBody.contains("jdbc:"))
        }

    @Test
    fun `GET health returns 503 quickly when PostgreSQL is unavailable without exposing JDBC details`() =
        testApplication {
            val failingRepo =
                object : HealthRepository {
                    override fun checkDatabaseHealth(): Boolean =
                        throw SQLTransientConnectionException(
                            "Connection refused jdbc:postgresql://127.0.0.1:59999/secret_db",
                        )
                }
            val appConfig = buildTestAppConfig()
            val failingHealthService = HealthService(failingRepo, appConfig.server.timezone)

            application {
                configureModule(
                    appConfig = appConfig,
                    healthServiceOverride = failingHealthService,
                    seedOnStartup = false,
                )
            }

            val response = client.get("/api/v1/health")
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

            val body = response.bodyAsText()
            val errorDto = Json.decodeFromString<ApiErrorDto>(body)
            assertEquals(503, errorDto.status)
            assertEquals("SERVICE_UNAVAILABLE", errorDto.error)
            assertEquals("/api/v1/health", errorDto.path)
            assertFalse(body.contains("jdbc:"))
            assertFalse(body.contains("secret_db"))
            assertFalse(body.contains("SQLTransientConnectionException"))
        }

    @Test
    fun `DatabaseFactory fails within configured 2000ms HikariCP timeout when PostgreSQL port is closed`() {
        val unreachableConfig =
            AppConfigBuilder()
                .server(
                    port = 8080,
                    timezone = "America/Bogota",
                    corsAllowedOrigins = listOf("http://localhost:8081"),
                    allowedClientIps = listOf("127.0.0.1"),
                ).database(
                    jdbcUrl = "jdbc:postgresql://127.0.0.1:59999/nonexistent",
                    user = "itm_user",
                    password = "itm_password",
                    maxPoolSize = 2,
                    connectionTimeoutMs = 2000L,
                    validationTimeoutMs = 1500L,
                ).jwt(
                    secret = "test_jwt_secret_at_least_32_characters_long_1234",
                    issuer = "itm-service-desk",
                    audience = "itm-service-desk-clients",
                    expirationHours = 1L,
                ).seedCoordinator(
                    fullName = "Coordinador General ITM",
                    documentNumber = "1000000001",
                    email = "coordinador@itm.edu.co",
                    phone = "3000000001",
                    rawPassword = "SeedPassword2026",
                ).build()

        val elapsedMs =
            measureTimeMillis {
                try {
                    val conn =
                        DatabaseFactory.init(
                            unreachableConfig.database,
                            runMigrations = false,
                        )
                    conn.use {
                        ExposedHealthRepository(it.database).checkDatabaseHealth()
                    }
                } catch (_: Exception) {
                    // Expected fast failure within HikariCP connectionTimeout
                }
            }

        assertTrue(elapsedMs < 3500L, "Expected HikariCP fast failure (< 3500ms), took ${elapsedMs}ms")
    }
}
