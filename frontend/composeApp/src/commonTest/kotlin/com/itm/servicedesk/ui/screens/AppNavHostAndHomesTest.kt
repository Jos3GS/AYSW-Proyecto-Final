package com.itm.servicedesk.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.core.storage.SessionStorage
import com.itm.servicedesk.data.mapper.RequestUiMapper
import com.itm.servicedesk.data.mapper.StaffUiMapper
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceHealth
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.AttentionRepository
import com.itm.servicedesk.domain.repository.AuthRepository
import com.itm.servicedesk.domain.repository.ServiceRequestRepository
import com.itm.servicedesk.domain.repository.StaffRepository
import com.itm.servicedesk.domain.repository.SystemHealthRepository
import com.itm.servicedesk.domain.validation.AttentionFormValidator
import com.itm.servicedesk.testing.AttentionTestFixtures
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.FakeAttentionRepository
import com.itm.servicedesk.testing.FakeAuthRepository
import com.itm.servicedesk.testing.FakeServiceRequestRepository
import com.itm.servicedesk.testing.FakeStaffRepository
import com.itm.servicedesk.testing.InMemoryTestSessionStorage
import com.itm.servicedesk.testing.RequestTestFixtures
import com.itm.servicedesk.ui.components.AttentionActionPanelTestTags
import com.itm.servicedesk.ui.components.CancelDialogTestTags
import com.itm.servicedesk.ui.components.ConfirmDialogTestTags
import com.itm.servicedesk.ui.components.RequireRoleTestTags
import com.itm.servicedesk.ui.components.TopBarTestTags
import com.itm.servicedesk.ui.navigation.AppNavHost
import com.itm.servicedesk.ui.navigation.AttentionDetailRoute
import com.itm.servicedesk.ui.navigation.CreateRequestRoute
import com.itm.servicedesk.ui.navigation.RequestDetailRoute
import com.itm.servicedesk.ui.navigation.ServiceDeskAppRoot
import com.itm.servicedesk.ui.screens.attention.AgentInboxTestTags
import com.itm.servicedesk.ui.screens.attention.AttentionDetailTestTags
import com.itm.servicedesk.ui.screens.attention.CoordinationPendingTestTags
import com.itm.servicedesk.ui.screens.auth.LoginTestTags
import com.itm.servicedesk.ui.screens.auth.RegisterTestTags
import com.itm.servicedesk.ui.screens.home.AgentHomeScreen
import com.itm.servicedesk.ui.screens.home.CoordinatorHomeScreen
import com.itm.servicedesk.ui.screens.home.HomeTestTags
import com.itm.servicedesk.ui.screens.requests.CreateRequestTestTags
import com.itm.servicedesk.ui.screens.requests.MyRequestsTestTags
import com.itm.servicedesk.ui.screens.requests.RequestDetailTestTags
import com.itm.servicedesk.ui.screens.staff.CreateStaffTestTags
import com.itm.servicedesk.ui.screens.staff.StaffListTestTags
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.CreateStaffViewModel
import com.itm.servicedesk.viewmodel.LoginViewModel
import com.itm.servicedesk.viewmodel.RegisterViewModel
import com.itm.servicedesk.viewmodel.SessionViewModel
import com.itm.servicedesk.viewmodel.StaffListViewModel
import com.itm.servicedesk.viewmodel.StartupUiMapper
import com.itm.servicedesk.viewmodel.StartupViewModel
import com.itm.servicedesk.viewmodel.attention.AgentInboxUiState
import com.itm.servicedesk.viewmodel.attention.AgentInboxViewModel
import com.itm.servicedesk.viewmodel.attention.AttentionDetailViewModel
import com.itm.servicedesk.viewmodel.attention.CoordinationPendingUiState
import com.itm.servicedesk.viewmodel.attention.CoordinationPendingViewModel
import com.itm.servicedesk.viewmodel.requests.CreateRequestViewModel
import com.itm.servicedesk.viewmodel.requests.MyRequestsViewModel
import com.itm.servicedesk.viewmodel.requests.RequestDetailViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private class NavFakeHealthRepository : SystemHealthRepository {
    override suspend fun checkHealth(): AppResult<ServiceHealth> =
        AppResult.Success(
            ServiceHealth(
                isServiceUp = true,
                isDatabaseUp = true,
                serviceName = "itm-service-desk-backend",
                checkedAt = Instant.parse("2026-11-14T11:24:00-05:00"),
            ),
        )
}

