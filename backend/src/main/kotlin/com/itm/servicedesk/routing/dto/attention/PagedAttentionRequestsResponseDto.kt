package com.itm.servicedesk.routing.dto.attention

import com.itm.servicedesk.domain.model.AttentionRequestSummary
import com.itm.servicedesk.domain.model.PagedResult
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class AttentionRequestSummaryDto(
    val id: String,
    val code: String,
    val subject: String,
    val category: String,
    val status: String,
    val priority: String?,
    val requesterId: String,
    val requesterName: String,
    val assignedToId: String?,
    val assignedToName: String?,
    val firstAssignedAt: String?,
    val dueAt: String?,
    val isOverdue: Boolean,
    val createdAt: String,
)

@Serializable
data class PagedAttentionRequestsResponseDto(
    val items: List<AttentionRequestSummaryDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

fun AttentionRequestSummary.toDto(zoneId: ZoneId): AttentionRequestSummaryDto =
    AttentionRequestSummaryDto(
        id = id.toString(),
        code = code,
        subject = subject,
        category = category.name,
        status = status.name,
        priority = priority?.name,
        requesterId = requesterId.toString(),
        requesterName = requesterName,
        assignedToId = assignedToId?.toString(),
        assignedToName = assignedToName,
        firstAssignedAt =
            firstAssignedAt
                ?.atZoneSameInstant(zoneId)
                ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        dueAt =
            dueAt
                ?.atZoneSameInstant(zoneId)
                ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        isOverdue = isOverdue,
        createdAt = createdAt.atZoneSameInstant(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )

fun PagedResult<AttentionRequestSummary>.toDto(zoneId: ZoneId): PagedAttentionRequestsResponseDto =
    PagedAttentionRequestsResponseDto(
        items = items.map { it.toDto(zoneId) },
        page = page,
        size = size,
        totalItems = totalItems,
        totalPages = totalPages,
    )
