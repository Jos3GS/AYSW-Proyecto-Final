package com.itm.servicedesk.viewmodel.attention

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.repository.AttentionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AgentInboxViewModel(
    private val attentionRepository: AttentionRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AgentInboxUiState())
    val uiState: StateFlow<AgentInboxUiState> = _uiState.asStateFlow()

    fun loadAssigned(
        page: Int = _uiState.value.currentPage,
        statusFilter: RequestStatus? = _uiState.value.selectedStatusFilter,
    ) {
        val targetPage = page.coerceAtLeast(1)
        val sanitizedFilter = statusFilter?.takeIf { it in ALLOWED_STATUS_FILTERS }

        _uiState.update {
            it.copy(
                currentPage = targetPage,
                selectedStatusFilter = sanitizedFilter,
                isLoading = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch(dispatcher) {
            when (
                val result =
                    attentionRepository.getMyAssigned(
                        page = targetPage,
                        size = AttentionRepository.DEFAULT_PAGE_SIZE,
                        status = sanitizedFilter,
                    )
            ) {
                is AppResult.Success -> {
                    val responsePage = result.data
                    _uiState.update {
                        it.copy(
                            items = responsePage.items,
                            currentPage = responsePage.page,
                            totalPages = responsePage.totalPages.coerceAtLeast(1),
                            totalItems = responsePage.totalItems,
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
        loadAssigned(page = 1, statusFilter = statusFilter)
    }

    fun nextPage() {
        val current = _uiState.value
        if (!current.isLoading && current.currentPage < current.totalPages) {
            loadAssigned(page = current.currentPage + 1, statusFilter = current.selectedStatusFilter)
        }
    }

    fun previousPage() {
        val current = _uiState.value
        if (!current.isLoading && current.currentPage > 1) {
            loadAssigned(page = current.currentPage - 1, statusFilter = current.selectedStatusFilter)
        }
    }

    fun refresh() {
        val current = _uiState.value
        loadAssigned(page = current.currentPage, statusFilter = current.selectedStatusFilter)
    }

    companion object {
        val ALLOWED_STATUS_FILTERS =
            setOf(
                RequestStatus.EN_REVISION,
                RequestStatus.ESCALADA,
                RequestStatus.RESUELTA,
                RequestStatus.CERRADA,
            )
    }
}
