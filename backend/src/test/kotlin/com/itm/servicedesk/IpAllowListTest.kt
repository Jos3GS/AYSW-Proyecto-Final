package com.itm.servicedesk

import com.itm.servicedesk.config.IpMatcher
import com.itm.servicedesk.config.configureIpAllowList
import com.itm.servicedesk.config.configureSecurityPlugins
import com.itm.servicedesk.config.configureStatusPages
import com.itm.servicedesk.domain.builder.ServerConfig
import com.itm.servicedesk.routing.dto.ApiErrorDto
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpAllowListTest {
    @Test
    fun `IpMatcher supports exact IPv4 IPv6 and CIDR ranges`() {
        val allowList = listOf("127.0.0.1", "::1", "192.168.10.0/24", "10.0.0.0/8")

        assertTrue(IpMatcher.isAllowed("127.0.0.1", allowList))
        assertTrue(IpMatcher.isAllowed("::1", allowList))
        assertTrue(IpMatcher.isAllowed("0:0:0:0:0:0:0:1", allowList))
        assertTrue(IpMatcher.isAllowed("192.168.10.45", allowList))
        assertTrue(IpMatcher.isAllowed("10.25.12.9", allowList))

        assertFalse(IpMatcher.isAllowed("192.168.11.45", allowList))
        assertFalse(IpMatcher.isAllowed("203.0.113.10", allowList))
        assertFalse(IpMatcher.isAllowed("invalid-ip", allowList))
    }

    @Test
    fun `IpAllowListPlugin allows permitted forwarded IP and returns 403 ApiErrorDto for blocked IP`() =
        testApplication {
            application {
                val serverConfig =
                    ServerConfig(
                        port = 8080,
                        timezone = "America/Bogota",
                        corsAllowedOrigins = listOf("http://localhost:8081"),
                        allowedClientIps = listOf("127.0.0.1", "::1", "192.168.50.0/24"),
                    )
                configureSecurityPlugins(serverConfig)
                configureStatusPages(serverConfig.timezone)
                configureIpAllowList(serverConfig.allowedClientIps)
                routing {
                    get("/ping") {
                        call.respondText("pong")
                    }
                }
            }

            val allowedResponse =
                client.get("/ping") {
                    header("X-Forwarded-For", "192.168.50.15")
                }
            assertEquals(HttpStatusCode.OK, allowedResponse.status)
            assertEquals("pong", allowedResponse.bodyAsText())

            val blockedResponse =
                client.get("/ping") {
                    header("X-Forwarded-For", "203.0.113.77")
                }
            assertEquals(HttpStatusCode.Forbidden, blockedResponse.status)

            val errorDto = Json.decodeFromString<ApiErrorDto>(blockedResponse.bodyAsText())
            assertEquals(403, errorDto.status)
            assertEquals("FORBIDDEN", errorDto.error)
            assertEquals("/ping", errorDto.path)
            assertTrue(errorDto.timestamp.endsWith("-05:00"))
        }
}
