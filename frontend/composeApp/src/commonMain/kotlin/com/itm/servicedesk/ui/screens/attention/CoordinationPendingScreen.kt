package com.itm.servicedesk.ui.screens.attention

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
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
import com.itm.servicedesk.ui.screens.home.HomeTestTags
import com.itm.servicedesk.viewmodel.attention.CoordinationPendingUiState

object CoordinationPendingTestTags {
    const val FILTER_ALL = "coordination_pending_filter_all"
    const val LIST_CONTAINER = "coordination_pending_list_container"
    const val EMPTY_STATE = "coordination_pending_empty_state"
    const val ERROR_BANNER = "coordination_pending_error_banner"
    const val ERROR_RETRY_BUTTON = "coordination_pending_error_retry_button"
    const val PREV_PAGE_BUTTON = "coordination_pending_prev_page_button"
    const val NEXT_PAGE_BUTTON = "coordination_pending_next_page_button"
    const val PAGINATION_LABEL = "coordination_pending_pagination_label"

    fun filterStatus(status: RequestStatus): String = "coordination_pending_filter_${status.name}"

    fun item(code: String): String = "coordination_pending_item_$code"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoordinationPendingScreen(
    uiState: CoordinationPendingUiState,
    onSelectStatusFilter: (RequestStatus?) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onManageStaffClick: (() -> Unit)? = null,
    onOpenReportsClick: (() -> Unit)? = null,
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.widthIn(min = 240.dp)) {
                    Text(
                        text = "Pendientes de coordinación",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Solicitudes Abierta y Escalada (más antiguas primero · 20 por página)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onOpenReportsClick != null || onManageStaffClick != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (onOpenReportsClick != null) {
                            OutlinedButton(
                                onClick = onOpenReportsClick,
                                modifier = Modifier.testTag(HomeTestTags.REPORTS_BUTTON),
                            ) {
                                Text(text = "Reportes y consolidado")
                            }
                        }
                        if (onManageStaffClick != null) {
                            Button(
                                onClick = onManageStaffClick,
                                modifier = Modifier.testTag(HomeTestTags.MANAGE_STAFF_BUTTON),
                            ) {
                                Text(text = "Personal interno")
                            }
                        }
                    }
                }
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
                    label = { Text(text = "Todas pendientes") },
                    modifier = Modifier.testTag(CoordinationPendingTestTags.FILTER_ALL),
                )
                listOf(RequestStatus.ABIERTA, RequestStatus.ESCALADA).forEach { status ->
                    FilterChip(
                        selected = uiState.selectedStatusFilter == status,
                        onClick = { onSelectStatusFilter(status) },
                        enabled = !uiState.isLoading,
                        label = { Text(text = uiMapper.statusLabel(status)) },
                        modifier = Modifier.testTag(CoordinationPendingTestTags.filterStatus(status)),
                    )
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag(CoordinationPendingTestTags.ERROR_BANNER),
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
                            modifier = Modifier.testTag(CoordinationPendingTestTags.ERROR_RETRY_BUTTON),
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
                            modifier = Modifier.fillMaxWidth().testTag(CoordinationPendingTestTags.EMPTY_STATE),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Text(
                                text = "No hay solicitudes pendientes para el filtro seleccionado.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(CoordinationPendingTestTags.LIST_CONTAINER),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isWideLayout) {
                            item(key = "coordination_pending_header") {
                                CoordinationTableHeader()
                            }
                        }
                        items(
                            items = uiState.items,
                            key = { item -> item.code },
                        ) { item ->
                            if (isWideLayout) {
                                CoordinationTableRow(
                                    item = item,
                                    uiMapper = uiMapper,
                                    onClick = { onOpenDetail(item.code) },
                                )
                            } else {
                                CoordinationSummaryCard(
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
                    modifier = Modifier.testTag(CoordinationPendingTestTags.PREV_PAGE_BUTTON),
                ) {
                    Text(text = "Anterior")
                }

                Text(
                    text =
                        "Página ${uiState.currentPage} de ${uiState.totalPages} " +
                            "(${uiState.totalItems} registros)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(CoordinationPendingTestTags.PAGINATION_LABEL),
                )

                OutlinedButton(
                    onClick = onNextPage,
                    enabled = !uiState.isLoading && uiState.currentPage < uiState.totalPages,
                    modifier = Modifier.testTag(CoordinationPendingTestTags.NEXT_PAGE_BUTTON),
                ) {
                    Text(text = "Siguiente")
                }
            }
        }
    }
}

@Composable
private fun CoordinationTableHeader() {
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
                text = "Fechas",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.1f),
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun CoordinationTableRow(
    item: AttentionRequestSummary,
    uiMapper: AttentionUiMapper,
    onClick: () -> Unit,
) {
    val createdLabel = uiMapper.formatDateTime(item.createdAt)
    val dueLabel = uiMapper.formatOptionalDateTime(item.dueAt)
    val priorityLabel = item.priority?.let(uiMapper::priorityLabel)

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(CoordinationPendingTestTags.item(item.code))
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
                if (priorityLabel != null) {
                    Text(
                        text = priorityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                Text(
                    text = createdLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (dueLabel != null) {
                    Text(
                        text = "Vence: $dueLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoordinationSummaryCard(
    item: AttentionRequestSummary,
    uiMapper: AttentionUiMapper,
    onClick: () -> Unit,
) {
    val createdLabel = uiMapper.formatDateTime(item.createdAt)
    val dueLabel = uiMapper.formatOptionalDateTime(item.dueAt)
    val priorityLabel = item.priority?.let(uiMapper::priorityLabel)

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(CoordinationPendingTestTags.item(item.code))
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
                if (priorityLabel != null) {
                    Text(
                        text = priorityLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
