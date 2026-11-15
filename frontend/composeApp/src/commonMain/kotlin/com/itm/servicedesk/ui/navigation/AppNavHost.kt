package com.itm.servicedesk.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itm.servicedesk.core.storage.SessionEvent
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.ui.screens.StartupRoute
import com.itm.servicedesk.ui.theme.ServiceDeskTheme
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data object StartupRouteDestination

@Composable
fun ServiceDeskAppRoot() {
    ServiceDeskTheme {
        AppNavHost()
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    sessionEventBus: SessionEventBus = koinInject(),
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sessionEventBus) {
        sessionEventBus.events.collect { event ->
            val message =
                when (event) {
                    SessionEvent.SessionExpired -> "Tu sesión ha expirado. Inicia sesión nuevamente."
                    SessionEvent.LoggedOut -> "Has cerrado sesión correctamente."
                }
            snackbarHostState.showSnackbar(message)
            navController.navigate(StartupRouteDestination) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = StartupRouteDestination,
    ) {
        composable<StartupRouteDestination> {
            StartupRoute(snackbarHostState = snackbarHostState)
        }
    }
}
