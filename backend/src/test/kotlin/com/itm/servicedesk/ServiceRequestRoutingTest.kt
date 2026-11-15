package com.itm.servicedesk

import com.itm.servicedesk.domain.model.HistoryAction
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.repository.RequestHistoryTable
import com.itm.servicedesk.repository.ServiceRequestsTable
import com.itm.servicedesk.repository.UsersTable
import com.itm.servicedesk.routing.dto.ApiErrorDto
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
import java.time.ZoneId
import java.util.UUID
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceRequestRoutingTest : IntegrationTestBase() {
    private val bogotaZone = ZoneId.of("America/Bogota")

    private suspend fun registerSolicitante(
        client: HttpClient,
        documentNumber: String,
        email: String,
        fullName: String = "Laura Solicitante",
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
        val token = json["accessToken"]!!.jsonPrimitive.content
        val userId = UUID.fromString(json["user"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        return userId to token
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

    private fun seedRequestRow(
        requesterId: UUID,
        code: String,
        status: RequestStatus,
        createdAt: OffsetDateTime,
        assignedTo: UUID? = null,
        priority: RequestPriority? = null,
        responseText: String? = null,
        respondedAt: OffsetDateTime? = null,
        closedAt: OffsetDateTime? = null,
    ): UUID {
        val requestId = UUID.randomUUID()
        val conn = initTestDatabase(runMigrations = false)
        conn.use {
            transaction(it.database) {
                ServiceRequestsTable.insert { row ->
                    row[id] = requestId
                    row[ServiceRequestsTable.code] = code
                    row[ServiceRequestsTable.requesterId] = requesterId
                    row[category] = RequestCategory.TRAMITE.name
                    row[subject] = "Solicitud sembrada $code"
                    row[description] = "Descripción de solicitud sembrada para pruebas de integración."
                    row[ServiceRequestsTable.status] = status.name
                    row[ServiceRequestsTable.assignedTo] = assignedTo
                    row[ServiceRequestsTable.priority] = priority?.name
                    row[ServiceRequestsTable.responseText] = responseText
                    row[ServiceRequestsTable.respondedAt] = respondedAt
                    row[ServiceRequestsTable.closedAt] = closedAt
                    row[ServiceRequestsTable.createdAt] = createdAt
                    row[updatedAt] = createdAt
                }
            }
        }
        return requestId
    }

    @Test
    fun `POST requests creates ABIERTA request with SOL-AAAA-NNNNN and CREACION history and rejects server fields`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            val (_, token) = registerSolicitante(client, "1020304050", "laura@itm.edu.co")

            val createResp =
                client.post("/api/v1/requests") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "category": "TRAMITE",
                          "subject": "  Solicitud de certificado oficial  ",
                          "description": "  Requiero el certificado oficial de calificaciones del semestre actual.  "
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.Created, createResp.status)
            val bodyJson = Json.parseToJsonElement(createResp.bodyAsText()).jsonObject
            val expectedYear = OffsetDateTime.now(bogotaZone).year
            val code = bodyJson["code"]!!.jsonPrimitive.content
            assertTrue(Regex("^SOL-$expectedYear-\\d{5}$").matches(code))
            assertEquals("TRAMITE", bodyJson["category"]!!.jsonPrimitive.content)
            assertEquals("Solicitud de certificado oficial", bodyJson["subject"]!!.jsonPrimitive.content)
            assertEquals(
                "Requiero el certificado oficial de calificaciones del semestre actual.",
                bodyJson["description"]!!.jsonPrimitive.content,
            )
            assertEquals("ABIERTA", bodyJson["status"]!!.jsonPrimitive.content)
            assertTrue(bodyJson["previousRequestCode"] == null || bodyJson["previousRequestCode"] is JsonNull)
            assertTrue(bodyJson["createdAt"]!!.jsonPrimitive.content.endsWith("-05:00"))
            assertTrue(bodyJson["updatedAt"]!!.jsonPrimitive.content.endsWith("-05:00"))

            val history = bodyJson["history"]!!.jsonArray
            assertEquals(1, history.size)
            val entry = history.single().jsonObject
            assertEquals("CREACION", entry["action"]!!.jsonPrimitive.content)
            assertTrue(entry["previousState"] == null || entry["previousState"] is JsonNull)
            assertEquals("ABIERTA", entry["newState"]!!.jsonPrimitive.content)
            assertTrue(entry["occurredAt"]!!.jsonPrimitive.content.endsWith("-05:00"))

            val serverFieldAttempt =
                client.post("/api/v1/requests") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "category": "TRAMITE",
                          "subject": "Intento con campo prohibido",
                          "description": "Descripción suficientemente larga para pasar longitud.",
                          "status": "RESUELTA"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, serverFieldAttempt.status)
        }

    @Test
    fun `POST requests links RESUELTA or CERRADA own request and rejects invalid previousRequestCode with 400`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            val (ownerId, ownerToken) = registerSolicitante(client, "1020304051", "owner@itm.edu.co")
            val (otherId, _) = registerSolicitante(client, "1020304052", "other@itm.edu.co", "Otro Solicitante")
            val now = OffsetDateTime.now(bogotaZone)

            seedRequestRow(ownerId, "SOL-2025-00001", RequestStatus.RESUELTA, now.minusDays(5))
            seedRequestRow(ownerId, "SOL-2025-00002", RequestStatus.CERRADA, now.minusDays(4))
            seedRequestRow(ownerId, "SOL-2025-00003", RequestStatus.ABIERTA, now.minusDays(3))
            seedRequestRow(ownerId, "SOL-2025-00004", RequestStatus.EN_REVISION, now.minusDays(2))
            seedRequestRow(ownerId, "SOL-2025-00005", RequestStatus.ESCALADA, now.minusDays(2))
            seedRequestRow(ownerId, "SOL-2025-00006", RequestStatus.CANCELADA, now.minusDays(2))
            seedRequestRow(otherId, "SOL-2025-00007", RequestStatus.RESUELTA, now.minusDays(2))

            val linkedResuelta =
                client.post("/api/v1/requests") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "category": "ATENCION_ADMINISTRATIVA",
                          "subject": "Inconformidad con respuesta previa",
                          "description": "No quedé satisfecho con la respuesta recibida en mi solicitud previa.",
                          "previousRequestCode": "SOL-2025-00001"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, linkedResuelta.status)
            assertEquals(
                "SOL-2025-00001",
                Json
                    .parseToJsonElement(linkedResuelta.bodyAsText())
                    .jsonObject["previousRequestCode"]!!
                    .jsonPrimitive.content,
            )

            val linkedCerrada =
                client.post("/api/v1/requests") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "category": "ORIENTACION",
                          "subject": "Reapertura de caso cerrado",
                          "description": "Necesito seguimiento adicional sobre la solicitud que fue cerrada.",
                          "previousRequestCode": "SOL-2025-00002"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, linkedCerrada.status)

            val invalidPrevCodes =
                listOf(
                    "INVALID-CODE",
                    "SOL-2025-99999",
                    "SOL-2025-00003",
                    "SOL-2025-00004",
                    "SOL-2025-00005",
                    "SOL-2025-00006",
                    "SOL-2025-00007",
                )
            invalidPrevCodes.forEach { badPrev ->
                val resp =
                    client.post("/api/v1/requests") {
                        header(HttpHeaders.Authorization, "Bearer $ownerToken")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "category": "TRAMITE",
                              "subject": "Intento vinculación inválida",
                              "description": "Esta solicitud debe fallar por código previo inválido.",
                              "previousRequestCode": "$badPrev"
                            }
                            """.trimIndent(),
                        )
                    }
                assertEquals(HttpStatusCode.BadRequest, resp.status, "Expected 400 for $badPrev")
                val err = Json.decodeFromString<ApiErrorDto>(resp.bodyAsText())
                assertTrue(err.details.any { it.field == "previousRequestCode" })
            }

            val verifyConn = initTestDatabase(runMigrations = false)
            verifyConn.use { conn ->
                transaction(conn.database) {
                    val status1 =
                        ServiceRequestsTable
                            .select(ServiceRequestsTable.status)
                            .where { ServiceRequestsTable.code eq "SOL-2025-00001" }
                            .single()[ServiceRequestsTable.status]
                    val status3 =
                        ServiceRequestsTable
                            .select(ServiceRequestsTable.status)
                            .where { ServiceRequestsTable.code eq "SOL-2025-00003" }
                            .single()[ServiceRequestsTable.status]
                    assertEquals("RESUELTA", status1)
                    assertEquals("ABIERTA", status3)
                }
            }
        }

    @Test
    fun `concurrent POST requests generate unique sequential SOL-AAAA-NNNNN codes without duplicates`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            val (_, token) = registerSolicitante(client, "1020304055", "concurrencia@itm.edu.co")

            val responses =
                coroutineScope {
                    (1..10)
                        .map { idx ->
                            async {
                                client.post("/api/v1/requests") {
                                    header(HttpHeaders.Authorization, "Bearer $token")
                                    contentType(ContentType.Application.Json)
                                    setBody(
                                        """
                                        {
                                          "category": "REQUERIMIENTO_SERVICIO",
                                          "subject": "Solicitud concurrente $idx",
                                          "description": "Descripción suficientemente larga para petición concurrente $idx."
                                        }
                                        """.trimIndent(),
                                    )
                                }
                            }
                        }.awaitAll()
                }

            assertTrue(responses.all { it.status == HttpStatusCode.Created })
            val codes =
                responses
                    .map {
                        Json
                            .parseToJsonElement(it.bodyAsText())
                            .jsonObject["code"]!!
                            .jsonPrimitive.content
                    }.toSet()
            assertEquals(10, codes.size)
        }

    @Test
    fun `RBAC enforces 401 without JWT and 403 for COORDINADOR or PERSONAL_ATENCION and 400 on Table 2 errors`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/requests").status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/requests/my").status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/requests/SOL-2026-00001").status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/requests/SOL-2026-00001/cancel").status)

            val coordToken = loginUser(client, "coordinador@itm.edu.co", "SeedPassword2026")
            val createStaffResp =
                client.post("/api/v1/users/staff") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "Agente Atención",
                          "documentNumber": "71829304",
                          "email": "agente@itm.edu.co",
                          "phone": "3119876543",
                          "password": "StaffPassword2026",
                          "role": "PERSONAL_ATENCION"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, createStaffResp.status)
            val agentToken = loginUser(client, "agente@itm.edu.co", "StaffPassword2026")

            listOf(coordToken, agentToken).forEach { nonApplicantToken ->
                assertEquals(
                    HttpStatusCode.Forbidden,
                    client
                        .post("/api/v1/requests") {
                            header(HttpHeaders.Authorization, "Bearer $nonApplicantToken")
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "category": "TRAMITE",
                                  "subject": "Asunto válido",
                                  "description": "Descripción suficientemente larga para probar RBAC."
                                }
                                """.trimIndent(),
                            )
                        }.status,
                )
                assertEquals(
                    HttpStatusCode.Forbidden,
                    client
                        .get("/api/v1/requests/my") {
                            header(HttpHeaders.Authorization, "Bearer $nonApplicantToken")
                        }.status,
                )
                assertEquals(
                    HttpStatusCode.Forbidden,
                    client
                        .get("/api/v1/requests/SOL-2026-00001") {
                            header(HttpHeaders.Authorization, "Bearer $nonApplicantToken")
                        }.status,
                )
                assertEquals(
                    HttpStatusCode.Forbidden,
                    client
                        .post("/api/v1/requests/SOL-2026-00001/cancel") {
                            header(HttpHeaders.Authorization, "Bearer $nonApplicantToken")
                        }.status,
                )
            }

            val (_, solicitanteToken) = registerSolicitante(client, "1020304056", "val@itm.edu.co")
            val invalidBodyResp =
                client.post("/api/v1/requests") {
                    header(HttpHeaders.Authorization, "Bearer $solicitanteToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "category": "OTRA",
                          "subject": "abc",
                          "description": "corta"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, invalidBodyResp.status)
            val err = Json.decodeFromString<ApiErrorDto>(invalidBodyResp.bodyAsText())
            assertEquals(setOf("category", "subject", "description"), err.details.map { it.field }.toSet())
        }

    @Test
    fun `GET requests my paginates max 20 filters by status and enforces performance under 2s with 1000 requests`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            val (ownerId, ownerToken) = registerSolicitante(client, "1020304057", "lista@itm.edu.co")
            val (otherId, _) = registerSolicitante(client, "1020304058", "otro.lista@itm.edu.co", "Otro Usuario")
            val baseTime = OffsetDateTime.now(bogotaZone).minusDays(10)

            val verifyConn = initTestDatabase(runMigrations = false)
            verifyConn.use { conn ->
                transaction(conn.database) {
                    val rows =
                        (1..1000).map { i ->
                            val status =
                                if (i <= 700) {
                                    RequestStatus.ABIERTA
                                } else {
                                    RequestStatus.RESUELTA
                                }
                            Triple(i, status, baseTime.plusSeconds(i.toLong()))
                        }
                    ServiceRequestsTable.batchInsert(rows, shouldReturnGeneratedValues = false) { (i, status, ts) ->
                        this[ServiceRequestsTable.id] = UUID.randomUUID()
                        this[ServiceRequestsTable.code] = "SOL-2026-${i.toString().padStart(5, '0')}"
                        this[ServiceRequestsTable.requesterId] = ownerId
                        this[ServiceRequestsTable.category] = RequestCategory.TRAMITE.name
                        this[ServiceRequestsTable.subject] = "Solicitud masiva número $i"
                        this[ServiceRequestsTable.description] =
                            "Descripción de solicitud masiva número $i para validación de rendimiento."
                        this[ServiceRequestsTable.status] = status.name
                        this[ServiceRequestsTable.createdAt] = ts
                        this[ServiceRequestsTable.updatedAt] = ts
                    }
                    ServiceRequestsTable.insert { row ->
                        row[id] = UUID.randomUUID()
                        row[code] = "SOL-2026-90001"
                        row[requesterId] = otherId
                        row[category] = RequestCategory.ORIENTACION.name
                        row[subject] = "Solicitud de otro usuario"
                        row[description] = "No debe aparecer en el listado del solicitante principal."
                        row[status] = RequestStatus.ABIERTA.name
                        row[createdAt] = baseTime.plusDays(1)
                        row[updatedAt] = baseTime.plusDays(1)
                    }
                }
            }

            var page1Body = ""
            val elapsedMs =
                measureTimeMillis {
                    val resp =
                        client.get("/api/v1/requests/my?page=1&size=20") {
                            header(HttpHeaders.Authorization, "Bearer $ownerToken")
                        }
                    assertEquals(HttpStatusCode.OK, resp.status)
                    page1Body = resp.bodyAsText()
                }
            assertTrue(elapsedMs < 2000L, "Expected < 2000ms for 1000 requests query, took ${elapsedMs}ms")

            val page1Json = Json.parseToJsonElement(page1Body).jsonObject
            assertEquals(1, page1Json["page"]!!.jsonPrimitive.int)
            assertEquals(20, page1Json["size"]!!.jsonPrimitive.int)
            assertEquals(1000, page1Json["totalItems"]!!.jsonPrimitive.int)
            assertEquals(50, page1Json["totalPages"]!!.jsonPrimitive.int)
            val items = page1Json["items"]!!.jsonArray
            assertEquals(20, items.size)
            assertEquals(
                "SOL-2026-01000",
                items
                    .first()
                    .jsonObject["code"]!!
                    .jsonPrimitive.content,
            )

            val filteredResp =
                client.get("/api/v1/requests/my?page=2&size=10&status=RESUELTA") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                }
            assertEquals(HttpStatusCode.OK, filteredResp.status)
            val filteredJson = Json.parseToJsonElement(filteredResp.bodyAsText()).jsonObject
            assertEquals(300, filteredJson["totalItems"]!!.jsonPrimitive.int)
            assertEquals(30, filteredJson["totalPages"]!!.jsonPrimitive.int)
            assertEquals(10, filteredJson["items"]!!.jsonArray.size)
            filteredJson["items"]!!.jsonArray.forEach { elem ->
                assertEquals("RESUELTA", elem.jsonObject["status"]!!.jsonPrimitive.content)
            }

            assertEquals(
                HttpStatusCode.BadRequest,
                client
                    .get("/api/v1/requests/my?size=21") {
                        header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                client
                    .get("/api/v1/requests/my?page=0") {
                        header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                client
                    .get("/api/v1/requests/my?status=UNKNOWN") {
                        header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    }.status,
            )
        }

    @Test
    fun `GET requests code enforces RF-03 privacy returns final response and handles 403 and 404`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            val (ownerId, ownerToken) = registerSolicitante(client, "1020304059", "detalle@itm.edu.co")
            val (_, otherToken) = registerSolicitante(client, "1020304060", "intruso@itm.edu.co", "Intruso")
            val now = OffsetDateTime.now(bogotaZone)

            val coordId =
                initTestDatabase(runMigrations = false).use { conn ->
                    transaction(conn.database) {
                        UsersTable
                            .select(UsersTable.id)
                            .where { UsersTable.email eq "coordinador@itm.edu.co" }
                            .single()[UsersTable.id]
                    }
                }

            val reqId =
                seedRequestRow(
                    requesterId = ownerId,
                    code = "SOL-2026-00077",
                    status = RequestStatus.CERRADA,
                    createdAt = now.minusDays(2),
                    assignedTo = coordId,
                    priority = RequestPriority.ALTA,
                    responseText = "Su certificado oficial fue emitido y enviado a registro académico.",
                    respondedAt = now.minusDays(1),
                    closedAt = now.minusHours(1),
                )

            initTestDatabase(runMigrations = false).use { conn ->
                transaction(conn.database) {
                    val entries =
                        listOf(
                            Triple(HistoryAction.CREACION, null to RequestStatus.ABIERTA, "Solicitud registrada."),
                            Triple(
                                HistoryAction.ASIGNACION,
                                RequestStatus.ABIERTA to RequestStatus.EN_REVISION,
                                "Solicitud asignada para revisión.",
                            ),
                            Triple(
                                HistoryAction.INTERVENCION,
                                RequestStatus.EN_REVISION to RequestStatus.EN_REVISION,
                                "Nota interna secreta del agente.",
                            ),
                            Triple(
                                HistoryAction.ESCALAMIENTO,
                                RequestStatus.EN_REVISION to RequestStatus.ESCALADA,
                                "Motivo interno confidencial de escalamiento.",
                            ),
                            Triple(
                                HistoryAction.REASIGNACION,
                                RequestStatus.ESCALADA to RequestStatus.EN_REVISION,
                                "Nota interna confidencial de reasignación.",
                            ),
                            Triple(
                                HistoryAction.RESPUESTA,
                                RequestStatus.EN_REVISION to RequestStatus.RESUELTA,
                                "Respuesta final registrada.",
                            ),
                            Triple(
                                HistoryAction.CIERRE_AUTOMATICO,
                                RequestStatus.RESUELTA to RequestStatus.CERRADA,
                                "Cierre automático tras 24 horas.",
                            ),
                        )
                    entries.forEachIndexed { idx, (action, states, commentText) ->
                        RequestHistoryTable.insert { row ->
                            row[id] = UUID.randomUUID()
                            row[requestId] = reqId
                            row[actorId] = if (action == HistoryAction.CIERRE_AUTOMATICO) null else coordId
                            row[actorRole] =
                                if (action == HistoryAction.CIERRE_AUTOMATICO) {
                                    HistoryActorRole.SISTEMA.name
                                } else {
                                    HistoryActorRole.COORDINADOR.name
                                }
                            row[RequestHistoryTable.action] = action.name
                            row[previousState] = states.first?.name
                            row[newState] = states.second.name
                            row[comment] = commentText
                            row[createdAt] = now.minusDays(2).plusHours(idx.toLong())
                        }
                    }
                }
            }

            val detailResp =
                client.get("/api/v1/requests/SOL-2026-00077") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                }
            assertEquals(HttpStatusCode.OK, detailResp.status)
            val bodyText = detailResp.bodyAsText()
            assertFalse(bodyText.contains("Nota interna secreta del agente"))
            assertFalse(bodyText.contains("Motivo interno confidencial de escalamiento"))
            assertFalse(bodyText.contains("Nota interna confidencial de reasignación"))

            val detailJson = Json.parseToJsonElement(bodyText).jsonObject
            assertEquals("CERRADA", detailJson["status"]!!.jsonPrimitive.content)
            assertEquals(
                "Su certificado oficial fue emitido y enviado a registro académico.",
                detailJson["responseText"]!!.jsonPrimitive.content,
            )
            assertTrue(detailJson["respondedAt"]!!.jsonPrimitive.content.endsWith("-05:00"))
            assertTrue(detailJson["closedAt"]!!.jsonPrimitive.content.endsWith("-05:00"))

            val historyArray = detailJson["history"]!!.jsonArray
            assertEquals(6, historyArray.size)
            assertTrue(historyArray.none { it.jsonObject["action"]!!.jsonPrimitive.content == "INTERVENCION" })
            val escEntry = historyArray.first { it.jsonObject["action"]!!.jsonPrimitive.content == "ESCALAMIENTO" }
            val reasigEntry = historyArray.first { it.jsonObject["action"]!!.jsonPrimitive.content == "REASIGNACION" }
            assertTrue(escEntry.jsonObject["comment"] == null || escEntry.jsonObject["comment"] is JsonNull)
            assertTrue(reasigEntry.jsonObject["comment"] == null || reasigEntry.jsonObject["comment"] is JsonNull)

            val forbiddenResp =
                client.get("/api/v1/requests/SOL-2026-00077") {
                    header(HttpHeaders.Authorization, "Bearer $otherToken")
                }
            assertEquals(HttpStatusCode.Forbidden, forbiddenResp.status)

            val notFoundResp =
                client.get("/api/v1/requests/SOL-2026-99999") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                }
            assertEquals(HttpStatusCode.NotFound, notFoundResp.status)
            val notFoundErr = Json.decodeFromString<ApiErrorDto>(notFoundResp.bodyAsText())
            assertEquals("Solicitud no encontrada.", notFoundErr.message)
        }

    @Test
    fun `POST requests cancel transitions ABIERTA to CANCELADA handles 403 404 409 and concurrent cancels`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            val (ownerId, ownerToken) = registerSolicitante(client, "1020304061", "cancel@itm.edu.co")
            val (_, otherToken) = registerSolicitante(client, "1020304062", "otro.cancel@itm.edu.co", "Otro Cancel")
            val now = OffsetDateTime.now(bogotaZone)

            val createResp =
                client.post("/api/v1/requests") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "category": "TRAMITE",
                          "subject": "Solicitud para cancelar",
                          "description": "Esta solicitud será cancelada por el usuario propietario."
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, createResp.status)
            val codeToCancel =
                Json
                    .parseToJsonElement(createResp.bodyAsText())
                    .jsonObject["code"]!!
                    .jsonPrimitive.content

            val longReasonResp =
                client.post("/api/v1/requests/$codeToCancel/cancel") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"reason": "${"X".repeat(501)}"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, longReasonResp.status)

            val forbiddenCancel =
                client.post("/api/v1/requests/$codeToCancel/cancel") {
                    header(HttpHeaders.Authorization, "Bearer $otherToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"reason": "Intento no autorizado"}""")
                }
            assertEquals(HttpStatusCode.Forbidden, forbiddenCancel.status)

            val cancelOk =
                client.post("/api/v1/requests/$codeToCancel/cancel") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"reason": "Ya completé el trámite en taquilla."}""")
                }
            assertEquals(HttpStatusCode.OK, cancelOk.status)
            val cancelledJson = Json.parseToJsonElement(cancelOk.bodyAsText()).jsonObject
            assertEquals("CANCELADA", cancelledJson["status"]!!.jsonPrimitive.content)
            val lastHist = cancelledJson["history"]!!.jsonArray.last().jsonObject
            assertEquals("CANCELACION", lastHist["action"]!!.jsonPrimitive.content)
            assertEquals("ABIERTA", lastHist["previousState"]!!.jsonPrimitive.content)
            assertEquals("CANCELADA", lastHist["newState"]!!.jsonPrimitive.content)
            assertEquals("Ya completé el trámite en taquilla.", lastHist["comment"]!!.jsonPrimitive.content)

            val repeatCancel =
                client.post("/api/v1/requests/$codeToCancel/cancel") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                }
            assertEquals(HttpStatusCode.Conflict, repeatCancel.status)
            assertEquals(
                "Solo es posible cancelar una solicitud que se encuentre en estado Abierta.",
                Json.decodeFromString<ApiErrorDto>(repeatCancel.bodyAsText()).message,
            )

            seedRequestRow(ownerId, "SOL-2026-00801", RequestStatus.EN_REVISION, now)
            seedRequestRow(ownerId, "SOL-2026-00802", RequestStatus.ESCALADA, now)
            seedRequestRow(ownerId, "SOL-2026-00803", RequestStatus.RESUELTA, now)
            seedRequestRow(ownerId, "SOL-2026-00804", RequestStatus.CERRADA, now)

            listOf("SOL-2026-00801", "SOL-2026-00802", "SOL-2026-00803", "SOL-2026-00804").forEach { code ->
                val conflictResp =
                    client.post("/api/v1/requests/$code/cancel") {
                        header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    }
                assertEquals(HttpStatusCode.Conflict, conflictResp.status)
            }

            val notFoundCancel =
                client.post("/api/v1/requests/SOL-2026-99999/cancel") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                }
            assertEquals(HttpStatusCode.NotFound, notFoundCancel.status)
            assertEquals(
                "Solicitud no encontrada.",
                Json.decodeFromString<ApiErrorDto>(notFoundCancel.bodyAsText()).message,
            )

            val secondCreateResp =
                client.post("/api/v1/requests") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "category": "ORIENTACION",
                          "subject": "Solicitud para cancelación concurrente",
                          "description": "Validando concurrencia en cancelación simultánea de solicitud."
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, secondCreateResp.status)
            val raceCode =
                Json
                    .parseToJsonElement(secondCreateResp.bodyAsText())
                    .jsonObject["code"]!!
                    .jsonPrimitive.content

            val raceResponses =
                coroutineScope {
                    (1..5)
                        .map {
                            async {
                                client.post("/api/v1/requests/$raceCode/cancel") {
                                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                                }
                            }
                        }.awaitAll()
                }
            assertEquals(1, raceResponses.count { it.status == HttpStatusCode.OK })
            assertEquals(4, raceResponses.count { it.status == HttpStatusCode.Conflict })
        }
}
