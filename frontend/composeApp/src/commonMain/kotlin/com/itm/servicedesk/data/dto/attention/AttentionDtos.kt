package com.itm.servicedesk.data.dto.attention

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

@Serializable
data class AttentionRequestSummaryDto(
    val code: String,
    val subject: String,
    val category: String,
    val status: String,
    val priority: String? = null,
    val requesterName: String,
    val assignedAgentId: String? = null,
    val assignedAgentName: String? = null,
    val createdAt: String,
    val dueAt: String? = null,
    val isOverdue: Boolean,
)

@Serializable
data class PagedAttentionRequestsResponseDto(
    val items: List<AttentionRequestSummaryDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

@Serializable
data class InternalRequestHistoryItemDto(
    val id: String,
    val eventType: String,
    val actorName: String? = null,
    val actorRole: String,
    val previousStatus: String? = null,
    val newStatus: String? = null,
    val detail: String? = null,
    val createdAt: String,
)

@Serializable
data class AttentionRequestDetailDto(
    val code: String,
    val subject: String,
    val description: String,
    val category: String,
    val status: String,
    val priority: String? = null,
    val requesterId: String,
    val requesterName: String,
    val assignedAgentId: String? = null,
    val assignedAgentName: String? = null,
    val previousRequestCode: String? = null,
    val responseText: String? = null,
    val createdAt: String,
    val assignedAt: String? = null,
    val dueAt: String? = null,
    val respondedAt: String? = null,
    val closedAt: String? = null,
    val isOverdue: Boolean,
    val slaCompliant: Boolean? = null,
    val history: List<InternalRequestHistoryItemDto> = emptyList(),
)
