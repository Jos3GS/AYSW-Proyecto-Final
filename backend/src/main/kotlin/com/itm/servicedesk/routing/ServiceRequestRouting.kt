package com.itm.servicedesk.routing

import com.itm.servicedesk.config.security.AUTH_JWT_NAME
import com.itm.servicedesk.config.security.requireJwtIdentity
import com.itm.servicedesk.config.security.withRole
import com.itm.servicedesk.domain.builder.MyRequestsQueryBuilder
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.routing.dto.requests.CancelServiceRequestDto
import com.itm.servicedesk.routing.dto.requests.CreateServiceRequestDto
import com.itm.servicedesk.routing.dto.requests.toDto
import com.itm.servicedesk.service.ServiceRequestService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.time.ZoneId

@OptIn(ExperimentalSerializationApi::class)
private val cancelRequestJson =
    Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }

fun Route.serviceRequestRouting(
    serviceRequestService: ServiceRequestService,
    timezone: String = "America/Bogota",
) {
    val zoneId = ZoneId.of(timezone)

    authenticate(AUTH_JWT_NAME) {
        withRole(UserRole.SOLICITANTE) {
            route("/api/v1/requests") {
                post {
                    val actor = call.requireJwtIdentity()
                    val request = call.receive<CreateServiceRequestDto>()
                    val created =
                        serviceRequestService.create(
                            actor = actor,
                            rawCategory = request.category,
                            rawSubject = request.subject,
                            rawDescription = request.description,
                            rawPreviousRequestCode = request.previousRequestCode,
                        )
                    call.respond(HttpStatusCode.Created, created.toDto(zoneId))
                }

                get("/my") {
                    val actor = call.requireJwtIdentity()
                    val query =
                        MyRequestsQueryBuilder()
                            .requesterId(actor.userId)
                            .page(call.request.queryParameters["page"])
                            .size(call.request.queryParameters["size"])
                            .status(call.request.queryParameters["status"])
                            .build()
                    val result = serviceRequestService.listMine(actor = actor, query = query)
                    call.respond(HttpStatusCode.OK, result.toDto(zoneId))
                }

                get("/{code}") {
                    val actor = call.requireJwtIdentity()
                    val code =
                        call.parameters["code"]?.trim().takeIf { !it.isNullOrEmpty() }
                            ?: throw DomainException.NotFoundException("Solicitud no encontrada.")
                    val detail = serviceRequestService.getDetail(actor = actor, code = code)
                    call.respond(HttpStatusCode.OK, detail.toDto(zoneId))
                }

                post("/{code}/cancel") {
                    val actor = call.requireJwtIdentity()
                    val code =
                        call.parameters["code"]?.trim().takeIf { !it.isNullOrEmpty() }
                            ?: throw DomainException.NotFoundException("Solicitud no encontrada.")
                    val rawBody = call.receiveText()
                    val payload =
                        if (rawBody.isBlank()) {
                            CancelServiceRequestDto()
                        } else {
                            cancelRequestJson.decodeFromString<CancelServiceRequestDto>(rawBody)
                        }
                    val updated =
                        serviceRequestService.cancel(
                            actor = actor,
                            code = code,
                            rawReason = payload.reason,
                        )
                    call.respond(HttpStatusCode.OK, updated.toDto(zoneId))
                }
            }
        }
    }
}
