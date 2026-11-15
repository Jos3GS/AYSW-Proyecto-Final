package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class StaffSummary(
    val id: UUID,
    val fullName: String,
    val email: String,
    val role: UserRole,
    val createdAt: OffsetDateTime,
)
