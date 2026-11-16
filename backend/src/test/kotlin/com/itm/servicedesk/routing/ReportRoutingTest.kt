package com.itm.servicedesk.routing

import com.itm.servicedesk.FakeTimeProvider
import com.itm.servicedesk.IntegrationTestBase
import com.itm.servicedesk.configureModule
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.repository.RequestCodeSequencesTable
import com.itm.servicedesk.repository.ServiceRequestsTable
import com.itm.servicedesk.routing.dto.ApiErrorDto
import com.itm.servicedesk.routing.dto.reports.CoordinationReportSummaryDto
import com.itm.servicedesk.routing.dto.reports.PagedCoordinationReportRequestsResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportRoutingTest : IntegrationTestBase() {
    private val bogotaOffset = ZoneOffset.of("-05:00")
    private val strictJson = Json { ignoreUnknownKeys = false }

    private suspend fun registerSolicitante(
        client: HttpClient,
        documentNumber: String,
        email: String,
        fullName: String = "Laura Gómez Pérez",
    ): Pair<UUID, String> {
        val resp =
            client.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "fullName": "$fullName",
                      "documentNumber": "$documentNumber",
                      "email": "$email",
                      "phone": "3001234567",
                      "password": "ClaveSegura2026"
                    }
                    """.trimIndent(),
                )
            }
        assertEquals(HttpStatusCode.Created, resp.status)
        val json = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        return UUID.fromString(json["user"]!!.jsonObject["id"]!!.jsonPrimitive.content) to
            json["accessToken"]!!.jsonPrimitive.content
    }

    private suspend fun loginUser(
        client: HttpClient,
        email: String,
        password: String,
    ): String {
        val resp =
            client.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email": "$email", "password": "$password"}""")
            }
        assertEquals(HttpStatusCode.OK, resp.status)
        return Json
            .parseToJsonElement(resp.bodyAsText())
            .jsonObject["accessToken"]!!
            .jsonPrimitive.content
    }

    private suspend fun createStaffAndLogin(
        client: HttpClient,
        coordToken: String,
        fullName: String,
        documentNumber: String,
        email: String,
    ): Pair<UUID, String> {
        val createResp =
            client.post("/api/v1/users/staff") {
                header(HttpHeaders.Authorization, "Bearer $coordToken")
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "fullName": "$fullName",
                      "documentNumber": "$documentNumber",
                      "email": "$email",
                      "phone": "3119876543",
                      "password": "StaffPassword2026",
                      "role": "PERSONAL_ATENCION"
                    }
                    """.trimIndent(),
                )
            }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val userId =
            UUID.fromString(
                Json
                    .parseToJsonElement(createResp.bodyAsText())
                    .jsonObject["id"]!!
                    .jsonPrimitive.content,
            )
        val token = loginUser(client, email, "StaffPassword2026")
        return userId to token
    }

    private suspend fun createRequest(
        client: HttpClient,
        solToken: String,
        category: RequestCategory,
        subject: String,
    ): String {
        val resp =
            client.post("/api/v1/requests") {
                header(HttpHeaders.Authorization, "Bearer $solToken")
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "category": "${category.name}",
                      "subject": "$subject",
                      "description": "Descripción formal de solicitud para pruebas de reportes gerenciales."
                    }
                    """.trimIndent(),
                )
            }
        assertEquals(HttpStatusCode.Created, resp.status)
        return Json
            .parseToJsonElement(resp.bodyAsText())
            .jsonObject["code"]!!
            .jsonPrimitive.content
    }

    @Test
    fun `RBAC rejects unauthenticated, revoked JWT, SOLICITANTE and PERSONAL_ATENCION on report endpoints`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val fakeClock = FakeTimeProvider(OffsetDateTime.of(2026, 11, 15, 10, 0, 0, 0, bogotaOffset))
            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig, timeProviderOverride = fakeClock)
            }

            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/reports/summary").status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/reports/requests").status)

            val coordToken = loginUser(client, "coordinador@itm.edu.co", "SeedPassword2026")
            val (_, solToken) = registerSolicitante(client, "1030000001", "sol.rbac@itm.edu.co")
            val (_, agentToken) =
                createStaffAndLogin(client, coordToken, "Agente Reporte", "74000001", "ag.rbac@itm.edu.co")

            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/api/v1/reports/summary") {
                        header(HttpHeaders.Authorization, "Bearer $solToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/api/v1/reports/requests") {
                        header(HttpHeaders.Authorization, "Bearer $solToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/api/v1/reports/summary") {
                        header(HttpHeaders.Authorization, "Bearer $agentToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/api/v1/reports/requests") {
                        header(HttpHeaders.Authorization, "Bearer $agentToken")
                    }.status,
            )

            // Revoked JWT must return 401
            val logoutResp =
                client.post("/api/v1/auth/logout") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.NoContent, logoutResp.status)
            assertEquals(
                HttpStatusCode.Unauthorized,
                client
                    .get("/api/v1/reports/summary") {
                        header(HttpHeaders.Authorization, "Bearer $coordToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client
                    .get("/api/v1/reports/requests") {
                        header(HttpHeaders.Authorization, "Bearer $coordToken")
                    }.status,
            )
        }

    @Test
    fun `summary and requests validate query params, handle empty matches, and reuse 004 detail endpoint`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val fakeClock = FakeTimeProvider(OffsetDateTime.of(2026, 11, 15, 10, 0, 0, 0, bogotaOffset))
            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig, timeProviderOverride = fakeClock)
            }

            val coordToken = loginUser(client, "coordinador@itm.edu.co", "SeedPassword2026")
            val (_, solToken) = registerSolicitante(client, "1030000002", "sol.empty@itm.edu.co")

            // Empty dataset still returns all 6 statuses, 4 categories, 3 priorities initialized to 0
            val emptySummaryResp =
                client.get("/api/v1/reports/summary") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.OK, emptySummaryResp.status)
            val emptySummary = strictJson.decodeFromString<CoordinationReportSummaryDto>(emptySummaryResp.bodyAsText())
            assertEquals(0L, emptySummary.totalRequests)
            assertEquals(6, emptySummary.byStatus.size)
            assertEquals(4, emptySummary.byCategory.size)
            assertEquals(3, emptySummary.byPriority.size)
            assertEquals(0L, emptySummary.unassignedPriorityCount)
            assertEquals(0L, emptySummary.unassignedCount)
            assertEquals(0L, emptySummary.overdueCount)
            assertEquals(0L, emptySummary.resolvedWithinSlaCount)
            assertEquals(0L, emptySummary.resolvedOutsideSlaCount)
            assertTrue(emptySummary.byAssignee.isEmpty())
            assertTrue(emptySummary.generatedAt.endsWith("-05:00"))

            // Invalid filters return 400 with field details
            val invalidResp =
                client.get(
                    "/api/v1/reports/requests?createdFrom=2026-11-20&createdTo=2026-11-10" +
                        "&assignedToId=not-a-uuid&size=25&page=0&status=BAD&category=BAD&priority=BAD",
                ) {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.BadRequest, invalidResp.status)
            val errDto = strictJson.decodeFromString<ApiErrorDto>(invalidResp.bodyAsText())
            assertEquals(
                setOf("createdTo", "assignedToId", "size", "page", "status", "category", "priority"),
                errDto.details.map { it.field }.toSet(),
            )

            // Create one request and verify reuse of 004 detail and absence of duplicated /reports/requests/{code}
            val code = createRequest(client, solToken, RequestCategory.TRAMITE, "Solicitud detalle reutilizado")
            assertEquals(
                HttpStatusCode.NotFound,
                client
                    .get("/api/v1/reports/requests/$code") {
                        header(HttpHeaders.Authorization, "Bearer $coordToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.OK,
                client
                    .get("/api/v1/attention/requests/$code") {
                        header(HttpHeaders.Authorization, "Bearer $coordToken")
                    }.status,
            )
        }

    @Test
    fun `consolidated metrics, combined filters, Bogota 23_30 boundary, and summary vs requests parity`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            // Reference "now" = 2026-11-16 12:00:00-05:00
            val nowTime = OffsetDateTime.of(2026, 11, 16, 12, 0, 0, 0, bogotaOffset)
            val fakeClock = FakeTimeProvider(nowTime)
            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig, timeProviderOverride = fakeClock)
            }

            val coordToken = loginUser(client, "coordinador@itm.edu.co", "SeedPassword2026")
            val (solId, _) = registerSolicitante(client, "1030000003", "sol.metrics@itm.edu.co", "Laura Gómez")
            val (agentAId, _) =
                createStaffAndLogin(client, coordToken, "Ana Martínez", "74000010", "ana.m@itm.edu.co")
            val (agentBId, _) =
                createStaffAndLogin(client, coordToken, "Carlos Mejía", "74000011", "carlos.m@itm.edu.co")

            // Seed 7 heterogeneous requests covering all 6 statuses, categories, priorities, SLAs & 23:30-05:00 boundary
            initTestDatabase(runMigrations = false).use { conn ->
                transaction(conn.database) {
                    data class SeedRow(
                        val code: String,
                        val category: RequestCategory,
                        val status: RequestStatus,
                        val priority: RequestPriority?,
                        val assignedTo: UUID?,
                        val dueAt: OffsetDateTime?,
                        val respondedAt: OffsetDateTime?,
                        val respondedWithinSla: Boolean?,
                        val closedAt: OffsetDateTime?,
                        val createdAt: OffsetDateTime,
                    )

                    val bogota14Early = OffsetDateTime.of(2026, 11, 14, 9, 0, 0, 0, bogotaOffset)
                    // 2026-11-15T04:30Z
                    val bogota14Late = OffsetDateTime.of(2026, 11, 14, 23, 30, 0, 0, bogotaOffset)
                    // 2026-11-15T05:00Z
                    val bogota15Midnight = OffsetDateTime.of(2026, 11, 15, 0, 0, 0, 0, bogotaOffset)

                    val seeds =
                        listOf(
                            // 1. ABIERTA (unassigned, no priority) on 2026-11-14 09:00
                            SeedRow(
                                code = "SOL-2026-00001",
                                category = RequestCategory.TRAMITE,
                                status = RequestStatus.ABIERTA,
                                priority = null,
                                assignedTo = null,
                                dueAt = null,
                                respondedAt = null,
                                respondedWithinSla = null,
                                closedAt = null,
                                createdAt = bogota14Early,
                            ),
                            // 2. EN_REVISION on-time (agentA, ALTA, dueAt > now) on 2026-11-14 23:30-05:00 (boundary!)
                            SeedRow(
                                code = "SOL-2026-00002",
                                category = RequestCategory.REQUERIMIENTO_SERVICIO,
                                status = RequestStatus.EN_REVISION,
                                priority = RequestPriority.ALTA,
                                assignedTo = agentAId,
                                dueAt = nowTime.plusHours(10),
                                respondedAt = null,
                                respondedWithinSla = null,
                                closedAt = null,
                                createdAt = bogota14Late,
                            ),
                            // 3. EN_REVISION overdue (agentB, ALTA, dueAt < now) on 2026-11-14 23:45-05:00
                            SeedRow(
                                code = "SOL-2026-00003",
                                category = RequestCategory.REQUERIMIENTO_SERVICIO,
                                status = RequestStatus.EN_REVISION,
                                priority = RequestPriority.ALTA,
                                assignedTo = agentBId,
                                dueAt = nowTime.minusHours(2),
                                respondedAt = null,
                                respondedWithinSla = null,
                                closedAt = null,
                                createdAt = bogota14Late.plusMinutes(15),
                            ),
                            // 4. ESCALADA overdue (agentA, MEDIA, dueAt < now) on 2026-11-15 00:00-05:00
                            SeedRow(
                                code = "SOL-2026-00004",
                                category = RequestCategory.ORIENTACION,
                                status = RequestStatus.ESCALADA,
                                priority = RequestPriority.MEDIA,
                                assignedTo = agentAId,
                                dueAt = nowTime.minusMinutes(30),
                                respondedAt = null,
                                respondedWithinSla = null,
                                closedAt = null,
                                createdAt = bogota15Midnight,
                            ),
                            // 5. RESUELTA within SLA (agentA, BAJA, dueAt < now should NOT count as active overdue!)
                            SeedRow(
                                code = "SOL-2026-00005",
                                category = RequestCategory.ATENCION_ADMINISTRATIVA,
                                status = RequestStatus.RESUELTA,
                                priority = RequestPriority.BAJA,
                                assignedTo = agentAId,
                                dueAt = nowTime.minusHours(5),
                                respondedAt = nowTime.minusHours(6),
                                respondedWithinSla = true,
                                closedAt = null,
                                createdAt = bogota15Midnight.plusHours(1),
                            ),
                            // 6. CERRADA outside SLA (agentB, MEDIA)
                            SeedRow(
                                code = "SOL-2026-00006",
                                category = RequestCategory.TRAMITE,
                                status = RequestStatus.CERRADA,
                                priority = RequestPriority.MEDIA,
                                assignedTo = agentBId,
                                dueAt = nowTime.minusHours(30),
                                respondedAt = nowTime.minusHours(26),
                                respondedWithinSla = false,
                                closedAt = nowTime.minusHours(2),
                                createdAt = bogota15Midnight.plusHours(2),
                            ),
                            // 7. CANCELADA (unassigned, no priority)
                            SeedRow(
                                code = "SOL-2026-00007",
                                category = RequestCategory.ORIENTACION,
                                status = RequestStatus.CANCELADA,
                                priority = null,
                                assignedTo = null,
                                dueAt = null,
                                respondedAt = null,
                                respondedWithinSla = null,
                                closedAt = null,
                                createdAt = bogota15Midnight.plusHours(3),
                            ),
                        )

                    ServiceRequestsTable.batchInsert(seeds, shouldReturnGeneratedValues = false) { seed ->
                        this[ServiceRequestsTable.id] = UUID.randomUUID()
                        this[ServiceRequestsTable.code] = seed.code
                        this[ServiceRequestsTable.requesterId] = solId
                        this[ServiceRequestsTable.category] = seed.category.name
                        this[ServiceRequestsTable.subject] = "Asunto ${seed.code}"
                        this[ServiceRequestsTable.description] = "Descripción de prueba para ${seed.code}."
                        this[ServiceRequestsTable.status] = seed.status.name
                        this[ServiceRequestsTable.assignedTo] = seed.assignedTo
                        this[ServiceRequestsTable.priority] = seed.priority?.name
                        if (seed.assignedTo != null) {
                            this[ServiceRequestsTable.firstAssignedAt] = seed.createdAt.plusMinutes(5)
                            this[ServiceRequestsTable.assignedAt] = seed.createdAt.plusMinutes(5)
                        }
                        this[ServiceRequestsTable.dueAt] = seed.dueAt
                        if (seed.respondedAt != null) {
                            this[ServiceRequestsTable.responseText] = "Respuesta formal registrada."
                        }
                        this[ServiceRequestsTable.respondedAt] = seed.respondedAt
                        this[ServiceRequestsTable.respondedWithinSla] = seed.respondedWithinSla
                        this[ServiceRequestsTable.closedAt] = seed.closedAt
                        this[ServiceRequestsTable.createdAt] = seed.createdAt
                        this[ServiceRequestsTable.updatedAt] = seed.createdAt
                    }
                    RequestCodeSequencesTable.insert {
                        it[year] = 2026
                        it[lastNumber] = 7
                    }
                }
            }

            // 1. Global summary without filters
            val globalResp =
                client.get("/api/v1/reports/summary") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.OK, globalResp.status)
            val globalSummary = strictJson.decodeFromString<CoordinationReportSummaryDto>(globalResp.bodyAsText())
            assertEquals(7L, globalSummary.totalRequests)
            assertEquals(1L, globalSummary.byStatus["ABIERTA"])
            assertEquals(2L, globalSummary.byStatus["EN_REVISION"])
            assertEquals(1L, globalSummary.byStatus["ESCALADA"])
            assertEquals(1L, globalSummary.byStatus["RESUELTA"])
            assertEquals(1L, globalSummary.byStatus["CERRADA"])
            assertEquals(1L, globalSummary.byStatus["CANCELADA"])
            assertEquals(2L, globalSummary.byCategory["TRAMITE"])
            assertEquals(2L, globalSummary.byCategory["ORIENTACION"])
            assertEquals(1L, globalSummary.byCategory["ATENCION_ADMINISTRATIVA"])
            assertEquals(2L, globalSummary.byCategory["REQUERIMIENTO_SERVICIO"])
            assertEquals(2L, globalSummary.byPriority["ALTA"])
            assertEquals(2L, globalSummary.byPriority["MEDIA"])
            assertEquals(1L, globalSummary.byPriority["BAJA"])
            assertEquals(2L, globalSummary.unassignedPriorityCount)
            assertEquals(2L, globalSummary.unassignedCount)
            assertEquals(2L, globalSummary.overdueCount) // only SOL-00003 and SOL-00004
            assertEquals(1L, globalSummary.resolvedWithinSlaCount)
            assertEquals(1L, globalSummary.resolvedOutsideSlaCount)

            // byAssignee sorted by totalAssigned DESC, assigneeName ASC (agentA=3, agentB=2)
            assertEquals(2, globalSummary.byAssignee.size)
            assertEquals(agentAId.toString(), globalSummary.byAssignee[0].assigneeId)
            assertEquals(3L, globalSummary.byAssignee[0].totalAssigned)
            assertEquals(1L, globalSummary.byAssignee[0].overdueAssigned)
            assertEquals(agentBId.toString(), globalSummary.byAssignee[1].assigneeId)
            assertEquals(2L, globalSummary.byAssignee[1].totalAssigned)
            assertEquals(1L, globalSummary.byAssignee[1].overdueAssigned)

            // 2. Bogota 23:30-05:00 boundary check: 2026-11-14 includes SOL-00001, 00002 (23:30), 00003 (23:45) only
            val bogotaDaySummaryResp =
                client.get("/api/v1/reports/summary?createdFrom=2026-11-14&createdTo=2026-11-14") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.OK, bogotaDaySummaryResp.status)
            val bogotaDaySummary =
                strictJson.decodeFromString<CoordinationReportSummaryDto>(bogotaDaySummaryResp.bodyAsText())
            assertEquals(3L, bogotaDaySummary.totalRequests)
            assertEquals(0L, bogotaDaySummary.byStatus["ESCALADA"])
            assertEquals(0L, bogotaDaySummary.byCategory["ORIENTACION"])
            assertEquals(0L, bogotaDaySummary.byPriority["BAJA"])

            val bogotaDayListResp =
                client.get("/api/v1/reports/requests?createdFrom=2026-11-14&createdTo=2026-11-14&page=1&size=20") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.OK, bogotaDayListResp.status)
            val bogotaDayList =
                strictJson.decodeFromString<PagedCoordinationReportRequestsResponseDto>(bogotaDayListResp.bodyAsText())
            assertEquals(bogotaDaySummary.totalRequests, bogotaDayList.totalItems)
            assertEquals(
                listOf("SOL-2026-00003", "SOL-2026-00002", "SOL-2026-00001"),
                bogotaDayList.items.map { it.code },
            )
            assertTrue(bogotaDayList.items[0].isOverdue)
            assertFalse(bogotaDayList.items[1].isOverdue)

            // 3. Combined filters parity (createdFrom + createdTo + status + category + priority + assignedToId)
            val comboQuery =
                "createdFrom=2026-11-14&createdTo=2026-11-14&status=EN_REVISION" +
                    "&category=REQUERIMIENTO_SERVICIO&priority=ALTA&assignedToId=$agentAId"
            val comboSummary =
                strictJson.decodeFromString<CoordinationReportSummaryDto>(
                    client
                        .get("/api/v1/reports/summary?$comboQuery") {
                            header(HttpHeaders.Authorization, "Bearer $coordToken")
                        }.bodyAsText(),
                )
            val comboList =
                strictJson.decodeFromString<PagedCoordinationReportRequestsResponseDto>(
                    client
                        .get("/api/v1/reports/requests?$comboQuery&page=1&size=20") {
                            header(HttpHeaders.Authorization, "Bearer $coordToken")
                        }.bodyAsText(),
                )
            assertEquals(1L, comboSummary.totalRequests)
            assertEquals(comboSummary.totalRequests, comboList.totalItems)
            assertEquals(listOf("SOL-2026-00002"), comboList.items.map { it.code })
            assertEquals(6, comboSummary.byStatus.size)
            assertEquals(4, comboSummary.byCategory.size)
            assertEquals(3, comboSummary.byPriority.size)
        }

    @Test
    fun `performance RNF-07 1000 requests summary and paged list respond under 2000ms`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val baseTime = OffsetDateTime.of(2026, 11, 15, 10, 0, 0, 0, bogotaOffset)
            val fakeClock = FakeTimeProvider(baseTime.plusHours(48))
            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig, timeProviderOverride = fakeClock)
            }

            val coordToken = loginUser(client, "coordinador@itm.edu.co", "SeedPassword2026")
            val (solId, _) = registerSolicitante(client, "1030000099", "sol.perf@itm.edu.co")
            val (agentAId, _) =
                createStaffAndLogin(client, coordToken, "Agente Perf Uno", "74000091", "ag.p1@itm.edu.co")
            val (agentBId, _) =
                createStaffAndLogin(client, coordToken, "Agente Perf Dos", "74000092", "ag.p2@itm.edu.co")

            initTestDatabase(runMigrations = false).use { conn ->
                transaction(conn.database) {
                    val categories = RequestCategory.entries
                    val priorities = RequestPriority.entries
                    val rows = (1..1000).toList()

                    ServiceRequestsTable.batchInsert(rows, shouldReturnGeneratedValues = false) { i ->
                        val ts = baseTime.plusSeconds(i.toLong())
                        val status =
                            when {
                                i <= 150 -> RequestStatus.ABIERTA
                                i <= 450 -> RequestStatus.EN_REVISION
                                i <= 550 -> RequestStatus.ESCALADA
                                i <= 750 -> RequestStatus.RESUELTA
                                i <= 900 -> RequestStatus.CERRADA
                                else -> RequestStatus.CANCELADA
                            }
                        val assignee =
                            if (status == RequestStatus.ABIERTA || status == RequestStatus.CANCELADA) {
                                null
                            } else if (i % 2 == 0) {
                                agentAId
                            } else {
                                agentBId
                            }
                        val priority =
                            if (assignee == null) {
                                null
                            } else {
                                priorities[i % priorities.size]
                            }

                        this[ServiceRequestsTable.id] = UUID.randomUUID()
                        this[ServiceRequestsTable.code] = "SOL-2026-${i.toString().padStart(5, '0')}"
                        this[ServiceRequestsTable.requesterId] = solId
                        this[ServiceRequestsTable.category] = categories[i % categories.size].name
                        this[ServiceRequestsTable.subject] = "Solicitud reporte carga $i"
                        this[ServiceRequestsTable.description] =
                            "Descripción masiva para prueba RNF-07 de reportes de coordinación."
                        this[ServiceRequestsTable.status] = status.name
                        this[ServiceRequestsTable.assignedTo] = assignee
                        this[ServiceRequestsTable.priority] = priority?.name
                        if (assignee != null) {
                            this[ServiceRequestsTable.firstAssignedAt] = ts
                            this[ServiceRequestsTable.assignedAt] = ts
                            this[ServiceRequestsTable.dueAt] =
                                if (i % 3 == 0) ts.plusHours(12) else ts.plusHours(72)
                        }
                        if (status == RequestStatus.RESUELTA || status == RequestStatus.CERRADA) {
                            this[ServiceRequestsTable.responseText] = "Respuesta formal generada en carga."
                            this[ServiceRequestsTable.respondedAt] = ts.plusHours(2)
                            this[ServiceRequestsTable.respondedWithinSla] = (i % 4 != 0)
                        }
                        if (status == RequestStatus.CERRADA) {
                            this[ServiceRequestsTable.closedAt] = ts.plusHours(26)
                        }
                        this[ServiceRequestsTable.createdAt] = ts
                        this[ServiceRequestsTable.updatedAt] = ts
                    }
                    RequestCodeSequencesTable.insert {
                        it[year] = 2026
                        it[lastNumber] = 1000
                    }
                }
            }

            var summaryBody = ""
            val summaryElapsedMs =
                measureTimeMillis {
                    val resp =
                        client.get("/api/v1/reports/summary") {
                            header(HttpHeaders.Authorization, "Bearer $coordToken")
                        }
                    assertEquals(HttpStatusCode.OK, resp.status)
                    summaryBody = resp.bodyAsText()
                }
            assertTrue(
                summaryElapsedMs < 2000L,
                "Expected < 2000ms for /api/v1/reports/summary with 1000 requests, took ${summaryElapsedMs}ms",
            )

            var requestsBody = ""
            val requestsElapsedMs =
                measureTimeMillis {
                    val resp =
                        client.get("/api/v1/reports/requests?page=1&size=20") {
                            header(HttpHeaders.Authorization, "Bearer $coordToken")
                        }
                    assertEquals(HttpStatusCode.OK, resp.status)
                    requestsBody = resp.bodyAsText()
                }
            assertTrue(
                requestsElapsedMs < 2000L,
                "Expected < 2000ms for /api/v1/reports/requests with 1000 requests, took ${requestsElapsedMs}ms",
            )

            val summaryDto = strictJson.decodeFromString<CoordinationReportSummaryDto>(summaryBody)
            val listDto = strictJson.decodeFromString<PagedCoordinationReportRequestsResponseDto>(requestsBody)
            assertEquals(1000L, summaryDto.totalRequests)
            assertEquals(1000L, listDto.totalItems)
            assertEquals(50, listDto.totalPages)
            assertEquals(20, listDto.items.size)
            assertEquals("SOL-2026-01000", listDto.items.first().code)
            assertEquals(summaryDto.totalRequests, summaryDto.byStatus.values.sum())
            assertEquals(summaryDto.totalRequests, summaryDto.byCategory.values.sum())
            assertEquals(
                summaryDto.totalRequests,
                summaryDto.byPriority.values.sum() + summaryDto.unassignedPriorityCount,
            )
            assertEquals(
                summaryDto.totalRequests,
                summaryDto.byAssignee.sumOf { it.totalAssigned } + summaryDto.unassignedCount,
            )
            assertEquals(summaryDto.overdueCount, summaryDto.byAssignee.sumOf { it.overdueAssigned })
        }
}
