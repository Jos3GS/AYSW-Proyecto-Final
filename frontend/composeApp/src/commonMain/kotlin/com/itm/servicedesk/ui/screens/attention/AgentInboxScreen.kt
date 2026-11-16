package com.itm.servicedesk.ui.screens.attention

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.data.mapper.AttentionUiMapper
import com.itm.servicedesk.domain.model.AttentionRequestSummary
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.ui.components.OverdueBadge
import com.itm.servicedesk.ui.components.RequestStatusChip
import com.itm.servicedesk.viewmodel.attention.AgentInboxUiState

object AgentInboxTestTags {
    const val FILTER_ALL = "agent_inbox_filter_all"
    const val LIST_CONTAINER = "agent_inbox_list_container"
    const val EMPTY_STATE = "agent_inbox_empty_state"
    const val ERROR_BANNER = "agent_inbox_error_banner"
    const val ERROR_RETRY_BUTTON = "agent_inbox_error_retry_button"
    const val PREV_PAGE_BUTTON = "agent_inbox_prev_page_button"
    const val NEXT_PAGE_BUTTON = "agent_inbox_next_page_button"
    const val PAGINATION_LABEL = "agent_inbox_pagination_label"

    fun filterStatus(status: RequestStatus): String = "agent_inbox_filter_${status.name}"

    fun item(code: String): String = "agent_inbox_item_$code"
}

@Composable
fun AgentInboxScreen(
    uiState: AgentInboxUiState,
    onSelectStatusFilter: (RequestStatus?) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiMapper = remember { AttentionUiMapper() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 1080.dp)
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Bandeja de atención",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Solicitudes asignadas (20 registros por página)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.selectedStatusFilter == null,
                    onClick = { onSelectStatusFilter(null) },
                    enabled = !uiState.isLoading,
                    label = { Text(text = "Todas") },
                    modifier = Modifier.testTag(AgentInboxTestTags.FILTER_ALL),
                )
                listOf(
                    RequestStatus.EN_REVISION,
                    RequestStatus.ESCALADA,
                    RequestStatus.RESUELTA,
                    RequestStatus.CERRADA,
                ).forEach { status ->
                    FilterChip(
                        selected = uiState.selectedStatusFilter == status,
                        onClick = { onSelectStatusFilter(status) },
                        enabled = !uiState.isLoading,
                        label = { Text(text = uiMapper.statusLabel(status)) },
                        modifier = Modifier.testTag(AgentInboxTestTags.filterStatus(status)),
                    )
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag(AgentInboxTestTags.ERROR_BANNER),
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
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag(AgentInboxTestTags.ERROR_RETRY_BUTTON),
                        ) {
                            Text(text = "Reintentar")
                        }
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                val isWideLayout = maxWidth >= 600.dp

                if (uiState.isLoading && uiState.items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (!uiState.isLoading && uiState.errorMessage == null && uiState.items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag(AgentInboxTestTags.EMPTY_STATE),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Text(
                                text = "No tienes solicitudes asignadas para el filtro seleccionado.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(AgentInboxTestTags.LIST_CONTAINER),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isWideLayout) {
                            item(key = "agent_inbox_table_header") {
                                AgentInboxTableHeader()
                            }
                        }
                        items(
                            items = uiState.items,
                            key = { item -> item.code },
                        ) { item ->
                            if (isWideLayout) {
                                AgentInboxTableRow(
                                    item = item,
                                    uiMapper = uiMapper,
                                    onClick = { onOpenDetail(item.code) },
                                )
                            } else {
                                AgentInboxSummaryCard(
                                    item = item,
                                    uiMapper = uiMapper,
                                    onClick = { onOpenDetail(item.code) },
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPreviousPage,
                    enabled = !uiState.isLoading && uiState.currentPage > 1,
                    modifier = Modifier.testTag(AgentInboxTestTags.PREV_PAGE_BUTTON),
                ) {
                    Text(text = "Anterior")
                }

                Text(
                    text =
                        "Página ${uiState.currentPage} de ${uiState.totalPages} " +
                            "(${uiState.totalItems} registros)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(AgentInboxTestTags.PAGINATION_LABEL),
                )

                OutlinedButton(
                    onClick = onNextPage,
                    enabled = !uiState.isLoading && uiState.currentPage < uiState.totalPages,
                    modifier = Modifier.testTag(AgentInboxTestTags.NEXT_PAGE_BUTTON),
                ) {
                    Text(text = "Siguiente")
                }
            }
        }
    }
}

@Composable
private fun AgentInboxTableHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Radicado",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.1f),
            )
            Text(
                text = "Asunto y solicitante",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.9f),
            )
            Text(
                text = "Categoría / Prioridad",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.2f),
            )
            Text(
                text = "Estado SLA",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.1f),
            )
            Text(
                text = "Vencimiento / Radicada",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.1f),
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun AgentInboxTableRow(
    item: AttentionRequestSummary,
    uiMapper: AttentionUiMapper,
    onClick: () -> Unit,
) {
    val createdLabel = uiMapper.formatDateTime(item.createdAt)
    val dueLabel = uiMapper.formatOptionalDateTime(item.dueAt)
    val priorityLabel = item.priority?.let(uiMapper::priorityLabel) ?: "Sin prioridad"

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(AgentInboxTestTags.item(item.code))
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.code,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.1f),
            )
            Column(
                modifier = Modifier.weight(1.9f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Solicitante: ${item.requesterName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = uiMapper.categoryLabel(item.category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = priorityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RequestStatusChip(
                    status = item.status,
                    label = uiMapper.statusLabel(item.status),
                )
                if (item.isOverdue) {
                    OverdueBadge()
                }
            }
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (dueLabel != null) {
                    Text(
                        text = "Vence: $dueLabel",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = createdLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AgentInboxSummaryCard(
    item: AttentionRequestSummary,
    uiMapper: AttentionUiMapper,
    onClick: () -> Unit,
) {
    val createdLabel = uiMapper.formatDateTime(item.createdAt)
    val dueLabel = uiMapper.formatOptionalDateTime(item.dueAt)
    val priorityLabel = item.priority?.let(uiMapper::priorityLabel) ?: "Sin prioridad"

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(AgentInboxTestTags.item(item.code))
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.code,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.isOverdue) {
                        OverdueBadge()
                    }
                    RequestStatusChip(
                        status = item.status,
                        label = uiMapper.statusLabel(item.status),
                    )
                }
            }
            Text(
                text = item.subject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Solicitante: ${item.requesterName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = uiMapper.categoryLabel(item.category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = priorityLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Radicada: $createdLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dueLabel != null) {
                    Text(
                        text = "Vence: $dueLabel",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
