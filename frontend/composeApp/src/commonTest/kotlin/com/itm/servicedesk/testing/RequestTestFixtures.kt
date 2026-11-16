package com.itm.servicedesk.testing

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CancelServiceRequestCommand
import com.itm.servicedesk.domain.model.CreateServiceRequestCommand
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestHistoryAction
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequestDetail
import com.itm.servicedesk.domain.model.ServiceRequestHistoryEntry
import com.itm.servicedesk.domain.model.ServiceRequestSummary
import com.itm.servicedesk.domain.model.ServiceRequestsPage
import com.itm.servicedesk.domain.repository.ServiceRequestRepository
import kotlin.time.Instant

class FakeServiceRequestRepository(
    var createHandler: suspend (CreateServiceRequestCommand) -> AppResult<ServiceRequestDetail> = { command ->
        AppResult.Success(
            RequestTestFixtures.requestDetail(
                code = "SOL-2026-00019",
                category = command.category,
                subject = command.subject,
                description = command.description,
                previousRequestCode = command.previousRequestCode,
                status = RequestStatus.ABIERTA,
            ),
        )
    },
    var getMyRequestsHandler: suspend (Int, Int, RequestStatus?) -> AppResult<ServiceRequestsPage> = { page, size, _ ->
        AppResult.Success(RequestTestFixtures.requestsPage(page = page, size = size))
    },
    var getDetailHandler: suspend (String) -> AppResult<ServiceRequestDetail> = { code ->
        AppResult.Success(RequestTestFixtures.requestDetail(code = code))
    },
    var cancelHandler: suspend (CancelServiceRequestCommand) -> AppResult<ServiceRequestDetail> = { command ->
        AppResult.Success(
            RequestTestFixtures.requestDetail(
                code = command.code,
                status = RequestStatus.CANCELADA,
                history =
                    listOf(
                        RequestTestFixtures.historyEntry(),
                        RequestTestFixtures.historyEntry(
                            id = "hist-2",
                            action = RequestHistoryAction.CANCELACION,
                            previousState = RequestStatus.ABIERTA,
                            newState = RequestStatus.CANCELADA,
                            comment = command.reason ?: "Solicitud cancelada por el solicitante.",
                        ),
                    ),
            ),
        )
    },
) : ServiceRequestRepository {
    val createCalls = mutableListOf<CreateServiceRequestCommand>()
    val listCalls = mutableListOf<Triple<Int, Int, RequestStatus?>>()
    val detailCalls = mutableListOf<String>()
    val cancelCalls = mutableListOf<CancelServiceRequestCommand>()

    override suspend fun create(command: CreateServiceRequestCommand): AppResult<ServiceRequestDetail> {
        createCalls.add(command)
        return createHandler(command)
    }

    override suspend fun getMyRequests(
        page: Int,
        size: Int,
        statusFilter: RequestStatus?,
    ): AppResult<ServiceRequestsPage> {
        listCalls.add(Triple(page, size, statusFilter))
        return getMyRequestsHandler(page, size, statusFilter)
    }

    override suspend fun getDetail(code: String): AppResult<ServiceRequestDetail> {
        detailCalls.add(code)
        return getDetailHandler(code)
    }

    override suspend fun cancel(command: CancelServiceRequestCommand): AppResult<ServiceRequestDetail> {
        cancelCalls.add(command)
        return cancelHandler(command)
    }
}

object RequestTestFixtures {
    val sampleCreatedAt: Instant = Instant.parse("2026-11-14T18:30:00-05:00")
    val sampleUpdatedAt: Instant = Instant.parse("2026-11-14T19:45:00-05:00")
    val sampleRespondedAt: Instant = Instant.parse("2026-11-15T10:15:00-05:00")
    val sampleClosedAt: Instant = Instant.parse("2026-11-15T16:00:00-05:00")

    fun requestSummary(
        id: String = "req-1",
        code: String = "SOL-2026-00019",
        subject: String = "Solicitud de certificado de notas oficial",
        category: RequestCategory = RequestCategory.TRAMITE,
        status: RequestStatus = RequestStatus.ABIERTA,
        previousRequestCode: String? = "SOL-2026-00012",
        createdAt: Instant = sampleCreatedAt,
    ): ServiceRequestSummary =
        ServiceRequestSummary(
            id = id,
            code = code,
            subject = subject,
            category = category,
            status = status,
            previousRequestCode = previousRequestCode,
            createdAt = createdAt,
        )

