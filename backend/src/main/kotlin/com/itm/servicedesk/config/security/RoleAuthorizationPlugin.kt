package com.itm.servicedesk.config.security

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.UserRole
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext

private class RoleAuthorizationConfig {
    var allowedRoles: Set<UserRole> = emptySet()
}

private class RoleAuthorizedRouteSelector : RouteSelector() {
    override suspend fun evaluate(
        context: RoutingResolveContext,
        segmentIndex: Int,
    ): RouteSelectorEvaluation = RouteSelectorEvaluation.Transparent
}

private val RoleAuthorizationPlugin =
    createRouteScopedPlugin(
        name = "RoleAuthorizationPlugin",
        createConfiguration = ::RoleAuthorizationConfig,
    ) {
        val allowedRoles = pluginConfig.allowedRoles
        on(AuthenticationChecked) { call ->
            val principal = call.principal<JwtUserPrincipal>() ?: return@on
            if (principal.identity.role !in allowedRoles) {
                throw DomainException.ForbiddenException(
                    "No tienes permisos suficientes para acceder a este recurso.",
                )
            }
        }
    }

fun Route.withRole(
    vararg allowedRoles: UserRole,
    build: Route.() -> Unit,
): Route {
    val authorizedRoute = createChild(RoleAuthorizedRouteSelector())
    authorizedRoute.install(RoleAuthorizationPlugin) {
        this.allowedRoles = allowedRoles.toSet()
    }
    authorizedRoute.build()
    return authorizedRoute
}
