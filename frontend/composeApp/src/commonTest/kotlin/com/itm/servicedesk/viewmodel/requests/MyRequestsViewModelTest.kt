package com.itm.servicedesk.viewmodel.requests

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.testing.FakeServiceRequestRepository
import com.itm.servicedesk.testing.RequestTestFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MyRequestsViewModelTest {
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
    fun `loads size 20, paginates, and always resets page to 1 when status filter changes or clears`() =
        runTest(testDispatcher) {
            val repo =
                FakeServiceRequestRepository(
                    getMyRequestsHandler = { page, size, statusFilter ->
                        AppResult.Success(
                            RequestTestFixtures.requestsPage(
                                items =
                                    listOf(
                                        RequestTestFixtures.requestSummary(
                                            status = statusFilter ?: RequestStatus.ABIERTA,
                                        ),
                                    ),
                                page = page,
                                size = size,
                                totalItems = 45,
                                totalPages = 3,
                            ),
                        )
                    },
                )
            val viewModel = MyRequestsViewModel(repository = repo, dispatcher = testDispatcher)

            viewModel.loadRequests()
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.page)
            assertEquals(20, viewModel.uiState.value.size)
            assertEquals(3, viewModel.uiState.value.totalPages)
            assertEquals(
                "14/11/2026 18:30",
                viewModel.uiState.value.items
                    .first()
                    .formattedCreatedAt,
            )

            viewModel.nextPage()
            advanceUntilIdle()
            viewModel.nextPage()
            advanceUntilIdle()
            assertEquals(3, viewModel.uiState.value.page)
            assertEquals(Triple(3, 20, null), repo.listCalls.last())

            viewModel.selectStatusFilter(RequestStatus.RESUELTA)
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.page)
            assertEquals(RequestStatus.RESUELTA, viewModel.uiState.value.selectedStatusFilter)
            assertEquals(Triple(1, 20, RequestStatus.RESUELTA), repo.listCalls.last())

            viewModel.nextPage()
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.page)

            viewModel.clearStatusFilter()
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.page)
            assertNull(viewModel.uiState.value.selectedStatusFilter)
            assertEquals(Triple(1, 20, null), repo.listCalls.last())

            viewModel.previousPage()
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.page)
        }

    @Test
    fun `sets friendly error message on network failure and retries cleanly`() =
        runTest(testDispatcher) {
            var shouldFail = true
            val repo =
                FakeServiceRequestRepository(
                    getMyRequestsHandler = { page, size, _ ->
                        if (shouldFail) {
                            AppResult.Failure(DomainError.NetworkUnavailable("Sin conexión con el servidor."))
                        } else {
                            AppResult.Success(RequestTestFixtures.requestsPage(page = page, size = size))
                        }
                    },
                )
            val viewModel = MyRequestsViewModel(repository = repo, dispatcher = testDispatcher)

            viewModel.loadRequests()
            advanceUntilIdle()
            assertEquals("Sin conexión con el servidor.", viewModel.uiState.value.errorMessage)

            shouldFail = false
            viewModel.refresh()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.errorMessage)
            assertEquals(1, viewModel.uiState.value.items.size)
        }
}
