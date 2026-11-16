package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.core.network.DefaultApiJson
import com.itm.servicedesk.data.dto.requests.PagedServiceRequestsResponseDto
import com.itm.servicedesk.data.dto.requests.ServiceRequestDetailDto
import com.itm.servicedesk.domain.model.CancelServiceRequestCommand
import com.itm.servicedesk.domain.model.CreateServiceRequestCommand
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestHistoryAction
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.testing.RequestTestFixtures
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RequestMapperTest {
    private val mapper = RequestMapper()

    @Test
    fun `CreateServiceRequestDto never serializes server managed fields`() {
        val dto =
            mapper.toCreateDto(
                CreateServiceRequestCommand(
                    category = RequestCategory.ATENCION_ADMINISTRATIVA,
                    subject = "  Asunto de prueba  ",
                    description = "  Descripción suficientemente larga para cumplir Tabla 2.  ",
                    previousRequestCode = "  sol-2026-00012 ",
                ),
            )
        assertEquals("SOL-2026-00012", dto.previousRequestCode)

        val encoded = DefaultApiJson.encodeToString(dto)
        val jsonKeys = DefaultApiJson.parseToJsonElement(encoded).jsonObject.keys
        assertEquals(setOf("category", "subject", "description", "previousRequestCode"), jsonKeys)
        assertFalse(jsonKeys.contains("code"))
        assertFalse(jsonKeys.contains("status"))
        assertFalse(jsonKeys.contains("requesterId"))
        assertFalse(jsonKeys.contains("createdAt"))

        val withoutPrevious =
            mapper.toCreateDto(
                CreateServiceRequestCommand(
                    category = RequestCategory.TRAMITE,
                    subject = "Asunto",
                    description = "Descripción suficientemente larga para prueba.",
                    previousRequestCode = "   ",
                ),
            )
        assertNull(withoutPrevious.previousRequestCode)
        val encodedWithoutPrev = DefaultApiJson.encodeToString(withoutPrevious)
        val noPrevJson: JsonObject = DefaultApiJson.parseToJsonElement(encodedWithoutPrev).jsonObject
        assertFalse(noPrevJson.containsKey("previousRequestCode"))
    }

    @Test
    fun `maps cancel dto, paged response dto, and detail dto with history`() {
        val cancelCmd = CancelServiceRequestCommand(code = "SOL-2026-00019", reason = "  Ya resuelto  ")
        val cancelDto = mapper.toCancelDto(cancelCmd)
        assertEquals("Ya resuelto", cancelDto.reason)
        val blankCancelCmd = CancelServiceRequestCommand(code = "SOL-2026-00019", reason = "   ")
        assertNull(mapper.toCancelDto(blankCancelCmd).reason)

        val pageDto =
            DefaultApiJson.decodeFromString<PagedServiceRequestsResponseDto>(
                RequestTestFixtures.pagedRequestsJson(),
            )
        val page = mapper.toDomain(pageDto)
        assertEquals(1, page.page)
        assertEquals(20, page.size)
        assertEquals(RequestStatus.ABIERTA, page.items.first().status)
        assertEquals(RequestCategory.TRAMITE, page.items.first().category)

        val detailDto =
            DefaultApiJson.decodeFromString<ServiceRequestDetailDto>(
                RequestTestFixtures.requestDetailJson(
                    status = "RESUELTA",
                    responseText = "Certificado emitido.",
                    respondedAt = "2026-11-15T10:15:00-05:00",
                    closedAt = "2026-11-15T16:00:00-05:00",
                ),
            )
        val detail = mapper.toDomain(detailDto)
        assertEquals(RequestStatus.RESUELTA, detail.status)
        assertEquals("Certificado emitido.", detail.responseText)
        assertEquals(RequestHistoryAction.CREACION, detail.history.first().action)
        assertNull(detail.history.first().previousState)
    }
}
