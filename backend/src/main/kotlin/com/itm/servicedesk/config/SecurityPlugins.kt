package com.itm.servicedesk.config

import com.itm.servicedesk.domain.builder.ServerConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.net.URI

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureSecurityPlugins(serverConfig: ServerConfig) {
    install(XForwardedHeaders) {
        extractEdgeProxy { origin, xForwarded ->
            val directIp =
                IpMatcher.resolveClientIp(
                    remoteHost = origin.remoteHost,
                    remoteAddress = origin.remoteAddress,
                )
            if (IpMatcher.isLoopbackIp(directIp)) {
                val forwardedIp =
                    xForwarded.forHeader
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.lastOrNull { it.isNotEmpty() }
                if (!forwardedIp.isNullOrBlank()) {
                    origin.remoteHost = forwardedIp
                    origin.remoteAddress = forwardedIp
                }
            }
        }
    }

    install(CORS) {
        serverConfig.corsAllowedOrigins.forEach { rawOrigin ->
            val trimmed = rawOrigin.trim()
            if (trimmed.isNotEmpty() && !trimmed.contains('*')) {
                val uri = URI.create(trimmed)
                val scheme = uri.scheme
                val host = uri.host
                if (!scheme.isNullOrBlank() && !host.isNullOrBlank()) {
                    val hostWithPort = if (uri.port != -1) "$host:${uri.port}" else host
                    allowHost(hostWithPort, schemes = listOf(scheme))
                }
            }
        }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
    }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = false
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }
}
