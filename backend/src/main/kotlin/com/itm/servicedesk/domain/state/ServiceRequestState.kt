package com.itm.servicedesk.domain.state

import com.itm.servicedesk.domain.model.AttentionTransition
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.HistoryAction
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.RequestPriority
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

    fun assign(
        request: ServiceRequest,
        coordinatorId: UUID,
        coordinatorRole: UserRole,
        assigneeId: UUID,
        priority: RequestPriority,
        now: OffsetDateTime,
    ): AttentionTransition {
        if (coordinatorRole != UserRole.COORDINADOR) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        return executeAssign(
            request = request,
            coordinatorId = coordinatorId,
            assigneeId = assigneeId,
            priority = priority,
            now = now,
        )
    }

    fun addIntervention(
        request: ServiceRequest,
        actorId: UUID,
        actorRole: UserRole,
        comment: String,
        now: OffsetDateTime,
    ): AttentionTransition {
        if (actorRole != UserRole.PERSONAL_ATENCION || request.assignedTo != actorId) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        return executeAddIntervention(
            request = request,
            actorId = actorId,
            comment = comment,
            now = now,
        )
    }

    fun escalate(
        request: ServiceRequest,
        actorId: UUID,
        actorRole: UserRole,
        reason: String,
        now: OffsetDateTime,
    ): AttentionTransition {
        if (actorRole != UserRole.PERSONAL_ATENCION || request.assignedTo != actorId) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        return executeEscalate(
            request = request,
            actorId = actorId,
            reason = reason,
            now = now,
        )
    }

    fun reassign(
        request: ServiceRequest,
        coordinatorId: UUID,
        coordinatorRole: UserRole,
        assigneeId: UUID,
        reason: String,
        newPriority: RequestPriority?,
        now: OffsetDateTime,
    ): AttentionTransition {
        if (coordinatorRole != UserRole.COORDINADOR) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        return executeReassign(
            request = request,
            coordinatorId = coordinatorId,
            assigneeId = assigneeId,
            reason = reason,
            newPriority = newPriority,
            now = now,
        )
    }

    fun respond(
        request: ServiceRequest,
        actorId: UUID,
        actorRole: UserRole,
        responseText: String,
        now: OffsetDateTime,
    ): AttentionTransition {
        if (actorRole != UserRole.PERSONAL_ATENCION || request.assignedTo != actorId) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        return executeRespond(
            request = request,
            actorId = actorId,
            responseText = responseText,
            now = now,
        )
    }

    fun autoClose(
        request: ServiceRequest,
        now: OffsetDateTime,
    ): AttentionTransition = executeAutoClose(request = request, now = now)

    protected open fun executeCancel(
        actorId: UUID,
        reason: String?,
        occurredAt: OffsetDateTime,
    ): StateTransition = throw DomainException.ConflictException(CONFLICT_CANCEL_MESSAGE)

    protected open fun executeAssign(
        request: ServiceRequest,
        coordinatorId: UUID,
        assigneeId: UUID,
        priority: RequestPriority,
        now: OffsetDateTime,
    ): AttentionTransition = throw DomainException.ConflictException(CONFLICT_ASSIGN_MESSAGE)

    protected open fun executeAddIntervention(
        request: ServiceRequest,
        actorId: UUID,
        comment: String,
        now: OffsetDateTime,
    ): AttentionTransition = throw DomainException.ConflictException(CONFLICT_INTERVENTION_MESSAGE)

    protected open fun executeEscalate(
        request: ServiceRequest,
        actorId: UUID,
        reason: String,
        now: OffsetDateTime,
    ): AttentionTransition = throw DomainException.ConflictException(CONFLICT_ESCALATE_MESSAGE)

    protected open fun executeReassign(
        request: ServiceRequest,
        coordinatorId: UUID,
        assigneeId: UUID,
        reason: String,
        newPriority: RequestPriority?,
        now: OffsetDateTime,
    ): AttentionTransition = throw DomainException.ConflictException(CONFLICT_REASSIGN_MESSAGE)

    protected open fun executeRespond(
        request: ServiceRequest,
        actorId: UUID,
        responseText: String,
        now: OffsetDateTime,
    ): AttentionTransition = throw DomainException.ConflictException(CONFLICT_RESPOND_MESSAGE)

    protected open fun executeAutoClose(
        request: ServiceRequest,
        now: OffsetDateTime,
    ): AttentionTransition = throw DomainException.ConflictException(CONFLICT_AUTOCLOSE_MESSAGE)

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

        override fun executeAssign(
            request: ServiceRequest,
            coordinatorId: UUID,
            assigneeId: UUID,
            priority: RequestPriority,
            now: OffsetDateTime,
        ): AttentionTransition =
            AttentionTransition(
                requestId = request.id,
                previousState = RequestStatus.ABIERTA,
                newState = RequestStatus.EN_REVISION,
                action = HistoryAction.ASIGNACION,
                actorId = coordinatorId,
                actorRole = HistoryActorRole.COORDINADOR,
                comment = "Solicitud asignada con prioridad ${priority.name}.",
                occurredAt = now,
                assignedTo = assigneeId,
                updateAssignment = true,
                priority = priority,
                updatePriority = true,
                firstAssignedAt = now,
                assignedAt = now,
                dueAt = priority.computeDueAt(now),
                updateDueAt = true,
            )
    }

    data object EnRevisionState : ServiceRequestState(RequestStatus.EN_REVISION) {
        override fun executeAddIntervention(
            request: ServiceRequest,
            actorId: UUID,
            comment: String,
            now: OffsetDateTime,
        ): AttentionTransition =
            AttentionTransition(
                requestId = request.id,
                previousState = RequestStatus.EN_REVISION,
                newState = RequestStatus.EN_REVISION,
                action = HistoryAction.INTERVENCION,
                actorId = actorId,
                actorRole = HistoryActorRole.PERSONAL_ATENCION,
                comment = comment.trim(),
                occurredAt = now,
            )

        override fun executeEscalate(
            request: ServiceRequest,
            actorId: UUID,
            reason: String,
            now: OffsetDateTime,
        ): AttentionTransition =
            AttentionTransition(
                requestId = request.id,
                previousState = RequestStatus.EN_REVISION,
                newState = RequestStatus.ESCALADA,
                action = HistoryAction.ESCALAMIENTO,
                actorId = actorId,
                actorRole = HistoryActorRole.PERSONAL_ATENCION,
                comment = reason.trim(),
                occurredAt = now,
            )

        override fun executeRespond(
            request: ServiceRequest,
            actorId: UUID,
            responseText: String,
            now: OffsetDateTime,
        ): AttentionTransition {
            val withinSla = request.dueAt?.let { !now.isAfter(it) } ?: false
            return AttentionTransition(
                requestId = request.id,
                previousState = RequestStatus.EN_REVISION,
                newState = RequestStatus.RESUELTA,
                action = HistoryAction.RESPUESTA,
                actorId = actorId,
                actorRole = HistoryActorRole.PERSONAL_ATENCION,
                comment = DEFAULT_RESPONSE_COMMENT,
                occurredAt = now,
                responseText = responseText.trim(),
                respondedAt = now,
                respondedWithinSla = withinSla,
            )
        }
    }

    data object EscaladaState : ServiceRequestState(RequestStatus.ESCALADA) {
        override fun executeReassign(
            request: ServiceRequest,
            coordinatorId: UUID,
            assigneeId: UUID,
            reason: String,
            newPriority: RequestPriority?,
            now: OffsetDateTime,
        ): AttentionTransition {
            val baseFirstAssignedAt =
                request.firstAssignedAt
                    ?: throw DomainException.ConflictException(MISSING_FIRST_ASSIGNED_MESSAGE)
            val effectivePriority =
                newPriority
                    ?: request.priority
                    ?: throw DomainException.ConflictException(MISSING_PRIORITY_MESSAGE)
            val recalculatedDueAt = effectivePriority.computeDueAt(baseFirstAssignedAt)
            return AttentionTransition(
                requestId = request.id,
                previousState = RequestStatus.ESCALADA,
                newState = RequestStatus.EN_REVISION,
                action = HistoryAction.REASIGNACION,
                actorId = coordinatorId,
                actorRole = HistoryActorRole.COORDINADOR,
                comment = reason.trim(),
                occurredAt = now,
                assignedTo = assigneeId,
                updateAssignment = true,
                priority = effectivePriority,
                updatePriority = true,
                firstAssignedAt = null,
                assignedAt = now,
                dueAt = recalculatedDueAt,
                updateDueAt = true,
            )
        }
    }

    data object ResueltaState : ServiceRequestState(RequestStatus.RESUELTA) {
        override fun executeAutoClose(
            request: ServiceRequest,
            now: OffsetDateTime,
        ): AttentionTransition {
            val respondedAt =
                request.respondedAt
                    ?: throw DomainException.ConflictException(CONFLICT_AUTOCLOSE_ELIGIBILITY_MESSAGE)
            if (respondedAt.plusHours(AUTO_CLOSE_HOURS).isAfter(now)) {
                throw DomainException.ConflictException(CONFLICT_AUTOCLOSE_ELIGIBILITY_MESSAGE)
            }
            return AttentionTransition(
                requestId = request.id,
                previousState = RequestStatus.RESUELTA,
                newState = RequestStatus.CERRADA,
                action = HistoryAction.CIERRE_AUTOMATICO,
                actorId = null,
                actorRole = HistoryActorRole.SISTEMA,
                comment = AUTO_CLOSE_COMMENT,
                occurredAt = now,
                closedAt = now,
            )
        }
    }

    data object CerradaState : ServiceRequestState(RequestStatus.CERRADA)

    data object CanceladaState : ServiceRequestState(RequestStatus.CANCELADA)

    companion object {
        const val CONFLICT_CANCEL_MESSAGE =
            "Solo es posible cancelar una solicitud que se encuentre en estado Abierta."
        const val CONFLICT_ASSIGN_MESSAGE =
            "Solo es posible asignar inicialmente una solicitud que se encuentre en estado Abierta."
        const val CONFLICT_INTERVENTION_MESSAGE =
            "Solo es posible registrar intervenciones cuando la solicitud está en estado En Revisión."
        const val CONFLICT_ESCALATE_MESSAGE =
            "Solo es posible escalar una solicitud cuando se encuentra en estado En Revisión."
        const val CONFLICT_REASSIGN_MESSAGE =
            "Solo es posible reasignar una solicitud cuando se encuentra en estado Escalada."
        const val CONFLICT_RESPOND_MESSAGE =
            "Solo es posible emitir respuesta final cuando la solicitud está en estado En Revisión."
        const val CONFLICT_AUTOCLOSE_MESSAGE =
            "Solo es posible cerrar automáticamente una solicitud que se encuentre en estado Resuelta."
        const val CONFLICT_AUTOCLOSE_ELIGIBILITY_MESSAGE =
            "La solicitud aún no cumple las 24 horas posteriores a la respuesta para cierre automático."
        const val AUTO_CLOSE_COMMENT = "Cierre automático"
        private const val AUTO_CLOSE_HOURS = 24L
        private const val FORBIDDEN_MESSAGE = "No tienes permisos suficientes para acceder a este recurso."
        private const val DEFAULT_CANCEL_COMMENT = "Cancelación de solicitud."
        private const val DEFAULT_RESPONSE_COMMENT = "Respuesta final registrada."
        private const val MISSING_FIRST_ASSIGNED_MESSAGE =
            "La solicitud escalada no cuenta con fecha inicial de asignación."
        private const val MISSING_PRIORITY_MESSAGE =
            "La solicitud escalada no cuenta con prioridad asignada."

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
