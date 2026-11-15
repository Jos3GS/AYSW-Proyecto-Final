package com.itm.servicedesk.core.storage

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface SessionEvent {
    data object SessionExpired : SessionEvent

    data object LoggedOut : SessionEvent
}

class SessionEventBus {
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    suspend fun emit(event: SessionEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: SessionEvent): Boolean = _events.tryEmit(event)
}
