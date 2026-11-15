package com.itm.servicedesk.domain.model

data class AuthSession(
    val token: IssuedToken,
    val user: UserProfile,
)
