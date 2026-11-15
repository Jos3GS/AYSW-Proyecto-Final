package com.itm.servicedesk

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.itm.servicedesk.domain.builder.JwtConfig
import com.itm.servicedesk.domain.model.User
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.service.JwtTokenService
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class JwtTokenServiceTest {
    @Test
    fun `issueToken emits HMAC256 JWT valid for 3600s with unique jti and required claims`() {
        val jwtConfig =
            JwtConfig(
                secret = "test_jwt_secret_at_least_32_characters_long_1234",
                issuer = "itm-service-desk",
                audience = "itm-service-desk-clients",
                expirationSeconds = 3600L,
            )
        val service = JwtTokenService(jwtConfig = jwtConfig, timezone = "America/Bogota")
        val now = OffsetDateTime.now(ZoneId.of("America/Bogota"))
        val user =
            User(
                id = UUID.randomUUID(),
                fullName = "Laura Gómez",
                documentNumber = "1020304050",
                email = "laura.gomez@correo.itm.edu.co",
                phone = "3001234567",
                passwordHash = "\$2a\$12\$dummy",
                role = UserRole.SOLICITANTE,
                active = true,
                createdAt = now,
                updatedAt = now,
            )

        val issued1 = service.issueToken(user)
        val issued2 = service.issueToken(user)

        assertEquals("Bearer", issued1.tokenType)
        assertEquals(3600L, issued1.expiresInSeconds)
        assertNotEquals(issued1.jti, issued2.jti)
        assertNotEquals(issued1.accessToken, issued2.accessToken)

        val verifier =
            JWT
                .require(Algorithm.HMAC256(jwtConfig.secret))
                .withIssuer(jwtConfig.issuer)
                .withAudience(jwtConfig.audience)
                .build()
        val decoded = verifier.verify(issued1.accessToken)

        assertEquals(user.id.toString(), decoded.subject)
        assertEquals(issued1.jti.toString(), decoded.id)
        assertEquals("SOLICITANTE", decoded.getClaim("role").asString())
        assertEquals(user.email, decoded.getClaim("email").asString())
        assertNotNull(decoded.issuedAt)
        assertNotNull(decoded.expiresAt)
        assertEquals(3600L, (decoded.expiresAt.time - decoded.issuedAt.time) / 1000L)
    }
}
