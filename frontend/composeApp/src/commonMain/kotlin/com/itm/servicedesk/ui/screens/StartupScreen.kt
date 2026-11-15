package com.itm.servicedesk.ui.screens

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.viewmodel.StartupEvent
import com.itm.servicedesk.viewmodel.StartupUiState
import com.itm.servicedesk.viewmodel.StartupViewModel
import org.koin.compose.viewmodel.koinViewModel

object StartupTestTags {
    const val LOADING_INDICATOR = "startup_loading_indicator"
    const val LOADING_MESSAGE = "startup_loading_message"
    const val READY_CARD = "startup_ready_card"
    const val LAST_CHECKED_TEXT = "startup_last_checked_text"
    const val ERROR_CARD = "startup_error_card"
    const val ERROR_TITLE = "startup_error_title"
    const val ERROR_MESSAGE = "startup_error_message"
    const val RETRY_BUTTON = "startup_retry_button"
}

@Composable
fun StartupRoute(
    viewModel: StartupViewModel = koinViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is StartupEvent.ShowErrorBanner) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        StartupScreen(
            uiState = uiState,
            onRetry = viewModel::retry,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun StartupScreen(
    uiState: StartupUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "ITM Service Desk",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Mesa de Servicios Institucional",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (uiState) {
                is StartupUiState.Loading -> LoadingContent()
                is StartupUiState.Ready -> ReadyContent(uiState = uiState)
                is StartupUiState.Error -> ErrorContent(uiState = uiState, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(
            modifier =
                Modifier
                    .testTag(StartupTestTags.LOADING_INDICATOR)
                    .semantics {
                        contentDescription = "Verificando disponibilidad del servicio"
                    },
        )
        Text(
            text = "Verificando disponibilidad del servicio...",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(StartupTestTags.LOADING_MESSAGE),
        )
    }
}

@Composable
private fun ReadyContent(uiState: StartupUiState.Ready) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(StartupTestTags.READY_CARD)
                .semantics {
                    contentDescription = "Estado operativo del servicio"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Servicio operativo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Estado de la aplicación: ${uiState.serviceLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Base de datos: ${uiState.databaseLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Última verificación: ${uiState.formattedCheckedAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag(StartupTestTags.LAST_CHECKED_TEXT),
            )
        }
    }
}

@Composable
private fun ErrorContent(
    uiState: StartupUiState.Error,
    onRetry: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(StartupTestTags.ERROR_CARD)
                .semantics {
                    contentDescription = "Error de disponibilidad del servicio"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = uiState.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.testTag(StartupTestTags.ERROR_TITLE),
            )
            Text(
                text = uiState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.testTag(StartupTestTags.ERROR_MESSAGE),
            )
            if (uiState.canRetry) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onRetry,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(StartupTestTags.RETRY_BUTTON)
                            .semantics {
                                contentDescription = "Reintentar conexión con el servidor"
                            },
                ) {
                    Text(text = "Reintentar")
                }
            }
        }
    }
}
