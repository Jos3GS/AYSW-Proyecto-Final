package com.itm.servicedesk.routing.dto.requests

import com.itm.servicedesk.domain.model.ApplicantVisibleHistoryEntry
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class RequestHistoryItemDto(
    val id: String,
    val action: String,
    val previousState: String?,
    val newState: String,
    val occurredAt: String,
    val comment: String?,
)

fun ApplicantVisibleHistoryEntry.toDto(zoneId: ZoneId): RequestHistoryItemDto =
    RequestHistoryItemDto(
        id = id.toString(),
        action = action.name,
        previousState = previousState?.name,
        newState = newState.name,
        occurredAt = occurredAt.atZoneSameInstant(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        comment = comment,
    )
