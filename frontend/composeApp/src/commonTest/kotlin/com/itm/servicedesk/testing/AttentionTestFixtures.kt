package com.itm.servicedesk.testing

import com.itm.servicedesk.domain.model.AddInterventionCommand
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.AssignRequestCommand
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.AttentionRequestSummary
import com.itm.servicedesk.domain.model.AttentionRequestsPage
import com.itm.servicedesk.domain.model.EscalateRequestCommand
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.InternalHistoryEntry
import com.itm.servicedesk.domain.model.ReassignRequestCommand
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.RespondRequestCommand
import com.itm.servicedesk.domain.repository.AttentionRepository
import kotlin.time.Instant

class FakeAttentionRepository(
    var getCoordinationPendingHandler: suspend (Int, Int, RequestStatus?) -> AppResult<AttentionRequestsPage> =
        { page, size, _ ->
            AppResult.Success(AttentionTestFixtures.requestsPage(page = page, size = size))
        },
    var getMyAssignedHandler: suspend (Int, Int, RequestStatus?) -> AppResult<AttentionRequestsPage> =
        { page, size, _ ->
            AppResult.Success(
                AttentionTestFixtures.requestsPage(
                    items =
                        listOf(
                            AttentionTestFixtures.requestSummary(
                                status = RequestStatus.EN_REVISION,
                                priority = RequestPriority.ALTA,
                                assignedAgentId = "agent-1",
                                assignedAgentName = "Carlos Andrés Mejía",
                            ),
                        ),
                    page = page,
                    size = size,
                ),
            )
        },
    var getInternalDetailHandler: suspend (String) -> AppResult<AttentionRequestDetail> = { code ->
        AppResult.Success(AttentionTestFixtures.requestDetail(code = code))
    },
    var assignHandler: suspend (String, AssignRequestCommand) -> AppResult<AttentionRequestDetail> = { code, cmd ->
        AppResult.Success(
            AttentionTestFixtures.requestDetail(
                code = code,
                status = RequestStatus.EN_REVISION,
                priority = cmd.priority,
                assignedAgentId = cmd.assigneeId,
                assignedAgentName = "Carlos Andrés Mejía",
                dueAt = AttentionTestFixtures.sampleDueAt,
            ),
        )
    },
    var reassignHandler: suspend (String, ReassignRequestCommand) -> AppResult<AttentionRequestDetail> = { code, cmd ->
        AppResult.Success(
            AttentionTestFixtures.requestDetail(
                code = code,
                status = RequestStatus.EN_REVISION,
                priority = cmd.priority ?: RequestPriority.MEDIA,
                assignedAgentId = cmd.assigneeId,
                assignedAgentName = "María Fernanda López",
                dueAt = AttentionTestFixtures.sampleDueAt,
            ),
        )
    },
    var addInterventionHandler: suspend (String, AddInterventionCommand) -> AppResult<AttentionRequestDetail> =
        { code, cmd ->
            AppResult.Success(
                AttentionTestFixtures.requestDetail(
                    code = code,
                    status = RequestStatus.EN_REVISION,
                    priority = RequestPriority.ALTA,
                    assignedAgentId = "agent-1",
                    assignedAgentName = "Carlos Andrés Mejía",
                    history =
                        listOf(
                            AttentionTestFixtures.historyEntry(),
                            AttentionTestFixtures.historyEntry(
                                id = "hist-2",
                                eventType = "INTERVENCION",
                                actorName = "Carlos Andrés Mejía",
                                actorRole = HistoryActorRole.PERSONAL_ATENCION,
                                previousStatus = RequestStatus.EN_REVISION,
                                newStatus = RequestStatus.EN_REVISION,
                                detail = cmd.comment,
                            ),
                        ),
                ),
            )
        },
    var escalateHandler: suspend (String, EscalateRequestCommand) -> AppResult<AttentionRequestDetail> = { code, cmd ->
        AppResult.Success(
            AttentionTestFixtures.requestDetail(
                code = code,
                status = RequestStatus.ESCALADA,
                priority = RequestPriority.ALTA,
                assignedAgentId = "agent-1",
                assignedAgentName = "Carlos Andrés Mejía",
                history =
                    listOf(
                        AttentionTestFixtures.historyEntry(),
                        AttentionTestFixtures.historyEntry(
                            id = "hist-2",
                            eventType = "ESCALAMIENTO",
                            actorName = "Carlos Andrés Mejía",
                            actorRole = HistoryActorRole.PERSONAL_ATENCION,
                            previousStatus = RequestStatus.EN_REVISION,
                            newStatus = RequestStatus.ESCALADA,
                            detail = cmd.reason,
                        ),
                    ),
            ),
        )
    },
    var respondHandler: suspend (String, RespondRequestCommand) -> AppResult<AttentionRequestDetail> = { code, cmd ->
        AppResult.Success(
            AttentionTestFixtures.requestDetail(
                code = code,
                status = RequestStatus.RESUELTA,
                priority = RequestPriority.ALTA,
                assignedAgentId = "agent-1",
                assignedAgentName = "Carlos Andrés Mejía",
                responseText = cmd.responseText,
                respondedAt = AttentionTestFixtures.sampleRespondedAt,
                slaCompliant = true,
            ),
        )
    },
) : AttentionRepository {
    val coordinationPendingCalls = mutableListOf<Triple<Int, Int, RequestStatus?>>()
    val myAssignedCalls = mutableListOf<Triple<Int, Int, RequestStatus?>>()
    val detailCalls = mutableListOf<String>()
    val assignCalls = mutableListOf<Pair<String, AssignRequestCommand>>()
    val reassignCalls = mutableListOf<Pair<String, ReassignRequestCommand>>()
    val interventionCalls = mutableListOf<Pair<String, AddInterventionCommand>>()
    val escalateCalls = mutableListOf<Pair<String, EscalateRequestCommand>>()
    val respondCalls = mutableListOf<Pair<String, RespondRequestCommand>>()

    override suspend fun getCoordinationPending(
        page: Int,
        size: Int,
        status: RequestStatus?,
    ): AppResult<AttentionRequestsPage> {
        coordinationPendingCalls.add(Triple(page, size, status))
        return getCoordinationPendingHandler(page, size, status)
    }

    override suspend fun getMyAssigned(
        page: Int,
        size: Int,
        status: RequestStatus?,
    ): AppResult<AttentionRequestsPage> {
        myAssignedCalls.add(Triple(page, size, status))
        return getMyAssignedHandler(page, size, status)
    }

    override suspend fun getInternalDetail(code: String): AppResult<AttentionRequestDetail> {
        detailCalls.add(code)
        return getInternalDetailHandler(code)
    }

    override suspend fun assignRequest(
        code: String,
        command: AssignRequestCommand,
    ): AppResult<AttentionRequestDetail> {
        assignCalls.add(code to command)
        return assignHandler(code, command)
    }

    override suspend fun reassignRequest(
        code: String,
        command: ReassignRequestCommand,
    ): AppResult<AttentionRequestDetail> {
        reassignCalls.add(code to command)
        return reassignHandler(code, command)
    }

    override suspend fun addIntervention(
        code: String,
        command: AddInterventionCommand,
    ): AppResult<AttentionRequestDetail> {
        interventionCalls.add(code to command)
        return addInterventionHandler(code, command)
    }

    override suspend fun escalateRequest(
        code: String,
        command: EscalateRequestCommand,
    ): AppResult<AttentionRequestDetail> {
        escalateCalls.add(code to command)
        return escalateHandler(code, command)
    }

    override suspend fun respondRequest(
        code: String,
        command: RespondRequestCommand,
    ): AppResult<AttentionRequestDetail> {
        respondCalls.add(code to command)
        return respondHandler(code, command)
    }
}

