package com.itm.servicedesk.ui.screens.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.data.mapper.RequestUiMapper
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.testing.RequestTestFixtures
import com.itm.servicedesk.ui.screens.ComposeUiTestBase
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.requests.MyRequestsUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class MyRequestsScreenTest : ComposeUiTestBase() {
    private val uiMapper = RequestUiMapper()

    @Test
    fun `renders paginated items, status filter chips, and triggers callbacks`() =
        runComposeUiTest {
            var selectedFilter: RequestStatus? = RequestStatus.ABIERTA
            var openedCode: String? = null
            var nextClicks = 0
            var newClicks = 0
            val item = uiMapper.toSummaryUiModel(RequestTestFixtures.requestSummary())

            setContent {
                ServiceDeskTheme {
                    MyRequestsScreen(
                        uiState =
                            MyRequestsUiState(
                                items = listOf(item),
                                page = 1,
                                size = 20,
                                totalItems = 25,
                                totalPages = 2,
                                selectedStatusFilter = null,
                                isLoading = false,
                            ),
                        onSelectStatusFilter = { selectedFilter = it },
                        onPreviousPage = {},
                        onNextPage = { nextClicks += 1 },
                        onRetry = {},
                        onNavigateToCreate = { newClicks += 1 },
                        onOpenDetail = { openedCode = it },
                    )
                }
            }

            onNodeWithTag(MyRequestsTestTags.FILTER_ALL).assertIsDisplayed()
            onNodeWithTag(MyRequestsTestTags.filterStatus(RequestStatus.ABIERTA)).assertIsDisplayed()
            onNodeWithTag(MyRequestsTestTags.LIST_CONTAINER).assertIsDisplayed()
            onNodeWithTag(MyRequestsTestTags.item("SOL-2026-00019")).assertIsDisplayed()
            onNodeWithText("14/11/2026 18:30", substring = true).assertIsDisplayed()

            onNodeWithTag(MyRequestsTestTags.filterStatus(RequestStatus.RESUELTA))
                .performScrollTo()
                .performClick()
            assertEquals(RequestStatus.RESUELTA, selectedFilter)

            onNodeWithTag(MyRequestsTestTags.FILTER_ALL).performScrollTo().performClick()
            assertNull(selectedFilter)

            onNodeWithTag(MyRequestsTestTags.item("SOL-2026-00019")).performClick()
            assertEquals("SOL-2026-00019", openedCode)

            onNodeWithTag(MyRequestsTestTags.NEXT_PAGE_BUTTON).performClick()
            assertEquals(1, nextClicks)

            onNodeWithTag(MyRequestsTestTags.NEW_BUTTON).performClick()
            assertEquals(1, newClicks)
        }

    @Test
    fun `renders wide layout table header when width is at least 600dp`() =
        runComposeUiTest {
            val item = uiMapper.toSummaryUiModel(RequestTestFixtures.requestSummary())

            setContent {
                ServiceDeskTheme {
                    Box(
                        modifier =
                            Modifier
                                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                                .size(900.dp, 500.dp),
                    ) {
                        MyRequestsScreen(
                            uiState =
                                MyRequestsUiState(
                                    items = listOf(item),
                                    page = 1,
                                    totalItems = 1,
                                    totalPages = 1,
                                    isLoading = false,
                                ),
                            onSelectStatusFilter = {},
                            onPreviousPage = {},
                            onNextPage = {},
                            onRetry = {},
                            onNavigateToCreate = {},
                            onOpenDetail = {},
                        )
                    }
                }
            }

            onNodeWithText("Radicado").assertIsDisplayed()
            onNodeWithText("SOL-2026-00019").assertIsDisplayed()
        }

    @Test
    fun `renders empty state when items are empty and no error`() =
        runComposeUiTest {
            setContent {
                ServiceDeskTheme {
                    MyRequestsScreen(
                        uiState = MyRequestsUiState(items = emptyList(), isLoading = false, errorMessage = null),
                        onSelectStatusFilter = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = {},
                        onNavigateToCreate = {},
                        onOpenDetail = {},
                    )
                }
            }
            onNodeWithTag(MyRequestsTestTags.EMPTY_STATE).assertIsDisplayed()
        }

    @Test
    fun `renders error banner and triggers retry callback`() =
        runComposeUiTest {
            var retryClicks = 0

            setContent {
                ServiceDeskTheme {
                    MyRequestsScreen(
                        uiState = MyRequestsUiState(errorMessage = "Error de red", isLoading = false),
                        onSelectStatusFilter = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = { retryClicks += 1 },
                        onNavigateToCreate = {},
                        onOpenDetail = {},
                    )
                }
            }
            onNodeWithTag(MyRequestsTestTags.ERROR_RETRY_BUTTON).performClick()
            assertEquals(1, retryClicks)
        }
}
