package com.itm.servicedesk.viewmodel.reports

import com.itm.servicedesk.domain.model.RequestStatus

data class BreakdownRowUi(
    val label: String,
    val count: Long,
    val ratio: Float,
)

data class AssigneeBreakdownRowUi(
    val assigneeName: String,
    val totalAssigned: Long,
    val overdueAssigned: Long,
    val ratio: Float,
)

data class CoordinationReportSummaryUi(
    val generatedAtFormatted: String,
    val totalRequests: Long,
    val overdueCount: Long,
    val resolvedWithinSlaCount: Long,
    val resolvedOutsideSlaCount: Long,
    val statusRows: List<BreakdownRowUi>,
    val categoryRows: List<BreakdownRowUi>,
    val priorityRows: List<BreakdownRowUi>,
    val assigneeRows: List<AssigneeBreakdownRowUi>,
    val unassignedCount: Long,
)

data class CoordinationReportRequestItemUi(
    val code: String,
    val subject: String,
    val categoryLabel: String,
    val status: RequestStatus,
    val statusLabel: String,
    val priorityLabel: String,
    val requesterName: String,
    val assignedToName: String,
    val dueAtFormatted: String?,
    val isOverdue: Boolean,
    val respondedAtFormatted: String?,
    val slaResultLabel: String?,
    val createdAtFormatted: String,
)

data class CoordinationReportRequestsPageUi(
    val items: List<CoordinationReportRequestItemUi>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)
