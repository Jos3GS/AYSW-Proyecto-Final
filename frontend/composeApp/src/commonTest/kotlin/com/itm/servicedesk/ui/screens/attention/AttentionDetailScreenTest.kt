package com.itm.servicedesk.ui.screens.attention

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.testing.AttentionTestFixtures
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.FakeAttentionRepository
import com.itm.servicedesk.testing.FakeStaffRepository
import com.itm.servicedesk.ui.components.AttentionActionPanelTestTags
import com.itm.servicedesk.ui.components.ConfirmDialogTestTags
import com.itm.servicedesk.ui.components.OverdueBadgeTestTags
import com.itm.servicedesk.ui.screens.ComposeUiTestBase
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.attention.AgentActionTab
import com.itm.servicedesk.viewmodel.attention.AttentionDetailUiState
import com.itm.servicedesk.viewmodel.attention.AttentionDetailViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AttentionDetailScreenTest : ComposeUiTestBase() {
    @Test
    fun `Coordinator ABIERTA shows assign panel with priority labels and confirmation flow`() =
        runComposeUiTest {
            val agent = AuthTestFixtures.staffMember(id = "agent-1", fullName = "Carlos Andrés Mejía")
            val attentionRepo = FakeAttentionRepository()
            val staffRepo =
                FakeStaffRepository(
                    listStaffHandler = { page, size, _ ->
                        AppResult.Success(AuthTestFixtures.staffPage(items = listOf(agent), page = page, size = size))
                    },
                )
            val viewModel =
                AttentionDetailViewModel(
                    attentionRepository = attentionRepo,
                    staffRepository = staffRepo,
                )
            viewModel.loadDetail(code = "SOL-2026-00019", userRole = UserRole.COORDINADOR)

            setContent {
                val state by viewModel.uiState.collectAsState()
                ServiceDeskTheme {
                    AttentionDetailScreen(
                        uiState = state,
                        userRole = UserRole.COORDINADOR,
                        onSelectAgent = viewModel::onSelectAgent,
                        onRetryLoadAgents = viewModel::retryLoadAgents,
                        onSelectPriority = viewModel::onSelectPriority,
                        onReassignmentReasonChange = viewModel::onReassignmentReasonChange,
                        onRequestAssign = viewModel::requestAssignConfirmation,
                        onRequestReassign = viewModel::requestReassignConfirmation,
                        onSelectAgentTab = viewModel::onSelectAgentTab,
                        onInterventionCommentChange = viewModel::onInterventionCommentChange,
                        onSubmitIntervention = viewModel::submitIntervention,
                        onEscalationReasonChange = viewModel::onEscalationReasonChange,
                        onRequestEscalate = viewModel::requestEscalateConfirmation,
                        onResponseTextChange = viewModel::onResponseTextChange,
                        onRequestRespond = viewModel::requestRespondConfirmation,
                        onConfirmPendingAction = viewModel::confirmPendingAction,
                        onDismissConfirmation = viewModel::dismissConfirmation,
                        onRetryLoadDetail = viewModel::retryLoadDetail,
                        onBackToList = {},
                    )
                }
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(AttentionActionPanelTestTags.ASSIGN_SUBMIT_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Alta (24 h)").performScrollTo().assertIsDisplayed()
            onNodeWithText("Media (72 h)").performScrollTo().assertIsDisplayed()
            onNodeWithText("Baja (120 h)").performScrollTo().assertIsDisplayed()

            onNodeWithTag(AttentionActionPanelTestTags.AGENT_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(AttentionActionPanelTestTags.agentOption("agent-1")).performClick()
            onNodeWithTag(AttentionActionPanelTestTags.priorityChip(RequestPriority.ALTA))
                .performScrollTo()
                .performClick()
            onNodeWithTag(AttentionActionPanelTestTags.ASSIGN_SUBMIT_BUTTON).performScrollTo().performClick()

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(ConfirmDialogTestTags.DIALOG).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Confirmar asignación").assertIsDisplayed()
            onNodeWithTag(ConfirmDialogTestTags.ACCEPT_BUTTON).performClick()

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(AttentionDetailTestTags.SUCCESS_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(1, attentionRepo.assignCalls.size)
            assertEquals(1, staffRepo.listCalls.size)
        }

    @Test
    fun `Coordinator ESCALADA with isOverdue keeps reassign button enabled and shows SLA notice`() =
        runComposeUiTest {
            val agent = AuthTestFixtures.staffMember(id = "agent-2", fullName = "María Fernanda López")
            val detail =
                AttentionTestFixtures.requestDetail(
                    code = "SOL-2026-00021",
                    status = RequestStatus.ESCALADA,
                    priority = RequestPriority.ALTA,
                    isOverdue = true,
                    dueAt = AttentionTestFixtures.sampleDueAt,
                )

            setContent {
                ServiceDeskTheme {
                    AttentionDetailScreen(
                        uiState =
                            AttentionDetailUiState(
                                detail = detail,
                                availableAgents = listOf(agent),
                            ),
                        userRole = UserRole.COORDINADOR,
                        onSelectAgent = {},
                        onRetryLoadAgents = {},
                        onSelectPriority = {},
                        onReassignmentReasonChange = {},
                        onRequestAssign = {},
                        onRequestReassign = {},
                        onSelectAgentTab = {},
                        onInterventionCommentChange = {},
                        onSubmitIntervention = {},
                        onEscalationReasonChange = {},
                        onRequestEscalate = {},
                        onResponseTextChange = {},
                        onRequestRespond = {},
                        onConfirmPendingAction = {},
                        onDismissConfirmation = {},
                        onRetryLoadDetail = {},
                        onBackToList = {},
                    )
                }
            }

            onNodeWithTag(OverdueBadgeTestTags.BADGE).assertIsDisplayed()
            onNodeWithTag(AttentionActionPanelTestTags.REASSIGN_SLA_NOTICE).performScrollTo().assertIsDisplayed()
            onNodeWithTag(AttentionActionPanelTestTags.REASSIGN_REASON_INPUT).performScrollTo().assertIsDisplayed()
            onNodeWithTag(AttentionActionPanelTestTags.REASSIGN_SUBMIT_BUTTON)
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
        }

    @Test
    fun `Agent EN_REVISION keeps overdue buttons enabled, preserves drafts across tabs, and handles 409 reload`() =
        runComposeUiTest {
            var escalateAttempts = 0
            val attentionRepo =
                FakeAttentionRepository(
                    getInternalDetailHandler = { code ->
                        AppResult.Success(
                            AttentionTestFixtures.requestDetail(
                                code = code,
                                status =
                                    if (escalateAttempts == 0) {
                                        RequestStatus.EN_REVISION
                                    } else {
                                        RequestStatus.ESCALADA
                                    },
                                priority = RequestPriority.ALTA,
                                assignedAgentId = "agent-1",
                                assignedAgentName = "Carlos Andrés Mejía",
                                isOverdue = true,
                                dueAt = AttentionTestFixtures.sampleDueAt,
                            ),
                        )
                    },
                    escalateHandler = { _, _ ->
                        escalateAttempts += 1
                        AppResult.Failure(
                            DomainError.Conflict("La solicitud cambió de estado concurrentemente."),
                        )
                    },
                )
            val staffRepo = FakeStaffRepository()
            val viewModel =
                AttentionDetailViewModel(
                    attentionRepository = attentionRepo,
                    staffRepository = staffRepo,
                )
            viewModel.loadDetail(code = "SOL-2026-00022", userRole = UserRole.PERSONAL_ATENCION)

            setContent {
                val state by viewModel.uiState.collectAsState()
                ServiceDeskTheme {
                    AttentionDetailScreen(
                        uiState = state,
                        userRole = UserRole.PERSONAL_ATENCION,
                        onSelectAgent = viewModel::onSelectAgent,
                        onRetryLoadAgents = viewModel::retryLoadAgents,
                        onSelectPriority = viewModel::onSelectPriority,
                        onReassignmentReasonChange = viewModel::onReassignmentReasonChange,
                        onRequestAssign = viewModel::requestAssignConfirmation,
                        onRequestReassign = viewModel::requestReassignConfirmation,
                        onSelectAgentTab = viewModel::onSelectAgentTab,
                        onInterventionCommentChange = viewModel::onInterventionCommentChange,
                        onSubmitIntervention = viewModel::submitIntervention,
                        onEscalationReasonChange = viewModel::onEscalationReasonChange,
                        onRequestEscalate = viewModel::requestEscalateConfirmation,
                        onResponseTextChange = viewModel::onResponseTextChange,
                        onRequestRespond = viewModel::requestRespondConfirmation,
                        onConfirmPendingAction = viewModel::confirmPendingAction,
                        onDismissConfirmation = viewModel::dismissConfirmation,
                        onRetryLoadDetail = viewModel::retryLoadDetail,
                        onBackToList = {},
                    )
                }
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(AttentionActionPanelTestTags.INTERVENTION_INPUT).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(0, staffRepo.listCalls.size)
            onNodeWithTag(OverdueBadgeTestTags.BADGE).assertIsDisplayed()

            onNodeWithTag(AttentionActionPanelTestTags.INTERVENTION_INPUT)
                .performScrollTo()
                .performTextInput("Borrador de intervención")

            onNodeWithTag(AttentionActionPanelTestTags.agentTab(AgentActionTab.RESPOND))
                .performScrollTo()
                .performClick()
            onNodeWithTag(AttentionActionPanelTestTags.RESPONSE_TEXT_INPUT)
                .performScrollTo()
                .performTextInput("Borrador de respuesta oficial extensa")
            onNodeWithTag(AttentionActionPanelTestTags.RESPONSE_SUBMIT_BUTTON).performScrollTo().assertIsEnabled()

            onNodeWithTag(AttentionActionPanelTestTags.agentTab(AgentActionTab.INTERVENTION))
                .performScrollTo()
                .performClick()
            onNodeWithTag(AttentionActionPanelTestTags.INTERVENTION_INPUT)
                .performScrollTo()
                .assertTextContains("Borrador de intervención")

            onNodeWithTag(AttentionActionPanelTestTags.agentTab(AgentActionTab.ESCALATE))
                .performScrollTo()
                .performClick()
            onNodeWithTag(AttentionActionPanelTestTags.ESCALATE_REASON_INPUT)
                .performScrollTo()
                .performTextInput("Requiere aprobación de coordinación académica.")
            onNodeWithTag(AttentionActionPanelTestTags.ESCALATE_SUBMIT_BUTTON)
                .performScrollTo()
                .assertIsEnabled()
                .performClick()

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(ConfirmDialogTestTags.DIALOG).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(ConfirmDialogTestTags.ACCEPT_BUTTON).performClick()

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(AttentionDetailTestTags.ACTION_ERROR_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(onAllNodesWithTag(ConfirmDialogTestTags.DIALOG).fetchSemanticsNodes().isEmpty())
            onNodeWithText("La solicitud cambió de estado concurrentemente.")
                .performScrollTo()
                .assertIsDisplayed()
            assertEquals(2, attentionRepo.detailCalls.size)
        }

    @Test
    fun `RESUELTA and CERRADA display SLA compliance label, Sistema actor, and read-only notice`() =
        runComposeUiTest {
            val detail =
                AttentionTestFixtures.requestDetail(
                    code = "SOL-2026-00099",
                    status = RequestStatus.CERRADA,
                    priority = RequestPriority.BAJA,
                    assignedAgentId = "agent-1",
                    assignedAgentName = "Carlos Andrés Mejía",
                    responseText = "Solicitud atendida y solucionada conforme al reglamento.",
                    respondedAt = AttentionTestFixtures.sampleRespondedAt,
                    closedAt = AttentionTestFixtures.sampleClosedAt,
                    slaCompliant = true,
                    history =
                        listOf(
                            AttentionTestFixtures.historyEntry(
                                id = "hist-sys",
                                eventType = "CIERRE_AUTOMATICO",
                                actorName = null,
                                actorRole = HistoryActorRole.SISTEMA,
                                previousStatus = RequestStatus.RESUELTA,
                                newStatus = RequestStatus.CERRADA,
                                detail = "Cierre automático tras 48 horas hábiles.",
                                createdAt = AttentionTestFixtures.sampleClosedAt,
                            ),
                        ),
                )

            setContent {
                ServiceDeskTheme {
                    AttentionDetailScreen(
                        uiState = AttentionDetailUiState(detail = detail),
                        userRole = UserRole.PERSONAL_ATENCION,
                        onSelectAgent = {},
                        onRetryLoadAgents = {},
                        onSelectPriority = {},
                        onReassignmentReasonChange = {},
                        onRequestAssign = {},
                        onRequestReassign = {},
                        onSelectAgentTab = {},
                        onInterventionCommentChange = {},
                        onSubmitIntervention = {},
                        onEscalationReasonChange = {},
                        onRequestEscalate = {},
                        onResponseTextChange = {},
                        onRequestRespond = {},
                        onConfirmPendingAction = {},
                        onDismissConfirmation = {},
                        onRetryLoadDetail = {},
                        onBackToList = {},
                    )
                }
            }

            onNodeWithTag(AttentionDetailTestTags.RESPONSE_CARD).performScrollTo().assertIsDisplayed()
            onNodeWithTag(AttentionDetailTestTags.SLA_COMPLIANCE_BADGE)
                .performScrollTo()
                .assertTextContains("Respondida dentro del plazo")
            onNodeWithTag(AttentionActionPanelTestTags.READ_ONLY_NOTICE).performScrollTo().assertIsDisplayed()
            onNodeWithTag(AttentionDetailTestTags.historyItem("hist-sys")).performScrollTo().assertIsDisplayed()
            onNodeWithText("Actor: Sistema").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `renders Respondida fuera del plazo when slaCompliant is false`() =
        runComposeUiTest {
            val detail =
                AttentionTestFixtures.requestDetail(
                    code = "SOL-2026-00100",
                    status = RequestStatus.RESUELTA,
                    priority = RequestPriority.ALTA,
                    responseText = "Respuesta emitida posterior al límite SLA.",
                    respondedAt = AttentionTestFixtures.sampleRespondedAt,
                    slaCompliant = false,
                )

            setContent {
                ServiceDeskTheme {
                    AttentionDetailScreen(
                        uiState = AttentionDetailUiState(detail = detail),
                        userRole = UserRole.COORDINADOR,
                        onSelectAgent = {},
                        onRetryLoadAgents = {},
                        onSelectPriority = {},
                        onReassignmentReasonChange = {},
                        onRequestAssign = {},
                        onRequestReassign = {},
                        onSelectAgentTab = {},
                        onInterventionCommentChange = {},
                        onSubmitIntervention = {},
                        onEscalationReasonChange = {},
                        onRequestEscalate = {},
                        onResponseTextChange = {},
                        onRequestRespond = {},
                        onConfirmPendingAction = {},
                        onDismissConfirmation = {},
                        onRetryLoadDetail = {},
                        onBackToList = {},
                    )
                }
            }

            onNodeWithTag(AttentionDetailTestTags.SLA_COMPLIANCE_BADGE)
                .performScrollTo()
                .assertTextContains("Respondida fuera del plazo")
            onNodeWithTag(AttentionActionPanelTestTags.READ_ONLY_NOTICE).performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `renders forbidden and generic error states with retry and back actions`() =
        runComposeUiTest {
            var retryClicks = 0
            var backClicks = 0

            setContent {
                ServiceDeskTheme {
                    AttentionDetailScreen(
                        uiState =
                            AttentionDetailUiState(
                                detail = null,
                                loadErrorMessage = "No tienes acceso a esta solicitud.",
                                isForbidden = true,
                            ),
                        userRole = UserRole.PERSONAL_ATENCION,
                        onSelectAgent = {},
                        onRetryLoadAgents = {},
                        onSelectPriority = {},
                        onReassignmentReasonChange = {},
                        onRequestAssign = {},
                        onRequestReassign = {},
                        onSelectAgentTab = {},
                        onInterventionCommentChange = {},
                        onSubmitIntervention = {},
                        onEscalationReasonChange = {},
                        onRequestEscalate = {},
                        onResponseTextChange = {},
                        onRequestRespond = {},
                        onConfirmPendingAction = {},
                        onDismissConfirmation = {},
                        onRetryLoadDetail = { retryClicks += 1 },
                        onBackToList = { backClicks += 1 },
                    )
                }
            }

            onNodeWithTag(AttentionDetailTestTags.ERROR_STATE).assertIsDisplayed()
            onNodeWithText("Acceso restringido").assertIsDisplayed()
            assertTrue(onAllNodesWithTag(AttentionDetailTestTags.RETRY_BUTTON).fetchSemanticsNodes().isEmpty())
            onNodeWithTag(AttentionDetailTestTags.BACK_TO_LIST_BUTTON).performClick()
            assertEquals(1, backClicks)
            assertEquals(0, retryClicks)
        }
}
