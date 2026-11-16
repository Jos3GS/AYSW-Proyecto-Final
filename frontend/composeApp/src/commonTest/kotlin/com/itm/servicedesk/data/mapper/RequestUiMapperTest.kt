package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestHistoryAction
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.testing.RequestTestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestUiMapperTest {
    private val uiMapper: ServiceRequestUiMapper = RequestUiMapper()

    @Test
    fun `maps all Spanish category, status, and history action labels`() {
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

        assertEquals("Creación de solicitud", uiMapper.actionLabel(RequestHistoryAction.CREACION))
        assertEquals("Cancelación de solicitud", uiMapper.actionLabel(RequestHistoryAction.CANCELACION))
        assertEquals("Asignación", uiMapper.actionLabel(RequestHistoryAction.ASIGNACION))
        assertEquals("Intervención", uiMapper.actionLabel(RequestHistoryAction.INTERVENCION))
        assertEquals("Escalamiento", uiMapper.actionLabel(RequestHistoryAction.ESCALAMIENTO))
        assertEquals("Reasignación", uiMapper.actionLabel(RequestHistoryAction.REASIGNACION))
        assertEquals("Respuesta registrada", uiMapper.actionLabel(RequestHistoryAction.RESPUESTA))
        assertEquals("Cierre automático", uiMapper.actionLabel(RequestHistoryAction.CIERRE_AUTOMATICO))
    }

    @Test
    fun `maps summary and detail with Bogota formatting and action flags`() {
        val summaryUi = uiMapper.toSummaryUiModel(RequestTestFixtures.requestSummary())
        assertEquals("Trámite", summaryUi.categoryLabel)
        assertEquals("Abierta", summaryUi.statusLabel)
        assertEquals("14/11/2026 18:30", summaryUi.formattedCreatedAt)

        val openDetailUi = uiMapper.toDetailUiModel(RequestTestFixtures.requestDetail(status = RequestStatus.ABIERTA))
        assertTrue(openDetailUi.canCancel)
        assertFalse(openDetailUi.canCreateRelated)
        assertNull(openDetailUi.formattedRespondedAt)
        assertEquals("Estado inicial: Abierta", openDetailUi.history.first().transitionLabel)

        val inReviewUi = uiMapper.toDetailUiModel(RequestTestFixtures.requestDetail(status = RequestStatus.EN_REVISION))
        assertFalse(inReviewUi.canCancel)
        assertFalse(inReviewUi.canCreateRelated)

        val resolvedUi =
            uiMapper.toDetailUiModel(
                RequestTestFixtures.requestDetail(
                    status = RequestStatus.RESUELTA,
                    responseText = "Respuesta emitida.",
                    respondedAt = RequestTestFixtures.sampleRespondedAt,
                    history =
                        listOf(
                            RequestTestFixtures.historyEntry(
                                action = RequestHistoryAction.RESPUESTA,
                                previousState = RequestStatus.EN_REVISION,
                                newState = RequestStatus.RESUELTA,
                                occurredAt = RequestTestFixtures.sampleRespondedAt,
                            ),
                        ),
                ),
            )
        assertFalse(resolvedUi.canCancel)
        assertTrue(resolvedUi.canCreateRelated)
        assertEquals("15/11/2026 10:15", resolvedUi.formattedRespondedAt)
        assertEquals("En revisión → Resuelta", resolvedUi.history.first().transitionLabel)

        val closedUi =
            uiMapper.toDetailUiModel(
                RequestTestFixtures.requestDetail(
                    status = RequestStatus.CERRADA,
                    closedAt = RequestTestFixtures.sampleClosedAt,
                ),
            )
        assertFalse(closedUi.canCancel)
        assertTrue(closedUi.canCreateRelated)
        assertEquals("15/11/2026 16:00", closedUi.formattedClosedAt)
    }
}
