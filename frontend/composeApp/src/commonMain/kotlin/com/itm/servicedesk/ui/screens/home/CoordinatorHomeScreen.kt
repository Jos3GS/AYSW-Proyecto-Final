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
import com.itm.servicedesk.ui.screens.attention.CoordinationPendingScreen
import com.itm.servicedesk.viewmodel.attention.CoordinationPendingUiState

@Composable
fun CoordinatorHomeScreen(
    user: UserProfile,
    uiState: CoordinationPendingUiState,
    onSelectStatusFilter: (RequestStatus?) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onManageStaffClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOpenReportsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(HomeTestTags.COORDINATOR_HOME),
        topBar = {
            AuthenticatedTopBar(
                title = "Panel de Coordinación",
                userFullName = user.fullName,
                roleLabel = "Coordinador",
                onLogoutClick = onLogoutClick,
            )
        },
    ) { innerPadding ->
        CoordinationPendingScreen(
            uiState = uiState,
            onSelectStatusFilter = onSelectStatusFilter,
            onPreviousPage = onPreviousPage,
            onNextPage = onNextPage,
            onRetry = onRetry,
            onOpenDetail = onOpenDetail,
            onManageStaffClick = onManageStaffClick,
            onOpenReportsClick = onOpenReportsClick,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
