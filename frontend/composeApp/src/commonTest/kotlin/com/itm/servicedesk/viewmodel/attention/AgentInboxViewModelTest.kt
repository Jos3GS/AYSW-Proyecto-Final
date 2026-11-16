package com.itm.servicedesk.viewmodel.attention

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.RequestPriority
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
class AgentInboxViewModelTest {
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
    fun `loads my-assigned requests, filters by agent status, and paginates`() =
        runTest(testDispatcher) {
            val repo =
                FakeAttentionRepository(
                    getMyAssignedHandler = { page, size, status ->
                        AppResult.Success(
                            AttentionTestFixtures.requestsPage(
                                items =
                                    listOf(
                                        AttentionTestFixtures.requestSummary(
                                            code = "SOL-2026-00010",
                                            status = status ?: RequestStatus.EN_REVISION,
                                            priority = RequestPriority.ALTA,
                                            isOverdue = true,
                                        ),
                                    ),
                                page = page,
                                size = size,
                                totalItems = 35,
                                totalPages = 2,
                            ),
                        )
                    },
                )
            val viewModel = AgentInboxViewModel(attentionRepository = repo, dispatcher = testDispatcher)

            viewModel.loadAssigned()
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(1, viewModel.uiState.value.currentPage)
            assertEquals(2, viewModel.uiState.value.totalPages)

            viewModel.nextPage()
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.currentPage)

            viewModel.previousPage()
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.currentPage)

            viewModel.selectStatusFilter(RequestStatus.CERRADA)
            advanceUntilIdle()
            assertEquals(RequestStatus.CERRADA, viewModel.uiState.value.selectedStatusFilter)
            assertEquals(1, viewModel.uiState.value.currentPage)

            // Disallowed filter (ABIERTA) sanitizes to null
            viewModel.selectStatusFilter(RequestStatus.ABIERTA)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedStatusFilter)
        }

    @Test
    fun `handles failure and recovers on refresh`() =
        runTest(testDispatcher) {
            var shouldFail = true
            val repo =
                FakeAttentionRepository(
                    getMyAssignedHandler = { page, size, _ ->
                        if (shouldFail) {
                            AppResult.Failure(DomainError.UnexpectedServerError("Error temporal."))
                        } else {
                            AppResult.Success(AttentionTestFixtures.requestsPage(page = page, size = size))
                        }
                    },
                )
            val viewModel = AgentInboxViewModel(attentionRepository = repo, dispatcher = testDispatcher)

            viewModel.loadAssigned()
            advanceUntilIdle()
            assertEquals("Error temporal.", viewModel.uiState.value.errorMessage)

            shouldFail = false
            viewModel.refresh()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.errorMessage)
        }
}
