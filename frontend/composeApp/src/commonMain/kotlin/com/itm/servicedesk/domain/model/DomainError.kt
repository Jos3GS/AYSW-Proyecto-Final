package com.itm.servicedesk.domain.model

sealed interface DomainError {
    val userMessage: String

    data class BadRequest(
        override val userMessage: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : DomainError

    data class Unauthorized(
        override val userMessage: String,
    ) : DomainError

    data class Forbidden(
        override val userMessage: String,
    ) : DomainError

    data class NotFound(
        override val userMessage: String,
    ) : DomainError

    data class Conflict(
        override val userMessage: String,
    ) : DomainError

    data class ServiceUnavailable(
        override val userMessage: String,
    ) : DomainError

    data class UnexpectedServerError(
        override val userMessage: String,
    ) : DomainError

    data class NetworkUnavailable(
        override val userMessage: String,
    ) : DomainError
}
