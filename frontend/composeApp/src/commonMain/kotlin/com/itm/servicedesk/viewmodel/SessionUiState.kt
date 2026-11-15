package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.domain.model.UserRole

sealed interface SessionUiState {
    data object Checking : SessionUiState

    data class Unauthenticated(
        val noticeMessage: String? = null,
    ) : SessionUiState

    data class Authenticated(
        val user: UserProfile,
        val isConfirmingLogout: Boolean = false,
        val isLoggingOut: Boolean = false,
        val errorMessage: String? = null,
    ) : SessionUiState
}

sealed interface SessionNavigationEvent {
    data class NavigateToHome(
        val role: UserRole,
    ) : SessionNavigationEvent

    data class NavigateToLogin(
        val noticeMessage: String? = null,
    ) : SessionNavigationEvent
}
