package com.itm.servicedesk.domain.validation

import com.itm.servicedesk.domain.model.RequestCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestFormValidatorTest {
    @Test
    fun `validates Tabla 2 rules with trim and optional previousRequestCode`() {
        val validErrors =
            RequestFormValidator.validateCreateRequest(
                category = RequestCategory.TRAMITE,
                subject = "  Asunto válido  ",
                description = "  Esta descripción tiene más de veinte caracteres válidos.  ",
                previousRequestCode = "  sol-2026-00012  ",
            )
        assertTrue(validErrors.isEmpty())
        assertEquals("SOL-2026-00012", RequestFormValidator.normalizePreviousRequestCode("  sol-2026-00012 "))
        assertNull(RequestFormValidator.normalizePreviousRequestCode("   "))
    }

    @Test
    fun `rejects missing category, out of bounds subject and description, and invalid previousRequestCode`() {
        val errors =
            RequestFormValidator.validateCreateRequest(
                category = null,
                subject = "  abc ",
                description = "   muy corta   ",
                previousRequestCode = "SOL-26-12",
            )
        assertNotNull(errors[RequestFormValidator.FIELD_CATEGORY])
        assertNotNull(errors[RequestFormValidator.FIELD_SUBJECT])
        assertNotNull(errors[RequestFormValidator.FIELD_DESCRIPTION])
        assertNotNull(errors[RequestFormValidator.FIELD_PREVIOUS_REQUEST_CODE])

        assertNotNull(RequestFormValidator.validateSubject("a".repeat(101)))
        assertNull(RequestFormValidator.validateSubject("a".repeat(5)))
        assertNull(RequestFormValidator.validateSubject("a".repeat(100)))

        assertNotNull(RequestFormValidator.validateDescription("b".repeat(2001)))
        assertNull(RequestFormValidator.validateDescription("b".repeat(20)))
        assertNull(RequestFormValidator.validateDescription("b".repeat(2000)))
    }

    @Test
    fun `validates strict SOL-YYYY-NNNNN+ regex for previousRequestCode`() {
        assertNull(RequestFormValidator.validatePreviousRequestCode(null))
        assertNull(RequestFormValidator.validatePreviousRequestCode(""))
        assertNull(RequestFormValidator.validatePreviousRequestCode("SOL-2026-00001"))
        assertNull(RequestFormValidator.validatePreviousRequestCode("sol-2026-123456"))

        assertNotNull(RequestFormValidator.validatePreviousRequestCode("REQ-2026-00001"))
        assertNotNull(RequestFormValidator.validatePreviousRequestCode("SOL-2026-0001"))
        assertNotNull(RequestFormValidator.validatePreviousRequestCode("SOL-26-00001"))
        assertNotNull(RequestFormValidator.validatePreviousRequestCode("SOL-2026-ABCDE"))
    }

    @Test
    fun `validates optional cancelReason up to 500 trimmed characters`() {
        assertNull(RequestFormValidator.validateCancelReason(null))
        assertNull(RequestFormValidator.validateCancelReason("   "))
        assertNull(RequestFormValidator.validateCancelReason("  ${"c".repeat(500)}  "))
        assertNotNull(RequestFormValidator.validateCancelReason("c".repeat(501)))
    }
}
