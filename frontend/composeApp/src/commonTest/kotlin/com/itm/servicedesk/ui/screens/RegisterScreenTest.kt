package com.itm.servicedesk.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.itm.servicedesk.domain.validation.UserFormValidator
import com.itm.servicedesk.ui.screens.auth.RegisterScreen
import com.itm.servicedesk.ui.screens.auth.RegisterTestTags
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.RegisterUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RegisterScreenTest : ComposeUiTestBase() {
    @Test
    fun `renders all registration fields, field errors, and navigation callbacks`() =
        runComposeUiTest {
            var submitClicks = 0
            var goLoginClicks = 0

            setContent {
                ServiceDeskTheme {
                    RegisterScreen(
                        uiState =
                            RegisterUiState(
                                fieldErrors =
                                    mapOf(
                                        UserFormValidator.FIELD_EMAIL to "Ingresa un correo electrónico válido.",
                                    ),
                                generalError = "El correo ya está registrado.",
                            ),
                        onFullNameChange = {},
                        onDocumentNumberChange = {},
                        onEmailChange = {},
                        onPhoneChange = {},
                        onPasswordChange = {},
                        onTogglePasswordVisibility = {},
                        onSubmit = { submitClicks += 1 },
                        onNavigateToLogin = { goLoginClicks += 1 },
                    )
                }
            }

            onNodeWithTag(RegisterTestTags.ERROR_BANNER)
                .performScrollTo()
                .assertIsDisplayed()
                .assertTextContains("El correo ya está registrado.")
            onNodeWithTag(RegisterTestTags.FULL_NAME_INPUT).performScrollTo().assertIsDisplayed()
            onNodeWithTag(RegisterTestTags.DOCUMENT_INPUT).performScrollTo().assertIsDisplayed()
            onNodeWithTag(RegisterTestTags.EMAIL_INPUT).performScrollTo().assertIsDisplayed()
            onNodeWithText("Ingresa un correo electrónico válido.").assertIsDisplayed()
            onNodeWithTag(RegisterTestTags.PHONE_INPUT).performScrollTo().assertIsDisplayed()
            onNodeWithTag(RegisterTestTags.PASSWORD_INPUT).performScrollTo().assertIsDisplayed()

            onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
            onNodeWithTag(RegisterTestTags.GO_LOGIN_BUTTON).performScrollTo().performClick()

            assertEquals(1, submitClicks)
            assertEquals(1, goLoginClicks)
        }
}
