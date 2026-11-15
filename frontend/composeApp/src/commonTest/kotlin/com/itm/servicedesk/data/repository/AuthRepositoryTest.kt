package com.itm.servicedesk.data.repository

import com.itm.servicedesk.core.network.createHttpClient
import com.itm.servicedesk.core.storage.SessionEvent
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.data.remote.KtorAuthRemoteDataSource
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.AuthSession
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.LoginCommand
import com.itm.servicedesk.domain.model.RegisterCommand
import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.InMemoryTestSessionStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    private fun createRepo(
        engine: MockEngine,
        storage: InMemoryTestSessionStorage = InMemoryTestSessionStorage(),
        eventBus: SessionEventBus = SessionEventBus(),
    ): AuthRepositoryImpl {
        val client =
            createHttpClient(
                engine = engine,
                baseUrl = "http://localhost:8080",
                sessionStorage = storage,
                sessionEventBus = eventBus,
            )
        return AuthRepositoryImpl(
            remoteDataSource = KtorAuthRemoteDataSource(client),
            sessionStorage = storage,
            sessionEventBus = eventBus,
        )
    }

    @Test
    fun `register posts without role or Authorization header and saves token on 201`() =
        runTest {
            val storage = InMemoryTestSessionStorage()
            var capturedBody = ""
            var capturedAuthHeader: String? = "unexpected"

            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/api/v1/auth/register", request.url.encodedPath)
                    capturedAuthHeader = request.headers[HttpHeaders.Authorization]
                    capturedBody = request.body.toByteArray().decodeToString()
                    respond(
                        content = AuthTestFixtures.authResponseJson(token = "jwt-new-register"),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val repo = createRepo(engine, storage)
            val result =
                repo.register(
                    RegisterCommand(
                        fullName = "Laura Gómez Pérez",
                        documentNumber = "1020304050",
                        email = "laura.gomez@correo.itm.edu.co",
                        phone = "3001234567",
                        password = "ClaveSegura2026",
                    ),
                )

            val success = assertIs<AppResult.Success<AuthSession>>(result)
            assertEquals("jwt-new-register", success.data.accessToken)
            assertEquals("jwt-new-register", storage.getToken())
            assertNull(capturedAuthHeader)
            assertFalse(capturedBody.contains("\"role\""))
        }

    @Test
    fun `register maps 409 Conflict and 400 fieldErrors without saving token`() =
        runTest {
            val storage = InMemoryTestSessionStorage()
            val engine =
                MockEngine { request ->
                    if (request.body
                            .toByteArray()
                            .decodeToString()
                            .contains("dup@itm.edu.co")
                    ) {
                        respond(
                            content =
                                AuthTestFixtures.apiErrorJson(
                                    status = 409,
                                    error = "CONFLICT",
                                    message = "El correo ya se encuentra registrado.",
                                    path = "/api/v1/auth/register",
                                ),
                            status = HttpStatusCode.Conflict,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(
                            content =
                                AuthTestFixtures.apiErrorJson(
                                    status = 400,
                                    error = "BAD_REQUEST",
                                    message = "Datos inválidos.",
                                    path = "/api/v1/auth/register",
                                    detailsJson = """[{"field":"documentNumber","message":"Documento inválido."}]""",
                                ),
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            val repo = createRepo(engine, storage)

            val conflict =
                repo.register(
                    RegisterCommand("Laura", "1020304050", "dup@itm.edu.co", "3001234567", "Clave1234"),
                )
            assertIs<DomainError.Conflict>(assertIs<AppResult.Failure>(conflict).error)
            assertNull(storage.getToken())

            val badRequest =
                repo.register(
                    RegisterCommand("Laura", "1020304050", "bad@itm.edu.co", "3001234567", "Clave1234"),
                )
            val err = assertIs<DomainError.BadRequest>(assertIs<AppResult.Failure>(badRequest).error)
            assertEquals("Documento inválido.", err.fieldErrors["documentNumber"])
        }

    @Test
    fun `login saves JWT on 200 and does NOT emit SessionExpired on 401`() =
        runTest {
            val storage = InMemoryTestSessionStorage()
            val eventBus = SessionEventBus()
            val emittedEvents = mutableListOf<SessionEvent>()
            val collector = launch { eventBus.events.collect { emittedEvents.add(it) } }

            val engine =
                MockEngine { request ->
                    val body = request.body.toByteArray().decodeToString()
                    if (body.contains("valid@itm.edu.co")) {
                        respond(
                            content =
                                AuthTestFixtures.authResponseJson(
                                    token = "jwt-login-1h",
                                    role = "COORDINADOR",
                                ),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(
                            content =
                                AuthTestFixtures.apiErrorJson(
                                    status = 401,
                                    error = "UNAUTHORIZED",
                                    message = "Credenciales inválidas.",
                                    path = "/api/v1/auth/login",
                                ),
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            val repo = createRepo(engine, storage, eventBus)

            val failed = repo.login(LoginCommand("wrong@itm.edu.co", "Wrong1234"))
            advanceUntilIdle()
            val unauthorized = assertIs<DomainError.Unauthorized>(assertIs<AppResult.Failure>(failed).error)
            assertEquals("Credenciales inválidas.", unauthorized.userMessage)
            assertTrue(emittedEvents.isEmpty())
            assertNull(storage.getToken())

            val ok = repo.login(LoginCommand("valid@itm.edu.co", "Clave1234"))
            val session = assertIs<AppResult.Success<AuthSession>>(ok).data
            assertEquals("jwt-login-1h", storage.getToken())
            assertEquals(UserRole.COORDINADOR, session.user.role)
            assertTrue(emittedEvents.isEmpty())

            collector.cancel()
        }

    @Test
    fun `getCurrentUser returns profile on 200 and emits SessionExpired on 401`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "active-jwt")
            val eventBus = SessionEventBus()
            val engine =
                MockEngine { request ->
                    assertEquals("Bearer active-jwt", request.headers[HttpHeaders.Authorization])
                    respond(
                        content = AuthTestFixtures.userProfileJson(role = "PERSONAL_ATENCION"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val repo = createRepo(engine, storage, eventBus)
            val result = repo.getCurrentUser()
            val profile = assertIs<AppResult.Success<UserProfile>>(result).data
            assertEquals(UserRole.PERSONAL_ATENCION, profile.role)

            val expiredBus = SessionEventBus()
            val expiredStorage = InMemoryTestSessionStorage(initialToken = "expired-jwt")
            val expiredEngine =
                MockEngine {
                    respond(
                        content =
                            AuthTestFixtures.apiErrorJson(
                                status = 401,
                                error = "UNAUTHORIZED",
                                message = "Token expirado",
                                path = "/api/v1/auth/me",
                            ),
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val expiredRepo = createRepo(expiredEngine, expiredStorage, expiredBus)
            val eventDeferred = async { expiredBus.events.first() }
            val expiredResult = expiredRepo.getCurrentUser()
            assertIs<DomainError.Unauthorized>(assertIs<AppResult.Failure>(expiredResult).error)
            assertNull(expiredStorage.getToken())
            assertEquals(SessionEvent.SessionExpired, eventDeferred.await())
        }

    @Test
    fun `logout clears SessionStorage and emits LoggedOut on both 204 and 401`() =
        runTest {
            val storage204 = InMemoryTestSessionStorage(initialToken = "jwt-204")
            val bus204 = SessionEventBus()
            val repo204 =
                createRepo(
                    MockEngine { request ->
                        assertEquals("/api/v1/auth/logout", request.url.encodedPath)
                        assertEquals("Bearer jwt-204", request.headers[HttpHeaders.Authorization])
                        respond("", HttpStatusCode.NoContent)
                    },
                    storage204,
                    bus204,
                )
            val event204 = async { bus204.events.first() }
            assertIs<AppResult.Success<Unit>>(repo204.logout())
            assertNull(storage204.getToken())
            assertEquals(SessionEvent.LoggedOut, event204.await())

            val storage401 = InMemoryTestSessionStorage(initialToken = "jwt-already-expired")
            val bus401 = SessionEventBus()
            val events401 = mutableListOf<SessionEvent>()
            val collector = launch { bus401.events.collect { events401.add(it) } }
            val repo401 =
                createRepo(
                    MockEngine {
                        respond(
                            content =
                                AuthTestFixtures.apiErrorJson(
                                    status = 401,
                                    error = "UNAUTHORIZED",
                                    message = "Expirado",
                                    path = "/api/v1/auth/logout",
                                ),
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                    storage401,
                    bus401,
                )

            assertIs<AppResult.Success<Unit>>(repo401.logout())
            advanceUntilIdle()
            assertNull(storage401.getToken())
            assertEquals(listOf<SessionEvent>(SessionEvent.LoggedOut), events401)
            collector.cancel()
        }
}
