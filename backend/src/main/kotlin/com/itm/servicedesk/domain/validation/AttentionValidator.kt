package com.itm.servicedesk.domain.validation

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.ValidationDetail
import java.util.UUID

data class ValidatedAssignCommand(
    val assigneeId: UUID,
    val priority: RequestPriority,
)

data class ValidatedReassignCommand(
    val assigneeId: UUID,
    val reason: String,
    val priority: RequestPriority?,
)

object AttentionValidator {
    private const val MIN_REASON_LENGTH = 10
    private const val MAX_REASON_LENGTH = 500
    private const val MIN_INTERVENTION_LENGTH = 10
    private const val MAX_INTERVENTION_LENGTH = 1000
    private const val MIN_RESPONSE_LENGTH = 20
    private const val MAX_RESPONSE_LENGTH = 2000
    private const val VALIDATION_ERROR_MESSAGE = "Los datos proporcionados contienen errores de validación."

    fun validateAssign(
        rawAssigneeId: String,
        rawPriority: String,
    ): ValidatedAssignCommand {
        val details = mutableListOf<ValidationDetail>()
        val parsedAssigneeId = parseUuidOrNull(rawAssigneeId)
        if (parsedAssigneeId == null) {
            details.add(
                ValidationDetail(
                    field = "assigneeId",
                    message = "El identificador del responsable debe ser un UUID válido.",
                ),
            )
        }

        val cleanedPriority = rawPriority.trim()
        val parsedPriority = RequestPriority.entries.firstOrNull { it.name == cleanedPriority }
        if (parsedPriority == null) {
            details.add(
                ValidationDetail(
                    field = "priority",
                    message = "La prioridad debe ser una de: ALTA, MEDIA o BAJA.",
                ),
            )
        }

        if (details.isNotEmpty() || parsedAssigneeId == null || parsedPriority == null) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details = details,
            )
        }

        return ValidatedAssignCommand(
            assigneeId = parsedAssigneeId,
            priority = parsedPriority,
        )
    }

    fun validateReassign(
        rawAssigneeId: String,
        rawReason: String,
        rawPriority: String?,
    ): ValidatedReassignCommand {
        val details = mutableListOf<ValidationDetail>()
        val parsedAssigneeId = parseUuidOrNull(rawAssigneeId)
        if (parsedAssigneeId == null) {
            details.add(
                ValidationDetail(
                    field = "assigneeId",
                    message = "El identificador del responsable debe ser un UUID válido.",
                ),
            )
        }

        val normalizedReason = rawReason.trim()
        if (normalizedReason.length !in MIN_REASON_LENGTH..MAX_REASON_LENGTH) {
            details.add(
                ValidationDetail(
                    field = "reason",
                    message = "El motivo de reasignación debe tener entre 10 y 500 caracteres.",
                ),
            )
        }

        val cleanedPriority = rawPriority?.trim()?.takeIf { it.isNotEmpty() }
        val parsedPriority =
            if (cleanedPriority != null) {
                val matched = RequestPriority.entries.firstOrNull { it.name == cleanedPriority }
                if (matched == null) {
                    details.add(
                        ValidationDetail(
                            field = "priority",
                            message = "La prioridad debe ser una de: ALTA, MEDIA o BAJA.",
                        ),
                    )
                }
                matched
            } else {
                null
            }

        if (details.isNotEmpty() || parsedAssigneeId == null) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details = details,
            )
        }

        return ValidatedReassignCommand(
            assigneeId = parsedAssigneeId,
            reason = normalizedReason,
            priority = parsedPriority,
        )
    }

    fun validateIntervention(rawComment: String): String {
        val normalizedComment = rawComment.trim()
        if (normalizedComment.length !in MIN_INTERVENTION_LENGTH..MAX_INTERVENTION_LENGTH) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details =
                    listOf(
                        ValidationDetail(
                            field = "comment",
                            message = "El comentario de intervención debe tener entre 10 y 1000 caracteres.",
                        ),
                    ),
            )
        }
        return normalizedComment
    }

    fun validateEscalation(rawReason: String): String {
        val normalizedReason = rawReason.trim()
        if (normalizedReason.length !in MIN_REASON_LENGTH..MAX_REASON_LENGTH) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details =
                    listOf(
                        ValidationDetail(
                            field = "reason",
                            message = "El motivo de escalamiento debe tener entre 10 y 500 caracteres.",
                        ),
                    ),
            )
        }
        return normalizedReason
    }

    fun validateResponse(rawResponseText: String): String {
        val normalizedResponse = rawResponseText.trim()
        if (normalizedResponse.length !in MIN_RESPONSE_LENGTH..MAX_RESPONSE_LENGTH) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details =
                    listOf(
                        ValidationDetail(
                            field = "responseText",
                            message = "La respuesta final debe tener entre 20 y 2000 caracteres.",
                        ),
                    ),
            )
        }
        return normalizedResponse
    }

    private fun parseUuidOrNull(raw: String): UUID? =
        raw
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}
