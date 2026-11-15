package com.itm.servicedesk.routing.dto.users

import com.itm.servicedesk.domain.model.StaffSummary
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class StaffSummaryDto(
    val id: String,
    val fullName: String,
    val email: String,
    val role: String,
    val createdAt: String,
)

fun StaffSummary.toDto(zoneId: ZoneId): StaffSummaryDto =
    StaffSummaryDto(
        id = id.toString(),
        fullName = fullName,
        email = email,
        role = role.name,
        createdAt =
            createdAt
                .atZoneSameInstant(zoneId)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
