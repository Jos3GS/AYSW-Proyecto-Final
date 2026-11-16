package com.itm.servicedesk.domain.validation

import com.itm.servicedesk.domain.model.RequestCategory

object RequestFormValidator {
    const val MIN_SUBJECT_LENGTH = 5
    const val MAX_SUBJECT_LENGTH = 100
    const val MIN_DESCRIPTION_LENGTH = 20
    const val MAX_DESCRIPTION_LENGTH = 2000
    const val MAX_CANCEL_REASON_LENGTH = 500

    const val FIELD_CATEGORY = "category"
    const val FIELD_SUBJECT = "subject"
    const val FIELD_DESCRIPTION = "description"
    const val FIELD_PREVIOUS_REQUEST_CODE = "previousRequestCode"
    const val FIELD_CANCEL_REASON = "reason"

    private val REQUEST_CODE_REGEX = Regex("^SOL-\\d{4}-\\d{5,}$")

    fun normalizePreviousRequestCode(previousRequestCode: String?): String? =
        previousRequestCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase()

    fun normalizeCancelReason(reason: String?): String? =
        reason
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun validateCategory(category: RequestCategory?): String? =
        if (category == null) {
            "Selecciona una categoría válida."
        } else {
            null
        }

    fun validateSubject(subject: String): String? {
        val trimmed = subject.trim()
        return if (trimmed.isEmpty()) {
            "El asunto es obligatorio."
        } else if (trimmed.length !in MIN_SUBJECT_LENGTH..MAX_SUBJECT_LENGTH) {
            "El asunto debe tener entre 5 y 100 caracteres."
        } else {
            null
        }
    }

    fun validateDescription(description: String): String? {
        val trimmed = description.trim()
        return if (trimmed.isEmpty()) {
            "La descripción es obligatoria."
        } else if (trimmed.length !in MIN_DESCRIPTION_LENGTH..MAX_DESCRIPTION_LENGTH) {
            "La descripción debe tener entre 20 y 2000 caracteres."
        } else {
            null
        }
    }

    fun validatePreviousRequestCode(previousRequestCode: String?): String? {
        val normalized = normalizePreviousRequestCode(previousRequestCode) ?: return null
        return if (!REQUEST_CODE_REGEX.matches(normalized)) {
            "El código de solicitud anterior debe cumplir el formato SOL-AAAA-NNNNN."
        } else {
            null
        }
    }

    fun validateCancelReason(reason: String?): String? {
        val normalized = normalizeCancelReason(reason) ?: return null
        return if (normalized.length > MAX_CANCEL_REASON_LENGTH) {
            "El motivo de cancelación no debe superar los 500 caracteres."
        } else {
            null
        }
    }

    fun validateCreateRequest(
        category: RequestCategory?,
        subject: String,
        description: String,
        previousRequestCode: String?,
    ): Map<String, String> =
        buildMap {
            validateCategory(category)?.let { put(FIELD_CATEGORY, it) }
            validateSubject(subject)?.let { put(FIELD_SUBJECT, it) }
            validateDescription(description)?.let { put(FIELD_DESCRIPTION, it) }
            validatePreviousRequestCode(previousRequestCode)?.let { put(FIELD_PREVIOUS_REQUEST_CODE, it) }
        }
}
