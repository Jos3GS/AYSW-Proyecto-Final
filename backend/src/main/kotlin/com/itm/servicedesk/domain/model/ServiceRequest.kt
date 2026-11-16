package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class ServiceRequest(
    val id: UUID,
    val code: String,
    val requesterId: UUID,
    val category: RequestCategory,
    val subject: String,
    val description: String,
    val status: RequestStatus,
    val previousRequestId: UUID? = null,
    val previousRequestCode: String? = null,
    val assignedTo: UUID? = null,
    val priority: RequestPriority? = null,
    val firstAssignedAt: OffsetDateTime? = null,
    val assignedAt: OffsetDateTime? = null,
    val dueAt: OffsetDateTime? = null,
    val responseText: String? = null,
    val respondedAt: OffsetDateTime? = null,
    val respondedWithinSla: Boolean? = null,
    val closedAt: OffsetDateTime? = null,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    fun isOverdue(now: OffsetDateTime): Boolean =
        (status == RequestStatus.EN_REVISION || status == RequestStatus.ESCALADA) &&
            dueAt != null &&
            now.isAfter(dueAt)
}
