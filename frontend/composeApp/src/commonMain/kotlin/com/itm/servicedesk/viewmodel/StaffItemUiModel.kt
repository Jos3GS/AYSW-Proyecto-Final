package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.domain.model.UserRole

data class StaffItemUiModel(
    val id: String,
    val fullName: String,
    val email: String,
    val role: UserRole,
    val roleLabel: String,
    val formattedCreatedAt: String,
)
