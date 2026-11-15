package com.itm.servicedesk.routing.dto.requests

import com.itm.servicedesk.domain.model.ServiceRequestSummary
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class ServiceRequestSummaryDto(
    val id: String,
    val code: String,
    val subject: String,
    val category: String,
    val status: String,
    val previousRequestCode: String?,
    val createdAt: String,
)

fun ServiceRequestSummary.toDto(zoneId: ZoneId): ServiceRequestSummaryDto =
    ServiceRequestSummaryDto(
        id = id.toString(),
        code = code,
        subject = subject,
        category = category.name,
        status = status.name,
        previousRequestCode = previousRequestCode,
        createdAt = createdAt.atZoneSameInstant(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
