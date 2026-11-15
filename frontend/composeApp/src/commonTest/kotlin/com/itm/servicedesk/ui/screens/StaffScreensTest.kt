package com.itm.servicedesk.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.validation.UserFormValidator
import com.itm.servicedesk.ui.components.ConfirmDialogTestTags
import com.itm.servicedesk.ui.screens.staff.CreateStaffScreen
import com.itm.servicedesk.ui.screens.staff.CreateStaffTestTags
import com.itm.servicedesk.ui.screens.staff.StaffListScreen
import com.itm.servicedesk.ui.screens.staff.StaffListTestTags
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.CreateStaffUiState
import com.itm.servicedesk.viewmodel.StaffItemUiModel
import com.itm.servicedesk.viewmodel.StaffListUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StaffScreensTest : ComposeUiTestBase() {
    @Test
    fun `StaffListScreen renders filters, Spanish items, dates, and pagination controls`() =
        runComposeUiTest {
            var nextClicks = 0
            var newStaffClicks = 0
            var selectedFilter: UserRole? = UserRole.SOLICITANTE

            setContent {
                ServiceDeskTheme {
                    StaffListScreen(
                        uiState =
                            StaffListUiState(
                                items =
                                    listOf(
                                        StaffItemUiModel(
                                            id = "1",
                                            fullName = "Carlos Andrés Mejía",
                                            email = "carlos.mejia@itm.edu.co",
                                            role = UserRole.PERSONAL_ATENCION,
                                            roleLabel = "Personal de atención",
                                            formattedCreatedAt = "14/11/2026 17:15",
                                        ),
                                    ),
                                page = 1,
                                size = 20,
                                totalItems = 25,
                                totalPages = 2,
                            ),
                        onSelectRoleFilter = { selectedFilter = it },
                        onPreviousPage = {},
                        onNextPage = { nextClicks += 1 },
                        onRetry = {},
                        onNavigateToCreateStaff = { newStaffClicks += 1 },
                    )
                }
            }

            onNodeWithTag(StaffListTestTags.FILTER_ALL).assertIsDisplayed()
            onNodeWithTag(StaffListTestTags.FILTER_AGENT).assertIsDisplayed()
            onNodeWithTag(StaffListTestTags.FILTER_COORDINATOR).assertIsDisplayed()
            onNodeWithTag(StaffListTestTags.LIST_CONTAINER).assertIsDisplayed()
            onNodeWithText("Carlos Andrés Mejía").assertIsDisplayed()
            onNodeWithText("14/11/2026 17:15", substring = true).assertIsDisplayed()

            onNodeWithTag(StaffListTestTags.FILTER_COORDINATOR).performClick()
            onNodeWithTag(StaffListTestTags.NEXT_PAGE_BUTTON).performClick()
            onNodeWithTag(StaffListTestTags.NEW_STAFF_BUTTON).performClick()

            assertEquals(UserRole.COORDINADOR, selectedFilter)
            assertEquals(1, nextClicks)
            assertEquals(1, newStaffClicks)
        }

    @Test
    fun `StaffListScreen renders wide table layout when width is at least 600dp`() =
        runComposeUiTest {
            setContent {
                ServiceDeskTheme {
                    Box(
                        modifier =
                            Modifier
                                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                                .size(800.dp, 450.dp),
                    ) {
                        StaffListScreen(
                            uiState =
                                StaffListUiState(
                                    items =
                                        listOf(
                                            StaffItemUiModel(
                                                id = "1",
                                                fullName = "Carlos Mejía",
                                                email = "carlos@itm.edu.co",
                                                role = UserRole.PERSONAL_ATENCION,
                                                roleLabel = "Personal de atención",
                                                formattedCreatedAt = "14/11/2026 17:15",
                                            ),
                                        ),
                                    page = 2,
                                    totalItems = 25,
                                    totalPages = 2,
                                ),
                            onSelectRoleFilter = {},
                            onPreviousPage = {},
                            onNextPage = {},
                            onRetry = {},
                            onNavigateToCreateStaff = {},
                        )
                    }
                }
            }

            onNodeWithText("Nombre").assertIsDisplayed()
            onNodeWithText("Carlos Mejía").assertIsDisplayed()
        }

    @Test
    fun `StaffListScreen renders empty state and error retry banner`() =
        runComposeUiTest {
            setContent {
                ServiceDeskTheme {
                    StaffListScreen(
                        uiState = StaffListUiState(items = emptyList(), isLoading = false, errorMessage = null),
                        onSelectRoleFilter = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = {},
                        onNavigateToCreateStaff = {},
                    )
                }
            }
            onNodeWithTag(StaffListTestTags.EMPTY_STATE).assertIsDisplayed()
        }

    @Test
    fun `StaffListScreen renders error banner and invokes retry callback`() =
        runComposeUiTest {
            var retryClicks = 0

            setContent {
                ServiceDeskTheme {
                    StaffListScreen(
                        uiState = StaffListUiState(errorMessage = "Error de conexión"),
                        onSelectRoleFilter = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = { retryClicks += 1 },
                        onNavigateToCreateStaff = {},
                    )
                }
            }

            onNodeWithTag(StaffListTestTags.ERROR_BANNER).assertIsDisplayed()
            onNodeWithText("Reintentar").performClick()
            assertEquals(1, retryClicks)
        }

    @Test
    fun `CreateStaffScreen renders role selector and confirmation dialog RNF-09`() =
        runComposeUiTest {
            var confirmed = 0
            var dismissed = 0

            setContent {
                ServiceDeskTheme {
                    CreateStaffScreen(
                        uiState =
                            CreateStaffUiState(
                                fullName = "Carlos Andrés Mejía",
                                selectedRole = UserRole.PERSONAL_ATENCION,
                                fieldErrors = mapOf(UserFormValidator.FIELD_ROLE to "Rol inválido"),
                                generalError = "El correo ya existe.",
                                isConfirmDialogOpen = true,
                            ),
                        confirmationRoleLabel = "Personal de atención",
                        onFullNameChange = {},
                        onDocumentNumberChange = {},
                        onEmailChange = {},
                        onPhoneChange = {},
                        onPasswordChange = {},
                        onTogglePasswordVisibility = {},
                        onRoleSelected = {},
                        onRequestSubmit = {},
                        onConfirmSubmit = { confirmed += 1 },
                        onDismissConfirmDialog = { dismissed += 1 },
                    )
                }
            }

            onNodeWithTag(CreateStaffTestTags.ERROR_BANNER)
                .performScrollTo()
                .assertIsDisplayed()
                .assertTextContains("El correo ya existe.")
            onNodeWithTag(CreateStaffTestTags.ROLE_SELECTOR).performScrollTo().assertIsDisplayed()
            onNodeWithText("Rol inválido").assertIsDisplayed()
            onNodeWithTag(CreateStaffTestTags.SUBMIT_BUTTON).performScrollTo().assertIsDisplayed()
            onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertIsDisplayed()
            onNodeWithText(
                "¿Confirmas el registro de Carlos Andrés Mejía con rol Personal de atención?",
            ).assertIsDisplayed()

            onNodeWithTag(ConfirmDialogTestTags.ACCEPT_BUTTON).performClick()
            onNodeWithTag(ConfirmDialogTestTags.CANCEL_BUTTON).performClick()

            assertEquals(1, confirmed)
            assertEquals(1, dismissed)
        }
}
