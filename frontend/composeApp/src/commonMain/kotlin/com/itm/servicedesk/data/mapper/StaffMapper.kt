package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.data.dto.CreateStaffRequestDto
import com.itm.servicedesk.data.dto.PagedStaffResponseDto
import com.itm.servicedesk.data.dto.StaffSummaryDto
import com.itm.servicedesk.domain.model.CreateStaffCommand
import com.itm.servicedesk.domain.model.StaffMember
import com.itm.servicedesk.domain.model.StaffPage
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.validation.UserFormValidator
import kotlin.time.Instant

class StaffMapper {
    fun toCreateStaffRequestDto(command: CreateStaffCommand): CreateStaffRequestDto =
        CreateStaffRequestDto(
            fullName = command.fullName.trim(),
            documentNumber = command.documentNumber.trim(),
            email = UserFormValidator.normalizeEmail(command.email),
            phone = command.phone.trim(),
            password = command.password,
            role = command.role.name,
        )

    fun toDomain(dto: StaffSummaryDto): StaffMember =
        StaffMember(
            id = dto.id,
            fullName = dto.fullName,
            email = dto.email,
            role = UserRole.valueOf(dto.role.trim().uppercase()),
            createdAt = Instant.parse(dto.createdAt),
        )

    fun toDomain(dto: PagedStaffResponseDto): StaffPage =
        StaffPage(
            items = dto.items.map(::toDomain),
            page = dto.page,
            size = dto.size,
            totalItems = dto.totalItems,
            totalPages = dto.totalPages,
        )
}
