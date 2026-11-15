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
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
    fun `IpMatcher resolveClientIp falls back to numeric remoteAddress when remoteHost is resolved hostname`() {
        assertEquals(
            "127.0.0.1",
            IpMatcher.resolveClientIp("kubernetes.docker.internal", "127.0.0.1"),
        )
        assertEquals(
            "0:0:0:0:0:0:0:1",
            IpMatcher.resolveClientIp("kubernetes.docker.internal", "0:0:0:0:0:0:0:1"),
        )
        assertEquals(
            "127.0.0.1",
            IpMatcher.resolveClientIp("localhost", "localhost"),
        )
        assertEquals(
            "192.168.50.15",
            IpMatcher.resolveClientIp("192.168.50.15", "127.0.0.1"),
        )
        assertTrue(IpMatcher.isLoopbackIp("127.0.0.1"))
        assertTrue(IpMatcher.isLoopbackIp("0:0:0:0:0:0:0:1"))
        assertFalse(IpMatcher.isLoopbackIp("192.168.1.10"))
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

    @Test
    fun `Real Netty server on 0_0_0_0 accepts 127_0_0_1 loopback and forwarded proxy headers`() =
        runBlocking {
            val freePort = ServerSocket(0).use { it.localPort }
            val server =
                embeddedServer(Netty, port = freePort, host = "0.0.0.0") {
                    val serverConfig =
                        ServerConfig(
                            port = freePort,
                            timezone = "America/Bogota",
                            corsAllowedOrigins = listOf("http://localhost:8081"),
                            allowedClientIps = listOf("127.0.0.1", "::1"),
                        )
                    configureSecurityPlugins(serverConfig)
                    configureStatusPages(serverConfig.timezone)
                    configureIpAllowList(serverConfig.allowedClientIps)
                    routing {
                        get("/ping") {
                            call.respondText("pong")
                        }
                    }
                }.start(wait = false)
            try {
                val httpClient = HttpClient.newHttpClient()
                val directReq =
                    HttpRequest
                        .newBuilder(URI.create("http://127.0.0.1:$freePort/ping"))
                        .GET()
                        .build()
                val directRes = httpClient.send(directReq, HttpResponse.BodyHandlers.ofString())
                assertEquals(200, directRes.statusCode(), "Body was: ${directRes.body()}")
                assertEquals("pong", directRes.body())

                val proxiedReq =
                    HttpRequest
                        .newBuilder(URI.create("http://127.0.0.1:$freePort/ping"))
                        .header("X-Forwarded-For", "::1")
                        .GET()
                        .build()
                val proxiedRes = httpClient.send(proxiedReq, HttpResponse.BodyHandlers.ofString())
                assertEquals(200, proxiedRes.statusCode(), "Body was: ${proxiedRes.body()}")
            } finally {
                server.stop(100, 500)
            }
        }
}
