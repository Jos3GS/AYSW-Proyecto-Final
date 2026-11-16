package com.itm.servicedesk.domain.validation

import com.itm.servicedesk.domain.model.RequestPriority
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AttentionFormValidatorTest {
    private val validator = AttentionFormValidator()

    @Test
    fun `validateAssign requires non-blank assigneeId and non-null priority`() {
        assertTrue(validator.validateAssign("agent-1", RequestPriority.ALTA).isEmpty())
        assertTrue(validator.validateAssign("  agent-1  ", RequestPriority.MEDIA).isEmpty())

        val missingBoth = validator.validateAssign("   ", null)
        assertNotNull(missingBoth[AttentionFormValidator.FIELD_ASSIGNEE_ID])
        assertNotNull(missingBoth[AttentionFormValidator.FIELD_PRIORITY])

        val missingPriority = validator.validateAssign("agent-1", null)
        assertNotNull(missingPriority[AttentionFormValidator.FIELD_PRIORITY])
    }

    @Test
    fun `validateReassign validates assigneeId, reason 10 to 500 trimmed, and optional priority`() {
        assertTrue(
            validator
                .validateReassign(
                    assigneeId = "agent-2",
                    reason = "  ${"a".repeat(10)}  ",
                    priority = null,
                ).isEmpty(),
        )
        assertTrue(
            validator
                .validateReassign(
                    assigneeId = "agent-2",
                    reason = "b".repeat(500),
                    priority = RequestPriority.BAJA,
                ).isEmpty(),
        )

        val tooShort = validator.validateReassign("agent-2", "  ${"a".repeat(9)}  ", null)
        assertNotNull(tooShort[AttentionFormValidator.FIELD_REASON])

        val tooLong = validator.validateReassign("agent-2", "a".repeat(501), RequestPriority.ALTA)
        assertNotNull(tooLong[AttentionFormValidator.FIELD_REASON])

        val blankInputs = validator.validateReassign(" ", "   ", null)
        assertNotNull(blankInputs[AttentionFormValidator.FIELD_ASSIGNEE_ID])
        assertNotNull(blankInputs[AttentionFormValidator.FIELD_REASON])
    }

    @Test
    fun `validateIntervention enforces trimmed length 10 to 1000`() {
        assertTrue(validator.validateIntervention("  ${"c".repeat(10)}  ").isEmpty())
        assertTrue(validator.validateIntervention("c".repeat(1000)).isEmpty())

        assertNotNull(validator.validateIntervention("   ")[AttentionFormValidator.FIELD_COMMENT])
        assertNotNull(validator.validateIntervention("  ${"c".repeat(9)}  ")[AttentionFormValidator.FIELD_COMMENT])
        assertNotNull(validator.validateIntervention("c".repeat(1001))[AttentionFormValidator.FIELD_COMMENT])
    }

    @Test
    fun `validateEscalate enforces trimmed length 10 to 500`() {
        assertTrue(validator.validateEscalate("  ${"e".repeat(10)}  ").isEmpty())
        assertTrue(validator.validateEscalate("e".repeat(500)).isEmpty())

        assertNotNull(validator.validateEscalate("   ")[AttentionFormValidator.FIELD_REASON])
        assertNotNull(validator.validateEscalate("  ${"e".repeat(9)}  ")[AttentionFormValidator.FIELD_REASON])
        assertNotNull(validator.validateEscalate("e".repeat(501))[AttentionFormValidator.FIELD_REASON])
    }

    @Test
    fun `validateRespond enforces trimmed length 20 to 2000`() {
        assertTrue(validator.validateRespond("  ${"r".repeat(20)}  ").isEmpty())
        assertTrue(validator.validateRespond("r".repeat(2000)).isEmpty())

        assertNotNull(validator.validateRespond("   ")[AttentionFormValidator.FIELD_RESPONSE_TEXT])
        assertNotNull(validator.validateRespond("  ${"r".repeat(19)}  ")[AttentionFormValidator.FIELD_RESPONSE_TEXT])
        assertNotNull(validator.validateRespond("r".repeat(2001))[AttentionFormValidator.FIELD_RESPONSE_TEXT])
    }
}
