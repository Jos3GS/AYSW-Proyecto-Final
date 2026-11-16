package com.itm.servicedesk.viewmodel.requests

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.RequestHistoryAction
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RequestDetailViewModelTest {
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
    fun `loads detail and enforces cancel only on ABIERTA and related only on RESUELTA or CERRADA`() =
        runTest(testDispatcher) {
            val repo =
                FakeServiceRequestRepository(
                    getDetailHandler = { code ->
                        val detail = RequestTestFixtures.requestDetail(code = code, status = RequestStatus.ABIERTA)
                        AppResult.Success(detail)
                    },
                )
            val viewModel = RequestDetailViewModel(repository = repo, dispatcher = testDispatcher)

            viewModel.loadDetail("SOL-2026-00019")
            advanceUntilIdle()

            val openContent = assertIs<RequestDetailUiState.Content>(viewModel.uiState.value)
            assertTrue(openContent.detail.canCancel)
            assertFalse(openContent.detail.canCreateRelated)

            viewModel.openCancelDialog()
            assertTrue((viewModel.uiState.value as RequestDetailUiState.Content).isCancelDialogOpen)

            viewModel.onCancelReasonChange("a".repeat(501))
            viewModel.confirmCancel()
            advanceUntilIdle()
            val reasonErrorState = assertIs<RequestDetailUiState.Content>(viewModel.uiState.value)
            assertNotNull(reasonErrorState.cancelReasonError)
            assertTrue(reasonErrorState.isCancelDialogOpen)
            assertTrue(repo.cancelCalls.isEmpty())

            viewModel.onCancelReasonChange("  Ya no lo necesito  ")
            viewModel.confirmCancel()
            advanceUntilIdle()

            val cancelledState = assertIs<RequestDetailUiState.Content>(viewModel.uiState.value)
            assertFalse(cancelledState.isCancelDialogOpen)
            assertEquals(RequestStatus.CANCELADA, cancelledState.detail.status)
            assertFalse(cancelledState.detail.canCancel)
            assertFalse(cancelledState.detail.canCreateRelated)
            assertNotNull(cancelledState.successNotice)
        }

    @Test
    fun `on 409 Conflict during cancel closes dialog, shows server error, and reloads detail`() =
        runTest(testDispatcher) {
            var currentDetail =
                RequestTestFixtures.requestDetail(
                    code = "SOL-2026-00019",
                    status = RequestStatus.ABIERTA,
                )
            val assignedDetail =
                RequestTestFixtures.requestDetail(
                    code = "SOL-2026-00019",
                    status = RequestStatus.EN_REVISION,
                    history =
                        listOf(
                            RequestTestFixtures.historyEntry(),
                            RequestTestFixtures.historyEntry(
                                id = "hist-2",
                                action = RequestHistoryAction.ASIGNACION,
                                previousState = RequestStatus.ABIERTA,
                                newState = RequestStatus.EN_REVISION,
                            ),
                        ),
                )

            val conflictMessage = "Solo es posible cancelar una solicitud que se encuentre en estado Abierta."
            val repo =
                FakeServiceRequestRepository(
                    getDetailHandler = { AppResult.Success(currentDetail) },
                    cancelHandler = {
                        currentDetail = assignedDetail
                        AppResult.Failure(DomainError.Conflict(conflictMessage))
                    },
                )

            val viewModel = RequestDetailViewModel(repository = repo, dispatcher = testDispatcher)
            viewModel.loadDetail("SOL-2026-00019")
            advanceUntilIdle()

            viewModel.openCancelDialog()
            viewModel.confirmCancel()
            advanceUntilIdle()

            val content = assertIs<RequestDetailUiState.Content>(viewModel.uiState.value)
            assertFalse(content.isCancelDialogOpen)
            assertFalse(content.isCancelling)
            assertEquals(
                "Solo es posible cancelar una solicitud que se encuentre en estado Abierta.",
                content.actionError,
            )
            assertEquals(RequestStatus.EN_REVISION, content.detail.status)
            assertFalse(content.detail.canCancel)
            assertEquals(2, repo.detailCalls.size)
        }

    @Test
    fun `maps 404 NotFound and 403 Forbidden into Error state`() =
        runTest(testDispatcher) {
            val repo =
                FakeServiceRequestRepository(
                    getDetailHandler = { code ->
                        if (code == "SOL-404") {
                            AppResult.Failure(DomainError.NotFound("Solicitud no encontrada."))
                        } else {
                            AppResult.Failure(DomainError.Forbidden("No tienes permisos para ver esta solicitud."))
                        }
                    },
                )
            val viewModel = RequestDetailViewModel(repository = repo, dispatcher = testDispatcher)

            viewModel.loadDetail("SOL-404")
            advanceUntilIdle()
            val notFound = assertIs<RequestDetailUiState.Error>(viewModel.uiState.value)
            assertTrue(notFound.isNotFound)
            assertFalse(notFound.isForbidden)

            viewModel.loadDetail("SOL-403")
            advanceUntilIdle()
            val forbidden = assertIs<RequestDetailUiState.Error>(viewModel.uiState.value)
            assertTrue(forbidden.isForbidden)
            assertFalse(forbidden.isNotFound)
        }
}
