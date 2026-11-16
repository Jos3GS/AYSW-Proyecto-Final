package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.data.dto.requests.CancelServiceRequestDto
import com.itm.servicedesk.data.dto.requests.CreateServiceRequestDto
import com.itm.servicedesk.data.dto.requests.PagedServiceRequestsResponseDto
import com.itm.servicedesk.data.dto.requests.RequestHistoryItemDto
import com.itm.servicedesk.data.dto.requests.ServiceRequestDetailDto
import com.itm.servicedesk.data.dto.requests.ServiceRequestSummaryDto
import com.itm.servicedesk.domain.model.CancelServiceRequestCommand
import com.itm.servicedesk.domain.model.CreateServiceRequestCommand
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestHistoryAction
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequestDetail
import com.itm.servicedesk.domain.model.ServiceRequestHistoryEntry
import com.itm.servicedesk.domain.model.ServiceRequestSummary
import com.itm.servicedesk.domain.model.ServiceRequestsPage
import com.itm.servicedesk.domain.validation.RequestFormValidator
import kotlin.time.Instant

class RequestMapper {
    fun toCreateDto(command: CreateServiceRequestCommand): CreateServiceRequestDto =
        CreateServiceRequestDto(
            category = command.category.name,
            subject = command.subject.trim(),
            description = command.description.trim(),
            previousRequestCode = RequestFormValidator.normalizePreviousRequestCode(command.previousRequestCode),
        )

    fun toCancelDto(command: CancelServiceRequestCommand): CancelServiceRequestDto =
        CancelServiceRequestDto(
            reason = RequestFormValidator.normalizeCancelReason(command.reason),
        )

    fun toDomain(dto: ServiceRequestSummaryDto): ServiceRequestSummary =
        ServiceRequestSummary(
            id = dto.id,
            code = dto.code,
            subject = dto.subject,
            category = RequestCategory.valueOf(dto.category.trim().uppercase()),
            status = RequestStatus.valueOf(dto.status.trim().uppercase()),
            previousRequestCode = dto.previousRequestCode?.takeIf { it.isNotBlank() },
            createdAt = Instant.parse(dto.createdAt),
        )

    fun toDomain(dto: PagedServiceRequestsResponseDto): ServiceRequestsPage =
        ServiceRequestsPage(
            items = dto.items.map(::toDomain),
            page = dto.page,
            size = dto.size,
            totalItems = dto.totalItems,
            totalPages = dto.totalPages,
        )

    fun toDomain(dto: RequestHistoryItemDto): ServiceRequestHistoryEntry =
        ServiceRequestHistoryEntry(
            id = dto.id,
            action = RequestHistoryAction.valueOf(dto.action.trim().uppercase()),
            previousState = dto.previousState?.let { RequestStatus.valueOf(it.trim().uppercase()) },
            newState = RequestStatus.valueOf(dto.newState.trim().uppercase()),
            occurredAt = Instant.parse(dto.occurredAt),
            comment = dto.comment?.takeIf { it.isNotBlank() },
        )

    fun toDomain(dto: ServiceRequestDetailDto): ServiceRequestDetail =
        ServiceRequestDetail(
            id = dto.id,
            code = dto.code,
            category = RequestCategory.valueOf(dto.category.trim().uppercase()),
            subject = dto.subject,
            description = dto.description,
            status = RequestStatus.valueOf(dto.status.trim().uppercase()),
            previousRequestCode = dto.previousRequestCode?.takeIf { it.isNotBlank() },
            responseText = dto.responseText?.takeIf { it.isNotBlank() },
            respondedAt = dto.respondedAt?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            closedAt = dto.closedAt?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            createdAt = Instant.parse(dto.createdAt),
            updatedAt = Instant.parse(dto.updatedAt),
            history = dto.history.map(::toDomain),
        )
}
