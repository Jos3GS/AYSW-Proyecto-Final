package com.itm.servicedesk

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.repository.UsersTable
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRoutingTest : IntegrationTestBase() {
    @Test
    fun `POST register creates SOLICITANTE with BCrypt cost 12 and returns 201 with 1h JWT`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            val registerResp =
                client.post("/api/v1/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "  Laura Gómez Pérez ",
                          "documentNumber": "1020304050",
                          "email": "Laura.Gomez@Correo.ITM.edu.co",
                          "phone": "3001234567",
                          "password": "ClaveSegura2026"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.Created, registerResp.status)
            val json = Json.parseToJsonElement(registerResp.bodyAsText()).jsonObject
            val accessToken = json["accessToken"]!!.jsonPrimitive.content
            assertEquals("Bearer", json["tokenType"]!!.jsonPrimitive.content)
            assertEquals(3600, json["expiresInSeconds"]!!.jsonPrimitive.int)
            assertTrue(json["expiresAt"]!!.jsonPrimitive.content.endsWith("-05:00"))

            val userObj = json["user"]!!.jsonObject
            assertEquals("Laura Gómez Pérez", userObj["fullName"]!!.jsonPrimitive.content)
            assertEquals("1020304050", userObj["documentNumber"]!!.jsonPrimitive.content)
            assertEquals("laura.gomez@correo.itm.edu.co", userObj["email"]!!.jsonPrimitive.content)
            assertEquals("3001234567", userObj["phone"]!!.jsonPrimitive.content)
            assertEquals("SOLICITANTE", userObj["role"]!!.jsonPrimitive.content)
            assertTrue(userObj["createdAt"]!!.jsonPrimitive.content.endsWith("-05:00"))
            assertNull(userObj["passwordHash"])
            assertNull(userObj["password"])

            val meResp =
                client.get("/api/v1/auth/me") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, meResp.status)
            val meJson = Json.parseToJsonElement(meResp.bodyAsText()).jsonObject
            assertEquals("laura.gomez@correo.itm.edu.co", meJson["email"]!!.jsonPrimitive.content)
            assertEquals("SOLICITANTE", meJson["role"]!!.jsonPrimitive.content)

            val verifyConn = initTestDatabase(runMigrations = false)
            verifyConn.use { conn ->
                val storedHash =
                    transaction(conn.database) {
                        UsersTable
                            .select(UsersTable.passwordHash)
                            .where { UsersTable.email eq "laura.gomez@correo.itm.edu.co" }
                            .single()[UsersTable.passwordHash]
                    }
                assertTrue(storedHash.startsWith("\$2a\$12\$"))
                assertTrue(BCrypt.verifyer().verify("ClaveSegura2026".toCharArray(), storedHash).verified)
            }
        }

    @Test
    fun `POST register rejects extra role field with 400 and accumulates Table 1 validation errors`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig, seedOnStartup = false)
            }

            val roleEscalationResp =
                client.post("/api/v1/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "Atacante Privilegios",
                          "documentNumber": "1099887766",
                          "email": "atacante@itm.edu.co",
                          "phone": "3009988776",
                          "password": "ClaveSegura2026",
                          "role": "COORDINADOR"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, roleEscalationResp.status)
            val escalationErr = Json.decodeFromString<ApiErrorDto>(roleEscalationResp.bodyAsText())
            assertEquals("MALFORMED_REQUEST", escalationErr.error)

            val invalidFieldsResp =
                client.post("/api/v1/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "12",
                          "documentNumber": "abc",
                          "email": "bad-email",
                          "phone": "12",
                          "password": "short"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, invalidFieldsResp.status)
            val validationErr = Json.decodeFromString<ApiErrorDto>(invalidFieldsResp.bodyAsText())
            assertEquals("VALIDATION_ERROR", validationErr.error)
            assertEquals(
                setOf("fullName", "documentNumber", "email", "phone", "password"),
                validationErr.details.map { it.field }.toSet(),
            )
        }

    @Test
    fun `POST register returns 409 on duplicate email or documentNumber including concurrent requests`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig, seedOnStartup = false)
            }

            val firstPayload =
                """
                {
                  "fullName": "Laura Gómez",
                  "documentNumber": "1020304050",
                  "email": "laura@itm.edu.co",
                  "phone": "3001234567",
                  "password": "ClaveSegura2026"
                }
                """.trimIndent()

            val first =
                client.post("/api/v1/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(firstPayload)
                }
            assertEquals(HttpStatusCode.Created, first.status)

            val duplicateEmail =
                client.post("/api/v1/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "fullName": "Otra Persona",
                          "documentNumber": "9988776655",
                          "email": "LAURA@ITM.EDU.CO",
                          "phone": "3009998877",
                          "password": "ClaveSegura2026"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Conflict, duplicateEmail.status)
            assertEquals("CONFLICT", Json.decodeFromString<ApiErrorDto>(duplicateEmail.bodyAsText()).error)

            val concurrentStatuses =
                coroutineScope {
                    (1..2)
                        .map { index ->
                            async {
                                client
                                    .post("/api/v1/auth/register") {
                                        contentType(ContentType.Application.Json)
                                        setBody(
                                            """
                                            {
                                              "fullName": "Usuario Concurrente",
                                              "documentNumber": "5544332211",
                                              "email": "concurrente$index@itm.edu.co",
                                              "phone": "3012223344",
                                              "password": "ClaveSegura2026"
                                            }
                                            """.trimIndent(),
                                        )
                                    }.status
                            }
                        }.awaitAll()
                }

            assertEquals(
                setOf(HttpStatusCode.Created, HttpStatusCode.Conflict),
                concurrentStatuses.toSet(),
            )
        }

    @Test
    fun `POST login emits fresh unique jti each time handles invalid credentials and inactive user`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            val login1 =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "email": "COORDINADOR@ITM.EDU.CO",
                          "password": "SeedPassword2026"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, login1.status)
            val token1 =
                Json
                    .parseToJsonElement(login1.bodyAsText())
                    .jsonObject["accessToken"]!!
                    .jsonPrimitive.content

            val login2 =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "email": "coordinador@itm.edu.co",
                          "password": "SeedPassword2026"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, login2.status)
            val body2 = Json.parseToJsonElement(login2.bodyAsText()).jsonObject
            val token2 = body2["accessToken"]!!.jsonPrimitive.content
            assertEquals(3600, body2["expiresInSeconds"]!!.jsonPrimitive.int)
            assertEquals(UserRole.COORDINADOR.name, body2["user"]!!.jsonObject["role"]!!.jsonPrimitive.content)

            val jti1 = JWT.decode(token1).id
            val jti2 = JWT.decode(token2).id
            assertNotEquals(jti1, jti2)
            assertNotEquals(token1, token2)

            val unknownEmail =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "noexiste@itm.edu.co", "password": "ClaveSegura2026"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, unknownEmail.status)
            val unknownErr = Json.decodeFromString<ApiErrorDto>(unknownEmail.bodyAsText())
            assertEquals("Credenciales inválidas.", unknownErr.message)

            val wrongPassword =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "coordinador@itm.edu.co", "password": "WrongPassword2026"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
            val wrongErr = Json.decodeFromString<ApiErrorDto>(wrongPassword.bodyAsText())
            assertEquals("Credenciales inválidas.", wrongErr.message)

            val blankLogin =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "", "password": ""}""")
                }
            assertEquals(HttpStatusCode.BadRequest, blankLogin.status)

            val deactivateConn = initTestDatabase(runMigrations = false)
            deactivateConn.use { conn ->
                transaction(conn.database) {
                    UsersTable.update({ UsersTable.email eq "coordinador@itm.edu.co" }) {
                        it[active] = false
                    }
                }
            }

            val inactiveLogin =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "coordinador@itm.edu.co", "password": "SeedPassword2026"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, inactiveLogin.status)

            val inactiveMe =
                client.get("/api/v1/auth/me") {
                    header(HttpHeaders.Authorization, "Bearer $token1")
                }
            assertEquals(HttpStatusCode.Unauthorized, inactiveMe.status)
        }

    @Test
    fun `POST logout revokes jti purges expired tokens and rejects subsequent reuse with 401`() =
        testApplication {
            val dbConn = initTestDatabase()
            cleanAuthTables(dbConn)
            val expiredJti = UUID.randomUUID()
            dbConn.dataSource.connection.use { conn ->
                val sql =
                    "INSERT INTO revoked_tokens (jti, expires_at) " +
                        "VALUES (?, NOW() - INTERVAL '10 minutes')"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setObject(1, expiredJti)
                    stmt.executeUpdate()
                }
                if (!conn.autoCommit) {
                    conn.commit()
                }
            }
            dbConn.close()

            val appConfig = buildTestAppConfig()
            application {
                configureModule(appConfig)
            }

            val loginResp =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "coordinador@itm.edu.co", "password": "SeedPassword2026"}""")
                }
            assertEquals(HttpStatusCode.OK, loginResp.status)
            val token =
                Json
                    .parseToJsonElement(
                        loginResp.bodyAsText(),
                    ).jsonObject["accessToken"]!!
                    .jsonPrimitive.content
            val jti = UUID.fromString(JWT.decode(token).id)

            val logoutResp =
                client.post("/api/v1/auth/logout") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.NoContent, logoutResp.status)

            val checkConn = initTestDatabase(runMigrations = false)
            checkConn.use { conn ->
                conn.dataSource.connection.use { sqlConn ->
                    sqlConn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE jti = ?").use { stmt ->
                        stmt.setObject(1, expiredJti)
                        stmt.executeQuery().use { rs ->
                            rs.next()
                            assertEquals(0, rs.getInt(1))
                        }
                    }
                    sqlConn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE jti = ?").use { stmt ->
                        stmt.setObject(1, jti)
                        stmt.executeQuery().use { rs ->
                            rs.next()
                            assertEquals(1, rs.getInt(1))
                        }
                    }
                }
            }

            val reuseMeResp =
                client.get("/api/v1/auth/me") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.Unauthorized, reuseMeResp.status)

            val reuseLogoutResp =
                client.post("/api/v1/auth/logout") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.Unauthorized, reuseLogoutResp.status)

            val expiredJwt =
                JWT
                    .create()
                    .withIssuer(appConfig.jwt.issuer)
                    .withAudience(appConfig.jwt.audience)
                    .withSubject(UUID.randomUUID().toString())
                    .withJWTId(UUID.randomUUID().toString())
                    .withClaim("role", UserRole.SOLICITANTE.name)
                    .withClaim("email", "coordinador@itm.edu.co")
                    .withIssuedAt(Date.from(Instant.now().minusSeconds(4000)))
                    .withExpiresAt(Date.from(Instant.now().minusSeconds(400)))
                    .sign(Algorithm.HMAC256(appConfig.jwt.secret))

            val expiredMeResp =
                client.get("/api/v1/auth/me") {
                    header(HttpHeaders.Authorization, "Bearer $expiredJwt")
                }
            assertEquals(HttpStatusCode.Unauthorized, expiredMeResp.status)

            val unauthenticatedMe = client.get("/api/v1/auth/me")
            assertEquals(HttpStatusCode.Unauthorized, unauthenticatedMe.status)

            val reloginResp =
                client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email": "coordinador@itm.edu.co", "password": "SeedPassword2026"}""")
                }
            assertEquals(HttpStatusCode.OK, reloginResp.status)
            val newToken =
                Json
                    .parseToJsonElement(reloginResp.bodyAsText())
                    .jsonObject["accessToken"]!!
                    .jsonPrimitive.content
            val newMeResp =
                client.get("/api/v1/auth/me") {
                    header(HttpHeaders.Authorization, "Bearer $newToken")
                }
            assertEquals(HttpStatusCode.OK, newMeResp.status)
        }
}
