package com.itm.servicedesk

import com.itm.servicedesk.domain.builder.AppConfig
import com.itm.servicedesk.domain.builder.AppConfigBuilder
import com.itm.servicedesk.domain.repository.HealthRepository
import com.itm.servicedesk.routing.dto.ApiErrorDto
import com.itm.servicedesk.service.HealthService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwaggerRoutingTest {
    private fun buildSwaggerTestConfig(): AppConfig =
        AppConfigBuilder()
            .server(
                port = 8080,
                timezone = "America/Bogota",
                corsAllowedOrigins = listOf("http://localhost:8081", "http://127.0.0.1:8081"),
                allowedClientIps = listOf("127.0.0.1", "::1"),
            ).database(
                jdbcUrl = "jdbc:postgresql://127.0.0.1:5432/itm_service_desk",
                user = "itm_user",
                password = "itm_password",
            ).jwt(
                secret = "test_jwt_secret_at_least_32_characters_long_1234",
                issuer = "itm-service-desk",
                audience = "itm-service-desk-clients",
                expirationSeconds = 3600L,
            ).seedCoordinator(
                fullName = "Coordinador General ITM",
                documentNumber = "1000000001",
                email = "coordinador@itm.edu.co",
                phone = "3000000001",
                rawPassword = "SeedPassword2026",
            ).build()

    private fun buildStubHealthService(): HealthService =
        HealthService(
            healthRepository =
                object : HealthRepository {
                    override fun checkDatabaseHealth(): Boolean = true
                },
            timezone = "America/Bogota",
        )

    @Test
    fun `GET swagger returns 200 HTML with Swagger UI and GET documentation yaml serves OpenAPI spec`() =
        testApplication {
            val appConfig = buildSwaggerTestConfig()
            val healthService = buildStubHealthService()
            application {
                configureModule(
                    appConfig = appConfig,
                    healthServiceOverride = healthService,
                    seedOnStartup = false,
                )
            }

            val uiResponse = client.get("/swagger")
            assertEquals(HttpStatusCode.OK, uiResponse.status)
            val uiBody = uiResponse.bodyAsText()
            assertTrue(uiBody.contains("swagger-ui", ignoreCase = true))
            assertTrue(uiBody.contains("documentation.yaml"))

            val specResponse = client.get("/swagger/documentation.yaml")
            assertEquals(HttpStatusCode.OK, specResponse.status)
            val specBody = specResponse.bodyAsText()
            assertTrue(specBody.contains("openapi: 3.0.3"))
            assertTrue(specBody.contains("ITM Service Desk API"))
            assertTrue(specBody.contains("bearerAuth:"))

            val expectedPaths =
                listOf(
                    "/api/v1/health:",
                    "/api/v1/auth/register:",
                    "/api/v1/auth/login:",
                    "/api/v1/auth/me:",
                    "/api/v1/auth/logout:",
                    "/api/v1/users/staff:",
                    "/api/v1/requests:",
                    "/api/v1/requests/my:",
                    "/api/v1/requests/{code}:",
                    "/api/v1/requests/{code}/cancel:",
                    "/api/v1/attention/coordination/pending:",
                    "/api/v1/attention/requests/{code}/assign:",
                    "/api/v1/attention/requests/{code}/reassign:",
                    "/api/v1/attention/my-assigned:",
                    "/api/v1/attention/requests/{code}:",
                    "/api/v1/attention/requests/{code}/interventions:",
                    "/api/v1/attention/requests/{code}/escalate:",
                    "/api/v1/attention/requests/{code}/respond:",
                    "/api/v1/reports/summary:",
                    "/api/v1/reports/requests:",
                )
            expectedPaths.forEach { path ->
                assertTrue(specBody.contains(path), "Missing OpenAPI path: $path")
            }

            val sameOriginUiResponse =
                client.get("/swagger") {
                    header("Origin", "http://localhost:80")
                }
            assertEquals(HttpStatusCode.OK, sameOriginUiResponse.status)
        }

    @Test
    fun `GET swagger enforces IpAllowListPlugin and blocks disallowed client IP with 403`() =
        testApplication {
            val appConfig = buildSwaggerTestConfig()
            val healthService = buildStubHealthService()
            application {
                configureModule(
                    appConfig = appConfig,
                    healthServiceOverride = healthService,
                    seedOnStartup = false,
                )
            }

            val blockedResponse =
                client.get("/swagger") {
                    header("X-Forwarded-For", "198.51.100.25")
                }
            assertEquals(HttpStatusCode.Forbidden, blockedResponse.status)
            val error = Json.decodeFromString<ApiErrorDto>(blockedResponse.bodyAsText())
            assertEquals(403, error.status)
            assertEquals("FORBIDDEN", error.error)
        }
}
