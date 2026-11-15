package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.data.dto.HealthResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class HealthMapperTest {
    private val mapper = HealthMapper()

    @Test
    fun `maps UP status and database to domain ServiceHealth`() {
        val dto =
            HealthResponseDto(
                status = "UP",
                service = "itm-service-desk-backend",
                database = "UP",
                timestamp = "2026-11-14T11:24:00-05:00",
            )

        val result = mapper.toDomain(dto)

        assertTrue(result.isServiceUp)
        assertTrue(result.isDatabaseUp)
        assertEquals("itm-service-desk-backend", result.serviceName)
        assertEquals(Instant.parse("2026-11-14T11:24:00-05:00"), result.checkedAt)
    }

    @Test
    fun `maps DOWN database as false while preserving timestamp`() {
        val dto =
            HealthResponseDto(
                status = "DOWN",
                service = "itm-service-desk-backend",
                database = "DOWN",
                timestamp = "2026-11-14T11:24:00-05:00",
            )

        val result = mapper.toDomain(dto)

        assertFalse(result.isServiceUp)
        assertFalse(result.isDatabaseUp)
    }
}
