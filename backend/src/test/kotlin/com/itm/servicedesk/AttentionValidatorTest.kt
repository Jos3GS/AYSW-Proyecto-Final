package com.itm.servicedesk

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.time.BogotaTimeProvider
import com.itm.servicedesk.domain.validation.AttentionValidator
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AttentionValidatorTest {
    @Test
    fun `BogotaTimeProvider returns OffsetDateTime in -05 00 zone`() {
        val provider = BogotaTimeProvider()
        assertEquals(ZoneOffset.of("-05:00"), provider.now().offset)

        val customProvider = BogotaTimeProvider("America/Bogota")
        assertEquals(ZoneOffset.of("-05:00"), customProvider.now().offset)
    }

    @Test
    fun `validateAssign trims inputs parses UUID and RequestPriority and accumulates errors`() {
        val validUuid = UUID.randomUUID()
        RequestPriority.entries.forEach { priority ->
            val validated =
                AttentionValidator.validateAssign(
                    rawAssigneeId = "  $validUuid  ",
                    rawPriority = "  ${priority.name}  ",
                )
            assertEquals(validUuid, validated.assigneeId)
            assertEquals(priority, validated.priority)
        }

        val ex =
            assertFailsWith<DomainException.ValidationException> {
                AttentionValidator.validateAssign(
                    rawAssigneeId = "not-a-uuid",
                    rawPriority = "URGENTE",
                )
            }
        assertEquals(400, ex.httpStatus)
        assertEquals(setOf("assigneeId", "priority"), ex.details.map { it.field }.toSet())
    }

    @Test
    fun `validateReassign validates assigneeId reason 10 to 500 chars and optional priority`() {
        val validUuid = UUID.randomUUID()
        val minReason = "1234567890"
        val maxReason = "R".repeat(500)

        val withoutPriority =
            AttentionValidator.validateReassign(
                rawAssigneeId = validUuid.toString(),
                rawReason = "  $minReason  ",
                rawPriority = "   ",
            )
        assertEquals(validUuid, withoutPriority.assigneeId)
        assertEquals(minReason, withoutPriority.reason)
        assertNull(withoutPriority.priority)

        val withPriority =
            AttentionValidator.validateReassign(
                rawAssigneeId = validUuid.toString(),
                rawReason = maxReason,
                rawPriority = "ALTA",
            )
        assertEquals(RequestPriority.ALTA, withPriority.priority)
        assertEquals(maxReason, withPriority.reason)

        val ex =
            assertFailsWith<DomainException.ValidationException> {
                AttentionValidator.validateReassign(
                    rawAssigneeId = "",
                    rawReason = "corto",
                    rawPriority = "INVALIDA",
                )
            }
        assertEquals(400, ex.httpStatus)
        assertEquals(setOf("assigneeId", "reason", "priority"), ex.details.map { it.field }.toSet())

        val tooLongEx =
            assertFailsWith<DomainException.ValidationException> {
                AttentionValidator.validateReassign(
                    rawAssigneeId = validUuid.toString(),
                    rawReason = "R".repeat(501),
                    rawPriority = null,
                )
            }
        assertEquals(listOf("reason"), tooLongEx.details.map { it.field })
    }

    @Test
    fun `validateIntervention enforces 10 to 1000 chars after trim`() {
        assertEquals("1234567890", AttentionValidator.validateIntervention("  1234567890  "))
        assertEquals("C".repeat(1000), AttentionValidator.validateIntervention("C".repeat(1000)))

        val shortEx =
            assertFailsWith<DomainException.ValidationException> {
                AttentionValidator.validateIntervention("  123456789  ")
            }
        assertEquals(listOf("comment"), shortEx.details.map { it.field })

        val longEx =
            assertFailsWith<DomainException.ValidationException> {
                AttentionValidator.validateIntervention("C".repeat(1001))
            }
        assertEquals(listOf("comment"), longEx.details.map { it.field })
    }

    @Test
    fun `validateEscalation enforces 10 to 500 chars after trim`() {
        assertEquals("1234567890", AttentionValidator.validateEscalation("  1234567890  "))
        assertEquals("E".repeat(500), AttentionValidator.validateEscalation("E".repeat(500)))

        val shortEx =
            assertFailsWith<DomainException.ValidationException> {
                AttentionValidator.validateEscalation("   breve   ")
            }
        assertEquals(listOf("reason"), shortEx.details.map { it.field })

        val longEx =
            assertFailsWith<DomainException.ValidationException> {
                AttentionValidator.validateEscalation("E".repeat(501))
            }
        assertEquals(listOf("reason"), longEx.details.map { it.field })
    }

    @Test
    fun `validateResponse enforces 20 to 2000 chars after trim`() {
        val minResp = "12345678901234567890"
        val maxResp = "X".repeat(2000)
        assertEquals(minResp, AttentionValidator.validateResponse("  $minResp  "))
        assertEquals(maxResp, AttentionValidator.validateResponse(maxResp))

        val shortEx =
            assertFailsWith<DomainException.ValidationException> {
                AttentionValidator.validateResponse("  1234567890123456789  ")
            }
        assertEquals(listOf("responseText"), shortEx.details.map { it.field })

        val longEx =
            assertFailsWith<DomainException.ValidationException> {
                AttentionValidator.validateResponse("X".repeat(2001))
            }
        assertEquals(listOf("responseText"), longEx.details.map { it.field })
    }
}
