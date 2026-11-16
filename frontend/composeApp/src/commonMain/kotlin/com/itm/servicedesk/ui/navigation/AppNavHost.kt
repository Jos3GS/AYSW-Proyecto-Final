package com.itm.servicedesk.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.ui.components.AuthenticatedTopBar
import com.itm.servicedesk.ui.components.ConfirmationDialog
import com.itm.servicedesk.ui.components.RequireRole
import com.itm.servicedesk.ui.screens.auth.LoginScreen
import com.itm.servicedesk.ui.screens.auth.RegisterScreen
import com.itm.servicedesk.ui.screens.home.AgentHomeScreen
import com.itm.servicedesk.ui.screens.home.ApplicantHomeScreen
import com.itm.servicedesk.ui.screens.home.CoordinatorHomeScreen
import com.itm.servicedesk.ui.screens.requests.CreateRequestScreen
import com.itm.servicedesk.ui.screens.requests.RequestDetailScreen
import com.itm.servicedesk.ui.screens.staff.CreateStaffScreen
import com.itm.servicedesk.ui.screens.staff.StaffListScreen
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import com.itm.servicedesk.viewmodel.CreateStaffEvent
import com.itm.servicedesk.viewmodel.CreateStaffViewModel
import com.itm.servicedesk.viewmodel.LoginEvent
import com.itm.servicedesk.viewmodel.LoginViewModel
import com.itm.servicedesk.viewmodel.RegisterEvent
import com.itm.servicedesk.viewmodel.RegisterViewModel
import com.itm.servicedesk.viewmodel.SessionNavigationEvent
import com.itm.servicedesk.viewmodel.SessionUiState
import com.itm.servicedesk.viewmodel.SessionViewModel
import com.itm.servicedesk.viewmodel.StaffListViewModel
import com.itm.servicedesk.viewmodel.requests.CreateRequestEvent
import com.itm.servicedesk.viewmodel.requests.CreateRequestViewModel
import com.itm.servicedesk.viewmodel.requests.MyRequestsViewModel
import com.itm.servicedesk.viewmodel.requests.RequestDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import com.itm.servicedesk.ui.screens.StartupRoute as StartupScreenRoute

