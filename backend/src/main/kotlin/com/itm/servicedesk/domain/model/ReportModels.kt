package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class ReportFilterCriteria(
    val createdFromInclusive: OffsetDateTime? = null,
    val createdToExclusive: OffsetDateTime? = null,
    val status: RequestStatus? = null,
    val category: RequestCategory? = null,
    val priority: RequestPriority? = null,
    val assignedToId: UUID? = null,
)

data class CoordinationReportPageQuery(
    val criteria: ReportFilterCriteria = ReportFilterCriteria(),
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE,
) {
    val offset: Long
        get() = (page - 1).toLong() * size.toLong()

    companion object {
        const val DEFAULT_PAGE = 1
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 20
    }
}

data class AssigneeMetric(
    val assigneeId: UUID,
    val assigneeName: String,
    val totalAssigned: Long,
    val overdueAssigned: Long,
)

data class CoordinationReportSummary(
    val generatedAt: OffsetDateTime,
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
    val id: UUID,
    val code: String,
    val subject: String,
    val category: RequestCategory,
    val status: RequestStatus,
    val priority: RequestPriority?,
    val requesterId: UUID,
    val requesterName: String,
    val assignedToId: UUID?,
    val assignedToName: String?,
    val dueAt: OffsetDateTime?,
    val isOverdue: Boolean,
    val respondedAt: OffsetDateTime?,
    val respondedWithinSla: Boolean?,
    val closedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)
