package com.itm.servicedesk.routing.dto.requests

import kotlinx.serialization.Serializable

@Serializable
data class CancelServiceRequestDto(
    val reason: String? = null,
)
