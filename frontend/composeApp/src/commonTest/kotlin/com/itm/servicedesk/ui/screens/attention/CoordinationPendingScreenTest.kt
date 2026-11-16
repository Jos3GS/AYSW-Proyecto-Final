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
import com.itm.servicedesk.ui.screens.home.HomeTestTags
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.attention.CoordinationPendingUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class CoordinationPendingScreenTest : ComposeUiTestBase() {
    @Test
    fun `renders pending queue with overdue badge, priority label, filters, and pagination`() =
        runComposeUiTest {
            var selectedFilter: RequestStatus? = RequestStatus.ABIERTA
            var openedCode: String? = null
            var nextClicks = 0
            var manageStaffClicks = 0
            val overdueEscalated =
                AttentionTestFixtures.requestSummary(
                    code = "SOL-2026-00019",
                    status = RequestStatus.ESCALADA,
                    priority = RequestPriority.ALTA,
                    isOverdue = true,
                    dueAt = AttentionTestFixtures.sampleDueAt,
                )

            setContent {
                ServiceDeskTheme {
                    CoordinationPendingScreen(
                        uiState =
                            CoordinationPendingUiState(
                                items = listOf(overdueEscalated),
                                currentPage = 1,
                                totalPages = 2,
                                totalItems = 25,
                                selectedStatusFilter = null,
                            ),
                        onSelectStatusFilter = { selectedFilter = it },
                        onPreviousPage = {},
                        onNextPage = { nextClicks += 1 },
                        onRetry = {},
                        onOpenDetail = { openedCode = it },
                        onManageStaffClick = { manageStaffClicks += 1 },
                    )
                }
            }

            onNodeWithTag(CoordinationPendingTestTags.FILTER_ALL).assertIsDisplayed()
            onNodeWithTag(CoordinationPendingTestTags.filterStatus(RequestStatus.ABIERTA)).assertIsDisplayed()
            onNodeWithTag(CoordinationPendingTestTags.filterStatus(RequestStatus.ESCALADA)).assertIsDisplayed()
            onNodeWithTag(OverdueBadgeTestTags.BADGE).assertIsDisplayed()
            onNodeWithText("Vencida").assertIsDisplayed()
            onNodeWithText("Alta (24 h)").assertIsDisplayed()
            onNodeWithText("14/11/2026 18:30", substring = true).assertIsDisplayed()

            onNodeWithTag(CoordinationPendingTestTags.filterStatus(RequestStatus.ESCALADA))
                .performScrollTo()
                .performClick()
            assertEquals(RequestStatus.ESCALADA, selectedFilter)

            onNodeWithTag(CoordinationPendingTestTags.FILTER_ALL).performScrollTo().performClick()
            assertNull(selectedFilter)

            onNodeWithTag(CoordinationPendingTestTags.item("SOL-2026-00019"))
                .assertIsEnabled()
                .performClick()
            assertEquals("SOL-2026-00019", openedCode)

            onNodeWithTag(CoordinationPendingTestTags.NEXT_PAGE_BUTTON).performClick()
            assertEquals(1, nextClicks)

            onNodeWithTag(HomeTestTags.MANAGE_STAFF_BUTTON).performClick()
            assertEquals(1, manageStaffClicks)
        }

    @Test
    fun `renders wide layout table header when width is at least 600dp`() =
        runComposeUiTest {
            val item = AttentionTestFixtures.requestSummary(code = "SOL-2026-00030")

            setContent {
                ServiceDeskTheme {
                    Box(
                        modifier =
                            Modifier
                                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                                .size(900.dp, 500.dp),
                    ) {
                        CoordinationPendingScreen(
                            uiState = CoordinationPendingUiState(items = listOf(item), totalItems = 1),
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
            onNodeWithTag(CoordinationPendingTestTags.item("SOL-2026-00030")).assertIsDisplayed()
        }

    @Test
    fun `renders empty state and error banner with retry`() =
        runComposeUiTest {
            var retryClicks = 0

            setContent {
                ServiceDeskTheme {
                    CoordinationPendingScreen(
                        uiState =
                            CoordinationPendingUiState(
                                items = emptyList(),
                                errorMessage = "No se pudo consultar la bandeja.",
                            ),
                        onSelectStatusFilter = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = { retryClicks += 1 },
                        onOpenDetail = {},
                    )
                }
            }

            onNodeWithTag(CoordinationPendingTestTags.ERROR_BANNER).assertIsDisplayed()
            onNodeWithText("No se pudo consultar la bandeja.").assertIsDisplayed()
            onNodeWithTag(CoordinationPendingTestTags.ERROR_RETRY_BUTTON).performClick()
            assertEquals(1, retryClicks)
        }

    @Test
    fun `renders empty state card when list is empty without error`() =
        runComposeUiTest {
            setContent {
                ServiceDeskTheme {
                    CoordinationPendingScreen(
                        uiState = CoordinationPendingUiState(items = emptyList()),
                        onSelectStatusFilter = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = {},
                        onOpenDetail = {},
                    )
                }
            }

            onNodeWithTag(CoordinationPendingTestTags.EMPTY_STATE).assertIsDisplayed()
        }
}
