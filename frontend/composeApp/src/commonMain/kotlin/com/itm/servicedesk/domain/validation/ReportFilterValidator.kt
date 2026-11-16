package com.itm.servicedesk.domain.validation

import com.itm.servicedesk.domain.model.ReportFilterQuery
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

sealed interface ReportFilterValidationResult {
    data class Valid(
        val query: ReportFilterQuery,
    ) : ReportFilterValidationResult

    data class Invalid(
        val fromDateError: String? = null,
        val toDateError: String? = null,
    ) : ReportFilterValidationResult
}

class ReportFilterValidator {
    fun validate(
        fromDateInput: String,
        toDateInput: String,
        status: RequestStatus? = null,
        category: RequestCategory? = null,
        priority: RequestPriority? = null,
        assignedToId: String? = null,
    ): ReportFilterValidationResult {
        val fromResult = parseOptionalDate(fromDateInput)
        val toResult = parseOptionalDate(toDateInput)

        val fromError = if (fromResult.isInvalid) INVALID_DATE_MESSAGE else null
        val toError = if (toResult.isInvalid) INVALID_DATE_MESSAGE else null

        if (fromError != null || toError != null) {
            return ReportFilterValidationResult.Invalid(
                fromDateError = fromError,
                toDateError = toError,
            )
        }

        val fromDate = fromResult.date
        val toDate = toResult.date
        if (fromDate != null && toDate != null && fromDate > toDate) {
            return ReportFilterValidationResult.Invalid(
                fromDateError = null,
                toDateError = INVALID_RANGE_MESSAGE,
            )
        }

        return ReportFilterValidationResult.Valid(
            query =
                ReportFilterQuery(
                    createdFromIso = fromDate?.let(::toIsoDate),
                    createdToIso = toDate?.let(::toIsoDate),
                    status = status,
                    category = category,
                    priority = priority,
                    assignedToId = assignedToId?.trim()?.takeIf { it.isNotEmpty() },
                ),
        )
    }

    private fun parseOptionalDate(rawInput: String): ParsedDateResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ParsedDateResult(date = null, isInvalid = false)
        }
        if (!DATE_INPUT_REGEX.matches(trimmed)) {
            return ParsedDateResult(date = null, isInvalid = true)
        }

        val parts = trimmed.split('/')
        val day = parts[0].toIntOrNull() ?: return ParsedDateResult(date = null, isInvalid = true)
        val month = parts[1].toIntOrNull() ?: return ParsedDateResult(date = null, isInvalid = true)
        val year = parts[2].toIntOrNull() ?: return ParsedDateResult(date = null, isInvalid = true)
        if (year < 1) {
            return ParsedDateResult(date = null, isInvalid = true)
        }

        val parsed =
            runCatching {
                LocalDate(year = year, month = month, day = day)
            }.getOrNull()

        return if (parsed != null) {
            ParsedDateResult(date = parsed, isInvalid = false)
        } else {
            ParsedDateResult(date = null, isInvalid = true)
        }
    }

    private fun toIsoDate(date: LocalDate): String {
        val year = date.year.toString().padStart(4, '0')
        val month =
            date.month.number
                .toString()
                .padStart(2, '0')
        val day = date.day.toString().padStart(2, '0')
        return "$year-$month-$day"
    }

    private data class ParsedDateResult(
        val date: LocalDate?,
        val isInvalid: Boolean,
    )

    companion object {
        private val DATE_INPUT_REGEX = Regex("^\\d{2}/\\d{2}/\\d{4}$")

        const val INVALID_DATE_MESSAGE = "Ingresa una fecha válida en formato dd/mm/aaaa."
        const val INVALID_RANGE_MESSAGE = "La fecha final no puede ser anterior a la fecha inicial."
    }
}
