package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class ServiceRequestSummary(
    val id: UUID,
    val code: String,
    val subject: String,
    val category: RequestCategory,
    val status: RequestStatus,
    val previousRequestCode: String?,
    val createdAt: OffsetDateTime,
)
