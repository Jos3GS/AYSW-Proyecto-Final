package com.itm.servicedesk.viewmodel.reports

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.ReportFilterQuery
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.validation.ReportFilterValidator
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.FakeReportRepository
import com.itm.servicedesk.testing.FakeStaffRepository
import com.itm.servicedesk.testing.ReportTestFixtures
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CoordinationReportsViewModelTest {
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
    fun `loadInitial executes summary, page 1 requests, and multipage staff catalog in parallel`() =
        runTest(testDispatcher) {
            val agentPage1 =
                (1..20).map { idx ->
                    AuthTestFixtures.staffMember(
                        id = "agent-$idx",
                        fullName = "Agente $idx",
                        role = UserRole.PERSONAL_ATENCION,
                    )
                }
            val agentPage2 =
                listOf(
                    AuthTestFixtures.staffMember(
                        id = "agent-21",
                        fullName = "Agente 21",
                        role = UserRole.PERSONAL_ATENCION,
                    ),
                )
            val staffRepo =
                FakeStaffRepository(
                    listStaffHandler = { page, size, roleFilter ->
                        assertEquals(UserRole.PERSONAL_ATENCION, roleFilter)
                        val items = if (page == 1) agentPage1 else agentPage2
                        AppResult.Success(
                            AuthTestFixtures.staffPage(
                                items = items,
                                page = page,
                                size = size,
                                totalItems = 21L,
                                totalPages = 2,
                            ),
                        )
                    },
                )
            val reportRepo = FakeReportRepository()
            val viewModel =
                CoordinationReportsViewModel(
                    reportRepository = reportRepo,
                    staffRepository = staffRepo,
                    dispatcher = testDispatcher,
                )

            viewModel.loadInitial()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isLoadingAgents)
            assertNotNull(state.summary)
            assertNotNull(state.requestsPage)
            assertEquals(21, state.availableAgents.size)
            assertEquals(1, reportRepo.summaryCalls.size)
            assertEquals(1, reportRepo.requestsCalls.size)
            assertEquals(1, reportRepo.requestsCalls.first().second)
            assertEquals(20, reportRepo.requestsCalls.first().third)
            assertEquals(2, staffRepo.listCalls.size)
            assertNull(state.errorMessage)

            // Calling loadInitial again when already loaded does not trigger duplicate calls
            viewModel.loadInitial()
            advanceUntilIdle()
            assertEquals(1, reportRepo.summaryCalls.size)
            assertEquals(1, reportRepo.requestsCalls.size)
        }

    @Test
    fun `editing draftFilter does not emit HTTP calls and invalid dates block apply`() =
        runTest(testDispatcher) {
            val reportRepo = FakeReportRepository()
            val staffRepo = FakeStaffRepository()
            val viewModel =
                CoordinationReportsViewModel(
                    reportRepository = reportRepo,
                    staffRepository = staffRepo,
                    dispatcher = testDispatcher,
                )

            viewModel.loadInitial()
            advanceUntilIdle()

            viewModel.onFromDateChanged("20/11/2026")
            viewModel.onToDateChanged("10/11/2026")
            viewModel.onStatusFilterChanged(RequestStatus.ESCALADA)
            viewModel.onCategoryFilterChanged(RequestCategory.TRAMITE)
            viewModel.onPriorityFilterChanged(RequestPriority.ALTA)
            viewModel.onAssigneeFilterChanged("agent-1")
            advanceUntilIdle()

            // No new HTTP calls during draft editing
            assertEquals(1, reportRepo.summaryCalls.size)
            assertEquals(1, reportRepo.requestsCalls.size)
            assertEquals(ReportFilterQuery(), viewModel.uiState.value.appliedFilter)

            viewModel.onApplyFilters()
            advanceUntilIdle()

            // Invalid range keeps appliedFilter untouched and blocks HTTP calls
            assertEquals(1, reportRepo.summaryCalls.size)
            assertEquals(1, reportRepo.requestsCalls.size)
            assertEquals(
                ReportFilterValidator.INVALID_RANGE_MESSAGE,
                viewModel.uiState.value.draftFilter.toDateError,
            )
            assertEquals(ReportFilterQuery(), viewModel.uiState.value.appliedFilter)

            // Typing clears the inline error
            viewModel.onToDateChanged("25/11/2026")
            assertNull(viewModel.uiState.value.draftFilter.toDateError)
        }

    @Test
    fun `applyFilters, pagination without re-fetching summary, and clearFilters work as specified`() =
        runTest(testDispatcher) {
            val reportRepo =
                FakeReportRepository(
                    getRequestsHandler = { _, page, size ->
                        AppResult.Success(
                            ReportTestFixtures.requestsPage(
                                items = listOf(ReportTestFixtures.requestItem(code = "SOL-2026-000$page")),
                                page = page,
                                size = size,
                                totalItems = 40L,
                                totalPages = 2,
                            ),
                        )
                    },
                )
            val viewModel =
                CoordinationReportsViewModel(
                    reportRepository = reportRepo,
                    staffRepository = FakeStaffRepository(),
                    dispatcher = testDispatcher,
                )

            viewModel.loadInitial()
            advanceUntilIdle()

            viewModel.onFromDateChanged("01/11/2026")
            viewModel.onToDateChanged("14/11/2026")
            viewModel.onCategoryFilterChanged(RequestCategory.TRAMITE)
            viewModel.onPriorityFilterChanged(RequestPriority.ALTA)
            viewModel.onAssigneeFilterChanged("agent-1")
            viewModel.onApplyFilters()
            advanceUntilIdle()

            val expectedApplied =
                ReportFilterQuery(
                    createdFromIso = "2026-11-01",
                    createdToIso = "2026-11-14",
                    status = null,
                    category = RequestCategory.TRAMITE,
                    priority = RequestPriority.ALTA,
                    assignedToId = "agent-1",
                )
            assertEquals(expectedApplied, viewModel.uiState.value.appliedFilter)
            assertEquals(2, reportRepo.summaryCalls.size)
            assertEquals(2, reportRepo.requestsCalls.size)
            assertEquals(expectedApplied, reportRepo.summaryCalls.last())
            assertEquals(expectedApplied, reportRepo.requestsCalls.last().first)

            // Pagination calls ONLY getRequests without re-calling getSummary
            viewModel.onNextPage()
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.currentPage)
            assertEquals(2, reportRepo.summaryCalls.size)
            assertEquals(3, reportRepo.requestsCalls.size)
            assertEquals(expectedApplied, reportRepo.requestsCalls.last().first)
            assertEquals(2, reportRepo.requestsCalls.last().second)

            viewModel.onPreviousPage()
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.currentPage)
            assertEquals(2, reportRepo.summaryCalls.size)
            assertEquals(4, reportRepo.requestsCalls.size)

            // Clear filters resets draft and applied filters and reloads page 1 globally
            viewModel.onClearFilters()
            advanceUntilIdle()

            assertEquals(ReportFilterDraft(), viewModel.uiState.value.draftFilter)
            assertEquals(ReportFilterQuery(), viewModel.uiState.value.appliedFilter)
            assertEquals(1, viewModel.uiState.value.currentPage)
            assertEquals(3, reportRepo.summaryCalls.size)
            assertEquals(5, reportRepo.requestsCalls.size)
            assertEquals(ReportFilterQuery(), reportRepo.summaryCalls.last())
            assertEquals(1, reportRepo.requestsCalls.last().second)
        }

    @Test
    fun `handles partial parallel failures, pagination errors, and retry recovery`() =
        runTest(testDispatcher) {
            var failSummary = false
            var failRequests = true
            var failStaff = true
            val reportRepo =
                FakeReportRepository(
                    getSummaryHandler = {
                        if (failSummary) {
                            AppResult.Failure(DomainError.NetworkUnavailable("Error de resumen."))
                        } else {
                            AppResult.Success(ReportTestFixtures.summary())
                        }
                    },
                    getRequestsHandler = { _, page, size ->
                        if (failRequests) {
                            AppResult.Failure(DomainError.Forbidden("Sin permisos para consultar listado."))
                        } else {
                            AppResult.Success(
                                ReportTestFixtures.requestsPage(
                                    page = page,
                                    size = size,
                                    totalItems = 40L,
                                    totalPages = 2,
                                ),
                            )
                        }
                    },
                )
            val staffRepo =
                FakeStaffRepository(
                    listStaffHandler = { page, size, _ ->
                        if (failStaff) {
                            AppResult.Failure(DomainError.NetworkUnavailable("Error de personal."))
                        } else {
                            AppResult.Success(
                                AuthTestFixtures.staffPage(
                                    items = listOf(AuthTestFixtures.staffMember(id = "agent-1")),
                                    page = page,
                                    size = size,
                                ),
                            )
                        }
                    },
                )
            val viewModel =
                CoordinationReportsViewModel(
                    reportRepository = reportRepo,
                    staffRepository = staffRepo,
                    dispatcher = testDispatcher,
                )

            viewModel.loadInitial()
            advanceUntilIdle()

            val partialState = viewModel.uiState.value
            assertFalse(partialState.isLoading)
            assertNotNull(partialState.summary)
            assertNull(partialState.requestsPage)
            assertTrue(partialState.availableAgents.isEmpty())
            assertEquals("Sin permisos para consultar listado.", partialState.errorMessage)

            // Retry recovers requests and staff
            failRequests = false
            failStaff = false
            viewModel.onRetry()
            advanceUntilIdle()

            val recoveredState = viewModel.uiState.value
            assertNull(recoveredState.errorMessage)
            assertNotNull(recoveredState.summary)
            assertNotNull(recoveredState.requestsPage)
            assertEquals(1, recoveredState.availableAgents.size)

            // Pagination failure preserves summary & previous page and allows retry on page 2
            failRequests = true
            viewModel.onPageSelected(2)
            advanceUntilIdle()
            assertEquals("Sin permisos para consultar listado.", viewModel.uiState.value.errorMessage)
            assertNotNull(viewModel.uiState.value.summary)
            assertEquals(2, viewModel.uiState.value.currentPage)

            failRequests = false
            val summaryCountBeforeRetry = reportRepo.summaryCalls.size
            viewModel.onRetry()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.errorMessage)
            assertEquals(summaryCountBeforeRetry, reportRepo.summaryCalls.size)
            assertEquals(2, viewModel.uiState.value.currentPage)
        }
}