    fun requestsPage(
        items: List<ServiceRequestSummary> = listOf(requestSummary()),
        page: Int = 1,
        size: Int = 20,
        totalItems: Long = items.size.toLong(),
        totalPages: Int = 1,
    ): ServiceRequestsPage =
        ServiceRequestsPage(
            items = items,
            page = page,
            size = size,
            totalItems = totalItems,
            totalPages = totalPages,
        )

    fun historyEntry(
        id: String = "hist-1",
        action: RequestHistoryAction = RequestHistoryAction.CREACION,
        previousState: RequestStatus? = null,
        newState: RequestStatus = RequestStatus.ABIERTA,
        occurredAt: Instant = sampleCreatedAt,
        comment: String? = "Solicitud registrada por el solicitante.",
    ): ServiceRequestHistoryEntry =
        ServiceRequestHistoryEntry(
            id = id,
            action = action,
            previousState = previousState,
            newState = newState,
            occurredAt = occurredAt,
            comment = comment,
        )

    fun requestDetail(
        id: String = "req-1",
        code: String = "SOL-2026-00019",
        category: RequestCategory = RequestCategory.TRAMITE,
        subject: String = "Solicitud de certificado de notas oficial",
        description: String =
            "Requiero el certificado oficial de calificaciones del semestre 2026-1 para trámite de beca.",
        status: RequestStatus = RequestStatus.ABIERTA,
        previousRequestCode: String? = "SOL-2026-00012",
        responseText: String? = null,
        respondedAt: Instant? = null,
        closedAt: Instant? = null,
        createdAt: Instant = sampleCreatedAt,
        updatedAt: Instant = sampleUpdatedAt,
        history: List<ServiceRequestHistoryEntry> = listOf(historyEntry()),
    ): ServiceRequestDetail =
        ServiceRequestDetail(
            id = id,
            code = code,
            category = category,
            subject = subject,
            description = description,
            status = status,
            previousRequestCode = previousRequestCode,
            responseText = responseText,
            respondedAt = respondedAt,
            closedAt = closedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            history = history,
        )

    fun requestDetailJson(
        id: String = "b7a1c2d3-4e5f-6789-abcd-0123456789ab",
        code: String = "SOL-2026-00019",
        category: String = "TRAMITE",
        status: String = "ABIERTA",
        previousRequestCode: String? = "SOL-2026-00012",
        responseText: String? = null,
        respondedAt: String? = null,
        closedAt: String? = null,
    ): String {
        val prevJson = previousRequestCode?.let { "\"$it\"" } ?: "null"
        val respJson = responseText?.let { "\"$it\"" } ?: "null"
        val respAtJson = respondedAt?.let { "\"$it\"" } ?: "null"
        val closedAtJson = closedAt?.let { "\"$it\"" } ?: "null"
        return """
            {
              "id": "$id",
              "code": "$code",
              "category": "$category",
              "subject": "Solicitud de certificado de notas oficial",
              "description": "Requiero el certificado oficial de calificaciones del semestre 2026-1 para trámite de beca.",
              "status": "$status",
              "previousRequestCode": $prevJson,
              "responseText": $respJson,
              "respondedAt": $respAtJson,
              "closedAt": $closedAtJson,
              "createdAt": "2026-11-14T18:30:00-05:00",
              "updatedAt": "2026-11-14T19:45:00-05:00",
              "history": [
                {
                  "id": "c1d2e3f4-5a6b-7c8d-9e0f-112233445566",
                  "action": "CREACION",
                  "previousState": null,
                  "newState": "ABIERTA",
                  "occurredAt": "2026-11-14T18:30:00-05:00",
                  "comment": "Solicitud registrada por el solicitante."
                }
              ]
            }
            """.trimIndent()
    }

    fun pagedRequestsJson(
        page: Int = 1,
        size: Int = 20,
        totalItems: Long = 1,
        totalPages: Int = 1,
        status: String = "ABIERTA",
    ): String =
        """
        {
          "items": [
            {
              "id": "b7a1c2d3-4e5f-6789-abcd-0123456789ab",
              "code": "SOL-2026-00019",
              "subject": "Solicitud de certificado de notas oficial",
              "category": "TRAMITE",
              "status": "$status",
              "previousRequestCode": "SOL-2026-00012",
              "createdAt": "2026-11-14T18:30:00-05:00"
            }
          ],
          "page": $page,
          "size": $size,
          "totalItems": $totalItems,
          "totalPages": $totalPages
        }
        """.trimIndent()
}
