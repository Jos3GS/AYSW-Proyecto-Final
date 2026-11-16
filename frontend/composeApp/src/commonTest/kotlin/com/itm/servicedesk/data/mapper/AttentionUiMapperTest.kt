package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.InternalHistoryEntry
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class AttentionUiMapperTest {
    private val uiMapper = AttentionUiMapper()

    @Test
    fun `maps mandatory Spanish priority, overdue, SLA compliance, and System actor labels`() {
        assertEquals("Alta (24 h)", uiMapper.priorityLabel(RequestPriority.ALTA))
        assertEquals("Media (72 h)", uiMapper.priorityLabel(RequestPriority.MEDIA))
        assertEquals("Baja (120 h)", uiMapper.priorityLabel(RequestPriority.BAJA))

        assertEquals("Vencida", uiMapper.overdueLabel(true))
        assertNull(uiMapper.overdueLabel(false))

        assertEquals("Respondida dentro del plazo", uiMapper.slaComplianceLabel(true))
        assertEquals("Respondida fuera del plazo", uiMapper.slaComplianceLabel(false))
        assertNull(uiMapper.slaComplianceLabel(null))

        assertEquals("Sistema", uiMapper.actorLabel(HistoryActorRole.SISTEMA, null))
        assertEquals("Sistema", uiMapper.actorLabel(HistoryActorRole.SISTEMA, "AutoJob"))
        assertEquals("Sistema", uiMapper.actorLabel(HistoryActorRole.COORDINADOR, null))
        assertEquals("Sistema", uiMapper.actorLabel(HistoryActorRole.COORDINADOR, "   "))
        assertEquals("Ana Coordinadora", uiMapper.actorLabel(HistoryActorRole.COORDINADOR, "Ana Coordinadora"))
    }

    @Test
    fun `maps categories, statuses, event types, transitions, and Bogota timestamps`() {
        assertEquals("Trámite", uiMapper.categoryLabel(RequestCategory.TRAMITE))
        assertEquals("Orientación", uiMapper.categoryLabel(RequestCategory.ORIENTACION))
        assertEquals("Atención administrativa", uiMapper.categoryLabel(RequestCategory.ATENCION_ADMINISTRATIVA))
        assertEquals("Requerimiento de servicio", uiMapper.categoryLabel(RequestCategory.REQUERIMIENTO_SERVICIO))

        assertEquals("Abierta", uiMapper.statusLabel(RequestStatus.ABIERTA))
        assertEquals("En revisión", uiMapper.statusLabel(RequestStatus.EN_REVISION))
        assertEquals("Escalada", uiMapper.statusLabel(RequestStatus.ESCALADA))
        assertEquals("Resuelta", uiMapper.statusLabel(RequestStatus.RESUELTA))
        assertEquals("Cerrada", uiMapper.statusLabel(RequestStatus.CERRADA))
        assertEquals("Cancelada", uiMapper.statusLabel(RequestStatus.CANCELADA))

        assertEquals("Creación de solicitud", uiMapper.eventTypeLabel("CREACION"))
        assertEquals("Asignación", uiMapper.eventTypeLabel("ASIGNACION"))
        assertEquals("Intervención", uiMapper.eventTypeLabel("INTERVENCION"))
        assertEquals("Escalamiento", uiMapper.eventTypeLabel("ESCALAMIENTO"))
        assertEquals("Reasignación", uiMapper.eventTypeLabel("REASIGNACION"))
        assertEquals("Respuesta registrada", uiMapper.eventTypeLabel("RESPUESTA"))
        assertEquals("Cierre automático", uiMapper.eventTypeLabel("CIERRE_AUTOMATICO"))
        assertEquals("Cancelación de solicitud", uiMapper.eventTypeLabel("CANCELACION"))
        assertEquals("CUSTOM_EVENT", uiMapper.eventTypeLabel("CUSTOM_EVENT"))

        assertEquals("Estado inicial: Abierta", uiMapper.transitionLabel(null, RequestStatus.ABIERTA))
        assertEquals(
            "En revisión → Escalada",
            uiMapper.transitionLabel(RequestStatus.EN_REVISION, RequestStatus.ESCALADA),
        )
        assertEquals("Abierta", uiMapper.transitionLabel(RequestStatus.ABIERTA, null))
        assertNull(uiMapper.transitionLabel(null, null))

        val instant = Instant.parse("2026-11-15T15:30:00Z")
        assertEquals("15/11/2026 10:30", uiMapper.formatDateTime(instant))
        assertEquals("15/11/2026 10:30", uiMapper.formatOptionalDateTime(instant))
        assertNull(uiMapper.formatOptionalDateTime(null))

        val entry =
            InternalHistoryEntry(
                id = "h-1",
                eventType = "CIERRE_AUTOMATICO",
                actorName = null,
                actorRole = HistoryActorRole.SISTEMA,
                previousStatus = RequestStatus.RESUELTA,
                newStatus = RequestStatus.CERRADA,
                detail = "Cierre automático.",
                createdAt = instant,
            )
        assertEquals("Sistema", uiMapper.actorLabel(entry))
        assertEquals("Sistema", uiMapper.actorRoleLabel(HistoryActorRole.SISTEMA))
        assertEquals("Solicitante", uiMapper.actorRoleLabel(HistoryActorRole.SOLICITANTE))
        assertEquals("Personal de atención", uiMapper.actorRoleLabel(HistoryActorRole.PERSONAL_ATENCION))
        assertEquals("Coordinador", uiMapper.actorRoleLabel(HistoryActorRole.COORDINADOR))
    }
}
