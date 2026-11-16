package com.itm.servicedesk.routing.dto.attention

import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.InternalHistoryEntry
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class InternalRequestHistoryItemDto(
    val id: String,
    val action: String,
    val previousState: String?,
    val newState: String,
    val actorId: String?,
    val actorName: String,
    val actorRole: String,
    val comment: String?,
    val occurredAt: String,
)

@Serializable
data class AttentionRequestDetailDto(
    val id: String,
    val code: String,
    val category: String,
    val subject: String,
    val description: String,
    val status: String,
    val previousRequestCode: String?,
    val requesterId: String,
    val requesterName: String,
    val assignedToId: String?,
    val assignedToName: String?,
    val priority: String?,
    val firstAssignedAt: String?,
    val assignedAt: String?,
    val dueAt: String?,
    val isOverdue: Boolean,
    val responseText: String?,
    val respondedAt: String?,
    val respondedWithinSla: Boolean?,
    val closedAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val history: List<InternalRequestHistoryItemDto>,
)

fun InternalHistoryEntry.toDto(zoneId: ZoneId): InternalRequestHistoryItemDto =
    InternalRequestHistoryItemDto(
        id = id.toString(),
        action = action.name,
        previousState = previousState?.name,
        newState = newState.name,
        actorId = actorId?.toString(),
        actorName = actorName,
        actorRole = actorRole.name,
        comment = comment,
        occurredAt = occurredAt.atZoneSameInstant(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )

fun AttentionRequestDetail.toDto(zoneId: ZoneId): AttentionRequestDetailDto =
    AttentionRequestDetailDto(
        id = request.id.toString(),
        code = request.code,
        category = request.category.name,
        subject = request.subject,
        description = request.description,
        status = request.status.name,
        previousRequestCode = request.previousRequestCode,
        requesterId = request.requesterId.toString(),
        requesterName = requesterName,
        assignedToId = request.assignedTo?.toString(),
        assignedToName = assignedToName,
        priority = request.priority?.name,
        firstAssignedAt =
            request.firstAssignedAt
                ?.atZoneSameInstant(zoneId)
                ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        assignedAt =
            request.assignedAt
                ?.atZoneSameInstant(zoneId)
                ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        dueAt =
            request.dueAt
                ?.atZoneSameInstant(zoneId)
                ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        isOverdue = isOverdue,
        responseText = request.responseText,
        respondedAt =
            request.respondedAt
                ?.atZoneSameInstant(zoneId)
                ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        respondedWithinSla = request.respondedWithinSla,
        closedAt =
            request.closedAt
                ?.atZoneSameInstant(zoneId)
                ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        createdAt = request.createdAt.atZoneSameInstant(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        updatedAt = request.updatedAt.atZoneSameInstant(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        history = history.map { it.toDto(zoneId) },
    )
