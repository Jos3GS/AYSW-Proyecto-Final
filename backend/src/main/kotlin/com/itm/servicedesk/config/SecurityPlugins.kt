package com.itm.servicedesk.config

import com.itm.servicedesk.domain.builder.ServerConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.MutableOriginConnectionPoint
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaderValues
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.net.URI

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureSecurityPlugins(serverConfig: ServerConfig) {
    install(XForwardedHeaders) {
        extractEdgeProxy { origin, xForwarded ->
            applyTrustedLoopbackProxyHeaders(origin, xForwarded)
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

private const val MAX_TCP_PORT = 65535

internal fun applyTrustedLoopbackProxyHeaders(
    origin: MutableOriginConnectionPoint,
    xForwarded: XForwardedHeaderValues,
) {
    val directIp =
        IpMatcher.resolveClientIp(
            remoteHost = origin.remoteHost,
            remoteAddress = origin.remoteAddress,
        )
    if (!IpMatcher.isLoopbackIp(directIp)) {
        return
    }

    lastForwardedToken(xForwarded.forHeader)?.let { forwardedIp ->
        origin.remoteHost = forwardedIp
        origin.remoteAddress = forwardedIp
    }

    val forwardedProto =
        lastForwardedToken(xForwarded.protoHeader)
            ?.lowercase()
            ?.takeIf { it == "http" || it == "https" }
            ?: if (xForwarded.httpsFlagHeader?.trim()?.equals("on", ignoreCase = true) == true) {
                "https"
            } else {
                null
            }
    if (forwardedProto != null) {
        origin.scheme = forwardedProto
    }

    val forwardedPort =
        lastForwardedToken(xForwarded.portHeader)
            ?.toIntOrNull()
            ?.takeIf { it in 1..MAX_TCP_PORT }

    val forwardedHost = lastForwardedToken(xForwarded.hostHeader)
    if (forwardedHost != null) {
        val (host, hostPort) = parseHostAndPort(forwardedHost)
        if (host.isNotBlank()) {
            origin.serverHost = host
            val resolvedPort =
                forwardedPort
                    ?: hostPort
                    ?: URLProtocol.byName[origin.scheme]?.defaultPort
            if (resolvedPort != null) {
                origin.serverPort = resolvedPort
            }
        }
    } else if (forwardedPort != null) {
        origin.serverPort = forwardedPort
    } else if (forwardedProto == "https" && origin.serverPort == URLProtocol.HTTP.defaultPort) {
        origin.serverPort = URLProtocol.HTTPS.defaultPort
    }
}

private fun lastForwardedToken(headerValue: String?): String? =
    headerValue
        ?.split(',')
        ?.map { it.trim() }
        ?.lastOrNull { it.isNotEmpty() }

internal fun parseHostAndPort(rawHost: String): Pair<String, Int?> {
    val trimmed = rawHost.trim()
    if (trimmed.startsWith("[")) {
        val closingIdx = trimmed.indexOf(']')
        if (closingIdx > 1) {
            val host = trimmed.substring(0, closingIdx + 1)
            val rest = trimmed.substring(closingIdx + 1)
            val port =
                if (rest.startsWith(":")) {
                    rest.substring(1).toIntOrNull()?.takeIf { it in 1..MAX_TCP_PORT }
                } else {
                    null
                }
            return host to port
        }
    }
    val colonIdx = trimmed.lastIndexOf(':')
    if (colonIdx > 0 && trimmed.indexOf(':') == colonIdx) {
        val port = trimmed.substring(colonIdx + 1).toIntOrNull()?.takeIf { it in 1..MAX_TCP_PORT }
        if (port != null) {
            return trimmed.substring(0, colonIdx) to port
        }
    }
    return trimmed to null
}
