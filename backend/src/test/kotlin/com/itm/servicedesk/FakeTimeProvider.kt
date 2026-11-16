package com.itm.servicedesk

import com.itm.servicedesk.domain.time.TimeProvider
import java.time.OffsetDateTime
import java.time.ZoneOffset

class FakeTimeProvider(
    initialTime: OffsetDateTime = OffsetDateTime.of(2026, 11, 15, 10, 0, 0, 0, ZoneOffset.of("-05:00")),
) : TimeProvider {
    private var currentTime: OffsetDateTime = initialTime

    override fun now(): OffsetDateTime = currentTime

    fun advanceHours(hours: Long) {
        currentTime = currentTime.plusHours(hours)
    }

    fun advanceMinutes(minutes: Long) {
        currentTime = currentTime.plusMinutes(minutes)
    }

    fun advanceSeconds(seconds: Long) {
        currentTime = currentTime.plusSeconds(seconds)
    }

    fun setNow(newTime: OffsetDateTime) {
        currentTime = newTime
    }
}
