package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.testing.ReportTestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportUiMapperTest {
    private val uiMapper = ReportUiMapper()

    @Test
    fun `maps summary into 6 statuses, 4 categories, 4 priorities, and assignees plus unassigned`() {
        val summaryUi = uiMapper.toSummaryUi(ReportTestFixtures.summary())

        assertEquals("15/11/2026 10:30", summaryUi.generatedAtFormatted)
        assertEquals(10L, summaryUi.totalRequests)
        assertEquals(1L, summaryUi.overdueCount)
        assertEquals(2L, summaryUi.resolvedWithinSlaCount)
        assertEquals(1L, summaryUi.resolvedOutsideSlaCount)

        assertEquals(6, summaryUi.statusRows.size)
        assertEquals("Abierta", summaryUi.statusRows[0].label)
        assertEquals(2L, summaryUi.statusRows[0].count)
        assertEquals(0.2f, summaryUi.statusRows[0].ratio)

        assertEquals(4, summaryUi.categoryRows.size)
        assertEquals("Trámite", summaryUi.categoryRows[0].label)
        assertEquals(0.4f, summaryUi.categoryRows[0].ratio)

        assertEquals(4, summaryUi.priorityRows.size)
        assertEquals("Alta (24 h)", summaryUi.priorityRows[0].label)
        assertEquals("Sin prioridad", summaryUi.priorityRows[3].label)
        assertEquals(2L, summaryUi.priorityRows[3].count)
        assertEquals(0.2f, summaryUi.priorityRows[3].ratio)

        assertEquals(3, summaryUi.assigneeRows.size)
        assertEquals("Carlos Andrés Mejía", summaryUi.assigneeRows[0].assigneeName)
        assertEquals(0.5f, summaryUi.assigneeRows[0].ratio)
        assertEquals("Sin responsable", summaryUi.assigneeRows[2].assigneeName)
        assertEquals(2L, summaryUi.assigneeRows[2].totalAssigned)
        assertEquals(0.2f, summaryUi.assigneeRows[2].ratio)
    }

    @Test
    fun `maps empty summary keeping full breakdown structure with zero counts and zero ratios`() {
        val emptyUi = uiMapper.toSummaryUi(ReportTestFixtures.emptySummary())

        assertEquals(0L, emptyUi.totalRequests)
        assertEquals(6, emptyUi.statusRows.size)
        assertEquals(4, emptyUi.categoryRows.size)
        assertEquals(4, emptyUi.priorityRows.size)
        assertEquals(1, emptyUi.assigneeRows.size)
        assertEquals("Sin responsable", emptyUi.assigneeRows[0].assigneeName)
        assertTrue(emptyUi.statusRows.all { it.count == 0L && it.ratio == 0f })
        assertTrue(emptyUi.categoryRows.all { it.count == 0L && it.ratio == 0f })
        assertTrue(emptyUi.priorityRows.all { it.count == 0L && it.ratio == 0f })
        assertEquals(0f, emptyUi.assigneeRows[0].ratio)
    }

    @Test
    fun `maps request items and pages with Spanish labels, Bogota timestamps, and SLA badges`() {
        val withinSla =
            ReportTestFixtures.requestItem(
                code = "SOL-2026-00019",
                status = RequestStatus.RESUELTA,
                priority = RequestPriority.ALTA,
                respondedAt = ReportTestFixtures.sampleRespondedAt,
                respondedWithinSla = true,
            )
        val unassignedOutsideSla =
            ReportTestFixtures.requestItem(
                code = "SOL-2026-00020",
                category = RequestCategory.REQUERIMIENTO_SERVICIO,
                status = RequestStatus.CERRADA,
                priority = null,
                assignedToId = null,
                assignedToName = null,
                dueAt = null,
                isOverdue = true,
                respondedAt = ReportTestFixtures.sampleRespondedAt,
                respondedWithinSla = false,
            )
        val openPending =
            ReportTestFixtures.requestItem(
                code = "SOL-2026-00021",
                status = RequestStatus.ABIERTA,
                priority = RequestPriority.BAJA,
                respondedWithinSla = null,
            )

        val pageUi =
            uiMapper.toRequestsPageUi(
                ReportTestFixtures.requestsPage(
                    items = listOf(withinSla, unassignedOutsideSla, openPending),
                    page = 1,
                    size = 20,
                    totalItems = 3,
                    totalPages = 1,
                ),
            )

        assertEquals(3, pageUi.items.size)
        assertEquals("Dentro del plazo", pageUi.items[0].slaResultLabel)
        assertEquals("14/11/2026 18:30", pageUi.items[0].createdAtFormatted)
        assertEquals("Alta (24 h)", pageUi.items[0].priorityLabel)

        assertEquals("Sin asignar", pageUi.items[1].priorityLabel)
        assertEquals("Sin asignar", pageUi.items[1].assignedToName)
        assertEquals("Fuera del plazo", pageUi.items[1].slaResultLabel)
        assertTrue(pageUi.items[1].isOverdue)

        assertEquals("Baja (120 h)", pageUi.items[2].priorityLabel)
        assertNull(pageUi.items[2].slaResultLabel)
    }
}
