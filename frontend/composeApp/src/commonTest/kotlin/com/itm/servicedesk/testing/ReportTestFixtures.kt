package com.itm.servicedesk.testing

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.AssigneeMetric
import com.itm.servicedesk.domain.model.CoordinationReportRequestItem
import com.itm.servicedesk.domain.model.CoordinationReportRequestsPage
import com.itm.servicedesk.domain.model.CoordinationReportSummary
import com.itm.servicedesk.domain.model.ReportFilterQuery
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.repository.ReportRepository
import kotlin.time.Instant

class FakeReportRepository(
    var getSummaryHandler: suspend (ReportFilterQuery) -> AppResult<CoordinationReportSummary> = {
        AppResult.Success(ReportTestFixtures.summary())
    },
    var getRequestsHandler: suspend (ReportFilterQuery, Int, Int) -> AppResult<CoordinationReportRequestsPage> =
        { _, page, size ->
            AppResult.Success(ReportTestFixtures.requestsPage(page = page, size = size))
        },
) : ReportRepository {
    val summaryCalls = mutableListOf<ReportFilterQuery>()
    val requestsCalls = mutableListOf<Triple<ReportFilterQuery, Int, Int>>()

    override suspend fun getSummary(filter: ReportFilterQuery): AppResult<CoordinationReportSummary> {
        summaryCalls.add(filter)
        return getSummaryHandler(filter)
    }

    override suspend fun getRequests(
        filter: ReportFilterQuery,
        page: Int,
        size: Int,
    ): AppResult<CoordinationReportRequestsPage> {
        requestsCalls.add(Triple(filter, page, size))
        return getRequestsHandler(filter, page, size)
    }
}

object ReportTestFixtures {
    val sampleGeneratedAt: Instant = Instant.parse("2026-11-15T15:30:00Z")
    val sampleCreatedAt: Instant = Instant.parse("2026-11-14T18:30:00-05:00")
    val sampleDueAt: Instant = Instant.parse("2026-11-15T18:30:00-05:00")
    val sampleRespondedAt: Instant = Instant.parse("2026-11-15T12:15:00-05:00")

    fun summary(
        generatedAt: Instant = sampleGeneratedAt,
        totalRequests: Long = 10L,
        byStatus: Map<RequestStatus, Long> =
            mapOf(
                RequestStatus.ABIERTA to 2L,
                RequestStatus.EN_REVISION to 3L,
                RequestStatus.ESCALADA to 1L,
                RequestStatus.RESUELTA to 2L,
                RequestStatus.CERRADA to 1L,
                RequestStatus.CANCELADA to 1L,
            ),
        byCategory: Map<RequestCategory, Long> =
            mapOf(
                RequestCategory.TRAMITE to 4L,
                RequestCategory.ORIENTACION to 2L,
                RequestCategory.ATENCION_ADMINISTRATIVA to 3L,
                RequestCategory.REQUERIMIENTO_SERVICIO to 1L,
            ),
        byPriority: Map<RequestPriority, Long> =
            mapOf(
                RequestPriority.ALTA to 3L,
                RequestPriority.MEDIA to 4L,
                RequestPriority.BAJA to 1L,
            ),
        unassignedPriorityCount: Long = 2L,
        byAssignee: List<AssigneeMetric> =
            listOf(
                AssigneeMetric(
                    assigneeId = "agent-1",
                    assigneeName = "Carlos Andrés Mejía",
                    totalAssigned = 5L,
                    overdueAssigned = 1L,
                ),
                AssigneeMetric(
                    assigneeId = "agent-2",
                    assigneeName = "María Fernanda López",
                    totalAssigned = 3L,
                    overdueAssigned = 0L,
                ),
            ),
        unassignedCount: Long = 2L,
        overdueCount: Long = 1L,
        resolvedWithinSlaCount: Long = 2L,
        resolvedOutsideSlaCount: Long = 1L,
    ): CoordinationReportSummary =
        CoordinationReportSummary(
            generatedAt = generatedAt,
            totalRequests = totalRequests,
            byStatus = byStatus,
            byCategory = byCategory,
            byPriority = byPriority,
            unassignedPriorityCount = unassignedPriorityCount,
            byAssignee = byAssignee,
            unassignedCount = unassignedCount,
            overdueCount = overdueCount,
            resolvedWithinSlaCount = resolvedWithinSlaCount,
            resolvedOutsideSlaCount = resolvedOutsideSlaCount,
        )

