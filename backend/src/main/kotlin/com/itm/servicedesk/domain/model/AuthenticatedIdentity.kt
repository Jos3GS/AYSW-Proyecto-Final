package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class AuthenticatedIdentity(
    val userId: UUID,
    val email: String,
    val role: UserRole,
    val jti: UUID,
    val expiresAt: OffsetDateTime,
)
