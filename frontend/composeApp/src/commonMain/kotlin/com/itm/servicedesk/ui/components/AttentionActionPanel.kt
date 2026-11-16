package com.itm.servicedesk.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.data.mapper.AttentionUiMapper
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.StaffMember
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.validation.AttentionFormValidator
import com.itm.servicedesk.viewmodel.attention.AgentActionTab
import com.itm.servicedesk.viewmodel.attention.AttentionDetailUiState

object AttentionActionPanelTestTags {
    const val PANEL = "attention_action_panel"
    const val READ_ONLY_NOTICE = "attention_read_only_notice"
    const val AGENT_DROPDOWN = "attention_agent_dropdown"
    const val AGENT_RETRY_BUTTON = "attention_agent_retry_button"
    const val REASSIGN_SLA_NOTICE = "attention_reassign_sla_notice"
    const val REASSIGN_REASON_INPUT = "attention_reassign_reason_input"
    const val ASSIGN_SUBMIT_BUTTON = "attention_assign_submit_button"
    const val REASSIGN_SUBMIT_BUTTON = "attention_reassign_submit_button"
    const val INTERVENTION_INPUT = "attention_intervention_input"
    const val INTERVENTION_SUBMIT_BUTTON = "attention_intervention_submit_button"
    const val ESCALATE_REASON_INPUT = "attention_escalate_reason_input"
    const val ESCALATE_SUBMIT_BUTTON = "attention_escalate_submit_button"
    const val RESPONSE_TEXT_INPUT = "attention_response_text_input"
    const val RESPONSE_SUBMIT_BUTTON = "attention_response_submit_button"

    fun agentOption(agentId: String): String = "attention_agent_option_$agentId"

    fun priorityChip(priority: RequestPriority): String = "attention_priority_chip_${priority.name}"

    fun agentTab(tab: AgentActionTab): String = "attention_agent_tab_${tab.name}"
}

