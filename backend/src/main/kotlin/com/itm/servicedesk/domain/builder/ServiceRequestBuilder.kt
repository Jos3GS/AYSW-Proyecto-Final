package com.itm.servicedesk.domain.builder

import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequest
import com.itm.servicedesk.domain.validation.RequestValidator
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class ServiceRequestBuilder {
    private var idValue: UUID? = null
    private var codeValue: String? = null
    private var requesterIdValue: UUID? = null
    private var categoryValue: RequestCategory? = null
    private var subjectValue: String? = null
    private var descriptionValue: String? = null
    private var previousRequestIdValue: UUID? = null
    private var previousRequestCodeValue: String? = null
    private var timezoneValue: String = "America/Bogota"
    private var createdAtValue: OffsetDateTime? = null

    fun id(id: UUID): ServiceRequestBuilder =
        apply {
            this.idValue = id
        }

    fun code(code: String): ServiceRequestBuilder =
        apply {
            this.codeValue = code.trim()
        }

    fun requesterId(requesterId: UUID): ServiceRequestBuilder =
        apply {
            this.requesterIdValue = requesterId
        }

    fun category(category: RequestCategory): ServiceRequestBuilder =
        apply {
            this.categoryValue = category
        }

    fun subject(subject: String): ServiceRequestBuilder =
        apply {
            this.subjectValue = subject.trim()
        }

    fun description(description: String): ServiceRequestBuilder =
        apply {
            this.descriptionValue = description.trim()
        }

    fun previousRequest(previousRequest: ServiceRequest?): ServiceRequestBuilder =
        apply {
            this.previousRequestIdValue = previousRequest?.id
            this.previousRequestCodeValue = previousRequest?.code
        }

    fun previousRequest(
        previousRequestId: UUID?,
        previousRequestCode: String?,
    ): ServiceRequestBuilder =
        apply {
            this.previousRequestIdValue = previousRequestId
            this.previousRequestCodeValue = previousRequestCode?.trim()?.takeIf { it.isNotEmpty() }
        }

    fun timezone(timezone: String): ServiceRequestBuilder =
        apply {
            this.timezoneValue = timezone
        }

    fun createdAt(createdAt: OffsetDateTime): ServiceRequestBuilder =
        apply {
            this.createdAtValue = createdAt
        }

    fun build(): ServiceRequest {
        val resolvedCode = checkNotNull(codeValue) { "El código de la solicitud es obligatorio." }
        check(RequestValidator.isValidRequestCode(resolvedCode)) {
            "El código de la solicitud debe cumplir el formato SOL-AAAA-NNNNN."
        }
        val resolvedRequesterId =
            checkNotNull(requesterIdValue) { "El identificador del solicitante es obligatorio." }
        val resolvedCategory = checkNotNull(categoryValue) { "La categoría de la solicitud es obligatoria." }
        val resolvedSubject = checkNotNull(subjectValue) { "El asunto de la solicitud es obligatorio." }
        val resolvedDescription = checkNotNull(descriptionValue) { "La descripción de la solicitud es obligatoria." }
        check((previousRequestIdValue == null) == (previousRequestCodeValue == null)) {
            "La referencia a la solicitud previa debe incluir id y code de forma consistente."
        }

        val zoneId = ZoneId.of(timezoneValue)
        val resolvedCreatedAt =
            (createdAtValue ?: OffsetDateTime.now(zoneId))
                .atZoneSameInstant(zoneId)
                .toOffsetDateTime()

        return ServiceRequest(
            id = idValue ?: UUID.randomUUID(),
            code = resolvedCode,
            requesterId = resolvedRequesterId,
            category = resolvedCategory,
            subject = resolvedSubject,
            description = resolvedDescription,
            status = RequestStatus.ABIERTA,
            previousRequestId = previousRequestIdValue,
            previousRequestCode = previousRequestCodeValue,
            createdAt = resolvedCreatedAt,
            updatedAt = resolvedCreatedAt,
        )
    }
}