@OptIn(ExperimentalTestApi::class)
class AppNavHostAndHomesTest : ComposeUiTestBase() {
    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

    private fun startTestKoin(
        initialToken: String? = null,
        authRepository: AuthRepository = FakeAuthRepository(),
        staffRepository: StaffRepository = FakeStaffRepository(),
        serviceRequestRepository: ServiceRequestRepository = FakeServiceRequestRepository(),
        attentionRepository: AttentionRepository = FakeAttentionRepository(),
    ) {
        stopKoin()
        startKoin {
            modules(
                module {
                    single { SessionEventBus() }
                    single<SessionStorage> { InMemoryTestSessionStorage(initialToken = initialToken) }
                    single<SystemHealthRepository> { NavFakeHealthRepository() }
                    single { authRepository }
                    single { staffRepository }
                    single { serviceRequestRepository }
                    single { attentionRepository }
                    single { StaffUiMapper() }
                    single { RequestUiMapper() }
                    single { AttentionFormValidator() }
                    factory { StartupUiMapper() }
                    viewModel { StartupViewModel(repository = get(), uiMapper = get()) }
                    viewModel {
                        SessionViewModel(
                            authRepository = get(),
                            sessionStorage = get(),
                            sessionEventBus = get(),
                        )
                    }
                    viewModel { LoginViewModel(authRepository = get()) }
                    viewModel { RegisterViewModel(authRepository = get()) }
                    viewModel { StaffListViewModel(staffRepository = get(), staffUiMapper = get()) }
                    viewModel { CreateStaffViewModel(staffRepository = get(), staffUiMapper = get()) }
                    viewModel { MyRequestsViewModel(repository = get(), uiMapper = get()) }
                    viewModel { CreateRequestViewModel(repository = get()) }
                    viewModel { RequestDetailViewModel(repository = get(), uiMapper = get()) }
                    viewModel { CoordinationPendingViewModel(attentionRepository = get()) }
                    viewModel { AgentInboxViewModel(attentionRepository = get()) }
                    viewModel {
                        AttentionDetailViewModel(
                            attentionRepository = get(),
                            staffRepository = get(),
                            validator = get(),
                        )
                    }
                },
            )
        }
    }

    @Test
    fun `CoordinatorHomeScreen renders pending queue and triggers actions`() =
        runComposeUiTest {
            var manageStaffClicks = 0
            var coordinatorLogoutClicks = 0
            var openedCode: String? = null

            val coordinator =
                AuthTestFixtures.userProfile(
                    fullName = "Ana Sofía Coordinator",
                    email = "ana.coord@itm.edu.co",
                    role = UserRole.COORDINADOR,
                )

            setContent {
                ServiceDeskTheme {
                    CoordinatorHomeScreen(
                        user = coordinator,
                        uiState =
                            CoordinationPendingUiState(
                                items = listOf(AttentionTestFixtures.requestSummary(code = "SOL-2026-00019")),
                                totalItems = 1,
                            ),
                        onSelectStatusFilter = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = {},
                        onOpenDetail = { openedCode = it },
                        onManageStaffClick = { manageStaffClicks += 1 },
                        onLogoutClick = { coordinatorLogoutClicks += 1 },
                    )
                }
            }

            onNodeWithTag(HomeTestTags.COORDINATOR_HOME).assertIsDisplayed()
            onNodeWithText("Pendientes de coordinación").assertIsDisplayed()
            onNodeWithTag(CoordinationPendingTestTags.item("SOL-2026-00019")).performClick()
            onNodeWithTag(HomeTestTags.MANAGE_STAFF_BUTTON).performClick()
            onNodeWithTag(TopBarTestTags.LOGOUT_BUTTON).performClick()

            assertEquals("SOL-2026-00019", openedCode)
            assertEquals(1, manageStaffClicks)
            assertEquals(1, coordinatorLogoutClicks)
        }

