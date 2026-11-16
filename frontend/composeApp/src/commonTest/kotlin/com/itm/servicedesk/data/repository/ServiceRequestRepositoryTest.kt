package com.itm.servicedesk.data.repository

import com.itm.servicedesk.core.network.createHttpClient
import com.itm.servicedesk.core.storage.SessionEvent
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.data.remote.KtorRequestRemoteDataSource
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CancelServiceRequestCommand
import com.itm.servicedesk.domain.model.CreateServiceRequestCommand
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequestDetail
import com.itm.servicedesk.domain.model.ServiceRequestsPage
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.InMemoryTestSessionStorage
import com.itm.servicedesk.testing.RequestTestFixtures
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class ServiceRequestRepositoryTest {
    @Test
    fun `create sends clean CreateServiceRequestDto and parses 201 Created`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "solicitante-jwt")
            var capturedBody = ""
            var capturedMethod: HttpMethod? = null

            val engine =
                MockEngine { request ->
                    capturedMethod = request.method
                    assertEquals("/api/v1/requests", request.url.encodedPath)
                    assertEquals("Bearer solicitante-jwt", request.headers[HttpHeaders.Authorization])
                    capturedBody = request.body.toByteArray().decodeToString()
                    respond(
                        content = RequestTestFixtures.requestDetailJson(),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client = createHttpClient(engine, "http://localhost:8080", storage, SessionEventBus())
            val repo = ServiceRequestRepositoryImpl(KtorRequestRemoteDataSource(client))

            val result =
                repo.create(
                    CreateServiceRequestCommand(
                        category = RequestCategory.TRAMITE,
                        subject = "  Solicitud de certificado de notas oficial  ",
                        description = "  Requiero el certificado oficial de calificaciones del semestre 2026-1.  ",
                        previousRequestCode = "sol-2026-00012",
                    ),
                )

            val detail = assertIs<AppResult.Success<ServiceRequestDetail>>(result).data
            assertEquals(HttpMethod.Post, capturedMethod)
            assertEquals("SOL-2026-00019", detail.code)
            val bodyKeys = Json.parseToJsonElement(capturedBody).jsonObject.keys
            assertFalse(bodyKeys.contains("code"))
            assertFalse(bodyKeys.contains("status"))
            assertFalse(bodyKeys.contains("requesterId"))
            assertFalse(bodyKeys.contains("createdAt"))
        }

    @Test
    fun `create maps 400 field error on previousRequestCode and 401 triggers SessionExpired`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "solicitante-jwt")
            val eventBus = SessionEventBus()

            val badRequestEngine =
                MockEngine {
                    respond(
                        content =
                            AuthTestFixtures.apiErrorJson(
                                status = 400,
                                error = "VALIDATION_ERROR",
                                message = "Error de validación.",
                                path = "/api/v1/requests",
                                detailsJson =
                                    """
                                    [
                                      {
                                        "field": "previousRequestCode",
                                        "message": "La solicitud referenciada debe estar Resuelta o Cerrada."
                                      }
                                    ]
                                    """.trimIndent(),
                            ),
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val badClient = createHttpClient(badRequestEngine, "http://localhost:8080", storage, eventBus)
            val badRepo = ServiceRequestRepositoryImpl(KtorRequestRemoteDataSource(badClient))

            val badResult =
                badRepo.create(
                    CreateServiceRequestCommand(
                        category = RequestCategory.TRAMITE,
                        subject = "Asunto válido",
                        description = "Descripción suficientemente larga para prueba.",
                        previousRequestCode = "SOL-2026-00001",
                    ),
                )
            val badError = assertIs<DomainError.BadRequest>(assertIs<AppResult.Failure>(badResult).error)
            assertEquals(
                "La solicitud referenciada debe estar Resuelta o Cerrada.",
                badError.fieldErrors["previousRequestCode"],
            )

            val unauthorizedEngine =
                MockEngine {
                    respond(
                        content =
                            AuthTestFixtures.apiErrorJson(
                                status = 401,
                                error = "UNAUTHORIZED",
                                message = "Token expirado.",
                                path = "/api/v1/requests",
                            ),
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val eventDeferred = async { eventBus.events.first() }
            val unauthClient = createHttpClient(unauthorizedEngine, "http://localhost:8080", storage, eventBus)
            val unauthRepo = ServiceRequestRepositoryImpl(KtorRequestRemoteDataSource(unauthClient))

            val unauthResult = unauthRepo.getMyRequests(page = 1, size = 20, statusFilter = null)
            assertIs<DomainError.Unauthorized>(assertIs<AppResult.Failure>(unauthResult).error)
            assertIs<SessionEvent.SessionExpired>(eventDeferred.await())
            assertNull(storage.getToken())
        }

    @Test
    fun `getMyRequests sends page size 20 and optional status filter`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "solicitante-jwt")
            var capturedPage: String? = null
            var capturedSize: String? = null
            var capturedStatus: String? = null

            val engine =
                MockEngine { request ->
                    assertEquals("/api/v1/requests/my", request.url.encodedPath)
                    capturedPage = request.url.parameters["page"]
                    capturedSize = request.url.parameters["size"]
                    capturedStatus = request.url.parameters["status"]
                    respond(
                        content = RequestTestFixtures.pagedRequestsJson(page = 2, size = 20, totalItems = 25),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client = createHttpClient(engine, "http://localhost:8080", storage, SessionEventBus())
            val repo = ServiceRequestRepositoryImpl(KtorRequestRemoteDataSource(client))

            val res = repo.getMyRequests(page = 2, size = 20, statusFilter = RequestStatus.RESUELTA)
            val page = assertIs<AppResult.Success<ServiceRequestsPage>>(res).data
            assertEquals("2", capturedPage)
            assertEquals("20", capturedSize)
            assertEquals("RESUELTA", capturedStatus)
            assertEquals(1, page.items.size)

            repo.getMyRequests(page = 1, size = 20, statusFilter = null)
            assertNull(capturedStatus)
        }

    @Test
    fun `getDetail and cancel map 200, 403, 404, and 409 Conflict`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "solicitante-jwt")

            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/requests/SOL-2026-00019" -> {
                            respond(
                                content = RequestTestFixtures.requestDetailJson(),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        "/api/v1/requests/SOL-2026-99999" -> {
                            respond(
                                content =
                                    AuthTestFixtures.apiErrorJson(
                                        status = 404,
                                        error = "NOT_FOUND",
                                        message = "Solicitud no encontrada.",
                                        path = request.url.encodedPath,
                                    ),
                                status = HttpStatusCode.NotFound,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        "/api/v1/requests/SOL-2026-00088" -> {
                            respond(
                                content =
                                    AuthTestFixtures.apiErrorJson(
                                        status = 403,
                                        error = "FORBIDDEN",
                                        message = "No tienes permisos para consultar esta solicitud.",
                                        path = request.url.encodedPath,
                                    ),
                                status = HttpStatusCode.Forbidden,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        "/api/v1/requests/SOL-2026-00019/cancel" -> {
                            respond(
                                content =
                                    AuthTestFixtures.apiErrorJson(
                                        status = 409,
                                        error = "CONFLICT",
                                        message = "Solo es posible cancelar una solicitud en estado Abierta.",
                                        path = request.url.encodedPath,
                                    ),
                                status = HttpStatusCode.Conflict,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        else -> {
                            respond(
                                content = RequestTestFixtures.requestDetailJson(status = "CANCELADA"),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }
                }

            val client = createHttpClient(engine, "http://localhost:8080", storage, SessionEventBus())
            val repo = ServiceRequestRepositoryImpl(KtorRequestRemoteDataSource(client))

            val detail = assertIs<AppResult.Success<ServiceRequestDetail>>(repo.getDetail("SOL-2026-00019")).data
            assertEquals("SOL-2026-00019", detail.code)

            val notFound = assertIs<AppResult.Failure>(repo.getDetail("SOL-2026-99999")).error
            assertIs<DomainError.NotFound>(notFound)

            val forbidden = assertIs<AppResult.Failure>(repo.getDetail("SOL-2026-00088")).error
            assertIs<DomainError.Forbidden>(forbidden)

            val conflict =
                assertIs<AppResult.Failure>(
                    repo.cancel(CancelServiceRequestCommand(code = "SOL-2026-00019", reason = "Ya resuelto")),
                ).error
            assertIs<DomainError.Conflict>(conflict)
            assertEquals("Solo es posible cancelar una solicitud en estado Abierta.", conflict.userMessage)

            val cancelled =
                assertIs<AppResult.Success<ServiceRequestDetail>>(
                    repo.cancel(CancelServiceRequestCommand(code = "SOL-2026-00020", reason = null)),
                ).data
            assertEquals(RequestStatus.CANCELADA, cancelled.status)
        }
}
