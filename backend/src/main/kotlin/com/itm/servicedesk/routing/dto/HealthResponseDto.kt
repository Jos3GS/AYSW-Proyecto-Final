package com.itm.servicedesk.routing.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponseDto(
    val status: String,
    val service: String,
    val database: String,
    val timestamp: String,
)
