package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.core.time.DateFormatter
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestHistoryAction
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequestDetail
import com.itm.servicedesk.domain.model.ServiceRequestHistoryEntry
import com.itm.servicedesk.domain.model.ServiceRequestSummary
import com.itm.servicedesk.viewmodel.requests.RequestDetailUiModel
import com.itm.servicedesk.viewmodel.requests.RequestHistoryItemUiModel
import com.itm.servicedesk.viewmodel.requests.RequestSummaryUiModel

typealias ServiceRequestUiMapper = RequestUiMapper

class RequestUiMapper {
    fun categoryLabel(category: RequestCategory): String =
        when (category) {
            RequestCategory.TRAMITE -> "Trámite"
            RequestCategory.ORIENTACION -> "Orientación"
            RequestCategory.ATENCION_ADMINISTRATIVA -> "Atención administrativa"
            RequestCategory.REQUERIMIENTO_SERVICIO -> "Requerimiento de servicio"
        }

    fun statusLabel(status: RequestStatus): String =
        when (status) {
            RequestStatus.ABIERTA -> "Abierta"
            RequestStatus.EN_REVISION -> "En revisión"
            RequestStatus.ESCALADA -> "Escalada"
            RequestStatus.RESUELTA -> "Resuelta"
            RequestStatus.CERRADA -> "Cerrada"
            RequestStatus.CANCELADA -> "Cancelada"
        }

    fun actionLabel(action: RequestHistoryAction): String =
        when (action) {
            RequestHistoryAction.CREACION -> "Creación de solicitud"
            RequestHistoryAction.CANCELACION -> "Cancelación de solicitud"
            RequestHistoryAction.ASIGNACION -> "Asignación"
            RequestHistoryAction.INTERVENCION -> "Intervención"
            RequestHistoryAction.ESCALAMIENTO -> "Escalamiento"
            RequestHistoryAction.REASIGNACION -> "Reasignación"
            RequestHistoryAction.RESPUESTA -> "Respuesta registrada"
            RequestHistoryAction.CIERRE_AUTOMATICO -> "Cierre automático"
        }

    fun transitionLabel(
        previousState: RequestStatus?,
        newState: RequestStatus,
    ): String =
        if (previousState == null) {
            "Estado inicial: ${statusLabel(newState)}"
        } else {
            "${statusLabel(previousState)} → ${statusLabel(newState)}"
        }

    fun toSummaryUiModel(summary: ServiceRequestSummary): RequestSummaryUiModel =
        RequestSummaryUiModel(
            id = summary.id,
            code = summary.code,
            subject = summary.subject,
            category = summary.category,
            categoryLabel = categoryLabel(summary.category),
            status = summary.status,
            statusLabel = statusLabel(summary.status),
            previousRequestCode = summary.previousRequestCode,
            formattedCreatedAt = DateFormatter.formatBogotaDateTime(summary.createdAt),
        )

    fun toHistoryItemUiModel(entry: ServiceRequestHistoryEntry): RequestHistoryItemUiModel =
        RequestHistoryItemUiModel(
            id = entry.id,
            actionLabel = actionLabel(entry.action),
            transitionLabel = transitionLabel(entry.previousState, entry.newState),
            formattedOccurredAt = DateFormatter.formatBogotaDateTime(entry.occurredAt),
            comment = entry.comment?.takeIf { it.isNotBlank() },
        )

    fun toDetailUiModel(detail: ServiceRequestDetail): RequestDetailUiModel =
        RequestDetailUiModel(
            id = detail.id,
            code = detail.code,
            category = detail.category,
            categoryLabel = categoryLabel(detail.category),
            status = detail.status,
            statusLabel = statusLabel(detail.status),
            subject = detail.subject,
            description = detail.description,
            previousRequestCode = detail.previousRequestCode,
            responseText = detail.responseText,
            formattedRespondedAt = detail.respondedAt?.let(DateFormatter::formatBogotaDateTime),
            formattedClosedAt = detail.closedAt?.let(DateFormatter::formatBogotaDateTime),
            formattedCreatedAt = DateFormatter.formatBogotaDateTime(detail.createdAt),
            formattedUpdatedAt = DateFormatter.formatBogotaDateTime(detail.updatedAt),
            canCancel = detail.status == RequestStatus.ABIERTA,
            canCreateRelated = detail.status == RequestStatus.RESUELTA || detail.status == RequestStatus.CERRADA,
            history = detail.history.map(::toHistoryItemUiModel),
        )
}
