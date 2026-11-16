package com.itm.servicedesk.routing

import com.itm.servicedesk.config.security.AUTH_JWT_NAME
import com.itm.servicedesk.config.security.requireJwtIdentity
import com.itm.servicedesk.config.security.withRole
import com.itm.servicedesk.domain.builder.AttentionQueryBuilder
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.routing.dto.attention.AddInterventionRequestDto
import com.itm.servicedesk.routing.dto.attention.AssignRequestDto
import com.itm.servicedesk.routing.dto.attention.EscalateRequestDto
import com.itm.servicedesk.routing.dto.attention.ReassignRequestDto
import com.itm.servicedesk.routing.dto.attention.RespondRequestDto
import com.itm.servicedesk.routing.dto.attention.toDto
import com.itm.servicedesk.service.AttentionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.ZoneId

fun Route.attentionRouting(
    attentionService: AttentionService,
    timezone: String = "America/Bogota",
) {
    val zoneId = ZoneId.of(timezone)

    authenticate(AUTH_JWT_NAME) {
        route("/api/v1/attention") {
            withRole(UserRole.COORDINADOR) {
                get("/coordination/pending") {
                    val actor = call.requireJwtIdentity()
                    val query =
                        AttentionQueryBuilder()
                            .forCoordinationPending()
                            .page(call.request.queryParameters["page"])
                            .size(call.request.queryParameters["size"])
                            .status(call.request.queryParameters["status"])
                            .build()
                    val result = attentionService.listCoordinationPending(actor = actor, query = query)
                    call.respond(HttpStatusCode.OK, result.toDto(zoneId))
                }

                post("/requests/{code}/assign") {
                    val actor = call.requireJwtIdentity()
                    val code =
                        call.parameters["code"]?.trim().takeIf { !it.isNullOrEmpty() }
                            ?: throw DomainException.NotFoundException("Solicitud no encontrada.")
                    val payload = call.receive<AssignRequestDto>()
                    val updated =
                        attentionService.assign(
                            actor = actor,
                            code = code,
                            rawAssigneeId = payload.assigneeId,
                            rawPriority = payload.priority,
                        )
                    call.respond(HttpStatusCode.OK, updated.toDto(zoneId))
                }

                post("/requests/{code}/reassign") {
                    val actor = call.requireJwtIdentity()
                    val code =
                        call.parameters["code"]?.trim().takeIf { !it.isNullOrEmpty() }
                            ?: throw DomainException.NotFoundException("Solicitud no encontrada.")
                    val payload = call.receive<ReassignRequestDto>()
                    val updated =
                        attentionService.reassign(
                            actor = actor,
                            code = code,
                            rawAssigneeId = payload.assigneeId,
                            rawReason = payload.reason,
                            rawPriority = payload.priority,
                        )
                    call.respond(HttpStatusCode.OK, updated.toDto(zoneId))
                }
            }

            withRole(UserRole.PERSONAL_ATENCION) {
                get("/my-assigned") {
                    val actor = call.requireJwtIdentity()
                    val query =
                        AttentionQueryBuilder()
                            .forAgent(actor.userId)
                            .page(call.request.queryParameters["page"])
                            .size(call.request.queryParameters["size"])
                            .status(call.request.queryParameters["status"])
                            .build()
                    val result = attentionService.listMyAssigned(actor = actor, query = query)
                    call.respond(HttpStatusCode.OK, result.toDto(zoneId))
                }

                post("/requests/{code}/interventions") {
                    val actor = call.requireJwtIdentity()
                    val code =
                        call.parameters["code"]?.trim().takeIf { !it.isNullOrEmpty() }
                            ?: throw DomainException.NotFoundException("Solicitud no encontrada.")
                    val payload = call.receive<AddInterventionRequestDto>()
                    val updated =
                        attentionService.addIntervention(
                            actor = actor,
                            code = code,
                            rawComment = payload.comment,
                        )
                    call.respond(HttpStatusCode.OK, updated.toDto(zoneId))
                }

                post("/requests/{code}/escalate") {
                    val actor = call.requireJwtIdentity()
                    val code =
                        call.parameters["code"]?.trim().takeIf { !it.isNullOrEmpty() }
                            ?: throw DomainException.NotFoundException("Solicitud no encontrada.")
                    val payload = call.receive<EscalateRequestDto>()
                    val updated =
                        attentionService.escalate(
                            actor = actor,
                            code = code,
                            rawReason = payload.reason,
                        )
                    call.respond(HttpStatusCode.OK, updated.toDto(zoneId))
                }

                post("/requests/{code}/respond") {
                    val actor = call.requireJwtIdentity()
                    val code =
                        call.parameters["code"]?.trim().takeIf { !it.isNullOrEmpty() }
                            ?: throw DomainException.NotFoundException("Solicitud no encontrada.")
                    val payload = call.receive<RespondRequestDto>()
                    val updated =
                        attentionService.respond(
                            actor = actor,
                            code = code,
                            rawResponseText = payload.responseText,
                        )
                    call.respond(HttpStatusCode.OK, updated.toDto(zoneId))
                }
            }

            withRole(UserRole.COORDINADOR, UserRole.PERSONAL_ATENCION) {
                get("/requests/{code}") {
                    val actor = call.requireJwtIdentity()
                    val code =
                        call.parameters["code"]?.trim().takeIf { !it.isNullOrEmpty() }
                            ?: throw DomainException.NotFoundException("Solicitud no encontrada.")
                    val detail = attentionService.getInternalDetail(actor = actor, code = code)
                    call.respond(HttpStatusCode.OK, detail.toDto(zoneId))
                }
            }
        }
    }
}
