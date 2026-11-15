package com.itm.servicedesk.domain.model

data class ValidationDetail(
    val field: String,
    val message: String,
)

sealed class DomainException(
    override val message: String,
    val errorCode: String,
    val httpStatus: Int,
    val details: List<ValidationDetail> = emptyList(),
) : RuntimeException(message) {
    class ValidationException(
        message: String,
        details: List<ValidationDetail> = emptyList(),
        errorCode: String = "VALIDATION_ERROR",
    ) : DomainException(message, errorCode, 400, details)

    class UnauthorizedException(
        message: String = "No autorizado.",
    ) : DomainException(message, "UNAUTHORIZED", 401)

    class ForbiddenException(
        message: String = "Acceso denegado.",
    ) : DomainException(message, "FORBIDDEN", 403)

    class NotFoundException(
        message: String = "Recurso no encontrado.",
    ) : DomainException(message, "NOT_FOUND", 404)

    class ConflictException(
        message: String = "Conflicto con el estado actual del recurso.",
    ) : DomainException(message, "CONFLICT", 409)

    class ServiceUnavailableException(
        message: String = "El servicio de base de datos no se encuentra disponible temporalmente.",
    ) : DomainException(message, "SERVICE_UNAVAILABLE", 503)
}
