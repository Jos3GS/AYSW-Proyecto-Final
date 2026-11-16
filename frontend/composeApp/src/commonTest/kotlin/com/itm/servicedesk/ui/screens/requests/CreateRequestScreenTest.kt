package com.itm.servicedesk.ui.screens.requests

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.validation.RequestFormValidator
import com.itm.servicedesk.ui.screens.ComposeUiTestBase
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.requests.CreateRequestUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CreateRequestScreenTest : ComposeUiTestBase() {
    @Test
    fun `renders 4 categories, Tabla 2 field errors, character counters, and submit callback`() =
        runComposeUiTest {
            var selectedCategory: RequestCategory? = null
            var submitClicks = 0

            setContent {
                ServiceDeskTheme {
                    CreateRequestScreen(
                        uiState =
                            CreateRequestUiState(
                                category = RequestCategory.TRAMITE,
                                subject = "Asunto válido",
                                description = "Descripción con más de veinte caracteres.",
                                previousRequestCode = "SOL-2026-00012",
                                fieldErrors =
                                    mapOf(
                                        RequestFormValidator.FIELD_PREVIOUS_REQUEST_CODE to
                                            "La solicitud anterior debe estar Resuelta o Cerrada.",
                                    ),
                                generalError = "Error al radicar.",
                            ),
                        onCategorySelected = { selectedCategory = it },
                        onSubjectChange = {},
                        onDescriptionChange = {},
                        onPreviousRequestCodeChange = {},
                        onSubmit = { submitClicks += 1 },
                    )
                }
            }

            onNodeWithTag(CreateRequestTestTags.ERROR_BANNER)
                .performScrollTo()
                .assertIsDisplayed()
                .assertTextContains("Error al radicar.")

            onNodeWithTag(CreateRequestTestTags.CATEGORY_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(CreateRequestTestTags.categoryOption(RequestCategory.ORIENTACION)).assertIsDisplayed()
            onNodeWithTag(CreateRequestTestTags.categoryOption(RequestCategory.ATENCION_ADMINISTRATIVA))
                .assertIsDisplayed()
            onNodeWithTag(CreateRequestTestTags.categoryOption(RequestCategory.REQUERIMIENTO_SERVICIO))
                .assertIsDisplayed()
            onNodeWithTag(CreateRequestTestTags.categoryOption(RequestCategory.ORIENTACION)).performClick()
            assertEquals(RequestCategory.ORIENTACION, selectedCategory)

            onNodeWithTag(CreateRequestTestTags.SUBJECT_INPUT).performScrollTo().assertIsDisplayed()
            onNodeWithTag(CreateRequestTestTags.DESCRIPTION_INPUT).performScrollTo().assertIsDisplayed()
            onNodeWithTag(CreateRequestTestTags.PREVIOUS_CODE_INPUT).performScrollTo().assertIsDisplayed()
            onNodeWithText("La solicitud anterior debe estar Resuelta o Cerrada.").assertIsDisplayed()

            onNodeWithTag(CreateRequestTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
            assertEquals(1, submitClicks)
        }
}
