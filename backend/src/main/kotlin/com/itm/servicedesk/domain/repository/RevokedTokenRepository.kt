package com.itm.servicedesk.domain.repository

import java.time.OffsetDateTime
import java.util.UUID

interface RevokedTokenRepository {
    fun revokeAndPurgeExpired(
        jti: UUID,
        expiresAt: OffsetDateTime,
        now: OffsetDateTime,
    )

    fun isRevoked(jti: UUID): Boolean
}