@Composable
fun ServiceDeskAppRoot() {
    ServiceDeskTheme {
        AppNavHost()
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel = koinViewModel(),
) {
    val sessionState by sessionViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sessionViewModel, navController) {
        sessionViewModel.navigationEvents.collect { event ->
            when (event) {
                is SessionNavigationEvent.NavigateToHome -> {
                    navController.navigate(homeRouteFor(event.role)) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }

                is SessionNavigationEvent.NavigateToLogin -> {
                    navController.navigate(LoginRoute) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    val authenticatedError = (sessionState as? SessionUiState.Authenticated)?.errorMessage
    LaunchedEffect(authenticatedError) {
        if (!authenticatedError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(authenticatedError)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = StartupRoute,
        ) {
            composable<StartupRoute> {
                StartupScreenRoute(
                    snackbarHostState = snackbarHostState,
                    onHealthCheckReady = sessionViewModel::restoreSession,
                )
            }

            composable<LoginRoute> {
                val authenticated = sessionState as? SessionUiState.Authenticated
                if (authenticated != null) {
                    LaunchedEffect(authenticated.user.role) {
                        navController.navigate(homeRouteFor(authenticated.user.role)) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                } else {
                    val loginViewModel: LoginViewModel = koinViewModel()
                    val loginUiState by loginViewModel.uiState.collectAsState()
                    val noticeMessage = (sessionState as? SessionUiState.Unauthenticated)?.noticeMessage

                    LaunchedEffect(loginViewModel) {
                        loginViewModel.events.collect { event ->
                            when (event) {
                                is LoginEvent.Authenticated -> {
                                    sessionViewModel.clearNotice()
                                    sessionViewModel.onAuthenticated(event.session)
                                }
                            }
                        }
                    }

                    LoginScreen(
                        uiState = loginUiState,
                        noticeMessage = noticeMessage,
                        onEmailChange = loginViewModel::onEmailChange,
                        onPasswordChange = loginViewModel::onPasswordChange,
                        onTogglePasswordVisibility = loginViewModel::togglePasswordVisibility,
                        onSubmit = loginViewModel::submit,
                        onNavigateToRegister = {
                            sessionViewModel.clearNotice()
                            navController.navigate(RegisterRoute) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }

            composable<RegisterRoute> {
                val authenticated = sessionState as? SessionUiState.Authenticated
                if (authenticated != null) {
                    LaunchedEffect(authenticated.user.role) {
                        navController.navigate(homeRouteFor(authenticated.user.role)) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                } else {
                    val registerViewModel: RegisterViewModel = koinViewModel()
                    val registerUiState by registerViewModel.uiState.collectAsState()

                    LaunchedEffect(registerViewModel) {
                        registerViewModel.events.collect { event ->
                            when (event) {
                                is RegisterEvent.RegisteredAndAuthenticated -> {
                                    sessionViewModel.clearNotice()
                                    sessionViewModel.onAuthenticated(event.session)
                                }
                            }
                        }
                    }

                    RegisterScreen(
                        uiState = registerUiState,
                        onFullNameChange = registerViewModel::onFullNameChange,
                        onDocumentNumberChange = registerViewModel::onDocumentNumberChange,
                        onEmailChange = registerViewModel::onEmailChange,
                        onPhoneChange = registerViewModel::onPhoneChange,
                        onPasswordChange = registerViewModel::onPasswordChange,
                        onTogglePasswordVisibility = registerViewModel::togglePasswordVisibility,
                        onSubmit = registerViewModel::submit,
                        onNavigateToLogin = {
                            navController.popBackStack()
                        },
                    )
                }
            }

            composable<ApplicantHomeRoute> {
                RequireRole(
                    sessionState = sessionState,
                    allowedRoles = setOf(UserRole.SOLICITANTE),
                    onGoHome = { role -> navigateToRoleHome(navController, role) },
                    onRequireLogin = { navigateToLoginRoot(navController) },
                ) {
                    val user = (sessionState as SessionUiState.Authenticated).user
                    val myRequestsViewModel: MyRequestsViewModel = koinViewModel()
                    val myRequestsUiState by myRequestsViewModel.uiState.collectAsState()

                    LaunchedEffect(Unit) {
                        myRequestsViewModel.loadRequests()
                    }

                    ApplicantHomeScreen(
                        user = user,
                        uiState = myRequestsUiState,
                        onSelectStatusFilter = myRequestsViewModel::selectStatusFilter,
                        onPreviousPage = myRequestsViewModel::previousPage,
                        onNextPage = myRequestsViewModel::nextPage,
                        onRetry = myRequestsViewModel::refresh,
                        onNavigateToCreate = {
                            navController.navigate(CreateRequestRoute()) {
                                launchSingleTop = true
                            }
                        },
                        onOpenDetail = { code ->
                            navController.navigate(RequestDetailRoute(code = code)) {
                                launchSingleTop = true
                            }
                        },
                        onLogoutClick = sessionViewModel::requestLogout,
                    )
                }
            }

            composable<CreateRequestRoute> { backStackEntry ->
                RequireRole(
                    sessionState = sessionState,
                    allowedRoles = setOf(UserRole.SOLICITANTE),
                    onGoHome = { role -> navigateToRoleHome(navController, role) },
                    onRequireLogin = { navigateToLoginRoot(navController) },
                ) {
                    val user = (sessionState as SessionUiState.Authenticated).user
                    val route = backStackEntry.toRoute<CreateRequestRoute>()
                    val createViewModel: CreateRequestViewModel = koinViewModel()
                    val createUiState by createViewModel.uiState.collectAsState()

                    LaunchedEffect(route.previousRequestCode) {
                        createViewModel.prefillPreviousRequestCode(route.previousRequestCode)
                    }

                    LaunchedEffect(createViewModel) {
                        createViewModel.events.collect { event ->
                            when (event) {
                                is CreateRequestEvent.Created -> {
                                    navController.navigate(RequestDetailRoute(code = event.detail.code)) {
                                        popUpTo<CreateRequestRoute> { inclusive = true }
                                        launchSingleTop = true
                                    }
                                    snackbarHostState.showSnackbar(
                                        "Solicitud ${event.detail.code} radicada correctamente.",
                                    )
                                }
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            AuthenticatedTopBar(
                                title = "Nueva solicitud",
                                userFullName = user.fullName,
                                roleLabel = "Solicitante",
                                onLogoutClick = sessionViewModel::requestLogout,
                                onBackClick = { navController.popBackStack() },
                            )
                        },
                    ) { innerPadding ->
                        CreateRequestScreen(
                            uiState = createUiState,
                            onCategorySelected = createViewModel::onCategorySelected,
                            onSubjectChange = createViewModel::onSubjectChange,
                            onDescriptionChange = createViewModel::onDescriptionChange,
                            onPreviousRequestCodeChange = createViewModel::onPreviousRequestCodeChange,
                            onSubmit = createViewModel::submit,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }

            composable<RequestDetailRoute> { backStackEntry ->
                RequireRole(
                    sessionState = sessionState,
                    allowedRoles = setOf(UserRole.SOLICITANTE),
                    onGoHome = { role -> navigateToRoleHome(navController, role) },
                    onRequireLogin = { navigateToLoginRoot(navController) },
                ) {
                    val user = (sessionState as SessionUiState.Authenticated).user
                    val route = backStackEntry.toRoute<RequestDetailRoute>()
                    val detailViewModel: RequestDetailViewModel = koinViewModel()
                    val detailUiState by detailViewModel.uiState.collectAsState()

                    LaunchedEffect(route.code) {
                        detailViewModel.loadDetail(route.code)
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            AuthenticatedTopBar(
                                title = "Detalle ${route.code}",
                                userFullName = user.fullName,
                                roleLabel = "Solicitante",
                                onLogoutClick = sessionViewModel::requestLogout,
                                onBackClick = { navController.popBackStack() },
                            )
                        },
                    ) { innerPadding ->
                        RequestDetailScreen(
                            uiState = detailUiState,
                            onOpenPreviousRequest = { previousCode ->
                                navController.navigate(RequestDetailRoute(code = previousCode)) {
                                    launchSingleTop = true
                                }
                            },
                            onCreateRelatedRequest = { sourceCode ->
                                navController.navigate(CreateRequestRoute(previousRequestCode = sourceCode)) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenCancelDialog = detailViewModel::openCancelDialog,
                            onCancelReasonChange = detailViewModel::onCancelReasonChange,
                            onConfirmCancel = detailViewModel::confirmCancel,
                            onDismissCancelDialog = detailViewModel::dismissCancelDialog,
                            onRetry = { detailViewModel.loadDetail(route.code) },
                            onBackToList = { navController.popBackStack() },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }

            composable<AgentHomeRoute> {
                RequireRole(
                    sessionState = sessionState,
                    allowedRoles = setOf(UserRole.PERSONAL_ATENCION),
                    onGoHome = { role -> navigateToRoleHome(navController, role) },
                    onRequireLogin = { navigateToLoginRoot(navController) },
                ) {
                    val user = (sessionState as SessionUiState.Authenticated).user
                    AgentHomeScreen(
                        user = user,
                        onLogoutClick = sessionViewModel::requestLogout,
                    )
                }
            }

            composable<CoordinatorHomeRoute> {
                RequireRole(
                    sessionState = sessionState,
                    allowedRoles = setOf(UserRole.COORDINADOR),
                    onGoHome = { role -> navigateToRoleHome(navController, role) },
                    onRequireLogin = { navigateToLoginRoot(navController) },
                ) {
                    val user = (sessionState as SessionUiState.Authenticated).user
                    CoordinatorHomeScreen(
                        user = user,
                        onManageStaffClick = {
                            navController.navigate(StaffListRoute) {
                                launchSingleTop = true
                            }
                        },
                        onLogoutClick = sessionViewModel::requestLogout,
                    )
                }
            }

            composable<StaffListRoute> {
                RequireRole(
                    sessionState = sessionState,
                    allowedRoles = setOf(UserRole.COORDINADOR),
                    onGoHome = { role -> navigateToRoleHome(navController, role) },
                    onRequireLogin = { navigateToLoginRoot(navController) },
                ) {
                    val user = (sessionState as SessionUiState.Authenticated).user
                    val staffListViewModel: StaffListViewModel = koinViewModel()
                    val staffListUiState by staffListViewModel.uiState.collectAsState()

                    LaunchedEffect(Unit) {
                        staffListViewModel.loadStaff()
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            AuthenticatedTopBar(
                                title = "Personal interno",
                                userFullName = user.fullName,
                                roleLabel = "Coordinador",
                                onLogoutClick = sessionViewModel::requestLogout,
                                onBackClick = { navController.popBackStack() },
                            )
                        },
                    ) { innerPadding ->
                        StaffListScreen(
                            uiState = staffListUiState,
                            onSelectRoleFilter = staffListViewModel::selectRoleFilter,
                            onPreviousPage = staffListViewModel::previousPage,
                            onNextPage = staffListViewModel::nextPage,
                            onRetry = staffListViewModel::refresh,
                            onNavigateToCreateStaff = {
                                navController.navigate(CreateStaffRoute) {
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }

            composable<CreateStaffRoute> {
                RequireRole(
                    sessionState = sessionState,
                    allowedRoles = setOf(UserRole.COORDINADOR),
                    onGoHome = { role -> navigateToRoleHome(navController, role) },
                    onRequireLogin = { navigateToLoginRoot(navController) },
                ) {
                    val user = (sessionState as SessionUiState.Authenticated).user
                    val createStaffViewModel: CreateStaffViewModel = koinViewModel()
                    val createStaffUiState by createStaffViewModel.uiState.collectAsState()

                    LaunchedEffect(createStaffViewModel) {
                        createStaffViewModel.events.collect { event ->
                            when (event) {
                                is CreateStaffEvent.StaffCreated -> {
                                    navController.navigate(StaffListRoute) {
                                        popUpTo<StaffListRoute> { inclusive = true }
                                        launchSingleTop = true
                                    }
                                    snackbarHostState.showSnackbar("Personal registrado correctamente.")
                                }
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            AuthenticatedTopBar(
                                title = "Registrar personal",
                                userFullName = user.fullName,
                                roleLabel = "Coordinador",
                                onLogoutClick = sessionViewModel::requestLogout,
                                onBackClick = { navController.popBackStack() },
                            )
                        },
                    ) { innerPadding ->
                        CreateStaffScreen(
                            uiState = createStaffUiState,
                            confirmationRoleLabel = createStaffViewModel.confirmationRoleLabel(),
                            onFullNameChange = createStaffViewModel::onFullNameChange,
                            onDocumentNumberChange = createStaffViewModel::onDocumentNumberChange,
                            onEmailChange = createStaffViewModel::onEmailChange,
                            onPhoneChange = createStaffViewModel::onPhoneChange,
                            onPasswordChange = createStaffViewModel::onPasswordChange,
                            onTogglePasswordVisibility = createStaffViewModel::togglePasswordVisibility,
                            onRoleSelected = createStaffViewModel::onRoleSelected,
                            onRequestSubmit = createStaffViewModel::requestSubmit,
                            onConfirmSubmit = createStaffViewModel::confirmSubmit,
                            onDismissConfirmDialog = createStaffViewModel::dismissConfirmDialog,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }

        val authenticatedState = sessionState as? SessionUiState.Authenticated
        if (authenticatedState?.isConfirmingLogout == true) {
            ConfirmationDialog(
                title = "Cerrar sesión",
                message = "¿Deseas cerrar tu sesión actual?",
                confirmText = "Cerrar sesión",
                isLoading = authenticatedState.isLoggingOut,
                onConfirm = sessionViewModel::confirmLogout,
                onDismiss = sessionViewModel::dismissLogoutDialog,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun homeRouteFor(role: UserRole): Any =
    when (role) {
        UserRole.SOLICITANTE -> ApplicantHomeRoute
        UserRole.PERSONAL_ATENCION -> AgentHomeRoute
        UserRole.COORDINADOR -> CoordinatorHomeRoute
    }

private fun navigateToRoleHome(
    navController: NavHostController,
    role: UserRole,
) {
    navController.navigate(homeRouteFor(role)) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

private fun navigateToLoginRoot(navController: NavHostController) {
    navController.navigate(LoginRoute) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
