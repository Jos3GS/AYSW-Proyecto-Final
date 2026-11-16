package com.itm.servicedesk

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.HistoryAction
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequest
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.state.ServiceRequestState
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceRequestStateLifecycleTest {
    private val offset = ZoneOffset.of("-05:00")
    private val baseNow = OffsetDateTime.of(2026, 11, 15, 9, 0, 0, 0, offset)
    private val requesterId = UUID.randomUUID()
    private val coordinatorId = UUID.randomUUID()
    private val agentA = UUID.randomUUID()
    private val agentB = UUID.randomUUID()

    private fun buildRequest(
        status: RequestStatus,
        assignedTo: UUID? = null,
        priority: RequestPriority? = null,
        firstAssignedAt: OffsetDateTime? = null,
        assignedAt: OffsetDateTime? = null,
        dueAt: OffsetDateTime? = null,
        respondedAt: OffsetDateTime? = null,
    ): ServiceRequest =
        ServiceRequest(
            id = UUID.randomUUID(),
            code = "SOL-2026-00010",
            requesterId = requesterId,
            category = RequestCategory.TRAMITE,
            subject = "Certificado académico oficial",
            description = "Requiero certificado oficial de calificaciones del semestre actual.",
            status = status,
            assignedTo = assignedTo,
            priority = priority,
            firstAssignedAt = firstAssignedAt,
            assignedAt = assignedAt,
            dueAt = dueAt,
            respondedAt = respondedAt,
            createdAt = baseNow.minusHours(2),
            updatedAt = baseNow.minusHours(1),
        )

    @Test
    fun `RequestPriority maps exact SLA hours 24h 72h 120h and computes dueAt`() {
        assertEquals(24L, RequestPriority.ALTA.slaHours)
        assertEquals(72L, RequestPriority.MEDIA.slaHours)
        assertEquals(120L, RequestPriority.BAJA.slaHours)

        assertEquals(baseNow.plusHours(24), RequestPriority.ALTA.computeDueAt(baseNow))
        assertEquals(baseNow.plusHours(72), RequestPriority.MEDIA.computeDueAt(baseNow))
        assertEquals(baseNow.plusHours(120), RequestPriority.BAJA.computeDueAt(baseNow))
    }

    @Test
    fun `isOverdue evaluates strictly EN_REVISION and ESCALADA when now is after dueAt`() {
        val due = baseNow.plusHours(24)
        val inReview = buildRequest(RequestStatus.EN_REVISION, assignedTo = agentA, dueAt = due)
        val escalated = buildRequest(RequestStatus.ESCALADA, assignedTo = agentA, dueAt = due)
        val resolved = buildRequest(RequestStatus.RESUELTA, assignedTo = agentA, dueAt = due)
        val closed = buildRequest(RequestStatus.CERRADA, assignedTo = agentA, dueAt = due)

        assertFalse(inReview.isOverdue(due))
        assertTrue(inReview.isOverdue(due.plusSeconds(1)))
        assertFalse(escalated.isOverdue(due))
        assertTrue(escalated.isOverdue(due.plusSeconds(1)))
        assertFalse(resolved.isOverdue(due.plusHours(10)))
        assertFalse(closed.isOverdue(due.plusHours(10)))
    }

    @Test
    fun `assign transitions ABIERTA to EN_REVISION setting firstAssignedAt and dueAt and rejects invalid actor`() {
        val open = buildRequest(RequestStatus.ABIERTA)
        val transition =
            ServiceRequestState
                .fromStatus(open.status)
                .assign(
                    request = open,
                    coordinatorId = coordinatorId,
                    coordinatorRole = UserRole.COORDINADOR,
                    assigneeId = agentA,
                    priority = RequestPriority.MEDIA,
                    now = baseNow,
                )

        assertEquals(RequestStatus.ABIERTA, transition.previousState)
        assertEquals(RequestStatus.EN_REVISION, transition.newState)
        assertEquals(HistoryAction.ASIGNACION, transition.action)
        assertEquals(coordinatorId, transition.actorId)
        assertEquals(HistoryActorRole.COORDINADOR, transition.actorRole)
        assertEquals(agentA, transition.assignedTo)
        assertEquals(RequestPriority.MEDIA, transition.priority)
        assertEquals(baseNow, transition.firstAssignedAt)
        assertEquals(baseNow, transition.assignedAt)
        assertEquals(baseNow.plusHours(72), transition.dueAt)

        listOf(UserRole.SOLICITANTE, UserRole.PERSONAL_ATENCION).forEach { badRole ->
            assertFailsWith<DomainException.ForbiddenException> {
                ServiceRequestState.fromStatus(open.status).assign(
                    request = open,
                    coordinatorId = coordinatorId,
                    coordinatorRole = badRole,
                    assigneeId = agentA,
                    priority = RequestPriority.ALTA,
                    now = baseNow,
                )
            }
        }

        RequestStatus.entries.filter { it != RequestStatus.ABIERTA }.forEach { nonOpenStatus ->
            val req = buildRequest(nonOpenStatus)
            assertFailsWith<DomainException.ConflictException> {
                ServiceRequestState.fromStatus(req.status).assign(
                    request = req,
                    coordinatorId = coordinatorId,
                    coordinatorRole = UserRole.COORDINADOR,
                    assigneeId = agentA,
                    priority = RequestPriority.ALTA,
                    now = baseNow,
                )
            }
        }
    }

    @Test
    fun `addIntervention and escalate require assigned PERSONAL_ATENCION and EN_REVISION and allow overdue requests`() {
        val firstAssigned = baseNow.minusHours(30)
        val dueAt = firstAssigned.plusHours(24)
        val overdueInReview =
            buildRequest(
                status = RequestStatus.EN_REVISION,
                assignedTo = agentA,
                priority = RequestPriority.ALTA,
                firstAssignedAt = firstAssigned,
                assignedAt = firstAssigned,
                dueAt = dueAt,
            )
        assertTrue(overdueInReview.isOverdue(baseNow))

        val intervention =
            ServiceRequestState.fromStatus(overdueInReview.status).addIntervention(
                request = overdueInReview,
                actorId = agentA,
                actorRole = UserRole.PERSONAL_ATENCION,
                comment = "  Revisión interna realizada pese a vencimiento.  ",
                now = baseNow,
            )
        assertEquals(RequestStatus.EN_REVISION, intervention.previousState)
        assertEquals(RequestStatus.EN_REVISION, intervention.newState)
        assertEquals(HistoryAction.INTERVENCION, intervention.action)
        assertEquals("Revisión interna realizada pese a vencimiento.", intervention.comment)

        val escalation =
            ServiceRequestState.fromStatus(overdueInReview.status).escalate(
                request = overdueInReview,
                actorId = agentA,
                actorRole = UserRole.PERSONAL_ATENCION,
                reason = "  Requiere concepto de coordinación académica.  ",
                now = baseNow,
            )
        assertEquals(RequestStatus.EN_REVISION, escalation.previousState)
        assertEquals(RequestStatus.ESCALADA, escalation.newState)
        assertEquals(HistoryAction.ESCALAMIENTO, escalation.action)
        assertEquals("Requiere concepto de coordinación académica.", escalation.comment)

        assertFailsWith<DomainException.ForbiddenException> {
            ServiceRequestState.fromStatus(overdueInReview.status).addIntervention(
                request = overdueInReview,
                actorId = agentB,
                actorRole = UserRole.PERSONAL_ATENCION,
                comment = "Intento de agente no asignado",
                now = baseNow,
            )
        }
        assertFailsWith<DomainException.ForbiddenException> {
            ServiceRequestState.fromStatus(overdueInReview.status).escalate(
                request = overdueInReview,
                actorId = coordinatorId,
                actorRole = UserRole.COORDINADOR,
                reason = "Coordinador no puede escalar",
                now = baseNow,
            )
        }

        RequestStatus.entries.filter { it != RequestStatus.EN_REVISION }.forEach { nonReview ->
            val req = buildRequest(nonReview, assignedTo = agentA)
            assertFailsWith<DomainException.ConflictException> {
                ServiceRequestState.fromStatus(req.status).addIntervention(
                    request = req,
                    actorId = agentA,
                    actorRole = UserRole.PERSONAL_ATENCION,
                    comment = "Nota válida de 20 caracteres.",
                    now = baseNow,
                )
            }
            assertFailsWith<DomainException.ConflictException> {
                ServiceRequestState.fromStatus(req.status).escalate(
                    request = req,
                    actorId = agentA,
                    actorRole = UserRole.PERSONAL_ATENCION,
                    reason = "Motivo válido de escalamiento.",
                    now = baseNow,
                )
            }
        }
    }

    @Test
    fun `reassign preserves firstAssignedAt and recalculates dueAt from firstAssignedAt without resetting SLA clock`() {
        val originalFirstAssignedAt = baseNow.minusHours(30)
        val escalated =
            buildRequest(
                status = RequestStatus.ESCALADA,
                assignedTo = agentA,
                priority = RequestPriority.MEDIA,
                firstAssignedAt = originalFirstAssignedAt,
                assignedAt = originalFirstAssignedAt,
                dueAt = originalFirstAssignedAt.plusHours(72),
            )

        val transition =
            ServiceRequestState.fromStatus(escalated.status).reassign(
                request = escalated,
                coordinatorId = coordinatorId,
                coordinatorRole = UserRole.COORDINADOR,
                assigneeId = agentB,
                reason = "Se reasigna al especialista B con prioridad ALTA.",
                newPriority = RequestPriority.ALTA,
                now = baseNow,
            )

        assertEquals(RequestStatus.ESCALADA, transition.previousState)
        assertEquals(RequestStatus.EN_REVISION, transition.newState)
        assertEquals(HistoryAction.REASIGNACION, transition.action)
        assertEquals(agentB, transition.assignedTo)
        assertEquals(RequestPriority.ALTA, transition.priority)
        assertNull(transition.firstAssignedAt)
        assertEquals(baseNow, transition.assignedAt)
        assertEquals(originalFirstAssignedAt.plusHours(24), transition.dueAt)
        assertTrue(transition.dueAt!!.isBefore(baseNow))

        val keepPriorityTransition =
            ServiceRequestState.fromStatus(escalated.status).reassign(
                request = escalated,
                coordinatorId = coordinatorId,
                coordinatorRole = UserRole.COORDINADOR,
                assigneeId = agentB,
                reason = "Se mantiene la prioridad MEDIA vigente.",
                newPriority = null,
                now = baseNow,
            )
        assertEquals(RequestPriority.MEDIA, keepPriorityTransition.priority)
        assertEquals(originalFirstAssignedAt.plusHours(72), keepPriorityTransition.dueAt)
    }

    @Test
    fun `respond computes respondedWithinSla accurately and autoClose requires 24h elapsed`() {
        val dueAt = baseNow.plusHours(24)
        val inReview =
            buildRequest(
                status = RequestStatus.EN_REVISION,
                assignedTo = agentA,
                priority = RequestPriority.ALTA,
                firstAssignedAt = baseNow,
                assignedAt = baseNow,
                dueAt = dueAt,
            )

        val onTime =
            ServiceRequestState.fromStatus(inReview.status).respond(
                request = inReview,
                actorId = agentA,
                actorRole = UserRole.PERSONAL_ATENCION,
                responseText = "Respuesta emitida exactamente dentro del límite SLA.",
                now = dueAt,
            )
        assertEquals(RequestStatus.RESUELTA, onTime.newState)
        assertEquals(true, onTime.respondedWithinSla)

        val late =
            ServiceRequestState.fromStatus(inReview.status).respond(
                request = inReview,
                actorId = agentA,
                actorRole = UserRole.PERSONAL_ATENCION,
                responseText = "Respuesta emitida después de vencer el SLA informativo.",
                now = dueAt.plusSeconds(1),
            )
        assertEquals(RequestStatus.RESUELTA, late.newState)
        assertEquals(false, late.respondedWithinSla)

        val resolvedAt = baseNow
        val resolved = buildRequest(status = RequestStatus.RESUELTA, respondedAt = resolvedAt)

        assertFailsWith<DomainException.ConflictException> {
            val state = ServiceRequestState.fromStatus(resolved.status)
            state.autoClose(resolved, resolvedAt.plusHours(24).minusSeconds(1))
        }

        val autoClosed =
            ServiceRequestState.fromStatus(resolved.status).autoClose(resolved, resolvedAt.plusHours(24))
        assertEquals(RequestStatus.RESUELTA, autoClosed.previousState)
        assertEquals(RequestStatus.CERRADA, autoClosed.newState)
        assertEquals(HistoryAction.CIERRE_AUTOMATICO, autoClosed.action)
        assertNull(autoClosed.actorId)
        assertEquals(HistoryActorRole.SISTEMA, autoClosed.actorRole)
        assertEquals("Cierre automático", autoClosed.comment)
        assertEquals(resolvedAt.plusHours(24), autoClosed.closedAt)
    }
}
