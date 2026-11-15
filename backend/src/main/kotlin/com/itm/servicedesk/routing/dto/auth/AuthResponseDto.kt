package com.itm.servicedesk.routing.dto.auth

import com.itm.servicedesk.domain.model.AuthSession
import com.itm.servicedesk.routing.dto.users.UserProfileDto
import com.itm.servicedesk.routing.dto.users.toDto
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    val expiresAt: String,
    val user: UserProfileDto,
)

fun AuthSession.toDto(zoneId: ZoneId): AuthResponseDto =
    AuthResponseDto(
        accessToken = token.accessToken,
        tokenType = token.tokenType,
        expiresInSeconds = token.expiresInSeconds,
        expiresAt =
            token.expiresAt
                .atZoneSameInstant(zoneId)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        user = user.toDto(zoneId),
    )
