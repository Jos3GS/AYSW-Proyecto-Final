package com.itm.servicedesk.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.ui.components.AuthenticatedTopBar
import com.itm.servicedesk.ui.components.ConfirmDialogTestTags
import com.itm.servicedesk.ui.components.ConfirmationDialog
import com.itm.servicedesk.ui.components.RequireRole
import com.itm.servicedesk.ui.components.RequireRoleTestTags
import com.itm.servicedesk.ui.components.TopBarTestTags
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.SessionUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RoleGuardAndLogoutTest : ComposeUiTestBase() {
    @Test
    fun `RequireRole allows COORDINADOR and blocks SOLICITANTE`() =
        runComposeUiTest {
            var goHomeRole: UserRole? = null
            val applicant = AuthTestFixtures.userProfile(role = UserRole.SOLICITANTE)

            setContent {
                ServiceDeskTheme {
                    RequireRole(
                        sessionState = SessionUiState.Authenticated(user = applicant),
                        allowedRoles = setOf(UserRole.COORDINADOR),
                        onGoHome = { goHomeRole = it },
                        onRequireLogin = {},
                    ) {
                        Text("Contenido exclusivo de coordinación")
                    }
                }
            }

            onNodeWithTag(RequireRoleTestTags.FORBIDDEN_CARD).assertIsDisplayed()
            onNodeWithTag(RequireRoleTestTags.GO_HOME_BUTTON).performClick()
            assertEquals(UserRole.SOLICITANTE, goHomeRole)
        }

    @Test
    fun `AuthenticatedTopBar and logout ConfirmationDialog trigger expected callbacks`() =
        runComposeUiTest {
            var logoutIconClicks = 0
            var confirmLogoutClicks = 0

            setContent {
                var showDialog by remember { mutableStateOf(false) }
                ServiceDeskTheme {
                    AuthenticatedTopBar(
                        title = "Inicio",
                        userFullName = "Laura Gómez",
                        roleLabel = "Solicitante",
                        onLogoutClick = {
                            logoutIconClicks += 1
                            showDialog = true
                        },
                    )
                    if (showDialog) {
                        ConfirmationDialog(
                            title = "Cerrar sesión",
                            message = "¿Deseas cerrar tu sesión actual?",
                            confirmText = "Cerrar sesión",
                            onConfirm = { confirmLogoutClicks += 1 },
                            onDismiss = { showDialog = false },
                        )
                    }
                }
            }

            onNodeWithTag(TopBarTestTags.LOGOUT_BUTTON).performClick()
            onNodeWithText("¿Deseas cerrar tu sesión actual?").assertIsDisplayed()
            onNodeWithTag(ConfirmDialogTestTags.ACCEPT_BUTTON).performClick()

            assertEquals(1, logoutIconClicks)
            assertEquals(1, confirmLogoutClicks)
        }

    @Test
    fun `RequireRole handles Unauthenticated and TopBar handles back click`() =
        runComposeUiTest {
            var requireLoginCalls = 0
            var backClicks = 0

            setContent {
                ServiceDeskTheme {
                    RequireRole(
                        sessionState = SessionUiState.Unauthenticated(),
                        allowedRoles = setOf(UserRole.COORDINADOR),
                        onGoHome = {},
                        onRequireLogin = { requireLoginCalls += 1 },
                    ) {
                        Text("Oculto")
                    }
                    AuthenticatedTopBar(
                        title = "Personal interno",
                        userFullName = "Ana",
                        roleLabel = "Coordinador",
                        onLogoutClick = {},
                        onBackClick = { backClicks += 1 },
                    )
                }
            }

            waitForIdle()
            onNodeWithTag(TopBarTestTags.BACK_BUTTON).performClick()

            assertEquals(1, requireLoginCalls)
            assertEquals(1, backClicks)
        }
}
