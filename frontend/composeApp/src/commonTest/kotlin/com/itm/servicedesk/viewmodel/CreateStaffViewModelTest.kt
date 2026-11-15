package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.FakeStaffRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateStaffViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opens confirmation dialog only when valid and ignores SOLICITANTE role`() =
        runTest(testDispatcher) {
            val repo = FakeStaffRepository()
            val viewModel = CreateStaffViewModel(staffRepository = repo, dispatcher = testDispatcher)

            viewModel.onRoleSelected(UserRole.SOLICITANTE)
            assertEquals(UserRole.PERSONAL_ATENCION, viewModel.uiState.value.selectedRole)

            viewModel.requestSubmit()
            assertFalse(viewModel.uiState.value.isConfirmDialogOpen)
            assertTrue(
                viewModel.uiState.value.fieldErrors
                    .isNotEmpty(),
            )

            viewModel.onFullNameChange("Carlos Andrés Mejía")
            viewModel.onDocumentNumberChange("71829304")
            viewModel.onEmailChange("carlos.mejia@itm.edu.co")
            viewModel.onPhoneChange("3119876543")
            viewModel.onPasswordChange("StaffPassword2026")
            viewModel.onRoleSelected(UserRole.COORDINADOR)

            viewModel.requestSubmit()
            assertTrue(viewModel.uiState.value.isConfirmDialogOpen)
            assertEquals("Coordinador", viewModel.confirmationRoleLabel())

            viewModel.dismissConfirmDialog()
            assertFalse(viewModel.uiState.value.isConfirmDialogOpen)
        }

    @Test
    fun `confirmSubmit creates staff, clears password, and emits StaffCreated`() =
        runTest(testDispatcher) {
            val expectedProfile =
                AuthTestFixtures.userProfile(
                    id = "staff-99",
                    fullName = "Carlos Andrés Mejía",
                    role = UserRole.PERSONAL_ATENCION,
                )
            val repo = FakeStaffRepository(createStaffHandler = { AppResult.Success(expectedProfile) })
            val viewModel = CreateStaffViewModel(staffRepository = repo, dispatcher = testDispatcher)

            viewModel.onFullNameChange("Carlos Andrés Mejía")
            viewModel.onDocumentNumberChange("71829304")
            viewModel.onEmailChange("carlos.mejia@itm.edu.co")
            viewModel.onPhoneChange("3119876543")
            viewModel.onPasswordChange("StaffPassword2026")
            assertFalse(
                viewModel.uiState.value
                    .toString()
                    .contains("StaffPassword2026"),
            )

            viewModel.requestSubmit()
            viewModel.confirmSubmit()
            advanceUntilIdle()

            assertEquals(1, repo.createCalls.size)
            assertEquals("", viewModel.uiState.value.password)
            assertFalse(viewModel.uiState.value.isConfirmDialogOpen)
            val event = assertIs<CreateStaffEvent.StaffCreated>(viewModel.events.first())
            assertEquals("staff-99", event.profile.id)
        }

    @Test
    fun `handles 409 Conflict without losing form values`() =
        runTest(testDispatcher) {
            val repo =
                FakeStaffRepository(
                    createStaffHandler = {
                        AppResult.Failure(DomainError.Conflict("El correo ya existe en el sistema."))
                    },
                )
            val viewModel = CreateStaffViewModel(staffRepository = repo, dispatcher = testDispatcher)

            viewModel.onFullNameChange("Carlos Andrés Mejía")
            viewModel.onDocumentNumberChange("71829304")
            viewModel.onEmailChange("carlos.mejia@itm.edu.co")
            viewModel.onPhoneChange("3119876543")
            viewModel.onPasswordChange("StaffPassword2026")
            viewModel.requestSubmit()
            viewModel.confirmSubmit()
            advanceUntilIdle()

            assertEquals("El correo ya existe en el sistema.", viewModel.uiState.value.generalError)
            assertEquals("Carlos Andrés Mejía", viewModel.uiState.value.fullName)
        }
}
