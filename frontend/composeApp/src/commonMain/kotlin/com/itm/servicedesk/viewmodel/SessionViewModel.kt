package com.itm.servicedesk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.core.storage.SessionEvent
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.core.storage.SessionStorage
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.AuthSession
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SessionViewModel(
    private val authRepository: AuthRepository,
    private val sessionStorage: SessionStorage,
    private val sessionEventBus: SessionEventBus,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Checking)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<SessionNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<SessionNavigationEvent> = _navigationEvents.receiveAsFlow()

    init {
        viewModelScope.launch(dispatcher) {
            sessionEventBus.events.collect { event ->
                when (event) {
                    SessionEvent.SessionExpired -> {
                        if (_uiState.value !is SessionUiState.Unauthenticated) {
                            _uiState.value = SessionUiState.Unauthenticated(noticeMessage = SESSION_EXPIRED_NOTICE)
                            _navigationEvents.send(SessionNavigationEvent.NavigateToLogin(SESSION_EXPIRED_NOTICE))
                        }
                    }

                    SessionEvent.LoggedOut -> {
                        if (_uiState.value is SessionUiState.Authenticated) {
                            _uiState.value = SessionUiState.Unauthenticated(noticeMessage = LOGGED_OUT_NOTICE)
                            _navigationEvents.send(SessionNavigationEvent.NavigateToLogin(LOGGED_OUT_NOTICE))
                        }
                    }
                }
            }
        }
    }

    fun restoreSession() {
        viewModelScope.launch(dispatcher) {
            _uiState.value = SessionUiState.Checking
            val token = sessionStorage.getToken()
            if (token.isNullOrBlank()) {
                _uiState.value = SessionUiState.Unauthenticated(noticeMessage = null)
                _navigationEvents.send(SessionNavigationEvent.NavigateToLogin(noticeMessage = null))
                return@launch
            }

            when (val result = authRepository.getCurrentUser()) {
                is AppResult.Success -> {
                    _uiState.value = SessionUiState.Authenticated(user = result.data)
                    _navigationEvents.send(SessionNavigationEvent.NavigateToHome(result.data.role))
                }

                is AppResult.Failure -> {
                    val notice =
                        if (result.error is DomainError.Unauthorized) {
                            sessionStorage.clearToken()
                            SESSION_EXPIRED_NOTICE
                        } else {
                            result.error.userMessage
                        }
                    _uiState.value = SessionUiState.Unauthenticated(noticeMessage = notice)
                    _navigationEvents.send(SessionNavigationEvent.NavigateToLogin(noticeMessage = notice))
                }
            }
        }
    }

    fun onAuthenticated(session: AuthSession) {
        _uiState.value = SessionUiState.Authenticated(user = session.user)
        viewModelScope.launch(dispatcher) {
            _navigationEvents.send(SessionNavigationEvent.NavigateToHome(session.user.role))
        }
    }

    fun requestLogout() {
        val current = _uiState.value as? SessionUiState.Authenticated ?: return
        _uiState.value = current.copy(isConfirmingLogout = true, errorMessage = null)
    }

    fun dismissLogoutDialog() {
        val current = _uiState.value as? SessionUiState.Authenticated ?: return
        if (!current.isLoggingOut) {
            _uiState.value = current.copy(isConfirmingLogout = false, errorMessage = null)
        }
    }

    fun confirmLogout() {
        val current = _uiState.value as? SessionUiState.Authenticated ?: return
        if (current.isLoggingOut) {
            return
        }

        _uiState.value = current.copy(isLoggingOut = true, errorMessage = null)
        viewModelScope.launch(dispatcher) {
            when (val result = authRepository.logout()) {
                is AppResult.Success -> {
                    _uiState.value = SessionUiState.Unauthenticated(noticeMessage = LOGGED_OUT_NOTICE)
                    _navigationEvents.send(SessionNavigationEvent.NavigateToLogin(LOGGED_OUT_NOTICE))
                }

                is AppResult.Failure -> {
                    val latest = _uiState.value as? SessionUiState.Authenticated ?: return@launch
                    _uiState.value =
                        latest.copy(
                            isConfirmingLogout = false,
                            isLoggingOut = false,
                            errorMessage = result.error.userMessage,
                        )
                }
            }
        }
    }

    fun clearNotice() {
        val current = _uiState.value as? SessionUiState.Unauthenticated ?: return
        if (current.noticeMessage != null) {
            _uiState.value = current.copy(noticeMessage = null)
        }
    }

    fun hasRole(vararg allowedRoles: UserRole): Boolean {
        val current = _uiState.value as? SessionUiState.Authenticated ?: return false
        return current.user.role in allowedRoles
    }

    companion object {
        const val SESSION_EXPIRED_NOTICE =
            "Tu sesión expiró. Por favor, inicia sesión de nuevo."
        const val LOGGED_OUT_NOTICE =
            "Has cerrado sesión correctamente."
    }
}
