package com.itm.servicedesk.config

import com.itm.servicedesk.domain.builder.AppConfig
import com.itm.servicedesk.domain.builder.AppConfigBuilder
import java.nio.file.Files
import java.nio.file.Path

object EnvConfig {
    fun load(
        envFilePath: Path = Path.of(".env"),
        systemEnv: Map<String, String> = System.getenv(),
    ): Map<String, String> {
        val merged = mutableMapOf<String, String>()

        if (Files.exists(envFilePath) && Files.isRegularFile(envFilePath)) {
            Files.readAllLines(envFilePath, Charsets.UTF_8).forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) {
                    return@forEach
                }
                val cleaned = line.removePrefix("export ").trim()
                val separatorIndex = cleaned.indexOf('=')
                if (separatorIndex <= 0) {
                    return@forEach
                }
                val key = cleaned.substring(0, separatorIndex).trim()
                val value = stripQuotes(cleaned.substring(separatorIndex + 1).trim())
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    merged[key] = value
                }
            }
        }

        systemEnv.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                merged[key] = value
            }
        }

        return merged
    }

    fun toAppConfig(
        envFilePath: Path = Path.of(".env"),
        systemEnv: Map<String, String> = System.getenv(),
    ): AppConfig {
        val env = load(envFilePath, systemEnv)

        val port = env["APP_PORT"]?.let { it.toIntOrNull() ?: -1 } ?: DEFAULT_PORT
        val timezone = env["APP_TIMEZONE"] ?: DEFAULT_TIMEZONE
        val corsAllowedOrigins = parseCsv(env["CORS_ALLOWED_ORIGINS"])
        val allowedClientIps =
            env["ALLOWED_CLIENT_IPS"]
                ?.let(::parseCsv)
                ?.ifEmpty { DEFAULT_ALLOWED_IPS }
                ?: DEFAULT_ALLOWED_IPS

        val dbUrl = env["DB_URL"] ?: ""
        val dbUser = env["DB_USER"] ?: ""
        val dbPassword = env["DB_PASSWORD"] ?: ""
        val dbMaxPoolSize = env["DB_MAX_POOL_SIZE"]?.let { it.toIntOrNull() ?: -1 } ?: DEFAULT_MAX_POOL_SIZE

        val jwtSecret = env["JWT_SECRET"] ?: ""
        val jwtIssuer = env["JWT_ISSUER"] ?: DEFAULT_JWT_ISSUER
        val jwtAudience = env["JWT_AUDIENCE"] ?: DEFAULT_JWT_AUDIENCE
        val jwtExpirationHours =
            env["JWT_EXPIRATION_HOURS"]?.let { it.toLongOrNull() ?: -1L } ?: DEFAULT_JWT_EXPIRATION_HOURS

        val seedName = env["SEED_COORDINATOR_NAME"] ?: ""
        val seedDocument = env["SEED_COORDINATOR_DOCUMENT"] ?: ""
        val seedEmail = env["SEED_COORDINATOR_EMAIL"] ?: ""
        val seedPhone = env["SEED_COORDINATOR_PHONE"] ?: ""
        val seedPassword = env["SEED_COORDINATOR_PASSWORD"] ?: ""

        return AppConfigBuilder()
            .server(
                port = port,
                timezone = timezone,
                corsAllowedOrigins = corsAllowedOrigins,
                allowedClientIps = allowedClientIps,
            ).database(
                jdbcUrl = dbUrl,
                user = dbUser,
                password = dbPassword,
                maxPoolSize = dbMaxPoolSize,
            ).jwt(
                secret = jwtSecret,
                issuer = jwtIssuer,
                audience = jwtAudience,
                expirationHours = jwtExpirationHours,
            ).seedCoordinator(
                fullName = seedName,
                documentNumber = seedDocument,
                email = seedEmail,
                phone = seedPhone,
                rawPassword = seedPassword,
            ).build()
    }

    private fun parseCsv(raw: String?): List<String> =
        raw
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun stripQuotes(value: String): String {
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length - 1)
            }
        }
        return value
    }

    private const val DEFAULT_PORT = 8080
    private const val DEFAULT_TIMEZONE = "America/Bogota"
    private const val DEFAULT_MAX_POOL_SIZE = 10
    private const val DEFAULT_JWT_ISSUER = "itm-service-desk"
    private const val DEFAULT_JWT_AUDIENCE = "itm-service-desk-clients"
    private const val DEFAULT_JWT_EXPIRATION_HOURS = 1L
    private val DEFAULT_ALLOWED_IPS = listOf("127.0.0.1", "::1")
}