    @Test
    fun `AgentHomeScreen renders assigned inbox and triggers logout action`() =
        runComposeUiTest {
            var agentLogoutClicks = 0
            var openedCode: String? = null
            val agent =
                AuthTestFixtures.userProfile(
                    fullName = "Carlos Mejía",
                    email = "carlos.mejia@itm.edu.co",
                    role = UserRole.PERSONAL_ATENCION,
                )

            setContent {
                ServiceDeskTheme {
                    AgentHomeScreen(
                        user = agent,
                        uiState =
                            AgentInboxUiState(
                                items =
                                    listOf(
                                        AttentionTestFixtures.requestSummary(
                                            code = "SOL-2026-00025",
                                            status = RequestStatus.EN_REVISION,
                                            priority = RequestPriority.ALTA,
                                        ),
                                    ),
                                totalItems = 1,
                            ),
                        onSelectStatusFilter = {},
                        onPreviousPage = {},
                        onNextPage = {},
                        onRetry = {},
                        onOpenDetail = { openedCode = it },
                        onLogoutClick = { agentLogoutClicks += 1 },
                    )
                }
            }

            onNodeWithTag(HomeTestTags.AGENT_HOME).assertIsDisplayed()
            onNodeWithText("Bandeja de atención").assertIsDisplayed()
            onNodeWithTag(AgentInboxTestTags.item("SOL-2026-00025")).performClick()
            onNodeWithTag(TopBarTestTags.LOGOUT_BUTTON).performClick()

            assertEquals("SOL-2026-00025", openedCode)
            assertEquals(1, agentLogoutClicks)
        }

