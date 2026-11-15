package com.itm.servicedesk.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponseDto(
    val status: String,
    val service: String,
    val database: String,
    val timestamp: String,
)
