package com.itm.servicedesk.routing

import com.itm.servicedesk.config.security.AUTH_JWT_NAME
import com.itm.servicedesk.config.security.requireJwtIdentity
import com.itm.servicedesk.config.security.withRole
import com.itm.servicedesk.domain.builder.StaffPageQueryBuilder
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.routing.dto.users.CreateStaffRequestDto
import com.itm.servicedesk.routing.dto.users.toDto
import com.itm.servicedesk.service.StaffService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.ZoneId

fun Route.staffRouting(
    staffService: StaffService,
    timezone: String = "America/Bogota",
) {
    val zoneId = ZoneId.of(timezone)

    authenticate(AUTH_JWT_NAME) {
        withRole(UserRole.COORDINADOR) {
            route("/api/v1/users/staff") {
                post {
                    val actor = call.requireJwtIdentity()
                    val request = call.receive<CreateStaffRequestDto>()
                    val created =
                        staffService.createStaff(
                            actor = actor,
                            fullName = request.fullName,
                            documentNumber = request.documentNumber,
                            email = request.email,
                            phone = request.phone,
                            password = request.password,
                            rawRole = request.role,
                        )
                    call.respond(HttpStatusCode.Created, created.toDto(zoneId))
                }

                get {
                    val actor = call.requireJwtIdentity()
                    val query =
                        StaffPageQueryBuilder()
                            .page(call.request.queryParameters["page"])
                            .size(call.request.queryParameters["size"])
                            .role(call.request.queryParameters["role"])
                            .build()
                    val result = staffService.listStaff(actor = actor, query = query)
                    call.respond(HttpStatusCode.OK, result.toDto(zoneId))
                }
            }
        }
    }
}
