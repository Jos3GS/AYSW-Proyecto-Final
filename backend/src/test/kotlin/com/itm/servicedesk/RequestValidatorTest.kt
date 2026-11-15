package com.itm.servicedesk

import com.itm.servicedesk.domain.model.ApplicantVisibleHistoryEntry
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.HistoryAction
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.validation.RequestValidator
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestValidatorTest {
    @Test
    fun `validateCreation trims inputs and accepts all 4 RequestCategory values and valid previousRequestCode`() {
        RequestCategory.entries.forEach { category ->
            val validated =
                RequestValidator.validateCreation(
                    rawCategory = "  ${category.name}  ",
                    rawSubject = "  Asunto válido de prueba  ",
                    rawDescription = "  Esta es una descripción suficientemente larga para cumplir Tabla 2.  ",
                    rawPreviousRequestCode = "  SOL-2026-00012  ",
                )

            assertEquals(category, validated.category)
            assertEquals("Asunto válido de prueba", validated.subject)
            assertEquals(
                "Esta es una descripción suficientemente larga para cumplir Tabla 2.",
                validated.description,
            )
            assertEquals("SOL-2026-00012", validated.previousRequestCode)
        }
    }

    @Test
    fun `validateCreation treats blank or null previousRequestCode as null and accepts boundary lengths`() {
        val minSubject = "12345"
        val maxSubject = "A".repeat(100)
        val minDescription = "12345678901234567890"
        val maxDescription = "B".repeat(2000)

        val minResult =
            RequestValidator.validateCreation(
                rawCategory = "TRAMITE",
                rawSubject = minSubject,
                rawDescription = minDescription,
                rawPreviousRequestCode = "   ",
            )
        assertEquals(minSubject, minResult.subject)
        assertEquals(minDescription, minResult.description)
        assertNull(minResult.previousRequestCode)

        val maxResult =
            RequestValidator.validateCreation(
                rawCategory = "ORIENTACION",
                rawSubject = maxSubject,
                rawDescription = maxDescription,
                rawPreviousRequestCode = null,
            )
        assertEquals(maxSubject, maxResult.subject)
        assertEquals(maxDescription, maxResult.description)
        assertNull(maxResult.previousRequestCode)
    }

    @Test
    fun `validateCreation accumulates all Table 2 errors and invalid previousRequestCode format`() {
        val ex =
            assertFailsWith<DomainException.ValidationException> {
                RequestValidator.validateCreation(
                    rawCategory = "DESCONOCIDA",
                    rawSubject = "   abc ",
                    rawDescription = "  Muy corta ",
                    rawPreviousRequestCode = "REQ-2026-123",
                )
            }

        assertEquals(400, ex.httpStatus)
        assertEquals(
            setOf("category", "subject", "description", "previousRequestCode"),
            ex.details.map { it.field }.toSet(),
        )
    }

    @Test
    fun `validateCreation rejects subject over 100 and description over 2000 chars`() {
        val ex =
            assertFailsWith<DomainException.ValidationException> {
                RequestValidator.validateCreation(
                    rawCategory = "TRAMITE",
                    rawSubject = "X".repeat(101),
                    rawDescription = "Y".repeat(2001),
                    rawPreviousRequestCode = "SOL-2026-1234",
                )
            }

        assertEquals(setOf("subject", "description", "previousRequestCode"), ex.details.map { it.field }.toSet())
        assertTrue(RequestValidator.isValidRequestCode("SOL-2026-00001"))
        assertTrue(RequestValidator.isValidRequestCode("SOL-2026-100000"))
        assertFalse(RequestValidator.isValidRequestCode("SOL-26-00001"))
    }

    @Test
    fun `validateCancellation trims reason allows null or blank and rejects over 500 chars`() {
        assertNull(RequestValidator.validateCancellation(null))
        assertNull(RequestValidator.validateCancellation("   "))
        assertEquals("Ya resolví el caso.", RequestValidator.validateCancellation("  Ya resolví el caso.  "))
        assertEquals("A".repeat(500), RequestValidator.validateCancellation("A".repeat(500)))

        val ex =
            assertFailsWith<DomainException.ValidationException> {
                RequestValidator.validateCancellation("A".repeat(501))
            }
        assertEquals(400, ex.httpStatus)
        assertEquals(listOf("reason"), ex.details.map { it.field })
    }

    @Test
    fun `ApplicantVisibleHistoryEntry fromRaw filters INTERVENCION and hides ESCALAMIENTO or REASIGNACION comments`() {
        val now = OffsetDateTime.now(ZoneOffset.of("-05:00"))

        assertNull(
            ApplicantVisibleHistoryEntry.fromRaw(
                id = UUID.randomUUID(),
                action = HistoryAction.INTERVENCION,
                previousState = RequestStatus.EN_REVISION,
                newState = RequestStatus.EN_REVISION,
                occurredAt = now,
                rawComment = "Nota interna privada",
            ),
        )

        val escalated =
            ApplicantVisibleHistoryEntry.fromRaw(
                id = UUID.randomUUID(),
                action = HistoryAction.ESCALAMIENTO,
                previousState = RequestStatus.EN_REVISION,
                newState = RequestStatus.ESCALADA,
                occurredAt = now,
                rawComment = "Motivo confidencial de escalamiento",
            )
        assertNotNull(escalated)
        assertNull(escalated.comment)

        val reassigned =
            ApplicantVisibleHistoryEntry.fromRaw(
                id = UUID.randomUUID(),
                action = HistoryAction.REASIGNACION,
                previousState = RequestStatus.ESCALADA,
                newState = RequestStatus.EN_REVISION,
                occurredAt = now,
                rawComment = "Motivo confidencial de reasignación",
            )
        assertNotNull(reassigned)
        assertNull(reassigned.comment)

        val created =
            ApplicantVisibleHistoryEntry.fromRaw(
                id = UUID.randomUUID(),
                action = HistoryAction.CREACION,
                previousState = null,
                newState = RequestStatus.ABIERTA,
                occurredAt = now,
                rawComment = "Solicitud registrada por el solicitante.",
            )
        assertNotNull(created)
        assertEquals("Solicitud registrada por el solicitante.", created.comment)
    }
}
