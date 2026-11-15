package com.itm.servicedesk.repository

import com.itm.servicedesk.domain.builder.MyRequestsQuery
import com.itm.servicedesk.domain.builder.ServiceRequestBuilder
import com.itm.servicedesk.domain.model.ApplicantVisibleHistoryEntry
import com.itm.servicedesk.domain.model.HistoryAction
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequest
import com.itm.servicedesk.domain.model.ServiceRequestDetail
import com.itm.servicedesk.domain.model.ServiceRequestSummary
import com.itm.servicedesk.domain.repository.ServiceRequestRepository
import com.itm.servicedesk.domain.state.StateTransition
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class ExposedServiceRequestRepository(
    private val database: Database,
    private val timezone: String = "America/Bogota",
) : ServiceRequestRepository {
    private val zoneId: ZoneId = ZoneId.of(timezone)

    override fun createWithHistory(
        builder: ServiceRequestBuilder,
        historyEntryId: UUID,
        historyComment: String,
    ): ServiceRequestDetail =
        transaction(database) {
            val now = OffsetDateTime.now(zoneId)
            val year = now.year
            val lastNumber =
                exec(
                    stmt = SEQUENCE_UPSERT_SQL,
                    args = listOf(IntegerColumnType() to year),
                    explicitStatementType = StatementType.SELECT,
                ) { rs ->
                    if (rs.next()) rs.getInt(1) else null
                } ?: error("No fue posible generar el consecutivo anual de la solicitud.")

            val generatedCode = "SOL-$year-${lastNumber.toString().padStart(5, '0')}"
            val request =
                builder
                    .code(generatedCode)
                    .timezone(timezone)
                    .createdAt(now)
                    .build()

            ServiceRequestsTable.insert { row ->
                row[id] = request.id
                row[code] = request.code
                row[requesterId] = request.requesterId
                row[category] = request.category.name
                row[subject] = request.subject
                row[description] = request.description
                row[status] = request.status.name
                row[previousRequestId] = request.previousRequestId
                row[previousRequestCode] = request.previousRequestCode
                row[createdAt] = request.createdAt
                row[updatedAt] = request.updatedAt
            }

            RequestHistoryTable.insert { row ->
                row[id] = historyEntryId
                row[requestId] = request.id
                row[actorId] = request.requesterId
                row[actorRole] = HistoryActorRole.SOLICITANTE.name
                row[action] = HistoryAction.CREACION.name
                row[previousState] = null
                row[newState] = RequestStatus.ABIERTA.name
                row[comment] = historyComment
                row[createdAt] = request.createdAt
            }

            val initialEntry =
                ApplicantVisibleHistoryEntry(
                    id = historyEntryId,
                    action = HistoryAction.CREACION,
                    previousState = null,
                    newState = RequestStatus.ABIERTA,
                    occurredAt = request.createdAt,
                    comment = historyComment,
                )

            ServiceRequestDetail(
                request = request,
                history = listOf(initialEntry),
            )
        }

    override fun findRequestByCode(code: String): ServiceRequest? {
        val normalizedCode = code.trim()
        return transaction(database) {
            ServiceRequestsTable
                .selectAllRequestColumns()
                .where { ServiceRequestsTable.code eq normalizedCode }
                .limit(1)
                .singleOrNull()
                ?.toServiceRequest()
        }
    }

    override fun findByCode(code: String): ServiceRequestDetail? {
        val normalizedCode = code.trim()
        return transaction(database) {
            val request =
                ServiceRequestsTable
                    .selectAllRequestColumns()
                    .where { ServiceRequestsTable.code eq normalizedCode }
                    .limit(1)
                    .singleOrNull()
                    ?.toServiceRequest()
                    ?: return@transaction null

            val visibleHistory = loadVisibleHistory(request.id)
            ServiceRequestDetail(
                request = request,
                history = visibleHistory,
            )
        }
    }

    override fun findPagedByRequester(query: MyRequestsQuery): PagedResult<ServiceRequestSummary> =
        transaction(database) {
            val whereClause: Op<Boolean> =
                if (query.status != null) {
                    (ServiceRequestsTable.requesterId eq query.requesterId) and
                        (ServiceRequestsTable.status eq query.status.name)
                } else {
                    ServiceRequestsTable.requesterId eq query.requesterId
                }

            val totalItems =
                ServiceRequestsTable
                    .select(ServiceRequestsTable.id)
                    .where { whereClause }
                    .count()

            val totalPages =
                if (totalItems == 0L) {
                    0
                } else {
                    ((totalItems + query.size - 1L) / query.size).toInt()
                }

            val items =
                ServiceRequestsTable
                    .select(
                        ServiceRequestsTable.id,
                        ServiceRequestsTable.code,
                        ServiceRequestsTable.subject,
                        ServiceRequestsTable.category,
                        ServiceRequestsTable.status,
                        ServiceRequestsTable.previousRequestCode,
                        ServiceRequestsTable.createdAt,
                    ).where { whereClause }
                    .orderBy(ServiceRequestsTable.createdAt to SortOrder.DESC, ServiceRequestsTable.id to SortOrder.ASC)
                    .limit(query.size)
                    .offset(query.offset)
                    .map { row ->
                        ServiceRequestSummary(
                            id = row[ServiceRequestsTable.id],
                            code = row[ServiceRequestsTable.code],
                            subject = row[ServiceRequestsTable.subject],
                            category = RequestCategory.valueOf(row[ServiceRequestsTable.category]),
                            status = RequestStatus.valueOf(row[ServiceRequestsTable.status]),
                            previousRequestCode = row[ServiceRequestsTable.previousRequestCode],
                            createdAt =
                                row[ServiceRequestsTable.createdAt]
                                    .atZoneSameInstant(
                                        zoneId,
                                    ).toOffsetDateTime(),
                        )
                    }

            PagedResult(
                items = items,
                page = query.page,
                size = query.size,
                totalItems = totalItems,
                totalPages = totalPages,
            )
        }

    override fun cancelWithHistory(
        requestId: UUID,
        transition: StateTransition,
        historyEntryId: UUID,
    ): ServiceRequestDetail? =
        transaction(database) {
            val updatedRows =
                ServiceRequestsTable.update(
                    where = {
                        (ServiceRequestsTable.id eq requestId) and
                            (ServiceRequestsTable.status eq transition.previousState.name)
                    },
                ) { row ->
                    row[status] = transition.newState.name
                    row[updatedAt] = transition.occurredAt
                }

            if (updatedRows != 1) {
                return@transaction null
            }

            RequestHistoryTable.insert { row ->
                row[id] = historyEntryId
                row[RequestHistoryTable.requestId] = requestId
                row[actorId] = transition.actorId
                row[actorRole] = transition.actorRole.name
                row[action] = transition.action.name
                row[previousState] = transition.previousState.name
                row[newState] = transition.newState.name
                row[comment] = transition.comment
                row[createdAt] = transition.occurredAt
            }

            val updatedRequest =
                ServiceRequestsTable
                    .selectAllRequestColumns()
                    .where { ServiceRequestsTable.id eq requestId }
                    .limit(1)
                    .single()
                    .toServiceRequest()

            val visibleHistory = loadVisibleHistory(requestId)
            ServiceRequestDetail(
                request = updatedRequest,
                history = visibleHistory,
            )
        }

    private fun loadVisibleHistory(requestId: UUID): List<ApplicantVisibleHistoryEntry> =
        RequestHistoryTable
            .select(
                RequestHistoryTable.id,
                RequestHistoryTable.action,
                RequestHistoryTable.previousState,
                RequestHistoryTable.newState,
                RequestHistoryTable.createdAt,
                RequestHistoryTable.comment,
            ).where {
                (RequestHistoryTable.requestId eq requestId) and
                    (RequestHistoryTable.action neq HistoryAction.INTERVENCION.name)
            }.orderBy(RequestHistoryTable.createdAt to SortOrder.ASC, RequestHistoryTable.id to SortOrder.ASC)
            .mapNotNull { row ->
                ApplicantVisibleHistoryEntry.fromRaw(
                    id = row[RequestHistoryTable.id],
                    action = HistoryAction.valueOf(row[RequestHistoryTable.action]),
                    previousState = row[RequestHistoryTable.previousState]?.let { RequestStatus.valueOf(it) },
                    newState = RequestStatus.valueOf(row[RequestHistoryTable.newState]),
                    occurredAt = row[RequestHistoryTable.createdAt].atZoneSameInstant(zoneId).toOffsetDateTime(),
                    rawComment = row[RequestHistoryTable.comment],
                )
            }

    private fun ServiceRequestsTable.selectAllRequestColumns() =
        select(
            id,
            code,
            requesterId,
            category,
            subject,
            description,
            status,
            previousRequestId,
            previousRequestCode,
            assignedTo,
            priority,
            firstAssignedAt,
            assignedAt,
            dueAt,
            responseText,
            respondedAt,
            respondedWithinSla,
            closedAt,
            createdAt,
            updatedAt,
        )

    private fun ResultRow.toServiceRequest(): ServiceRequest =
        ServiceRequest(
            id = this[ServiceRequestsTable.id],
            code = this[ServiceRequestsTable.code],
            requesterId = this[ServiceRequestsTable.requesterId],
            category = RequestCategory.valueOf(this[ServiceRequestsTable.category]),
            subject = this[ServiceRequestsTable.subject],
            description = this[ServiceRequestsTable.description],
            status = RequestStatus.valueOf(this[ServiceRequestsTable.status]),
            previousRequestId = this[ServiceRequestsTable.previousRequestId],
            previousRequestCode = this[ServiceRequestsTable.previousRequestCode],
            assignedTo = this[ServiceRequestsTable.assignedTo],
            priority = this[ServiceRequestsTable.priority]?.let { RequestPriority.valueOf(it) },
            firstAssignedAt =
                this[ServiceRequestsTable.firstAssignedAt]?.atZoneSameInstant(zoneId)?.toOffsetDateTime(),
            assignedAt = this[ServiceRequestsTable.assignedAt]?.atZoneSameInstant(zoneId)?.toOffsetDateTime(),
            dueAt = this[ServiceRequestsTable.dueAt]?.atZoneSameInstant(zoneId)?.toOffsetDateTime(),
            responseText = this[ServiceRequestsTable.responseText],
            respondedAt = this[ServiceRequestsTable.respondedAt]?.atZoneSameInstant(zoneId)?.toOffsetDateTime(),
            respondedWithinSla = this[ServiceRequestsTable.respondedWithinSla],
            closedAt = this[ServiceRequestsTable.closedAt]?.atZoneSameInstant(zoneId)?.toOffsetDateTime(),
            createdAt = this[ServiceRequestsTable.createdAt].atZoneSameInstant(zoneId).toOffsetDateTime(),
            updatedAt = this[ServiceRequestsTable.updatedAt].atZoneSameInstant(zoneId).toOffsetDateTime(),
        )

    companion object {
        private const val SEQUENCE_UPSERT_SQL =
            "INSERT INTO request_code_sequences (year, last_number) VALUES (?, 1) " +
                "ON CONFLICT (year) DO UPDATE SET last_number = request_code_sequences.last_number + 1 " +
                "RETURNING last_number"
    }
}
