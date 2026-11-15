package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.ServiceHealth
import com.itm.servicedesk.domain.repository.SystemHealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FakeSystemHealthRepository(
    private val responses: ArrayDeque<AppResult<ServiceHealth>>,
) : SystemHealthRepository {
    var callCount: Int = 0
        private set

    override suspend fun checkHealth(): AppResult<ServiceHealth> {
        callCount += 1
        return responses.removeFirst()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits Loading then Ready with formatted Bogota timestamp when checkHealth succeeds`() =
        runTest(testDispatcher) {
            val health =
                ServiceHealth(
                    isServiceUp = true,
                    isDatabaseUp = true,
                    serviceName = "itm-service-desk-backend",
                    checkedAt = Instant.parse("2026-11-14T11:24:00-05:00"),
                )
            val repository = FakeSystemHealthRepository(ArrayDeque(listOf(AppResult.Success(health))))

            val viewModel = StartupViewModel(repository = repository, uiMapper = StartupUiMapper())
            assertIs<StartupUiState.Loading>(viewModel.uiState.value)

            advanceUntilIdle()

            val ready = assertIs<StartupUiState.Ready>(viewModel.uiState.value)
            assertEquals("Activo", ready.serviceLabel)
            assertEquals("Conectada", ready.databaseLabel)
            assertEquals("14/11/2026 11:24", ready.formattedCheckedAt)
            assertEquals(StartupEvent.HealthCheckSucceeded, viewModel.events.first())
        }

    @Test
    fun `emits Error on failure and recovers to Ready when retry succeeds`() =
        runTest(testDispatcher) {
            val failure =
                AppResult.Failure(
                    DomainError.ServiceUnavailable("Servicio temporalmente no disponible."),
                )
            val success =
                AppResult.Success(
                    ServiceHealth(
                        isServiceUp = true,
                        isDatabaseUp = true,
                        serviceName = "itm-service-desk-backend",
                        checkedAt = Instant.parse("2026-11-14T16:30:00Z"),
                    ),
                )
            val repository = FakeSystemHealthRepository(ArrayDeque(listOf(failure, success)))

            val viewModel = StartupViewModel(repository = repository, uiMapper = StartupUiMapper())
            advanceUntilIdle()

            val errorState = assertIs<StartupUiState.Error>(viewModel.uiState.value)
            assertEquals("Servicio temporalmente no disponible.", errorState.message)
            assertTrue(errorState.canRetry)
            val errorEvent = assertIs<StartupEvent.ShowErrorBanner>(viewModel.events.first())
            assertEquals("Servicio temporalmente no disponible.", errorEvent.message)

            viewModel.retry()
            assertIs<StartupUiState.Loading>(viewModel.uiState.value)

            advanceUntilIdle()

            val readyState = assertIs<StartupUiState.Ready>(viewModel.uiState.value)
            assertEquals("14/11/2026 11:30", readyState.formattedCheckedAt)
            assertEquals(2, repository.callCount)
        }

    @Test
    fun `maps degraded health and NetworkUnavailable and generic error titles`() =
        runTest(testDispatcher) {
            val degraded =
                AppResult.Success(
                    ServiceHealth(
                        isServiceUp = false,
                        isDatabaseUp = false,
                        serviceName = "itm-service-desk-backend",
                        checkedAt = Instant.parse("2026-11-14T11:24:00-05:00"),
                    ),
                )
            val networkFailure = AppResult.Failure(DomainError.NetworkUnavailable("Sin red"))
            val serverFailure = AppResult.Failure(DomainError.UnexpectedServerError("Error interno"))
            val repository =
                FakeSystemHealthRepository(ArrayDeque(listOf(degraded, networkFailure, serverFailure)))

            val viewModel = StartupViewModel(repository = repository, uiMapper = StartupUiMapper())
            advanceUntilIdle()

            val degradedReady = assertIs<StartupUiState.Ready>(viewModel.uiState.value)
            assertEquals("Con degradación", degradedReady.serviceLabel)
            assertEquals("Sin conexión", degradedReady.databaseLabel)

            viewModel.retry()
            advanceUntilIdle()
            val networkError = assertIs<StartupUiState.Error>(viewModel.uiState.value)
            assertEquals("Sin conexión con el servidor", networkError.title)

            viewModel.retry()
            advanceUntilIdle()
            val genericError = assertIs<StartupUiState.Error>(viewModel.uiState.value)
            assertEquals("Error al verificar el servicio", genericError.title)
        }
}
