package com.itm.servicedesk

import com.itm.servicedesk.domain.builder.MyRequestsQueryBuilder
import com.itm.servicedesk.domain.builder.ServiceRequestBuilder
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestStatus
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ServiceRequestBuilderTest {
    @Test
    fun `ServiceRequestBuilder creates immutable ABIERTA request in America Bogota and links previousRequest`() {
        val requesterId = UUID.randomUUID()
        val prevId = UUID.randomUUID()
        val utcNow = OffsetDateTime.of(2026, 11, 15, 19, 30, 0, 0, ZoneOffset.UTC)

        val previous =
            ServiceRequestBuilder()
                .id(prevId)
                .code("SOL-2026-00001")
                .requesterId(requesterId)
                .category(RequestCategory.TRAMITE)
                .subject("Solicitud previa")
                .description("Descripción de la solicitud previa resuelta.")
                .timezone("America/Bogota")
                .createdAt(utcNow.minusDays(1))
                .build()

        val created =
            ServiceRequestBuilder()
                .code("SOL-2026-00002")
                .requesterId(requesterId)
                .category(RequestCategory.ORIENTACION)
                .subject("  Nueva solicitud vinculada ")
                .description("  Descripción detallada de la nueva solicitud vinculada.  ")
                .previousRequest(previous)
                .createdAt(utcNow)
                .build()

        assertEquals("SOL-2026-00002", created.code)
        assertEquals(RequestStatus.ABIERTA, created.status)
        assertEquals("Nueva solicitud vinculada", created.subject)
        assertEquals("Descripción detallada de la nueva solicitud vinculada.", created.description)
        assertEquals(prevId, created.previousRequestId)
        assertEquals("SOL-2026-00001", created.previousRequestCode)
        assertEquals(ZoneOffset.of("-05:00"), created.createdAt.offset)
        assertEquals(created.createdAt, created.updatedAt)

        val manualPrev =
            ServiceRequestBuilder()
                .code("SOL-2026-00003")
                .requesterId(requesterId)
                .category(RequestCategory.REQUERIMIENTO_SERVICIO)
                .subject("Otra solicitud")
                .description("Descripción suficientemente larga para construir la entidad.")
                .previousRequest(prevId, "SOL-2026-00001")
                .build()
        assertEquals(prevId, manualPrev.previousRequestId)
        assertEquals("SOL-2026-00001", manualPrev.previousRequestCode)
        assertEquals(ZoneOffset.of("-05:00"), manualPrev.createdAt.offset)

        val withoutPrev =
            ServiceRequestBuilder()
                .code("SOL-2026-00004")
                .requesterId(requesterId)
                .category(RequestCategory.ATENCION_ADMINISTRATIVA)
                .subject("Sin previa")
                .description("Descripción suficientemente larga sin solicitud previa.")
                .previousRequest(null)
                .build()
        assertNull(withoutPrev.previousRequestId)
        assertNull(withoutPrev.previousRequestCode)
    }

    @Test
    fun `ServiceRequestBuilder validates mandatory and consistent fields`() {
        val requesterId = UUID.randomUUID()
        assertFailsWith<IllegalStateException> {
            ServiceRequestBuilder().requesterId(requesterId).build()
        }
        assertFailsWith<IllegalStateException> {
            ServiceRequestBuilder().code("INVALID").requesterId(requesterId).build()
        }
        assertFailsWith<IllegalStateException> {
            ServiceRequestBuilder()
                .code("SOL-2026-00001")
                .requesterId(requesterId)
                .category(RequestCategory.TRAMITE)
                .subject("Asunto válido")
                .description("Descripción válida de más de veinte caracteres.")
                .previousRequest(UUID.randomUUID(), null)
                .build()
        }
    }

    @Test
    fun `MyRequestsQueryBuilder uses defaults parses valid parameters and enforces page and size 1 to 20`() {
        val requesterId = UUID.randomUUID()
        val defaultQuery = MyRequestsQueryBuilder().requesterId(requesterId).build()
        assertEquals(requesterId, defaultQuery.requesterId)
        assertEquals(1, defaultQuery.page)
        assertEquals(20, defaultQuery.size)
        assertEquals(0L, defaultQuery.offset)
        assertNull(defaultQuery.status)

        val customQuery =
            MyRequestsQueryBuilder()
                .requesterId(requesterId)
                .page("2")
                .size("15")
                .status("EN_REVISION")
                .build()
        assertEquals(2, customQuery.page)
        assertEquals(15, customQuery.size)
        assertEquals(15L, customQuery.offset)
        assertEquals(RequestStatus.EN_REVISION, customQuery.status)

        val clearedQuery =
            MyRequestsQueryBuilder()
                .requesterId(requesterId)
                .page(" ")
                .size("")
                .status(RequestStatus.ABIERTA)
                .status("  ")
                .build()
        assertEquals(1, clearedQuery.page)
        assertEquals(20, clearedQuery.size)
        assertNull(clearedQuery.status)

        val exInt =
            assertFailsWith<DomainException.ValidationException> {
                MyRequestsQueryBuilder()
                    .requesterId(requesterId)
                    .page(0)
                    .size(21)
                    .status("INVALIDA")
                    .build()
            }
        assertEquals(setOf("page", "size", "status"), exInt.details.map { it.field }.toSet())

        val exRaw =
            assertFailsWith<DomainException.ValidationException> {
                MyRequestsQueryBuilder()
                    .requesterId(requesterId)
                    .page("uno")
                    .size("veinte")
                    .build()
            }
        assertEquals(setOf("page", "size"), exRaw.details.map { it.field }.toSet())
    }
}
