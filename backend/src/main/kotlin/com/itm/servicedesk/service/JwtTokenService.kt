package com.itm.servicedesk.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.itm.servicedesk.domain.builder.JwtConfig
import com.itm.servicedesk.domain.model.IssuedToken
import com.itm.servicedesk.domain.model.User
import com.itm.servicedesk.domain.security.TokenProvider
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

class JwtTokenService(
    private val jwtConfig: JwtConfig,
    private val timezone: String = "America/Bogota",
) : TokenProvider {
    private val zoneId: ZoneId = ZoneId.of(timezone)
    private val algorithm: Algorithm = Algorithm.HMAC256(jwtConfig.secret)

    override fun issueToken(user: User): IssuedToken {
        val jti = UUID.randomUUID()
        val issuedInstant = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val expiresInstant = issuedInstant.plusSeconds(jwtConfig.expirationSeconds)

        val accessToken =
            JWT
                .create()
                .withIssuer(jwtConfig.issuer)
                .withAudience(jwtConfig.audience)
                .withSubject(user.id.toString())
                .withJWTId(jti.toString())
                .withClaim("role", user.role.name)
                .withClaim("email", user.email)
                .withIssuedAt(Date.from(issuedInstant))
                .withExpiresAt(Date.from(expiresInstant))
                .sign(algorithm)

        return IssuedToken(
            accessToken = accessToken,
            tokenType = "Bearer",
            jti = jti,
            issuedAt = OffsetDateTime.ofInstant(issuedInstant, zoneId),
            expiresAt = OffsetDateTime.ofInstant(expiresInstant, zoneId),
            expiresInSeconds = jwtConfig.expirationSeconds,
        )
    }
}
