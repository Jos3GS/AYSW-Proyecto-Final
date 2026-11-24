package com.itm.servicedesk.routing

import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.Route

fun Route.swaggerRouting() {
    swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
}
