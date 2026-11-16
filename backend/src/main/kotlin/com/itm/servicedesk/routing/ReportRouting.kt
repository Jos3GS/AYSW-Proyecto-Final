package com.itm.servicedesk.routing

import com.itm.servicedesk.config.security.AUTH_JWT_NAME
import com.itm.servicedesk.config.security.requireJwtIdentity
import com.itm.servicedesk.config.security.withRole
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.time.BogotaTimeProvider
import com.itm.servicedesk.routing.dto.reports.toDto
import com.itm.servicedesk.service.ReportService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.time.ZoneId

fun Route.reportRouting(
    reportService: ReportService,
    timezone: String = BogotaTimeProvider.DEFAULT_TIMEZONE,
) {
    val zoneId = ZoneId.of(timezone)

    authenticate(AUTH_JWT_NAME) {
        withRole(UserRole.COORDINADOR) {
            route("/api/v1/reports") {
                get("/summary") {
                    val actor = call.requireJwtIdentity()
                    val params = call.request.queryParameters
                    val summary =
                        reportService.getSummary(
                            actor = actor,
                            createdFrom = params["createdFrom"],
                            createdTo = params["createdTo"],
                            status = params["status"],
                            category = params["category"],
                            priority = params["priority"],
                            assignedToId = params["assignedToId"],
                        )
                    call.respond(HttpStatusCode.OK, summary.toDto(zoneId))
                }

                get("/requests") {
                    val actor = call.requireJwtIdentity()
                    val params = call.request.queryParameters
                    val result =
                        reportService.listRequests(
                            actor = actor,
                            createdFrom = params["createdFrom"],
                            createdTo = params["createdTo"],
                            status = params["status"],
                            category = params["category"],
                            priority = params["priority"],
                            assignedToId = params["assignedToId"],
                            page = params["page"],
                            size = params["size"],
                        )
                    call.respond(HttpStatusCode.OK, result.toDto(zoneId))
                }
            }
        }
    }
}
