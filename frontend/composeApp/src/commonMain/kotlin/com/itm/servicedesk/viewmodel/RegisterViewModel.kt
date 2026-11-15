package com.itm.servicedesk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.RegisterCommand
import com.itm.servicedesk.domain.repository.AuthRepository
import com.itm.servicedesk.domain.validation.UserFormValidator
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

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<RegisterEvent>(Channel.BUFFERED)
    val events: Flow<RegisterEvent> = _events.receiveAsFlow()

    fun onFullNameChange(value: String) {
        _uiState.update {
            it.copy(
                fullName = value,
                fieldErrors = it.fieldErrors - UserFormValidator.FIELD_FULL_NAME,
                generalError = null,
            )
        }
    }

    fun onDocumentNumberChange(value: String) {
        _uiState.update {
            it.copy(
                documentNumber = value,
                fieldErrors = it.fieldErrors - UserFormValidator.FIELD_DOCUMENT_NUMBER,
                generalError = null,
            )
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                fieldErrors = it.fieldErrors - UserFormValidator.FIELD_EMAIL,
                generalError = null,
            )
        }
    }

    fun onPhoneChange(value: String) {
        _uiState.update {
            it.copy(
                phone = value,
                fieldErrors = it.fieldErrors - UserFormValidator.FIELD_PHONE,
                generalError = null,
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                fieldErrors = it.fieldErrors - UserFormValidator.FIELD_PASSWORD,
                generalError = null,
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun submit() {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting) {
            return
        }

        val validationErrors =
            UserFormValidator.validateRegistration(
                fullName = snapshot.fullName,
                documentNumber = snapshot.documentNumber,
                email = snapshot.email,
                phone = snapshot.phone,
                password = snapshot.password,
            )
        if (validationErrors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = validationErrors, generalError = null) }
            return
        }

        _uiState.update {
            it.copy(
                isSubmitting = true,
                fieldErrors = emptyMap(),
                generalError = null,
            )
        }

        viewModelScope.launch(dispatcher) {
            val command =
                RegisterCommand(
                    fullName = snapshot.fullName.trim(),
                    documentNumber = snapshot.documentNumber.trim(),
                    email = UserFormValidator.normalizeEmail(snapshot.email),
                    phone = snapshot.phone.trim(),
                    password = snapshot.password,
                )
            when (val result = authRepository.register(command)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            password = "",
                            isPasswordVisible = false,
                            fieldErrors = emptyMap(),
                            generalError = null,
                            isSubmitting = false,
                        )
                    }
                    _events.send(RegisterEvent.RegisteredAndAuthenticated(result.data))
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
