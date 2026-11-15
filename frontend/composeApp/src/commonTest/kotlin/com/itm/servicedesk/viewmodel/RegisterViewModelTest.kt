package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.validation.UserFormValidator
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.FakeAuthRepository
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
class RegisterViewModelTest {
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
    fun `validates Tabla 1 fields before calling repository`() =
        runTest(testDispatcher) {
            val repo = FakeAuthRepository()
            val viewModel = RegisterViewModel(authRepository = repo, dispatcher = testDispatcher)

            viewModel.onFullNameChange("A")
            viewModel.onDocumentNumberChange("12")
            viewModel.onEmailChange("bad")
            viewModel.onPhoneChange("123")
            viewModel.onPasswordChange("1" + "ñ".repeat(36))
            viewModel.submit()

            assertEquals(0, repo.registerCalls)
            val errors = viewModel.uiState.value.fieldErrors
            assertTrue(errors.containsKey(UserFormValidator.FIELD_FULL_NAME))
            assertTrue(errors.containsKey(UserFormValidator.FIELD_DOCUMENT_NUMBER))
            assertTrue(errors.containsKey(UserFormValidator.FIELD_EMAIL))
            assertTrue(errors.containsKey(UserFormValidator.FIELD_PHONE))
            assertTrue(errors.containsKey(UserFormValidator.FIELD_PASSWORD))
        }

    @Test
    fun `registers applicant, clears password, and emits auto-login event on 201`() =
        runTest(testDispatcher) {
            val expectedSession = AuthTestFixtures.authSession()
            val repo = FakeAuthRepository(registerHandler = { AppResult.Success(expectedSession) })
            val viewModel = RegisterViewModel(authRepository = repo, dispatcher = testDispatcher)

            viewModel.onFullNameChange("Laura Gómez Pérez")
            viewModel.onDocumentNumberChange("1020304050")
            viewModel.onEmailChange("laura.gomez@correo.itm.edu.co")
            viewModel.onPhoneChange("3001234567")
            viewModel.onPasswordChange("ClaveSegura2026")
            assertFalse(
                viewModel.uiState.value
                    .toString()
                    .contains("ClaveSegura2026"),
            )

            viewModel.submit()
            advanceUntilIdle()

            assertEquals(1, repo.registerCalls)
            assertEquals("", viewModel.uiState.value.password)
            val event = assertIs<RegisterEvent.RegisteredAndAuthenticated>(viewModel.events.first())
            assertEquals(expectedSession, event.session)
        }

    @Test
    fun `maps 400 fieldErrors and 409 Conflict preserving form inputs`() =
        runTest(testDispatcher) {
            val repo =
                FakeAuthRepository(
                    registerHandler = {
                        AppResult.Failure(
                            DomainError.Conflict("El correo institucional ya está registrado."),
                        )
                    },
                )
            val viewModel = RegisterViewModel(authRepository = repo, dispatcher = testDispatcher)

            viewModel.onFullNameChange("Laura Gómez Pérez")
            viewModel.onDocumentNumberChange("1020304050")
            viewModel.onEmailChange("laura.gomez@correo.itm.edu.co")
            viewModel.onPhoneChange("3001234567")
            viewModel.onPasswordChange("ClaveSegura2026")
            viewModel.submit()
            advanceUntilIdle()

            assertEquals("El correo institucional ya está registrado.", viewModel.uiState.value.generalError)
            assertEquals("Laura Gómez Pérez", viewModel.uiState.value.fullName)

            repo.registerHandler = {
                AppResult.Failure(
                    DomainError.BadRequest(
                        userMessage = "Error de validación.",
                        fieldErrors = mapOf("email" to "Correo no permitido."),
                    ),
                )
            }
            viewModel.submit()
            advanceUntilIdle()
            assertEquals("Correo no permitido.", viewModel.uiState.value.fieldErrors["email"])
        }
}
