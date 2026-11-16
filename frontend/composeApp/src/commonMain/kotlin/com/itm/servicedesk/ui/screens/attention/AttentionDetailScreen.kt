package com.itm.servicedesk.ui.screens.attention

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.data.mapper.AttentionUiMapper
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.InternalHistoryEntry
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.ui.components.AttentionActionPanel
import com.itm.servicedesk.ui.components.ConfirmationDialog
import com.itm.servicedesk.ui.components.OverdueBadge
import com.itm.servicedesk.ui.components.RequestStatusChip
import com.itm.servicedesk.viewmodel.attention.AgentActionTab
import com.itm.servicedesk.viewmodel.attention.AttentionDetailUiState
import com.itm.servicedesk.viewmodel.attention.PendingConfirmationAction

object AttentionDetailTestTags {
    const val CODE = "attention_detail_code"
    const val STATUS_BADGE = "attention_detail_status_badge"
    const val SLA_COMPLIANCE_BADGE = "attention_detail_sla_compliance_badge"
    const val PREVIOUS_CODE_TEXT = "attention_detail_previous_code_text"
    const val RESPONSE_CARD = "attention_detail_response_card"
    const val HISTORY_LIST = "attention_detail_history_list"
    const val ACTION_ERROR_BANNER = "attention_detail_action_error_banner"
    const val SUCCESS_BANNER = "attention_detail_success_banner"
    const val ERROR_STATE = "attention_detail_error_state"
    const val RETRY_BUTTON = "attention_detail_retry_button"
    const val BACK_TO_LIST_BUTTON = "attention_detail_back_to_list_button"

    fun historyItem(id: String): String = "attention_detail_history_item_$id"
}

@Composable
fun AttentionDetailScreen(
    uiState: AttentionDetailUiState,
    userRole: UserRole,
    onSelectAgent: (String) -> Unit,
    onRetryLoadAgents: () -> Unit,
    onSelectPriority: (RequestPriority?) -> Unit,
    onReassignmentReasonChange: (String) -> Unit,
    onRequestAssign: () -> Unit,
    onRequestReassign: () -> Unit,
    onSelectAgentTab: (AgentActionTab) -> Unit,
    onInterventionCommentChange: (String) -> Unit,
    onSubmitIntervention: () -> Unit,
    onEscalationReasonChange: (String) -> Unit,
    onRequestEscalate: () -> Unit,
    onResponseTextChange: (String) -> Unit,
    onRequestRespond: () -> Unit,
    onConfirmPendingAction: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onRetryLoadDetail: () -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiMapper = remember { AttentionUiMapper() }

    when {
        uiState.isLoading && uiState.detail == null -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.detail == null && !uiState.loadErrorMessage.isNullOrBlank() -> {
            AttentionDetailErrorView(
                message = uiState.loadErrorMessage,
                isForbidden = uiState.isForbidden,
                isNotFound = uiState.isNotFound,
                onRetry = onRetryLoadDetail,
                onBackToList = onBackToList,
                modifier = modifier,
            )
        }

        uiState.detail != null -> {
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
                    modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (!uiState.actionErrorMessage.isNullOrBlank()) {
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(AttentionDetailTestTags.ACTION_ERROR_BANNER)
                                    .semantics(mergeDescendants = true) {},
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                        ) {
                            Text(
                                text = uiState.actionErrorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }

                    if (!uiState.actionSuccessMessage.isNullOrBlank()) {
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(AttentionDetailTestTags.SUCCESS_BANNER)
                                    .semantics(mergeDescendants = true) {},
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                        ) {
                            Text(
                                text = uiState.actionSuccessMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }

                    AttentionHeaderCard(
                        detail = detail,
                        uiMapper = uiMapper,
                    )

                    AttentionDescriptionCard(detail = detail)

                    if (hasOfficialResponseSection(detail)) {
                        AttentionOfficialResponseCard(
                            detail = detail,
                            uiMapper = uiMapper,
                        )
                    }

                    AttentionActionPanel(
                        uiState = uiState,
                        userRole = userRole,
                        onSelectAgent = onSelectAgent,
                        onRetryLoadAgents = onRetryLoadAgents,
                        onSelectPriority = onSelectPriority,
                        onReassignmentReasonChange = onReassignmentReasonChange,
                        onRequestAssign = onRequestAssign,
                        onRequestReassign = onRequestReassign,
                        onSelectAgentTab = onSelectAgentTab,
                        onInterventionCommentChange = onInterventionCommentChange,
                        onSubmitIntervention = onSubmitIntervention,
                        onEscalationReasonChange = onEscalationReasonChange,
                        onRequestEscalate = onRequestEscalate,
                        onResponseTextChange = onResponseTextChange,
                        onRequestRespond = onRequestRespond,
                    )

                    InternalHistoryCard(
                        history = detail.history,
                        uiMapper = uiMapper,
                    )
                }
            }

            val pending = uiState.pendingConfirmation
            if (pending != null) {
                val (title, message, confirmText) =
                    confirmationCopy(
                        code = detail.code,
                        pending = pending,
                        uiMapper = uiMapper,
                    )
                ConfirmationDialog(
                    title = title,
                    message = message,
                    confirmText = confirmText,
                    isLoading = uiState.isSubmitting,
                    onConfirm = onConfirmPendingAction,
                    onDismiss = onDismissConfirmation,
                )
            }
        }
    }
}

