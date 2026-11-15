package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.core.network.DefaultApiJson
import com.itm.servicedesk.data.dto.AuthResponseDto
import com.itm.servicedesk.data.dto.CreateStaffRequestDto
import com.itm.servicedesk.data.dto.LoginRequestDto
import com.itm.servicedesk.data.dto.RegisterRequestDto
import com.itm.servicedesk.domain.model.CreateStaffCommand
import com.itm.servicedesk.domain.model.LoginCommand
import com.itm.servicedesk.domain.model.RegisterCommand
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.testing.AuthTestFixtures
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthMapperTest {
    private val mapper = AuthMapper()

    @Test
    fun `RegisterRequestDto serializes without role field and masks password in toString`() {
        val command =
            RegisterCommand(
                fullName = "  Laura Gómez Pérez  ",
                documentNumber = " 1020304050 ",
                email = " LAURA.Gomez@correo.itm.edu.co ",
                phone = " 3001234567 ",
                password = "SuperSecretPassword2026",
            )

        val dto: RegisterRequestDto = mapper.toRegisterRequestDto(command)
        val encodedJson = DefaultApiJson.encodeToString(dto)

        assertFalse(encodedJson.contains("\"role\""))
        assertEquals("laura.gomez@correo.itm.edu.co", dto.email)
        assertEquals("Laura Gómez Pérez", dto.fullName)
        assertFalse(dto.toString().contains("SuperSecretPassword2026"))
        assertTrue(dto.toString().contains("password=***"))
        assertFalse(command.toString().contains("SuperSecretPassword2026"))
    }

    @Test
    fun `LoginRequestDto CreateStaffRequestDto and AuthResponseDto mask secrets in toString`() {
        val loginCommand = LoginCommand(email = " User@ITM.EDU.CO ", password = "MyLoginSecret1")
        val loginDto: LoginRequestDto = mapper.toLoginRequestDto(loginCommand)
        assertEquals("user@itm.edu.co", loginDto.email)
        assertFalse(loginDto.toString().contains("MyLoginSecret1"))
        assertFalse(loginCommand.toString().contains("MyLoginSecret1"))

        val staffCommand =
            CreateStaffCommand(
                fullName = "Carlos Mejía",
                documentNumber = "71829304",
                email = "carlos@itm.edu.co",
                phone = "3119876543",
                password = "StaffSecretPassword1",
                role = UserRole.COORDINADOR,
            )
        val staffDto: CreateStaffRequestDto = StaffMapper().toCreateStaffRequestDto(staffCommand)
        assertFalse(staffDto.toString().contains("StaffSecretPassword1"))
        assertFalse(staffCommand.toString().contains("StaffSecretPassword1"))

        val authDto =
            DefaultApiJson.decodeFromString<AuthResponseDto>(
                AuthTestFixtures.authResponseJson(token = "eyJhbGciOiJIUzI1NiJ9.supersecret"),
            )
        assertFalse(authDto.toString().contains("supersecret"))
        assertTrue(authDto.toString().contains("accessToken=***"))

        val session = mapper.toDomain(authDto)
        assertEquals(3600L, session.expiresInSeconds)
        assertEquals(UserRole.SOLICITANTE, session.user.role)
        assertFalse(session.toString().contains("supersecret"))
    }
}
