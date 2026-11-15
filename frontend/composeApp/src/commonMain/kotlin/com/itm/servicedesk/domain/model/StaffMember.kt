package com.itm.servicedesk.domain.model

import kotlin.time.Instant

data class StaffMember(
    val id: String,
    val fullName: String,
    val email: String,
    val role: UserRole,
    val createdAt: Instant,
)
