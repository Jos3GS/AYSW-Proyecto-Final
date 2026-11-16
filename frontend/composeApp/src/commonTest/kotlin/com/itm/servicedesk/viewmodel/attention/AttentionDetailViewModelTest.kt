package com.itm.servicedesk.viewmodel.attention

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.validation.AttentionFormValidator
import com.itm.servicedesk.testing.AttentionTestFixtures
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.FakeAttentionRepository
import com.itm.servicedesk.testing.FakeStaffRepository
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttentionDetailViewModelTest {
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
    fun `COORDINADOR loads all multi-page PERSONAL_ATENCION staff when ABIERTA or ESCALADA`() =
        runTest(testDispatcher) {
            val attentionRepo =
                FakeAttentionRepository(
                    getInternalDetailHandler = { code ->
                        AppResult.Success(
                            AttentionTestFixtures.requestDetail(code = code, status = RequestStatus.ABIERTA),
                        )
                    },
                )
            val page1Agents =
                (1..20).map { idx ->
                    AuthTestFixtures.staffMember(
                        id = "agent-$idx",
                        fullName = "Agente $idx",
                        role = UserRole.PERSONAL_ATENCION,
                    )
                }
            val page2Agents =
                listOf(
                    AuthTestFixtures.staffMember(
                        id = "agent-21",
                        fullName = "Agente 21",
                        role = UserRole.PERSONAL_ATENCION,
                    ),
                    AuthTestFixtures.staffMember(
                        id = "coord-1",
                        fullName = "Coordinador Extra",
                        role = UserRole.COORDINADOR,
                    ),
                )
            val staffRepo =
                FakeStaffRepository(
                    listStaffHandler = { page, size, roleFilter ->
                        assertEquals(UserRole.PERSONAL_ATENCION, roleFilter)
                        val items = if (page == 1) page1Agents else page2Agents
                        AppResult.Success(
                            AuthTestFixtures.staffPage(
                                items = items,
                                page = page,
                                size = size,
                                totalItems = 21,
                                totalPages = 2,
                            ),
                        )
                    },
                )

            val viewModel =
                AttentionDetailViewModel(
                    attentionRepository = attentionRepo,
                    staffRepository = staffRepo,
                    dispatcher = testDispatcher,
                )

            viewModel.loadDetail("SOL-2026-00019", UserRole.COORDINADOR)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, staffRepo.listCalls.size)
            assertEquals(21, state.availableAgents.size)
            assertEquals("agent-21", state.availableAgents.last().id)

            // Assign flow requires confirmation
            viewModel.onSelectAgent("agent-21")
            viewModel.onSelectPriority(RequestPriority.ALTA)
            viewModel.requestAssignConfirmation()
            val pending = assertIs<PendingConfirmationAction.Assign>(viewModel.uiState.value.pendingConfirmation)
            assertEquals("Agente 21", pending.agentName)
            assertEquals(RequestPriority.ALTA, pending.command.priority)

            viewModel.confirmPendingAction()
            advanceUntilIdle()
            assertEquals(1, attentionRepo.assignCalls.size)
            assertNull(viewModel.uiState.value.pendingConfirmation)
            assertEquals(
                RequestStatus.EN_REVISION,
                viewModel.uiState.value.detail
                    ?.status,
            )
            assertTrue(
                viewModel.uiState.value.availableAgents
                    .isEmpty(),
            )
        }

    @Test
    fun `PERSONAL_ATENCION never invokes StaffRepository even if detail is ABIERTA or EN_REVISION`() =
        runTest(testDispatcher) {
            val attentionRepo =
                FakeAttentionRepository(
                    getInternalDetailHandler = { code ->
                        AppResult.Success(
                            AttentionTestFixtures.requestDetail(
                                code = code,
                                status = RequestStatus.EN_REVISION,
                                priority = RequestPriority.ALTA,
                                isOverdue = true,
                            ),
                        )
                    },
                )
            val staffRepo = FakeStaffRepository()
            val viewModel =
                AttentionDetailViewModel(
                    attentionRepository = attentionRepo,
                    staffRepository = staffRepo,
                    dispatcher = testDispatcher,
                )

            viewModel.loadDetail("SOL-2026-00019", UserRole.PERSONAL_ATENCION)
            advanceUntilIdle()

            assertTrue(staffRepo.listCalls.isEmpty())
            assertTrue(
                viewModel.uiState.value.availableAgents
                    .isEmpty(),
            )

            // Even if status were ABIERTA, PERSONAL_ATENCION must never call StaffRepository
            attentionRepo.getInternalDetailHandler = { code ->
                AppResult.Success(AttentionTestFixtures.requestDetail(code = code, status = RequestStatus.ABIERTA))
            }
            viewModel.retryLoadDetail()
            advanceUntilIdle()
            assertTrue(staffRepo.listCalls.isEmpty())
        }

    @Test
    fun `preserves drafts across AgentActionTab switches and runs intervention, escalate, respond`() =
        runTest(testDispatcher) {
            val attentionRepo =
                FakeAttentionRepository(
                    getInternalDetailHandler = { code ->
                        AppResult.Success(
                            AttentionTestFixtures.requestDetail(
                                code = code,
                                status = RequestStatus.EN_REVISION,
                                priority = RequestPriority.MEDIA,
                                isOverdue = true,
                            ),
                        )
                    },
                )
            val staffRepo = FakeStaffRepository()
            val viewModel =
                AttentionDetailViewModel(
                    attentionRepository = attentionRepo,
                    staffRepository = staffRepo,
                    dispatcher = testDispatcher,
                )

            viewModel.loadDetail("SOL-2026-00019", UserRole.PERSONAL_ATENCION)
            advanceUntilIdle()

            // Draft in INTERVENTION
            viewModel.onInterventionCommentChange("Nota interna en proceso")
            // Switch to ESCALATE and type draft
            viewModel.onSelectAgentTab(AgentActionTab.ESCALATE)
            viewModel.onEscalationReasonChange("Requiere autorización de decanatura")
            // Switch to RESPOND and type draft
            viewModel.onSelectAgentTab(AgentActionTab.RESPOND)
            viewModel.onResponseTextChange("Respuesta definitiva emitida para el solicitante.")

            // Switch back and verify all 3 drafts remain isolated and intact
            viewModel.onSelectAgentTab(AgentActionTab.INTERVENTION)
            val state = viewModel.uiState.value
            assertEquals("Nota interna en proceso", state.interventionCommentDraft)
            assertEquals("Requiere autorización de decanatura", state.escalationReasonDraft)
            assertEquals("Respuesta definitiva emitida para el solicitante.", state.responseTextDraft)

            // Submit intervention directly without confirmation dialog
            viewModel.submitIntervention()
            advanceUntilIdle()
            assertEquals(1, attentionRepo.interventionCalls.size)
            assertEquals("", viewModel.uiState.value.interventionCommentDraft)
            assertEquals("Requiere autorización de decanatura", viewModel.uiState.value.escalationReasonDraft)
            assertEquals(
                "Respuesta definitiva emitida para el solicitante.",
                viewModel.uiState.value.responseTextDraft,
            )

            // Escalate requires confirmation
            viewModel.onSelectAgentTab(AgentActionTab.ESCALATE)
            viewModel.requestEscalateConfirmation()
            assertIs<PendingConfirmationAction.Escalate>(viewModel.uiState.value.pendingConfirmation)
            viewModel.dismissConfirmation()
            assertNull(viewModel.uiState.value.pendingConfirmation)

            // Respond requires confirmation
            viewModel.onSelectAgentTab(AgentActionTab.RESPOND)
            viewModel.requestRespondConfirmation()
            assertIs<PendingConfirmationAction.Respond>(viewModel.uiState.value.pendingConfirmation)
            viewModel.confirmPendingAction()
            advanceUntilIdle()
            assertEquals(1, attentionRepo.respondCalls.size)
            assertEquals(
                RequestStatus.RESUELTA,
                viewModel.uiState.value.detail
                    ?.status,
            )
            assertEquals("", viewModel.uiState.value.responseTextDraft)
        }

    @Test
    fun `409 Conflict closes confirmation dialog, shows server message, and automatically reloads internal detail`() =
        runTest(testDispatcher) {
            var currentDetail =
                AttentionTestFixtures.requestDetail(
                    code = "SOL-2026-00019",
                    status = RequestStatus.ABIERTA,
                )
            val updatedByAnotherCoord =
                AttentionTestFixtures.requestDetail(
                    code = "SOL-2026-00019",
                    status = RequestStatus.EN_REVISION,
                    priority = RequestPriority.ALTA,
                    assignedAgentId = "agent-9",
                    assignedAgentName = "Otro Agente",
                )
            val attentionRepo =
                FakeAttentionRepository(
                    getInternalDetailHandler = { AppResult.Success(currentDetail) },
                    assignHandler = { _, _ ->
                        currentDetail = updatedByAnotherCoord
                        AppResult.Failure(DomainError.Conflict("La solicitud ya fue asignada por otro coordinador."))
                    },
                )
            val staffRepo = FakeStaffRepository()
            val viewModel =
                AttentionDetailViewModel(
                    attentionRepository = attentionRepo,
                    staffRepository = staffRepo,
                    dispatcher = testDispatcher,
                )

            viewModel.loadDetail("SOL-2026-00019", UserRole.COORDINADOR)
            advanceUntilIdle()

            viewModel.onSelectAgent("a1b2c3d4-e5f6-47a8-b9c0-112233445566")
            viewModel.onSelectPriority(RequestPriority.MEDIA)
            viewModel.requestAssignConfirmation()
            assertNotNull(viewModel.uiState.value.pendingConfirmation)

            viewModel.confirmPendingAction()
            advanceUntilIdle()

            val afterConflict = viewModel.uiState.value
            assertNull(afterConflict.pendingConfirmation)
            assertEquals(
                "La solicitud ya fue asignada por otro coordinador.",
                afterConflict.actionErrorMessage,
            )
            assertEquals(RequestStatus.EN_REVISION, afterConflict.detail?.status)
            assertEquals(2, attentionRepo.detailCalls.size)
        }

    @Test
    fun `handles reassignment validation, agent load retry, and 403 or 404 load errors`() =
        runTest(testDispatcher) {
            var failAgents = true
            val attentionRepo =
                FakeAttentionRepository(
                    getInternalDetailHandler = { code ->
                        when (code) {
                            "SOL-403" -> {
                                AppResult.Failure(DomainError.Forbidden("Acceso denegado."))
                            }

                            "SOL-404" -> {
                                AppResult.Failure(DomainError.NotFound("No encontrada."))
                            }

                            else -> {
                                AppResult.Success(
                                    AttentionTestFixtures.requestDetail(
                                        code = code,
                                        status = RequestStatus.ESCALADA,
                                        priority = RequestPriority.BAJA,
                                    ),
                                )
                            }
                        }
                    },
                )
            val staffRepo =
                FakeStaffRepository(
                    listStaffHandler = { page, size, _ ->
                        if (failAgents) {
                            AppResult.Failure(DomainError.NetworkUnavailable("Error al cargar agentes."))
                        } else {
                            AppResult.Success(AuthTestFixtures.staffPage(page = page, size = size))
                        }
                    },
                )

            val viewModel =
                AttentionDetailViewModel(
                    attentionRepository = attentionRepo,
                    staffRepository = staffRepo,
                    dispatcher = testDispatcher,
                )

            viewModel.loadDetail("SOL-403", UserRole.PERSONAL_ATENCION)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isForbidden)

            viewModel.loadDetail("SOL-404", UserRole.COORDINADOR)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isNotFound)

            viewModel.loadDetail("SOL-2026-00019", UserRole.COORDINADOR)
            advanceUntilIdle()
            assertEquals("Error al cargar agentes.", viewModel.uiState.value.agentsLoadError)

            failAgents = false
            viewModel.retryLoadAgents()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.agentsLoadError)
            assertEquals(1, viewModel.uiState.value.availableAgents.size)

            // Reassign validation failure keeps dialog closed
            viewModel.onSelectAgent("")
            viewModel.onReassignmentReasonChange("corta")
            viewModel.requestReassignConfirmation()
            assertNull(viewModel.uiState.value.pendingConfirmation)
            assertNotNull(viewModel.uiState.value.validationErrors[AttentionFormValidator.FIELD_ASSIGNEE_ID])
            assertNotNull(viewModel.uiState.value.validationErrors[AttentionFormValidator.FIELD_REASON])

            // Valid reassignment without priority override
            viewModel.onSelectAgent("a1b2c3d4-e5f6-47a8-b9c0-112233445566")
            viewModel.onReassignmentReasonChange("Reasignación por disponibilidad del equipo.")
            viewModel.requestReassignConfirmation()
            val pendingReassign =
                assertIs<PendingConfirmationAction.Reassign>(viewModel.uiState.value.pendingConfirmation)
            assertNull(pendingReassign.command.priority)

            viewModel.confirmPendingAction()
            advanceUntilIdle()
            assertEquals(1, attentionRepo.reassignCalls.size)
            assertEquals(
                RequestStatus.EN_REVISION,
                viewModel.uiState.value.detail
                    ?.status,
            )
        }
}
