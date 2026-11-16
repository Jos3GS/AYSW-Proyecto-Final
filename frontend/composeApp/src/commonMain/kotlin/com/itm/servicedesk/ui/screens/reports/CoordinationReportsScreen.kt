package com.itm.servicedesk.ui.screens.reports

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.data.mapper.ReportUiMapper
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.ui.components.OverdueBadge
import com.itm.servicedesk.ui.components.ReportFilterPanel
import com.itm.servicedesk.ui.components.ReportSummarySection
import com.itm.servicedesk.ui.components.RequestStatusChip
import com.itm.servicedesk.viewmodel.reports.CoordinationReportRequestItemUi
import com.itm.servicedesk.viewmodel.reports.CoordinationReportsUiState

object CoordinationReportsTestTags {
    const val SCREEN = "coordination_reports_screen"
    const val LIST_CONTAINER = "coordination_reports_list"
    const val EMPTY_STATE = "coordination_reports_empty_state"
    const val ERROR_BANNER = "coordination_reports_error_banner"
    const val RETRY_BUTTON = "coordination_reports_retry_button"
    const val PREV_PAGE_BUTTON = "coordination_reports_prev_page_button"
    const val NEXT_PAGE_BUTTON = "coordination_reports_next_page_button"
    const val PAGINATION_LABEL = "coordination_reports_pagination_label"

    fun item(code: String): String = "coordination_reports_item_$code"

    fun slaBadge(code: String): String = "coordination_reports_sla_badge_$code"
}

