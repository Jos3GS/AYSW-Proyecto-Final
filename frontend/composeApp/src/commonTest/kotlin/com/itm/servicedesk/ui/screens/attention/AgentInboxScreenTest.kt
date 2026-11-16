package com.itm.servicedesk.ui.screens.attention

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.testing.AttentionTestFixtures
import com.itm.servicedesk.ui.components.OverdueBadgeTestTags
import com.itm.servicedesk.ui.screens.ComposeUiTestBase
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.attention.AgentInboxUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class AgentInboxScreenTest : ComposeUiTestBase() {
    @Test
    fun `renders assigned requests, overdue badge, priority labels, filters, and pagination`() =
        runComposeUiTest {
            var selectedFilter: RequestStatus? = RequestStatus.EN_REVISION
            var openedCode: String? = null
            var prevClicks = 0
            val assignedItem =
                AttentionTestFixtures.requestSummary(
                    code = "SOL-2026-00044",
                    status = RequestStatus.EN_REVISION,
                    priority = RequestPriority.MEDIA,
                    assignedAgentId = "agent-1",
                    assignedAgentName = "Carlos Andrés Mejía",
                    dueAt = AttentionTestFixtures.sampleDueAt,
                    isOverdue = true,
                )

            setContent {
                ServiceDeskTheme {
                    AgentInboxScreen(
                        uiState =
                            AgentInboxUiState(
                                items = listOf(assignedItem),
                                currentPage = 2,
                                totalPages = 2,
                                totalItems = 21,
                                selectedStatusFilter = null,
                            ),
                        onSelectStatusFilter = { selectedFilter = it },
                        onPreviousPage = { prevClicks += 1 },
                        onNextPage = {},
                        onRetry = {},
                        onOpenDetail = { openedCode = it },
                    )
                }
            }

            onNodeWithTag(AgentInboxTestTags.FILTER_ALL).assertIsDisplayed()
            onNodeWithTag(AgentInboxTestTags.filterStatus(RequestStatus.EN_REVISION)).assertIsDisplayed()
            onNodeWithTag(AgentInboxTestTags.filterStatus(RequestStatus.ESCALADA)).assertIsDisplayed()
            onNodeWithTag(AgentInboxTestTags.filterStatus(RequestStatus.RESUELTA)).assertIsDisplayed()
            onNodeWithTag(AgentInboxTestTags.filterStatus(RequestStatus.CERRADA)).assertIsDisplayed()
            onNodeWithTag(OverdueBadgeTestTags.BADGE).assertIsDisplayed()
            onNodeWithText("Media (72 h)").assertIsDisplayed()
            onNodeWithText("Vence: 15/11/2026 19:00", substring = true).assertIsDisplayed()

            onNodeWithTag(AgentInboxTestTags.filterStatus(RequestStatus.RESUELTA))
                .performScrollTo()
                .performClick()
            assertEquals(RequestStatus.RESUELTA, selectedFilter)

            onNodeWithTag(AgentInboxTestTags.FILTER_ALL).performScrollTo().performClick()
            assertNull(selectedFilter)

            onNodeWithTag(AgentInboxTestTags.item("SOL-2026-00044"))
                .assertIsEnabled()
                .performClick()
            assertEquals("SOL-2026-00044", openedCode)

            onNodeWithTag(AgentInboxTestTags.PREV_PAGE_BUTTON).performClick()
            assertEquals(1, prevClicks)
        }

    @Test
    fun `renders wide layout table header when width is at least 600dp`() =
        runComposeUiTest {
            val item =
                AttentionTestFixtures.requestSummary(
                    code = "SOL-2026-00045",
                    status = RequestStatus.EN_REVISION,
                    priority = RequestPriority.BAJA,
                )

            setContent {
                ServiceDeskTheme {
                    Box(
                        modifier =
                            Modifier
                                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                                .size(900.dp, 500.dp),
                    ) {
                        AgentInboxScreen(
                            uiState = AgentInboxUiState(items = listOf(item), totalItems = 1),
                            onSelectStatusFilter = {},
                            onPreviousPage = {},
                            onNextPage = {},
                            onRetry = {},
                            onOpenDetail = {},
                        )
                    }
                }
            }

            onNodeWithText("Radicado").assertIsDisplayed()
            onNodeWithText("Asunto y solicitante").assertIsDisplayed()
            onNodeWithText("Vencimiento / Radicada").assertExists()
            onNodeWithText("Baja (120 h)").assertExists()
            onNodeWithTag(AgentInboxTestTags.item("SOL-2026-00045")).assertIsDisplayed()
        }

    @Test
    fun `renders error banner with retry and empty state`() =
        runComposeUiTest {
            var retryClicks = 0

            setContent {
                ServiceDeskTheme {
                    AgentInboxScreen(
                        uiState =
                            AgentInboxUiState(
                                items = emptyList(),
                                errorMessage = "Error cargando asignaciones.",
                            ),
                        onSelectStatusFilter = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = { retryClicks += 1 },
                        onOpenDetail = {},
                    )
                }
            }

            onNodeWithTag(AgentInboxTestTags.ERROR_BANNER).assertIsDisplayed()
            onNodeWithTag(AgentInboxTestTags.ERROR_RETRY_BUTTON).performClick()
            assertEquals(1, retryClicks)
        }

    @Test
    fun `renders empty state card when no assigned requests`() =
        runComposeUiTest {
            setContent {
                ServiceDeskTheme {
                    AgentInboxScreen(
                        uiState = AgentInboxUiState(items = emptyList()),
                        onSelectStatusFilter = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = {},
                        onOpenDetail = {},
                    )
                }
            }

            onNodeWithTag(AgentInboxTestTags.EMPTY_STATE).assertIsDisplayed()
        }
}
