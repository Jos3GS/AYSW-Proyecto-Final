package com.itm.servicedesk.data.dto.requests

import kotlinx.serialization.Serializable

@Serializable
data class CreateServiceRequestDto(
    val category: String,
    val subject: String,
    val description: String,
    val previousRequestCode: String? = null,
)

@Serializable
data class CancelServiceRequestDto(
    val reason: String? = null,
)

@Serializable
data class RequestHistoryItemDto(
    val id: String,
    val action: String,
    val previousState: String? = null,
    val newState: String,
    val occurredAt: String,
    val comment: String? = null,
)

@Serializable
data class ServiceRequestDetailDto(
    val id: String,
    val code: String,
    val category: String,
    val subject: String,
    val description: String,
    val status: String,
    val previousRequestCode: String? = null,
    val responseText: String? = null,
    val respondedAt: String? = null,
    val closedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val history: List<RequestHistoryItemDto> = emptyList(),
)

@Serializable
data class ServiceRequestSummaryDto(
    val id: String,
    val code: String,
    val subject: String,
    val category: String,
    val status: String,
    val previousRequestCode: String? = null,
    val createdAt: String,
)

@Serializable
data class PagedServiceRequestsResponseDto(
    val items: List<ServiceRequestSummaryDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)
