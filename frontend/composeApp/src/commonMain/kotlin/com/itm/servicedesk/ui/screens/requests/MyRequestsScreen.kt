package com.itm.servicedesk.ui.screens.requests

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
import com.itm.servicedesk.data.mapper.RequestUiMapper
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.ui.components.RequestStatusChip
import com.itm.servicedesk.viewmodel.requests.MyRequestsUiState
import com.itm.servicedesk.viewmodel.requests.RequestSummaryUiModel

object MyRequestsTestTags {
    const val FILTER_ALL = "requests_filter_all"
    const val LIST_CONTAINER = "requests_list_container"
    const val EMPTY_STATE = "requests_empty_state"
    const val ERROR_BANNER = "requests_error_banner"
    const val ERROR_RETRY_BUTTON = "requests_error_retry_button"
    const val PREV_PAGE_BUTTON = "requests_prev_page_button"
    const val NEXT_PAGE_BUTTON = "requests_next_page_button"
    const val PAGINATION_LABEL = "requests_pagination_label"
    const val NEW_BUTTON = "requests_new_button"

    fun filterStatus(status: RequestStatus): String = "requests_filter_${status.name}"

    fun item(code: String): String = "requests_item_$code"
}

@Composable
fun MyRequestsScreen(
    uiState: MyRequestsUiState,
    onSelectStatusFilter: (RequestStatus?) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToCreate: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiMapper = remember { RequestUiMapper() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mis solicitudes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Consulta paginada (20 registros por página)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onNavigateToCreate,
                    modifier = Modifier.testTag(MyRequestsTestTags.NEW_BUTTON),
                ) {
                    Text(text = "Nueva solicitud")
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
                    label = { Text(text = "Todas") },
                    modifier = Modifier.testTag(MyRequestsTestTags.FILTER_ALL),
                )
                RequestStatus.entries.forEach { status ->
                    FilterChip(
                        selected = uiState.selectedStatusFilter == status,
                        onClick = { onSelectStatusFilter(status) },
                        enabled = !uiState.isLoading,
                        label = { Text(text = uiMapper.statusLabel(status)) },
                        modifier = Modifier.testTag(MyRequestsTestTags.filterStatus(status)),
                    )
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag(MyRequestsTestTags.ERROR_BANNER),
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
                            modifier = Modifier.testTag(MyRequestsTestTags.ERROR_RETRY_BUTTON),
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
                            modifier = Modifier.fillMaxWidth().testTag(MyRequestsTestTags.EMPTY_STATE),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Text(
                                text = "No tienes solicitudes registradas para el filtro seleccionado.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(MyRequestsTestTags.LIST_CONTAINER),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isWideLayout) {
                            item(key = "requests_table_header") {
                                RequestsTableHeader()
                            }
                        }
                        items(
                            items = uiState.items,
                            key = { item -> item.id },
                        ) { item ->
                            if (isWideLayout) {
                                RequestTableRow(
                                    item = item,
                                    onClick = { onOpenDetail(item.code) },
                                )
                            } else {
                                RequestSummaryCard(
                                    item = item,
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
                    enabled = !uiState.isLoading && uiState.page > 1,
                    modifier = Modifier.testTag(MyRequestsTestTags.PREV_PAGE_BUTTON),
                ) {
                    Text(text = "Anterior")
                }

                Text(
                    text = "Página ${uiState.page} de ${uiState.totalPages} (${uiState.totalItems} registros)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(MyRequestsTestTags.PAGINATION_LABEL),
                )

                OutlinedButton(
                    onClick = onNextPage,
                    enabled = !uiState.isLoading && uiState.page < uiState.totalPages,
                    modifier = Modifier.testTag(MyRequestsTestTags.NEXT_PAGE_BUTTON),
                ) {
                    Text(text = "Siguiente")
                }
            }
        }
    }
}

@Composable
private fun RequestsTableHeader() {
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
                text = "Asunto",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.8f),
            )
            Text(
                text = "Categoría",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.2f),
            )
            Text(
                text = "Estado",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.9f),
            )
            Text(
                text = "Fecha",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun RequestTableRow(
    item: RequestSummaryUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(MyRequestsTestTags.item(item.code))
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1.1f)) {
                Text(
                    text = item.code,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.previousRequestCode.isNullOrBlank()) {
                    Text(
                        text = "Previa: ${item.previousRequestCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = item.subject,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.8f),
            )
            Text(
                text = item.categoryLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.2f),
            )
            Box(modifier = Modifier.weight(0.9f)) {
                RequestStatusChip(status = item.status, label = item.statusLabel)
            }
            Text(
                text = item.formattedCreatedAt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RequestSummaryCard(
    item: RequestSummaryUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(MyRequestsTestTags.item(item.code))
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
                RequestStatusChip(status = item.status, label = item.statusLabel)
            }
            Text(
                text = item.subject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
                    text = item.formattedCreatedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!item.previousRequestCode.isNullOrBlank()) {
                Text(
                    text = "Solicitud previa: ${item.previousRequestCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
