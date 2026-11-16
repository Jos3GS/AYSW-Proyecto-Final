package com.itm.servicedesk.viewmodel.requests

import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestStatus

data class RequestSummaryUiModel(
    val id: String,
    val code: String,
    val subject: String,
    val category: RequestCategory,
    val categoryLabel: String,
    val status: RequestStatus,
    val statusLabel: String,
    val previousRequestCode: String?,
    val formattedCreatedAt: String,
)

data class RequestHistoryItemUiModel(
    val id: String,
    val actionLabel: String,
    val transitionLabel: String,
    val formattedOccurredAt: String,
    val comment: String?,
)

data class RequestDetailUiModel(
    val id: String,
    val code: String,
    val category: RequestCategory,
    val categoryLabel: String,
    val status: RequestStatus,
    val statusLabel: String,
    val subject: String,
    val description: String,
    val previousRequestCode: String?,
    val responseText: String?,
    val formattedRespondedAt: String?,
    val formattedClosedAt: String?,
    val formattedCreatedAt: String,
    val formattedUpdatedAt: String,
    val canCancel: Boolean,
    val canCreateRelated: Boolean,
    val history: List<RequestHistoryItemUiModel>,
)
