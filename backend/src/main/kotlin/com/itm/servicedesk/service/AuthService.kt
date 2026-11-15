package com.itm.servicedesk.service

import com.itm.servicedesk.domain.builder.AuthSessionBuilder
import com.itm.servicedesk.domain.model.AuthSession
import com.itm.servicedesk.domain.model.AuthenticatedIdentity
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.CreateUserCommand
import com.itm.servicedesk.domain.repository.RevokedTokenRepository
import com.itm.servicedesk.domain.repository.UserRepository
import com.itm.servicedesk.domain.security.PasswordHasher
import com.itm.servicedesk.domain.security.TokenProvider
import com.itm.servicedesk.domain.validation.UserValidator
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class AuthService(
    private val userRepository: UserRepository,
    private val revokedTokenRepository: RevokedTokenRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenProvider: TokenProvider,
    private val timezone: String = "America/Bogota",
) {
    private val zoneId: ZoneId = ZoneId.of(timezone)

    fun register(
        fullName: String,
        documentNumber: String,
        email: String,
        phone: String,
        password: String,
    ): AuthSession {
        val validated =
            UserValidator.validateRegistration(
                fullName = fullName,
                documentNumber = documentNumber,
                email = email,
                phone = phone,
                password = password,
            )

        if (userRepository.existsByEmailOrDocument(validated.email, validated.documentNumber)) {
            throw DomainException.ConflictException(CONFLICT_MESSAGE)
        }

        val passwordHash = passwordHasher.hash(validated.password)
        val now = OffsetDateTime.now(zoneId)

        val createdUser =
            userRepository.create(
                CreateUserCommand(
                    id = UUID.randomUUID(),
                    fullName = validated.fullName,
                    documentNumber = validated.documentNumber,
                    email = validated.email,
                    phone = validated.phone,
                    passwordHash = passwordHash,
                    role = UserRole.SOLICITANTE,
                    active = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

        val issuedToken = tokenProvider.issueToken(createdUser)
        return AuthSessionBuilder()
            .token(issuedToken)
            .user(createdUser)
            .build()
    }

    fun login(
        email: String,
        password: String,
    ): AuthSession {
        val validated = UserValidator.validateLogin(email = email, password = password)
        val user = userRepository.findByEmail(validated.email)

        if (user == null) {
            passwordHasher.verify(validated.password, passwordHasher.dummyHash)
            throw DomainException.UnauthorizedException(INVALID_CREDENTIALS_MESSAGE)
        }

        val passwordMatches = passwordHasher.verify(validated.password, user.passwordHash)
        if (!passwordMatches || !user.active) {
            throw DomainException.UnauthorizedException(INVALID_CREDENTIALS_MESSAGE)
        }

        val issuedToken = tokenProvider.issueToken(user)
        return AuthSessionBuilder()
            .token(issuedToken)
            .user(user)
            .build()
    }

    fun getMe(identity: AuthenticatedIdentity): UserProfile {
        val user =
            userRepository.findById(identity.userId)
                ?: throw DomainException.UnauthorizedException(SESSION_INVALID_MESSAGE)

        if (!user.active) {
            throw DomainException.UnauthorizedException(SESSION_INVALID_MESSAGE)
        }

        return user.toProfile()
    }

    fun logout(identity: AuthenticatedIdentity) {
        val now = OffsetDateTime.now(zoneId)
        revokedTokenRepository.revokeAndPurgeExpired(
            jti = identity.jti,
            expiresAt = identity.expiresAt,
            now = now,
        )
    }

    companion object {
        private const val INVALID_CREDENTIALS_MESSAGE = "Credenciales inválidas."
        private const val SESSION_INVALID_MESSAGE = "Sesión no válida o usuario inactivo."
        private const val CONFLICT_MESSAGE =
            "Ya existe una cuenta registrada con el correo electrónico o número de documento indicado."
    }
}
