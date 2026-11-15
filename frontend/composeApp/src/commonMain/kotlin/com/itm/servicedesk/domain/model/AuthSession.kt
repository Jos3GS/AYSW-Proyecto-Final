package com.itm.servicedesk.domain.model

import kotlin.time.Instant

data class AuthSession(
    val accessToken: String,
    val expiresInSeconds: Long,
    val expiresAt: Instant,
    val user: UserProfile,
) {
    override fun toString(): String =
        "AuthSession(accessToken=***, expiresInSeconds=$expiresInSeconds, expiresAt=$expiresAt, user=$user)"
}
