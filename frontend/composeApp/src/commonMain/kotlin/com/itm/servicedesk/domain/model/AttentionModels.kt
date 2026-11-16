package com.itm.servicedesk.domain.model

import kotlin.time.Instant

enum class RequestPriority {
    ALTA,
    MEDIA,
    BAJA,
}

enum class HistoryActorRole {
    SOLICITANTE,
    PERSONAL_ATENCION,
    COORDINADOR,
    SISTEMA,
}

data class AttentionRequestSummary(
    val code: String,
    val subject: String,
    val category: RequestCategory,
    val status: RequestStatus,
    val priority: RequestPriority?,
    val requesterName: String,
    val assignedAgentId: String?,
    val assignedAgentName: String?,
    val createdAt: Instant,
    val dueAt: Instant?,
    val isOverdue: Boolean,
)

data class AttentionRequestsPage(
    val items: List<AttentionRequestSummary>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

data class InternalHistoryEntry(
    val id: String,
    val eventType: String,
    val actorName: String?,
    val actorRole: HistoryActorRole,
    val previousStatus: RequestStatus?,
    val newStatus: RequestStatus?,
    val detail: String?,
    val createdAt: Instant,
)

data class AttentionRequestDetail(
    val code: String,
    val subject: String,
    val description: String,
    val category: RequestCategory,
    val status: RequestStatus,
    val priority: RequestPriority?,
    val requesterId: String,
    val requesterName: String,
    val assignedAgentId: String?,
    val assignedAgentName: String?,
    val previousRequestCode: String?,
    val responseText: String?,
    val createdAt: Instant,
    val assignedAt: Instant?,
    val dueAt: Instant?,
    val respondedAt: Instant?,
    val closedAt: Instant?,
    val isOverdue: Boolean,
    val slaCompliant: Boolean?,
    val history: List<InternalHistoryEntry>,
)

data class AssignRequestCommand(
    val assigneeId: String,
    val priority: RequestPriority,
)

data class ReassignRequestCommand(
    val assigneeId: String,
    val reason: String,
    val priority: RequestPriority? = null,
)

data class AddInterventionCommand(
    val comment: String,
)

data class EscalateRequestCommand(
    val reason: String,
)

data class RespondRequestCommand(
    val responseText: String,
)