    fun emptySummary(): CoordinationReportSummary =
        CoordinationReportSummary(
            generatedAt = sampleGeneratedAt,
            totalRequests = 0L,
            byStatus = emptyMap(),
            byCategory = emptyMap(),
            byPriority = emptyMap(),
            unassignedPriorityCount = 0L,
            byAssignee = emptyList(),
            unassignedCount = 0L,
            overdueCount = 0L,
            resolvedWithinSlaCount = 0L,
            resolvedOutsideSlaCount = 0L,
        )

    fun requestItem(
        id: String = "req-id-1",
        code: String = "SOL-2026-00019",
        subject: String = "Certificado académico oficial",
        category: RequestCategory = RequestCategory.TRAMITE,
        status: RequestStatus = RequestStatus.EN_REVISION,
        priority: RequestPriority? = RequestPriority.ALTA,
        requesterId: String = "user-1",
        requesterName: String = "Laura Gómez Pérez",
        assignedToId: String? = "agent-1",
        assignedToName: String? = "Carlos Andrés Mejía",
        dueAt: Instant? = sampleDueAt,
        isOverdue: Boolean = false,
        respondedAt: Instant? = null,
        respondedWithinSla: Boolean? = null,
        closedAt: Instant? = null,
        createdAt: Instant = sampleCreatedAt,
    ): CoordinationReportRequestItem =
        CoordinationReportRequestItem(
            id = id,
            code = code,
            subject = subject,
            category = category,
            status = status,
            priority = priority,
            requesterId = requesterId,
            requesterName = requesterName,
            assignedToId = assignedToId,
            assignedToName = assignedToName,
            dueAt = dueAt,
            isOverdue = isOverdue,
            respondedAt = respondedAt,
            respondedWithinSla = respondedWithinSla,
            closedAt = closedAt,
            createdAt = createdAt,
        )

    fun requestsPage(
        items: List<CoordinationReportRequestItem> = listOf(requestItem()),
        page: Int = 1,
        size: Int = 20,
        totalItems: Long = items.size.toLong(),
        totalPages: Int = 1,
    ): CoordinationReportRequestsPage =
        CoordinationReportRequestsPage(
            items = items,
            page = page,
            size = size,
            totalItems = totalItems,
            totalPages = totalPages,
        )

    fun summaryJson(): String =
        """
        {
          "generatedAt": "2026-11-15T15:30:00Z",
          "totalRequests": 10,
          "byStatus": {
            "ABIERTA": 2,
            "EN_REVISION": 3,
            "ESCALADA": 1,
            "RESUELTA": 2,
            "CERRADA": 1,
            "CANCELADA": 1
          },
          "byCategory": {
            "TRAMITE": 4,
            "ORIENTACION": 2,
            "ATENCION_ADMINISTRATIVA": 3,
            "REQUERIMIENTO_SERVICIO": 1
          },
          "byPriority": {
            "ALTA": 3,
            "MEDIA": 4,
            "BAJA": 1
          },
          "unassignedPriorityCount": 2,
          "byAssignee": [
            {
              "assigneeId": "agent-1",
              "assigneeName": "Carlos Andrés Mejía",
              "totalAssigned": 5,
              "overdueAssigned": 1
            }
          ],
          "unassignedCount": 2,
          "overdueCount": 1,
          "resolvedWithinSlaCount": 2,
          "resolvedOutsideSlaCount": 1
        }
        """.trimIndent()

    fun pagedRequestsJson(
        page: Int = 1,
        size: Int = 20,
        totalItems: Long = 1,
        totalPages: Int = 1,
    ): String =
        """
        {
          "items": [
            {
              "id": "req-id-1",
              "code": "SOL-2026-00019",
              "subject": "Certificado académico oficial",
              "category": "TRAMITE",
              "status": "RESUELTA",
              "priority": "ALTA",
              "requesterId": "user-1",
              "requesterName": "Laura Gómez Pérez",
              "assignedToId": "agent-1",
              "assignedToName": "Carlos Andrés Mejía",
              "dueAt": "2026-11-15T18:30:00-05:00",
              "isOverdue": false,
              "respondedAt": "2026-11-15T12:15:00-05:00",
              "respondedWithinSla": true,
              "closedAt": null,
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
