package com.itm.servicedesk.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorDto(
    val timestamp: String,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val details: List<FieldErrorDto> = emptyList(),
)

@Serializable
data class FieldErrorDto(
    val field: String,
    val message: String,
)
