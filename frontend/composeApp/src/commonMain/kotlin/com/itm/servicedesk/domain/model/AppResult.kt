package com.itm.servicedesk.domain.model

sealed interface AppResult<out T> {
    data class Success<T>(
        val data: T,
    ) : AppResult<T>

    data class Failure(
        val error: DomainError,
    ) : AppResult<Nothing>
}
