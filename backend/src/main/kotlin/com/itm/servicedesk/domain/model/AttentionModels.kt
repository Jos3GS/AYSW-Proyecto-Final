package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class AttentionRequestSummary(
    val id: UUID,
    val code: String,
    val subject: String,
    val category: RequestCategory,
    val status: RequestStatus,
    val priority: RequestPriority?,
    val requesterId: UUID,
    val requesterName: String,
    val assignedToId: UUID?,
    val assignedToName: String?,
    val firstAssignedAt: OffsetDateTime?,
    val dueAt: OffsetDateTime?,
    val isOverdue: Boolean,
    val createdAt: OffsetDateTime,
)

data class InternalHistoryEntry(
    val id: UUID,
    val action: HistoryAction,
    val previousState: RequestStatus?,
    val newState: RequestStatus,
    val actorId: UUID?,
    val actorName: String,
    val actorRole: HistoryActorRole,
    val comment: String?,
    val occurredAt: OffsetDateTime,
)

data class AttentionRequestDetail(
    val request: ServiceRequest,
    val requesterName: String,
    val assignedToName: String?,
    val isOverdue: Boolean,
    val history: List<InternalHistoryEntry>,
)

data class AttentionTransition(
    val requestId: UUID,
    val previousState: RequestStatus,
    val newState: RequestStatus,
    val action: HistoryAction,
    val actorId: UUID?,
    val actorRole: HistoryActorRole,
    val comment: String?,
    val occurredAt: OffsetDateTime,
    val assignedTo: UUID? = null,
    val updateAssignment: Boolean = false,
    val priority: RequestPriority? = null,
    val updatePriority: Boolean = false,
    val firstAssignedAt: OffsetDateTime? = null,
    val assignedAt: OffsetDateTime? = null,
    val dueAt: OffsetDateTime? = null,
    val updateDueAt: Boolean = false,
    val responseText: String? = null,
    val respondedAt: OffsetDateTime? = null,
    val respondedWithinSla: Boolean? = null,
    val closedAt: OffsetDateTime? = null,
)
