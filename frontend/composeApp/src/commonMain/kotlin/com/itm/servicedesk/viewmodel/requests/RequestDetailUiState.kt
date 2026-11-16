package com.itm.servicedesk.viewmodel.requests

sealed interface RequestDetailUiState {
    val code: String

    data class Loading(
        override val code: String,
    ) : RequestDetailUiState

    data class Error(
        override val code: String,
        val message: String,
        val isNotFound: Boolean = false,
        val isForbidden: Boolean = false,
    ) : RequestDetailUiState

    data class Content(
        val detail: RequestDetailUiModel,
        val isCancelDialogOpen: Boolean = false,
        val cancelReason: String = "",
        val cancelReasonError: String? = null,
        val isCancelling: Boolean = false,
        val actionError: String? = null,
        val successNotice: String? = null,
    ) : RequestDetailUiState {
        override val code: String
            get() = detail.code
    }
}
