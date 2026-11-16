package com.itm.servicedesk.ui.screens.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.ui.components.AuthenticatedTopBar
import com.itm.servicedesk.ui.screens.requests.MyRequestsScreen
import com.itm.servicedesk.viewmodel.requests.MyRequestsUiState

object HomeTestTags {
    const val APPLICANT_HOME = "applicant_home_screen"
    const val AGENT_HOME = "agent_home_screen"
    const val COORDINATOR_HOME = "coordinator_home_screen"
    const val MANAGE_STAFF_BUTTON = "coordinator_manage_staff_button"
    const val REPORTS_BUTTON = "coordinator_reports_button"
}

@Composable
fun ApplicantHomeScreen(
    user: UserProfile,
    uiState: MyRequestsUiState,
    onSelectStatusFilter: (RequestStatus?) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToCreate: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(HomeTestTags.APPLICANT_HOME),
        topBar = {
            AuthenticatedTopBar(
                title = "Portal del Solicitante",
                userFullName = user.fullName,
                roleLabel = "Solicitante",
                onLogoutClick = onLogoutClick,
            )
        },
    ) { innerPadding ->
        MyRequestsScreen(
            uiState = uiState,
            onSelectStatusFilter = onSelectStatusFilter,
            onPreviousPage = onPreviousPage,
            onNextPage = onNextPage,
            onRetry = onRetry,
            onNavigateToCreate = onNavigateToCreate,
            onOpenDetail = onOpenDetail,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
