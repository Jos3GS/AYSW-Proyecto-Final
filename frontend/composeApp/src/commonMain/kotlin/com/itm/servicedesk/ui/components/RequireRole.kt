package com.itm.servicedesk.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.viewmodel.SessionUiState

object RequireRoleTestTags {
    const val FORBIDDEN_CARD = "rbac_forbidden_card"
    const val GO_HOME_BUTTON = "rbac_go_home_button"
}

@Composable
fun RequireRole(
    sessionState: SessionUiState,
    allowedRoles: Set<UserRole>,
    onGoHome: (UserRole) -> Unit,
    onRequireLogin: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when (sessionState) {
        is SessionUiState.Checking -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is SessionUiState.Unauthenticated -> {
            LaunchedEffect(Unit) {
                onRequireLogin()
            }
            Box(modifier = modifier.fillMaxSize())
        }

        is SessionUiState.Authenticated -> {
            if (sessionState.user.role in allowedRoles) {
                content()
            } else {
                Box(
                    modifier =
                        modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        modifier =
                            Modifier
                                .widthIn(max = 480.dp)
                                .fillMaxWidth()
                                .testTag(RequireRoleTestTags.FORBIDDEN_CARD),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Acceso denegado",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = "No tienes permisos para acceder a esta sección.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Button(
                                onClick = { onGoHome(sessionState.user.role) },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag(RequireRoleTestTags.GO_HOME_BUTTON),
                            ) {
                                Text(text = "Volver a mi inicio")
                            }
                        }
                    }
                }
            }
        }
    }
}
