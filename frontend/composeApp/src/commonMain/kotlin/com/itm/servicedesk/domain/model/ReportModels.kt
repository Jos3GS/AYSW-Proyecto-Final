package com.itm.servicedesk.domain.model

import kotlin.time.Instant

data class ReportFilterQuery(
    val createdFromIso: String? = null,
    val createdToIso: String? = null,
    val status: RequestStatus? = null,
    val category: RequestCategory? = null,
    val priority: RequestPriority? = null,
    val assignedToId: String? = null,
)

data class AssigneeMetric(
    val assigneeId: String,
    val assigneeName: String,
    val totalAssigned: Long,
    val overdueAssigned: Long,
)

data class CoordinationReportSummary(
    val generatedAt: Instant,
    val totalRequests: Long,
    val byStatus: Map<RequestStatus, Long>,
    val byCategory: Map<RequestCategory, Long>,
    val byPriority: Map<RequestPriority, Long>,
    val unassignedPriorityCount: Long,
    val byAssignee: List<AssigneeMetric>,
    val unassignedCount: Long,
    val overdueCount: Long,
    val resolvedWithinSlaCount: Long,
    val resolvedOutsideSlaCount: Long,
)

data class CoordinationReportRequestItem(
    val id: String,
    val code: String,
    val subject: String,
    val category: RequestCategory,
    val status: RequestStatus,
    val priority: RequestPriority?,
    val requesterId: String,
    val requesterName: String,
    val assignedToId: String?,
    val assignedToName: String?,
    val dueAt: Instant?,
    val isOverdue: Boolean,
    val respondedAt: Instant?,
    val respondedWithinSla: Boolean?,
    val closedAt: Instant?,
    val createdAt: Instant,
)

data class CoordinationReportRequestsPage(
    val items: List<CoordinationReportRequestItem>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)
