package com.itm.servicedesk.routing

import com.itm.servicedesk.routing.dto.HealthResponseDto
import com.itm.servicedesk.service.HealthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.format.DateTimeFormatter

fun Route.healthRouting(healthService: HealthService) {
    get("/api/v1/health") {
        val health = healthService.checkHealth()
        call.respond(
            status = HttpStatusCode.OK,
            message =
                HealthResponseDto(
                    status = health.status.name,
                    service = health.serviceName,
                    database = health.databaseStatus.name,
                    timestamp = health.checkedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                ),
        )
    }
}
