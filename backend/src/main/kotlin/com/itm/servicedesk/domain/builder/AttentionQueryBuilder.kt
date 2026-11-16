package com.itm.servicedesk.domain.builder

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ValidationDetail
import java.util.UUID

enum class AttentionQueryMode {
    COORDINATION_PENDING,
    AGENT_ASSIGNED,
}

enum class AttentionSortOrder {
    CREATED_AT_ASC,
    DUE_AT_ASC_NULLS_LAST,
}

data class AttentionPageQuery(
    val mode: AttentionQueryMode,
    val assignedTo: UUID? = null,
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE,
    val status: RequestStatus? = null,
    val sortOrder: AttentionSortOrder = AttentionSortOrder.CREATED_AT_ASC,
) {
    val offset: Long
        get() = (page - 1).toLong() * size.toLong()

    companion object {
        const val DEFAULT_PAGE = 1
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 20
        val COORDINATION_ALLOWED_STATUSES = setOf(RequestStatus.ABIERTA, RequestStatus.ESCALADA)
        val AGENT_ALLOWED_STATUSES =
            setOf(
                RequestStatus.EN_REVISION,
                RequestStatus.ESCALADA,
                RequestStatus.RESUELTA,
                RequestStatus.CERRADA,
            )
    }
}

class AttentionQueryBuilder {
    private var modeValue: AttentionQueryMode = AttentionQueryMode.COORDINATION_PENDING
    private var assignedToValue: UUID? = null
    private var pageValue: Int = AttentionPageQuery.DEFAULT_PAGE
    private var pageParseError: Boolean = false

    private var sizeValue: Int = AttentionPageQuery.DEFAULT_SIZE
    private var sizeParseError: Boolean = false

    private var statusValue: RequestStatus? = null
    private var statusInvalidValue: Boolean = false
    private var sortOrderValue: AttentionSortOrder? = null

    fun forCoordinationPending(): AttentionQueryBuilder =
        apply {
            this.modeValue = AttentionQueryMode.COORDINATION_PENDING
            this.assignedToValue = null
            if (this.sortOrderValue == null) {
                this.sortOrderValue = AttentionSortOrder.CREATED_AT_ASC
            }
        }

    fun forAgent(agentId: UUID): AttentionQueryBuilder =
        apply {
            this.modeValue = AttentionQueryMode.AGENT_ASSIGNED
            this.assignedToValue = agentId
            if (this.sortOrderValue == null) {
                this.sortOrderValue = AttentionSortOrder.DUE_AT_ASC_NULLS_LAST
            }
        }

    fun page(page: Int): AttentionQueryBuilder =
        apply {
            this.pageValue = page
            this.pageParseError = false
        }

    fun page(rawPage: String?): AttentionQueryBuilder =
        apply {
            val cleaned = rawPage?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.pageValue = AttentionPageQuery.DEFAULT_PAGE
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

    fun size(size: Int): AttentionQueryBuilder =
        apply {
            this.sizeValue = size
            this.sizeParseError = false
        }

    fun size(rawSize: String?): AttentionQueryBuilder =
        apply {
            val cleaned = rawSize?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.sizeValue = AttentionPageQuery.DEFAULT_SIZE
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

    fun status(status: RequestStatus?): AttentionQueryBuilder =
        apply {
            this.statusValue = status
            this.statusInvalidValue = false
        }

    fun status(rawStatus: String?): AttentionQueryBuilder =
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

    fun sortOrder(sortOrder: AttentionSortOrder): AttentionQueryBuilder =
        apply {
            this.sortOrderValue = sortOrder
        }

    fun build(): AttentionPageQuery {
        val details = mutableListOf<ValidationDetail>()
        val allowedStatuses =
            when (modeValue) {
                AttentionQueryMode.COORDINATION_PENDING -> {
                    AttentionPageQuery.COORDINATION_ALLOWED_STATUSES
                }

                AttentionQueryMode.AGENT_ASSIGNED -> {
                    checkNotNull(assignedToValue) { "El identificador del personal asignado es obligatorio." }
                    AttentionPageQuery.AGENT_ALLOWED_STATUSES
                }
            }

        if (pageParseError) {
            details.add(ValidationDetail("page", "El parámetro 'page' debe ser un número entero válido."))
        } else if (pageValue < 1) {
            details.add(ValidationDetail("page", "El parámetro 'page' debe ser mayor o igual a 1."))
        }

        if (sizeParseError) {
            details.add(ValidationDetail("size", "El parámetro 'size' debe ser un número entero válido."))
        } else if (sizeValue !in 1..AttentionPageQuery.MAX_SIZE) {
            details.add(
                ValidationDetail(
                    "size",
                    "El parámetro 'size' debe estar entre 1 y ${AttentionPageQuery.MAX_SIZE}.",
                ),
            )
        }

        val currentStatus = statusValue
        if (statusInvalidValue || (currentStatus != null && currentStatus !in allowedStatuses)) {
            val allowedNames = allowedStatuses.joinToString(", ") { it.name }
            details.add(
                ValidationDetail(
                    "status",
                    "El parámetro 'status' debe ser uno de: $allowedNames.",
                ),
            )
        }

        if (details.isNotEmpty()) {
            throw DomainException.ValidationException(
                message = "Los datos proporcionados contienen errores de validación.",
                details = details,
            )
        }

        val resolvedSort =
            sortOrderValue ?: when (modeValue) {
                AttentionQueryMode.COORDINATION_PENDING -> AttentionSortOrder.CREATED_AT_ASC
                AttentionQueryMode.AGENT_ASSIGNED -> AttentionSortOrder.DUE_AT_ASC_NULLS_LAST
            }

        return AttentionPageQuery(
            mode = modeValue,
            assignedTo = assignedToValue,
            page = pageValue,
            size = sizeValue,
            status = statusValue,
            sortOrder = resolvedSort,
        )
    }
}
