package com.itm.servicedesk

import com.itm.servicedesk.routing.dto.ApiErrorDto
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaffRoutingTest : IntegrationTestBase() {
    @Test
    fun `POST users staff creates PERSONAL_ATENCION and COORDINADOR and allows immediate login`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            val coordLogin =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "coordinador@itm.edu.co", "password": "SeedPassword2026"}""")
                }
            assertEquals(HttpStatusCode.OK, coordLogin.status)
            val coordToken =
                Json
                    .parseToJsonElement(coordLogin.bodyAsText())
                    .jsonObject["accessToken"]!!
                    .jsonPrimitive.content

            val createAgentResp =
                client.post("/api/v1/users/staff") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "Carlos Andrés Mejía",
                          "documentNumber": "71829304",
                          "email": "Carlos.Mejia@itm.edu.co",
                          "phone": "3119876543",
                          "password": "StaffPassword2026",
                          "role": "PERSONAL_ATENCION"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, createAgentResp.status)
            val agentJson = Json.parseToJsonElement(createAgentResp.bodyAsText()).jsonObject
            assertEquals("carlos.mejia@itm.edu.co", agentJson["email"]!!.jsonPrimitive.content)
            assertEquals("PERSONAL_ATENCION", agentJson["role"]!!.jsonPrimitive.content)
            assertNull(agentJson["accessToken"])
            assertNull(agentJson["passwordHash"])

            val agentLogin =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "carlos.mejia@itm.edu.co", "password": "StaffPassword2026"}""")
                }
            assertEquals(HttpStatusCode.OK, agentLogin.status)

            val duplicateResp =
                client.post("/api/v1/users/staff") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "Otro Agente",
                          "documentNumber": "71829304",
                          "email": "otro@itm.edu.co",
                          "phone": "3119876599",
                          "password": "StaffPassword2026",
                          "role": "PERSONAL_ATENCION"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Conflict, duplicateResp.status)
        }

    @Test
    fun `RBAC enforces 401 without JWT 403 for SOLICITANTE or PERSONAL_ATENCION and 400 for invalid staff role`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/users/staff").status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/users/staff").status)

            val regSolicitante =
                client.post("/api/v1/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "Laura Solicitante",
                          "documentNumber": "1020304050",
                          "email": "laura@itm.edu.co",
                          "phone": "3001234567",
                          "password": "ClaveSegura2026"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, regSolicitante.status)
            val solicitanteToken =
                Json
                    .parseToJsonElement(regSolicitante.bodyAsText())
                    .jsonObject["accessToken"]!!
                    .jsonPrimitive.content

            val solicitanteGet =
                client.get("/api/v1/users/staff") {
                    header(HttpHeaders.Authorization, "Bearer $solicitanteToken")
                }
            assertEquals(HttpStatusCode.Forbidden, solicitanteGet.status)
            assertEquals("FORBIDDEN", Json.decodeFromString<ApiErrorDto>(solicitanteGet.bodyAsText()).error)

            val solicitantePost =
                client.post("/api/v1/users/staff") {
                    header(HttpHeaders.Authorization, "Bearer $solicitanteToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "Intento Staff",
                          "documentNumber": "88990011",
                          "email": "intento@itm.edu.co",
                          "phone": "3112223344",
                          "password": "StaffPassword2026",
                          "role": "PERSONAL_ATENCION"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Forbidden, solicitantePost.status)

            val coordLogin =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "coordinador@itm.edu.co", "password": "SeedPassword2026"}""")
                }
            val coordToken =
                Json
                    .parseToJsonElement(coordLogin.bodyAsText())
                    .jsonObject["accessToken"]!!
                    .jsonPrimitive.content

            val invalidRoleResp =
                client.post("/api/v1/users/staff") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "Nuevo Solicitante",
                          "documentNumber": "88990022",
                          "email": "solicitante.staff@itm.edu.co",
                          "phone": "3112223355",
                          "password": "StaffPassword2026",
                          "role": "SOLICITANTE"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, invalidRoleResp.status)
            val invalidRoleErr = Json.decodeFromString<ApiErrorDto>(invalidRoleResp.bodyAsText())
            assertTrue(invalidRoleErr.details.any { it.field == "role" })

            val createAgent =
                client.post("/api/v1/users/staff") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "Agente Atención",
                          "documentNumber": "77665544",
                          "email": "agente@itm.edu.co",
                          "phone": "3123334455",
                          "password": "StaffPassword2026",
                          "role": "PERSONAL_ATENCION"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, createAgent.status)

            val agentLogin =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "agente@itm.edu.co", "password": "StaffPassword2026"}""")
                }
            val agentToken =
                Json
                    .parseToJsonElement(agentLogin.bodyAsText())
                    .jsonObject["accessToken"]!!
                    .jsonPrimitive.content

            val agentGetStaff =
                client.get("/api/v1/users/staff") {
                    header(HttpHeaders.Authorization, "Bearer $agentToken")
                }
            assertEquals(HttpStatusCode.Forbidden, agentGetStaff.status)

            val agentPostStaff =
                client.post("/api/v1/users/staff") {
                    header(HttpHeaders.Authorization, "Bearer $agentToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "Otro Staff",
                          "documentNumber": "66554433",
                          "email": "otrostaff@itm.edu.co",
                          "phone": "3123334466",
                          "password": "StaffPassword2026",
                          "role": "PERSONAL_ATENCION"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Forbidden, agentPostStaff.status)
        }

    @Test
    fun `GET users staff paginates max 20 excludes SOLICITANTE filters by role and validates params`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            client.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "fullName": "Solicitante Excluido",
                      "documentNumber": "90000001",
                      "email": "solicitante.excluido@itm.edu.co",
                      "phone": "3009000001",
                      "password": "ClaveSegura2026"
                    }
                    """.trimIndent(),
                )
            }

            val coordLogin =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "coordinador@itm.edu.co", "password": "SeedPassword2026"}""")
                }
            val coordToken =
                Json
                    .parseToJsonElement(coordLogin.bodyAsText())
                    .jsonObject["accessToken"]!!
                    .jsonPrimitive.content

            for (i in 1..22) {
                val role = if (i % 2 == 0) "COORDINADOR" else "PERSONAL_ATENCION"
                val resp =
                    client.post("/api/v1/users/staff") {
                        header(HttpHeaders.Authorization, "Bearer $coordToken")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "fullName": "Miembro Staff $i",
                              "documentNumber": "${70000000 + i}",
                              "email": "staff$i@itm.edu.co",
                              "phone": "31000000${i.toString().padStart(2, '0')}",
                              "password": "StaffPassword2026",
                              "role": "$role"
                            }
                            """.trimIndent(),
                        )
                    }
                // Note: "Miembro Staff $i" contains digits which should fail fullName regex!
                // Use alphabetic suffix instead in valid creation below
                assertEquals(HttpStatusCode.BadRequest, resp.status)
            }

            val letters = ('A'..'Z').toList()
            for (i in 1..22) {
                val role = if (i % 2 == 0) "COORDINADOR" else "PERSONAL_ATENCION"
                val suffix = "${letters[(i - 1) / 26]}${letters[(i - 1) % 26]}"
                val resp =
                    client.post("/api/v1/users/staff") {
                        header(HttpHeaders.Authorization, "Bearer $coordToken")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "fullName": "Miembro Staff $suffix",
                              "documentNumber": "${70000000 + i}",
                              "email": "staff$i@itm.edu.co",
                              "phone": "31000000${i.toString().padStart(2, '0')}",
                              "password": "StaffPassword2026",
                              "role": "$role"
                            }
                            """.trimIndent(),
                        )
                    }
                assertEquals(HttpStatusCode.Created, resp.status)
            }

            val page1Resp =
                client.get("/api/v1/users/staff") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.OK, page1Resp.status)
            val page1Json = Json.parseToJsonElement(page1Resp.bodyAsText()).jsonObject
            val page1Items = page1Json["items"]!!.jsonArray
            assertEquals(20, page1Items.size)
            assertEquals(1, page1Json["page"]!!.jsonPrimitive.int)
            assertEquals(20, page1Json["size"]!!.jsonPrimitive.int)
            assertEquals(23, page1Json["totalItems"]!!.jsonPrimitive.int)
            assertEquals(2, page1Json["totalPages"]!!.jsonPrimitive.int)

            page1Items.forEach { elem ->
                val obj = elem.jsonObject
                val role = obj["role"]!!.jsonPrimitive.content
                assertTrue(role == "PERSONAL_ATENCION" || role == "COORDINADOR")
                assertNull(obj["passwordHash"])
                assertNull(obj["documentNumber"])
                assertNull(obj["phone"])
            }

            val page2Resp =
                client.get("/api/v1/users/staff?page=2&size=20") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.OK, page2Resp.status)
            val page2Items = Json.parseToJsonElement(page2Resp.bodyAsText()).jsonObject["items"]!!.jsonArray
            assertEquals(3, page2Items.size)

            val filteredResp =
                client.get("/api/v1/users/staff?role=PERSONAL_ATENCION&size=10") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.OK, filteredResp.status)
            val filteredJson = Json.parseToJsonElement(filteredResp.bodyAsText()).jsonObject
            assertEquals(11, filteredJson["totalItems"]!!.jsonPrimitive.int)
            assertEquals(2, filteredJson["totalPages"]!!.jsonPrimitive.int)
            filteredJson["items"]!!.jsonArray.forEach { elem ->
                assertEquals("PERSONAL_ATENCION", elem.jsonObject["role"]!!.jsonPrimitive.content)
            }

            val emptyPageResp =
                client.get("/api/v1/users/staff?page=99") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.OK, emptyPageResp.status)
            assertEquals(
                0,
                Json
                    .parseToJsonElement(emptyPageResp.bodyAsText())
                    .jsonObject["items"]!!
                    .jsonArray.size,
            )

            val invalidSizeResp =
                client.get("/api/v1/users/staff?size=21") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.BadRequest, invalidSizeResp.status)

            val solicitanteFilterResp =
                client.get("/api/v1/users/staff?role=SOLICITANTE") {
                    header(HttpHeaders.Authorization, "Bearer $coordToken")
                }
            assertEquals(HttpStatusCode.BadRequest, solicitanteFilterResp.status)
        }
}
