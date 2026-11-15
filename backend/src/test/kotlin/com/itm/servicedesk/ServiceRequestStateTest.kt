package com.itm.servicedesk

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.HistoryAction
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.RequestCategory
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

class ServiceRequestStateTest {
    private val ownerId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val now = OffsetDateTime.now(ZoneOffset.of("-05:00"))

    private fun sampleRequest(status: RequestStatus): ServiceRequest =
        ServiceRequest(
            id = UUID.randomUUID(),
            code = "SOL-2026-00001",
            requesterId = ownerId,
            category = RequestCategory.TRAMITE,
            subject = "Certificado de notas",
            description = "Requiero certificado oficial de notas para trámite académico.",
            status = status,
            createdAt = now.minusHours(1),
            updatedAt = now.minusHours(1),
        )

    @Test
    fun `AbiertaState transitions to CANCELADA for owner SOLICITANTE with custom or default reason`() {
        val request = sampleRequest(RequestStatus.ABIERTA)
        val state = ServiceRequestState.fromStatus(request.status)
        assertEquals(RequestStatus.ABIERTA, state.status)

        val transitionWithReason =
            state.cancel(
                request = request,
                actorId = ownerId,
                actorRole = UserRole.SOLICITANTE,
                reason = "  Ya no necesito el certificado. ",
                occurredAt = now,
            )

        assertEquals(RequestStatus.ABIERTA, transitionWithReason.previousState)
        assertEquals(RequestStatus.CANCELADA, transitionWithReason.newState)
        assertEquals(HistoryAction.CANCELACION, transitionWithReason.action)
        assertEquals(ownerId, transitionWithReason.actorId)
        assertEquals(HistoryActorRole.SOLICITANTE, transitionWithReason.actorRole)
        assertEquals("Ya no necesito el certificado.", transitionWithReason.comment)
        assertEquals(now, transitionWithReason.occurredAt)

        val transitionDefault =
            state.cancel(
                request = request,
                actorId = ownerId,
                actorRole = UserRole.SOLICITANTE,
                reason = "   ",
                occurredAt = now,
            )
        assertEquals("Cancelación de solicitud.", transitionDefault.comment)

        val transitionNull =
            state.cancel(
                request = request,
                actorId = ownerId,
                actorRole = UserRole.SOLICITANTE,
                reason = null,
                occurredAt = now,
            )
        assertEquals("Cancelación de solicitud.", transitionNull.comment)
    }

    @Test
    fun `cancel throws ForbiddenException when actor is not owner or not SOLICITANTE`() {
        val request = sampleRequest(RequestStatus.ABIERTA)
        val state = ServiceRequestState.fromStatus(request.status)

        assertFailsWith<DomainException.ForbiddenException> {
            state.cancel(
                request = request,
                actorId = otherUserId,
                actorRole = UserRole.SOLICITANTE,
                reason = null,
                occurredAt = now,
            )
        }

        assertFailsWith<DomainException.ForbiddenException> {
            state.cancel(
                request = request,
                actorId = ownerId,
                actorRole = UserRole.COORDINADOR,
                reason = null,
                occurredAt = now,
            )
        }

        assertFailsWith<DomainException.ForbiddenException> {
            state.cancel(
                request = request,
                actorId = ownerId,
                actorRole = UserRole.PERSONAL_ATENCION,
                reason = null,
                occurredAt = now,
            )
        }
    }

    @Test
    fun `all 5 non-ABIERTA states throw ConflictException on cancel`() {
        val nonCancelableStatuses =
            listOf(
                RequestStatus.EN_REVISION,
                RequestStatus.ESCALADA,
                RequestStatus.RESUELTA,
                RequestStatus.CERRADA,
                RequestStatus.CANCELADA,
            )

        nonCancelableStatuses.forEach { status ->
            val request = sampleRequest(status)
            val state = ServiceRequestState.fromStatus(status)
            assertEquals(status, state.status)

            val ex =
                assertFailsWith<DomainException.ConflictException> {
                    state.cancel(
                        request = request,
                        actorId = ownerId,
                        actorRole = UserRole.SOLICITANTE,
                        reason = "Intento de cancelación",
                        occurredAt = now,
                    )
                }
            assertEquals(409, ex.httpStatus)
            assertEquals(ServiceRequestState.CONFLICT_CANCEL_MESSAGE, ex.message)
        }
    }
}
