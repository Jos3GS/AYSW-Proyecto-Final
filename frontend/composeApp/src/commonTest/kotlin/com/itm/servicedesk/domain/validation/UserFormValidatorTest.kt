package com.itm.servicedesk.domain.validation

import com.itm.servicedesk.domain.model.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserFormValidatorTest {
    @Test
    fun `accepts Spanish full names with accents and enye`() {
        assertNull(UserFormValidator.validateFullName("Laura Gómez Pérez"))
        assertNull(UserFormValidator.validateFullName("  Ñañez Peña Úrsula  "))
    }

    @Test
    fun `rejects invalid full names`() {
        assertNotNull(UserFormValidator.validateFullName("   "))
        assertNotNull(UserFormValidator.validateFullName("Ab"))
        assertNotNull(UserFormValidator.validateFullName("A".repeat(101)))
        assertNotNull(UserFormValidator.validateFullName("Laura123"))
        assertNotNull(UserFormValidator.validateFullName("Laura-Gómez"))
    }

    @Test
    fun `validates documentNumber and phone numeric ranges`() {
        assertNull(UserFormValidator.validateDocumentNumber("1020304050"))
        assertNotNull(UserFormValidator.validateDocumentNumber("1234"))
        assertNotNull(UserFormValidator.validateDocumentNumber("1".repeat(16)))
        assertNotNull(UserFormValidator.validateDocumentNumber("1020A40"))

        assertNull(UserFormValidator.validatePhone("3001234567"))
        assertNotNull(UserFormValidator.validatePhone("123456"))
        assertNotNull(UserFormValidator.validatePhone("1".repeat(16)))
        assertNotNull(UserFormValidator.validatePhone("300-1234"))
    }

    @Test
    fun `validates and normalizes email`() {
        assertEquals("laura.gomez@itm.edu.co", UserFormValidator.normalizeEmail("  LAURA.Gomez@ITM.EDU.CO "))
        assertNull(UserFormValidator.validateEmail("  LAURA.Gomez@correo.itm.edu.co "))
        assertNotNull(UserFormValidator.validateEmail(""))
        assertNotNull(UserFormValidator.validateEmail("correo-invalido"))
        assertNotNull(UserFormValidator.validateEmail("a..b@itm.edu.co"))
        assertNotNull(UserFormValidator.validateEmail("${"a".repeat(90)}@itm.edu.co"))
    }

    @Test
    fun `validates password rules including strict 72 UTF-8 bytes BCrypt limit`() {
        assertNull(UserFormValidator.validatePassword("ClaveSegura2026"))
        assertNull(UserFormValidator.validatePassword("Contraseña1"))
        assertNull(UserFormValidator.validatePassword("A1" + "b".repeat(70)))

        assertNotNull(UserFormValidator.validatePassword(""))
        assertNotNull(UserFormValidator.validatePassword("Abc1234"))
        assertNotNull(UserFormValidator.validatePassword("SoloLetrasSinNumero"))
        assertNotNull(UserFormValidator.validatePassword("12345678"))

        val ascii73Bytes = "A1" + "b".repeat(71)
        assertEquals(73, ascii73Bytes.encodeToByteArray().size)
        assertNotNull(UserFormValidator.validatePassword(ascii73Bytes))

        val multibyteOver72Bytes = "1" + "ñ".repeat(36)
        assertEquals(37, multibyteOver72Bytes.length)
        assertEquals(73, multibyteOver72Bytes.encodeToByteArray().size)
        assertNotNull(UserFormValidator.validatePassword(multibyteOver72Bytes))
    }

    @Test
    fun `validates staff roles and aggregate form validation maps`() {
        assertNull(UserFormValidator.validateStaffRole(UserRole.PERSONAL_ATENCION))
        assertNull(UserFormValidator.validateStaffRole(UserRole.COORDINADOR))
        assertNotNull(UserFormValidator.validateStaffRole(UserRole.SOLICITANTE))

        val loginErrors = UserFormValidator.validateLogin("bad", "")
        assertTrue(loginErrors.containsKey(UserFormValidator.FIELD_EMAIL))
        assertTrue(loginErrors.containsKey(UserFormValidator.FIELD_PASSWORD))

        val regErrors =
            UserFormValidator.validateRegistration(
                fullName = "Laura Gómez Pérez",
                documentNumber = "1020304050",
                email = "laura@itm.edu.co",
                phone = "3001234567",
                password = "ClaveSegura2026",
            )
        assertTrue(regErrors.isEmpty())

        val staffErrors =
            UserFormValidator.validateCreateStaff(
                fullName = "Carlos Mejía",
                documentNumber = "71829304",
                email = "carlos@itm.edu.co",
                phone = "3119876543",
                password = "StaffPassword2026",
                role = UserRole.SOLICITANTE,
            )
        assertEquals(setOf(UserFormValidator.FIELD_ROLE), staffErrors.keys)
    }
}
