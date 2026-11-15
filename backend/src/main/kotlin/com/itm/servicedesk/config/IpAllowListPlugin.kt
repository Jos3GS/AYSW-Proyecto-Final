package com.itm.servicedesk.config

import com.itm.servicedesk.domain.model.DomainException
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import java.net.InetAddress

object IpMatcher {
    private val ipv4LiteralRegex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val ipv6LiteralRegex = Regex("""^[0-9a-fA-F:]+(%[0-9a-zA-Z_.-]+)?$""")

    fun isAllowed(
        candidateIp: String,
        allowedEntries: List<String>,
    ): Boolean {
        val candidateAddress = parseNumericIp(candidateIp) ?: return false
        return allowedEntries.any { rawEntry ->
            val entry = rawEntry.trim()
            if (entry.isEmpty()) {
                false
            } else if (entry.contains('/')) {
                matchesCidr(candidateAddress, entry)
            } else {
                val allowedAddress = parseNumericIp(entry) ?: return@any false
                candidateAddress.address.contentEquals(allowedAddress.address)
            }
        }
    }

    private fun matchesCidr(
        candidateAddress: InetAddress,
        cidr: String,
    ): Boolean {
        val parts = cidr.split('/')
        if (parts.size != 2) return false
        val networkAddress = parseNumericIp(parts[0]) ?: return false
        val prefixLength = parts[1].toIntOrNull() ?: return false

        val candidateBytes = candidateAddress.address
        val networkBytes = networkAddress.address
        if (candidateBytes.size != networkBytes.size) return false

        val maxBits = networkBytes.size * 8
        if (prefixLength !in 0..maxBits) return false

        val fullBytes = prefixLength / 8
        for (i in 0 until fullBytes) {
            if (candidateBytes[i] != networkBytes[i]) return false
        }

        val remainingBits = prefixLength % 8
        if (remainingBits != 0) {
            val mask = (0xFF shl (8 - remainingBits)) and 0xFF
            if ((candidateBytes[fullBytes].toInt() and mask) != (networkBytes[fullBytes].toInt() and mask)) {
                return false
            }
        }
        return true
    }

    fun resolveClientIp(
        remoteHost: String,
        remoteAddress: String,
    ): String {
        if (parseNumericIp(remoteHost) != null) return remoteHost.trim()
        if (parseNumericIp(remoteAddress) != null) return remoteAddress.trim()

        val candidate = remoteAddress.trim().ifEmpty { remoteHost.trim() }
        if (candidate.equals("localhost", ignoreCase = true)) {
            return "127.0.0.1"
        }

        // En Windows con Docker Desktop, 127.0.0.1 puede resolverse como "kubernetes.docker.internal".
        val resolvedLoopback =
            runCatching { InetAddress.getByName(candidate) }
                .getOrNull()
                ?.takeIf { it.isLoopbackAddress }
                ?.hostAddress
        return resolvedLoopback ?: remoteHost.trim()
    }

    fun isLoopbackIp(candidateIp: String): Boolean = parseNumericIp(candidateIp)?.isLoopbackAddress == true

    private fun parseNumericIp(raw: String): InetAddress? {
        val trimmed = raw.trim().substringBefore('%')
        if (trimmed.isEmpty()) return null
        val isIpv4 = ipv4LiteralRegex.matches(trimmed)
        val isIpv6 = trimmed.contains(':') && ipv6LiteralRegex.matches(trimmed)
        if (!isIpv4 && !isIpv6) return null
        return runCatching { InetAddress.getByName(trimmed) }.getOrNull()
    }
}

private class IpAllowListConfig {
    var allowedIps: List<String> = listOf("127.0.0.1", "::1")
}

private val IpAllowListPlugin =
    createApplicationPlugin(name = "IpAllowListPlugin", createConfiguration = ::IpAllowListConfig) {
        val allowedEntries = pluginConfig.allowedIps
        onCall { call ->
            val normalizedHost =
                IpMatcher.resolveClientIp(
                    remoteHost = call.request.origin.remoteHost,
                    remoteAddress = call.request.origin.remoteAddress,
                )
            if (!IpMatcher.isAllowed(normalizedHost, allowedEntries)) {
                throw DomainException.ForbiddenException(
                    "Acceso denegado para la dirección IP cliente.",
                )
            }
        }
    }

fun Application.configureIpAllowList(allowedClientIps: List<String>) {
    install(IpAllowListPlugin) {
        allowedIps = allowedClientIps
    }
}
