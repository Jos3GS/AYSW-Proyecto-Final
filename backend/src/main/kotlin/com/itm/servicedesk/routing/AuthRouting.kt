package com.itm.servicedesk.routing

import com.itm.servicedesk.config.security.AUTH_JWT_NAME
import com.itm.servicedesk.config.security.requireJwtIdentity
import com.itm.servicedesk.routing.dto.auth.LoginRequestDto
import com.itm.servicedesk.routing.dto.auth.RegisterRequestDto
import com.itm.servicedesk.routing.dto.auth.toDto
import com.itm.servicedesk.routing.dto.users.toDto
import com.itm.servicedesk.service.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.ZoneId

fun Route.authRouting(
    authService: AuthService,
    timezone: String = "America/Bogota",
) {
    val zoneId = ZoneId.of(timezone)

    route("/api/v1/auth") {
        post("/register") {
            val request = call.receive<RegisterRequestDto>()
            val session =
                authService.register(
                    fullName = request.fullName,
                    documentNumber = request.documentNumber,
                    email = request.email,
                    phone = request.phone,
                    password = request.password,
                )
            call.respond(HttpStatusCode.Created, session.toDto(zoneId))
        }

        post("/login") {
            val request = call.receive<LoginRequestDto>()
            val session =
                authService.login(
                    email = request.email,
                    password = request.password,
                )
            call.respond(HttpStatusCode.OK, session.toDto(zoneId))
        }

        authenticate(AUTH_JWT_NAME) {
            get("/me") {
                val identity = call.requireJwtIdentity()
                val profile = authService.getMe(identity)
                call.respond(HttpStatusCode.OK, profile.toDto(zoneId))
            }

            post("/logout") {
                val identity = call.requireJwtIdentity()
                authService.logout(identity)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
