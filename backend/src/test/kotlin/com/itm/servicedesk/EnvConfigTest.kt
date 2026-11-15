package com.itm.servicedesk

import com.itm.servicedesk.config.EnvConfig
import com.itm.servicedesk.domain.model.DomainException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnvConfigTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `load reads dotenv file ignores comments and prioritizes system environment`() {
        val envFile = tempDir.resolve(".env")
        Files.writeString(
            envFile,
            """
            # Comment line
            APP_PORT=8080
            APP_TIMEZONE="America/Bogota"
            DB_USER='file_user'
            DB_PASSWORD=file_password

            EMPTY_IGNORED=
            """.trimIndent(),
        )

        val systemEnv =
            mapOf(
                "DB_USER" to "system_user_override",
                "JWT_SECRET" to "system_secret_override_32_characters_long",
            )

        val loaded = EnvConfig.load(envFilePath = envFile, systemEnv = systemEnv)

        assertEquals("8080", loaded["APP_PORT"])
        assertEquals("America/Bogota", loaded["APP_TIMEZONE"])
        assertEquals("system_user_override", loaded["DB_USER"])
        assertEquals("file_password", loaded["DB_PASSWORD"])
        assertEquals("system_secret_override_32_characters_long", loaded["JWT_SECRET"])
    }

    @Test
    fun `toAppConfig builds valid configuration from dotenv and defaults`() {
        val envFile = tempDir.resolve(".env")
        Files.writeString(
            envFile,
            """
            CORS_ALLOWED_ORIGINS=http://localhost:8081,http://127.0.0.1:8081
            DB_URL=jdbc:postgresql://localhost:5432/itm_service_desk
            DB_USER=itm_user
            DB_PASSWORD=local_db_secret
            JWT_SECRET=super_secret_jwt_key_at_least_32_characters
            JWT_ISSUER=itm-service-desk
            JWT_AUDIENCE=itm-service-desk-clients
            SEED_COORDINATOR_NAME=Coordinador General
            SEED_COORDINATOR_DOCUMENT=1000000001
            SEED_COORDINATOR_EMAIL=Coordinador@ITM.edu.co
            SEED_COORDINATOR_PHONE=3000000001
            SEED_COORDINATOR_PASSWORD=SecureSeed2026
            """.trimIndent(),
        )

        val config = EnvConfig.toAppConfig(envFilePath = envFile, systemEnv = emptyMap())

        assertEquals(8080, config.server.port)
        assertEquals("America/Bogota", config.server.timezone)
        assertEquals(listOf("127.0.0.1", "::1"), config.server.allowedClientIps)
        assertEquals(listOf("http://localhost:8081", "http://127.0.0.1:8081"), config.server.corsAllowedOrigins)
        assertEquals(10, config.database.maxPoolSize)
        assertEquals(3600L, config.jwt.expirationSeconds)
        assertEquals("coordinador@itm.edu.co", config.seedCoordinator.email)
    }

    @Test
    fun `toAppConfig throws ValidationException when required variables are missing`() {
        val envFile = tempDir.resolve("missing.env")
        val exception =
            assertFailsWith<DomainException.ValidationException> {
                EnvConfig.toAppConfig(envFilePath = envFile, systemEnv = emptyMap())
            }
        assertEquals(400, exception.httpStatus)
    }
}
