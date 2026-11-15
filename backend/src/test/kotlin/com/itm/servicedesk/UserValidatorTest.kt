package com.itm.servicedesk

import com.itm.servicedesk.domain.builder.AuthSessionBuilder
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.IssuedToken
import com.itm.servicedesk.domain.model.User
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.CreateUserCommand
import com.itm.servicedesk.domain.validation.UserValidator
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserValidatorTest {
    @Test
    fun `validateRegistration normalizes trim and lowercase email and accepts unicode letters`() {
        val result =
            UserValidator.validateRegistration(
                fullName = "  María José Peña Ñandú  ",
                documentNumber = "  1020304050 ",
                email = "  Laura.Gomez@Correo.ITM.edu.co  ",
                phone = " 3001234567 ",
                password = "ClaveSegura2026",
            )

        assertEquals("María José Peña Ñandú", result.fullName)
        assertEquals("1020304050", result.documentNumber)
        assertEquals("laura.gomez@correo.itm.edu.co", result.email)
        assertEquals("3001234567", result.phone)
        assertEquals("ClaveSegura2026", result.password)
        assertFalse(result.toString().contains("ClaveSegura2026"))
    }

    @Test
    fun `validateRegistration accumulates all Table 1 errors in details`() {
        val ex =
            assertFailsWith<DomainException.ValidationException> {
                UserValidator.validateRegistration(
                    fullName = "Laura 123!",
                    documentNumber = "12ab",
                    email = "correo-invalido",
                    phone = "123",
                    password = "short",
                )
            }

        assertEquals(400, ex.httpStatus)
        val fields = ex.details.map { it.field }.toSet()
        assertEquals(
            setOf("fullName", "documentNumber", "email", "phone", "password"),
            fields,
        )
    }

    @Test
    fun `validateRegistration rejects password exceeding 72 UTF-8 bytes even under 72 chars`() {
        val multibytePassword = "Clave" + "á".repeat(35) + "1"
        assertTrue(multibytePassword.length < 72)
        assertTrue(multibytePassword.toByteArray(Charsets.UTF_8).size > 72)

        val ex =
            assertFailsWith<DomainException.ValidationException> {
                UserValidator.validateRegistration(
                    fullName = "Laura Gómez Pérez",
                    documentNumber = "1020304050",
                    email = "laura@itm.edu.co",
                    phone = "3001234567",
                    password = multibytePassword,
                )
            }

        assertEquals(listOf("password"), ex.details.map { it.field })
    }

    @Test
    fun `validateRegistration rejects password missing digit or letter`() {
        val noDigit =
            assertFailsWith<DomainException.ValidationException> {
                UserValidator.validateRegistration(
                    fullName = "Laura Gómez",
                    documentNumber = "1020304050",
                    email = "laura@itm.edu.co",
                    phone = "3001234567",
                    password = "SoloLetrasSinNumero",
                )
            }
        assertEquals(listOf("password"), noDigit.details.map { it.field })

        val noLetter =
            assertFailsWith<DomainException.ValidationException> {
                UserValidator.validateRegistration(
                    fullName = "Laura Gómez",
                    documentNumber = "1020304050",
                    email = "laura@itm.edu.co",
                    phone = "3001234567",
                    password = "1234567890",
                )
            }
        assertEquals(listOf("password"), noLetter.details.map { it.field })
    }

    @Test
    fun `validateStaffCreation allows PERSONAL_ATENCION and COORDINADOR and rejects SOLICITANTE`() {
        val staff =
            UserValidator.validateStaffCreation(
                fullName = "Carlos Mejía",
                documentNumber = "71829304",
                email = "Carlos.Mejia@ITM.edu.co",
                phone = "3119876543",
                password = "StaffPassword2026",
                rawRole = "PERSONAL_ATENCION",
            )
        assertEquals(UserRole.PERSONAL_ATENCION, staff.role)
        assertEquals("carlos.mejia@itm.edu.co", staff.user.email)

        val coordinator =
            UserValidator.validateStaffCreation(
                fullName = "Ana María Ruiz",
                documentNumber = "71829305",
                email = "ana.ruiz@itm.edu.co",
                phone = "3119876544",
                password = "StaffPassword2026",
                rawRole = "COORDINADOR",
            )
        assertEquals(UserRole.COORDINADOR, coordinator.role)

        val invalidRoleEx =
            assertFailsWith<DomainException.ValidationException> {
                UserValidator.validateStaffCreation(
                    fullName = "ab",
                    documentNumber = "71829306",
                    email = "nuevo@itm.edu.co",
                    phone = "3119876545",
                    password = "StaffPassword2026",
                    rawRole = "SOLICITANTE",
                )
            }
        assertEquals(setOf("fullName", "role"), invalidRoleEx.details.map { it.field }.toSet())
    }

    @Test
    fun `validateLogin normalizes email and accumulates blank field errors`() {
        val valid = UserValidator.validateLogin("  USER@ITM.EDU.CO ", "wrong")
        assertEquals("user@itm.edu.co", valid.email)
        assertEquals("wrong", valid.password)
        assertFalse(valid.toString().contains("wrong"))

        val ex =
            assertFailsWith<DomainException.ValidationException> {
                UserValidator.validateLogin("invalid", "   ")
            }
        assertEquals(setOf("email", "password"), ex.details.map { it.field }.toSet())
    }

    @Test
    fun `domain models mask secrets in toString and AuthSessionBuilder requires both fields`() {
        val now = OffsetDateTime.now(ZoneOffset.of("-05:00"))
        val user =
            User(
                id = UUID.randomUUID(),
                fullName = "Laura Gómez",
                documentNumber = "1020304050",
                email = "laura@itm.edu.co",
                phone = "3001234567",
                passwordHash = "\$2a\$12\$SecretHashValueDoNotLeak",
                role = UserRole.SOLICITANTE,
                active = true,
                createdAt = now,
                updatedAt = now,
            )
        assertFalse(user.toString().contains("SecretHashValueDoNotLeak"))
        assertTrue(user.toString().contains("***"))

        val command =
            CreateUserCommand(
                id = user.id,
                fullName = user.fullName,
                documentNumber = user.documentNumber,
                email = user.email,
                phone = user.phone,
                passwordHash = "AnotherSecretHash",
                role = UserRole.SOLICITANTE,
                createdAt = now,
                updatedAt = now,
            )
        assertFalse(command.toString().contains("AnotherSecretHash"))

        val token =
            IssuedToken(
                accessToken = "super.secret.jwt.token",
                jti = UUID.randomUUID(),
                issuedAt = now,
                expiresAt = now.plusSeconds(3600L),
            )
        assertFalse(token.toString().contains("super.secret.jwt.token"))

        val session = AuthSessionBuilder().token(token).user(user).build()
        assertEquals(user.id, session.user.id)
        assertEquals(3600L, session.token.expiresInSeconds)

        assertFailsWith<IllegalStateException> {
            AuthSessionBuilder().user(user.toProfile()).build()
        }
        assertFailsWith<IllegalStateException> {
            AuthSessionBuilder().token(token).build()
        }
    }
}
