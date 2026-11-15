package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime

enum class HealthStatus {
    UP,
    DOWN,
}

data class SystemHealth(
    val status: HealthStatus,
    val serviceName: String,
    val databaseStatus: HealthStatus,
    val checkedAt: OffsetDateTime,
)
