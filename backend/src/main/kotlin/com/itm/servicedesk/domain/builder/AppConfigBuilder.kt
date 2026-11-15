package com.itm.servicedesk.domain.builder

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.ValidationDetail
import java.time.DateTimeException
import java.time.ZoneId

data class ServerConfig(
    val port: Int,
    val timezone: String,
    val corsAllowedOrigins: List<String>,
    val allowedClientIps: List<String>,
)

data class DatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int,
    val connectionTimeoutMs: Long = 2000L,
    val validationTimeoutMs: Long = 1500L,
) {
    override fun toString(): String =
        "DatabaseConfig(jdbcUrl='***', user='***', password='***', " +
            "maxPoolSize=$maxPoolSize, connectionTimeoutMs=$connectionTimeoutMs, " +
            "validationTimeoutMs=$validationTimeoutMs)"
}

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val expirationSeconds: Long = 3600L,
) {
    override fun toString(): String =
        "JwtConfig(secret='***', issuer='$issuer', audience='$audience', " +
            "expirationSeconds=$expirationSeconds)"
}

data class SeedCoordinatorConfig(
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val rawPassword: String,
) {
    override fun toString(): String =
        "SeedCoordinatorConfig(fullName='$fullName', documentNumber='$documentNumber', " +
            "email='$email', phone='$phone', rawPassword='***')"
}

data class AppConfig(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val seedCoordinator: SeedCoordinatorConfig,
)

class AppConfigBuilder {
    private var port: Int = 8080
    private var timezone: String = "America/Bogota"
    private var corsAllowedOrigins: List<String> = listOf("http://localhost:8081")
    private var allowedClientIps: List<String> = listOf("127.0.0.1", "::1")

    private var jdbcUrl: String = ""
    private var dbUser: String = ""
    private var dbPassword: String = ""
    private var dbMaxPoolSize: Int = 10
    private var dbConnectionTimeoutMs: Long = 2000L
    private var dbValidationTimeoutMs: Long = 1500L

    private var jwtSecret: String = ""
    private var jwtIssuer: String = "itm-service-desk"
    private var jwtAudience: String = "itm-service-desk-clients"
    private var jwtExpirationSeconds: Long = 3600L

    private var seedFullName: String = ""
    private var seedDocumentNumber: String = ""
    private var seedEmail: String = ""
    private var seedPhone: String = ""
    private var seedRawPassword: String = ""

    fun server(
        port: Int,
        timezone: String,
        corsAllowedOrigins: List<String>,
        allowedClientIps: List<String>,
    ): AppConfigBuilder =
        apply {
            this.port = port
            this.timezone = timezone.trim()
            this.corsAllowedOrigins = corsAllowedOrigins.map { it.trim() }.filter { it.isNotEmpty() }
            this.allowedClientIps = allowedClientIps.map { it.trim() }.filter { it.isNotEmpty() }
        }

    fun database(
        jdbcUrl: String,
        user: String,
        password: String,
        maxPoolSize: Int = 10,
        connectionTimeoutMs: Long = 2000L,
        validationTimeoutMs: Long = 1500L,
    ): AppConfigBuilder =
        apply {
            this.jdbcUrl = jdbcUrl.trim()
            this.dbUser = user.trim()
            this.dbPassword = password
            this.dbMaxPoolSize = maxPoolSize
            this.dbConnectionTimeoutMs = connectionTimeoutMs
            this.dbValidationTimeoutMs = validationTimeoutMs
        }

    fun jwt(
        secret: String,
        issuer: String,
        audience: String,
        expirationSeconds: Long = 3600L,
    ): AppConfigBuilder =
        apply {
            this.jwtSecret = secret
            this.jwtIssuer = issuer.trim()
            this.jwtAudience = audience.trim()
            this.jwtExpirationSeconds = expirationSeconds
        }

    fun seedCoordinator(
        fullName: String,
        documentNumber: String,
        email: String,
        phone: String,
        rawPassword: String,
    ): AppConfigBuilder =
        apply {
            this.seedFullName = fullName.trim()
            this.seedDocumentNumber = documentNumber.trim()
            this.seedEmail = email.trim().lowercase()
            this.seedPhone = phone.trim()
            this.seedRawPassword = rawPassword
        }

    fun build(): AppConfig {
        val details = mutableListOf<ValidationDetail>()

        validateServer(details)
        validateDatabase(details)
        validateJwt(details)
        validateSeedCoordinator(details)

        if (details.isNotEmpty()) {
            throw DomainException.ValidationException(
                message = "La configuración de la aplicación es inválida.",
                details = details,
            )
        }

        return AppConfig(
            server =
                ServerConfig(
                    port = port,
                    timezone = timezone,
                    corsAllowedOrigins = corsAllowedOrigins,
                    allowedClientIps = allowedClientIps,
                ),
            database =
                DatabaseConfig(
                    jdbcUrl = jdbcUrl,
                    user = dbUser,
                    password = dbPassword,
                    maxPoolSize = dbMaxPoolSize,
                    connectionTimeoutMs = dbConnectionTimeoutMs,
                    validationTimeoutMs = dbValidationTimeoutMs,
                ),
            jwt =
                JwtConfig(
                    secret = jwtSecret,
                    issuer = jwtIssuer,
                    audience = jwtAudience,
                    expirationSeconds = jwtExpirationSeconds,
                ),
            seedCoordinator =
                SeedCoordinatorConfig(
                    fullName = seedFullName,
                    documentNumber = seedDocumentNumber,
                    email = seedEmail,
                    phone = seedPhone,
                    rawPassword = seedRawPassword,
                ),
        )
    }

