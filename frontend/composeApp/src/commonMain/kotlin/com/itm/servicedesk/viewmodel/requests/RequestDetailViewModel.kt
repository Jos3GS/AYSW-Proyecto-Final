package com.itm.servicedesk.viewmodel.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.data.mapper.RequestUiMapper
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CancelServiceRequestCommand
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.repository.ServiceRequestRepository
import com.itm.servicedesk.domain.validation.RequestFormValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RequestDetailViewModel(
    private val repository: ServiceRequestRepository,
    private val uiMapper: RequestUiMapper = RequestUiMapper(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RequestDetailUiState>(RequestDetailUiState.Loading(code = ""))
    val uiState: StateFlow<RequestDetailUiState> = _uiState.asStateFlow()

    fun loadDetail(code: String) {
        _uiState.value = RequestDetailUiState.Loading(code = code)
        viewModelScope.launch(dispatcher) {
            fetchAndPublishDetail(code = code, preservedActionError = null)
        }
    }

    fun openCancelDialog() {
        val current = _uiState.value as? RequestDetailUiState.Content ?: return
        if (!current.detail.canCancel || current.isCancelling) {
            return
        }
        _uiState.value =
            current.copy(
                isCancelDialogOpen = true,
                cancelReason = "",
                cancelReasonError = null,
                actionError = null,
                successNotice = null,
            )
    }

    fun dismissCancelDialog() {
        val current = _uiState.value as? RequestDetailUiState.Content ?: return
        if (!current.isCancelling) {
            _uiState.value =
                current.copy(
                    isCancelDialogOpen = false,
                    cancelReasonError = null,
                )
        }
    }

    fun onCancelReasonChange(reason: String) {
        val current = _uiState.value as? RequestDetailUiState.Content ?: return
        if (!current.isCancelling) {
            _uiState.value =
                current.copy(
                    cancelReason = reason,
                    cancelReasonError = null,
                )
        }
    }

    fun confirmCancel() {
        val current = _uiState.value as? RequestDetailUiState.Content ?: return
        if (!current.detail.canCancel || current.isCancelling) {
            return
        }

        val reasonValidationError = RequestFormValidator.validateCancelReason(current.cancelReason)
        if (reasonValidationError != null) {
            _uiState.value = current.copy(cancelReasonError = reasonValidationError)
            return
        }

        _uiState.value =
            current.copy(
                isCancelling = true,
                cancelReasonError = null,
                actionError = null,
            )

        viewModelScope.launch(dispatcher) {
            val command =
                CancelServiceRequestCommand(
                    code = current.detail.code,
                    reason = RequestFormValidator.normalizeCancelReason(current.cancelReason),
                )
            when (val result = repository.cancel(command)) {
                is AppResult.Success -> {
                    _uiState.value =
                        RequestDetailUiState.Content(
                            detail = uiMapper.toDetailUiModel(result.data),
                            isCancelDialogOpen = false,
                            cancelReason = "",
                            cancelReasonError = null,
                            isCancelling = false,
                            actionError = null,
                            successNotice = "Solicitud cancelada correctamente.",
                        )
                }

                is AppResult.Failure -> {
                    handleCancelFailure(current = current, error = result.error)
                }
            }
        }
    }

    private suspend fun handleCancelFailure(
        current: RequestDetailUiState.Content,
        error: DomainError,
    ) {
        when (error) {
            is DomainError.BadRequest -> {
                val fieldMsg = error.fieldErrors[RequestFormValidator.FIELD_CANCEL_REASON] ?: error.userMessage
                _uiState.value =
                    current.copy(
                        isCancelDialogOpen = true,
                        isCancelling = false,
                        cancelReasonError = fieldMsg,
                    )
            }

            is DomainError.Conflict -> {
                _uiState.value =
                    current.copy(
                        isCancelDialogOpen = false,
                        cancelReason = "",
                        cancelReasonError = null,
                        isCancelling = false,
                        actionError = error.userMessage,
                    )
                fetchAndPublishDetail(
                    code = current.detail.code,
                    preservedActionError = error.userMessage,
                )
            }

            is DomainError.NotFound -> {
                _uiState.value =
                    RequestDetailUiState.Error(
                        code = current.detail.code,
                        message = error.userMessage,
                        isNotFound = true,
                    )
            }

            is DomainError.Forbidden -> {
                _uiState.value =
                    RequestDetailUiState.Error(
                        code = current.detail.code,
                        message = error.userMessage,
                        isForbidden = true,
                    )
            }

            else -> {
                _uiState.value =
                    current.copy(
                        isCancelDialogOpen = false,
                        isCancelling = false,
                        actionError = error.userMessage,
                    )
            }
        }
    }

    private suspend fun fetchAndPublishDetail(
        code: String,
        preservedActionError: String?,
    ) {
        when (val result = repository.getDetail(code)) {
            is AppResult.Success -> {
                _uiState.value =
                    RequestDetailUiState.Content(
                        detail = uiMapper.toDetailUiModel(result.data),
                        actionError = preservedActionError,
                    )
            }

            is AppResult.Failure -> {
                val error = result.error
                _uiState.value =
                    RequestDetailUiState.Error(
                        code = code,
                        message = error.userMessage,
                        isNotFound = error is DomainError.NotFound,
                        isForbidden = error is DomainError.Forbidden,
                    )
            }
        }
    }
}
