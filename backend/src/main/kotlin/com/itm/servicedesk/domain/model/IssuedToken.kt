package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class IssuedToken(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val jti: UUID,
    val issuedAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
    val expiresInSeconds: Long = 3600L,
) {
    override fun toString(): String =
        "IssuedToken(accessToken='***', tokenType='$tokenType', jti=$jti, " +
            "issuedAt=$issuedAt, expiresAt=$expiresAt, expiresInSeconds=$expiresInSeconds)"
}
