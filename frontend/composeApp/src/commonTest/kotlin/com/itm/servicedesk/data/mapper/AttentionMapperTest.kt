package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.core.network.DefaultApiJson
import com.itm.servicedesk.data.dto.attention.AttentionRequestDetailDto
import com.itm.servicedesk.data.dto.attention.PagedAttentionRequestsResponseDto
import com.itm.servicedesk.domain.model.AddInterventionCommand
import com.itm.servicedesk.domain.model.AssignRequestCommand
import com.itm.servicedesk.domain.model.EscalateRequestCommand
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.ReassignRequestCommand
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.RespondRequestCommand
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttentionMapperTest {
    private val mapper = AttentionMapper()

    @Test
    fun `maps outgoing mutation commands to strict JSON DTOs`() {
        val assignDto =
            mapper.toAssignDto(
                AssignRequestCommand(assigneeId = " agent-1 ", priority = RequestPriority.ALTA),
            )
        assertEquals("agent-1", assignDto.assigneeId)
        assertEquals("ALTA", assignDto.priority)
        val assignKeys = DefaultApiJson.parseToJsonElement(DefaultApiJson.encodeToString(assignDto)).jsonObject.keys
        assertEquals(setOf("assigneeId", "priority"), assignKeys)

        val reassignNoPriority =
            mapper.toReassignDto(
                ReassignRequestCommand(
                    assigneeId = " agent-2 ",
                    reason = "  Reasignación por carga operativa.  ",
                    priority = null,
                ),
            )
        assertNull(reassignNoPriority.priority)
        val reassignJson =
            DefaultApiJson.parseToJsonElement(DefaultApiJson.encodeToString(reassignNoPriority)).jsonObject
        assertFalse(reassignJson.containsKey("priority"))

        val reassignWithPriority =
            mapper.toReassignDto(
                ReassignRequestCommand(
                    assigneeId = "agent-2",
                    reason = "Prioridad ajustada",
                    priority = RequestPriority.MEDIA,
                ),
            )
        assertEquals("MEDIA", reassignWithPriority.priority)

        val interventionDto = mapper.toInterventionDto(AddInterventionCommand(comment = "  Nota interna validada  "))
        assertEquals("Nota interna validada", interventionDto.comment)

        val escalateDto = mapper.toEscalateDto(EscalateRequestCommand(reason = "  Requiere otro especialista  "))
        assertEquals("Requiere otro especialista", escalateDto.reason)

        val respondDto = mapper.toRespondDto(RespondRequestCommand(responseText = "  Respuesta definitiva completa.  "))
        assertEquals("Respuesta definitiva completa.", respondDto.responseText)
    }

    @Test
    fun `decodes strict JSON paged response and internal detail with history`() {
        val pagedJson =
            """
            {
              "items": [
                {
                  "code": "SOL-2026-00019",
                  "subject": "Soporte de matrícula",
                  "category": "TRAMITE",
                  "status": "EN_REVISION",
                  "priority": "ALTA",
                  "requesterName": "Laura Gómez",
                  "assignedAgentId": "agent-1",
                  "assignedAgentName": "Carlos Mejía",
                  "createdAt": "2026-11-14T18:30:00-05:00",
                  "dueAt": "2026-11-15T18:30:00-05:00",
                  "isOverdue": true
                }
              ],
              "page": 1,
              "size": 20,
              "totalItems": 1,
              "totalPages": 1
            }
            """.trimIndent()

        val pageDto = DefaultApiJson.decodeFromString<PagedAttentionRequestsResponseDto>(pagedJson)
        val page = mapper.toDomain(pageDto)
        assertEquals(1, page.items.size)
        val summary = page.items.first()
        assertEquals("SOL-2026-00019", summary.code)
        assertEquals(RequestCategory.TRAMITE, summary.category)
        assertEquals(RequestStatus.EN_REVISION, summary.status)
        assertEquals(RequestPriority.ALTA, summary.priority)
        assertTrue(summary.isOverdue)

        val detailJson =
            """
            {
              "code": "SOL-2026-00019",
              "subject": "Soporte de matrícula",
              "description": "Necesito apoyo con la validación de matrícula del semestre.",
              "category": "TRAMITE",
              "status": "CERRADA",
              "priority": "MEDIA",
              "requesterId": "req-1",
              "requesterName": "Laura Gómez",
              "assignedAgentId": "agent-1",
              "assignedAgentName": "Carlos Mejía",
              "previousRequestCode": null,
              "responseText": "Su matrícula fue validada correctamente en el sistema.",
              "createdAt": "2026-11-10T14:00:00-05:00",
              "assignedAt": "2026-11-10T15:00:00-05:00",
              "dueAt": "2026-11-13T15:00:00-05:00",
              "respondedAt": "2026-11-12T10:00:00-05:00",
              "closedAt": "2026-11-15T10:00:00-05:00",
              "isOverdue": false,
              "slaCompliant": true,
              "history": [
                {
                  "id": "h-1",
                  "eventType": "CIERRE_AUTOMATICO",
                  "actorName": null,
                  "actorRole": "SISTEMA",
                  "previousStatus": "RESUELTA",
                  "newStatus": "CERRADA",
                  "detail": "Cierre automático tras vencimiento del plazo de revisión.",
                  "createdAt": "2026-11-15T10:00:00-05:00"
                }
              ]
            }
            """.trimIndent()

        val detailDto = DefaultApiJson.decodeFromString<AttentionRequestDetailDto>(detailJson)
        val detail = mapper.toDomain(detailDto)
        assertEquals(RequestStatus.CERRADA, detail.status)
        assertEquals(RequestPriority.MEDIA, detail.priority)
        assertEquals(true, detail.slaCompliant)
        assertNull(detail.previousRequestCode)
        assertEquals(HistoryActorRole.SISTEMA, detail.history.first().actorRole)
        assertNull(detail.history.first().actorName)
    }
}
