package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.core.network.DefaultApiJson
import com.itm.servicedesk.domain.model.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ErrorMapperTest {
    private val mapper = ErrorMapper(DefaultApiJson)

    @Test
    fun `maps 400 Bad Request with field details to DomainError BadRequest`() {
        val body =
            """
            {
              "timestamp": "2026-11-14T11:24:00-05:00",
              "status": 400,
              "error": "BAD_REQUEST",
              "message": "Los datos enviados no son válidos.",
              "path": "/api/v1/requests",
              "details": [
                {"field": "title", "message": "El título es obligatorio."}
              ]
            }
            """.trimIndent()

        val error = mapper.fromHttpResponse(400, body)

        val badRequest = assertIs<DomainError.BadRequest>(error)
        assertEquals("Los datos enviados no son válidos.", badRequest.userMessage)
        assertEquals(mapOf("title" to "El título es obligatorio."), badRequest.fieldErrors)
    }

    @Test
    fun `maps 401 Unauthorized to DomainError Unauthorized`() {
        val body =
            """
            {
              "timestamp": "2026-11-14T11:24:00-05:00",
              "status": 401,
              "error": "UNAUTHORIZED",
              "message": "Tu sesión ha expirado. Inicia sesión nuevamente.",
              "path": "/api/v1/requests",
              "details": []
            }
            """.trimIndent()

        val error = mapper.fromHttpResponse(401, body)

        val unauthorized = assertIs<DomainError.Unauthorized>(error)
        assertEquals("Tu sesión ha expirado. Inicia sesión nuevamente.", unauthorized.userMessage)
    }

    @Test
    fun `maps 403 Forbidden to DomainError Forbidden`() {
        val body =
            """
            {
              "timestamp": "2026-11-14T11:24:00-05:00",
              "status": 403,
              "error": "FORBIDDEN",
              "message": "No tienes permisos para realizar esta acción.",
              "path": "/api/v1/admin/users",
              "details": []
            }
            """.trimIndent()

        val error = mapper.fromHttpResponse(403, body)

        val forbidden = assertIs<DomainError.Forbidden>(error)
        assertEquals("No tienes permisos para realizar esta acción.", forbidden.userMessage)
    }

    @Test
    fun `maps 404 Not Found to DomainError NotFound`() {
        val body =
            """
            {
              "timestamp": "2026-11-14T11:24:00-05:00",
              "status": 404,
              "error": "NOT_FOUND",
              "message": "El recurso solicitado no existe.",
              "path": "/api/v1/requests/999",
              "details": []
            }
            """.trimIndent()

        val error = mapper.fromHttpResponse(404, body)

        val notFound = assertIs<DomainError.NotFound>(error)
        assertEquals("El recurso solicitado no existe.", notFound.userMessage)
    }

    @Test
    fun `maps 409 Conflict to DomainError Conflict`() {
        val body =
            """
            {
              "timestamp": "2026-11-14T11:24:00-05:00",
              "status": 409,
              "error": "CONFLICT",
              "message": "El correo institucional ya se encuentra registrado.",
              "path": "/api/v1/auth/register",
              "details": []
            }
            """.trimIndent()

        val error = mapper.fromHttpResponse(409, body)

        val conflict = assertIs<DomainError.Conflict>(error)
        assertEquals("El correo institucional ya se encuentra registrado.", conflict.userMessage)
    }

    @Test
    fun `maps 503 Service Unavailable to DomainError ServiceUnavailable`() {
        val body =
            """
            {
              "timestamp": "2026-11-14T11:24:00-05:00",
              "status": 503,
              "error": "SERVICE_UNAVAILABLE",
              "message": "El servicio de base de datos no se encuentra disponible temporalmente.",
              "path": "/api/v1/health",
              "details": []
            }
            """.trimIndent()

        val error = mapper.fromHttpResponse(503, body)

        val unavailable = assertIs<DomainError.ServiceUnavailable>(error)
        assertEquals(
            "El servicio de base de datos no se encuentra disponible temporalmente.",
            unavailable.userMessage,
        )
    }

    @Test
    fun `maps 500 Internal Server Error to safe generic message without raw json or stacktrace`() {
        val rawBody = """{"exception":"NullPointerException","trace":"at com.itm.SecretClass(File.kt:42)"}"""

        val error = mapper.fromHttpResponse(500, rawBody)

        val serverError = assertIs<DomainError.UnexpectedServerError>(error)
        assertFalse(serverError.userMessage.contains("NullPointerException"))
        assertFalse(serverError.userMessage.contains("SecretClass"))
        assertFalse(serverError.userMessage.contains("{"))
    }

    @Test
    fun `maps corrupted json body on 503 to fallback safe message`() {
        val error = mapper.fromHttpResponse(503, "<html>503 Bad Gateway</html>")

        val unavailable = assertIs<DomainError.ServiceUnavailable>(error)
        assertFalse(unavailable.userMessage.contains("<html>"))
    }

    @Test
    fun `maps transport throwable to NetworkUnavailable without leaking technical url`() {
        val error = mapper.fromThrowable(IllegalStateException("Failed to connect to http://10.0.2.2:8080"))

        val networkError = assertIs<DomainError.NetworkUnavailable>(error)
        assertFalse(networkError.userMessage.contains("10.0.2.2"))
        assertFalse(networkError.userMessage.contains("IllegalStateException"))
    }

    @Test
    fun `maps null or blank response body to safe Spanish fallbacks`() {
        assertIs<DomainError.BadRequest>(mapper.fromHttpResponse(400, null))
        assertIs<DomainError.Unauthorized>(mapper.fromHttpResponse(401, ""))
        assertIs<DomainError.Forbidden>(mapper.fromHttpResponse(403, "   "))
        assertIs<DomainError.NotFound>(mapper.fromHttpResponse(404, null))
        assertIs<DomainError.Conflict>(mapper.fromHttpResponse(409, null))
    }
}
