package com.itm.servicedesk.config

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.routing.dto.ApiErrorDto
import com.itm.servicedesk.routing.dto.ErrorDetailDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val statusPagesLogger = LoggerFactory.getLogger("com.itm.servicedesk.config.StatusPagesConfig")

fun Application.configureStatusPages(timezone: String = "America/Bogota") {
    val zoneId = ZoneId.of(timezone)

    install(StatusPages) {
        exception<DomainException> { call, cause ->
            val statusCode = HttpStatusCode.fromValue(cause.httpStatus)
            call.respondError(
                zoneId = zoneId,
                statusCode = statusCode,
                error = cause.errorCode,
                message = cause.message,
                details = cause.details.map { ErrorDetailDto(field = it.field, message = it.message) },
            )
        }

        exception<BadRequestException> { call, _ ->
            call.respondMalformedRequest(zoneId)
        }

        exception<ContentTransformationException> { call, _ ->
            call.respondMalformedRequest(zoneId)
        }

        exception<CannotTransformContentToTypeException> { call, _ ->
            call.respondMalformedRequest(zoneId)
        }

        exception<MissingRequestParameterException> { call, _ ->
            call.respondMalformedRequest(zoneId)
        }

        exception<ParameterConversionException> { call, _ ->
            call.respondMalformedRequest(zoneId)
        }

        exception<SerializationException> { call, _ ->
            call.respondMalformedRequest(zoneId)
        }

        exception<ExposedSQLException> { call, cause ->
            statusPagesLogger.error(
                "Database error on path={}: {}",
                call.request.path(),
                cause.javaClass.simpleName,
            )
            call.respondServiceUnavailable(zoneId)
        }

        exception<SQLException> { call, cause ->
            statusPagesLogger.error(
                "SQL connectivity error on path={}: {}",
                call.request.path(),
                cause.javaClass.simpleName,
            )
            call.respondServiceUnavailable(zoneId)
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondError(
                zoneId = zoneId,
                statusCode = HttpStatusCode.NotFound,
                error = "NOT_FOUND",
                message = "El recurso solicitado no fue encontrado.",
            )
        }

        exception<Throwable> { call, cause ->
            statusPagesLogger.error(
                "Unhandled internal error on path={}: {}",
                call.request.path(),
                cause.javaClass.simpleName,
            )
            call.respondError(
                zoneId = zoneId,
                statusCode = HttpStatusCode.InternalServerError,
                error = "INTERNAL_ERROR",
                message = "Ha ocurrido un error interno inesperado en el servidor.",
            )
        }
    }
}

private suspend fun ApplicationCall.respondMalformedRequest(zoneId: ZoneId) {
    respondError(
        zoneId = zoneId,
        statusCode = HttpStatusCode.BadRequest,
        error = "MALFORMED_REQUEST",
        message = "El cuerpo o los parámetros de la solicitud son inválidos.",
    )
}

private suspend fun ApplicationCall.respondServiceUnavailable(zoneId: ZoneId) {
    respondError(
        zoneId = zoneId,
        statusCode = HttpStatusCode.ServiceUnavailable,
        error = "SERVICE_UNAVAILABLE",
        message = "El servicio de base de datos no se encuentra disponible temporalmente.",
    )
}

private suspend fun ApplicationCall.respondError(
    zoneId: ZoneId,
    statusCode: HttpStatusCode,
    error: String,
    message: String,
    details: List<ErrorDetailDto> = emptyList(),
) {
    val timestamp = OffsetDateTime.now(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    respond(
        status = statusCode,
        message =
            ApiErrorDto(
                timestamp = timestamp,
                status = statusCode.value,
                error = error,
                message = message,
                path = request.path(),
                details = details,
            ),
    )
}
