package com.itm.servicedesk.domain.model

import kotlin.time.Instant

data class ServiceRequestSummary(
    val id: String,
    val code: String,
    val subject: String,
    val category: RequestCategory,
    val status: RequestStatus,
    val previousRequestCode: String?,
    val createdAt: Instant,
)

data class ServiceRequestHistoryEntry(
    val id: String,
    val action: RequestHistoryAction,
    val previousState: RequestStatus?,
    val newState: RequestStatus,
    val occurredAt: Instant,
    val comment: String?,
)

data class ServiceRequestDetail(
    val id: String,
    val code: String,
    val category: RequestCategory,
    val subject: String,
    val description: String,
    val status: RequestStatus,
    val previousRequestCode: String?,
    val responseText: String?,
    val respondedAt: Instant?,
    val closedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val history: List<ServiceRequestHistoryEntry>,
)

data class ServiceRequestsPage(
    val items: List<ServiceRequestSummary>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)
