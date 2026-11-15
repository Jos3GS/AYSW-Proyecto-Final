package com.itm.servicedesk.domain.model

import kotlin.time.Instant

data class UserProfile(
    val id: String,
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val createdAt: Instant,
)
