package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.data.dto.AuthResponseDto
import com.itm.servicedesk.data.dto.LoginRequestDto
import com.itm.servicedesk.data.dto.RegisterRequestDto
import com.itm.servicedesk.data.dto.UserProfileDto
import com.itm.servicedesk.domain.model.AuthSession
import com.itm.servicedesk.domain.model.LoginCommand
import com.itm.servicedesk.domain.model.RegisterCommand
import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.validation.UserFormValidator
import kotlin.time.Instant

class AuthMapper {
    fun toRegisterRequestDto(command: RegisterCommand): RegisterRequestDto =
        RegisterRequestDto(
            fullName = command.fullName.trim(),
            documentNumber = command.documentNumber.trim(),
            email = UserFormValidator.normalizeEmail(command.email),
            phone = command.phone.trim(),
            password = command.password,
        )

    fun toLoginRequestDto(command: LoginCommand): LoginRequestDto =
        LoginRequestDto(
            email = UserFormValidator.normalizeEmail(command.email),
            password = command.password,
        )

    fun toDomain(dto: AuthResponseDto): AuthSession =
        AuthSession(
            accessToken = dto.accessToken,
            expiresInSeconds = dto.expiresInSeconds,
            expiresAt = Instant.parse(dto.expiresAt),
            user = toDomain(dto.user),
        )

    fun toDomain(dto: UserProfileDto): UserProfile =
        UserProfile(
            id = dto.id,
            fullName = dto.fullName,
            documentNumber = dto.documentNumber,
            email = dto.email,
            phone = dto.phone,
            role = mapRole(dto.role),
            createdAt = Instant.parse(dto.createdAt),
        )

    fun mapRole(rawRole: String): UserRole = UserRole.valueOf(rawRole.trim().uppercase())
}
