package com.itm.servicedesk.routing.dto.auth

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val password: String,
)
