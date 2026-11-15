package com.itm.servicedesk

import com.itm.servicedesk.domain.builder.StaffPageQueryBuilder
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.UserRole
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StaffPageQueryBuilderTest {
    @Test
    fun `build uses default page 1 size 20 and null roleFilter`() {
        val query = StaffPageQueryBuilder().build()

        assertEquals(1, query.page)
        assertEquals(20, query.size)
        assertEquals(0L, query.offset)
        assertNull(query.roleFilter)
    }

    @Test
    fun `build parses valid raw query params and calculates offset`() {
        val query =
            StaffPageQueryBuilder()
                .page("3")
                .size("15")
                .role("PERSONAL_ATENCION")
                .build()

        assertEquals(3, query.page)
        assertEquals(15, query.size)
        assertEquals(30L, query.offset)
        assertEquals(UserRole.PERSONAL_ATENCION, query.roleFilter)
    }

    @Test
    fun `build rejects page less than 1 size outside 1 to 20 and role SOLICITANTE`() {
        val ex =
            assertFailsWith<DomainException.ValidationException> {
                StaffPageQueryBuilder()
                    .page(0)
                    .size(21)
                    .role(UserRole.SOLICITANTE)
                    .build()
            }

        assertEquals(400, ex.httpStatus)
        assertEquals(setOf("page", "size", "role"), ex.details.map { it.field }.toSet())
    }

    @Test
    fun `build rejects non-numeric page and size and unknown role`() {
        val ex =
            assertFailsWith<DomainException.ValidationException> {
                StaffPageQueryBuilder()
                    .page("abc")
                    .size("xyz")
                    .role("ADMIN")
                    .build()
            }

        assertEquals(setOf("page", "size", "role"), ex.details.map { it.field }.toSet())
    }
}
