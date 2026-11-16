package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime

enum class RequestPriority(
    val slaHours: Long,
) {
    ALTA(24L),
    MEDIA(72L),
    BAJA(120L),
    ;

    fun computeDueAt(firstAssignedAt: OffsetDateTime): OffsetDateTime = firstAssignedAt.plusHours(slaHours)
}
