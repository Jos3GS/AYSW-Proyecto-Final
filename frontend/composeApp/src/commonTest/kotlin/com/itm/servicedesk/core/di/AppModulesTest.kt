package com.itm.servicedesk.core.di

import com.itm.servicedesk.core.storage.SessionEvent
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.core.storage.SessionStorage
import com.itm.servicedesk.domain.repository.AttentionRepository
import com.itm.servicedesk.domain.repository.AuthRepository
import com.itm.servicedesk.domain.repository.ServiceRequestRepository
import com.itm.servicedesk.domain.repository.StaffRepository
import com.itm.servicedesk.domain.repository.SystemHealthRepository
import com.itm.servicedesk.domain.validation.AttentionFormValidator
import com.itm.servicedesk.viewmodel.CreateStaffViewModel
import com.itm.servicedesk.viewmodel.LoginViewModel
import com.itm.servicedesk.viewmodel.RegisterViewModel
import com.itm.servicedesk.viewmodel.SessionViewModel
import com.itm.servicedesk.viewmodel.StaffListViewModel
import com.itm.servicedesk.viewmodel.StartupViewModel
import com.itm.servicedesk.viewmodel.attention.AgentInboxViewModel
import com.itm.servicedesk.viewmodel.attention.AttentionDetailViewModel
import com.itm.servicedesk.viewmodel.attention.CoordinationPendingViewModel
import com.itm.servicedesk.viewmodel.requests.CreateRequestViewModel
import com.itm.servicedesk.viewmodel.requests.MyRequestsViewModel
import com.itm.servicedesk.viewmodel.requests.RequestDetailViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class InMemoryTestSessionStorage : SessionStorage {
    private var token: String? = null

    override suspend fun saveToken(token: String) {
        this.token = token
    }

    override suspend fun getToken(): String? = token

    override suspend fun clearToken() {
        token = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppModulesTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        stopKoin()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `resolves core network repository and viewmodel graph via Koin`() =
        runTest(testDispatcher) {
            val mockEngine =
                MockEngine.create {
                    dispatcher = testDispatcher
                    addHandler {
                        respond(
                            content =
                                """
                                {
                                  "status": "UP",
                                  "service": "itm-service-desk-backend",
                                  "database": "UP",
                                  "timestamp": "2026-11-14T11:24:00-05:00"
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            val testOverrides =
                module {
                    single<SessionStorage> { InMemoryTestSessionStorage() }
                    single<HttpClientEngine> { mockEngine }
                }

            val koinApp =
                startKoin {
                    allowOverride(true)
                    modules(appModules + testOverrides)
                }

            val koin = koinApp.koin
            assertNotNull(koin.get<Json>())
            val eventBus = koin.get<SessionEventBus>()
            val eventDeferred = async { eventBus.events.first() }
            testScheduler.runCurrent()
            assertTrue(eventBus.tryEmit(SessionEvent.LoggedOut))
            assertIs<SessionEvent.LoggedOut>(eventDeferred.await())

            val client = koin.get<HttpClient>()
            assertNotNull(client)
            assertNotNull(koin.get<SystemHealthRepository>())
            assertNotNull(koin.get<AuthRepository>())
            assertNotNull(koin.get<StaffRepository>())
            assertNotNull(koin.get<ServiceRequestRepository>())
            assertNotNull(koin.get<AttentionRepository>())
            assertNotNull(koin.get<AttentionFormValidator>())
            assertNotNull(koin.get<SessionViewModel>())
            assertNotNull(koin.get<LoginViewModel>())
            assertNotNull(koin.get<RegisterViewModel>())
            assertNotNull(koin.get<StaffListViewModel>())
            assertNotNull(koin.get<CreateStaffViewModel>())
            assertNotNull(koin.get<MyRequestsViewModel>())
            assertNotNull(koin.get<CreateRequestViewModel>())
            assertNotNull(koin.get<RequestDetailViewModel>())
            assertNotNull(koin.get<CoordinationPendingViewModel>())
            assertNotNull(koin.get<AgentInboxViewModel>())
            assertNotNull(koin.get<AttentionDetailViewModel>())
            val viewModel = koin.get<StartupViewModel>()
            advanceUntilIdle()
            assertIs<com.itm.servicedesk.viewmodel.StartupUiState.Ready>(viewModel.uiState.value)
            client.close()
        }
}
