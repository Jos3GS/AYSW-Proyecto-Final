package com.itm.servicedesk.data.repository

import com.itm.servicedesk.core.network.createHttpClient
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.data.remote.KtorStaffRemoteDataSource
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CreateStaffCommand
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.StaffPage
import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.InMemoryTestSessionStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class StaffRepositoryTest {
    @Test
    fun `listStaff sends page size 20 and optional role query param`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "coord-jwt")
            var capturedQueryPage: String? = null
            var capturedQuerySize: String? = null
            var capturedQueryRole: String? = null

            val engine =
                MockEngine { request ->
                    assertEquals("/api/v1/users/staff", request.url.encodedPath)
                    assertEquals("Bearer coord-jwt", request.headers[HttpHeaders.Authorization])
                    capturedQueryPage = request.url.parameters["page"]
                    capturedQuerySize = request.url.parameters["size"]
                    capturedQueryRole = request.url.parameters["role"]
                    respond(
                        content = AuthTestFixtures.pagedStaffJson(page = 2, size = 20, totalItems = 21, totalPages = 2),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client = createHttpClient(engine, "http://localhost:8080", storage, SessionEventBus())
            val repo = StaffRepositoryImpl(KtorStaffRemoteDataSource(client))

            val filtered = repo.listStaff(page = 2, size = 20, roleFilter = UserRole.PERSONAL_ATENCION)
            val page = assertIs<AppResult.Success<StaffPage>>(filtered).data
            assertEquals("2", capturedQueryPage)
            assertEquals("20", capturedQuerySize)
            assertEquals("PERSONAL_ATENCION", capturedQueryRole)
            assertEquals(2, page.totalPages)

            repo.listStaff(page = 1, size = 20, roleFilter = null)
            assertNull(capturedQueryRole)
        }

    @Test
    fun `createStaff preserves coordinator session token and maps 403 and 409`() =
        runTest {
            val storage = InMemoryTestSessionStorage(initialToken = "coord-jwt-preserved")
            val engine =
                MockEngine { request ->
                    when {
                        request.url.parameters["case"] == "forbidden" -> {
                            respond(
                                content =
                                    AuthTestFixtures.apiErrorJson(
                                        403,
                                        "FORBIDDEN",
                                        "Acceso restringido a Coordinación.",
                                        "/api/v1/users/staff",
                                    ),
                                status = HttpStatusCode.Forbidden,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        else -> {
                            respond(
                                content =
                                    AuthTestFixtures.userProfileJson(
                                        id = "staff-new-id",
                                        fullName = "Carlos Andrés Mejía",
                                        role = "PERSONAL_ATENCION",
                                    ),
                                status = HttpStatusCode.Created,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }
                }

            val client = createHttpClient(engine, "http://localhost:8080", storage, SessionEventBus())
            val repo = StaffRepositoryImpl(KtorStaffRemoteDataSource(client))

            val created =
                repo.createStaff(
                    CreateStaffCommand(
                        fullName = "Carlos Andrés Mejía",
                        documentNumber = "71829304",
                        email = "carlos.mejia@itm.edu.co",
                        phone = "3119876543",
                        password = "StaffPassword2026",
                        role = UserRole.PERSONAL_ATENCION,
                    ),
                )

            val profile = assertIs<AppResult.Success<UserProfile>>(created).data
            assertEquals("staff-new-id", profile.id)
            assertEquals("coord-jwt-preserved", storage.getToken())

            val forbiddenEngine =
                MockEngine {
                    respond(
                        content =
                            AuthTestFixtures.apiErrorJson(
                                403,
                                "FORBIDDEN",
                                "No tienes permisos para consultar personal.",
                                "/api/v1/users/staff",
                            ),
                        status = HttpStatusCode.Forbidden,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val forbiddenRepo =
                StaffRepositoryImpl(
                    KtorStaffRemoteDataSource(
                        createHttpClient(forbiddenEngine, "http://localhost:8080", storage, SessionEventBus()),
                    ),
                )
            val forbiddenResult = forbiddenRepo.listStaff(page = 1, size = 20, roleFilter = null)
            assertIs<DomainError.Forbidden>(assertIs<AppResult.Failure>(forbiddenResult).error)
            assertEquals("coord-jwt-preserved", storage.getToken())
        }
}
