package com.itm.servicedesk.service

import com.itm.servicedesk.FakeTimeProvider
import com.itm.servicedesk.domain.model.AssigneeMetric
import com.itm.servicedesk.domain.model.AuthenticatedIdentity
import com.itm.servicedesk.domain.model.CoordinationReportPageQuery
import com.itm.servicedesk.domain.model.CoordinationReportRequestItem
import com.itm.servicedesk.domain.model.CoordinationReportSummary
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.ReportFilterCriteria
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.ReportRepository
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReportServiceTest {
    private val bogotaOffset = ZoneOffset.of("-05:00")
    private val fixedNow = OffsetDateTime.of(2026, 11, 15, 14, 30, 0, 0, bogotaOffset)

    private val coordinatorActor =
        AuthenticatedIdentity(
            userId = UUID.randomUUID(),
            email = "coordinador@itm.edu.co",
            role = UserRole.COORDINADOR,
            jti = UUID.randomUUID(),
            expiresAt = fixedNow.plusHours(1),
        )

    private val agentActor =
        AuthenticatedIdentity(
            userId = UUID.randomUUID(),
            email = "agente@itm.edu.co",
            role = UserRole.PERSONAL_ATENCION,
            jti = UUID.randomUUID(),
            expiresAt = fixedNow.plusHours(1),
        )

    private val solicitanteActor =
        AuthenticatedIdentity(
            userId = UUID.randomUUID(),
            email = "solicitante@itm.edu.co",
            role = UserRole.SOLICITANTE,
            jti = UUID.randomUUID(),
            expiresAt = fixedNow.plusHours(1),
        )

    @Test
    fun `getSummary normalizes missing enum keys to zero and passes TimeProvider now`() {
        val assigneeId = UUID.randomUUID()
        var capturedCriteria: ReportFilterCriteria? = null
        var capturedNow: OffsetDateTime? = null

        val stubRepo =
            object : ReportRepository {
                override fun getSummary(
                    criteria: ReportFilterCriteria,
                    now: OffsetDateTime,
                ): CoordinationReportSummary {
                    capturedCriteria = criteria
                    capturedNow = now
                    return CoordinationReportSummary(
                        generatedAt = now.minusMinutes(5),
                        totalRequests = 5L,
                        byStatus = mapOf(RequestStatus.ABIERTA to 2L, RequestStatus.EN_REVISION to 3L),
                        byCategory = mapOf(RequestCategory.TRAMITE to 5L),
                        byPriority = mapOf(RequestPriority.ALTA to 3L),
                        unassignedPriorityCount = 2L,
                        byAssignee =
                            listOf(
                                AssigneeMetric(
                                    assigneeId = assigneeId,
                                    assigneeName = "Carlos Mejía",
                                    totalAssigned = 3L,
                                    overdueAssigned = 1L,
                                ),
                            ),
                        unassignedCount = 2L,
                        overdueCount = 1L,
                        resolvedWithinSlaCount = 0L,
                        resolvedOutsideSlaCount = 0L,
                    )
                }

                override fun findRequests(
                    query: CoordinationReportPageQuery,
                    now: OffsetDateTime,
                ): PagedResult<CoordinationReportRequestItem> = PagedResult(emptyList(), query.page, query.size, 0L, 0)
            }

        val service = ReportService(stubRepo, FakeTimeProvider(fixedNow))
        val summary =
            service.getSummary(
                actor = coordinatorActor,
                createdFrom = "2026-11-14",
                createdTo = "2026-11-15",
                category = "TRAMITE",
            )

        assertEquals(fixedNow, capturedNow)
        assertEquals(fixedNow, summary.generatedAt)
        assertEquals(
            OffsetDateTime.of(2026, 11, 14, 0, 0, 0, 0, bogotaOffset),
            capturedCriteria?.createdFromInclusive,
        )
        assertEquals(
            OffsetDateTime.of(2026, 11, 16, 0, 0, 0, 0, bogotaOffset),
            capturedCriteria?.createdToExclusive,
        )
        assertEquals(RequestCategory.TRAMITE, capturedCriteria?.category)

        assertEquals(RequestStatus.entries.toSet(), summary.byStatus.keys)
        assertEquals(RequestCategory.entries.toSet(), summary.byCategory.keys)
        assertEquals(RequestPriority.entries.toSet(), summary.byPriority.keys)
        assertEquals(0L, summary.byStatus[RequestStatus.CERRADA])
        assertEquals(0L, summary.byCategory[RequestCategory.ORIENTACION])
        assertEquals(0L, summary.byPriority[RequestPriority.BAJA])

        assertEquals(summary.totalRequests, summary.byStatus.values.sum())
        assertEquals(summary.totalRequests, summary.byCategory.values.sum())
        assertEquals(summary.totalRequests, summary.byPriority.values.sum() + summary.unassignedPriorityCount)
        assertEquals(summary.totalRequests, summary.byAssignee.sumOf { it.totalAssigned } + summary.unassignedCount)
        assertEquals(summary.overdueCount, summary.byAssignee.sumOf { it.overdueAssigned })
    }

    @Test
    fun `listRequests delegates builder validation and passes TimeProvider now`() {
        var capturedQuery: CoordinationReportPageQuery? = null
        var capturedNow: OffsetDateTime? = null

        val stubRepo =
            object : ReportRepository {
                override fun getSummary(
                    criteria: ReportFilterCriteria,
                    now: OffsetDateTime,
                ): CoordinationReportSummary = error("Not expected")

                override fun findRequests(
                    query: CoordinationReportPageQuery,
                    now: OffsetDateTime,
                ): PagedResult<CoordinationReportRequestItem> {
                    capturedQuery = query
                    capturedNow = now
                    return PagedResult(emptyList(), query.page, query.size, 0L, 0)
                }
            }

        val service = ReportService(stubRepo, FakeTimeProvider(fixedNow))
        val result =
            service.listRequests(
                actor = coordinatorActor,
                status = "ESCALADA",
                page = "2",
                size = "10",
            )

        assertEquals(fixedNow, capturedNow)
        assertEquals(RequestStatus.ESCALADA, capturedQuery?.criteria?.status)
        assertEquals(2, result.page)
        assertEquals(10, result.size)

        assertFailsWith<DomainException.ValidationException> {
            service.listRequests(
                actor = coordinatorActor,
                createdFrom = "2026-11-20",
                createdTo = "2026-11-10",
                size = "25",
            )
        }
    }

    @Test
    fun `rejects non-COORDINADOR actors with ForbiddenException`() {
        val stubRepo =
            object : ReportRepository {
                override fun getSummary(
                    criteria: ReportFilterCriteria,
                    now: OffsetDateTime,
                ): CoordinationReportSummary = error("Not expected")

                override fun findRequests(
                    query: CoordinationReportPageQuery,
                    now: OffsetDateTime,
                ): PagedResult<CoordinationReportRequestItem> = error("Not expected")
            }

        val service = ReportService(stubRepo, FakeTimeProvider(fixedNow))

        listOf(agentActor, solicitanteActor).forEach { actor ->
            assertFailsWith<DomainException.ForbiddenException> {
                service.getSummary(actor = actor)
            }
            assertFailsWith<DomainException.ForbiddenException> {
                service.listRequests(actor = actor)
            }
        }
    }
}
