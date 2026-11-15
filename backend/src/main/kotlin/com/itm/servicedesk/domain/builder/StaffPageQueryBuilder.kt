package com.itm.servicedesk.domain.builder

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.model.ValidationDetail

data class StaffPageQuery(
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE,
    val roleFilter: UserRole? = null,
) {
    val offset: Long
        get() = (page - 1).toLong() * size.toLong()

    companion object {
        const val DEFAULT_PAGE = 1
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 20
    }
}

class StaffPageQueryBuilder {
    private var pageValue: Int = StaffPageQuery.DEFAULT_PAGE
    private var pageParseError: Boolean = false

    private var sizeValue: Int = StaffPageQuery.DEFAULT_SIZE
    private var sizeParseError: Boolean = false

    private var roleValue: UserRole? = null
    private var roleInvalidValue: Boolean = false

    fun page(page: Int): StaffPageQueryBuilder =
        apply {
            this.pageValue = page
            this.pageParseError = false
        }

    fun page(rawPage: String?): StaffPageQueryBuilder =
        apply {
            val cleaned = rawPage?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.pageValue = StaffPageQuery.DEFAULT_PAGE
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

    fun size(size: Int): StaffPageQueryBuilder =
        apply {
            this.sizeValue = size
            this.sizeParseError = false
        }

    fun size(rawSize: String?): StaffPageQueryBuilder =
        apply {
            val cleaned = rawSize?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.sizeValue = StaffPageQuery.DEFAULT_SIZE
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

    fun role(role: UserRole?): StaffPageQueryBuilder =
        apply {
            this.roleValue = role
            this.roleInvalidValue = false
        }

    fun role(rawRole: String?): StaffPageQueryBuilder =
        apply {
            val cleaned = rawRole?.trim()
            if (cleaned.isNullOrEmpty()) {
                this.roleValue = null
                this.roleInvalidValue = false
            } else {
                val matched = UserRole.entries.firstOrNull { it.name == cleaned }
                if (matched == null) {
                    this.roleValue = null
                    this.roleInvalidValue = true
                } else {
                    this.roleValue = matched
                    this.roleInvalidValue = false
                }
            }
        }

    fun build(): StaffPageQuery {
        val details = mutableListOf<ValidationDetail>()

        if (pageParseError) {
            details.add(ValidationDetail("page", "El parámetro 'page' debe ser un número entero válido."))
        } else if (pageValue < 1) {
            details.add(ValidationDetail("page", "El parámetro 'page' debe ser mayor o igual a 1."))
        }

        if (sizeParseError) {
            details.add(ValidationDetail("size", "El parámetro 'size' debe ser un número entero válido."))
        } else if (sizeValue !in 1..StaffPageQuery.MAX_SIZE) {
            details.add(
                ValidationDetail(
                    "size",
                    "El parámetro 'size' debe estar entre 1 y ${StaffPageQuery.MAX_SIZE}.",
                ),
            )
        }

        if (roleInvalidValue || roleValue == UserRole.SOLICITANTE) {
            details.add(
                ValidationDetail(
                    "role",
                    "El parámetro 'role' solo permite los valores PERSONAL_ATENCION o COORDINADOR.",
                ),
            )
        }

        if (details.isNotEmpty()) {
            throw DomainException.ValidationException(
                message = "Los datos proporcionados contienen errores de validación.",
                details = details,
            )
        }

        return StaffPageQuery(
            page = pageValue,
            size = sizeValue,
            roleFilter = roleValue,
        )
    }
}
