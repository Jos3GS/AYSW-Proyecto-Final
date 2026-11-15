package com.itm.servicedesk.service

import com.itm.servicedesk.domain.builder.StaffPageQuery
import com.itm.servicedesk.domain.model.AuthenticatedIdentity
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.StaffSummary
import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.model.ValidationDetail
import com.itm.servicedesk.domain.repository.CreateUserCommand
import com.itm.servicedesk.domain.repository.UserRepository
import com.itm.servicedesk.domain.security.PasswordHasher
import com.itm.servicedesk.domain.validation.UserValidator
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class StaffService(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val timezone: String = "America/Bogota",
) {
    private val zoneId: ZoneId = ZoneId.of(timezone)

    fun createStaff(
        actor: AuthenticatedIdentity,
        fullName: String,
        documentNumber: String,
        email: String,
        phone: String,
        password: String,
        rawRole: String,
    ): UserProfile {
        if (actor.role != UserRole.COORDINADOR) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }

        val validated =
            UserValidator.validateStaffCreation(
                fullName = fullName,
                documentNumber = documentNumber,
                email = email,
                phone = phone,
                password = password,
                rawRole = rawRole,
            )

        if (userRepository.existsByEmailOrDocument(validated.user.email, validated.user.documentNumber)) {
            throw DomainException.ConflictException(CONFLICT_MESSAGE)
        }

        val passwordHash = passwordHasher.hash(validated.user.password)
        val now = OffsetDateTime.now(zoneId)

        val createdUser =
            userRepository.create(
                CreateUserCommand(
                    id = UUID.randomUUID(),
                    fullName = validated.user.fullName,
                    documentNumber = validated.user.documentNumber,
                    email = validated.user.email,
                    phone = validated.user.phone,
                    passwordHash = passwordHash,
                    role = validated.role,
                    active = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

        return createdUser.toProfile()
    }

    fun listStaff(
        actor: AuthenticatedIdentity,
        query: StaffPageQuery,
    ): PagedResult<StaffSummary> {
        if (actor.role != UserRole.COORDINADOR) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }

        if (query.roleFilter == UserRole.SOLICITANTE) {
            throw DomainException.ValidationException(
                message = "Los datos proporcionados contienen errores de validación.",
                details =
                    listOf(
                        ValidationDetail(
                            field = "role",
                            message = "El parámetro 'role' solo permite los valores PERSONAL_ATENCION o COORDINADOR.",
                        ),
                    ),
            )
        }

        return userRepository.findStaffPaged(query)
    }

    companion object {
        private const val FORBIDDEN_MESSAGE = "No tienes permisos suficientes para acceder a este recurso."
        private const val CONFLICT_MESSAGE =
            "Ya existe una cuenta registrada con el correo electrónico o número de documento indicado."
    }
}
