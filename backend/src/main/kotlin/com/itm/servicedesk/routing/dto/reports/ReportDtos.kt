package com.itm.servicedesk.routing.dto.reports

import com.itm.servicedesk.domain.model.AssigneeMetric
import com.itm.servicedesk.domain.model.CoordinationReportRequestItem
import com.itm.servicedesk.domain.model.CoordinationReportSummary
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class AssigneeMetricDto(
    val assigneeId: String,
    val assigneeName: String,
    val totalAssigned: Long,
    val overdueAssigned: Long,
)

@Serializable
data class CoordinationReportSummaryDto(
    val generatedAt: String,
    val totalRequests: Long,
    val byStatus: Map<String, Long>,
    val byCategory: Map<String, Long>,
    val byPriority: Map<String, Long>,
    val unassignedPriorityCount: Long,
    val byAssignee: List<AssigneeMetricDto>,
    val unassignedCount: Long,
    val overdueCount: Long,
    val resolvedWithinSlaCount: Long,
    val resolvedOutsideSlaCount: Long,
)

@Serializable
data class CoordinationReportRequestItemDto(
    val id: String,
    val code: String,
    val subject: String,
    val category: String,
    val status: String,
    val priority: String? = null,
    val requesterId: String,
    val requesterName: String,
    val assignedToId: String? = null,
    val assignedToName: String? = null,
    val dueAt: String? = null,
    val isOverdue: Boolean,
    val respondedAt: String? = null,
    val respondedWithinSla: Boolean? = null,
    val closedAt: String? = null,
    val createdAt: String,
)

@Serializable
data class PagedCoordinationReportRequestsResponseDto(
    val items: List<CoordinationReportRequestItemDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

fun AssigneeMetric.toDto(): AssigneeMetricDto =
    AssigneeMetricDto(
        assigneeId = assigneeId.toString(),
        assigneeName = assigneeName,
        totalAssigned = totalAssigned,
        overdueAssigned = overdueAssigned,
    )

fun CoordinationReportSummary.toDto(zoneId: ZoneId): CoordinationReportSummaryDto =
    CoordinationReportSummaryDto(
        generatedAt = generatedAt.atZoneSameInstant(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        totalRequests = totalRequests,
        byStatus = RequestStatus.entries.associate { it.name to (byStatus[it] ?: 0L) },
        byCategory = RequestCategory.entries.associate { it.name to (byCategory[it] ?: 0L) },
        byPriority = RequestPriority.entries.associate { it.name to (byPriority[it] ?: 0L) },
        unassignedPriorityCount = unassignedPriorityCount,
        byAssignee = byAssignee.map { it.toDto() },
        unassignedCount = unassignedCount,
        overdueCount = overdueCount,
        resolvedWithinSlaCount = resolvedWithinSlaCount,
        resolvedOutsideSlaCount = resolvedOutsideSlaCount,
    )

fun CoordinationReportRequestItem.toDto(zoneId: ZoneId): CoordinationReportRequestItemDto =
    CoordinationReportRequestItemDto(
        id = id.toString(),
        code = code,
        subject = subject,
        category = category.name,
        status = status.name,
        priority = priority?.name,
        requesterId = requesterId.toString(),
        requesterName = requesterName,
        assignedToId = assignedToId?.toString(),
        assignedToName = assignedToName,
        dueAt = dueAt?.atZoneSameInstant(zoneId)?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        isOverdue = isOverdue,
        respondedAt = respondedAt?.atZoneSameInstant(zoneId)?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        respondedWithinSla = respondedWithinSla,
        closedAt = closedAt?.atZoneSameInstant(zoneId)?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        createdAt = createdAt.atZoneSameInstant(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )

fun PagedResult<CoordinationReportRequestItem>.toDto(zoneId: ZoneId): PagedCoordinationReportRequestsResponseDto =
    PagedCoordinationReportRequestsResponseDto(
        items = items.map { it.toDto(zoneId) },
        page = page,
        size = size,
        totalItems = totalItems,
        totalPages = totalPages,
    )