    @Test
    fun `AppNavHost navigates Login to Register and auto-logs in to ApplicantHome`() =
        runComposeUiTest {
            val applicantSession =
                AuthTestFixtures.authSession(
                    user = AuthTestFixtures.userProfile(role = UserRole.SOLICITANTE),
                )
            startTestKoin(
                initialToken = null,
                authRepository =
                    FakeAuthRepository(
                        registerHandler = { AppResult.Success(applicantSession) },
                    ),
            )

            setContent {
                ServiceDeskAppRoot()
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(LoginTestTags.EMAIL_INPUT).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(LoginTestTags.GO_REGISTER_BUTTON).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(RegisterTestTags.FULL_NAME_INPUT).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(RegisterTestTags.FULL_NAME_INPUT).performScrollTo().performTextInput("Laura Gómez")
            onNodeWithTag(RegisterTestTags.DOCUMENT_INPUT).performScrollTo().performTextInput("1020304050")
            onNodeWithTag(RegisterTestTags.EMAIL_INPUT).performScrollTo().performTextInput("laura@itm.edu.co")
            onNodeWithTag(RegisterTestTags.PHONE_INPUT).performScrollTo().performTextInput("3001234567")
            onNodeWithTag(RegisterTestTags.PASSWORD_INPUT).performScrollTo().performTextInput("Clave1234")
            onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(HomeTestTags.APPLICANT_HOME).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(HomeTestTags.APPLICANT_HOME).assertIsDisplayed()
        }

    @Test
    fun `AppNavHost confirms Logout from ApplicantHome back to Login with notice`() =
        runComposeUiTest {
            val applicant = AuthTestFixtures.userProfile(role = UserRole.SOLICITANTE)
            startTestKoin(
                initialToken = "valid-applicant-jwt",
                authRepository =
                    FakeAuthRepository(
                        getCurrentUserHandler = { AppResult.Success(applicant) },
                    ),
            )

            setContent {
                ServiceDeskAppRoot()
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(HomeTestTags.APPLICANT_HOME).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(TopBarTestTags.LOGOUT_BUTTON).performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(ConfirmDialogTestTags.DIALOG).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(ConfirmDialogTestTags.ACCEPT_BUTTON).performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(LoginTestTags.NOTICE_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(LoginTestTags.NOTICE_BANNER).assertIsDisplayed()
        }

    @Test
    fun `AppNavHost authenticates Coordinator from Login to CoordinatorHome`() =
        runComposeUiTest {
            val coordinator =
                AuthTestFixtures.userProfile(
                    fullName = "Coordinadora ITM",
                    role = UserRole.COORDINADOR,
                )
            val coordinatorSession = AuthTestFixtures.authSession(user = coordinator)
            startTestKoin(
                initialToken = null,
                authRepository =
                    FakeAuthRepository(
                        loginHandler = { AppResult.Success(coordinatorSession) },
                    ),
            )

            setContent {
                ServiceDeskAppRoot()
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(LoginTestTags.EMAIL_INPUT).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(LoginTestTags.EMAIL_INPUT).performScrollTo().performTextInput("coord@itm.edu.co")
            onNodeWithTag(LoginTestTags.PASSWORD_INPUT).performScrollTo().performTextInput("Clave1234")
            onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(HomeTestTags.COORDINATOR_HOME).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(HomeTestTags.COORDINATOR_HOME).assertIsDisplayed()
        }

    @Test
    fun `AppNavHost navigates CoordinatorHome to StaffList and CreateStaff with confirmation`() =
        runComposeUiTest {
            val coordinator =
                AuthTestFixtures.userProfile(
                    fullName = "Coordinadora ITM",
                    role = UserRole.COORDINADOR,
                )
            val staffRepo = FakeStaffRepository()
            startTestKoin(
                initialToken = "valid-coord-jwt",
                authRepository =
                    FakeAuthRepository(
                        getCurrentUserHandler = { AppResult.Success(coordinator) },
                    ),
                staffRepository = staffRepo,
            )

            setContent {
                ServiceDeskAppRoot()
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(HomeTestTags.COORDINATOR_HOME).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(HomeTestTags.MANAGE_STAFF_BUTTON).performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(StaffListTestTags.LIST_CONTAINER).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(StaffListTestTags.NEW_STAFF_BUTTON).performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(CreateStaffTestTags.FULL_NAME_INPUT).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(CreateStaffTestTags.FULL_NAME_INPUT).performScrollTo().performTextInput("Carlos Mejía")
            onNodeWithTag(CreateStaffTestTags.DOCUMENT_INPUT).performScrollTo().performTextInput("71829304")
            onNodeWithTag(CreateStaffTestTags.EMAIL_INPUT).performScrollTo().performTextInput("carlos@itm.edu.co")
            onNodeWithTag(CreateStaffTestTags.PHONE_INPUT).performScrollTo().performTextInput("3119876543")
            onNodeWithTag(CreateStaffTestTags.PASSWORD_INPUT).performScrollTo().performTextInput("StaffClave2026")
            onNodeWithTag(CreateStaffTestTags.ROLE_COORDINATOR_OPTION).performScrollTo().performClick()
            onNodeWithTag(CreateStaffTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(ConfirmDialogTestTags.DIALOG).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(ConfirmDialogTestTags.ACCEPT_BUTTON).performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(StaffListTestTags.LIST_CONTAINER).fetchSemanticsNodes().isNotEmpty()
            }

            assertEquals(1, staffRepo.createCalls.size)
            assertEquals(UserRole.COORDINADOR, staffRepo.createCalls.first().role)
            onNodeWithTag(StaffListTestTags.LIST_CONTAINER).assertIsDisplayed()
        }

    @Test
    fun `AppNavHost restores Agent session directly to AgentHomeRoute`() =
        runComposeUiTest {
            val agent = AuthTestFixtures.userProfile(role = UserRole.PERSONAL_ATENCION)
            startTestKoin(
                initialToken = "valid-agent-jwt",
                authRepository =
                    FakeAuthRepository(
                        getCurrentUserHandler = { AppResult.Success(agent) },
                    ),
            )

            setContent {
                ServiceDeskAppRoot()
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(HomeTestTags.AGENT_HOME).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(HomeTestTags.AGENT_HOME).assertIsDisplayed()
        }

    @Test
    fun `AppNavHost executes Applicant create, detail, cancel dialog, and 409 conflict reload`() =
        runComposeUiTest {
            val applicant = AuthTestFixtures.userProfile(role = UserRole.SOLICITANTE)
            var currentStatus = RequestStatus.ABIERTA
            var cancelAttempts = 0
            val requestRepo =
                FakeServiceRequestRepository(
                    getMyRequestsHandler = { page, size, _ ->
                        AppResult.Success(
                            RequestTestFixtures.requestsPage(
                                items = listOf(RequestTestFixtures.requestSummary(code = "SOL-2026-00019")),
                                page = page,
                                size = size,
                            ),
                        )
                    },
                    createHandler = { command ->
                        AppResult.Success(
                            RequestTestFixtures.requestDetail(
                                code = "SOL-2026-00020",
                                category = command.category,
                                subject = command.subject,
                                description = command.description,
                                status = RequestStatus.ABIERTA,
                            ),
                        )
                    },
                    getDetailHandler = { code ->
                        AppResult.Success(
                            RequestTestFixtures.requestDetail(
                                code = code,
                                status = currentStatus,
                            ),
                        )
                    },
                    cancelHandler = { command ->
                        cancelAttempts += 1
                        if (cancelAttempts == 1) {
                            currentStatus = RequestStatus.EN_REVISION
                            AppResult.Failure(
                                DomainError.Conflict("La solicitud ya fue tomada para revisión."),
                            )
                        } else {
                            currentStatus = RequestStatus.CANCELADA
                            AppResult.Success(
                                RequestTestFixtures.requestDetail(
                                    code = command.code,
                                    status = RequestStatus.CANCELADA,
                                ),
                            )
                        }
                    },
                )
            startTestKoin(
                initialToken = "valid-applicant-jwt",
                authRepository = FakeAuthRepository(getCurrentUserHandler = { AppResult.Success(applicant) }),
                serviceRequestRepository = requestRepo,
            )

            setContent {
                ServiceDeskAppRoot()
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(MyRequestsTestTags.NEW_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(MyRequestsTestTags.NEW_BUTTON).performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(CreateRequestTestTags.CATEGORY_DROPDOWN).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(CreateRequestTestTags.CATEGORY_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(CreateRequestTestTags.categoryOption(RequestCategory.TRAMITE)).performClick()
            onNodeWithTag(CreateRequestTestTags.SUBJECT_INPUT)
                .performScrollTo()
                .performTextInput("Certificado de estudios")
            onNodeWithTag(CreateRequestTestTags.DESCRIPTION_INPUT)
                .performScrollTo()
                .performTextInput("Necesito el certificado oficial del periodo 2026-1 para beca.")
            onNodeWithTag(CreateRequestTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(RequestDetailTestTags.CODE).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("SOL-2026-00020").assertIsDisplayed()
            onNodeWithTag(RequestDetailTestTags.CANCEL_BUTTON).performScrollTo().performClick()

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(CancelDialogTestTags.DIALOG).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(CancelDialogTestTags.REASON_INPUT).performTextInput("Ya no lo requiero")
            onNodeWithTag(CancelDialogTestTags.CONFIRM_BUTTON).performClick()

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(RequestDetailTestTags.ACTION_ERROR_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("La solicitud ya fue tomada para revisión.").assertIsDisplayed()
            onNodeWithText("En revisión").assertIsDisplayed()
        }

    @Test
    fun `AppNavHost pre-fills previousRequestCode from RESUELTA detail and enforces SOLICITANTE role`() =
        runComposeUiTest {
            val applicant = AuthTestFixtures.userProfile(role = UserRole.SOLICITANTE)
            val resolvedRepo =
                FakeServiceRequestRepository(
                    getMyRequestsHandler = { page, size, _ ->
                        AppResult.Success(
                            RequestTestFixtures.requestsPage(
                                items =
                                    listOf(
                                        RequestTestFixtures.requestSummary(
                                            code = "SOL-2026-00015",
                                            status = RequestStatus.RESUELTA,
                                        ),
                                    ),
                                page = page,
                                size = size,
                            ),
                        )
                    },
                    getDetailHandler = { code ->
                        AppResult.Success(
                            RequestTestFixtures.requestDetail(
                                code = code,
                                status = RequestStatus.RESUELTA,
                                responseText = "Respuesta lista.",
                                respondedAt = RequestTestFixtures.sampleRespondedAt,
                            ),
                        )
                    },
                )
            startTestKoin(
                initialToken = "valid-applicant-jwt",
                authRepository = FakeAuthRepository(getCurrentUserHandler = { AppResult.Success(applicant) }),
                serviceRequestRepository = resolvedRepo,
            )

            setContent {
                ServiceDeskAppRoot()
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(MyRequestsTestTags.item("SOL-2026-00015")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(MyRequestsTestTags.item("SOL-2026-00015")).performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(RequestDetailTestTags.CREATE_RELATED_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(RequestDetailTestTags.CREATE_RELATED_BUTTON).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(CreateRequestTestTags.PREVIOUS_CODE_INPUT).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(CreateRequestTestTags.PREVIOUS_CODE_INPUT)
                .performScrollTo()
                .assertIsDisplayed()
                .assertTextContains("SOL-2026-00015")
        }

    @Test
    fun `AppNavHost enforces RequireRole SOLICITANTE on CreateRequestRoute and RequestDetailRoute`() =
        runComposeUiTest {
            val coordinator = AuthTestFixtures.userProfile(role = UserRole.COORDINADOR)
            startTestKoin(
                initialToken = "valid-coord-jwt",
                authRepository = FakeAuthRepository(getCurrentUserHandler = { AppResult.Success(coordinator) }),
            )
            lateinit var controller: NavHostController
            setContent {
                ServiceDeskTheme {
                    controller = rememberNavController()
                    AppNavHost(navController = controller)
                }
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(HomeTestTags.COORDINATOR_HOME).fetchSemanticsNodes().isNotEmpty()
            }
            runOnIdle {
                controller.navigate(CreateRequestRoute())
            }
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(RequireRoleTestTags.FORBIDDEN_CARD).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(RequireRoleTestTags.FORBIDDEN_CARD).assertIsDisplayed()
            onNodeWithTag(RequireRoleTestTags.GO_HOME_BUTTON).performClick()

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(HomeTestTags.COORDINATOR_HOME).fetchSemanticsNodes().isNotEmpty()
            }
            runOnIdle {
                controller.navigate(RequestDetailRoute(code = "SOL-2026-00015"))
            }
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(RequireRoleTestTags.FORBIDDEN_CARD).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(RequireRoleTestTags.FORBIDDEN_CARD).assertIsDisplayed()
        }

    @Test
    fun `AppNavHost enforces RequireRole on AttentionDetailRoute for SOLICITANTE`() =
        runComposeUiTest {
            val applicant = AuthTestFixtures.userProfile(role = UserRole.SOLICITANTE)
            startTestKoin(
                initialToken = "valid-applicant-jwt",
                authRepository = FakeAuthRepository(getCurrentUserHandler = { AppResult.Success(applicant) }),
            )
            lateinit var controller: NavHostController
            setContent {
                ServiceDeskTheme {
                    controller = rememberNavController()
                    AppNavHost(navController = controller)
                }
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(HomeTestTags.APPLICANT_HOME).fetchSemanticsNodes().isNotEmpty()
            }
            runOnIdle {
                controller.navigate(AttentionDetailRoute(code = "SOL-2026-00019"))
            }
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(RequireRoleTestTags.FORBIDDEN_CARD).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(RequireRoleTestTags.FORBIDDEN_CARD).assertIsDisplayed()
        }

    @Test
    fun `AppNavHost navigates Coordinator from pending queue to AttentionDetailRoute and assigns`() =
        runComposeUiTest {
            val coordinator = AuthTestFixtures.userProfile(role = UserRole.COORDINADOR)
            val agent = AuthTestFixtures.staffMember(id = "agent-1", fullName = "Carlos Andrés Mejía")
            val attentionRepo = FakeAttentionRepository()
            val staffRepo =
                FakeStaffRepository(
                    listStaffHandler = { page, size, _ ->
                        AppResult.Success(AuthTestFixtures.staffPage(items = listOf(agent), page = page, size = size))
                    },
                )
            startTestKoin(
                initialToken = "valid-coord-jwt",
                authRepository = FakeAuthRepository(getCurrentUserHandler = { AppResult.Success(coordinator) }),
                staffRepository = staffRepo,
                attentionRepository = attentionRepo,
            )

            setContent {
                ServiceDeskAppRoot()
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(CoordinationPendingTestTags.item("SOL-2026-00019"))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            onNodeWithTag(CoordinationPendingTestTags.item("SOL-2026-00019")).performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(AttentionDetailTestTags.CODE).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(AttentionActionPanelTestTags.AGENT_DROPDOWN).performScrollTo().performClick()
            onNodeWithTag(AttentionActionPanelTestTags.agentOption("agent-1")).performClick()
            onNodeWithTag(AttentionActionPanelTestTags.priorityChip(RequestPriority.ALTA))
                .performScrollTo()
                .performClick()
            onNodeWithTag(AttentionActionPanelTestTags.ASSIGN_SUBMIT_BUTTON).performScrollTo().performClick()

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(ConfirmDialogTestTags.DIALOG).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(ConfirmDialogTestTags.ACCEPT_BUTTON).performClick()

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(AttentionDetailTestTags.SUCCESS_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(1, attentionRepo.assignCalls.size)
            onNodeWithTag(AttentionDetailTestTags.SUCCESS_BANNER).performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `AppNavHost navigates Agent from inbox to AttentionDetailRoute and adds intervention without staff call`() =
        runComposeUiTest {
            val agentUser = AuthTestFixtures.userProfile(role = UserRole.PERSONAL_ATENCION)
            val attentionRepo =
                FakeAttentionRepository(
                    getInternalDetailHandler = { code ->
                        AppResult.Success(
                            AttentionTestFixtures.requestDetail(
                                code = code,
                                status = RequestStatus.EN_REVISION,
                                priority = RequestPriority.ALTA,
                                assignedAgentId = "agent-1",
                                assignedAgentName = "Carlos Andrés Mejía",
                            ),
                        )
                    },
                )
            val staffRepo = FakeStaffRepository()
            startTestKoin(
                initialToken = "valid-agent-jwt",
                authRepository = FakeAuthRepository(getCurrentUserHandler = { AppResult.Success(agentUser) }),
                staffRepository = staffRepo,
                attentionRepository = attentionRepo,
            )

            setContent {
                ServiceDeskAppRoot()
            }

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(AgentInboxTestTags.item("SOL-2026-00019")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(AgentInboxTestTags.item("SOL-2026-00019")).performClick()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(AttentionActionPanelTestTags.INTERVENTION_INPUT).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(AttentionActionPanelTestTags.INTERVENTION_INPUT)
                .performScrollTo()
                .performTextInput("Revisión técnica realizada en plataforma.")
            onNodeWithTag(AttentionActionPanelTestTags.INTERVENTION_SUBMIT_BUTTON).performScrollTo().performClick()

            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag(AttentionDetailTestTags.SUCCESS_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(0, staffRepo.listCalls.size)
            assertEquals(1, attentionRepo.interventionCalls.size)
        }
}
