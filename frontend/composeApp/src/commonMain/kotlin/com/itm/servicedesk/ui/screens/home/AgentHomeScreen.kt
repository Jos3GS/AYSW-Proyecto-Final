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
import com.itm.servicedesk.ui.screens.attention.AgentInboxScreen
import com.itm.servicedesk.viewmodel.attention.AgentInboxUiState

@Composable
fun AgentHomeScreen(
    user: UserProfile,
    uiState: AgentInboxUiState,
    onSelectStatusFilter: (RequestStatus?) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(HomeTestTags.AGENT_HOME),
        topBar = {
            AuthenticatedTopBar(
                title = "Bandeja de Atención",
                userFullName = user.fullName,
                roleLabel = "Personal de atención",
                onLogoutClick = onLogoutClick,
            )
        },
    ) { innerPadding ->
        AgentInboxScreen(
            uiState = uiState,
            onSelectStatusFilter = onSelectStatusFilter,
            onPreviousPage = onPreviousPage,
            onNextPage = onNextPage,
            onRetry = onRetry,
            onOpenDetail = onOpenDetail,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
