package com.itm.servicedesk.config.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.itm.servicedesk.domain.builder.JwtConfig
import com.itm.servicedesk.domain.model.AuthenticatedIdentity
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.RevokedTokenRepository
import com.itm.servicedesk.domain.repository.UserRepository
import com.itm.servicedesk.routing.dto.ApiErrorDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.response.respond
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class JwtUserPrincipal(
    val identity: AuthenticatedIdentity,
)

fun ApplicationCall.requireJwtIdentity(): AuthenticatedIdentity =
    principal<JwtUserPrincipal>()?.identity
        ?: throw com.itm.servicedesk.domain.model.DomainException.UnauthorizedException(
            "No autorizado. Token ausente o inválido.",
        )

fun Application.configureJwtAuth(
    jwtConfig: JwtConfig,
    timezone: String,
    userRepository: UserRepository,
    revokedTokenRepository: RevokedTokenRepository,
) {
    val zoneId = ZoneId.of(timezone)
    val jwtVerifier =
        JWT
            .require(Algorithm.HMAC256(jwtConfig.secret))
            .withIssuer(jwtConfig.issuer)
            .withAudience(jwtConfig.audience)
            .build()

    install(Authentication) {
        jwt(AUTH_JWT_NAME) {
            verifier(jwtVerifier)
            validate { credential ->
                val payload = credential.payload
                val userId = parseUuidOrNull(payload.subject) ?: return@validate null
                val jti = parseUuidOrNull(payload.id) ?: return@validate null
                val rawRole = payload.getClaim("role").asString() ?: return@validate null
                val role = UserRole.entries.firstOrNull { it.name == rawRole } ?: return@validate null
                val email = payload.getClaim("email").asString()?.takeIf { it.isNotBlank() } ?: return@validate null
                val expiresAtInstant = payload.expiresAt?.toInstant() ?: return@validate null

                if (revokedTokenRepository.isRevoked(jti)) {
                    return@validate null
                }

                val user = userRepository.findById(userId)
                if (user == null || !user.active) {
                    return@validate null
                }

                JwtUserPrincipal(
                    AuthenticatedIdentity(
                        userId = userId,
                        email = email,
                        role = role,
                        jti = jti,
                        expiresAt = OffsetDateTime.ofInstant(expiresAtInstant, zoneId),
                    ),
                )
            }
            challenge { _, _ ->
                val timestamp = OffsetDateTime.now(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                call.respond(
                    status = HttpStatusCode.Unauthorized,
                    message =
                        ApiErrorDto(
                            timestamp = timestamp,
                            status = HttpStatusCode.Unauthorized.value,
                            error = "UNAUTHORIZED",
                            message = "No autorizado. Token ausente, inválido, expirado o revocado.",
                            path = call.request.path(),
                        ),
                )
            }
        }
    }
}

private fun parseUuidOrNull(raw: String?): UUID? =
    raw
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

const val AUTH_JWT_NAME = "auth-jwt"
