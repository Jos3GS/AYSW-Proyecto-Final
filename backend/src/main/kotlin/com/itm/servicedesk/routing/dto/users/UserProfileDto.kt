package com.itm.servicedesk.routing.dto.users

import com.itm.servicedesk.domain.model.UserProfile
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class UserProfileDto(
    val id: String,
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val role: String,
    val createdAt: String,
)

fun UserProfile.toDto(zoneId: ZoneId): UserProfileDto =
    UserProfileDto(
        id = id.toString(),
        fullName = fullName,
        documentNumber = documentNumber,
        email = email,
        phone = phone,
        role = role.name,
        createdAt =
            createdAt
                .atZoneSameInstant(zoneId)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