@Composable
fun CoordinationReportsScreen(
    uiState: CoordinationReportsUiState,
    onFromDateChanged: (String) -> Unit,
    onToDateChanged: (String) -> Unit,
    onStatusFilterChanged: (RequestStatus?) -> Unit,
    onCategoryFilterChanged: (RequestCategory?) -> Unit,
    onPriorityFilterChanged: (RequestPriority?) -> Unit,
    onAssigneeFilterChanged: (String?) -> Unit,
    onApplyFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().testTag(CoordinationReportsTestTags.SCREEN),
        contentAlignment = Alignment.TopCenter,
    ) {
        val isWideLayout = maxWidth >= 720.dp
        val requestsPage = uiState.requestsPage
        val totalPages = requestsPage?.totalPages ?: 1
        val totalItems = requestsPage?.totalItems ?: 0L

        LazyColumn(
            modifier =
                Modifier
                    .widthIn(max = 1120.dp)
                    .fillMaxSize()
                    .testTag(CoordinationReportsTestTags.LIST_CONTAINER),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!uiState.errorMessage.isNullOrBlank()) {
                item(key = "reports_error_banner") {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(CoordinationReportsTestTags.ERROR_BANNER),
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
                                modifier = Modifier.testTag(CoordinationReportsTestTags.RETRY_BUTTON),
                            ) {
                                Text(text = "Reintentar")
                            }
                        }
                    }
                }
            }

            item(key = "reports_filter_panel") {
                ReportFilterPanel(
                    draftFilter = uiState.draftFilter,
                    availableAgents = uiState.availableAgents,
                    isLoading = uiState.isLoading,
                    isLoadingAgents = uiState.isLoadingAgents,
                    onFromDateChanged = onFromDateChanged,
                    onToDateChanged = onToDateChanged,
                    onStatusFilterChanged = onStatusFilterChanged,
                    onCategoryFilterChanged = onCategoryFilterChanged,
                    onPriorityFilterChanged = onPriorityFilterChanged,
                    onAssigneeFilterChanged = onAssigneeFilterChanged,
                    onApplyFilters = onApplyFilters,
                    onClearFilters = onClearFilters,
                )
            }

            if (uiState.isLoadingSummary && uiState.summary == null) {
                item(key = "reports_summary_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.summary != null) {
                item(key = "reports_summary_section") {
                    ReportSummarySection(summary = uiState.summary)
                }
            }

            item(key = "reports_requests_section_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Listado general de solicitudes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Hasta 20 solicitudes por página · Selecciona una fila para ver el detalle completo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (uiState.isLoadingRequests) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            if (uiState.isLoadingRequests && requestsPage == null) {
                item(key = "reports_requests_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (requestsPage != null && requestsPage.items.isEmpty() && !uiState.isLoadingRequests) {
                item(key = "reports_requests_empty") {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(CoordinationReportsTestTags.EMPTY_STATE),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    ) {
                        Text(
                            text = "No se encontraron solicitudes con los filtros aplicados.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            } else if (requestsPage != null) {
                if (isWideLayout) {
                    item(key = "reports_requests_table_header") {
                        ReportRequestsTableHeader()
                    }
                }

                items(
                    items = requestsPage.items,
                    key = { item -> item.code },
                ) { item ->
                    if (isWideLayout) {
                        ReportRequestTableRow(
                            item = item,
                            onClick = { onOpenDetail(item.code) },
                        )
                    } else {
                        ReportRequestCard(
                            item = item,
                            onClick = { onOpenDetail(item.code) },
                        )
                    }
                }
            }

            item(key = "reports_pagination") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onPreviousPage,
                        enabled = !uiState.isLoading && uiState.currentPage > 1,
                        modifier = Modifier.testTag(CoordinationReportsTestTags.PREV_PAGE_BUTTON),
                    ) {
                        Text(text = "Anterior")
                    }

                    Text(
                        text = "Página ${uiState.currentPage} de $totalPages (Total: $totalItems)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(CoordinationReportsTestTags.PAGINATION_LABEL),
                    )

                    OutlinedButton(
                        onClick = onNextPage,
                        enabled = !uiState.isLoading && uiState.currentPage < totalPages,
                        modifier = Modifier.testTag(CoordinationReportsTestTags.NEXT_PAGE_BUTTON),
                    ) {
                        Text(text = "Siguiente")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRequestsTableHeader() {
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
                modifier = Modifier.weight(1.8f),
            )
            Text(
                text = "Categoría / Prioridad",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.2f),
            )
            Text(
                text = "Responsable",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.1f),
            )
            Text(
                text = "Estado y SLA",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.2f),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportRequestTableRow(
    item: CoordinationReportRequestItemUi,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(CoordinationReportsTestTags.item(item.code))
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                modifier = Modifier.weight(1.8f),
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
                    text = item.categoryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.priorityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.assignedToName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.1f),
            )
            FlowRow(
                modifier = Modifier.weight(1.2f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RequestStatusChip(
                    status = item.status,
                    label = item.statusLabel,
                )
                if (item.isOverdue) {
                    OverdueBadge()
                }
                if (item.slaResultLabel != null) {
                    SlaComplianceBadge(
                        code = item.code,
                        label = item.slaResultLabel,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.createdAtFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.dueAtFormatted != null) {
                    Text(
                        text = "Vence: ${item.dueAtFormatted}",
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportRequestCard(
    item: CoordinationReportRequestItemUi,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(CoordinationReportsTestTags.item(item.code))
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                RequestStatusChip(
                    status = item.status,
                    label = item.statusLabel,
                )
            }

            Text(
                text = item.subject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "Solicitante: ${item.requesterName} · Responsable: ${item.assignedToName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.categoryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = item.priorityLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            if (item.isOverdue || item.slaResultLabel != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (item.isOverdue) {
                        OverdueBadge()
                    }
                    if (item.slaResultLabel != null) {
                        SlaComplianceBadge(
                            code = item.code,
                            label = item.slaResultLabel,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Creada: ${item.createdAtFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.dueAtFormatted != null) {
                    Text(
                        text = "Vence: ${item.dueAtFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlaComplianceBadge(
    code: String,
    label: String,
) {
    val isWithinSla = label == ReportUiMapper.SLA_WITHIN_LABEL
    val (containerColor, contentColor) =
        if (isWithinSla) {
            Color(0xFFD8F3DC) to Color(0xFF1B4332)
        } else {
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }

    Surface(
        modifier =
            Modifier
                .testTag(CoordinationReportsTestTags.slaBadge(code))
                .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
