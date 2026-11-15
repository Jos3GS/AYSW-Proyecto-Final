package com.itm.servicedesk.service

import com.itm.servicedesk.domain.builder.MyRequestsQuery
import com.itm.servicedesk.domain.builder.ServiceRequestBuilder
import com.itm.servicedesk.domain.model.AuthenticatedIdentity
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequestDetail
import com.itm.servicedesk.domain.model.ServiceRequestSummary
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.model.ValidationDetail
import com.itm.servicedesk.domain.repository.ServiceRequestRepository
import com.itm.servicedesk.domain.state.ServiceRequestState
import com.itm.servicedesk.domain.validation.RequestValidator
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class ServiceRequestService(
    private val serviceRequestRepository: ServiceRequestRepository,
    private val timezone: String = "America/Bogota",
) {
    private val zoneId: ZoneId = ZoneId.of(timezone)

    fun create(
        actor: AuthenticatedIdentity,
        rawCategory: String,
        rawSubject: String,
        rawDescription: String,
        rawPreviousRequestCode: String?,
    ): ServiceRequestDetail {
        requireSolicitante(actor)

        val validated =
            RequestValidator.validateCreation(
                rawCategory = rawCategory,
                rawSubject = rawSubject,
                rawDescription = rawDescription,
                rawPreviousRequestCode = rawPreviousRequestCode,
            )

        val previousRequest =
            validated.previousRequestCode?.let { prevCode ->
                val found = serviceRequestRepository.findRequestByCode(prevCode)
                val isEligible =
                    found != null &&
                        found.requesterId == actor.userId &&
                        (found.status == RequestStatus.RESUELTA || found.status == RequestStatus.CERRADA)
                if (!isEligible) {
                    throw DomainException.ValidationException(
                        message = VALIDATION_ERROR_MESSAGE,
                        details =
                            listOf(
                                ValidationDetail(
                                    field = "previousRequestCode",
                                    message =
                                        "La solicitud anterior debe existir, pertenecer al solicitante " +
                                            "y estar en estado RESUELTA o CERRADA.",
                                ),
                            ),
                    )
                }
                found
            }

        val builder =
            ServiceRequestBuilder()
                .id(UUID.randomUUID())
                .requesterId(actor.userId)
                .category(validated.category)
                .subject(validated.subject)
                .description(validated.description)
                .previousRequest(previousRequest)
                .timezone(timezone)

        return serviceRequestRepository.createWithHistory(
            builder = builder,
            historyEntryId = UUID.randomUUID(),
            historyComment = CREATION_HISTORY_COMMENT,
        )
    }

    fun listMine(
        actor: AuthenticatedIdentity,
        query: MyRequestsQuery,
    ): PagedResult<ServiceRequestSummary> {
        requireSolicitante(actor)
        if (query.requesterId != actor.userId) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        return serviceRequestRepository.findPagedByRequester(query)
    }

    fun getDetail(
        actor: AuthenticatedIdentity,
        code: String,
    ): ServiceRequestDetail {
        requireSolicitante(actor)
        val detail =
            serviceRequestRepository.findByCode(code.trim())
                ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)
        if (detail.request.requesterId != actor.userId) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
        return detail
    }

    fun cancel(
        actor: AuthenticatedIdentity,
        code: String,
        rawReason: String?,
    ): ServiceRequestDetail {
        requireSolicitante(actor)
        val normalizedReason = RequestValidator.validateCancellation(rawReason)

        val existing =
            serviceRequestRepository.findRequestByCode(code.trim())
                ?: throw DomainException.NotFoundException(NOT_FOUND_MESSAGE)

        val now = OffsetDateTime.now(zoneId)
        val transition =
            ServiceRequestState
                .fromStatus(existing.status)
                .cancel(
                    request = existing,
                    actorId = actor.userId,
                    actorRole = actor.role,
                    reason = normalizedReason,
                    occurredAt = now,
                )

        return serviceRequestRepository.cancelWithHistory(
            requestId = existing.id,
            transition = transition,
            historyEntryId = UUID.randomUUID(),
        ) ?: throw DomainException.ConflictException(ServiceRequestState.CONFLICT_CANCEL_MESSAGE)
    }

    private fun requireSolicitante(actor: AuthenticatedIdentity) {
        if (actor.role != UserRole.SOLICITANTE) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
    }

    companion object {
        private const val FORBIDDEN_MESSAGE = "No tienes permisos suficientes para acceder a este recurso."
        private const val NOT_FOUND_MESSAGE = "Solicitud no encontrada."
        private const val VALIDATION_ERROR_MESSAGE = "Los datos proporcionados contienen errores de validación."
        private const val CREATION_HISTORY_COMMENT = "Solicitud registrada por el solicitante."
    }
}
