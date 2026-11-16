package com.itm.servicedesk.domain.time

import java.time.OffsetDateTime

interface TimeProvider {
    fun now(): OffsetDateTime
}
