package com.itm.servicedesk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.repository.SystemHealthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class StartupViewModel(
    private val repository: SystemHealthRepository,
    private val uiMapper: StartupUiMapper = StartupUiMapper(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<StartupUiState>(StartupUiState.Loading)
    val uiState: StateFlow<StartupUiState> = _uiState.asStateFlow()

    private val _events = Channel<StartupEvent>(Channel.BUFFERED)
    val events: Flow<StartupEvent> = _events.receiveAsFlow()

    init {
        checkSystemHealth()
    }

    fun retry() {
        _uiState.value = StartupUiState.Loading
        checkSystemHealth()
    }

    private fun checkSystemHealth() {
        viewModelScope.launch {
            when (val result = repository.checkHealth()) {
                is AppResult.Success -> {
                    _uiState.value = uiMapper.mapReady(result.data)
                    _events.send(StartupEvent.HealthCheckSucceeded)
                }

                is AppResult.Failure -> {
                    val errorState = uiMapper.mapError(result.error)
                    _uiState.value = errorState
                    _events.send(StartupEvent.ShowErrorBanner(errorState.message))
                }
            }
        }
    }
}