@Composable
fun AttentionActionPanel(
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
    modifier: Modifier = Modifier,
) {
    val detail = uiState.detail ?: return
    val uiMapper = remember { AttentionUiMapper() }

    when {
        userRole == UserRole.COORDINADOR && detail.status == RequestStatus.ABIERTA -> {
            CoordinatorAssignCard(
                uiState = uiState,
                uiMapper = uiMapper,
                isReassign = false,
                onSelectAgent = onSelectAgent,
                onRetryLoadAgents = onRetryLoadAgents,
                onSelectPriority = onSelectPriority,
                onReassignmentReasonChange = onReassignmentReasonChange,
                onSubmit = onRequestAssign,
                modifier = modifier,
            )
        }

        userRole == UserRole.COORDINADOR && detail.status == RequestStatus.ESCALADA -> {
            CoordinatorAssignCard(
                uiState = uiState,
                uiMapper = uiMapper,
                isReassign = true,
                onSelectAgent = onSelectAgent,
                onRetryLoadAgents = onRetryLoadAgents,
                onSelectPriority = onSelectPriority,
                onReassignmentReasonChange = onReassignmentReasonChange,
                onSubmit = onRequestReassign,
                modifier = modifier,
            )
        }

        userRole == UserRole.PERSONAL_ATENCION && detail.status == RequestStatus.EN_REVISION -> {
            AgentActionCard(
                uiState = uiState,
                onSelectAgentTab = onSelectAgentTab,
                onInterventionCommentChange = onInterventionCommentChange,
                onSubmitIntervention = onSubmitIntervention,
                onEscalationReasonChange = onEscalationReasonChange,
                onRequestEscalate = onRequestEscalate,
                onResponseTextChange = onResponseTextChange,
                onRequestRespond = onRequestRespond,
                modifier = modifier,
            )
        }

        else -> {
            Card(
                modifier = modifier.fillMaxWidth().testTag(AttentionActionPanelTestTags.READ_ONLY_NOTICE),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Text(
                    text =
                        if (detail.status == RequestStatus.RESUELTA || detail.status == RequestStatus.CERRADA) {
                            "Esta solicitud se encuentra finalizada en modo de solo lectura."
                        } else {
                            "No hay acciones operativas disponibles para tu rol en el estado actual."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoordinatorAssignCard(
    uiState: AttentionDetailUiState,
    uiMapper: AttentionUiMapper,
    isReassign: Boolean,
    onSelectAgent: (String) -> Unit,
    onRetryLoadAgents: () -> Unit,
    onSelectPriority: (RequestPriority?) -> Unit,
    onReassignmentReasonChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = uiState.detail ?: return
    val agentError = uiState.validationErrors[AttentionFormValidator.FIELD_ASSIGNEE_ID]
    val priorityError = uiState.validationErrors[AttentionFormValidator.FIELD_PRIORITY]
    val reasonError = uiState.validationErrors[AttentionFormValidator.FIELD_REASON]

    Card(
        modifier = modifier.fillMaxWidth().testTag(AttentionActionPanelTestTags.PANEL),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isReassign) "Reasignar solicitud escalada" else "Asignar solicitud",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            if (isReassign) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(AttentionActionPanelTestTags.REASSIGN_SLA_NOTICE),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                ) {
                    Text(
                        text =
                            "Aviso SLA: el vencimiento SLA se recalcula desde la primera asignación " +
                                "y no se reinicia al reasignar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (uiState.isLoadingAgents) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Cargando personal de atención...",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else if (!uiState.agentsLoadError.isNullOrBlank()) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = uiState.agentsLoadError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onRetryLoadAgents,
                            modifier = Modifier.testTag(AttentionActionPanelTestTags.AGENT_RETRY_BUTTON),
                        ) {
                            Text(text = "Reintentar")
                        }
                    }
                }
            } else {
                AgentSelectorField(
                    agents = uiState.availableAgents,
                    selectedAgentId = uiState.selectedAgentId,
                    agentError = agentError,
                    enabled = !uiState.isSubmitting,
                    onSelectAgent = onSelectAgent,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val currentPriorityText =
                    detail.priority
                        ?.let(uiMapper::priorityLabel)
                        ?.let { " (actual: $it)" }
                        .orEmpty()
                Text(
                    text =
                        if (isReassign) {
                            "Prioridad SLA (opcional)$currentPriorityText"
                        } else {
                            "Prioridad SLA (obligatoria)"
                        },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RequestPriority.entries.forEach { priority ->
                        val selected = uiState.selectedPriority == priority
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (isReassign && selected) {
                                    onSelectPriority(null)
                                } else {
                                    onSelectPriority(priority)
                                }
                            },
                            enabled = !uiState.isSubmitting,
                            label = { Text(text = uiMapper.priorityLabel(priority)) },
                            modifier = Modifier.testTag(AttentionActionPanelTestTags.priorityChip(priority)),
                        )
                    }
                }
                if (!priorityError.isNullOrBlank()) {
                    Text(
                        text = priorityError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (isReassign) {
                OutlinedTextField(
                    value = uiState.reassignmentReasonDraft,
                    onValueChange = onReassignmentReasonChange,
                    label = { Text(text = "Motivo de reasignación") },
                    enabled = !uiState.isSubmitting,
                    isError = reasonError != null,
                    minLines = 3,
                    maxLines = 5,
                    supportingText = {
                        val trimmedLen = uiState.reassignmentReasonDraft.trim().length
                        val counter = "$trimmedLen / ${AttentionFormValidator.MAX_REASON_LENGTH} caracteres (mín. 10)"
                        Text(text = reasonError ?: counter)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(AttentionActionPanelTestTags.REASSIGN_REASON_INPUT),
                )
            }

            Button(
                onClick = onSubmit,
                enabled = !uiState.isSubmitting && !uiState.isLoadingAgents,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            if (isReassign) {
                                AttentionActionPanelTestTags.REASSIGN_SUBMIT_BUTTON
                            } else {
                                AttentionActionPanelTestTags.ASSIGN_SUBMIT_BUTTON
                            },
                        ),
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(text = if (isReassign) "Reasignar solicitud" else "Asignar solicitud")
                }
            }
        }
    }
}

@Composable
private fun AgentSelectorField(
    agents: List<StaffMember>,
    selectedAgentId: String,
    agentError: String?,
    enabled: Boolean,
    onSelectAgent: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName =
        agents.firstOrNull { it.id == selectedAgentId }?.fullName
            ?: "Selecciona un integrante de personal de atención"

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(text = "Responsable asignado") },
            isError = agentError != null,
            supportingText = agentError?.let { msg -> { Text(text = msg) } },
            colors =
                OutlinedTextFieldDefaults.colors(
                    disabledTextColor =
                        if (selectedAgentId.isNotBlank()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    disabledBorderColor =
                        if (agentError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    disabledLabelColor =
                        if (agentError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    disabledSupportingTextColor = MaterialTheme.colorScheme.error,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(AttentionActionPanelTestTags.AGENT_DROPDOWN)
                    .clickable(enabled = enabled && agents.isNotEmpty()) {
                        expanded = true
                    },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            agents.forEach { agent ->
                DropdownMenuItem(
                    text = { Text(text = "${agent.fullName} (${agent.email})") },
                    onClick = {
                        expanded = false
                        onSelectAgent(agent.id)
                    },
                    modifier = Modifier.testTag(AttentionActionPanelTestTags.agentOption(agent.id)),
                )
            }
        }
    }
}

@Composable
private fun AgentActionCard(
    uiState: AttentionDetailUiState,
    onSelectAgentTab: (AgentActionTab) -> Unit,
    onInterventionCommentChange: (String) -> Unit,
    onSubmitIntervention: () -> Unit,
    onEscalationReasonChange: (String) -> Unit,
    onRequestEscalate: () -> Unit,
    onResponseTextChange: (String) -> Unit,
    onRequestRespond: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val commentError = uiState.validationErrors[AttentionFormValidator.FIELD_COMMENT]
    val reasonError = uiState.validationErrors[AttentionFormValidator.FIELD_REASON]
    val responseError = uiState.validationErrors[AttentionFormValidator.FIELD_RESPONSE_TEXT]

    Card(
        modifier = modifier.fillMaxWidth().testTag(AttentionActionPanelTestTags.PANEL),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Acciones de atención",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            PrimaryTabRow(
                selectedTabIndex = uiState.activeAgentTab.ordinal,
            ) {
                AgentActionTab.entries.forEach { tab ->
                    val label =
                        when (tab) {
                            AgentActionTab.INTERVENTION -> "Intervención"
                            AgentActionTab.ESCALATE -> "Escalar"
                            AgentActionTab.RESPOND -> "Responder"
                        }
                    Tab(
                        selected = uiState.activeAgentTab == tab,
                        onClick = { onSelectAgentTab(tab) },
                        enabled = !uiState.isSubmitting,
                        text = { Text(text = label) },
                        modifier = Modifier.testTag(AttentionActionPanelTestTags.agentTab(tab)),
                    )
                }
            }

            when (uiState.activeAgentTab) {
                AgentActionTab.INTERVENTION -> {
                    OutlinedTextField(
                        value = uiState.interventionCommentDraft,
                        onValueChange = onInterventionCommentChange,
                        label = { Text(text = "Nota de intervención interna") },
                        enabled = !uiState.isSubmitting,
                        isError = commentError != null,
                        minLines = 3,
                        maxLines = 6,
                        supportingText = {
                            val trimmedLen = uiState.interventionCommentDraft.trim().length
                            val counter =
                                "$trimmedLen / ${AttentionFormValidator.MAX_COMMENT_LENGTH} caracteres (mín. 10)"
                            Text(text = commentError ?: counter)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(AttentionActionPanelTestTags.INTERVENTION_INPUT),
                    )
                    Button(
                        onClick = onSubmitIntervention,
                        enabled = !uiState.isSubmitting,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(AttentionActionPanelTestTags.INTERVENTION_SUBMIT_BUTTON),
                    ) {
                        Text(text = "Registrar intervención")
                    }
                }

                AgentActionTab.ESCALATE -> {
                    OutlinedTextField(
                        value = uiState.escalationReasonDraft,
                        onValueChange = onEscalationReasonChange,
                        label = { Text(text = "Motivo de escalamiento") },
                        enabled = !uiState.isSubmitting,
                        isError = reasonError != null,
                        minLines = 3,
                        maxLines = 5,
                        supportingText = {
                            val trimmedLen = uiState.escalationReasonDraft.trim().length
                            val counter =
                                "$trimmedLen / ${AttentionFormValidator.MAX_REASON_LENGTH} caracteres (mín. 10)"
                            Text(text = reasonError ?: counter)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(AttentionActionPanelTestTags.ESCALATE_REASON_INPUT),
                    )
                    OutlinedButton(
                        onClick = onRequestEscalate,
                        enabled = !uiState.isSubmitting,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(AttentionActionPanelTestTags.ESCALATE_SUBMIT_BUTTON),
                    ) {
                        Text(text = "Escalar solicitud")
                    }
                }

                AgentActionTab.RESPOND -> {
                    Text(
                        text = "La respuesta oficial es definitiva e inmutable una vez confirmada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = uiState.responseTextDraft,
                        onValueChange = onResponseTextChange,
                        label = { Text(text = "Respuesta definitiva al solicitante") },
                        enabled = !uiState.isSubmitting,
                        isError = responseError != null,
                        minLines = 4,
                        maxLines = 8,
                        supportingText = {
                            val trimmedLen = uiState.responseTextDraft.trim().length
                            val counter =
                                "$trimmedLen / ${AttentionFormValidator.MAX_RESPONSE_LENGTH} caracteres (mín. 20)"
                            Text(text = responseError ?: counter)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(AttentionActionPanelTestTags.RESPONSE_TEXT_INPUT),
                    )
                    Button(
                        onClick = onRequestRespond,
                        enabled = !uiState.isSubmitting,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(AttentionActionPanelTestTags.RESPONSE_SUBMIT_BUTTON),
                    ) {
                        Text(text = "Enviar respuesta oficial")
                    }
                }
            }
        }
    }
}
