package com.itm.servicedesk.domain.validation

import com.itm.servicedesk.domain.model.RequestPriority

class AttentionFormValidator {
    fun validateAssigneeId(assigneeId: String): String? =
        if (assigneeId.trim().isEmpty()) {
            "Selecciona un integrante de personal de atención."
        } else {
            null
        }

    fun validatePriority(priority: RequestPriority?): String? =
        if (priority == null) {
            "Selecciona una prioridad SLA."
        } else {
            null
        }

    fun validateReassignmentReason(reason: String): String? {
        val trimmed = reason.trim()
        return when {
            trimmed.isEmpty() -> {
                "El motivo de reasignación es obligatorio."
            }

            trimmed.length !in MIN_REASON_LENGTH..MAX_REASON_LENGTH -> {
                "El motivo de reasignación debe tener entre $MIN_REASON_LENGTH y $MAX_REASON_LENGTH caracteres."
            }

            else -> {
                null
            }
        }
    }

    fun validateInterventionComment(comment: String): String? {
        val trimmed = comment.trim()
        return when {
            trimmed.isEmpty() -> {
                "El comentario de intervención es obligatorio."
            }

            trimmed.length !in MIN_COMMENT_LENGTH..MAX_COMMENT_LENGTH -> {
                "El comentario de intervención debe tener entre $MIN_COMMENT_LENGTH y $MAX_COMMENT_LENGTH caracteres."
            }

            else -> {
                null
            }
        }
    }

    fun validateEscalationReason(reason: String): String? {
        val trimmed = reason.trim()
        return when {
            trimmed.isEmpty() -> {
                "El motivo de escalamiento es obligatorio."
            }

            trimmed.length !in MIN_REASON_LENGTH..MAX_REASON_LENGTH -> {
                "El motivo de escalamiento debe tener entre $MIN_REASON_LENGTH y $MAX_REASON_LENGTH caracteres."
            }

            else -> {
                null
            }
        }
    }

    fun validateResponseText(responseText: String): String? {
        val trimmed = responseText.trim()
        return when {
            trimmed.isEmpty() -> {
                "La respuesta oficial es obligatoria."
            }

            trimmed.length !in MIN_RESPONSE_LENGTH..MAX_RESPONSE_LENGTH -> {
                "La respuesta oficial debe tener entre $MIN_RESPONSE_LENGTH y $MAX_RESPONSE_LENGTH caracteres."
            }

            else -> {
                null
            }
        }
    }

    fun validateAssign(
        assigneeId: String,
        priority: RequestPriority?,
    ): Map<String, String> =
        buildMap {
            validateAssigneeId(assigneeId)?.let { put(FIELD_ASSIGNEE_ID, it) }
            validatePriority(priority)?.let { put(FIELD_PRIORITY, it) }
        }

    fun validateReassign(
        assigneeId: String,
        reason: String,
        @Suppress("UNUSED_PARAMETER")
        priority: RequestPriority? = null,
    ): Map<String, String> =
        buildMap {
            validateAssigneeId(assigneeId)?.let { put(FIELD_ASSIGNEE_ID, it) }
            validateReassignmentReason(reason)?.let { put(FIELD_REASON, it) }
        }

    fun validateIntervention(comment: String): Map<String, String> =
        buildMap {
            validateInterventionComment(comment)?.let { put(FIELD_COMMENT, it) }
        }

    fun validateEscalate(reason: String): Map<String, String> =
        buildMap {
            validateEscalationReason(reason)?.let { put(FIELD_REASON, it) }
        }

    fun validateRespond(responseText: String): Map<String, String> =
        buildMap {
            validateResponseText(responseText)?.let { put(FIELD_RESPONSE_TEXT, it) }
        }

    companion object {
        const val MIN_REASON_LENGTH = 10
        const val MAX_REASON_LENGTH = 500
        const val MIN_COMMENT_LENGTH = 10
        const val MAX_COMMENT_LENGTH = 1000
        const val MIN_RESPONSE_LENGTH = 20
        const val MAX_RESPONSE_LENGTH = 2000

        const val FIELD_ASSIGNEE_ID = "assigneeId"
        const val FIELD_PRIORITY = "priority"
        const val FIELD_REASON = "reason"
        const val FIELD_COMMENT = "comment"
        const val FIELD_RESPONSE_TEXT = "responseText"
    }
}
