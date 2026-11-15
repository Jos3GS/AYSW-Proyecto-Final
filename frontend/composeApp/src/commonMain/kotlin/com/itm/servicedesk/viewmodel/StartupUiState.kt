package com.itm.servicedesk.viewmodel

sealed interface StartupUiState {
    data object Loading : StartupUiState

    data class Ready(
        val serviceLabel: String,
        val databaseLabel: String,
        val formattedCheckedAt: String,
    ) : StartupUiState

    data class Error(
        val title: String,
        val message: String,
        val canRetry: Boolean = true,
    ) : StartupUiState
}

sealed interface StartupEvent {
    data object HealthCheckSucceeded : StartupEvent

    data class ShowErrorBanner(
        val message: String,
    ) : StartupEvent
}
