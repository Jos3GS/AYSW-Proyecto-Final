package com.itm.servicedesk.service

import com.itm.servicedesk.domain.builder.ReportFilterBuilder
import com.itm.servicedesk.domain.model.AuthenticatedIdentity
import com.itm.servicedesk.domain.model.CoordinationReportRequestItem
import com.itm.servicedesk.domain.model.CoordinationReportSummary
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.ReportRepository
import com.itm.servicedesk.domain.time.BogotaTimeProvider
import com.itm.servicedesk.domain.time.TimeProvider
import java.time.OffsetDateTime
import java.time.ZoneId

class ReportService(
    private val reportRepository: ReportRepository,
    private val timeProvider: TimeProvider,
    timezone: String = BogotaTimeProvider.DEFAULT_TIMEZONE,
) {
    private val zoneId: ZoneId = ZoneId.of(timezone)

    fun getSummary(
        actor: AuthenticatedIdentity,
        createdFrom: String? = null,
        createdTo: String? = null,
        status: String? = null,
        category: String? = null,
        priority: String? = null,
        assignedToId: String? = null,
    ): CoordinationReportSummary {
        requireCoordinator(actor)
        val criteria =
            ReportFilterBuilder(zoneId)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .status(status)
                .category(category)
                .priority(priority)
                .assignedToId(assignedToId)
                .buildCriteria()
        val now = timeProvider.now()
        val summary = reportRepository.getSummary(criteria, now)
        return normalizeSummary(summary, now)
    }

    fun listRequests(
        actor: AuthenticatedIdentity,
        createdFrom: String? = null,
        createdTo: String? = null,
        status: String? = null,
        category: String? = null,
        priority: String? = null,
        assignedToId: String? = null,
        page: String? = null,
        size: String? = null,
    ): PagedResult<CoordinationReportRequestItem> {
        requireCoordinator(actor)
        val query =
            ReportFilterBuilder(zoneId)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .status(status)
                .category(category)
                .priority(priority)
                .assignedToId(assignedToId)
                .page(page)
                .size(size)
                .buildPageQuery()
        return reportRepository.findRequests(query, timeProvider.now())
    }

    private fun normalizeSummary(
        summary: CoordinationReportSummary,
        now: OffsetDateTime,
    ): CoordinationReportSummary {
        val completeByStatus = RequestStatus.entries.associateWith { summary.byStatus[it] ?: 0L }
        val completeByCategory = RequestCategory.entries.associateWith { summary.byCategory[it] ?: 0L }
        val completeByPriority = RequestPriority.entries.associateWith { summary.byPriority[it] ?: 0L }
        return summary.copy(
            generatedAt = now.atZoneSameInstant(zoneId).toOffsetDateTime(),
            byStatus = completeByStatus,
            byCategory = completeByCategory,
            byPriority = completeByPriority,
        )
    }

    private fun requireCoordinator(actor: AuthenticatedIdentity) {
        if (actor.role != UserRole.COORDINADOR) {
            throw DomainException.ForbiddenException(FORBIDDEN_MESSAGE)
        }
    }

    companion object {
        private const val FORBIDDEN_MESSAGE =
            "Solo el rol COORDINADOR tiene acceso a los reportes de coordinación."
    }
}
