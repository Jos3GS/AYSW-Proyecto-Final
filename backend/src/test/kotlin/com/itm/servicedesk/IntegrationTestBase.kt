package com.itm.servicedesk

import com.itm.servicedesk.config.DatabaseConnection
import com.itm.servicedesk.config.DatabaseFactory
import com.itm.servicedesk.domain.builder.AppConfig
import com.itm.servicedesk.domain.builder.AppConfigBuilder
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.TimeZone

abstract class IntegrationTestBase {
    protected fun buildTestAppConfig(
        jdbcUrl: String = postgres.jdbcUrl,
        dbUser: String = postgres.username,
        dbPassword: String = postgres.password,
        corsAllowedOrigins: List<String> = listOf("http://localhost:8081", "http://127.0.0.1:8081"),
        allowedClientIps: List<String> = listOf("127.0.0.1", "::1"),
        connectionTimeoutMs: Long = 2000L,
        validationTimeoutMs: Long = 1500L,
    ): AppConfig =
        AppConfigBuilder()
            .server(
                port = 8080,
                timezone = "America/Bogota",
                corsAllowedOrigins = corsAllowedOrigins,
                allowedClientIps = allowedClientIps,
            ).database(
                jdbcUrl = jdbcUrl,
                user = dbUser,
                password = dbPassword,
                maxPoolSize = 5,
                connectionTimeoutMs = connectionTimeoutMs,
                validationTimeoutMs = validationTimeoutMs,
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

    protected fun initTestDatabase(runMigrations: Boolean = true): DatabaseConnection {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"))
        val config = buildTestAppConfig()
        return DatabaseFactory.init(config.database, runMigrations = runMigrations)
    }

    protected fun cleanUsersTable(connection: DatabaseConnection) {
        cleanAuthTables(connection)
    }

    protected fun cleanAuthTables(connection: DatabaseConnection) {
        connection.dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM request_history")
                stmt.executeUpdate("DELETE FROM service_requests")
                stmt.executeUpdate("DELETE FROM request_code_sequences")
                stmt.executeUpdate("DELETE FROM revoked_tokens")
                stmt.executeUpdate("DELETE FROM users")
            }
            if (!conn.autoCommit) {
                conn.commit()
            }
        }
    }

    companion object {
        private val postgresImage = DockerImageName.parse("postgres:18-alpine")

        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(postgresImage).apply {
                withDatabaseName("itm_service_desk_test")
                withUsername("itm_test")
                withPassword("itm_test_pass")
                start()
            }
    }
}
