package com.itm.servicedesk.viewmodel.attention

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.testing.AttentionTestFixtures
import com.itm.servicedesk.testing.FakeAttentionRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CoordinationPendingViewModelTest {
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
    fun `loads pending requests, filters by ABIERTA or ESCALADA, and paginates`() =
        runTest(testDispatcher) {
            val repo =
                FakeAttentionRepository(
                    getCoordinationPendingHandler = { page, size, status ->
                        val items =
                            listOf(
                                AttentionTestFixtures.requestSummary(
                                    code = "SOL-2026-00001",
                                    status = status ?: RequestStatus.ABIERTA,
                                ),
                                AttentionTestFixtures.requestSummary(
                                    code = "SOL-2026-00002",
                                    status = RequestStatus.ESCALADA,
                                    isOverdue = true,
                                ),
                            )
                        AppResult.Success(
                            AttentionTestFixtures.requestsPage(
                                items = items,
                                page = page,
                                size = size,
                                totalItems = 40,
                                totalPages = 2,
                            ),
                        )
                    },
                )
            val viewModel = CoordinationPendingViewModel(attentionRepository = repo, dispatcher = testDispatcher)

            viewModel.loadPending()
            advanceUntilIdle()

            val initial = viewModel.uiState.value
            assertFalse(initial.isLoading)
            assertEquals(1, initial.currentPage)
            assertEquals(2, initial.totalPages)
            assertEquals(40L, initial.totalItems)
            assertEquals("SOL-2026-00001", initial.items[0].code)
            assertEquals("SOL-2026-00002", initial.items[1].code)
            assertNull(initial.selectedStatusFilter)

            viewModel.nextPage()
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.currentPage)

            viewModel.previousPage()
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.currentPage)

            viewModel.selectStatusFilter(RequestStatus.ESCALADA)
            advanceUntilIdle()
            assertEquals(RequestStatus.ESCALADA, viewModel.uiState.value.selectedStatusFilter)
            assertEquals(1, viewModel.uiState.value.currentPage)

            // Non-pending filters are sanitized to null
            viewModel.selectStatusFilter(RequestStatus.RESUELTA)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedStatusFilter)
        }

    @Test
    fun `publishes error message and supports refresh`() =
        runTest(testDispatcher) {
            var shouldFail = true
            val repo =
                FakeAttentionRepository(
                    getCoordinationPendingHandler = { page, size, _ ->
                        if (shouldFail) {
                            AppResult.Failure(DomainError.NetworkUnavailable("Sin conexión."))
                        } else {
                            AppResult.Success(AttentionTestFixtures.requestsPage(page = page, size = size))
                        }
                    },
                )
            val viewModel = CoordinationPendingViewModel(attentionRepository = repo, dispatcher = testDispatcher)

            viewModel.loadPending()
            advanceUntilIdle()
            assertEquals("Sin conexión.", viewModel.uiState.value.errorMessage)

            shouldFail = false
            viewModel.refresh()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.errorMessage)
            assertEquals(1, viewModel.uiState.value.items.size)
        }
}
