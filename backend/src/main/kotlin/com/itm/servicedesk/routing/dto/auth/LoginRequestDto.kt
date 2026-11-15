package com.itm.servicedesk.routing.dto.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)
