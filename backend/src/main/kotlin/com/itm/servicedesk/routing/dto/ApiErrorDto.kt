package com.itm.servicedesk.routing.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorDetailDto(
    val field: String,
    val message: String,
)

@Serializable
data class ApiErrorDto(
    val timestamp: String,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val details: List<ErrorDetailDto> = emptyList(),
)
