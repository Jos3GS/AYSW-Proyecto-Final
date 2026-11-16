package com.itm.servicedesk.viewmodel.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.data.mapper.RequestUiMapper
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.repository.ServiceRequestRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyRequestsViewModel(
    private val repository: ServiceRequestRepository,
    private val uiMapper: RequestUiMapper = RequestUiMapper(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyRequestsUiState())
    val uiState: StateFlow<MyRequestsUiState> = _uiState.asStateFlow()

    fun loadRequests(
        page: Int = _uiState.value.page,
        statusFilter: RequestStatus? = _uiState.value.selectedStatusFilter,
    ) {
        val targetPage = page.coerceAtLeast(1)

        _uiState.update {
            it.copy(
                page = targetPage,
                selectedStatusFilter = statusFilter,
                isLoading = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch(dispatcher) {
            when (
                val result =
                    repository.getMyRequests(
                        page = targetPage,
                        size = ServiceRequestRepository.DEFAULT_PAGE_SIZE,
                        statusFilter = statusFilter,
                    )
            ) {
                is AppResult.Success -> {
                    val responsePage = result.data
                    _uiState.update {
                        it.copy(
                            items = responsePage.items.map(uiMapper::toSummaryUiModel),
                            page = responsePage.page,
                            size = ServiceRequestRepository.DEFAULT_PAGE_SIZE,
                            totalItems = responsePage.totalItems,
                            totalPages = responsePage.totalPages.coerceAtLeast(1),
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }

                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error.userMessage,
                        )
                    }
                }
            }
        }
    }

    fun selectStatusFilter(statusFilter: RequestStatus?) {
        loadRequests(page = 1, statusFilter = statusFilter)
    }

    fun clearStatusFilter() {
        loadRequests(page = 1, statusFilter = null)
    }

    fun nextPage() {
        val current = _uiState.value
        if (!current.isLoading && current.page < current.totalPages) {
            loadRequests(page = current.page + 1, statusFilter = current.selectedStatusFilter)
        }
    }

    fun previousPage() {
        val current = _uiState.value
        if (!current.isLoading && current.page > 1) {
            loadRequests(page = current.page - 1, statusFilter = current.selectedStatusFilter)
        }
    }

    fun refresh() {
        val current = _uiState.value
        loadRequests(page = current.page, statusFilter = current.selectedStatusFilter)
    }
}
