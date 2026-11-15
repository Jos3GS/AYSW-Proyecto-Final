package com.itm.servicedesk.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.ui.components.AuthenticatedTopBar

object HomeTestTags {
    const val APPLICANT_HOME = "applicant_home_screen"
    const val AGENT_HOME = "agent_home_screen"
    const val COORDINATOR_HOME = "coordinator_home_screen"
    const val MANAGE_STAFF_BUTTON = "coordinator_manage_staff_button"
}

@Composable
fun ApplicantHomeScreen(
    user: UserProfile,
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
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Card(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Bienvenido, ${user.fullName}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Correo institucional: ${user.email}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Tu sesión está activa. Desde aquí podrás registrar y consultar tus solicitudes.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
