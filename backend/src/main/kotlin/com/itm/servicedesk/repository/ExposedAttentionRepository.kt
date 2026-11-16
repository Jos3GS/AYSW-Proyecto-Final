package com.itm.servicedesk.repository

import com.itm.servicedesk.domain.builder.AttentionPageQuery
import com.itm.servicedesk.domain.builder.AttentionSortOrder
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.AttentionRequestSummary
import com.itm.servicedesk.domain.model.AttentionTransition
import com.itm.servicedesk.domain.model.HistoryAction
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.InternalHistoryEntry
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequest
import com.itm.servicedesk.domain.repository.AttentionRepository
import com.itm.servicedesk.domain.state.ServiceRequestState
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class ExposedAttentionRepository(
    private val database: Database,
    private val timezone: String = "America/Bogota",
) : AttentionRepository {
    private val zoneId: ZoneId = ZoneId.of(timezone)
    private val requesterUsers = UsersTable.alias("requester_users")
    private val assignedUsers = UsersTable.alias("assigned_users")
    private val historyActorUsers = UsersTable.alias("history_actor_users")

    override fun findPendingForCoordination(
        query: AttentionPageQuery,
        now: OffsetDateTime,
    ): PagedResult<AttentionRequestSummary> =
        transaction(database) {
            val whereClause: Op<Boolean> =
                if (query.status != null) {
                    ServiceRequestsTable.status eq query.status.name
                } else {
                    ServiceRequestsTable.status inList
                        listOf(RequestStatus.ABIERTA.name, RequestStatus.ESCALADA.name)
                }

            val totalItems =
                ServiceRequestsTable
                    .select(ServiceRequestsTable.id)
                    .where { whereClause }
                    .count()

            val totalPages = computeTotalPages(totalItems, query.size)

            val items =
                ServiceRequestsTable
                    .join(
                        otherTable = requesterUsers,
                        joinType = JoinType.INNER,
                        onColumn = ServiceRequestsTable.requesterId,
                        otherColumn = requesterUsers[UsersTable.id],
                    ).join(
                        otherTable = assignedUsers,
                        joinType = JoinType.LEFT,
                        onColumn = ServiceRequestsTable.assignedTo,
                        otherColumn = assignedUsers[UsersTable.id],
                    ).select(
                        ServiceRequestsTable.id,
                        ServiceRequestsTable.code,
                        ServiceRequestsTable.subject,
                        ServiceRequestsTable.category,
                        ServiceRequestsTable.status,
                        ServiceRequestsTable.priority,
                        ServiceRequestsTable.requesterId,
                        requesterUsers[UsersTable.fullName],
                        ServiceRequestsTable.assignedTo,
                        assignedUsers[UsersTable.fullName],
                        ServiceRequestsTable.firstAssignedAt,
                        ServiceRequestsTable.dueAt,
                        ServiceRequestsTable.createdAt,
                    ).where { whereClause }
                    .orderBy(
                        ServiceRequestsTable.createdAt to SortOrder.ASC,
                        ServiceRequestsTable.id to SortOrder.ASC,
                    ).limit(query.size)
                    .offset(query.offset)
                    .map { row -> row.toSummary(now) }

            PagedResult(
                items = items,
                page = query.page,
                size = query.size,
                totalItems = totalItems,
                totalPages = totalPages,
            )
        }

    override fun findAssignedToAgent(
        query: AttentionPageQuery,
        now: OffsetDateTime,
    ): PagedResult<AttentionRequestSummary> =
        transaction(database) {
            val agentId = checkNotNull(query.assignedTo) { "El identificador del agente asignado es obligatorio." }
            val whereClause: Op<Boolean> =
                if (query.status != null) {
                    (ServiceRequestsTable.assignedTo eq agentId) and
                        (ServiceRequestsTable.status eq query.status.name)
                } else {
                    ServiceRequestsTable.assignedTo eq agentId
                }

            val totalItems =
                ServiceRequestsTable
                    .select(ServiceRequestsTable.id)
                    .where { whereClause }
                    .count()

            val totalPages = computeTotalPages(totalItems, query.size)

            val orderColumns: Array<Pair<Expression<*>, SortOrder>> =
                when (query.sortOrder) {
                    AttentionSortOrder.CREATED_AT_ASC -> {
                        arrayOf(
                            ServiceRequestsTable.createdAt to SortOrder.ASC,
                            ServiceRequestsTable.id to SortOrder.ASC,
                        )
                    }

                    AttentionSortOrder.DUE_AT_ASC_NULLS_LAST -> {
                        arrayOf(
                            ServiceRequestsTable.dueAt to SortOrder.ASC_NULLS_LAST,
                            ServiceRequestsTable.createdAt to SortOrder.ASC,
                            ServiceRequestsTable.id to SortOrder.ASC,
                        )
                    }
                }

            val items =
                ServiceRequestsTable
                    .join(
                        otherTable = requesterUsers,
                        joinType = JoinType.INNER,
                        onColumn = ServiceRequestsTable.requesterId,
                        otherColumn = requesterUsers[UsersTable.id],
                    ).join(
                        otherTable = assignedUsers,
                        joinType = JoinType.LEFT,
                        onColumn = ServiceRequestsTable.assignedTo,
                        otherColumn = assignedUsers[UsersTable.id],
                    ).select(
                        ServiceRequestsTable.id,
                        ServiceRequestsTable.code,
                        ServiceRequestsTable.subject,
                        ServiceRequestsTable.category,
                        ServiceRequestsTable.status,
                        ServiceRequestsTable.priority,
                        ServiceRequestsTable.requesterId,
                        requesterUsers[UsersTable.fullName],
                        ServiceRequestsTable.assignedTo,
                        assignedUsers[UsersTable.fullName],
                        ServiceRequestsTable.firstAssignedAt,
                        ServiceRequestsTable.dueAt,
                        ServiceRequestsTable.createdAt,
                    ).where { whereClause }
                    .orderBy(*orderColumns)
                    .limit(query.size)
                    .offset(query.offset)
                    .map { row -> row.toSummary(now) }

            PagedResult(
                items = items,
                page = query.page,
                size = query.size,
                totalItems = totalItems,
                totalPages = totalPages,
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

    override fun findInternalDetailByCode(
        code: String,
        now: OffsetDateTime,
    ): AttentionRequestDetail? {
        val normalizedCode = code.trim()
        return transaction(database) {
            val row =
                ServiceRequestsTable
                    .join(
                        otherTable = requesterUsers,
                        joinType = JoinType.INNER,
                        onColumn = ServiceRequestsTable.requesterId,
                        otherColumn = requesterUsers[UsersTable.id],
                    ).join(
                        otherTable = assignedUsers,
                        joinType = JoinType.LEFT,
                        onColumn = ServiceRequestsTable.assignedTo,
                        otherColumn = assignedUsers[UsersTable.id],
                    ).select(
                        ServiceRequestsTable.id,
                        ServiceRequestsTable.code,
                        ServiceRequestsTable.requesterId,
                        ServiceRequestsTable.category,
                        ServiceRequestsTable.subject,
                        ServiceRequestsTable.description,
                        ServiceRequestsTable.status,
                        ServiceRequestsTable.previousRequestId,
                        ServiceRequestsTable.previousRequestCode,
                        ServiceRequestsTable.assignedTo,
                        ServiceRequestsTable.priority,
                        ServiceRequestsTable.firstAssignedAt,
                        ServiceRequestsTable.assignedAt,
                        ServiceRequestsTable.dueAt,
                        ServiceRequestsTable.responseText,
                        ServiceRequestsTable.respondedAt,
                        ServiceRequestsTable.respondedWithinSla,
                        ServiceRequestsTable.closedAt,
                        ServiceRequestsTable.createdAt,
                        ServiceRequestsTable.updatedAt,
                        requesterUsers[UsersTable.fullName],
                        assignedUsers[UsersTable.fullName],
                    ).where { ServiceRequestsTable.code eq normalizedCode }
                    .limit(1)
                    .singleOrNull()
                    ?: return@transaction null

            val request = row.toServiceRequest()
            val requesterName = row[requesterUsers[UsersTable.fullName]]
            val assignedToName = row.getOrNull(assignedUsers[UsersTable.fullName])
            val history = loadInternalHistory(request.id)

            AttentionRequestDetail(
                request = request,
                requesterName = requesterName,
                assignedToName = assignedToName,
                isOverdue = request.isOverdue(now),
                history = history,
            )
        }
    }

    override fun applyTransitionWithHistory(
        expectedCurrentStatus: RequestStatus,
        transition: AttentionTransition,
        historyEntryId: UUID,
    ): ServiceRequest? =
        transaction(database) {
            val updatedRows =
                ServiceRequestsTable.update(
                    where = {
                        (ServiceRequestsTable.id eq transition.requestId) and
                            (ServiceRequestsTable.status eq expectedCurrentStatus.name)
                    },
                ) { row ->
                    row[status] = transition.newState.name
                    row[updatedAt] = transition.occurredAt
                    if (transition.updateAssignment) {
                        row[assignedTo] = transition.assignedTo
                    }
                    if (transition.updatePriority) {
                        row[priority] = transition.priority?.name
                    }
                    if (transition.firstAssignedAt != null) {
                        row[firstAssignedAt] = transition.firstAssignedAt
                    }
                    if (transition.assignedAt != null) {
                        row[assignedAt] = transition.assignedAt
                    }
                    if (transition.updateDueAt) {
                        row[dueAt] = transition.dueAt
                    }
                    if (transition.responseText != null) {
                        row[responseText] = transition.responseText
                    }
                    if (transition.respondedAt != null) {
                        row[respondedAt] = transition.respondedAt
                    }
                    if (transition.respondedWithinSla != null) {
                        row[respondedWithinSla] = transition.respondedWithinSla
                    }
                    if (transition.closedAt != null) {
                        row[closedAt] = transition.closedAt
                    }
                }

            if (updatedRows != 1) {
                return@transaction null
            }

            RequestHistoryTable.insert { row ->
                row[id] = historyEntryId
                row[requestId] = transition.requestId
                row[actorId] = transition.actorId
                row[actorRole] = transition.actorRole.name
                row[action] = transition.action.name
                row[previousState] = transition.previousState.name
                row[newState] = transition.newState.name
                row[comment] = transition.comment
                row[createdAt] = transition.occurredAt
            }

            ServiceRequestsTable
                .selectAllRequestColumns()
                .where { ServiceRequestsTable.id eq transition.requestId }
                .limit(1)
                .single()
                .toServiceRequest()
        }

    override fun autoCloseEligibleRequests(
        cutoff: OffsetDateTime,
        now: OffsetDateTime,
        batchSize: Int,
    ): Int =
        transaction(database) {
            val eligibleIds = mutableListOf<UUID>()
            exec(
                stmt = AUTO_CLOSE_SELECT_SQL,
                args =
                    listOf(
                        ServiceRequestsTable.respondedAt.columnType to cutoff,
                        IntegerColumnType() to batchSize,
                    ),
                explicitStatementType = StatementType.SELECT,
            ) { rs ->
                while (rs.next()) {
                    eligibleIds.add(rs.getObject(1, UUID::class.java))
                }
            }

            var closedCount = 0
            eligibleIds.forEach { reqId ->
                val updatedRows =
                    ServiceRequestsTable.update(
                        where = {
                            (ServiceRequestsTable.id eq reqId) and
                                (ServiceRequestsTable.status eq RequestStatus.RESUELTA.name)
                        },
                    ) { row ->
                        row[status] = RequestStatus.CERRADA.name
                        row[closedAt] = now
                        row[updatedAt] = now
                    }

                if (updatedRows == 1) {
                    RequestHistoryTable.insert { row ->
                        row[id] = UUID.randomUUID()
                        row[requestId] = reqId
                        row[actorId] = null
                        row[actorRole] = HistoryActorRole.SISTEMA.name
                        row[action] = HistoryAction.CIERRE_AUTOMATICO.name
                        row[previousState] = RequestStatus.RESUELTA.name
                        row[newState] = RequestStatus.CERRADA.name
                        row[comment] = ServiceRequestState.AUTO_CLOSE_COMMENT
                        row[createdAt] = now
                    }
                    closedCount++
                }
            }
            closedCount
        }

    private fun loadInternalHistory(requestId: UUID): List<InternalHistoryEntry> =
        RequestHistoryTable
            .join(
                otherTable = historyActorUsers,
                joinType = JoinType.LEFT,
                onColumn = RequestHistoryTable.actorId,
                otherColumn = historyActorUsers[UsersTable.id],
            ).select(
                RequestHistoryTable.id,
                RequestHistoryTable.action,
                RequestHistoryTable.previousState,
                RequestHistoryTable.newState,
                RequestHistoryTable.actorId,
                RequestHistoryTable.actorRole,
                RequestHistoryTable.comment,
                RequestHistoryTable.createdAt,
                historyActorUsers[UsersTable.fullName],
            ).where { RequestHistoryTable.requestId eq requestId }
            .orderBy(RequestHistoryTable.createdAt to SortOrder.ASC, RequestHistoryTable.id to SortOrder.ASC)
            .map { row ->
                val actorRole = HistoryActorRole.valueOf(row[RequestHistoryTable.actorRole])
                val actorName =
                    if (actorRole == HistoryActorRole.SISTEMA) {
                        SYSTEM_ACTOR_NAME
                    } else {
                        row.getOrNull(historyActorUsers[UsersTable.fullName]) ?: SYSTEM_ACTOR_NAME
                    }
                InternalHistoryEntry(
                    id = row[RequestHistoryTable.id],
                    action = HistoryAction.valueOf(row[RequestHistoryTable.action]),
                    previousState = row[RequestHistoryTable.previousState]?.let { RequestStatus.valueOf(it) },
                    newState = RequestStatus.valueOf(row[RequestHistoryTable.newState]),
                    actorId = row[RequestHistoryTable.actorId],
                    actorName = actorName,
                    actorRole = actorRole,
                    comment = row[RequestHistoryTable.comment],
                    occurredAt = row[RequestHistoryTable.createdAt].atZoneSameInstant(zoneId).toOffsetDateTime(),
                )
            }

    private fun ResultRow.toSummary(now: OffsetDateTime): AttentionRequestSummary {
        val status = RequestStatus.valueOf(this[ServiceRequestsTable.status])
        val dueAt = this[ServiceRequestsTable.dueAt]?.atZoneSameInstant(zoneId)?.toOffsetDateTime()
        val isOverdue =
            (status == RequestStatus.EN_REVISION || status == RequestStatus.ESCALADA) &&
                dueAt != null &&
                now.isAfter(dueAt)

        return AttentionRequestSummary(
            id = this[ServiceRequestsTable.id],
            code = this[ServiceRequestsTable.code],
            subject = this[ServiceRequestsTable.subject],
            category = RequestCategory.valueOf(this[ServiceRequestsTable.category]),
            status = status,
            priority = this[ServiceRequestsTable.priority]?.let { RequestPriority.valueOf(it) },
            requesterId = this[ServiceRequestsTable.requesterId],
            requesterName = this[requesterUsers[UsersTable.fullName]],
            assignedToId = this[ServiceRequestsTable.assignedTo],
            assignedToName = this.getOrNull(assignedUsers[UsersTable.fullName]),
            firstAssignedAt =
                this[ServiceRequestsTable.firstAssignedAt]?.atZoneSameInstant(zoneId)?.toOffsetDateTime(),
            dueAt = dueAt,
            isOverdue = isOverdue,
            createdAt = this[ServiceRequestsTable.createdAt].atZoneSameInstant(zoneId).toOffsetDateTime(),
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

    private fun computeTotalPages(
        totalItems: Long,
        size: Int,
    ): Int =
        if (totalItems == 0L) {
            0
        } else {
            ((totalItems + size - 1L) / size).toInt()
        }

    companion object {
        private const val SYSTEM_ACTOR_NAME = "Sistema"
        private const val AUTO_CLOSE_SELECT_SQL =
            "SELECT id FROM service_requests " +
                "WHERE status = 'RESUELTA' AND responded_at <= ? " +
                "ORDER BY responded_at ASC, id ASC " +
                "LIMIT ? " +
                "FOR UPDATE SKIP LOCKED"
    }
}
