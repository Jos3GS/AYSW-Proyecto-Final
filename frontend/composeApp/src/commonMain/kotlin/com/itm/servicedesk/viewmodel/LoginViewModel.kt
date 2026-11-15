package com.itm.servicedesk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.LoginCommand
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

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun onEmailChange(value: String) {
        _uiState.update { state ->
            state.copy(
                email = value,
                fieldErrors = state.fieldErrors - UserFormValidator.FIELD_EMAIL,
                generalError = null,
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { state ->
            state.copy(
                password = value,
                fieldErrors = state.fieldErrors - UserFormValidator.FIELD_PASSWORD,
                generalError = null,
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { state ->
            state.copy(isPasswordVisible = !state.isPasswordVisible)
        }
    }

    fun submit() {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting) {
            return
        }

        val validationErrors = UserFormValidator.validateLogin(snapshot.email, snapshot.password)
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
                LoginCommand(
                    email = UserFormValidator.normalizeEmail(snapshot.email),
                    password = snapshot.password,
                )
            when (val result = authRepository.login(command)) {
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
                    _events.send(LoginEvent.Authenticated(result.data))
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
                        } else if (error is DomainError.Unauthorized) {
                            state.copy(
                                generalError =
                                    if (error.userMessage.contains("expirado")) {
                                        "Credenciales inválidas."
                                    } else {
                                        error.userMessage
                                    },
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
