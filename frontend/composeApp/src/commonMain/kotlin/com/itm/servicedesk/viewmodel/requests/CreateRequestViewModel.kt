package com.itm.servicedesk.viewmodel.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CreateServiceRequestCommand
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.repository.ServiceRequestRepository
import com.itm.servicedesk.domain.validation.RequestFormValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateRequestViewModel(
    private val repository: ServiceRequestRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateRequestUiState())
    val uiState: StateFlow<CreateRequestUiState> = _uiState.asStateFlow()

    private val _events = Channel<CreateRequestEvent>(Channel.BUFFERED)
    val events: Flow<CreateRequestEvent> = _events.receiveAsFlow()

    private var hasPrefilledFromRoute: Boolean = false

    fun prefillPreviousRequestCode(code: String?) {
        val normalized = RequestFormValidator.normalizePreviousRequestCode(code)
        if (!hasPrefilledFromRoute && normalized != null) {
            hasPrefilledFromRoute = true
            _uiState.update {
                it.copy(
                    previousRequestCode = normalized,
                    fieldErrors = it.fieldErrors - RequestFormValidator.FIELD_PREVIOUS_REQUEST_CODE,
                )
            }
        }
    }

    fun onCategorySelected(category: RequestCategory) {
        _uiState.update {
            it.copy(
                category = category,
                fieldErrors = it.fieldErrors - RequestFormValidator.FIELD_CATEGORY,
                generalError = null,
            )
        }
    }

    fun onSubjectChange(value: String) {
        _uiState.update {
            it.copy(
                subject = value,
                fieldErrors = it.fieldErrors - RequestFormValidator.FIELD_SUBJECT,
                generalError = null,
            )
        }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update {
            it.copy(
                description = value,
                fieldErrors = it.fieldErrors - RequestFormValidator.FIELD_DESCRIPTION,
                generalError = null,
            )
        }
    }

    fun onPreviousRequestCodeChange(value: String) {
        _uiState.update {
            it.copy(
                previousRequestCode = value,
                fieldErrors = it.fieldErrors - RequestFormValidator.FIELD_PREVIOUS_REQUEST_CODE,
                generalError = null,
            )
        }
    }

    fun submit() {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting) {
            return
        }

        val validationErrors =
            RequestFormValidator.validateCreateRequest(
                category = snapshot.category,
                subject = snapshot.subject,
                description = snapshot.description,
                previousRequestCode = snapshot.previousRequestCode,
            )
        if (validationErrors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    fieldErrors = validationErrors,
                    generalError = null,
                )
            }
            return
        }

        val selectedCategory = snapshot.category ?: return

        _uiState.update {
            it.copy(
                isSubmitting = true,
                fieldErrors = emptyMap(),
                generalError = null,
            )
        }

        viewModelScope.launch(dispatcher) {
            val command =
                CreateServiceRequestCommand(
                    category = selectedCategory,
                    subject = snapshot.subject.trim(),
                    description = snapshot.description.trim(),
                    previousRequestCode =
                        RequestFormValidator.normalizePreviousRequestCode(snapshot.previousRequestCode),
                )
            when (val result = repository.create(command)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            fieldErrors = emptyMap(),
                            generalError = null,
                            createdCode = result.data.code,
                        )
                    }
                    _events.send(CreateRequestEvent.Created(result.data))
                }

                is AppResult.Failure -> {
                    val error = result.error
                    _uiState.update { state ->
                        if (error is DomainError.BadRequest) {
                            state.copy(
                                fieldErrors = error.fieldErrors,
                                generalError = if (error.fieldErrors.isEmpty()) error.userMessage else null,
                                isSubmitting = false,
                            )
                        } else {
                            state.copy(
                                generalError = error.userMessage,
                                isSubmitting = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
