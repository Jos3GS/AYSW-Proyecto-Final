package com.itm.servicedesk.data.dto.reports

import kotlinx.serialization.Serializable

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