object AttentionTestFixtures {
    val sampleCreatedAt: Instant = Instant.parse("2026-11-14T18:30:00-05:00")
    val sampleAssignedAt: Instant = Instant.parse("2026-11-14T19:00:00-05:00")
    val sampleDueAt: Instant = Instant.parse("2026-11-15T19:00:00-05:00")
    val sampleRespondedAt: Instant = Instant.parse("2026-11-15T10:15:00-05:00")
    val sampleClosedAt: Instant = Instant.parse("2026-11-17T10:15:00-05:00")

    fun requestSummary(
        code: String = "SOL-2026-00019",
        subject: String = "Solicitud de certificado de notas oficial",
        category: RequestCategory = RequestCategory.TRAMITE,
        status: RequestStatus = RequestStatus.ABIERTA,
        priority: RequestPriority? = null,
        requesterName: String = "Laura Gómez Pérez",
        assignedAgentId: String? = null,
        assignedAgentName: String? = null,
        createdAt: Instant = sampleCreatedAt,
        dueAt: Instant? = null,
        isOverdue: Boolean = false,
    ): AttentionRequestSummary =
        AttentionRequestSummary(
            code = code,
            subject = subject,
            category = category,
            status = status,
            priority = priority,
            requesterName = requesterName,
            assignedAgentId = assignedAgentId,
            assignedAgentName = assignedAgentName,
            createdAt = createdAt,
            dueAt = dueAt,
            isOverdue = isOverdue,
        )

    fun requestsPage(
        items: List<AttentionRequestSummary> = listOf(requestSummary()),
        page: Int = 1,
        size: Int = 20,
        totalItems: Long = items.size.toLong(),
        totalPages: Int = 1,
    ): AttentionRequestsPage =
        AttentionRequestsPage(
            items = items,
            page = page,
            size = size,
            totalItems = totalItems,
            totalPages = totalPages,
        )

