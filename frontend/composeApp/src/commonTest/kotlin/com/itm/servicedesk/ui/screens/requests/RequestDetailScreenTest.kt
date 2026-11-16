package com.itm.servicedesk.ui.screens.requests

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.itm.servicedesk.data.mapper.RequestUiMapper
import com.itm.servicedesk.domain.model.RequestHistoryAction
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.testing.RequestTestFixtures
import com.itm.servicedesk.ui.components.CancelDialogTestTags
import com.itm.servicedesk.ui.screens.ComposeUiTestBase
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.requests.RequestDetailUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RequestDetailScreenTest : ComposeUiTestBase() {
    private val uiMapper = RequestUiMapper()

    @Test
    fun `ABIERTA shows cancel button and CancelRequestDialog but hides related button`() =
        runComposeUiTest {
            var cancelConfirmed = 0
            var cancelDismissed = 0
            val detailUi = uiMapper.toDetailUiModel(RequestTestFixtures.requestDetail(status = RequestStatus.ABIERTA))

            setContent {
                ServiceDeskTheme {
                    RequestDetailScreen(
                        uiState =
                            RequestDetailUiState.Content(
                                detail = detailUi,
                                isCancelDialogOpen = true,
                                cancelReason = "Motivo de prueba",
                                actionError = "Conflicto previo",
                            ),
                        onOpenPreviousRequest = {},
                        onCreateRelatedRequest = {},
                        onOpenCancelDialog = {},
                        onCancelReasonChange = {},
                        onConfirmCancel = { cancelConfirmed += 1 },
                        onDismissCancelDialog = { cancelDismissed += 1 },
                        onRetry = {},
                        onBackToList = {},
                    )
                }
            }

            onNodeWithTag(RequestDetailTestTags.CODE).assertIsDisplayed().assertTextContains("SOL-2026-00019")
            onNodeWithTag(RequestDetailTestTags.STATUS_BADGE).assertIsDisplayed().assertTextContains("Abierta")
            onNodeWithTag(RequestDetailTestTags.ACTION_ERROR_BANNER).assertIsDisplayed()
            onNodeWithTag(RequestDetailTestTags.CANCEL_BUTTON).performScrollTo().assertIsDisplayed()
            assertTrue(onAllNodesWithTag(RequestDetailTestTags.CREATE_RELATED_BUTTON).fetchSemanticsNodes().isEmpty())
            assertTrue(onAllNodesWithTag(RequestDetailTestTags.RESPONSE_CARD).fetchSemanticsNodes().isEmpty())

            onNodeWithTag(CancelDialogTestTags.DIALOG).assertIsDisplayed()
            onNodeWithTag(CancelDialogTestTags.REASON_INPUT).assertIsDisplayed()
            onNodeWithTag(CancelDialogTestTags.CONFIRM_BUTTON).performClick()
            onNodeWithTag(CancelDialogTestTags.DISMISS_BUTTON).performClick()
            assertEquals(1, cancelConfirmed)
            assertEquals(1, cancelDismissed)
        }

    @Test
    fun `RESUELTA shows official response, timeline, and create related button without cancel button`() =
        runComposeUiTest {
            var relatedCode: String? = null
            var previousOpened: String? = null
            val detailUi =
                uiMapper.toDetailUiModel(
                    RequestTestFixtures.requestDetail(
                        status = RequestStatus.RESUELTA,
                        previousRequestCode = "SOL-2026-00012",
                        responseText = "Certificado disponible para descarga en portal académico.",
                        respondedAt = RequestTestFixtures.sampleRespondedAt,
                        history =
                            listOf(
                                RequestTestFixtures.historyEntry(),
                                RequestTestFixtures.historyEntry(
                                    id = "hist-2",
                                    action = RequestHistoryAction.RESPUESTA,
                                    previousState = RequestStatus.EN_REVISION,
                                    newState = RequestStatus.RESUELTA,
                                    occurredAt = RequestTestFixtures.sampleRespondedAt,
                                    comment = "Respuesta final registrada.",
                                ),
                            ),
                    ),
                )

            setContent {
                ServiceDeskTheme {
                    RequestDetailScreen(
                        uiState = RequestDetailUiState.Content(detail = detailUi),
                        onOpenPreviousRequest = { previousOpened = it },
                        onCreateRelatedRequest = { relatedCode = it },
                        onOpenCancelDialog = {},
                        onCancelReasonChange = {},
                        onConfirmCancel = {},
                        onDismissCancelDialog = {},
                        onRetry = {},
                        onBackToList = {},
                    )
                }
            }

            onNodeWithTag(RequestDetailTestTags.PREVIOUS_CODE_LINK).performScrollTo().performClick()
            assertEquals("SOL-2026-00012", previousOpened)

            onNodeWithTag(RequestDetailTestTags.RESPONSE_CARD).performScrollTo().assertIsDisplayed()
            onNodeWithText("Certificado disponible para descarga en portal académico.").assertIsDisplayed()
            onNodeWithTag(RequestDetailTestTags.HISTORY_LIST).performScrollTo().assertIsDisplayed()
            onNodeWithTag(RequestDetailTestTags.CREATE_RELATED_BUTTON).performScrollTo().performClick()
            assertEquals("SOL-2026-00019", relatedCode)
            assertTrue(onAllNodesWithTag(RequestDetailTestTags.CANCEL_BUTTON).fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `renders 404 error state with back to list button`() =
        runComposeUiTest {
            var backClicks = 0

            setContent {
                ServiceDeskTheme {
                    RequestDetailScreen(
                        uiState =
                            RequestDetailUiState.Error(
                                code = "SOL-2026-99999",
                                message = "Solicitud no encontrada.",
                                isNotFound = true,
                            ),
                        onOpenPreviousRequest = {},
                        onCreateRelatedRequest = {},
                        onOpenCancelDialog = {},
                        onCancelReasonChange = {},
                        onConfirmCancel = {},
                        onDismissCancelDialog = {},
                        onRetry = {},
                        onBackToList = { backClicks += 1 },
                    )
                }
            }

            onNodeWithTag(RequestDetailTestTags.ERROR_STATE).assertIsDisplayed()
            onNodeWithText("Solicitud no encontrada.").assertIsDisplayed()
            onNodeWithTag(RequestDetailTestTags.BACK_TO_LIST_BUTTON).performClick()
            assertEquals(1, backClicks)
        }
}
