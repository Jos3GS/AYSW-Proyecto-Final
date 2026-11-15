package com.itm.servicedesk.routing.dto.requests

import kotlinx.serialization.Serializable

@Serializable
data class CreateServiceRequestDto(
    val category: String,
    val subject: String,
    val description: String,
    val previousRequestCode: String? = null,
)