    private fun validateServer(details: MutableList<ValidationDetail>) {
        if (port !in 1..65535) {
            details.add(ValidationDetail("APP_PORT", "El puerto debe estar entre 1 y 65535."))
        }
        try {
            ZoneId.of(timezone)
        } catch (_: DateTimeException) {
            details.add(ValidationDetail("APP_TIMEZONE", "Zona horaria inválida: '$timezone'."))
        }
        if (corsAllowedOrigins.isEmpty()) {
            details.add(
                ValidationDetail(
                    "CORS_ALLOWED_ORIGINS",
                    "Debe configurar al menos un origen CORS permitido.",
                ),
            )
        } else if (corsAllowedOrigins.any { it == "*" || it.contains("*") }) {
            details.add(
                ValidationDetail(
                    "CORS_ALLOWED_ORIGINS",
                    "Está prohibido usar comodines '*' en CORS_ALLOWED_ORIGINS.",
                ),
            )
        }
        if (allowedClientIps.isEmpty()) {
            details.add(
                ValidationDetail(
                    "ALLOWED_CLIENT_IPS",
                    "Debe configurar al menos una IP o rango CIDR permitido.",
                ),
            )
        }
    }

    private fun validateDatabase(details: MutableList<ValidationDetail>) {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            details.add(ValidationDetail("DB_URL", "DB_URL debe iniciar con 'jdbc:postgresql://'."))
        }
        if (dbUser.isBlank()) {
            details.add(ValidationDetail("DB_USER", "DB_USER es obligatorio."))
        }
        if (dbPassword.isBlank()) {
            details.add(ValidationDetail("DB_PASSWORD", "DB_PASSWORD es obligatorio."))
        }
        if (dbMaxPoolSize <= 0) {
            details.add(ValidationDetail("DB_MAX_POOL_SIZE", "DB_MAX_POOL_SIZE debe ser mayor a 0."))
        }
        if (dbConnectionTimeoutMs <= 0 || dbValidationTimeoutMs <= 0) {
            details.add(
                ValidationDetail(
                    "DB_TIMEOUTS",
                    "Los timeouts de conexión y validación de HikariCP deben ser mayores a 0.",
                ),
            )
        }
    }

    private fun validateJwt(details: MutableList<ValidationDetail>) {
        if (jwtSecret.length < MIN_JWT_SECRET_LENGTH) {
            details.add(
                ValidationDetail(
                    "JWT_SECRET",
                    "JWT_SECRET debe tener al menos $MIN_JWT_SECRET_LENGTH caracteres.",
                ),
            )
        }
        if (jwtIssuer.isBlank()) {
            details.add(ValidationDetail("JWT_ISSUER", "JWT_ISSUER es obligatorio."))
        }
        if (jwtAudience.isBlank()) {
            details.add(ValidationDetail("JWT_AUDIENCE", "JWT_AUDIENCE es obligatorio."))
        }
        if (jwtExpirationSeconds <= 0L) {
            details.add(ValidationDetail("JWT_EXPIRATION_SECONDS", "JWT_EXPIRATION_SECONDS debe ser mayor a 0."))
        }
    }

    private fun validateSeedCoordinator(details: MutableList<ValidationDetail>) {
        if (seedFullName.length !in 3..100 || !NAME_REGEX.matches(seedFullName)) {
            details.add(
                ValidationDetail(
                    "SEED_COORDINATOR_NAME",
                    "El nombre debe tener entre 3 y 100 caracteres alfabéticos.",
                ),
            )
        }
        if (!DOCUMENT_REGEX.matches(seedDocumentNumber)) {
            details.add(
                ValidationDetail(
                    "SEED_COORDINATOR_DOCUMENT",
                    "El documento debe contener entre 5 y 15 dígitos numéricos.",
                ),
            )
        }
        if (seedEmail.length > 100 || !EMAIL_REGEX.matches(seedEmail)) {
            details.add(
                ValidationDetail(
                    "SEED_COORDINATOR_EMAIL",
                    "El correo electrónico del coordinador semilla no es válido.",
                ),
            )
        }
        if (!PHONE_REGEX.matches(seedPhone)) {
            details.add(
                ValidationDetail(
                    "SEED_COORDINATOR_PHONE",
                    "El teléfono debe contener entre 7 y 15 dígitos numéricos.",
                ),
            )
        }
        val hasLetter = seedRawPassword.any { it.isLetter() }
        val hasDigit = seedRawPassword.any { it.isDigit() }
        val utf8Bytes = seedRawPassword.toByteArray(Charsets.UTF_8).size
        if (seedRawPassword.length < 8 || utf8Bytes > 72 || !hasLetter || !hasDigit) {
            details.add(
                ValidationDetail(
                    "SEED_COORDINATOR_PASSWORD",
                    "La contraseña semilla debe tener entre 8 y 72 bytes, con al menos una letra y un dígito.",
                ),
            )
        }
    }

    companion object {
        private const val MIN_JWT_SECRET_LENGTH = 32
        private val NAME_REGEX = Regex("^[\\p{L} ]+$")
        private val DOCUMENT_REGEX = Regex("^[0-9]{5,15}$")
        private val PHONE_REGEX = Regex("^[0-9]{7,15}$")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
