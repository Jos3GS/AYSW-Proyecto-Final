package com.itm.servicedesk.domain.time

import java.time.OffsetDateTime
import java.time.ZoneId

class BogotaTimeProvider(
    private val zoneId: ZoneId = ZoneId.of(DEFAULT_TIMEZONE),
) : TimeProvider {
    constructor(timezone: String) : this(ZoneId.of(timezone))

    override fun now(): OffsetDateTime = OffsetDateTime.now(zoneId)

    companion object {
        const val DEFAULT_TIMEZONE = "America/Bogota"
    }
}
