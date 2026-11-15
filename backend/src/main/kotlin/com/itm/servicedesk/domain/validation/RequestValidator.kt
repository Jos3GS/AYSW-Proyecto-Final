package com.itm.servicedesk.domain.validation

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.ValidationDetail

data class ValidatedCreateServiceRequest(
    val category: RequestCategory,
    val subject: String,
    val description: String,
    val previousRequestCode: String?,
)

object RequestValidator {
    private const val MIN_SUBJECT_LENGTH = 5
    private const val MAX_SUBJECT_LENGTH = 100
    private const val MIN_DESCRIPTION_LENGTH = 20
    private const val MAX_DESCRIPTION_LENGTH = 2000
    private const val MAX_CANCEL_REASON_LENGTH = 500
    private const val VALIDATION_ERROR_MESSAGE = "Los datos proporcionados contienen errores de validación."

    private val REQUEST_CODE_REGEX = Regex("^SOL-\\d{4}-\\d{5,}$")

    fun isValidRequestCode(code: String): Boolean = REQUEST_CODE_REGEX.matches(code)

    fun validateCreation(
        rawCategory: String,
        rawSubject: String,
        rawDescription: String,
        rawPreviousRequestCode: String?,
    ): ValidatedCreateServiceRequest {
        val details = mutableListOf<ValidationDetail>()

        val cleanedCategory = rawCategory.trim()
        val resolvedCategory = RequestCategory.entries.firstOrNull { it.name == cleanedCategory }
        if (resolvedCategory == null) {
            details.add(
                ValidationDetail(
                    field = "category",
                    message =
                        "La categoría debe ser una de: TRAMITE, ORIENTACION, " +
                            "ATENCION_ADMINISTRATIVA o REQUERIMIENTO_SERVICIO.",
                ),
            )
        }

        val normalizedSubject = rawSubject.trim()
        if (normalizedSubject.length !in MIN_SUBJECT_LENGTH..MAX_SUBJECT_LENGTH) {
            details.add(
                ValidationDetail(
                    field = "subject",
                    message = "El asunto debe tener entre 5 y 100 caracteres.",
                ),
            )
        }

        val normalizedDescription = rawDescription.trim()
        if (normalizedDescription.length !in MIN_DESCRIPTION_LENGTH..MAX_DESCRIPTION_LENGTH) {
            details.add(
                ValidationDetail(
                    field = "description",
                    message = "La descripción debe tener entre 20 y 2000 caracteres.",
                ),
            )
        }

        val normalizedPreviousCode = rawPreviousRequestCode?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedPreviousCode != null && !isValidRequestCode(normalizedPreviousCode)) {
            details.add(
                ValidationDetail(
                    field = "previousRequestCode",
                    message = "El código de solicitud anterior debe cumplir el formato SOL-AAAA-NNNNN.",
                ),
            )
        }

        if (details.isNotEmpty() || resolvedCategory == null) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details = details,
            )
        }

        return ValidatedCreateServiceRequest(
            category = resolvedCategory,
            subject = normalizedSubject,
            description = normalizedDescription,
            previousRequestCode = normalizedPreviousCode,
        )
    }

    fun validateCancellation(rawReason: String?): String? {
        val normalizedReason = rawReason?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedReason != null && normalizedReason.length > MAX_CANCEL_REASON_LENGTH) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details =
                    listOf(
                        ValidationDetail(
                            field = "reason",
                            message = "El motivo de cancelación no debe superar los 500 caracteres.",
                        ),
                    ),
            )
        }
        return normalizedReason
    }
}
