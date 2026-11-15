package com.itm.servicedesk.domain.repository

import com.itm.servicedesk.domain.model.UserRole
import java.time.OffsetDateTime
import java.util.UUID

data class SeedUserData(
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
    override fun toString(): String =
        "SeedUserData(id=$id, fullName='$fullName', documentNumber='$documentNumber', " +
            "email='$email', phone='$phone', passwordHash='***', role=$role, active=$active)"
}

interface UserSeedRepository {
    fun existsCoordinator(): Boolean

    fun createCoordinatorIfAbsent(user: SeedUserData): Boolean
}
