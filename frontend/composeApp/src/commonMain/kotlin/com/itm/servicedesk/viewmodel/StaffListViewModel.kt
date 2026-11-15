package com.itm.servicedesk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.data.mapper.StaffUiMapper
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.StaffRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StaffListViewModel(
    private val staffRepository: StaffRepository,
    private val staffUiMapper: StaffUiMapper = StaffUiMapper(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StaffListUiState())
    val uiState: StateFlow<StaffListUiState> = _uiState.asStateFlow()

    fun loadStaff(
        page: Int = _uiState.value.page,
        roleFilter: UserRole? = _uiState.value.selectedRoleFilter,
    ) {
        val targetPage = page.coerceAtLeast(1)
        val sanitizedRole = roleFilter?.takeIf { it.isInternalStaff() }

        _uiState.update {
            it.copy(
                page = targetPage,
                selectedRoleFilter = sanitizedRole,
                isLoading = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch(dispatcher) {
            when (
                val result =
                    staffRepository.listStaff(
                        page = targetPage,
                        size = StaffRepository.DEFAULT_PAGE_SIZE,
                        roleFilter = sanitizedRole,
                    )
            ) {
                is AppResult.Success -> {
                    val staffPage = result.data
                    _uiState.update {
                        it.copy(
                            items = staffPage.items.map(staffUiMapper::toUiModel),
                            page = staffPage.page,
                            size = staffPage.size,
                            totalItems = staffPage.totalItems,
                            totalPages = staffPage.totalPages.coerceAtLeast(1),
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

    fun selectRoleFilter(roleFilter: UserRole?) {
        loadStaff(page = 1, roleFilter = roleFilter)
    }

    fun nextPage() {
        val current = _uiState.value
        if (!current.isLoading && current.page < current.totalPages) {
            loadStaff(page = current.page + 1, roleFilter = current.selectedRoleFilter)
        }
    }

    fun previousPage() {
        val current = _uiState.value
        if (!current.isLoading && current.page > 1) {
            loadStaff(page = current.page - 1, roleFilter = current.selectedRoleFilter)
        }
    }

    fun refresh() {
        val current = _uiState.value
        loadStaff(page = current.page, roleFilter = current.selectedRoleFilter)
    }
}
