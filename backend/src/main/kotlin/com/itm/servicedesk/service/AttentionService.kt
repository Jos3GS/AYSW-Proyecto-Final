package com.itm.servicedesk.service

import com.itm.servicedesk.domain.builder.AttentionPageQuery
import com.itm.servicedesk.domain.builder.AttentionQueryMode
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.AttentionRequestSummary
import com.itm.servicedesk.domain.model.AuthenticatedIdentity
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.model.ValidationDetail
import com.itm.servicedesk.domain.repository.AttentionRepository
import com.itm.servicedesk.domain.repository.UserRepository
import com.itm.servicedesk.domain.state.ServiceRequestState
import com.itm.servicedesk.domain.time.TimeProvider
import com.itm.servicedesk.domain.validation.AttentionValidator
import java.util.UUID

class AttentionService(
    private val attentionRepository: AttentionRepository,
    private val userRepository: UserRepository,
    private val timeProvider: TimeProvider,
) {
    fun listCoordinationPending(
        actor: AuthenticatedIdentity,
        query: AttentionPageQuery,
    ): PagedResult<AttentionRequestSummary> {
        requireCoordinator(actor)
        if (query.mode != AttentionQueryMode.COORDINATION_PENDING) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        return attentionRepository.findPendingForCoordination(query, timeProvider.now())
    }

    fun listMyAssigned(
        actor: AuthenticatedIdentity,
        query: AttentionPageQuery,
    ): PagedResult<AttentionRequestSummary> {
        requirePersonalAtencion(actor)
        if (query.mode != AttentionQueryMode.AGENT_ASSIGNED || query.assignedTo != actor.userId) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        return attentionRepository.findAssignedToAgent(query, timeProvider.now())
    }

    fun getInternalDetail(
        actor: AuthenticatedIdentity,
        code: String,
    ): AttentionRequestDetail {
        if (actor.role != UserRole.COORDINADOR && actor.role != UserRole.PERSONAL_ATENCION) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        val detail =
            attentionRepository.findInternalDetailByCode(code.trim(), timeProvider.now())
                ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
        if (actor.role == UserRole.PERSONAL_ATENCION && detail.request.assignedTo != actor.userId) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        return detail
    }

    fun assign(
        actor: AuthenticatedIdentity,
        code: String,
        rawAssigneeId: String,
        rawPriority: String,
    ): AttentionRequestDetail {
        requireCoordinator(actor)
        val validated = AttentionValidator.validateAssign(rawAssigneeId, rawPriority)
        val existing =
            attentionRepository.findRequestByCode(code.trim())
                ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
        val now = timeProvider.now()
        val transition =
            ServiceRequestState
                .fromStatus(existing.status)
                .assign(
                    request = existing,
                    coordinatorId = actor.userId,
                    coordinatorRole = actor.role,
                    assigneeId = validated.assigneeId,
                    priority = validated.priority,
                    now = now,
                )
        requireValidAssignee(validated.assigneeId)

        attentionRepository.applyTransitionWithHistory(
            expectedCurrentStatus = existing.status,
            transition = transition,
            historyEntryId = UUID.randomUUID(),
        ) ?: throw DomainException.ConflictException(ServiceRequestState.CONFLICT_ASSIGN_MESSAGE)

        return attentionRepository.findInternalDetailByCode(existing.code, now)
            ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
    }

    fun reassign(
        actor: AuthenticatedIdentity,
        code: String,
        rawAssigneeId: String,
        rawReason: String,
        rawPriority: String?,
    ): AttentionRequestDetail {
        requireCoordinator(actor)
        val validated = AttentionValidator.validateReassign(rawAssigneeId, rawReason, rawPriority)
        val existing =
            attentionRepository.findRequestByCode(code.trim())
                ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
        val now = timeProvider.now()
        val transition =
            ServiceRequestState
                .fromStatus(existing.status)
                .reassign(
                    request = existing,
                    coordinatorId = actor.userId,
                    coordinatorRole = actor.role,
                    assigneeId = validated.assigneeId,
                    reason = validated.reason,
                    newPriority = validated.priority,
                    now = now,
                )
        requireValidAssignee(validated.assigneeId)

        attentionRepository.applyTransitionWithHistory(
            expectedCurrentStatus = existing.status,
            transition = transition,
            historyEntryId = UUID.randomUUID(),
        ) ?: throw DomainException.ConflictException(ServiceRequestState.CONFLICT_REASSIGN_MESSAGE)

        return attentionRepository.findInternalDetailByCode(existing.code, now)
            ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
    }

    fun addIntervention(
        actor: AuthenticatedIdentity,
        code: String,
        rawComment: String,
    ): AttentionRequestDetail {
        requirePersonalAtencion(actor)
        val comment = AttentionValidator.validateIntervention(rawComment)
        val existing =
            attentionRepository.findRequestByCode(code.trim())
                ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
        val now = timeProvider.now()
        val transition =
            ServiceRequestState
                .fromStatus(existing.status)
                .addIntervention(
                    request = existing,
                    actorId = actor.userId,
                    actorRole = actor.role,
                    comment = comment,
                    now = now,
                )

        attentionRepository.applyTransitionWithHistory(
            expectedCurrentStatus = existing.status,
            transition = transition,
            historyEntryId = UUID.randomUUID(),
        ) ?: throw DomainException.ConflictException(ServiceRequestState.CONFLICT_INTERVENTION_MESSAGE)

        return attentionRepository.findInternalDetailByCode(existing.code, now)
            ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
    }

    fun escalate(
        actor: AuthenticatedIdentity,
        code: String,
        rawReason: String,
    ): AttentionRequestDetail {
        requirePersonalAtencion(actor)
        val reason = AttentionValidator.validateEscalation(rawReason)
        val existing =
            attentionRepository.findRequestByCode(code.trim())
                ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
        val now = timeProvider.now()
        val transition =
            ServiceRequestState
                .fromStatus(existing.status)
                .escalate(
                    request = existing,
                    actorId = actor.userId,
                    actorRole = actor.role,
                    reason = reason,
                    now = now,
                )

        attentionRepository.applyTransitionWithHistory(
            expectedCurrentStatus = existing.status,
            transition = transition,
            historyEntryId = UUID.randomUUID(),
        ) ?: throw DomainException.ConflictException(ServiceRequestState.CONFLICT_ESCALATE_MESSAGE)

        return attentionRepository.findInternalDetailByCode(existing.code, now)
            ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
    }

    fun respond(
        actor: AuthenticatedIdentity,
        code: String,
        rawResponseText: String,
    ): AttentionRequestDetail {
        requirePersonalAtencion(actor)
        val responseText = AttentionValidator.validateResponse(rawResponseText)
        val existing =
            attentionRepository.findRequestByCode(code.trim())
                ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
        val now = timeProvider.now()
        val transition =
            ServiceRequestState
                .fromStatus(existing.status)
                .respond(
                    request = existing,
                    actorId = actor.userId,
                    actorRole = actor.role,
                    responseText = responseText,
                    now = now,
                )

        attentionRepository.applyTransitionWithHistory(
            expectedCurrentStatus = existing.status,
            transition = transition,
            historyEntryId = UUID.randomUUID(),
        ) ?: throw DomainException.ConflictException(ServiceRequestState.CONFLICT_RESPOND_MESSAGE)

        return attentionRepository.findInternalDetailByCode(existing.code, now)
            ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
    }

    private fun requireValidAssignee(assigneeId: UUID) {
        val assignee = userRepository.findById(assigneeId)
        if (assignee == null || !assignee.active || assignee.role != UserRole.PERSONAL_ATENCION) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details =
                    listOf(
                        ValidationDetail(
                            field = "assigneeId",
                            message =
                                "El responsable asignado debe corresponder a un usuario activo con rol PERSONAL_ATENCION.",
                        ),
                    ),
            )
        }
    }

    private fun requireCoordinator(actor: AuthenticatedIdentity) {
        if (actor.role != UserRole.COORDINADOR) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
    }

    private fun requirePersonalAtencion(actor: AuthenticatedIdentity) {
        if (actor.role != UserRole.PERSONAL_ATENCION) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
    }

    companion object {
        private const val FORBIDDEN_MESSAGE = "No tienes permisos suficientes para acceder a este recurso."
        private const val NOT_FOUND_MESSAGE = "Solicitud no encontrada."
        private const val VALIDATION_ERROR_MESSAGE = "Los datos proporcionados contienen errores de validación."
    }
}
