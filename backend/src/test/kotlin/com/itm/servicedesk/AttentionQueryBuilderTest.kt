package com.itm.servicedesk

import com.itm.servicedesk.domain.builder.AttentionQueryBuilder
import com.itm.servicedesk.domain.builder.AttentionQueryMode
import com.itm.servicedesk.domain.builder.AttentionSortOrder
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.RequestStatus
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AttentionQueryBuilderTest {
    @Test
    fun `coordination pending builder defaults to page 1 size 20 CREATED_AT_ASC and accepts ABIERTA or ESCALADA`() {
        val defaultQuery = AttentionQueryBuilder().forCoordinationPending().build()
        assertEquals(AttentionQueryMode.COORDINATION_PENDING, defaultQuery.mode)
        assertEquals(1, defaultQuery.page)
        assertEquals(20, defaultQuery.size)
        assertEquals(0L, defaultQuery.offset)
        assertNull(defaultQuery.status)
        assertEquals(AttentionSortOrder.CREATED_AT_ASC, defaultQuery.sortOrder)

        val escaladaQuery =
            AttentionQueryBuilder()
                .forCoordinationPending()
                .page("2")
                .size("10")
                .status("ESCALADA")
                .build()
        assertEquals(2, escaladaQuery.page)
        assertEquals(10, escaladaQuery.size)
        assertEquals(10L, escaladaQuery.offset)
        assertEquals(RequestStatus.ESCALADA, escaladaQuery.status)

        val abiertaQuery =
            AttentionQueryBuilder()
                .forCoordinationPending()
                .status(RequestStatus.ABIERTA)
                .build()
        assertEquals(RequestStatus.ABIERTA, abiertaQuery.status)
    }

    @Test
    fun `coordination pending builder rejects invalid page size and non-pending statuses`() {
        val ex =
            assertFailsWith<DomainException.ValidationException> {
                AttentionQueryBuilder()
                    .forCoordinationPending()
                    .page("0")
                    .size("21")
                    .status("EN_REVISION")
                    .build()
            }
        assertEquals(400, ex.httpStatus)
        assertEquals(setOf("page", "size", "status"), ex.details.map { it.field }.toSet())

        val parseEx =
            assertFailsWith<DomainException.ValidationException> {
                AttentionQueryBuilder()
                    .forCoordinationPending()
                    .page("abc")
                    .size("xyz")
                    .status("UNKNOWN")
                    .build()
            }
        assertEquals(setOf("page", "size", "status"), parseEx.details.map { it.field }.toSet())
    }

    @Test
    fun `agent assigned builder validates agentId allowed statuses and sortOrder`() {
        val agentId = UUID.randomUUID()
        val query =
            AttentionQueryBuilder()
                .forAgent(agentId)
                .page(3)
                .size(5)
                .status("RESUELTA")
                .sortOrder(AttentionSortOrder.CREATED_AT_ASC)
                .build()

        assertEquals(AttentionQueryMode.AGENT_ASSIGNED, query.mode)
        assertEquals(agentId, query.assignedTo)
        assertEquals(3, query.page)
        assertEquals(5, query.size)
        assertEquals(10L, query.offset)
        assertEquals(RequestStatus.RESUELTA, query.status)
        assertEquals(AttentionSortOrder.CREATED_AT_ASC, query.sortOrder)

        listOf("ABIERTA", "CANCELADA", "INVALID").forEach { badStatus ->
            val ex =
                assertFailsWith<DomainException.ValidationException> {
                    AttentionQueryBuilder()
                        .forAgent(agentId)
                        .status(badStatus)
                        .build()
                }
            assertEquals(listOf("status"), ex.details.map { it.field })
        }
    }
}
