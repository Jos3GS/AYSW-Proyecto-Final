package com.itm.servicedesk.ui.screens.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.ui.components.CancelRequestDialog
import com.itm.servicedesk.ui.components.RequestStatusChip
import com.itm.servicedesk.viewmodel.requests.RequestDetailUiModel
import com.itm.servicedesk.viewmodel.requests.RequestDetailUiState
import com.itm.servicedesk.viewmodel.requests.RequestHistoryItemUiModel

object RequestDetailTestTags {
    const val CODE = "request_detail_code"
    const val STATUS_BADGE = "request_detail_status_badge"
    const val PREVIOUS_CODE_LINK = "request_detail_previous_code_link"
    const val RESPONSE_CARD = "request_detail_response_card"
    const val HISTORY_LIST = "request_detail_history_list"
    const val CREATE_RELATED_BUTTON = "request_detail_create_related_button"
    const val CANCEL_BUTTON = "request_detail_cancel_button"
    const val ACTION_ERROR_BANNER = "request_detail_action_error_banner"
    const val SUCCESS_BANNER = "request_detail_success_banner"
    const val ERROR_STATE = "request_detail_error_state"
    const val BACK_TO_LIST_BUTTON = "request_detail_back_to_list_button"
}

@Composable
fun RequestDetailScreen(
    uiState: RequestDetailUiState,
    onOpenPreviousRequest: (String) -> Unit,
    onCreateRelatedRequest: (String) -> Unit,
    onOpenCancelDialog: () -> Unit,
    onCancelReasonChange: (String) -> Unit,
    onConfirmCancel: () -> Unit,
    onDismissCancelDialog: () -> Unit,
    onRetry: () -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is RequestDetailUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is RequestDetailUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier =
                        Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                            .testTag(RequestDetailTestTags.ERROR_STATE),
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
                            text =
                                if (uiState.isForbidden) {
                                    "Acceso restringido"
                                } else if (uiState.isNotFound) {
                                    "Solicitud no encontrada"
                                } else {
                                    "No fue posible cargar la solicitud"
                                },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!uiState.isNotFound && !uiState.isForbidden) {
                                OutlinedButton(onClick = onRetry) {
                                    Text(text = "Reintentar")
                                }
                            }
                            Button(
                                onClick = onBackToList,
                                modifier = Modifier.testTag(RequestDetailTestTags.BACK_TO_LIST_BUTTON),
                            ) {
                                Text(text = "Volver a mis solicitudes")
                            }
                        }
                    }
                }
            }
        }

        is RequestDetailUiState.Content -> {
            RequestDetailContent(
                uiState = uiState,
                onOpenPreviousRequest = onOpenPreviousRequest,
                onCreateRelatedRequest = onCreateRelatedRequest,
                onOpenCancelDialog = onOpenCancelDialog,
                onCancelReasonChange = onCancelReasonChange,
                onConfirmCancel = onConfirmCancel,
                onDismissCancelDialog = onDismissCancelDialog,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun RequestDetailContent(
    uiState: RequestDetailUiState.Content,
    onOpenPreviousRequest: (String) -> Unit,
    onCreateRelatedRequest: (String) -> Unit,
    onOpenCancelDialog: () -> Unit,
    onCancelReasonChange: (String) -> Unit,
    onConfirmCancel: () -> Unit,
    onDismissCancelDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = uiState.detail

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!uiState.actionError.isNullOrBlank()) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(RequestDetailTestTags.ACTION_ERROR_BANNER)
                            .semantics(mergeDescendants = true) {},
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Text(
                        text = uiState.actionError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            if (!uiState.successNotice.isNullOrBlank()) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(RequestDetailTestTags.SUCCESS_BANNER)
                            .semantics(mergeDescendants = true) {},
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                ) {
                    Text(
                        text = uiState.successNotice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            HeaderCard(
                detail = detail,
                onOpenPreviousRequest = onOpenPreviousRequest,
            )

            DescriptionCard(detail = detail)

            if (detail.canCreateRelated && hasOfficialResponseOrClosure(detail)) {
                OfficialResponseCard(detail = detail)
            }

            if (detail.canCreateRelated || detail.canCancel) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (detail.canCreateRelated) {
                        Button(
                            onClick = { onCreateRelatedRequest(detail.code) },
                            modifier = Modifier.testTag(RequestDetailTestTags.CREATE_RELATED_BUTTON),
                        ) {
                            Text(text = "Nueva solicitud relacionada")
                        }
                    }
                    if (detail.canCancel) {
                        OutlinedButton(
                            onClick = onOpenCancelDialog,
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            modifier = Modifier.testTag(RequestDetailTestTags.CANCEL_BUTTON),
                        ) {
                            Text(text = "Cancelar solicitud")
                        }
                    }
                }
            }

            HistoryCard(history = detail.history)
        }
    }

    if (uiState.isCancelDialogOpen) {
        CancelRequestDialog(
            requestCode = detail.code,
            reason = uiState.cancelReason,
            reasonError = uiState.cancelReasonError,
            isCancelling = uiState.isCancelling,
            onReasonChange = onCancelReasonChange,
            onConfirm = onConfirmCancel,
            onDismiss = onDismissCancelDialog,
        )
    }
}

private fun hasOfficialResponseOrClosure(detail: RequestDetailUiModel): Boolean =
    !detail.responseText.isNullOrBlank() ||
        detail.formattedRespondedAt != null ||
        detail.formattedClosedAt != null

@Composable
private fun HeaderCard(
    detail: RequestDetailUiModel,
    onOpenPreviousRequest: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = detail.code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(RequestDetailTestTags.CODE),
                )
                RequestStatusChip(
                    status = detail.status,
                    label = detail.statusLabel,
                    modifier = Modifier.testTag(RequestDetailTestTags.STATUS_BADGE),
                )
            }
            Text(
                text = "Categoría: ${detail.categoryLabel}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Radicada: ${detail.formattedCreatedAt} · Última actualización: ${detail.formattedUpdatedAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val prevCode = detail.previousRequestCode
            if (!prevCode.isNullOrBlank()) {
                TextButton(
                    onClick = { onOpenPreviousRequest(prevCode) },
                    modifier = Modifier.testTag(RequestDetailTestTags.PREVIOUS_CODE_LINK),
                ) {
                    Text(text = "Solicitud vinculada: $prevCode")
                }
            }
        }
    }
}

@Composable
private fun DescriptionCard(detail: RequestDetailUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = detail.subject,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider()
            Text(
                text = detail.description,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun OfficialResponseCard(detail: RequestDetailUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(RequestDetailTestTags.RESPONSE_CARD),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Respuesta oficial",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!detail.responseText.isNullOrBlank()) {
                Text(
                    text = detail.responseText,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (detail.formattedRespondedAt != null) {
                Text(
                    text = "Fecha de respuesta: ${detail.formattedRespondedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (detail.formattedClosedAt != null) {
                Text(
                    text = "Fecha de cierre: ${detail.formattedClosedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(history: List<RequestHistoryItemUiModel>) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(RequestDetailTestTags.HISTORY_LIST),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Historial visible",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (history.isEmpty()) {
                Text(
                    text = "Sin eventos registrados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                history.forEachIndexed { index, entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry.actionLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = entry.formattedOccurredAt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = entry.transitionLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (!entry.comment.isNullOrBlank()) {
                            Text(
                                text = entry.comment,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (index < history.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
