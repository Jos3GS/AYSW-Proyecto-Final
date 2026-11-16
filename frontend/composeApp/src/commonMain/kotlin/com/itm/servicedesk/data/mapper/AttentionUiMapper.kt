package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.core.time.DateFormatter
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.InternalHistoryEntry
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import kotlin.time.Instant

class AttentionUiMapper {
    fun priorityLabel(priority: RequestPriority): String =
        when (priority) {
            RequestPriority.ALTA -> "Alta (24 h)"
            RequestPriority.MEDIA -> "Media (72 h)"
            RequestPriority.BAJA -> "Baja (120 h)"
        }

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

    fun overdueLabel(isOverdue: Boolean): String? =
        if (isOverdue) {
            OVERDUE_BADGE_LABEL
        } else {
            null
        }

    fun slaComplianceLabel(slaCompliant: Boolean?): String? =
        when (slaCompliant) {
            true -> "Respondida dentro del plazo"
            false -> "Respondida fuera del plazo"
            null -> null
        }

    fun actorLabel(
        actorRole: HistoryActorRole,
        actorName: String?,
    ): String =
        if (actorRole == HistoryActorRole.SISTEMA || actorName.isNullOrBlank()) {
            SYSTEM_ACTOR_LABEL
        } else {
            actorName
        }

    fun actorLabel(entry: InternalHistoryEntry): String =
        actorLabel(actorRole = entry.actorRole, actorName = entry.actorName)

    fun actorRoleLabel(actorRole: HistoryActorRole): String =
        when (actorRole) {
            HistoryActorRole.SOLICITANTE -> "Solicitante"
            HistoryActorRole.PERSONAL_ATENCION -> "Personal de atención"
            HistoryActorRole.COORDINADOR -> "Coordinador"
            HistoryActorRole.SISTEMA -> SYSTEM_ACTOR_LABEL
        }

    fun eventTypeLabel(eventType: String): String =
        when (eventType.trim().uppercase()) {
            "CREACION" -> "Creación de solicitud"
            "CANCELACION" -> "Cancelación de solicitud"
            "ASIGNACION" -> "Asignación"
            "INTERVENCION" -> "Intervención"
            "ESCALAMIENTO" -> "Escalamiento"
            "REASIGNACION" -> "Reasignación"
            "RESPUESTA" -> "Respuesta registrada"
            "CIERRE_AUTOMATICO" -> "Cierre automático"
            else -> eventType
        }

    fun transitionLabel(
        previousStatus: RequestStatus?,
        newStatus: RequestStatus?,
    ): String? =
        when {
            previousStatus == null && newStatus == null -> {
                null
            }

            previousStatus == null && newStatus != null -> {
                "Estado inicial: ${statusLabel(newStatus)}"
            }

            previousStatus != null && newStatus != null -> {
                "${statusLabel(previousStatus)} → ${statusLabel(newStatus)}"
            }

            else -> {
                previousStatus?.let(::statusLabel)
            }
        }

    fun formatDateTime(instant: Instant): String = DateFormatter.formatBogotaDateTime(instant)

    fun formatOptionalDateTime(instant: Instant?): String? = instant?.let(DateFormatter::formatBogotaDateTime)

    companion object {
        const val OVERDUE_BADGE_LABEL = "Vencida"
        const val SYSTEM_ACTOR_LABEL = "Sistema"
    }
}
