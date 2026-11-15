package com.itm.servicedesk.routing.dto.requests

import com.itm.servicedesk.domain.model.ServiceRequestDetail
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class ServiceRequestDetailDto(
    val id: String,
    val code: String,
    val category: String,
    val subject: String,
    val description: String,
    val status: String,
    val previousRequestCode: String?,
    val responseText: String?,
    val respondedAt: String?,
    val closedAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val history: List<RequestHistoryItemDto>,
)

fun ServiceRequestDetail.toDto(zoneId: ZoneId): ServiceRequestDetailDto =
    ServiceRequestDetailDto(
        id = request.id.toString(),
        code = request.code,
        category = request.category.name,
        subject = request.subject,
        description = request.description,
        status = request.status.name,
        previousRequestCode = request.previousRequestCode,
        responseText = request.responseText,
        respondedAt =
            request.respondedAt
                ?.atZoneSameInstant(zoneId)
                ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        closedAt =
            request.closedAt
                ?.atZoneSameInstant(zoneId)
                ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        createdAt = request.createdAt.atZoneSameInstant(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        updatedAt = request.updatedAt.atZoneSameInstant(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        history = history.map { it.toDto(zoneId) },
    )
