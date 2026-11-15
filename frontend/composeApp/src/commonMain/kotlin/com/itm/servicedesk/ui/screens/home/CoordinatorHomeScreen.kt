package com.itm.servicedesk.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
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

@Composable
fun CoordinatorHomeScreen(
    user: UserProfile,
    onManageStaffClick: () -> Unit,
    onLogoutClick: () -> Unit,
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Bienvenido, ${user.fullName}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Correo: ${user.email}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Administra el personal interno de atención y coordinación del servicio.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onManageStaffClick,
                        modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.MANAGE_STAFF_BUTTON),
                    ) {
                        Text(text = "Gestionar personal interno")
                    }
                }
            }
        }
    }
}
