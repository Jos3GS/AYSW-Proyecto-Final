package com.itm.servicedesk.domain.state

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.HistoryAction
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequest
import com.itm.servicedesk.domain.model.UserRole
import java.time.OffsetDateTime
import java.util.UUID

data class StateTransition(
    val previousState: RequestStatus,
    val newState: RequestStatus,
    val action: HistoryAction,
    val actorId: UUID,
    val actorRole: HistoryActorRole,
    val comment: String?,
    val occurredAt: OffsetDateTime,
)

sealed class ServiceRequestState(
    val status: RequestStatus,
) {
    fun cancel(
        request: ServiceRequest,
        actorId: UUID,
        actorRole: UserRole,
        reason: String?,
        occurredAt: OffsetDateTime,
    ): StateTransition {
        if (actorRole != UserRole.SOLICITANTE || request.requesterId != actorId) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        return executeCancel(
            actorId = actorId,
            reason = reason,
            occurredAt = occurredAt,
        )
    }

    protected abstract fun executeCancel(
        actorId: UUID,
        reason: String?,
        occurredAt: OffsetDateTime,
    ): StateTransition

    data object AbiertaState : ServiceRequestState(RequestStatus.ABIERTA) {
        override fun executeCancel(
            actorId: UUID,
            reason: String?,
            occurredAt: OffsetDateTime,
        ): StateTransition {
            val resolvedComment = reason?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_CANCEL_COMMENT
            return StateTransition(
                previousState = RequestStatus.ABIERTA,
                newState = RequestStatus.CANCELADA,
                action = HistoryAction.CANCELACION,
                actorId = actorId,
                actorRole = HistoryActorRole.SOLICITANTE,
                comment = resolvedComment,
                occurredAt = occurredAt,
            )
        }
    }

    data object EnRevisionState : ServiceRequestState(RequestStatus.EN_REVISION) {
        override fun executeCancel(
            actorId: UUID,
            reason: String?,
            occurredAt: OffsetDateTime,
        ): StateTransition = throw DomainException.ConflictException(CONFLICT_CANCEL_MESSAGE)
    }

    data object EscaladaState : ServiceRequestState(RequestStatus.ESCALADA) {
        override fun executeCancel(
            actorId: UUID,
            reason: String?,
            occurredAt: OffsetDateTime,
        ): StateTransition = throw DomainException.ConflictException(CONFLICT_CANCEL_MESSAGE)
    }

    data object ResueltaState : ServiceRequestState(RequestStatus.RESUELTA) {
        override fun executeCancel(
            actorId: UUID,
            reason: String?,
            occurredAt: OffsetDateTime,
        ): StateTransition = throw DomainException.ConflictException(CONFLICT_CANCEL_MESSAGE)
    }

    data object CerradaState : ServiceRequestState(RequestStatus.CERRADA) {
        override fun executeCancel(
            actorId: UUID,
            reason: String?,
            occurredAt: OffsetDateTime,
        ): StateTransition = throw DomainException.ConflictException(CONFLICT_CANCEL_MESSAGE)
    }

    data object CanceladaState : ServiceRequestState(RequestStatus.CANCELADA) {
        override fun executeCancel(
            actorId: UUID,
            reason: String?,
            occurredAt: OffsetDateTime,
        ): StateTransition = throw DomainException.ConflictException(CONFLICT_CANCEL_MESSAGE)
    }

    companion object {
        const val CONFLICT_CANCEL_MESSAGE =
            "Solo es posible cancelar una solicitud que se encuentre en estado Abierta."
        private const val FORBIDDEN_MESSAGE = "No tienes permisos suficientes para acceder a este recurso."
        private const val DEFAULT_CANCEL_COMMENT = "Cancelación de solicitud."

        fun fromStatus(status: RequestStatus): ServiceRequestState =
            when (status) {
                RequestStatus.ABIERTA -> AbiertaState
                RequestStatus.EN_REVISION -> EnRevisionState
                RequestStatus.ESCALADA -> EscaladaState
                RequestStatus.RESUELTA -> ResueltaState
                RequestStatus.CERRADA -> CerradaState
                RequestStatus.CANCELADA -> CanceladaState
            }
    }
}