@Composable
private fun AttentionDetailErrorView(
    message: String,
    isForbidden: Boolean,
    isNotFound: Boolean,
    onRetry: () -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .testTag(AttentionDetailTestTags.ERROR_STATE),
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
                        when {
                            isForbidden -> "Acceso restringido"
                            isNotFound -> "Solicitud no encontrada"
                            else -> "No fue posible cargar la solicitud"
                        },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!isNotFound && !isForbidden) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag(AttentionDetailTestTags.RETRY_BUTTON),
                        ) {
                            Text(text = "Reintentar")
                        }
                    }
                    Button(
                        onClick = onBackToList,
                        modifier = Modifier.testTag(AttentionDetailTestTags.BACK_TO_LIST_BUTTON),
                    ) {
                        Text(text = "Volver a la bandeja")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionHeaderCard(
    detail: AttentionRequestDetail,
    uiMapper: AttentionUiMapper,
) {
    val priorityLabel = detail.priority?.let(uiMapper::priorityLabel) ?: "Sin asignar"
    val assignedLabel = detail.assignedAgentName ?: "Sin asignar"
    val createdLabel = uiMapper.formatDateTime(detail.createdAt)
    val assignedAtLabel = uiMapper.formatOptionalDateTime(detail.assignedAt)
    val dueAtLabel = uiMapper.formatOptionalDateTime(detail.dueAt)

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
                    modifier = Modifier.testTag(AttentionDetailTestTags.CODE),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (detail.isOverdue) {
                        OverdueBadge()
                    }
                    RequestStatusChip(
                        status = detail.status,
                        label = uiMapper.statusLabel(detail.status),
                        modifier = Modifier.testTag(AttentionDetailTestTags.STATUS_BADGE),
                    )
                }
            }
            Text(
                text = "Categoría: ${uiMapper.categoryLabel(detail.category)} · Prioridad: $priorityLabel",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Solicitante: ${detail.requesterName} · Responsable: $assignedLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Radicada: $createdLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (assignedAtLabel != null || dueAtLabel != null) {
                val slaLine =
                    listOfNotNull(
                        assignedAtLabel?.let { "Asignada: $it" },
                        dueAtLabel?.let { "Vence SLA: $it" },
                    ).joinToString(" · ")
                Text(
                    text = slaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!detail.previousRequestCode.isNullOrBlank()) {
                Text(
                    text = "Solicitud previa vinculada: ${detail.previousRequestCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag(AttentionDetailTestTags.PREVIOUS_CODE_TEXT),
                )
            }
        }
    }
}

