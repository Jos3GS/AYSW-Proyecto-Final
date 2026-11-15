package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.core.network.DefaultApiJson
import com.itm.servicedesk.data.dto.ApiErrorDto
import com.itm.servicedesk.domain.model.DomainError
import kotlinx.serialization.json.Json

class ErrorMapper(
    private val json: Json = DefaultApiJson,
) {
    fun fromHttpResponse(
        statusCode: Int,
        responseBody: String?,
    ): DomainError {
        val parsedError = parseApiError(responseBody)

        return when (statusCode) {
            HTTP_BAD_REQUEST -> {
                val fieldErrors = parsedError?.details?.associate { it.field to it.message }.orEmpty()
                DomainError.BadRequest(
                    userMessage = safeMessage(parsedError, DEFAULT_BAD_REQUEST_MESSAGE),
                    fieldErrors = fieldErrors,
                )
            }

            HTTP_UNAUTHORIZED -> {
                DomainError.Unauthorized(
                    userMessage = safeMessage(parsedError, DEFAULT_UNAUTHORIZED_MESSAGE),
                )
            }

            HTTP_FORBIDDEN -> {
                DomainError.Forbidden(
                    userMessage = safeMessage(parsedError, DEFAULT_FORBIDDEN_MESSAGE),
                )
            }

            HTTP_NOT_FOUND -> {
                DomainError.NotFound(
                    userMessage = safeMessage(parsedError, DEFAULT_NOT_FOUND_MESSAGE),
                )
            }

            HTTP_CONFLICT -> {
                DomainError.Conflict(
                    userMessage = safeMessage(parsedError, DEFAULT_CONFLICT_MESSAGE),
                )
            }

            HTTP_SERVICE_UNAVAILABLE -> {
                DomainError.ServiceUnavailable(
                    userMessage = safeMessage(parsedError, DEFAULT_SERVICE_UNAVAILABLE_MESSAGE),
                )
            }

            else -> {
                DomainError.UnexpectedServerError(DEFAULT_SERVER_ERROR_MESSAGE)
            }
        }
    }

    fun fromThrowable(throwable: Throwable): DomainError = DomainError.NetworkUnavailable(DEFAULT_NETWORK_MESSAGE)

    private fun parseApiError(responseBody: String?): ApiErrorDto? {
        if (responseBody.isNullOrBlank()) {
            return null
        }
        return runCatching {
            json.decodeFromString<ApiErrorDto>(responseBody)
        }.getOrNull()
    }

    private fun safeMessage(
        dto: ApiErrorDto?,
        fallback: String,
    ): String =
        dto
            ?.message
            ?.takeIf { it.isNotBlank() }
            ?: fallback

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_SERVICE_UNAVAILABLE = 503

        const val DEFAULT_BAD_REQUEST_MESSAGE =
            "Los datos enviados no son válidos. Revisa la información e intenta nuevamente."
        const val DEFAULT_UNAUTHORIZED_MESSAGE =
            "Tu sesión ha expirado. Inicia sesión nuevamente."
        const val DEFAULT_FORBIDDEN_MESSAGE =
            "No tienes permisos para realizar esta acción."
        const val DEFAULT_NOT_FOUND_MESSAGE =
            "No se encontró el recurso solicitado."
        const val DEFAULT_CONFLICT_MESSAGE =
            "No fue posible completar la operación por un conflicto con los datos existentes."
        const val DEFAULT_SERVICE_UNAVAILABLE_MESSAGE =
            "El servicio no se encuentra disponible temporalmente. Intenta nuevamente en unos momentos."
        const val DEFAULT_SERVER_ERROR_MESSAGE =
            "Ocurrió un error interno en el servidor. Por favor, intenta nuevamente más tarde."
        const val DEFAULT_NETWORK_MESSAGE =
            "No fue posible conectarse con el servidor. Verifica tu conexión e intenta nuevamente."
    }
}
