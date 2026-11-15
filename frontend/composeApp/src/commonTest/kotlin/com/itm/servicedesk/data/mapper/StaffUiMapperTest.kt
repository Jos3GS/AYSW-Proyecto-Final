package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.core.network.DefaultApiJson
import com.itm.servicedesk.data.dto.PagedStaffResponseDto
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.testing.AuthTestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals

class StaffUiMapperTest {
    private val staffMapper = StaffMapper()
    private val staffUiMapper = StaffUiMapper()

    @Test
    fun `maps Spanish role labels and formats createdAt in America Bogota`() {
        val dto = DefaultApiJson.decodeFromString<PagedStaffResponseDto>(AuthTestFixtures.pagedStaffJson())
        val page = staffMapper.toDomain(dto)

        assertEquals(1, page.page)
        assertEquals(20, page.size)
        assertEquals(1, page.items.size)

        val uiModel = staffUiMapper.toUiModel(page.items.first())
        assertEquals("Personal de atención", uiModel.roleLabel)
        assertEquals("14/11/2026 17:15", uiModel.formattedCreatedAt)
        assertEquals("Coordinador", staffUiMapper.roleLabel(UserRole.COORDINADOR))
        assertEquals("Solicitante", staffUiMapper.roleLabel(UserRole.SOLICITANTE))
    }
}
