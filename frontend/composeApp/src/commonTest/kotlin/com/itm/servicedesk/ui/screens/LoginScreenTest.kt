package com.itm.servicedesk.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.itm.servicedesk.ui.screens.auth.LoginScreen
import com.itm.servicedesk.ui.screens.auth.LoginTestTags
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.LoginUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LoginScreenTest : ComposeUiTestBase() {
    @Test
    fun `renders login inputs, banners, and triggers callbacks`() =
        runComposeUiTest {
            var submitted = 0
            var goRegisterClicks = 0
            var capturedEmail = ""
            var capturedPassword = ""

            setContent {
                var state by remember {
                    mutableStateOf(
                        LoginUiState(
                            email = "",
                            password = "",
                            generalError = "Credenciales inválidas.",
                        ),
                    )
                }
                ServiceDeskTheme {
                    LoginScreen(
                        uiState = state,
                        noticeMessage = "Tu sesión expiró. Por favor, inicia sesión de nuevo.",
                        onEmailChange = {
                            capturedEmail = it
                            state = state.copy(email = it)
                        },
                        onPasswordChange = {
                            capturedPassword = it
                            state = state.copy(password = it)
                        },
                        onTogglePasswordVisibility = {},
                        onSubmit = { submitted += 1 },
                        onNavigateToRegister = { goRegisterClicks += 1 },
                    )
                }
            }

            onNodeWithTag(LoginTestTags.NOTICE_BANNER)
                .performScrollTo()
                .assertIsDisplayed()
                .assertTextContains("Tu sesión expiró. Por favor, inicia sesión de nuevo.")
            onNodeWithTag(LoginTestTags.ERROR_BANNER)
                .performScrollTo()
                .assertIsDisplayed()
                .assertTextContains("Credenciales inválidas.")

            onNodeWithTag(LoginTestTags.EMAIL_INPUT).performScrollTo().performTextInput("laura@itm.edu.co")
            onNodeWithTag(LoginTestTags.PASSWORD_INPUT).performScrollTo().performTextInput("Clave1234")
            onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
            onNodeWithTag(LoginTestTags.GO_REGISTER_BUTTON).performScrollTo().performClick()

            assertEquals("laura@itm.edu.co", capturedEmail)
            assertEquals("Clave1234", capturedPassword)
            assertEquals(1, submitted)
            assertEquals(1, goRegisterClicks)
        }
}
