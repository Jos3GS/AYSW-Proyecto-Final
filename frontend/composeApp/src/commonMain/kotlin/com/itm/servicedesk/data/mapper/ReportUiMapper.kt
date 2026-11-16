package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.core.time.DateFormatter
import com.itm.servicedesk.domain.model.CoordinationReportRequestItem
import com.itm.servicedesk.domain.model.CoordinationReportRequestsPage
import com.itm.servicedesk.domain.model.CoordinationReportSummary
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.viewmodel.reports.AssigneeBreakdownRowUi
import com.itm.servicedesk.viewmodel.reports.BreakdownRowUi
import com.itm.servicedesk.viewmodel.reports.CoordinationReportRequestItemUi
import com.itm.servicedesk.viewmodel.reports.CoordinationReportRequestsPageUi
import com.itm.servicedesk.viewmodel.reports.CoordinationReportSummaryUi

class ReportUiMapper {
    fun statusLabel(status: RequestStatus): String =
        when (status) {
            RequestStatus.ABIERTA -> "Abierta"
            RequestStatus.EN_REVISION -> "En revisión"
            RequestStatus.ESCALADA -> "Escalada"
            RequestStatus.RESUELTA -> "Resuelta"
            RequestStatus.CERRADA -> "Cerrada"
            RequestStatus.CANCELADA -> "Cancelada"
        }

    fun categoryLabel(category: RequestCategory): String =
        when (category) {
            RequestCategory.TRAMITE -> "Trámite"
            RequestCategory.ORIENTACION -> "Orientación"
            RequestCategory.ATENCION_ADMINISTRATIVA -> "Atención administrativa"
            RequestCategory.REQUERIMIENTO_SERVICIO -> "Requerimiento de servicio"
        }

    fun priorityLabel(priority: RequestPriority): String =
        when (priority) {
            RequestPriority.ALTA -> "Alta (24 h)"
            RequestPriority.MEDIA -> "Media (72 h)"
            RequestPriority.BAJA -> "Baja (120 h)"
        }

    fun slaResultLabel(respondedWithinSla: Boolean?): String? =
        when (respondedWithinSla) {
            true -> SLA_WITHIN_LABEL
            false -> SLA_OUTSIDE_LABEL
            null -> null
        }

    fun toSummaryUi(summary: CoordinationReportSummary): CoordinationReportSummaryUi {
        val total = summary.totalRequests
        val statusRows =
            RequestStatus.entries.map { status ->
                val count = summary.byStatus[status] ?: 0L
                BreakdownRowUi(
                    label = statusLabel(status),
                    count = count,
                    ratio = computeRatio(count = count, total = total),
                )
            }
        val categoryRows =
            RequestCategory.entries.map { category ->
                val count = summary.byCategory[category] ?: 0L
                BreakdownRowUi(
                    label = categoryLabel(category),
                    count = count,
                    ratio = computeRatio(count = count, total = total),
                )
            }
        val priorityRows =
            RequestPriority.entries.map { priority ->
                val count = summary.byPriority[priority] ?: 0L
                BreakdownRowUi(
                    label = priorityLabel(priority),
                    count = count,
                    ratio = computeRatio(count = count, total = total),
                )
            } +
                BreakdownRowUi(
                    label = NO_PRIORITY_LABEL,
                    count = summary.unassignedPriorityCount,
                    ratio = computeRatio(count = summary.unassignedPriorityCount, total = total),
                )

        val assigneeRows =
            summary.byAssignee.map { metric ->
                AssigneeBreakdownRowUi(
                    assigneeName = metric.assigneeName,
                    totalAssigned = metric.totalAssigned,
                    overdueAssigned = metric.overdueAssigned,
                    ratio = computeRatio(count = metric.totalAssigned, total = total),
                )
            } +
                AssigneeBreakdownRowUi(
                    assigneeName = NO_ASSIGNEE_LABEL,
                    totalAssigned = summary.unassignedCount,
                    overdueAssigned = 0L,
                    ratio = computeRatio(count = summary.unassignedCount, total = total),
                )

        return CoordinationReportSummaryUi(
            generatedAtFormatted = DateFormatter.formatBogotaDateTime(summary.generatedAt),
            totalRequests = total,
            overdueCount = summary.overdueCount,
            resolvedWithinSlaCount = summary.resolvedWithinSlaCount,
            resolvedOutsideSlaCount = summary.resolvedOutsideSlaCount,
            statusRows = statusRows,
            categoryRows = categoryRows,
            priorityRows = priorityRows,
            assigneeRows = assigneeRows,
            unassignedCount = summary.unassignedCount,
        )
    }

    fun toRequestItemUi(item: CoordinationReportRequestItem): CoordinationReportRequestItemUi =
        CoordinationReportRequestItemUi(
            code = item.code,
            subject = item.subject,
            categoryLabel = categoryLabel(item.category),
            status = item.status,
            statusLabel = statusLabel(item.status),
            priorityLabel = item.priority?.let(::priorityLabel) ?: UNASSIGNED_LABEL,
            requesterName = item.requesterName,
            assignedToName = item.assignedToName?.takeIf { it.isNotBlank() } ?: UNASSIGNED_LABEL,
            dueAtFormatted = item.dueAt?.let(DateFormatter::formatBogotaDateTime),
            isOverdue = item.isOverdue,
            respondedAtFormatted = item.respondedAt?.let(DateFormatter::formatBogotaDateTime),
            slaResultLabel = slaResultLabel(item.respondedWithinSla),
            createdAtFormatted = DateFormatter.formatBogotaDateTime(item.createdAt),
        )

    fun toRequestsPageUi(page: CoordinationReportRequestsPage): CoordinationReportRequestsPageUi =
        CoordinationReportRequestsPageUi(
            items = page.items.map(::toRequestItemUi),
            page = page.page,
            size = page.size,
            totalItems = page.totalItems,
            totalPages = page.totalPages.coerceAtLeast(1),
        )

    private fun computeRatio(
        count: Long,
        total: Long,
    ): Float {
        if (total <= 0L || count <= 0L) {
            return 0f
        }
        return (count.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        const val NO_PRIORITY_LABEL = "Sin prioridad"
        const val NO_ASSIGNEE_LABEL = "Sin responsable"
        const val UNASSIGNED_LABEL = "Sin asignar"
        const val SLA_WITHIN_LABEL = "Dentro del plazo"
        const val SLA_OUTSIDE_LABEL = "Fuera del plazo"
    }
}
