package com.itm.servicedesk.data.repository

import com.itm.servicedesk.core.network.createHttpClient
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.data.remote.KtorAttentionRemoteDataSource
import com.itm.servicedesk.domain.model.AddInterventionCommand
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.AssignRequestCommand
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.AttentionRequestsPage
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.EscalateRequestCommand
import com.itm.servicedesk.domain.model.ReassignRequestCommand
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.RespondRequestCommand
import com.itm.servicedesk.testing.AttentionTestFixtures
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.InMemoryTestSessionStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AttentionRepositoryImplTest {
    @Test
    fun `getCoordinationPending and getMyAssigned send page size 20 and optional status`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "staff-jwt")
            val capturedRequests = mutableListOf<Triple<String, String?, String?>>()

            val engine =
                MockEngine { request ->
                    capturedRequests.add(
                        Triple(
                            request.url.encodedPath,
                            request.url.parameters["size"],
                            request.url.parameters["status"],
                        ),
                    )
                    respond(
                        content = AttentionTestFixtures.pagedAttentionJson(page = 2, size = 20, totalItems = 25),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client = createHttpClient(engine, "http://localhost:8080", storage, SessionEventBus())
            val repo = AttentionRepositoryImpl(KtorAttentionRemoteDataSource(client))

            val coordRes = repo.getCoordinationPending(page = 2, size = 20, status = RequestStatus.ESCALADA)
            assertIs<AppResult.Success<AttentionRequestsPage>>(coordRes)
            assertEquals(Triple("/api/v1/attention/coordination/pending", "20", "ESCALADA"), capturedRequests[0])

            val agentRes = repo.getMyAssigned(page = 1, size = 20, status = null)
            assertIs<AppResult.Success<AttentionRequestsPage>>(agentRes)
            assertEquals(Triple("/api/v1/attention/my-assigned", "20", null), capturedRequests[1])
        }

    @Test
    fun `all 5 attention mutations and internal detail send exact paths and payloads`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "staff-jwt")
            val recordedPayloads = mutableMapOf<String, String>()

            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    if (request.method == HttpMethod.Post) {
                        recordedPayloads[path] = request.body.toByteArray().decodeToString()
                    }
                    respond(
                        content = AttentionTestFixtures.attentionDetailJson(status = "EN_REVISION"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client = createHttpClient(engine, "http://localhost:8080", storage, SessionEventBus())
            val repo = AttentionRepositoryImpl(KtorAttentionRemoteDataSource(client))

            assertIs<AppResult.Success<AttentionRequestDetail>>(repo.getInternalDetail("SOL-2026-00019"))

            assertIs<AppResult.Success<AttentionRequestDetail>>(
                repo.assignRequest(
                    "SOL-2026-00019",
                    AssignRequestCommand(assigneeId = "agent-1", priority = RequestPriority.ALTA),
                ),
            )
            val assignObj =
                Json
                    .parseToJsonElement(recordedPayloads.getValue("/api/v1/attention/requests/SOL-2026-00019/assign"))
                    .jsonObject
            assertEquals("agent-1", assignObj["assigneeId"]?.jsonPrimitive?.content)
            assertEquals("ALTA", assignObj["priority"]?.jsonPrimitive?.content)

            assertIs<AppResult.Success<AttentionRequestDetail>>(
                repo.reassignRequest(
                    "SOL-2026-00019",
                    ReassignRequestCommand(assigneeId = "agent-2", reason = "Cambio de turno", priority = null),
                ),
            )
            val reassignObj =
                Json
                    .parseToJsonElement(
                        recordedPayloads.getValue("/api/v1/attention/requests/SOL-2026-00019/reassign"),
                    ).jsonObject
            assertEquals("agent-2", reassignObj["assigneeId"]?.jsonPrimitive?.content)
            assertEquals("Cambio de turno", reassignObj["reason"]?.jsonPrimitive?.content)
            assertNull(reassignObj["priority"])

            assertIs<AppResult.Success<AttentionRequestDetail>>(
                repo.addIntervention("SOL-2026-00019", AddInterventionCommand("Nota de verificación interna")),
            )
            val intervObj =
                Json
                    .parseToJsonElement(
                        recordedPayloads.getValue("/api/v1/attention/requests/SOL-2026-00019/interventions"),
                    ).jsonObject
            assertEquals("Nota de verificación interna", intervObj["comment"]?.jsonPrimitive?.content)

            assertIs<AppResult.Success<AttentionRequestDetail>>(
                repo.escalateRequest("SOL-2026-00019", EscalateRequestCommand("Requiere revisión de coordinación")),
            )
            val escObj =
                Json
                    .parseToJsonElement(
                        recordedPayloads.getValue("/api/v1/attention/requests/SOL-2026-00019/escalate"),
                    ).jsonObject
            assertEquals("Requiere revisión de coordinación", escObj["reason"]?.jsonPrimitive?.content)

            assertIs<AppResult.Success<AttentionRequestDetail>>(
                repo.respondRequest(
                    "SOL-2026-00019",
                    RespondRequestCommand("Su certificado fue emitido correctamente en plataforma."),
                ),
            )
            val respObj =
                Json
                    .parseToJsonElement(
                        recordedPayloads.getValue("/api/v1/attention/requests/SOL-2026-00019/respond"),
                    ).jsonObject
            assertEquals(
                "Su certificado fue emitido correctamente en plataforma.",
                respObj["responseText"]?.jsonPrimitive?.content,
            )
        }

    @Test
    fun `maps 400, 403, 404, 409 Conflict and network errors cleanly`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "staff-jwt")
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/attention/requests/SOL-400/respond" -> {
                            respond(
                                content =
                                    AuthTestFixtures.apiErrorJson(
                                        400,
                                        "BAD_REQUEST",
                                        "Error de validación",
                                        request.url.encodedPath,
                                        """[{"field":"responseText","message":"Longitud mínima 20."}]""",
                                    ),
                                status = HttpStatusCode.BadRequest,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        "/api/v1/attention/requests/SOL-403" -> {
                            respond(
                                content =
                                    AuthTestFixtures.apiErrorJson(
                                        403,
                                        "FORBIDDEN",
                                        "No tienes permisos para consultar esta solicitud.",
                                        request.url.encodedPath,
                                    ),
                                status = HttpStatusCode.Forbidden,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        "/api/v1/attention/requests/SOL-404" -> {
                            respond(
                                content =
                                    AuthTestFixtures.apiErrorJson(
                                        404,
                                        "NOT_FOUND",
                                        "Solicitud no encontrada.",
                                        request.url.encodedPath,
                                    ),
                                status = HttpStatusCode.NotFound,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        "/api/v1/attention/requests/SOL-409/assign" -> {
                            respond(
                                content =
                                    AuthTestFixtures.apiErrorJson(
                                        409,
                                        "CONFLICT",
                                        "La solicitud ya fue asignada por otro coordinador.",
                                        request.url.encodedPath,
                                    ),
                                status = HttpStatusCode.Conflict,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        else -> {
                            error("Fallo de red simulado")
                        }
                    }
                }

            val client = createHttpClient(engine, "http://localhost:8080", storage, SessionEventBus())
            val repo = AttentionRepositoryImpl(KtorAttentionRemoteDataSource(client))

            val badReq =
                assertIs<AppResult.Failure>(repo.respondRequest("SOL-400", RespondRequestCommand("Corta"))).error
            assertIs<DomainError.BadRequest>(badReq)
            assertEquals("Longitud mínima 20.", badReq.fieldErrors["responseText"])

            val forbidden = assertIs<AppResult.Failure>(repo.getInternalDetail("SOL-403")).error
            assertIs<DomainError.Forbidden>(forbidden)

            val notFound = assertIs<AppResult.Failure>(repo.getInternalDetail("SOL-404")).error
            assertIs<DomainError.NotFound>(notFound)

            val conflict =
                assertIs<AppResult.Failure>(
                    repo.assignRequest("SOL-409", AssignRequestCommand("agent-1", RequestPriority.ALTA)),
                ).error
            assertIs<DomainError.Conflict>(conflict)
            assertEquals("La solicitud ya fue asignada por otro coordinador.", conflict.userMessage)

            val networkErr = assertIs<AppResult.Failure>(repo.getInternalDetail("SOL-NET")).error
            assertIs<DomainError.NetworkUnavailable>(networkErr)
        }
}
