package com.itm.servicedesk.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class DateFormatterTest {
    @Test
    fun `formats bogota offset timestamp to dd mm yyyy hh mm`() {
        val instant = Instant.parse("2026-11-13T20:03:00-05:00")

        val formatted = DateFormatter.formatBogotaDateTime(instant)

        assertEquals("13/11/2026 20:03", formatted)
    }

    @Test
    fun `converts utc instant to america bogota timezone with zero padding`() {
        val utcInstant = Instant.parse("2026-01-05T06:09:00Z")

        val formatted = DateFormatter.formatBogotaDateTime(utcInstant)

        assertEquals("05/01/2026 01:09", formatted)
    }

    @Test
    fun `handles day boundary crossing when converting utc to bogota`() {
        val utcInstant = Instant.parse("2026-11-15T03:15:00Z")

        val formatted = DateFormatter.formatIsoToBogota("2026-11-15T03:15:00Z")

        assertEquals("14/11/2026 22:15", formatted)
        assertEquals(DateFormatter.formatBogotaDateTime(utcInstant), formatted)
    }

    @Test
    fun `throws IllegalArgumentException for invalid iso timestamp`() {
        assertFailsWith<IllegalArgumentException> {
            DateFormatter.formatIsoToBogota("not-a-timestamp")
        }
    }
}
