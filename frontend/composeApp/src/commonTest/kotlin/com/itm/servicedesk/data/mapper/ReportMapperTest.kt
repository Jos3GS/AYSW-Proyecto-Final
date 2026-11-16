package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.core.network.DefaultApiJson
import com.itm.servicedesk.data.dto.reports.CoordinationReportSummaryDto
import com.itm.servicedesk.data.dto.reports.PagedCoordinationReportRequestsResponseDto
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.testing.ReportTestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportMapperTest {
    private val mapper = ReportMapper()

    @Test
    fun `deserializes strict JSON with ignoreUnknownKeys false and maps summary to domain`() {
        val dto = DefaultApiJson.decodeFromString<CoordinationReportSummaryDto>(ReportTestFixtures.summaryJson())
        val domain = mapper.toDomain(dto)

        assertEquals(ReportTestFixtures.sampleGeneratedAt, domain.generatedAt)
        assertEquals(10L, domain.totalRequests)
        assertEquals(2L, domain.byStatus[RequestStatus.ABIERTA])
        assertEquals(3L, domain.byStatus[RequestStatus.EN_REVISION])
        assertEquals(4L, domain.byCategory[RequestCategory.TRAMITE])
        assertEquals(3L, domain.byPriority[RequestPriority.ALTA])
        assertEquals(2L, domain.unassignedPriorityCount)
        assertEquals(1, domain.byAssignee.size)
        assertEquals("Carlos Andrés Mejía", domain.byAssignee[0].assigneeName)
        assertEquals(5L, domain.byAssignee[0].totalAssigned)
        assertEquals(1L, domain.byAssignee[0].overdueAssigned)
        assertEquals(2L, domain.unassignedCount)
        assertEquals(1L, domain.overdueCount)
        assertEquals(2L, domain.resolvedWithinSlaCount)
        assertEquals(1L, domain.resolvedOutsideSlaCount)
    }

    @Test
    fun `deserializes strict JSON and maps paged report requests including nullable fields`() {
        val dto =
            DefaultApiJson.decodeFromString<PagedCoordinationReportRequestsResponseDto>(
                ReportTestFixtures.pagedRequestsJson(page = 2, size = 20, totalItems = 25, totalPages = 2),
            )
        val page = mapper.toDomain(dto)

        assertEquals(2, page.page)
        assertEquals(20, page.size)
        assertEquals(25L, page.totalItems)
        assertEquals(2, page.totalPages)
        assertEquals(1, page.items.size)

        val item = page.items.first()
        assertEquals("SOL-2026-00019", item.code)
        assertEquals(RequestCategory.TRAMITE, item.category)
        assertEquals(RequestStatus.RESUELTA, item.status)
        assertEquals(RequestPriority.ALTA, item.priority)
        assertEquals("agent-1", item.assignedToId)
        assertEquals("Carlos Andrés Mejía", item.assignedToName)
        assertFalse(item.isOverdue)
        assertTrue(item.respondedWithinSla == true)
        assertNull(item.closedAt)
    }
}
