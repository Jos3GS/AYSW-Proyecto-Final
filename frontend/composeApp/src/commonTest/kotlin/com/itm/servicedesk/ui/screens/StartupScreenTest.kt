package com.itm.servicedesk.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.itm.servicedesk.core.storage.SessionEvent
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.ServiceHealth
import com.itm.servicedesk.domain.repository.SystemHealthRepository
import com.itm.servicedesk.ui.navigation.ServiceDeskAppRoot
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.StartupUiMapper
import com.itm.servicedesk.viewmodel.StartupUiState
import com.itm.servicedesk.viewmodel.StartupViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private class UiFakeHealthRepository(
    private val result: AppResult<ServiceHealth>,
) : SystemHealthRepository {
    override suspend fun checkHealth(): AppResult<ServiceHealth> = result
}

@OptIn(ExperimentalTestApi::class)
class StartupScreenTest : ComposeUiTestBase() {
    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

    @Test
    fun `renders loading indicator and Spanish status text when state is Loading`() =
        runComposeUiTest {
            setContent {
                ServiceDeskTheme {
                    StartupScreen(
                        uiState = StartupUiState.Loading,
                        onRetry = {},
                    )
                }
            }

            onNodeWithTag(StartupTestTags.LOADING_INDICATOR).assertIsDisplayed()
            onNodeWithTag(StartupTestTags.LOADING_MESSAGE).assertIsDisplayed()
        }

    @Test
    fun `renders service ready card with formatted timestamp when state is Ready`() =
        runComposeUiTest {
            setContent {
                ServiceDeskTheme {
                    StartupScreen(
                        uiState =
                            StartupUiState.Ready(
                                serviceLabel = "Activo",
                                databaseLabel = "Conectada",
                                formattedCheckedAt = "14/11/2026 11:24",
                            ),
                        onRetry = {},
                    )
                }
            }

            onNodeWithTag(StartupTestTags.READY_CARD).assertIsDisplayed()
            onNodeWithTag(StartupTestTags.LAST_CHECKED_TEXT)
                .assertIsDisplayed()
                .assertTextContains("14/11/2026 11:24", substring = true)
        }

    @Test
    fun `renders error card and triggers onRetry when clicking Reintentar`() =
        runComposeUiTest {
            var retryClicks = 0

            setContent {
                ServiceDeskTheme {
                    StartupScreen(
                        uiState =
                            StartupUiState.Error(
                                title = "Servicio no disponible",
                                message = "No pudimos conectarnos con el servidor.",
                                canRetry = true,
                            ),
                        onRetry = { retryClicks += 1 },
                    )
                }
            }

            onNodeWithTag(StartupTestTags.ERROR_CARD).assertIsDisplayed()
            onNodeWithTag(StartupTestTags.ERROR_MESSAGE)
                .assertIsDisplayed()
                .assertTextContains("No pudimos conectarnos con el servidor.")
            onNodeWithText("Reintentar").assertIsDisplayed()
            onNodeWithTag(StartupTestTags.RETRY_BUTTON).performClick()

            assertEquals(1, retryClicks)
        }

    @Test
    fun `StartupRoute shows error snackbar when repository fails`() =
        runComposeUiTest {
            val viewModel =
                StartupViewModel(
                    repository =
                        UiFakeHealthRepository(
                            AppResult.Failure(
                                DomainError.ServiceUnavailable("Servicio temporalmente no disponible."),
                            ),
                        ),
                )

            setContent {
                ServiceDeskTheme {
                    StartupRoute(viewModel = viewModel)
                }
            }

            waitForIdle()
            onNodeWithTag(StartupTestTags.ERROR_CARD).assertIsDisplayed()
        }

    @Test
    fun `ServiceDeskAppRoot renders StartupRoute and handles SessionEventBus events`() =
        runComposeUiTest {
            val eventBus = SessionEventBus()
            val health =
                ServiceHealth(
                    isServiceUp = true,
                    isDatabaseUp = true,
                    serviceName = "itm-service-desk-backend",
                    checkedAt = Instant.parse("2026-11-14T11:24:00-05:00"),
                )
            stopKoin()
            startKoin {
                modules(
                    module {
                        single { eventBus }
                        single<SystemHealthRepository> {
                            UiFakeHealthRepository(AppResult.Success(health))
                        }
                        factory { StartupUiMapper() }
                        viewModel { StartupViewModel(repository = get(), uiMapper = get()) }
                    },
                )
            }

            setContent {
                ServiceDeskAppRoot()
            }

            waitForIdle()
            onNodeWithTag(StartupTestTags.READY_CARD).assertIsDisplayed()

            eventBus.tryEmit(SessionEvent.SessionExpired)
            waitForIdle()
            eventBus.tryEmit(SessionEvent.LoggedOut)
            waitForIdle()
        }
}
