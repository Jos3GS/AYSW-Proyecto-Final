package com.itm.servicedesk.domain.builder

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ValidationDetail
import java.util.UUID

data class MyRequestsQuery(
    val requesterId: UUID,
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE,
    val status: RequestStatus? = null,
) {
    val offset: Long
        get() = (page - 1).toLong() * size.toLong()

    companion object {
        const val DEFAULT_PAGE = 1
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 20
    }
}

class MyRequestsQueryBuilder {
    private var requesterIdValue: UUID? = null
    private var pageValue: Int = MyRequestsQuery.DEFAULT_PAGE
    private var pageParseError: Boolean = false

    private var sizeValue: Int = MyRequestsQuery.DEFAULT_SIZE
    private var sizeParseError: Boolean = false

    private var statusValue: RequestStatus? = null
    private var statusInvalidValue: Boolean = false

    fun requesterId(requesterId: UUID): MyRequestsQueryBuilder =
        apply {
            this.requesterIdValue = requesterId
        }

    fun page(page: Int): MyRequestsQueryBuilder =
        apply {
            this.pageValue = page
            this.pageParseError = false
        }

    fun page(rawPage: String?): MyRequestsQueryBuilder =
        apply {
            val cleaned = rawPage?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.pageValue = MyRequestsQuery.DEFAULT_PAGE
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

    fun size(size: Int): MyRequestsQueryBuilder =
        apply {
            this.sizeValue = size
            this.sizeParseError = false
        }

    fun size(rawSize: String?): MyRequestsQueryBuilder =
        apply {
            val cleaned = rawSize?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.sizeValue = MyRequestsQuery.DEFAULT_SIZE
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

    fun status(status: RequestStatus?): MyRequestsQueryBuilder =
        apply {
            this.statusValue = status
            this.statusInvalidValue = false
        }

    fun status(rawStatus: String?): MyRequestsQueryBuilder =
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

    fun build(): MyRequestsQuery {
        val resolvedRequesterId =
            checkNotNull(requesterIdValue) { "El identificador del solicitante es obligatorio." }
        val details = mutableListOf<ValidationDetail>()

        if (pageParseError) {
            details.add(ValidationDetail("page", "El parámetro 'page' debe ser un número entero válido."))
        } else if (pageValue < 1) {
            details.add(ValidationDetail("page", "El parámetro 'page' debe ser mayor o igual a 1."))
        }

        if (sizeParseError) {
            details.add(ValidationDetail("size", "El parámetro 'size' debe ser un número entero válido."))
        } else if (sizeValue !in 1..MyRequestsQuery.MAX_SIZE) {
            details.add(
                ValidationDetail(
                    "size",
                    "El parámetro 'size' debe estar entre 1 y ${MyRequestsQuery.MAX_SIZE}.",
                ),
            )
        }

        if (statusInvalidValue) {
            details.add(
                ValidationDetail(
                    "status",
                    "El parámetro 'status' debe ser uno de: ABIERTA, EN_REVISION, ESCALADA, RESUELTA, CERRADA o CANCELADA.",
                ),
            )
        }

        if (details.isNotEmpty()) {
            throw DomainException.ValidationException(
                message = "Los datos proporcionados contienen errores de validación.",
                details = details,
            )
        }

        return MyRequestsQuery(
            requesterId = resolvedRequesterId,
            page = pageValue,
            size = sizeValue,
            status = statusValue,
        )
    }
}