@Composable
private fun AttentionDescriptionCard(detail: AttentionRequestDetail) {
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

private fun hasOfficialResponseSection(detail: AttentionRequestDetail): Boolean =
    !detail.responseText.isNullOrBlank() ||
        detail.respondedAt != null ||
        detail.closedAt != null ||
        detail.slaCompliant != null

@Composable
private fun AttentionOfficialResponseCard(
    detail: AttentionRequestDetail,
    uiMapper: AttentionUiMapper,
) {
    val respondedLabel = uiMapper.formatOptionalDateTime(detail.respondedAt)
    val closedLabel = uiMapper.formatOptionalDateTime(detail.closedAt)
    val slaLabel = uiMapper.slaComplianceLabel(detail.slaCompliant)

    Card(
        modifier = Modifier.fillMaxWidth().testTag(AttentionDetailTestTags.RESPONSE_CARD),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            ),
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
                    text = "Respuesta oficial",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (slaLabel != null) {
                    val isCompliant = detail.slaCompliant == true
                    Surface(
                        modifier =
                            Modifier
                                .testTag(AttentionDetailTestTags.SLA_COMPLIANCE_BADGE)
                                .semantics(mergeDescendants = true) {},
                        shape = RoundedCornerShape(50),
                        color =
                            if (isCompliant) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                        contentColor =
                            if (isCompliant) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                    ) {
                        Text(
                            text = slaLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            if (!detail.responseText.isNullOrBlank()) {
                Text(
                    text = detail.responseText,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (respondedLabel != null) {
                Text(
                    text = "Fecha de respuesta: $respondedLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (closedLabel != null) {
                Text(
                    text = "Fecha de cierre: $closedLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InternalHistoryCard(
    history: List<InternalHistoryEntry>,
    uiMapper: AttentionUiMapper,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(AttentionDetailTestTags.HISTORY_LIST),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Historial interno de trazabilidad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (history.isEmpty()) {
                Text(
                    text = "Sin eventos internos registrados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                history.forEachIndexed { index, entry ->
                    Column(
                        modifier = Modifier.testTag(AttentionDetailTestTags.historyItem(entry.id)),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = uiMapper.eventTypeLabel(entry.eventType),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = uiMapper.formatDateTime(entry.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val actorDisplay =
                            if (entry.actorRole == HistoryActorRole.SISTEMA) {
                                "Actor: ${uiMapper.actorLabel(entry)}"
                            } else {
                                "Actor: ${uiMapper.actorLabel(entry)} (${uiMapper.actorRoleLabel(entry.actorRole)})"
                            }
                        Text(
                            text = actorDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val transition = uiMapper.transitionLabel(entry.previousStatus, entry.newStatus)
                        if (!transition.isNullOrBlank()) {
                            Text(
                                text = transition,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (!entry.detail.isNullOrBlank()) {
                            Text(
                                text = entry.detail,
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

private fun confirmationCopy(
    code: String,
    pending: PendingConfirmationAction,
    uiMapper: AttentionUiMapper,
): Triple<String, String, String> =
    when (pending) {
        is PendingConfirmationAction.Assign -> {
            val priorityText = uiMapper.priorityLabel(pending.command.priority)
            Triple(
                "Confirmar asignación",
                "¿Confirmas asignar la solicitud $code a ${pending.agentName} con prioridad $priorityText?",
                "Asignar",
            )
        }

        is PendingConfirmationAction.Reassign -> {
            val prioritySuffix =
                pending.command.priority
                    ?.let { priority ->
                        " con prioridad ${uiMapper.priorityLabel(priority)}"
                    }.orEmpty()
            Triple(
                "Confirmar reasignación",
                "¿Confirmas reasignar la solicitud $code a ${pending.agentName}$prioritySuffix?",
                "Reasignar",
            )
        }

        is PendingConfirmationAction.Escalate -> {
            Triple(
                "Confirmar escalamiento",
                "¿Confirmas escalar la solicitud $code? Pasará a estado Escalada para revisión de coordinación.",
                "Escalar",
            )
        }

        is PendingConfirmationAction.Respond -> {
            Triple(
                "Confirmar respuesta oficial",
                "¿Confirmas enviar esta respuesta oficial para la solicitud $code? " +
                    "Esta acción es definitiva y no se puede editar.",
                "Enviar respuesta",
            )
        }
    }
