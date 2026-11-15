package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.core.storage.SessionEvent
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.FakeAuthRepository
import com.itm.servicedesk.testing.InMemoryTestSessionStorage
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
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
    fun `restoreSession moves to Unauthenticated when no stored token exists`() =
        runTest(testDispatcher) {
            val storage = InMemoryTestSessionStorage(initialToken = null)
            val viewModel =
                SessionViewModel(
                    authRepository = FakeAuthRepository(),
                    sessionStorage = storage,
                    sessionEventBus = SessionEventBus(),
                    dispatcher = testDispatcher,
                )

            viewModel.restoreSession()
            advanceUntilIdle()

            val state = assertIs<SessionUiState.Unauthenticated>(viewModel.uiState.value)
            assertNull(state.noticeMessage)
            val navEvent = assertIs<SessionNavigationEvent.NavigateToLogin>(viewModel.navigationEvents.first())
            assertNull(navEvent.noticeMessage)
        }

    @Test
    fun `restoreSession authenticates user when token is valid on auth me`() =
        runTest(testDispatcher) {
            val storage = InMemoryTestSessionStorage(initialToken = "valid-jwt")
            val coordinator = AuthTestFixtures.userProfile(role = UserRole.COORDINADOR)
            val authRepo = FakeAuthRepository(getCurrentUserHandler = { AppResult.Success(coordinator) })
            val viewModel =
                SessionViewModel(
                    authRepository = authRepo,
                    sessionStorage = storage,
                    sessionEventBus = SessionEventBus(),
                    dispatcher = testDispatcher,
                )

            viewModel.restoreSession()
            advanceUntilIdle()

            val state = assertIs<SessionUiState.Authenticated>(viewModel.uiState.value)
            assertEquals(UserRole.COORDINADOR, state.user.role)
            assertTrue(viewModel.hasRole(UserRole.COORDINADOR))
            assertFalse(viewModel.hasRole(UserRole.SOLICITANTE))
            val navEvent = assertIs<SessionNavigationEvent.NavigateToHome>(viewModel.navigationEvents.first())
            assertEquals(UserRole.COORDINADOR, navEvent.role)
        }

    @Test
    fun `restoreSession redirects to login with notice when token fails with 401`() =
        runTest(testDispatcher) {
            val storage = InMemoryTestSessionStorage(initialToken = "expired-jwt")
            val authRepo =
                FakeAuthRepository(
                    getCurrentUserHandler = { AppResult.Failure(DomainError.Unauthorized("Sesión expirada")) },
                )
            val viewModel =
                SessionViewModel(
                    authRepository = authRepo,
                    sessionStorage = storage,
                    sessionEventBus = SessionEventBus(),
                    dispatcher = testDispatcher,
                )

            viewModel.restoreSession()
            advanceUntilIdle()

            val state = assertIs<SessionUiState.Unauthenticated>(viewModel.uiState.value)
            assertNotNull(state.noticeMessage)
            assertIs<SessionNavigationEvent.NavigateToLogin>(viewModel.navigationEvents.first())
        }

    @Test
    fun `logout confirmation flow calls repository and emits NavigateToLogin`() =
        runTest(testDispatcher) {
            val storage = InMemoryTestSessionStorage(initialToken = "active-jwt")
            val eventBus = SessionEventBus()
            val authRepo = FakeAuthRepository()
            val viewModel =
                SessionViewModel(
                    authRepository = authRepo,
                    sessionStorage = storage,
                    sessionEventBus = eventBus,
                    dispatcher = testDispatcher,
                )

            viewModel.onAuthenticated(AuthTestFixtures.authSession())
            advanceUntilIdle()

            viewModel.requestLogout()
            assertTrue(assertIs<SessionUiState.Authenticated>(viewModel.uiState.value).isConfirmingLogout)

            viewModel.dismissLogoutDialog()
            assertFalse(assertIs<SessionUiState.Authenticated>(viewModel.uiState.value).isConfirmingLogout)

            viewModel.requestLogout()
            viewModel.confirmLogout()
            advanceUntilIdle()

            assertEquals(1, authRepo.logoutCalls)
            assertIs<SessionUiState.Unauthenticated>(viewModel.uiState.value)
        }

    @Test
    fun `SessionEvent SessionExpired resets Authenticated state to Unauthenticated`() =
        runTest(testDispatcher) {
            val eventBus = SessionEventBus()
            val viewModel =
                SessionViewModel(
                    authRepository = FakeAuthRepository(),
                    sessionStorage = InMemoryTestSessionStorage(),
                    sessionEventBus = eventBus,
                    dispatcher = testDispatcher,
                )

            viewModel.onAuthenticated(AuthTestFixtures.authSession())
            advanceUntilIdle()
            assertIs<SessionUiState.Authenticated>(viewModel.uiState.value)
            assertIs<SessionNavigationEvent.NavigateToHome>(viewModel.navigationEvents.first())

            eventBus.emit(SessionEvent.SessionExpired)
            advanceUntilIdle()

            val state = assertIs<SessionUiState.Unauthenticated>(viewModel.uiState.value)
            assertNotNull(state.noticeMessage)
            assertIs<SessionNavigationEvent.NavigateToLogin>(viewModel.navigationEvents.first())
        }

    @Test
    fun `restoreSession handles non-401 error, clearNotice, and confirmLogout failure`() =
        runTest(testDispatcher) {
            val storage = InMemoryTestSessionStorage(initialToken = "valid-jwt")
            val authRepo =
                FakeAuthRepository(
                    getCurrentUserHandler = {
                        AppResult.Failure(DomainError.ServiceUnavailable("Servidor caído"))
                    },
                    logoutHandler = {
                        AppResult.Failure(DomainError.NetworkUnavailable("Sin conexión"))
                    },
                )
            val viewModel =
                SessionViewModel(
                    authRepository = authRepo,
                    sessionStorage = storage,
                    sessionEventBus = SessionEventBus(),
                    dispatcher = testDispatcher,
                )

            viewModel.restoreSession()
            advanceUntilIdle()
            assertEquals(
                "Servidor caído",
                assertIs<SessionUiState.Unauthenticated>(viewModel.uiState.value).noticeMessage,
            )
            viewModel.clearNotice()
            assertNull(assertIs<SessionUiState.Unauthenticated>(viewModel.uiState.value).noticeMessage)

            viewModel.onAuthenticated(AuthTestFixtures.authSession())
            advanceUntilIdle()
            viewModel.requestLogout()
            viewModel.confirmLogout()
            advanceUntilIdle()

            val authState = assertIs<SessionUiState.Authenticated>(viewModel.uiState.value)
            assertEquals("Sin conexión", authState.errorMessage)
            assertFalse(authState.isConfirmingLogout)
        }
}
