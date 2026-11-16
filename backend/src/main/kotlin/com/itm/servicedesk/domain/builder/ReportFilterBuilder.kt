package com.itm.servicedesk.domain.builder

import com.itm.servicedesk.domain.model.CoordinationReportPageQuery
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.ReportFilterCriteria
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ValidationDetail
import com.itm.servicedesk.domain.time.BogotaTimeProvider
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

class ReportFilterBuilder(
    private val zoneId: ZoneId = ZoneId.of(BogotaTimeProvider.DEFAULT_TIMEZONE),
) {
    constructor(timezone: String) : this(ZoneId.of(timezone))

    private var createdFromDate: LocalDate? = null
    private var createdFromParseError: Boolean = false

    private var createdToDate: LocalDate? = null
    private var createdToParseError: Boolean = false

    private var statusValue: RequestStatus? = null
    private var statusInvalidValue: Boolean = false

    private var categoryValue: RequestCategory? = null
    private var categoryInvalidValue: Boolean = false

    private var priorityValue: RequestPriority? = null
    private var priorityInvalidValue: Boolean = false

    private var assignedToIdValue: UUID? = null
    private var assignedToIdInvalidValue: Boolean = false

    private var pageValue: Int = CoordinationReportPageQuery.DEFAULT_PAGE
    private var pageParseError: Boolean = false

    private var sizeValue: Int = CoordinationReportPageQuery.DEFAULT_SIZE
    private var sizeParseError: Boolean = false

    fun createdFrom(date: LocalDate?): ReportFilterBuilder =
        apply {
            this.createdFromDate = date
            this.createdFromParseError = false
        }

    fun createdFrom(rawDate: String?): ReportFilterBuilder =
        apply {
            val cleaned = rawDate?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.createdFromDate = null
                this.createdFromParseError = false
            } else {
                val parsed = parseStrictIsoDate(cleaned)
                if (parsed == null) {
                    this.createdFromDate = null
                    this.createdFromParseError = true
                } else {
                    this.createdFromDate = parsed
                    this.createdFromParseError = false
                }
            }
        }

    fun createdTo(date: LocalDate?): ReportFilterBuilder =
        apply {
            this.createdToDate = date
            this.createdToParseError = false
        }

    fun createdTo(rawDate: String?): ReportFilterBuilder =
        apply {
            val cleaned = rawDate?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.createdToDate = null
                this.createdToParseError = false
            } else {
                val parsed = parseStrictIsoDate(cleaned)
                if (parsed == null) {
                    this.createdToDate = null
                    this.createdToParseError = true
                } else {
                    this.createdToDate = parsed
                    this.createdToParseError = false
                }
            }
        }

    fun status(status: RequestStatus?): ReportFilterBuilder =
        apply {
            this.statusValue = status
            this.statusInvalidValue = false
        }

    fun status(rawStatus: String?): ReportFilterBuilder =
        apply {
            val cleaned = rawStatus?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.statusValue = null
                this.statusInvalidValue = false
            } else {
                val matched = RequestStatus.entries.firstOrNull { it.name == cleaned }
                if (matched == null) {
                    this.statusValue = null
                    this.statusInvalidValue = true
                } else {
                    this.statusValue = matched
                    this.statusInvalidValue = false
                }
            }
        }

    fun category(category: RequestCategory?): ReportFilterBuilder =
        apply {
            this.categoryValue = category
            this.categoryInvalidValue = false
        }

    fun category(rawCategory: String?): ReportFilterBuilder =
        apply {
            val cleaned = rawCategory?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.categoryValue = null
                this.categoryInvalidValue = false
            } else {
                val matched = RequestCategory.entries.firstOrNull { it.name == cleaned }
                if (matched == null) {
                    this.categoryValue = null
                    this.categoryInvalidValue = true
                } else {
                    this.categoryValue = matched
                    this.categoryInvalidValue = false
                }
            }
        }

    fun priority(priority: RequestPriority?): ReportFilterBuilder =
        apply {
            this.priorityValue = priority
            this.priorityInvalidValue = false
        }

    fun priority(rawPriority: String?): ReportFilterBuilder =
        apply {
            val cleaned = rawPriority?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.priorityValue = null
                this.priorityInvalidValue = false
            } else {
                val matched = RequestPriority.entries.firstOrNull { it.name == cleaned }
                if (matched == null) {
                    this.priorityValue = null
                    this.priorityInvalidValue = true
                } else {
                    this.priorityValue = matched
                    this.priorityInvalidValue = false
                }
            }
        }

    fun assignedToId(assignedToId: UUID?): ReportFilterBuilder =
        apply {
            this.assignedToIdValue = assignedToId
            this.assignedToIdInvalidValue = false
        }

    fun assignedToId(rawAssignedToId: String?): ReportFilterBuilder =
        apply {
            val cleaned = rawAssignedToId?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.assignedToIdValue = null
                this.assignedToIdInvalidValue = false
            } else {
                val parsed = parseStrictUuid(cleaned)
                if (parsed == null) {
                    this.assignedToIdValue = null
                    this.assignedToIdInvalidValue = true
                } else {
                    this.assignedToIdValue = parsed
                    this.assignedToIdInvalidValue = false
                }
            }
        }

    fun page(page: Int): ReportFilterBuilder =
        apply {
            this.pageValue = page
            this.pageParseError = false
        }

    fun page(rawPage: String?): ReportFilterBuilder =
        apply {
            val cleaned = rawPage?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.pageValue = CoordinationReportPageQuery.DEFAULT_PAGE
                this.pageParseError = false
            } else {
                val parsed = cleaned.toIntOrNull()
                if (parsed == null) {
                    this.pageParseError = true
                } else {
                    this.pageValue = parsed
                    this.pageParseError = false
                }
            }
        }

    fun size(size: Int): ReportFilterBuilder =
        apply {
            this.sizeValue = size
            this.sizeParseError = false
        }

    fun size(rawSize: String?): ReportFilterBuilder =
        apply {
            val cleaned = rawSize?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.sizeValue = CoordinationReportPageQuery.DEFAULT_SIZE
                this.sizeParseError = false
            } else {
                val parsed = cleaned.toIntOrNull()
                if (parsed == null) {
                    this.sizeParseError = true
                } else {
                    this.sizeValue = parsed
                    this.sizeParseError = false
                }
            }
        }

    fun buildCriteria(): ReportFilterCriteria {
        val details = validateFilterFields()
        if (details.isNotEmpty()) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details = details,
            )
        }
        return toCriteria()
    }

    fun buildPageQuery(): CoordinationReportPageQuery {
        val details = validateFilterFields()

        if (pageParseError) {
            details.add(ValidationDetail("page", "El parámetro 'page' debe ser un número entero válido."))
        } else if (pageValue < 1) {
            details.add(ValidationDetail("page", "El parámetro 'page' debe ser mayor o igual a 1."))
        }

        if (sizeParseError) {
            details.add(ValidationDetail("size", "El parámetro 'size' debe ser un número entero válido."))
        } else if (sizeValue !in 1..CoordinationReportPageQuery.MAX_SIZE) {
            details.add(
                ValidationDetail(
                    "size",
                    "El parámetro 'size' debe estar entre 1 y ${CoordinationReportPageQuery.MAX_SIZE}.",
                ),
            )
        }

        if (details.isNotEmpty()) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details = details,
            )
        }

        return CoordinationReportPageQuery(
            criteria = toCriteria(),
            page = pageValue,
            size = sizeValue,
        )
    }

    private fun validateFilterFields(): MutableList<ValidationDetail> {
        val details = mutableListOf<ValidationDetail>()

        if (createdFromParseError) {
            details.add(
                ValidationDetail("createdFrom", "El parámetro 'createdFrom' debe tener formato ISO YYYY-MM-DD."),
            )
        }
        if (createdToParseError) {
            details.add(
                ValidationDetail("createdTo", "El parámetro 'createdTo' debe tener formato ISO YYYY-MM-DD."),
            )
        }
        val from = createdFromDate
        val to = createdToDate
        if (!createdFromParseError && !createdToParseError && from != null && to != null && from.isAfter(to)) {
            details.add(
                ValidationDetail("createdTo", "La fecha final no puede ser anterior a la fecha inicial."),
            )
        }

        if (statusInvalidValue) {
            val allowed = RequestStatus.entries.joinToString(", ") { it.name }
            details.add(ValidationDetail("status", "El parámetro 'status' debe ser uno de: $allowed."))
        }
        if (categoryInvalidValue) {
            val allowed = RequestCategory.entries.joinToString(", ") { it.name }
            details.add(ValidationDetail("category", "El parámetro 'category' debe ser uno de: $allowed."))
        }
        if (priorityInvalidValue) {
            val allowed = RequestPriority.entries.joinToString(", ") { it.name }
            details.add(ValidationDetail("priority", "El parámetro 'priority' debe ser uno de: $allowed."))
        }
        if (assignedToIdInvalidValue) {
            details.add(ValidationDetail("assignedToId", "El parámetro 'assignedToId' debe ser un UUID válido."))
        }

        return details
    }

    private fun toCriteria(): ReportFilterCriteria =
        ReportFilterCriteria(
            createdFromInclusive = createdFromDate?.atStartOfDay(zoneId)?.toOffsetDateTime(),
            createdToExclusive = createdToDate?.plusDays(1)?.atStartOfDay(zoneId)?.toOffsetDateTime(),
            status = statusValue,
            category = categoryValue,
            priority = priorityValue,
            assignedToId = assignedToIdValue,
        )

    private fun parseStrictIsoDate(raw: String): LocalDate? {
        if (!ISO_DATE_REGEX.matches(raw)) {
            return null
        }
        return runCatching { LocalDate.parse(raw, STRICT_DATE_FORMATTER) }.getOrNull()
    }

    private fun parseStrictUuid(raw: String): UUID? {
        if (!UUID_REGEX.matches(raw)) {
            return null
        }
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    companion object {
        private const val VALIDATION_ERROR_MESSAGE = "Los parámetros de filtro contienen errores."
        private val ISO_DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")
        private val STRICT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
        private val UUID_REGEX =
            Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""")
    }
}
