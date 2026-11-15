package com.itm.servicedesk.core.time

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

object DateFormatter {
    private val bogotaTimeZone: TimeZone by lazy {
        ensureTimeZoneDatabaseInitialized()
        TimeZone.of("America/Bogota")
    }

    fun formatBogotaDateTime(instant: Instant): String {
        val localDateTime = instant.toLocalDateTime(bogotaTimeZone)
        val day = localDateTime.day.toString().padStart(2, '0')
        val month =
            localDateTime.month.number
                .toString()
                .padStart(2, '0')
        val year = localDateTime.year.toString().padStart(4, '0')
        val hour = localDateTime.hour.toString().padStart(2, '0')
        val minute = localDateTime.minute.toString().padStart(2, '0')
        return "$day/$month/$year $hour:$minute"
    }

    fun formatIsoToBogota(isoTimestamp: String): String {
        val instant = Instant.parse(isoTimestamp)
        return formatBogotaDateTime(instant)
    }
}
