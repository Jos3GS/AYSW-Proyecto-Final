package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class UserProfile(
    val id: UUID,
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val active: Boolean,
    val createdAt: OffsetDateTime,
)
