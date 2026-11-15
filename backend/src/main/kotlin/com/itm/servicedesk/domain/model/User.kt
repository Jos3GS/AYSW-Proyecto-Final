package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class User(
    val id: UUID,
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val passwordHash: String,
    val role: UserRole,
    val active: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    fun toProfile(): UserProfile =
        UserProfile(
            id = id,
            fullName = fullName,
            documentNumber = documentNumber,
            email = email,
            phone = phone,
            role = role,
            active = active,
            createdAt = createdAt,
        )

    fun toStaffSummary(): StaffSummary =
        StaffSummary(
            id = id,
            fullName = fullName,
            email = email,
            role = role,
            createdAt = createdAt,
        )

    override fun toString(): String =
        "User(id=$id, fullName='$fullName', documentNumber='$documentNumber', " +
            "email='$email', phone='$phone', passwordHash='***', role=$role, " +
            "active=$active, createdAt=$createdAt, updatedAt=$updatedAt)"
}
