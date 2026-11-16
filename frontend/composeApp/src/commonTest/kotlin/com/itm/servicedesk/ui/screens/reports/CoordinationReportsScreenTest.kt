package com.itm.servicedesk.ui.screens.reports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.data.mapper.ReportUiMapper
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.ReportTestFixtures
import com.itm.servicedesk.ui.components.OverdueBadgeTestTags
import com.itm.servicedesk.ui.components.ReportFilterPanelTestTags
import com.itm.servicedesk.ui.components.ReportSummarySectionTestTags
import com.itm.servicedesk.ui.screens.ComposeUiTestBase
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.reports.CoordinationReportsUiState
import com.itm.servicedesk.viewmodel.reports.ReportFilterDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class CoordinationReportsScreenTest : ComposeUiTestBase() {
    private val uiMapper = ReportUiMapper()

    @Test
    fun `renders KPI summary, breakdowns, overdue and SLA badges, pagination, and row click`() =
        runComposeUiTest {
            val summaryUi = uiMapper.toSummaryUi(ReportTestFixtures.summary())
            val overdueOpen =
                ReportTestFixtures.requestItem(
                    code = "SOL-2026-00019",
                    status = RequestStatus.ESCALADA,
                    priority = RequestPriority.ALTA,
                    isOverdue = true,
                    respondedWithinSla = null,
                )
            val resolvedWithin =
                ReportTestFixtures.requestItem(
                    code = "SOL-2026-00020",
                    status = RequestStatus.RESUELTA,
                    priority = RequestPriority.MEDIA,
                    isOverdue = false,
                    respondedAt = ReportTestFixtures.sampleRespondedAt,
                    respondedWithinSla = true,
                )
            val pageUi =
                uiMapper.toRequestsPageUi(
                    ReportTestFixtures.requestsPage(
                        items = listOf(overdueOpen, resolvedWithin),
                        page = 1,
                        size = 20,
                        totalItems = 25L,
                        totalPages = 2,
                    ),
                )
            var openedCode: String? = null
            var nextClicks = 0

            setContent {
                ServiceDeskTheme {
                    CoordinationReportsScreen(
                        uiState =
                            CoordinationReportsUiState(
                                summary = summaryUi,
                                requestsPage = pageUi,
                                availableAgents = listOf(AuthTestFixtures.staffMember(id = "agent-1")),
                                currentPage = 1,
                            ),
                        onFromDateChanged = {},
                        onToDateChanged = {},
                        onStatusFilterChanged = {},
                        onCategoryFilterChanged = {},
                        onPriorityFilterChanged = {},
                        onAssigneeFilterChanged = {},
                        onApplyFilters = {},
                        onClearFilters = {},
                        onPreviousPage = {},
                        onNextPage = { nextClicks += 1 },
                        onRetry = {},
                        onOpenDetail = { openedCode = it },
                    )
                }
            }

            onNodeWithTag(ReportFilterPanelTestTags.PANEL).assertIsDisplayed()
            onNodeWithTag(CoordinationReportsTestTags.LIST_CONTAINER)
                .performScrollToNode(hasTestTag(ReportSummarySectionTestTags.SECTION))
            onNodeWithTag(ReportSummarySectionTestTags.SECTION).assertIsDisplayed()
            onNodeWithTag(ReportSummarySectionTestTags.KPI_TOTAL).assertIsDisplayed()
            onNodeWithTag(ReportSummarySectionTestTags.KPI_OVERDUE).assertIsDisplayed()
            onNodeWithTag(ReportSummarySectionTestTags.KPI_WITHIN_SLA).assertIsDisplayed()
            onNodeWithTag(ReportSummarySectionTestTags.KPI_OUTSIDE_SLA).assertIsDisplayed()
            onNodeWithText("15/11/2026 10:30", substring = true).assertIsDisplayed()

            onNodeWithTag(ReportSummarySectionTestTags.PRIORITY_CARD).performScrollTo()
            onNodeWithText("Sin prioridad").performScrollTo().assertIsDisplayed()
            onNodeWithTag(ReportSummarySectionTestTags.ASSIGNEE_CARD).performScrollTo()
            onNodeWithText("Sin responsable").performScrollTo().assertIsDisplayed()

            onNodeWithTag(CoordinationReportsTestTags.LIST_CONTAINER)
                .performScrollToNode(hasTestTag(CoordinationReportsTestTags.item("SOL-2026-00019")))
            onNodeWithTag(OverdueBadgeTestTags.BADGE).assertIsDisplayed()

            onNodeWithTag(CoordinationReportsTestTags.LIST_CONTAINER)
                .performScrollToNode(hasTestTag(CoordinationReportsTestTags.item("SOL-2026-00020")))
            onNodeWithTag(CoordinationReportsTestTags.slaBadge("SOL-2026-00020")).assertIsDisplayed()
            onNodeWithText("Dentro del plazo").assertIsDisplayed()

            onNodeWithTag(CoordinationReportsTestTags.item("SOL-2026-00019")).performClick()
            assertEquals("SOL-2026-00019", openedCode)

            onNodeWithTag(CoordinationReportsTestTags.LIST_CONTAINER)
                .performScrollToNode(hasTestTag(CoordinationReportsTestTags.NEXT_PAGE_BUTTON))
            onNodeWithTag(CoordinationReportsTestTags.PREV_PAGE_BUTTON).assertIsNotEnabled()
            onNodeWithTag(CoordinationReportsTestTags.NEXT_PAGE_BUTTON).assertIsEnabled().performClick()
            assertEquals(1, nextClicks)
        }

    @Test
    fun `renders wide layout and handles filter callbacks and inline date errors`() =
        runComposeUiTest {
            var fromInput = ""
            var toInput = ""
            var selectedStatus: RequestStatus? = null
            var selectedCategory: RequestCategory? = null
            var selectedPriority: RequestPriority? = null
            var selectedAssignee: String? = null
            var applyClicks = 0
            var clearClicks = 0

            val summaryUi = uiMapper.toSummaryUi(ReportTestFixtures.summary())
            val pageUi = uiMapper.toRequestsPageUi(ReportTestFixtures.requestsPage())
            val agent = AuthTestFixtures.staffMember(id = "agent-1", fullName = "Carlos Andrés Mejía")

            setContent {
                var draftState by remember {
                    mutableStateOf(
                        ReportFilterDraft(
                            fromDateInput = "20/11/2026",
                            toDateInput = "10/11/2026",
                            toDateError = "La fecha final no puede ser anterior a la fecha inicial.",
                        ),
                    )
                }
                ServiceDeskTheme {
                    Box(
                        modifier =
                            Modifier
                                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                                .size(960.dp, 800.dp),
                    ) {
                        CoordinationReportsScreen(
                            uiState =
                                CoordinationReportsUiState(
                                    summary = summaryUi,
                                    requestsPage = pageUi,
                                    availableAgents = listOf(agent),
                                    draftFilter = draftState,
                                ),
                            onFromDateChanged = {
                                fromInput = it
                                draftState = draftState.copy(fromDateInput = it)
                            },
                            onToDateChanged = {
                                toInput = it
                                draftState = draftState.copy(toDateInput = it)
                            },
                            onStatusFilterChanged = { selectedStatus = it },
                            onCategoryFilterChanged = { selectedCategory = it },
                            onPriorityFilterChanged = { selectedPriority = it },
                            onAssigneeFilterChanged = { selectedAssignee = it },
                            onApplyFilters = { applyClicks += 1 },
                            onClearFilters = { clearClicks += 1 },
                            onPreviousPage = {},
                            onNextPage = {},
                            onRetry = {},
                            onOpenDetail = {},
                        )
                    }
                }
            }

            onNodeWithText("La fecha final no puede ser anterior a la fecha inicial.").assertExists()

            onNodeWithTag(ReportFilterPanelTestTags.FROM_DATE_INPUT)
                .performScrollTo()
                .performTextReplacement("01/11/2026")
            onNodeWithTag(ReportFilterPanelTestTags.TO_DATE_INPUT)
                .performScrollTo()
                .performTextReplacement("14/11/2026")

            onNodeWithTag(ReportFilterPanelTestTags.STATUS_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(ReportFilterPanelTestTags.statusOption(RequestStatus.ESCALADA)).performClick()
            assertEquals(RequestStatus.ESCALADA, selectedStatus)

            onNodeWithTag(ReportFilterPanelTestTags.CATEGORY_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(ReportFilterPanelTestTags.categoryOption(RequestCategory.TRAMITE)).performClick()
            assertEquals(RequestCategory.TRAMITE, selectedCategory)

            onNodeWithTag(ReportFilterPanelTestTags.PRIORITY_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(ReportFilterPanelTestTags.priorityOption(RequestPriority.ALTA)).performClick()
            assertEquals(RequestPriority.ALTA, selectedPriority)

            onNodeWithTag(ReportFilterPanelTestTags.ASSIGNEE_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(ReportFilterPanelTestTags.assigneeOption("agent-1")).performClick()
            assertEquals("agent-1", selectedAssignee)

            onNodeWithTag(ReportFilterPanelTestTags.APPLY_BUTTON).performScrollTo().performClick()
            onNodeWithTag(ReportFilterPanelTestTags.CLEAR_BUTTON).performScrollTo().performClick()

            assertEquals(1, applyClicks)
            assertEquals(1, clearClicks)
            assertEquals("01/11/2026", fromInput)
            assertEquals("14/11/2026", toInput)

            onNodeWithTag(CoordinationReportsTestTags.LIST_CONTAINER)
                .performScrollToNode(hasText("Asunto y solicitante"))
            onNodeWithText("Asunto y solicitante").assertExists()
        }

    @Test
    fun `renders empty state and error banner with retry`() =
        runComposeUiTest {
            var retryClicks = 0
            val emptySummaryUi = uiMapper.toSummaryUi(ReportTestFixtures.emptySummary())
            val emptyPageUi =
                uiMapper.toRequestsPageUi(
                    ReportTestFixtures.requestsPage(
                        items = emptyList(),
                        page = 1,
                        size = 20,
                        totalItems = 0L,
                        totalPages = 1,
                    ),
                )

            setContent {
                ServiceDeskTheme {
                    CoordinationReportsScreen(
                        uiState =
                            CoordinationReportsUiState(
                                summary = emptySummaryUi,
                                requestsPage = emptyPageUi,
                                errorMessage = "No fue posible consultar los reportes.",
                            ),
                        onFromDateChanged = {},
                        onToDateChanged = {},
                        onStatusFilterChanged = {},
                        onCategoryFilterChanged = {},
                        onPriorityFilterChanged = {},
                        onAssigneeFilterChanged = {},
                        onApplyFilters = {},
                        onClearFilters = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = { retryClicks += 1 },
                        onOpenDetail = {},
                    )
                }
            }

            onNodeWithTag(CoordinationReportsTestTags.ERROR_BANNER).assertIsDisplayed()
            onNodeWithText("No fue posible consultar los reportes.").assertIsDisplayed()
            onNodeWithTag(CoordinationReportsTestTags.RETRY_BUTTON).performClick()
            assertEquals(1, retryClicks)

            onNodeWithTag(CoordinationReportsTestTags.LIST_CONTAINER)
                .performScrollToNode(hasTestTag(CoordinationReportsTestTags.EMPTY_STATE))
            onNodeWithText("No se encontraron solicitudes con los filtros aplicados.").assertIsDisplayed()
        }

    @Test
    fun `clears dropdown selections when Todos or Todas is chosen`() =
        runComposeUiTest {
            var selectedStatus: RequestStatus? = RequestStatus.ABIERTA
            var selectedCategory: RequestCategory? = RequestCategory.TRAMITE
            var selectedPriority: RequestPriority? = RequestPriority.ALTA
            var selectedAssignee: String? = "agent-1"

            setContent {
                ServiceDeskTheme {
                    CoordinationReportsScreen(
                        uiState =
                            CoordinationReportsUiState(
                                availableAgents = listOf(AuthTestFixtures.staffMember(id = "agent-1")),
                                draftFilter =
                                    ReportFilterDraft(
                                        selectedStatus = selectedStatus,
                                        selectedCategory = selectedCategory,
                                        selectedPriority = selectedPriority,
                                        selectedAssigneeId = selectedAssignee,
                                    ),
                            ),
                        onFromDateChanged = {},
                        onToDateChanged = {},
                        onStatusFilterChanged = { selectedStatus = it },
                        onCategoryFilterChanged = { selectedCategory = it },
                        onPriorityFilterChanged = { selectedPriority = it },
                        onAssigneeFilterChanged = { selectedAssignee = it },
                        onApplyFilters = {},
                        onClearFilters = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = {},
                        onOpenDetail = {},
                    )
                }
            }

            onNodeWithTag(ReportFilterPanelTestTags.STATUS_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(ReportFilterPanelTestTags.statusOption(null)).performClick()
            assertNull(selectedStatus)

            onNodeWithTag(ReportFilterPanelTestTags.CATEGORY_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(ReportFilterPanelTestTags.categoryOption(null)).performClick()
            assertNull(selectedCategory)

            onNodeWithTag(ReportFilterPanelTestTags.PRIORITY_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(ReportFilterPanelTestTags.priorityOption(null)).performClick()
            assertNull(selectedPriority)

            onNodeWithTag(ReportFilterPanelTestTags.ASSIGNEE_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(ReportFilterPanelTestTags.assigneeOption(null)).performClick()
            assertNull(selectedAssignee)
        }
}
