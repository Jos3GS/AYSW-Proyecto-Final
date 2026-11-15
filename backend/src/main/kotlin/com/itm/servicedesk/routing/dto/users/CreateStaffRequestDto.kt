package com.itm.servicedesk.routing.dto.users

import kotlinx.serialization.Serializable

@Serializable
data class CreateStaffRequestDto(
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val password: String,
    val role: String,
)
