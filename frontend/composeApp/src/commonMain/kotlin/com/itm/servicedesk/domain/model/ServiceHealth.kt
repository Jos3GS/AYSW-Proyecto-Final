package com.itm.servicedesk.domain.model

import kotlin.time.Instant

data class ServiceHealth(
    val isServiceUp: Boolean,
    val isDatabaseUp: Boolean,
    val serviceName: String,
    val checkedAt: Instant,
)
