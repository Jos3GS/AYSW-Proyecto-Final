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
class LoginViewModelTest {
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
    fun `blocks submit and populates fieldErrors when email or password are invalid`() =
        runTest(testDispatcher) {
            val repo = FakeAuthRepository()
            val viewModel = LoginViewModel(authRepository = repo, dispatcher = testDispatcher)

            viewModel.onEmailChange("correo-invalido")
            viewModel.onPasswordChange("")
            viewModel.submit()

            assertEquals(0, repo.loginCalls)
            val errors = viewModel.uiState.value.fieldErrors
            assertTrue(errors.containsKey(UserFormValidator.FIELD_EMAIL))
            assertTrue(errors.containsKey(UserFormValidator.FIELD_PASSWORD))
        }

    @Test
    fun `clears password from uiState and emits Authenticated on 200 OK`() =
        runTest(testDispatcher) {
            val expectedSession = AuthTestFixtures.authSession()
            val repo = FakeAuthRepository(loginHandler = { AppResult.Success(expectedSession) })
            val viewModel = LoginViewModel(authRepository = repo, dispatcher = testDispatcher)

            viewModel.onEmailChange("laura.gomez@correo.itm.edu.co")
            viewModel.onPasswordChange("ClaveSegura2026")
            assertFalse(
                viewModel.uiState.value
                    .toString()
                    .contains("ClaveSegura2026"),
            )

            viewModel.submit()
            advanceUntilIdle()

            assertEquals(1, repo.loginCalls)
            assertEquals("", viewModel.uiState.value.password)
            val event = assertIs<LoginEvent.Authenticated>(viewModel.events.first())
            assertEquals(expectedSession, event.session)
        }

    @Test
    fun `shows Credenciales invalidas on 401 Unauthorized without clearing email`() =
        runTest(testDispatcher) {
            val repo =
                FakeAuthRepository(
                    loginHandler = {
                        AppResult.Failure(DomainError.Unauthorized("Credenciales inválidas."))
                    },
                )
            val viewModel = LoginViewModel(authRepository = repo, dispatcher = testDispatcher)

            viewModel.onEmailChange("laura.gomez@correo.itm.edu.co")
            viewModel.onPasswordChange("ClaveErronea1")
            viewModel.submit()
            advanceUntilIdle()

            assertEquals("laura.gomez@correo.itm.edu.co", viewModel.uiState.value.email)
            assertEquals("Credenciales inválidas.", viewModel.uiState.value.generalError)
            assertFalse(viewModel.uiState.value.isSubmitting)
        }

    @Test
    fun `togglePasswordVisibility and BadRequest errors update LoginUiState`() =
        runTest(testDispatcher) {
            val repo =
                FakeAuthRepository(
                    loginHandler = {
                        AppResult.Failure(
                            DomainError.BadRequest(
                                userMessage = "Datos inválidos.",
                                fieldErrors = mapOf(UserFormValidator.FIELD_EMAIL to "Correo inválido"),
                            ),
                        )
                    },
                )
            val viewModel = LoginViewModel(authRepository = repo, dispatcher = testDispatcher)

            viewModel.togglePasswordVisibility()
            assertTrue(viewModel.uiState.value.isPasswordVisible)

            viewModel.onEmailChange("laura.gomez@correo.itm.edu.co")
            viewModel.onPasswordChange("ClaveSegura2026")
            viewModel.submit()
            advanceUntilIdle()

            assertEquals("Correo inválido", viewModel.uiState.value.fieldErrors[UserFormValidator.FIELD_EMAIL])
        }
}
