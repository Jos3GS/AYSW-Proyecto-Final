package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.StaffRepository

data class StaffListUiState(
    val items: List<StaffItemUiModel> = emptyList(),
    val page: Int = 1,
    val size: Int = StaffRepository.DEFAULT_PAGE_SIZE,
    val totalItems: Long = 0,
    val totalPages: Int = 1,
    val selectedRoleFilter: UserRole? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
