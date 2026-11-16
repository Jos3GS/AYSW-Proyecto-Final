package com.itm.servicedesk.repository

import com.itm.servicedesk.domain.model.AssigneeMetric
import com.itm.servicedesk.domain.model.CoordinationReportPageQuery
import com.itm.servicedesk.domain.model.CoordinationReportRequestItem
import com.itm.servicedesk.domain.model.CoordinationReportSummary
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.ReportFilterCriteria
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.repository.ReportRepository
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneId

class ExposedReportRepository(
    private val database: Database,
    timezone: String = "America/Bogota",
) : ReportRepository {
    private val zoneId: ZoneId = ZoneId.of(timezone)
    private val requesterUsers = UsersTable.alias("report_requester_users")
    private val assignedUsers = UsersTable.alias("report_assigned_users")

    override fun getSummary(
        criteria: ReportFilterCriteria,
        now: OffsetDateTime,
    ): CoordinationReportSummary =
        transaction(
            db = database,
            transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ,
            readOnly = true,
        ) {
            val filterOp = ServiceRequestsTable.toFilterOp(criteria)
            val idCount = ServiceRequestsTable.id.count()

            val byStatus = RequestStatus.entries.associateWith { 0L }.toMutableMap()
            ServiceRequestsTable
                .select(ServiceRequestsTable.status, idCount)
                .where { filterOp }
                .groupBy(ServiceRequestsTable.status)
                .forEach { row ->
                    val status = RequestStatus.valueOf(row[ServiceRequestsTable.status])
                    byStatus[status] = row[idCount]
                }
            val totalRequests = byStatus.values.sum()

            val byCategory = RequestCategory.entries.associateWith { 0L }.toMutableMap()
            ServiceRequestsTable
                .select(ServiceRequestsTable.category, idCount)
                .where { filterOp }
                .groupBy(ServiceRequestsTable.category)
                .forEach { row ->
                    val category = RequestCategory.valueOf(row[ServiceRequestsTable.category])
                    byCategory[category] = row[idCount]
                }

            val byPriority = RequestPriority.entries.associateWith { 0L }.toMutableMap()
            var unassignedPriorityCount = 0L
            ServiceRequestsTable
                .select(ServiceRequestsTable.priority, idCount)
                .where { filterOp }
                .groupBy(ServiceRequestsTable.priority)
                .forEach { row ->
                    val rawPriority = row[ServiceRequestsTable.priority]
                    val count = row[idCount]
                    if (rawPriority == null) {
                        unassignedPriorityCount = count
                    } else {
                        byPriority[RequestPriority.valueOf(rawPriority)] = count
                    }
                }

            val overdueOp: Op<Boolean> =
                (ServiceRequestsTable.status inList ACTIVE_OVERDUE_STATUSES) and
                    ServiceRequestsTable.dueAt.isNotNull() and
                    (ServiceRequestsTable.dueAt less now)

            val overdueCaseSum =
                Case()
                    .When(overdueOp, longLiteral(1L))
                    .Else(longLiteral(0L))
                    .sum()

            val byAssignee =
                ServiceRequestsTable
                    .join(
                        otherTable = assignedUsers,
                        joinType = JoinType.INNER,
                        onColumn = ServiceRequestsTable.assignedTo,
                        otherColumn = assignedUsers[UsersTable.id],
                    ).select(
                        ServiceRequestsTable.assignedTo,
                        assignedUsers[UsersTable.fullName],
                        idCount,
                        overdueCaseSum,
                    ).where { filterOp and ServiceRequestsTable.assignedTo.isNotNull() }
                    .groupBy(ServiceRequestsTable.assignedTo, assignedUsers[UsersTable.fullName])
                    .orderBy(
                        idCount to SortOrder.DESC,
                        assignedUsers[UsersTable.fullName] to SortOrder.ASC,
                    ).map { row ->
                        AssigneeMetric(
                            assigneeId = checkNotNull(row[ServiceRequestsTable.assignedTo]),
                            assigneeName = row[assignedUsers[UsersTable.fullName]],
                            totalAssigned = row[idCount],
                            overdueAssigned = row[overdueCaseSum] ?: 0L,
                        )
                    }

            val unassignedSum =
                Case()
                    .When(ServiceRequestsTable.assignedTo.isNull(), longLiteral(1L))
                    .Else(longLiteral(0L))
                    .sum()
            val withinSlaOp: Op<Boolean> =
                (ServiceRequestsTable.status inList RESOLVED_SLA_STATUSES) and
                    (ServiceRequestsTable.respondedWithinSla eq true)
            val outsideSlaOp: Op<Boolean> =
                (ServiceRequestsTable.status inList RESOLVED_SLA_STATUSES) and
                    (ServiceRequestsTable.respondedWithinSla eq false)
            val withinSlaSum =
                Case()
                    .When(withinSlaOp, longLiteral(1L))
                    .Else(longLiteral(0L))
                    .sum()
            val outsideSlaSum =
                Case()
                    .When(outsideSlaOp, longLiteral(1L))
                    .Else(longLiteral(0L))
                    .sum()

            val aggregateRow =
                ServiceRequestsTable
                    .select(unassignedSum, overdueCaseSum, withinSlaSum, outsideSlaSum)
                    .where { filterOp }
                    .single()

            CoordinationReportSummary(
                generatedAt = now.atZoneSameInstant(zoneId).toOffsetDateTime(),
                totalRequests = totalRequests,
                byStatus = byStatus,
                byCategory = byCategory,
                byPriority = byPriority,
                unassignedPriorityCount = unassignedPriorityCount,
                byAssignee = byAssignee,
                unassignedCount = aggregateRow[unassignedSum] ?: 0L,
                overdueCount = aggregateRow[overdueCaseSum] ?: 0L,
                resolvedWithinSlaCount = aggregateRow[withinSlaSum] ?: 0L,
                resolvedOutsideSlaCount = aggregateRow[outsideSlaSum] ?: 0L,
            )
        }

    override fun findRequests(
        query: CoordinationReportPageQuery,
        now: OffsetDateTime,
    ): PagedResult<CoordinationReportRequestItem> =
        transaction(
            db = database,
            transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ,
            readOnly = true,
        ) {
            val filterOp = ServiceRequestsTable.toFilterOp(query.criteria)

            val totalItems =
                ServiceRequestsTable
                    .select(ServiceRequestsTable.id)
                    .where { filterOp }
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
                        ServiceRequestsTable.dueAt,
                        ServiceRequestsTable.respondedAt,
                        ServiceRequestsTable.respondedWithinSla,
                        ServiceRequestsTable.closedAt,
                        ServiceRequestsTable.createdAt,
                    ).where { filterOp }
                    .orderBy(
                        ServiceRequestsTable.createdAt to SortOrder.DESC,
                        ServiceRequestsTable.id to SortOrder.DESC,
                    ).limit(query.size)
                    .offset(query.offset)
                    .map { row -> row.toReportItem(now) }

            PagedResult(
                items = items,
                page = query.page,
                size = query.size,
                totalItems = totalItems,
                totalPages = totalPages,
            )
        }

    private fun ResultRow.toReportItem(now: OffsetDateTime): CoordinationReportRequestItem {
        val status = RequestStatus.valueOf(this[ServiceRequestsTable.status])
        val dueAt = this[ServiceRequestsTable.dueAt]?.atZoneSameInstant(zoneId)?.toOffsetDateTime()
        val isOverdue =
            (status == RequestStatus.EN_REVISION || status == RequestStatus.ESCALADA) &&
                dueAt != null &&
                now.isAfter(dueAt)

        return CoordinationReportRequestItem(
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
            dueAt = dueAt,
            isOverdue = isOverdue,
            respondedAt = this[ServiceRequestsTable.respondedAt]?.atZoneSameInstant(zoneId)?.toOffsetDateTime(),
            respondedWithinSla = this[ServiceRequestsTable.respondedWithinSla],
            closedAt = this[ServiceRequestsTable.closedAt]?.atZoneSameInstant(zoneId)?.toOffsetDateTime(),
            createdAt = this[ServiceRequestsTable.createdAt].atZoneSameInstant(zoneId).toOffsetDateTime(),
        )
    }

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
        private val ACTIVE_OVERDUE_STATUSES =
            listOf(RequestStatus.EN_REVISION.name, RequestStatus.ESCALADA.name)
        private val RESOLVED_SLA_STATUSES =
            listOf(RequestStatus.RESUELTA.name, RequestStatus.CERRADA.name)
    }
}

internal fun ServiceRequestsTable.toFilterOp(criteria: ReportFilterCriteria): Op<Boolean> {
    var op: Op<Boolean> = Op.TRUE
    criteria.createdFromInclusive?.let { from ->
        op = op and (createdAt greaterEq from)
    }
    criteria.createdToExclusive?.let { toExclusive ->
        op = op and (createdAt less toExclusive)
    }
    criteria.status?.let { statusValue ->
        op = op and (status eq statusValue.name)
    }
    criteria.category?.let { categoryValue ->
        op = op and (category eq categoryValue.name)
    }
    criteria.priority?.let { priorityValue ->
        op = op and (priority eq priorityValue.name)
    }
    criteria.assignedToId?.let { assigneeId ->
        op = op and (assignedTo eq assigneeId)
    }
    return op
}
