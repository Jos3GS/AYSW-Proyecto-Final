package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.data.dto.reports.AssigneeMetricDto
import com.itm.servicedesk.data.dto.reports.CoordinationReportRequestItemDto
import com.itm.servicedesk.data.dto.reports.CoordinationReportSummaryDto
import com.itm.servicedesk.data.dto.reports.PagedCoordinationReportRequestsResponseDto
import com.itm.servicedesk.domain.model.AssigneeMetric
import com.itm.servicedesk.domain.model.CoordinationReportRequestItem
import com.itm.servicedesk.domain.model.CoordinationReportRequestsPage
import com.itm.servicedesk.domain.model.CoordinationReportSummary
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import kotlin.time.Instant

class ReportMapper {
    fun toDomain(dto: AssigneeMetricDto): AssigneeMetric =
        AssigneeMetric(
            assigneeId = dto.assigneeId,
            assigneeName = dto.assigneeName,
            totalAssigned = dto.totalAssigned,
            overdueAssigned = dto.overdueAssigned,
        )

    fun toDomain(dto: CoordinationReportSummaryDto): CoordinationReportSummary =
        CoordinationReportSummary(
            generatedAt = Instant.parse(dto.generatedAt),
            totalRequests = dto.totalRequests,
            byStatus =
                dto.byStatus.entries.associate { (key, count) ->
                    RequestStatus.valueOf(key.trim().uppercase()) to count
                },
            byCategory =
                dto.byCategory.entries.associate { (key, count) ->
                    RequestCategory.valueOf(key.trim().uppercase()) to count
                },
            byPriority =
                dto.byPriority.entries.associate { (key, count) ->
                    RequestPriority.valueOf(key.trim().uppercase()) to count
                },
            unassignedPriorityCount = dto.unassignedPriorityCount,
            byAssignee = dto.byAssignee.map(::toDomain),
            unassignedCount = dto.unassignedCount,
            overdueCount = dto.overdueCount,
            resolvedWithinSlaCount = dto.resolvedWithinSlaCount,
            resolvedOutsideSlaCount = dto.resolvedOutsideSlaCount,
        )

    fun toDomain(dto: CoordinationReportRequestItemDto): CoordinationReportRequestItem =
        CoordinationReportRequestItem(
            id = dto.id,
            code = dto.code,
            subject = dto.subject,
            category = RequestCategory.valueOf(dto.category.trim().uppercase()),
            status = RequestStatus.valueOf(dto.status.trim().uppercase()),
            priority =
                dto.priority
                    ?.takeIf { it.isNotBlank() }
                    ?.let { RequestPriority.valueOf(it.trim().uppercase()) },
            requesterId = dto.requesterId,
            requesterName = dto.requesterName,
            assignedToId = dto.assignedToId?.takeIf { it.isNotBlank() },
            assignedToName = dto.assignedToName?.takeIf { it.isNotBlank() },
            dueAt = dto.dueAt?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            isOverdue = dto.isOverdue,
            respondedAt = dto.respondedAt?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            respondedWithinSla = dto.respondedWithinSla,
            closedAt = dto.closedAt?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            createdAt = Instant.parse(dto.createdAt),
        )

    fun toDomain(dto: PagedCoordinationReportRequestsResponseDto): CoordinationReportRequestsPage =
        CoordinationReportRequestsPage(
            items = dto.items.map(::toDomain),
            page = dto.page,
            size = dto.size,
            totalItems = dto.totalItems,
            totalPages = dto.totalPages,
        )
}
