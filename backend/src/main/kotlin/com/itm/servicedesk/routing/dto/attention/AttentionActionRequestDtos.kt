package com.itm.servicedesk.routing.dto.attention

import kotlinx.serialization.Serializable

@Serializable
data class AssignRequestDto(
    val assigneeId: String,
    val priority: String,
)

@Serializable
data class ReassignRequestDto(
    val assigneeId: String,
    val reason: String,
    val priority: String? = null,
)

@Serializable
data class AddInterventionRequestDto(
    val comment: String,
)

@Serializable
data class EscalateRequestDto(
    val reason: String,
)

@Serializable
data class RespondRequestDto(
    val responseText: String,
)
