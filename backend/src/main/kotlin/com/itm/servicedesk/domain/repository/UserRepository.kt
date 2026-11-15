package com.itm.servicedesk.domain.repository

import com.itm.servicedesk.domain.builder.StaffPageQuery
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.StaffSummary
import com.itm.servicedesk.domain.model.User
import com.itm.servicedesk.domain.model.UserRole
import java.time.OffsetDateTime
import java.util.UUID

data class CreateUserCommand(
    val id: UUID,
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val passwordHash: String,
    val role: UserRole,
    val active: Boolean = true,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    override fun toString(): String =
        "CreateUserCommand(id=$id, fullName='$fullName', documentNumber='$documentNumber', " +
            "email='$email', phone='$phone', passwordHash='***', role=$role, active=$active)"
}

interface UserRepository {
    fun create(command: CreateUserCommand): User

    fun findById(id: UUID): User?

    fun findByEmail(email: String): User?

    fun existsByEmailOrDocument(
        email: String,
        documentNumber: String,
    ): Boolean

    fun findStaffPaged(query: StaffPageQuery): PagedResult<StaffSummary>
}
