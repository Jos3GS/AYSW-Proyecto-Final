package com.itm.servicedesk

import com.itm.servicedesk.domain.builder.AppConfigBuilder
import com.itm.servicedesk.domain.model.DomainException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppConfigBuilderTest {
    private fun validBuilder(): AppConfigBuilder =
        AppConfigBuilder()
            .server(
                port = 8080,
                timezone = "America/Bogota",
                corsAllowedOrigins = listOf("http://localhost:8081"),
                allowedClientIps = listOf("127.0.0.1", "::1"),
            ).database(
                jdbcUrl = "jdbc:postgresql://localhost:5432/itm_service_desk",
                user = "itm_secret_user",
                password = "SuperSecretDbPassword123",
                maxPoolSize = 10,
            ).jwt(
                secret = "super_secret_jwt_key_with_more_than_32_chars",
                issuer = "itm-service-desk",
                audience = "itm-service-desk-clients",
                expirationSeconds = 3600L,
            ).seedCoordinator(
                fullName = "Coordinador General ITM",
                documentNumber = "1000000001",
                email = "Coordinador@ITM.edu.co",
                phone = "3000000001",
                rawPassword = "CoordinatorSecret2026",
            )

    @Test
    fun `build succeeds and masks all credentials in toString`() {
        val config = validBuilder().build()

        assertEquals(8080, config.server.port)
        assertEquals(3600L, config.jwt.expirationSeconds)
        assertEquals("coordinador@itm.edu.co", config.seedCoordinator.email)

        val dbString = config.database.toString()
        assertFalse(dbString.contains("SuperSecretDbPassword123"))
        assertFalse(dbString.contains("itm_secret_user"))
        assertFalse(dbString.contains("jdbc:postgresql://"))
        assertTrue(dbString.contains("***"))

        val jwtString = config.jwt.toString()
        assertFalse(jwtString.contains("super_secret_jwt_key_with_more_than_32_chars"))
        assertTrue(jwtString.contains("***"))

        val seedString = config.seedCoordinator.toString()
        assertFalse(seedString.contains("CoordinatorSecret2026"))
        assertTrue(seedString.contains("***"))
    }

    @Test
    fun `build rejects wildcard in CORS_ALLOWED_ORIGINS`() {
        val builder =
            validBuilder().server(
                port = 8080,
                timezone = "America/Bogota",
                corsAllowedOrigins = listOf("*"),
                allowedClientIps = listOf("127.0.0.1"),
            )
        val exception = assertFailsWith<DomainException.ValidationException> { builder.build() }
        assertTrue(exception.details.any { it.field == "CORS_ALLOWED_ORIGINS" })
    }

    @Test
    fun `build rejects invalid port timezone jwt secret and coordinator fields`() {
        val builder =
            AppConfigBuilder()
                .server(
                    port = 70000,
                    timezone = "Invalid/Timezone",
                    corsAllowedOrigins = emptyList(),
                    allowedClientIps = emptyList(),
                ).database(
                    jdbcUrl = "jdbc:h2:mem:forbidden",
                    user = "",
                    password = "",
                    maxPoolSize = 0,
                    connectionTimeoutMs = 0,
                    validationTimeoutMs = 0,
                ).jwt(
                    secret = "short",
                    issuer = "",
                    audience = "",
                    expirationSeconds = 0,
                ).seedCoordinator(
                    fullName = "12",
                    documentNumber = "abc",
                    email = "not-an-email",
                    phone = "123",
                    rawPassword = "short",
                )

        val exception = assertFailsWith<DomainException.ValidationException> { builder.build() }
        val fields = exception.details.map { it.field }.toSet()
        assertTrue(
            fields.containsAll(
                listOf(
                    "APP_PORT",
                    "APP_TIMEZONE",
                    "CORS_ALLOWED_ORIGINS",
                    "ALLOWED_CLIENT_IPS",
                    "DB_URL",
                    "JWT_SECRET",
                    "SEED_COORDINATOR_NAME",
                    "SEED_COORDINATOR_DOCUMENT",
                    "SEED_COORDINATOR_EMAIL",
                    "SEED_COORDINATOR_PHONE",
                    "SEED_COORDINATOR_PASSWORD",
                ),
            ),
        )
    }

    @Test
    fun `build accepts default or positive autoCloseIntervalSeconds and rejects non-positive`() {
        val defaultConfig = validBuilder().build()
        assertEquals(60L, defaultConfig.server.autoCloseIntervalSeconds)

        val customConfig = validBuilder().autoCloseIntervalSeconds(120L).build()
        assertEquals(120L, customConfig.server.autoCloseIntervalSeconds)

        val zeroEx =
            assertFailsWith<DomainException.ValidationException> {
                validBuilder().autoCloseIntervalSeconds(0L).build()
            }
        assertTrue(zeroEx.details.any { it.field == "AUTO_CLOSE_INTERVAL_SECONDS" })

        val negativeEx =
            assertFailsWith<DomainException.ValidationException> {
                validBuilder().autoCloseIntervalSeconds(-5L).build()
            }
        assertTrue(negativeEx.details.any { it.field == "AUTO_CLOSE_INTERVAL_SECONDS" })
    }
}
