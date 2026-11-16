package com.itm.servicedesk.domain.builder

import com.itm.servicedesk.domain.model.CoordinationReportPageQuery
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportFilterBuilderTest {
    private val bogotaOffset = ZoneOffset.of("-05:00")

    @Test
    fun `defaults produce empty criteria and page 1 size 20`() {
        val criteria = ReportFilterBuilder().buildCriteria()
        assertNull(criteria.createdFromInclusive)
        assertNull(criteria.createdToExclusive)
        assertNull(criteria.status)
        assertNull(criteria.category)
        assertNull(criteria.priority)
        assertNull(criteria.assignedToId)

        val query = ReportFilterBuilder().buildPageQuery()
        assertEquals(criteria, query.criteria)
        assertEquals(1, query.page)
        assertEquals(20, query.size)
        assertEquals(0L, query.offset)
    }

    @Test
    fun `converts createdFrom and createdTo into half-open America Bogota interval covering 23_30-05_00`() {
        val agentId = UUID.randomUUID()
        val query =
            ReportFilterBuilder()
                .createdFrom("2026-11-14")
                .createdTo("2026-11-14")
                .status("EN_REVISION")
                .category("REQUERIMIENTO_SERVICIO")
                .priority("ALTA")
                .assignedToId(agentId.toString())
                .page("2")
                .size("15")
                .buildPageQuery()

        assertEquals(
            OffsetDateTime.of(2026, 11, 14, 0, 0, 0, 0, bogotaOffset),
            query.criteria.createdFromInclusive,
        )
        assertEquals(
            OffsetDateTime.of(2026, 11, 15, 0, 0, 0, 0, bogotaOffset),
            query.criteria.createdToExclusive,
        )
        assertEquals(RequestStatus.EN_REVISION, query.criteria.status)
        assertEquals(RequestCategory.REQUERIMIENTO_SERVICIO, query.criteria.category)
        assertEquals(RequestPriority.ALTA, query.criteria.priority)
        assertEquals(agentId, query.criteria.assignedToId)
        assertEquals(2, query.page)
        assertEquals(15, query.size)
        assertEquals(15L, query.offset)

        // 2026-11-14T23:30:00-05:00 (2026-11-15T04:30:00Z) must fall inside [from, toExclusive)
        val lateNightBogota = OffsetDateTime.of(2026, 11, 15, 4, 30, 0, 0, ZoneOffset.UTC)
        assertFalse(lateNightBogota.isBefore(query.criteria.createdFromInclusive))
        assertTrue(lateNightBogota.isBefore(query.criteria.createdToExclusive))

        // 2026-11-15T00:00:00-05:00 is excluded
        val nextDayMidnightBogota = OffsetDateTime.of(2026, 11, 15, 0, 0, 0, 0, bogotaOffset)
        assertFalse(nextDayMidnightBogota.isBefore(query.criteria.createdToExclusive))
    }

    @Test
    fun `supports single bound date filters and typed overloads`() {
        val agentId = UUID.randomUUID()
        val onlyFrom =
            ReportFilterBuilder()
                .createdFrom(LocalDate.of(2026, 11, 1))
                .status(RequestStatus.ABIERTA)
                .category(RequestCategory.TRAMITE)
                .priority(RequestPriority.MEDIA)
                .assignedToId(agentId)
                .buildCriteria()

        assertEquals(OffsetDateTime.of(2026, 11, 1, 0, 0, 0, 0, bogotaOffset), onlyFrom.createdFromInclusive)
        assertNull(onlyFrom.createdToExclusive)

        val onlyTo = ReportFilterBuilder().createdTo(" 2026-11-10 ").buildCriteria()
        assertNull(onlyTo.createdFromInclusive)
        assertEquals(OffsetDateTime.of(2026, 11, 11, 0, 0, 0, 0, bogotaOffset), onlyTo.createdToExclusive)
    }

    @Test
    fun `rejects inverted date range and accumulates all invalid filter and pagination fields`() {
        val invertedEx =
            assertFailsWith<DomainException.ValidationException> {
                ReportFilterBuilder()
                    .createdFrom("2026-11-20")
                    .createdTo("2026-11-10")
                    .assignedToId("not-a-uuid")
                    .size(25)
                    .buildPageQuery()
            }
        assertEquals(400, invertedEx.httpStatus)
        assertEquals(
            setOf("createdTo", "assignedToId", "size"),
            invertedEx.details.map { it.field }.toSet(),
        )

        val multiEx =
            assertFailsWith<DomainException.ValidationException> {
                ReportFilterBuilder()
                    .createdFrom("14/11/2026")
                    .createdTo("2026-02-30")
                    .status("INEXISTENTE")
                    .category("OTRA")
                    .priority("URGENTE")
                    .assignedToId("1-2-3-4-5")
                    .page("0")
                    .size("abc")
                    .buildPageQuery()
            }
        assertEquals(
            setOf("createdFrom", "createdTo", "status", "category", "priority", "assignedToId", "page", "size"),
            multiEx.details.map { it.field }.toSet(),
        )

        val pageParseEx =
            assertFailsWith<DomainException.ValidationException> {
                ReportFilterBuilder()
                    .page("xyz")
                    .size(0)
                    .buildPageQuery()
            }
        assertEquals(setOf("page", "size"), pageParseEx.details.map { it.field }.toSet())
        assertEquals(CoordinationReportPageQuery.MAX_SIZE, 20)
    }
}