    fun historyEntry(
        id: String = "hist-1",
        eventType: String = "CREACION",
        actorName: String? = "Laura Gómez Pérez",
        actorRole: HistoryActorRole = HistoryActorRole.SOLICITANTE,
        previousStatus: RequestStatus? = null,
        newStatus: RequestStatus? = RequestStatus.ABIERTA,
        detail: String? = "Solicitud registrada por el solicitante.",
        createdAt: Instant = sampleCreatedAt,
    ): InternalHistoryEntry =
        InternalHistoryEntry(
            id = id,
            eventType = eventType,
            actorName = actorName,
            actorRole = actorRole,
            previousStatus = previousStatus,
            newStatus = newStatus,
            detail = detail,
            createdAt = createdAt,
        )

    fun requestDetail(
        code: String = "SOL-2026-00019",
        subject: String = "Solicitud de certificado de notas oficial",
        description: String =
            "Requiero el certificado oficial de calificaciones del semestre 2026-1 para trámite de beca.",
        category: RequestCategory = RequestCategory.TRAMITE,
        status: RequestStatus = RequestStatus.ABIERTA,
        priority: RequestPriority? = null,
        requesterId: String = "req-1",
        requesterName: String = "Laura Gómez Pérez",
        assignedAgentId: String? = null,
        assignedAgentName: String? = null,
        previousRequestCode: String? = null,
        responseText: String? = null,
        createdAt: Instant = sampleCreatedAt,
        assignedAt: Instant? = null,
        dueAt: Instant? = null,
        respondedAt: Instant? = null,
        closedAt: Instant? = null,
        isOverdue: Boolean = false,
        slaCompliant: Boolean? = null,
        history: List<InternalHistoryEntry> = listOf(historyEntry()),
    ): AttentionRequestDetail =
        AttentionRequestDetail(
            code = code,
            subject = subject,
            description = description,
            category = category,
            status = status,
            priority = priority,
            requesterId = requesterId,
            requesterName = requesterName,
            assignedAgentId = assignedAgentId,
            assignedAgentName = assignedAgentName,
            previousRequestCode = previousRequestCode,
            responseText = responseText,
            createdAt = createdAt,
            assignedAt = assignedAt,
            dueAt = dueAt,
            respondedAt = respondedAt,
            closedAt = closedAt,
            isOverdue = isOverdue,
            slaCompliant = slaCompliant,
            history = history,
        )

    fun pagedAttentionJson(
        page: Int = 1,
        size: Int = 20,
        totalItems: Long = 1,
        totalPages: Int = 1,
        status: String = "ABIERTA",
        priority: String? = null,
        isOverdue: Boolean = false,
    ): String {
        val priorityJson = priority?.let { "\"$it\"" } ?: "null"
        return """
            {
              "items": [
                {
                  "code": "SOL-2026-00019",
                  "subject": "Solicitud de certificado de notas oficial",
                  "category": "TRAMITE",
                  "status": "$status",
                  "priority": $priorityJson,
                  "requesterName": "Laura Gómez Pérez",
                  "assignedAgentId": null,
                  "assignedAgentName": null,
                  "createdAt": "2026-11-14T18:30:00-05:00",
                  "dueAt": null,
                  "isOverdue": $isOverdue
                }
              ],
              "page": $page,
              "size": $size,
              "totalItems": $totalItems,
              "totalPages": $totalPages
            }
            """.trimIndent()
    }

    fun attentionDetailJson(
        code: String = "SOL-2026-00019",
        status: String = "EN_REVISION",
        priority: String? = "ALTA",
        isOverdue: Boolean = false,
        slaCompliant: Boolean? = null,
    ): String {
        val priorityJson = priority?.let { "\"$it\"" } ?: "null"
        val slaJson = slaCompliant?.toString() ?: "null"
        return """
            {
              "code": "$code",
              "subject": "Solicitud de certificado de notas oficial",
              "description": "Requiero el certificado oficial de calificaciones del semestre 2026-1.",
              "category": "TRAMITE",
              "status": "$status",
              "priority": $priorityJson,
              "requesterId": "req-1",
              "requesterName": "Laura Gómez Pérez",
              "assignedAgentId": "agent-1",
              "assignedAgentName": "Carlos Andrés Mejía",
              "previousRequestCode": null,
              "responseText": null,
              "createdAt": "2026-11-14T18:30:00-05:00",
              "assignedAt": "2026-11-14T19:00:00-05:00",
              "dueAt": "2026-11-15T19:00:00-05:00",
              "respondedAt": null,
              "closedAt": null,
              "isOverdue": $isOverdue,
              "slaCompliant": $slaJson,
              "history": [
                {
                  "id": "hist-1",
                  "eventType": "CREACION",
                  "actorName": "Laura Gómez Pérez",
                  "actorRole": "SOLICITANTE",
                  "previousStatus": null,
                  "newStatus": "ABIERTA",
                  "detail": "Solicitud registrada.",
                  "createdAt": "2026-11-14T18:30:00-05:00"
                }
              ]
            }
            """.trimIndent()
    }
}
