package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.data.dto.attention.AddInterventionRequestDto
import com.itm.servicedesk.data.dto.attention.AssignRequestDto
import com.itm.servicedesk.data.dto.attention.AttentionRequestDetailDto
import com.itm.servicedesk.data.dto.attention.AttentionRequestSummaryDto
import com.itm.servicedesk.data.dto.attention.EscalateRequestDto
import com.itm.servicedesk.data.dto.attention.InternalRequestHistoryItemDto
import com.itm.servicedesk.data.dto.attention.PagedAttentionRequestsResponseDto
import com.itm.servicedesk.data.dto.attention.ReassignRequestDto
import com.itm.servicedesk.data.dto.attention.RespondRequestDto
import com.itm.servicedesk.domain.model.AddInterventionCommand
import com.itm.servicedesk.domain.model.AssignRequestCommand
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.AttentionRequestSummary
import com.itm.servicedesk.domain.model.AttentionRequestsPage
import com.itm.servicedesk.domain.model.EscalateRequestCommand
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.InternalHistoryEntry
import com.itm.servicedesk.domain.model.ReassignRequestCommand
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.RespondRequestCommand
import kotlin.time.Instant

class AttentionMapper {
    fun toAssignDto(command: AssignRequestCommand): AssignRequestDto =
        AssignRequestDto(
            assigneeId = command.assigneeId.trim(),
            priority = command.priority.name,
        )

    fun toReassignDto(command: ReassignRequestCommand): ReassignRequestDto =
        ReassignRequestDto(
            assigneeId = command.assigneeId.trim(),
            reason = command.reason.trim(),
            priority = command.priority?.name,
        )

    fun toInterventionDto(command: AddInterventionCommand): AddInterventionRequestDto =
        AddInterventionRequestDto(
            comment = command.comment.trim(),
        )

    fun toEscalateDto(command: EscalateRequestCommand): EscalateRequestDto =
        EscalateRequestDto(
            reason = command.reason.trim(),
        )

    fun toRespondDto(command: RespondRequestCommand): RespondRequestDto =
        RespondRequestDto(
            responseText = command.responseText.trim(),
        )

    fun toDomain(dto: AttentionRequestSummaryDto): AttentionRequestSummary =
        AttentionRequestSummary(
            code = dto.code,
            subject = dto.subject,
            category = RequestCategory.valueOf(dto.category.trim().uppercase()),
            status = RequestStatus.valueOf(dto.status.trim().uppercase()),
            priority = dto.priority?.takeIf { it.isNotBlank() }?.let { RequestPriority.valueOf(it.trim().uppercase()) },
            requesterName = dto.requesterName,
            assignedAgentId = dto.assignedAgentId?.takeIf { it.isNotBlank() },
            assignedAgentName = dto.assignedAgentName?.takeIf { it.isNotBlank() },
            createdAt = Instant.parse(dto.createdAt),
            dueAt = dto.dueAt?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            isOverdue = dto.isOverdue,
        )

    fun toDomain(dto: PagedAttentionRequestsResponseDto): AttentionRequestsPage =
        AttentionRequestsPage(
            items = dto.items.map(::toDomain),
            page = dto.page,
            size = dto.size,
            totalItems = dto.totalItems,
            totalPages = dto.totalPages,
        )

    fun toDomain(dto: InternalRequestHistoryItemDto): InternalHistoryEntry =
        InternalHistoryEntry(
            id = dto.id,
            eventType = dto.eventType,
            actorName = dto.actorName?.takeIf { it.isNotBlank() },
            actorRole = HistoryActorRole.valueOf(dto.actorRole.trim().uppercase()),
            previousStatus =
                dto.previousStatus
                    ?.takeIf { it.isNotBlank() }
                    ?.let { RequestStatus.valueOf(it.trim().uppercase()) },
            newStatus =
                dto.newStatus
                    ?.takeIf { it.isNotBlank() }
                    ?.let { RequestStatus.valueOf(it.trim().uppercase()) },
            detail = dto.detail?.takeIf { it.isNotBlank() },
            createdAt = Instant.parse(dto.createdAt),
        )

    fun toDomain(dto: AttentionRequestDetailDto): AttentionRequestDetail =
        AttentionRequestDetail(
            code = dto.code,
            subject = dto.subject,
            description = dto.description,
            category = RequestCategory.valueOf(dto.category.trim().uppercase()),
            status = RequestStatus.valueOf(dto.status.trim().uppercase()),
            priority = dto.priority?.takeIf { it.isNotBlank() }?.let { RequestPriority.valueOf(it.trim().uppercase()) },
            requesterId = dto.requesterId,
            requesterName = dto.requesterName,
            assignedAgentId = dto.assignedAgentId?.takeIf { it.isNotBlank() },
            assignedAgentName = dto.assignedAgentName?.takeIf { it.isNotBlank() },
            previousRequestCode = dto.previousRequestCode?.takeIf { it.isNotBlank() },
            responseText = dto.responseText?.takeIf { it.isNotBlank() },
            createdAt = Instant.parse(dto.createdAt),
            assignedAt = dto.assignedAt?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            dueAt = dto.dueAt?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            respondedAt = dto.respondedAt?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            closedAt = dto.closedAt?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            isOverdue = dto.isOverdue,
            slaCompliant = dto.slaCompliant,
            history = dto.history.map(::toDomain),
        )
}
