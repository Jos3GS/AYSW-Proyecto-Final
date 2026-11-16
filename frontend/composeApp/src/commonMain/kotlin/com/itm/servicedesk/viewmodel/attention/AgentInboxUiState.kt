package com.itm.servicedesk.viewmodel.attention

import com.itm.servicedesk.domain.model.AttentionRequestSummary
import com.itm.servicedesk.domain.model.RequestStatus

data class AgentInboxUiState(
    val items: List<AttentionRequestSummary> = emptyList(),
    val selectedStatusFilter: RequestStatus? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Long = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
