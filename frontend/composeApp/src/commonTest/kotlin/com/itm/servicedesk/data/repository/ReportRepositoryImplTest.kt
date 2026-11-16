package com.itm.servicedesk.data.repository

import com.itm.servicedesk.core.network.createHttpClient
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.data.remote.KtorReportRemoteDataSource
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CoordinationReportRequestsPage
import com.itm.servicedesk.domain.model.CoordinationReportSummary
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.ReportFilterQuery
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.InMemoryTestSessionStorage
import com.itm.servicedesk.testing.ReportTestFixtures
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ReportRepositoryImplTest {
    @Test
    fun `getSummary and getRequests omit null filters and send selected query parameters`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "coord-jwt")
            val capturedCalls = mutableListOf<Pair<String, Parameters>>()

            val engine =
                MockEngine { request ->
                    capturedCalls.add(request.url.encodedPath to request.url.parameters)
                    when (request.url.encodedPath) {
                        "/api/v1/reports/summary" -> {
                            respond(
                                content = ReportTestFixtures.summaryJson(),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        "/api/v1/reports/requests" -> {
                            respond(
                                content = ReportTestFixtures.pagedRequestsJson(page = 2, size = 20, totalItems = 25),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        else -> {
                            error("Unexpected path: ${request.url.encodedPath}")
                        }
                    }
                }

            val client = createHttpClient(engine, "http://localhost:8080", storage, SessionEventBus())
            val repo = ReportRepositoryImpl(KtorReportRemoteDataSource(client))

            val emptySummaryRes = repo.getSummary(ReportFilterQuery())
            assertIs<AppResult.Success<CoordinationReportSummary>>(emptySummaryRes)
            val emptySummaryCall = capturedCalls[0]
            assertEquals("/api/v1/reports/summary", emptySummaryCall.first)
            assertFalse(emptySummaryCall.second.contains("createdFrom"))
            assertFalse(emptySummaryCall.second.contains("createdTo"))
            assertFalse(emptySummaryCall.second.contains("status"))
            assertFalse(emptySummaryCall.second.contains("category"))
            assertFalse(emptySummaryCall.second.contains("priority"))
            assertFalse(emptySummaryCall.second.contains("assignedToId"))

            val fullFilter =
                ReportFilterQuery(
                    createdFromIso = "2026-11-01",
                    createdToIso = "2026-11-14",
                    status = RequestStatus.ESCALADA,
                    category = RequestCategory.TRAMITE,
                    priority = RequestPriority.ALTA,
                    assignedToId = "agent-1",
                )
            val filteredRequestsRes = repo.getRequests(filter = fullFilter, page = 2, size = 20)
            assertIs<AppResult.Success<CoordinationReportRequestsPage>>(filteredRequestsRes)

            val requestsCall = capturedCalls[1]
            assertEquals("/api/v1/reports/requests", requestsCall.first)
            assertEquals("2026-11-01", requestsCall.second["createdFrom"])
            assertEquals("2026-11-14", requestsCall.second["createdTo"])
            assertEquals("ESCALADA", requestsCall.second["status"])
            assertEquals("TRAMITE", requestsCall.second["category"])
            assertEquals("ALTA", requestsCall.second["priority"])
            assertEquals("agent-1", requestsCall.second["assignedToId"])
            assertEquals("2", requestsCall.second["page"])
            assertEquals("20", requestsCall.second["size"])
        }

    @Test
    fun `maps 400, 401, 403 and network failures cleanly`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "coord-jwt")
            var mode = 400
            val engine =
                MockEngine { request ->
                    when (mode) {
                        400 -> {
                            respond(
                                content =
                                    AuthTestFixtures.apiErrorJson(
                                        400,
                                        "BAD_REQUEST",
                                        "Rango de fechas inválido.",
                                        request.url.encodedPath,
                                    ),
                                status = HttpStatusCode.BadRequest,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        401 -> {
                            respond(
                                content =
                                    AuthTestFixtures.apiErrorJson(
                                        401,
                                        "UNAUTHORIZED",
                                        "Tu sesión ha expirado.",
                                        request.url.encodedPath,
                                    ),
                                status = HttpStatusCode.Unauthorized,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        403 -> {
                            respond(
                                content =
                                    AuthTestFixtures.apiErrorJson(
                                        403,
                                        "FORBIDDEN",
                                        "Acceso solo para coordinación.",
                                        request.url.encodedPath,
                                    ),
                                status = HttpStatusCode.Forbidden,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        else -> {
                            error("Fallo de red simulado")
                        }
                    }
                }

            val client = createHttpClient(engine, "http://localhost:8080", storage, SessionEventBus())
            val repo = ReportRepositoryImpl(KtorReportRemoteDataSource(client))

            val err400 = assertIs<AppResult.Failure>(repo.getSummary(ReportFilterQuery())).error
            assertIs<DomainError.BadRequest>(err400)
            assertEquals("Rango de fechas inválido.", err400.userMessage)

            mode = 401
            val err401 = assertIs<AppResult.Failure>(repo.getRequests(ReportFilterQuery(), page = 1)).error
            assertIs<DomainError.Unauthorized>(err401)

            mode = 403
            val err403 = assertIs<AppResult.Failure>(repo.getSummary(ReportFilterQuery())).error
            assertIs<DomainError.Forbidden>(err403)

            mode = 500
            val errNet = assertIs<AppResult.Failure>(repo.getRequests(ReportFilterQuery(), page = 1)).error
            assertIs<DomainError.NetworkUnavailable>(errNet)
        }
}
