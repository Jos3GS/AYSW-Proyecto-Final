package com.itm.servicedesk.viewmodel.requests

import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.repository.ServiceRequestRepository

data class MyRequestsUiState(
    val items: List<RequestSummaryUiModel> = emptyList(),
    val page: Int = 1,
    val size: Int = ServiceRequestRepository.DEFAULT_PAGE_SIZE,
    val totalItems: Long = 0,
    val totalPages: Int = 1,
    val selectedStatusFilter: RequestStatus? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
