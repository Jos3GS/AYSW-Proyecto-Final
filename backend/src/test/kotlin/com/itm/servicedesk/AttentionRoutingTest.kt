package com.itm.servicedesk

import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.repository.ExposedAttentionRepository
import com.itm.servicedesk.repository.RequestCodeSequencesTable
import com.itm.servicedesk.repository.RequestHistoryTable
import com.itm.servicedesk.repository.ServiceRequestsTable
import com.itm.servicedesk.routing.dto.ApiErrorDto
import com.itm.servicedesk.service.AutoCloseService
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AttentionRoutingTest : IntegrationTestBase() {
    private val bogotaOffset = ZoneOffset.of("-05:00")

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
        role: String = "PERSONAL_ATENCION",
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
                      "role": "$role"
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
        solicitanteToken: String,
        subject: String,
    ): String {
        val resp =
            client.post("/api/v1/requests") {
                header(HttpHeaders.Authorization, "Bearer $solicitanteToken")
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "category": "TRAMITE",
                      "subject": "$subject",
                      "description": "Requiero gestión administrativa formal de esta solicitud."
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
    fun `RBAC enforces 401 without JWT and 403 across all 8 attention endpoints`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val fakeClock = FakeTimeProvider(OffsetDateTime.of(2026, 11, 15, 10, 0, 0, 0, bogotaOffset))
            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig, timeProviderOverride = fakeClock)
            }

            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("/api/v1/attention/coordination/pending").status,
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.post("/api/v1/attention/requests/SOL-2026-00001/assign").status,
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.post("/api/v1/attention/requests/SOL-2026-00001/reassign").status,
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("/api/v1/attention/my-assigned").status,
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("/api/v1/attention/requests/SOL-2026-00001").status,
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.post("/api/v1/attention/requests/SOL-2026-00001/interventions").status,
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.post("/api/v1/attention/requests/SOL-2026-00001/escalate").status,
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.post("/api/v1/attention/requests/SOL-2026-00001/respond").status,
            )

            val coordToken = loginUser(client, "coordinador@itm.edu.co", "SeedPassword2026")
            val (solId, solToken) = registerSolicitante(client, "1020304001", "sol1@itm.edu.co")
            val (agentAId, agentAToken) =
                createStaffAndLogin(client, coordToken, "Agente Uno", "70000001", "ag1@itm.edu.co")
            val (_, agentBToken) =
                createStaffAndLogin(client, coordToken, "Agente Dos", "70000002", "ag2@itm.edu.co")

            val code = createRequest(client, solToken, "Caso para RBAC")
            val assignResp =
                client.post("/api/v1/attention/requests/$code/assign") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"assigneeId": "$agentAId", "priority": "ALTA"}""")
                }
            assertEquals(HttpStatusCode.OK, assignResp.status)

            // SOLICITANTE gets 403 on all 8 endpoints
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/api/v1/attention/coordination/pending") {
                        header(HttpHeaders.Authorization, "Bearer $solToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/api/v1/attention/my-assigned") {
                        header(HttpHeaders.Authorization, "Bearer $solToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/api/v1/attention/requests/$code") {
                        header(HttpHeaders.Authorization, "Bearer $solToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .post("/api/v1/attention/requests/$code/assign") {
                        header(HttpHeaders.Authorization, "Bearer $solToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"assigneeId": "$agentAId", "priority": "ALTA"}""")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .post("/api/v1/attention/requests/$code/reassign") {
                        header(HttpHeaders.Authorization, "Bearer $solToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"assigneeId": "$agentAId", "reason": "Motivo suficiente"}""")
                    }.status,
            )

            // PERSONAL_ATENCION gets 403 on coordination endpoints
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/api/v1/attention/coordination/pending") {
                        header(HttpHeaders.Authorization, "Bearer $agentAToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .post("/api/v1/attention/requests/$code/assign") {
                        header(HttpHeaders.Authorization, "Bearer $agentAToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"assigneeId": "$agentAId", "priority": "ALTA"}""")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .post("/api/v1/attention/requests/$code/reassign") {
                        header(HttpHeaders.Authorization, "Bearer $agentAToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"assigneeId": "$agentAId", "reason": "Motivo suficiente"}""")
                    }.status,
            )

            // COORDINADOR gets 403 on my-assigned, interventions, escalate, respond
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/api/v1/attention/my-assigned") {
                        header(HttpHeaders.Authorization, "Bearer $coordToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .post("/api/v1/attention/requests/$code/interventions") {
                        header(HttpHeaders.Authorization, "Bearer $coordToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"comment": "Comentario de coordinador prohibido"}""")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .post("/api/v1/attention/requests/$code/escalate") {
                        header(HttpHeaders.Authorization, "Bearer $coordToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"reason": "Escalamiento de coordinador prohibido"}""")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .post("/api/v1/attention/requests/$code/respond") {
                        header(HttpHeaders.Authorization, "Bearer $coordToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"responseText": "Respuesta definitiva desde coordinador no permitida."}""")
                    }.status,
            )

            // Non-assigned PERSONAL_ATENCION (agentB) gets 403 on detail, interventions, escalate, respond
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .get("/api/v1/attention/requests/$code") {
                        header(HttpHeaders.Authorization, "Bearer $agentBToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .post("/api/v1/attention/requests/$code/interventions") {
                        header(HttpHeaders.Authorization, "Bearer $agentBToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"comment": "Intervención de otro agente no asignado"}""")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .post("/api/v1/attention/requests/$code/escalate") {
                        header(HttpHeaders.Authorization, "Bearer $agentBToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"reason": "Escalamiento de otro agente no asignado"}""")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .post("/api/v1/attention/requests/$code/respond") {
                        header(HttpHeaders.Authorization, "Bearer $agentBToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"responseText": "Respuesta de otro agente no asignado al caso."}""")
                    }.status,
            )

            // Assign rejects SOLICITANTE or unknown assigneeId with 400
            val code2 = createRequest(client, solToken, "Caso rechazo assignee")
            val badAssigneeResp =
                client.post("/api/v1/attention/requests/$code2/assign") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"assigneeId": "$solId", "priority": "MEDIA"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, badAssigneeResp.status)
            assertTrue(
                Json
                    .decodeFromString<ApiErrorDto>(badAssigneeResp.bodyAsText())
                    .details
                    .any { it.field == "assigneeId" },
            )
        }

    @Test
    fun `full lifecycle SLA non-reset on reassign RF-03 privacy overdue non-blocking and Sistema autoclose`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val t0 = OffsetDateTime.now(bogotaOffset).plusMinutes(1).truncatedTo(ChronoUnit.SECONDS)
            val fakeClock = FakeTimeProvider(t0)
            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig, timeProviderOverride = fakeClock)
            }

            val coordToken = loginUser(client, "coordinador@itm.edu.co", "SeedPassword2026")
            val (_, solToken) = registerSolicitante(client, "1020304010", "laura.ciclo@itm.edu.co")
            val (agentAId, agentAToken) =
                createStaffAndLogin(client, coordToken, "Carlos Mejía", "71000001", "carlos@itm.edu.co")
            val (agentBId, agentBToken) =
                createStaffAndLogin(client, coordToken, "Ana Ruiz", "71000002", "ana@itm.edu.co")

            // Create request at t0
            val code = createRequest(client, solToken, "Certificado de notas ciclo completo")

            // Assign at t0 + 1h with MEDIA (72h SLA -> dueAt = t0 + 73h)
            val tAssign = t0.plusHours(1)
            fakeClock.setNow(tAssign)
            val assignResp =
                client.post("/api/v1/attention/requests/$code/assign") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"assigneeId": "$agentAId", "priority": "MEDIA"}""")
                }
            assertEquals(HttpStatusCode.OK, assignResp.status)
            val assignedJson = Json.parseToJsonElement(assignResp.bodyAsText()).jsonObject
            assertEquals("EN_REVISION", assignedJson["status"]!!.jsonPrimitive.content)
            assertEquals("MEDIA", assignedJson["priority"]!!.jsonPrimitive.content)
            assertEquals("Carlos Mejía", assignedJson["assignedToName"]!!.jsonPrimitive.content)
            val firstAssignedAtStr = assignedJson["firstAssignedAt"]!!.jsonPrimitive.content
            val expectedMediaDueAt = tAssign.plusHours(72)
            assertEquals(
                expectedMediaDueAt.toInstant(),
                OffsetDateTime.parse(assignedJson["dueAt"]!!.jsonPrimitive.content).toInstant(),
            )

            // Agent A adds internal intervention
            fakeClock.setNow(tAssign.plusHours(2))
            val intResp =
                client.post("/api/v1/attention/requests/$code/interventions") {
                    header(HttpHeaders.Authorization, "Bearer $agentAToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"comment": "Nota interna confidencial: se validó expediente con admisiones."}""")
                }
            assertEquals(HttpStatusCode.OK, intResp.status)
            assertEquals(
                "EN_REVISION",
                Json
                    .parseToJsonElement(intResp.bodyAsText())
                    .jsonObject["status"]!!
                    .jsonPrimitive.content,
            )

            // Agent A escalates
            fakeClock.setNow(tAssign.plusHours(5))
            val escResp =
                client.post("/api/v1/attention/requests/$code/escalate") {
                    header(HttpHeaders.Authorization, "Bearer $agentAToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"reason": "Motivo interno: requiere validación de decanatura."}""")
                }
            assertEquals(HttpStatusCode.OK, escResp.status)
            assertEquals(
                "ESCALADA",
                Json
                    .parseToJsonElement(escResp.bodyAsText())
                    .jsonObject["status"]!!
                    .jsonPrimitive.content,
            )

            // RF-03 check: applicant endpoint MUST hide INTERVENCION and ESCALAMIENTO comment
            val applicantView =
                client.get("/api/v1/requests/$code") {
                    header(HttpHeaders.Authorization, "Bearer $solToken")
                }
            assertEquals(HttpStatusCode.OK, applicantView.status)
            val applicantText = applicantView.bodyAsText()
            assertFalse(applicantText.contains("Nota interna confidencial"))
            assertFalse(applicantText.contains("Motivo interno: requiere validación"))

            // Coordinator pending inbox contains ESCALADA
            val pendingResp =
                client.get("/api/v1/attention/coordination/pending?status=ESCALADA") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.OK, pendingResp.status)
            val pendingTotal =
                Json
                    .parseToJsonElement(pendingResp.bodyAsText())
                    .jsonObject["totalItems"]!!
                    .jsonPrimitive.int
            assertEquals(1, pendingTotal)

            // Advance clock +30h from tAssign; Coordinator reassigns to Agent B with priority ALTA (24h).
            // SLA invariant: dueAt = firstAssignedAt + 24h (tAssign + 24h, already in past -> isOverdue = true)
            fakeClock.setNow(tAssign.plusHours(30))
            val reassignResp =
                client.post("/api/v1/attention/requests/$code/reassign") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "assigneeId": "$agentBId",
                          "reason": "Se reasigna a Ana Ruiz por especialidad y prioridad urgente.",
                          "priority": "ALTA"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, reassignResp.status)
            val reassignedJson = Json.parseToJsonElement(reassignResp.bodyAsText()).jsonObject
            assertEquals("EN_REVISION", reassignedJson["status"]!!.jsonPrimitive.content)
            assertEquals("ALTA", reassignedJson["priority"]!!.jsonPrimitive.content)
            assertEquals("Ana Ruiz", reassignedJson["assignedToName"]!!.jsonPrimitive.content)
            assertEquals(
                OffsetDateTime.parse(firstAssignedAtStr).toInstant(),
                OffsetDateTime.parse(reassignedJson["firstAssignedAt"]!!.jsonPrimitive.content).toInstant(),
            )
            assertEquals(
                tAssign.plusHours(24).toInstant(),
                OffsetDateTime.parse(reassignedJson["dueAt"]!!.jsonPrimitive.content).toInstant(),
            )
            assertTrue(reassignedJson["isOverdue"]!!.jsonPrimitive.boolean)

            // Agent B checks my-assigned -> sees overdue = true
            val myAssignedResp =
                client.get("/api/v1/attention/my-assigned") {
                    header(HttpHeaders.Authorization, "Bearer $agentBToken")
                }
            assertEquals(HttpStatusCode.OK, myAssignedResp.status)
            val myItems =
                Json
                    .parseToJsonElement(myAssignedResp.bodyAsText())
                    .jsonObject["items"]!!
                    .jsonArray
            assertEquals(1, myItems.size)
            assertTrue(
                myItems
                    .single()
                    .jsonObject["isOverdue"]!!
                    .jsonPrimitive.boolean,
            )

            // Overdue does NOT block intervention or final response
            val overdueIntResp =
                client.post("/api/v1/attention/requests/$code/interventions") {
                    header(HttpHeaders.Authorization, "Bearer $agentBToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"comment": "Se procede con emisión pese a vencimiento de plazo."}""")
                }
            assertEquals(HttpStatusCode.OK, overdueIntResp.status)

            val respondResp =
                client.post("/api/v1/attention/requests/$code/respond") {
                    header(HttpHeaders.Authorization, "Bearer $agentBToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"responseText": "Estimada solicitante, su certificado oficial fue emitido."}""",
                    )
                }
            assertEquals(HttpStatusCode.OK, respondResp.status)
            val respondedJson = Json.parseToJsonElement(respondResp.bodyAsText()).jsonObject
            assertEquals("RESUELTA", respondedJson["status"]!!.jsonPrimitive.content)
            assertFalse(respondedJson["respondedWithinSla"]!!.jsonPrimitive.boolean)
            assertFalse(respondedJson["isOverdue"]!!.jsonPrimitive.boolean)

            // Response is immutable: second respond -> 409 Conflict
            val secondRespondResp =
                client.post("/api/v1/attention/requests/$code/respond") {
                    header(HttpHeaders.Authorization, "Bearer $agentBToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"responseText": "Intento de sobrescribir la respuesta final emitida."}""")
                }
            assertEquals(HttpStatusCode.Conflict, secondRespondResp.status)

            // Advance +24h and run AutoCloseService
            fakeClock.advanceHours(24)
            initTestDatabase(runMigrations = false).use { conn ->
                val repo = ExposedAttentionRepository(conn.database)
                val closedCount = AutoCloseService(repo, fakeClock).closeEligibleRequests()
                assertEquals(1, closedCount)
            }

            // Internal detail exposes full history including INTERVENCION, reasons, and Sistema auto-close
            val internalDetail =
                client.get("/api/v1/attention/requests/$code") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.OK, internalDetail.status)
            val detailJson = Json.parseToJsonElement(internalDetail.bodyAsText()).jsonObject
            assertEquals("CERRADA", detailJson["status"]!!.jsonPrimitive.content)
            assertNotNull(detailJson["closedAt"]!!.jsonPrimitive.content)
            val history = detailJson["history"]!!.jsonArray
            assertEquals(8, history.size)
            assertTrue(history.any { it.jsonObject["action"]!!.jsonPrimitive.content == "INTERVENCION" })
            val autoCloseEntry = history.last().jsonObject
            assertEquals("CIERRE_AUTOMATICO", autoCloseEntry["action"]!!.jsonPrimitive.content)
            assertTrue(autoCloseEntry["actorId"] == null || autoCloseEntry["actorId"] is JsonNull)
            assertEquals("Sistema", autoCloseEntry["actorName"]!!.jsonPrimitive.content)
            assertEquals("SISTEMA", autoCloseEntry["actorRole"]!!.jsonPrimitive.content)
            assertEquals("Cierre automático", autoCloseEntry["comment"]!!.jsonPrimitive.content)
        }

    @Test
    fun `coordination pending orders oldest first under 2s with 1000 requests and concurrent transitions yield 409`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val baseTime = OffsetDateTime.of(2026, 11, 15, 6, 0, 0, 0, bogotaOffset)
            val fakeClock = FakeTimeProvider(baseTime)
            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig, timeProviderOverride = fakeClock)
            }

            val coordToken = loginUser(client, "coordinador@itm.edu.co", "SeedPassword2026")
            val (solId, solToken) = registerSolicitante(client, "1020304020", "perf@itm.edu.co")
            val (agentAId, _) =
                createStaffAndLogin(client, coordToken, "Agente Carga", "72000001", "carga@itm.edu.co")
            val (agentBId, _) =
                createStaffAndLogin(client, coordToken, "Agente Dos Carga", "72000002", "carga2@itm.edu.co")

            // Seed 1000 requests across states
            initTestDatabase(runMigrations = false).use { conn ->
                transaction(conn.database) {
                    val rows =
                        (1..1000).map { i ->
                            val status =
                                when {
                                    i <= 400 -> RequestStatus.ABIERTA
                                    i <= 600 -> RequestStatus.ESCALADA
                                    else -> RequestStatus.EN_REVISION
                                }
                            Triple(i, status, baseTime.plusSeconds(i.toLong()))
                        }
                    ServiceRequestsTable.batchInsert(rows, shouldReturnGeneratedValues = false) { (i, status, ts) ->
                        this[ServiceRequestsTable.id] = UUID.randomUUID()
                        this[ServiceRequestsTable.code] = "SOL-2026-${i.toString().padStart(5, '0')}"
                        this[ServiceRequestsTable.requesterId] = solId
                        this[ServiceRequestsTable.category] = RequestCategory.TRAMITE.name
                        this[ServiceRequestsTable.subject] = "Solicitud carga $i"
                        this[ServiceRequestsTable.description] =
                            "Descripción masiva para prueba de bandeja de coordinación."
                        this[ServiceRequestsTable.status] = status.name
                        if (status != RequestStatus.ABIERTA) {
                            this[ServiceRequestsTable.assignedTo] = agentAId
                            this[ServiceRequestsTable.priority] = RequestPriority.ALTA.name
                            this[ServiceRequestsTable.firstAssignedAt] = ts
                            this[ServiceRequestsTable.assignedAt] = ts
                            this[ServiceRequestsTable.dueAt] = ts.plusHours(24)
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

            var pageBody = ""
            val elapsedMs =
                measureTimeMillis {
                    val resp =
                        client.get("/api/v1/attention/coordination/pending?page=1&size=20") {
                            header(HttpHeaders.Authorization, "Bearer $coordToken")
                        }
                    assertEquals(HttpStatusCode.OK, resp.status)
                    pageBody = resp.bodyAsText()
                }
            assertTrue(elapsedMs < 2000L, "Expected < 2000ms for 1000 requests pending query, took ${elapsedMs}ms")

            val pageJson = Json.parseToJsonElement(pageBody).jsonObject
            assertEquals(600, pageJson["totalItems"]!!.jsonPrimitive.int)
            assertEquals(30, pageJson["totalPages"]!!.jsonPrimitive.int)
            val items = pageJson["items"]!!.jsonArray
            assertEquals(20, items.size)
            assertEquals(
                "SOL-2026-00001",
                items
                    .first()
                    .jsonObject["code"]!!
                    .jsonPrimitive.content,
            )

            // Concurrent assign race on a fresh ABIERTA request -> 1 succeeds (200), others 409 Conflict
            val raceCode = createRequest(client, solToken, "Solicitud carrera concurrente")
            val raceResponses =
                coroutineScope {
                    listOf(agentAId, agentBId, agentAId, agentBId)
                        .map { targetAgent ->
                            async {
                                client.post("/api/v1/attention/requests/$raceCode/assign") {
                                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                                    contentType(ContentType.Application.Json)
                                    setBody("""{"assigneeId": "$targetAgent", "priority": "BAJA"}""")
                                }
                            }
                        }.awaitAll()
                }
            assertEquals(1, raceResponses.count { it.status == HttpStatusCode.OK })
            assertEquals(3, raceResponses.count { it.status == HttpStatusCode.Conflict })

            initTestDatabase(runMigrations = false).use { conn ->
                transaction(conn.database) {
                    val reqId =
                        ServiceRequestsTable
                            .select(ServiceRequestsTable.id)
                            .where { ServiceRequestsTable.code eq raceCode }
                            .single()[ServiceRequestsTable.id]
                    val historyCount =
                        RequestHistoryTable
                            .select(RequestHistoryTable.id)
                            .where { RequestHistoryTable.requestId eq reqId }
                            .count()
                    assertEquals(2L, historyCount)
                }
            }
        }

    @Test
    fun `priority SLAs ALTA BAJA on-time respond reassign without priority agent detail and 404 or 400 validations`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val baseTime = OffsetDateTime.now(bogotaOffset).plusMinutes(1).truncatedTo(ChronoUnit.SECONDS)
            val fakeClock = FakeTimeProvider(baseTime)
            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig, timeProviderOverride = fakeClock)
            }

            val coordToken = loginUser(client, "coordinador@itm.edu.co", "SeedPassword2026")
            val (_, solToken) = registerSolicitante(client, "1020304030", "sla.edge@itm.edu.co")
            val (coordStaffId, _) =
                createStaffAndLogin(
                    client,
                    coordToken,
                    "Segundo Coordinador",
                    "73000000",
                    "coord2@itm.edu.co",
                    role = "COORDINADOR",
                )
            val (agentAId, agentAToken) =
                createStaffAndLogin(client, coordToken, "Agente Alta", "73000001", "ag.alta@itm.edu.co")
            val (agentBId, agentBToken) =
                createStaffAndLogin(client, coordToken, "Agente Baja", "73000002", "ag.baja@itm.edu.co")

            // 404 on unknown code and 400 on unknown JSON field or COORDINADOR assigneeId
            assertEquals(
                HttpStatusCode.NotFound,
                client
                    .get("/api/v1/attention/requests/SOL-2026-99999") {
                        header(HttpHeaders.Authorization, "Bearer $coordToken")
                    }.status,
            )

            val altaCode = createRequest(client, solToken, "Solicitud prioridad ALTA")
            val unknownFieldResp =
                client.post("/api/v1/attention/requests/$altaCode/assign") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"assigneeId": "$agentAId", "priority": "ALTA", "extra": true}""")
                }
            assertEquals(HttpStatusCode.BadRequest, unknownFieldResp.status)

            val coordAssigneeResp =
                client.post("/api/v1/attention/requests/$altaCode/assign") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"assigneeId": "$coordStaffId", "priority": "ALTA"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, coordAssigneeResp.status)

            // ALTA = 24h SLA, respond on time -> respondedWithinSla = true
            val assignAltaResp =
                client.post("/api/v1/attention/requests/$altaCode/assign") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"assigneeId": "$agentAId", "priority": "ALTA"}""")
                }
            assertEquals(HttpStatusCode.OK, assignAltaResp.status)
            val altaJson = Json.parseToJsonElement(assignAltaResp.bodyAsText()).jsonObject
            assertEquals(
                baseTime.plusHours(24).toInstant(),
                OffsetDateTime.parse(altaJson["dueAt"]!!.jsonPrimitive.content).toInstant(),
            )

            // Assigned agent CAN view internal detail
            val agentDetailResp =
                client.get("/api/v1/attention/requests/$altaCode") {
                    header(HttpHeaders.Authorization, "Bearer $agentAToken")
                }
            assertEquals(HttpStatusCode.OK, agentDetailResp.status)

            fakeClock.advanceHours(12)
            val onTimeRespondResp =
                client.post("/api/v1/attention/requests/$altaCode/respond") {
                    header(HttpHeaders.Authorization, "Bearer $agentAToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"responseText": "Solicitud atendida satisfactoriamente dentro de SLA."}""")
                }
            assertEquals(HttpStatusCode.OK, onTimeRespondResp.status)
            assertTrue(
                Json
                    .parseToJsonElement(onTimeRespondResp.bodyAsText())
                    .jsonObject["respondedWithinSla"]!!
                    .jsonPrimitive.boolean,
            )

            // BAJA = 120h SLA, escalate and reassign WITHOUT priority -> keeps BAJA & firstAssignedAt + 120h
            val bajaCode = createRequest(client, solToken, "Solicitud prioridad BAJA")
            val assignBajaTime = fakeClock.now()
            val assignBajaResp =
                client.post("/api/v1/attention/requests/$bajaCode/assign") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"assigneeId": "$agentAId", "priority": "BAJA"}""")
                }
            assertEquals(HttpStatusCode.OK, assignBajaResp.status)
            assertEquals(
                assignBajaTime.plusHours(120).toInstant(),
                OffsetDateTime
                    .parse(
                        Json
                            .parseToJsonElement(assignBajaResp.bodyAsText())
                            .jsonObject["dueAt"]!!
                            .jsonPrimitive.content,
                    ).toInstant(),
            )

            assertEquals(
                HttpStatusCode.OK,
                client
                    .post("/api/v1/attention/requests/$bajaCode/escalate") {
                        header(HttpHeaders.Authorization, "Bearer $agentAToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"reason": "Escalamiento para validar preservación de prioridad BAJA."}""")
                    }.status,
            )

            fakeClock.advanceHours(10)
            val reassignKeepPriorityResp =
                client.post("/api/v1/attention/requests/$bajaCode/reassign") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"assigneeId": "$agentBId", "reason": "Reasignación manteniendo prioridad previa."}""")
                }
            assertEquals(HttpStatusCode.OK, reassignKeepPriorityResp.status)
            val reassignJson = Json.parseToJsonElement(reassignKeepPriorityResp.bodyAsText()).jsonObject
            assertEquals("BAJA", reassignJson["priority"]!!.jsonPrimitive.content)
            assertEquals(
                assignBajaTime.plusHours(120).toInstant(),
                OffsetDateTime.parse(reassignJson["dueAt"]!!.jsonPrimitive.content).toInstant(),
            )
            assertEquals(
                HttpStatusCode.OK,
                client
                    .get("/api/v1/attention/my-assigned?status=EN_REVISION") {
                        header(HttpHeaders.Authorization, "Bearer $agentBToken")
                    }.status,
            )
        }
}
