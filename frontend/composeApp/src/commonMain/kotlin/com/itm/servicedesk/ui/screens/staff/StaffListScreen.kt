package com.itm.servicedesk.ui.screens.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.viewmodel.StaffItemUiModel
import com.itm.servicedesk.viewmodel.StaffListUiState

object StaffListTestTags {
    const val FILTER_ALL = "staff_filter_all"
    const val FILTER_AGENT = "staff_filter_agent"
    const val FILTER_COORDINATOR = "staff_filter_coordinator"
    const val LIST_CONTAINER = "staff_list_container"
    const val PREV_PAGE_BUTTON = "staff_prev_page_button"
    const val NEXT_PAGE_BUTTON = "staff_next_page_button"
    const val NEW_STAFF_BUTTON = "staff_new_button"
    const val PAGINATION_LABEL = "staff_pagination_label"
    const val EMPTY_STATE = "staff_empty_state"
    const val ERROR_BANNER = "staff_error_banner"
}

@Composable
fun StaffListScreen(
    uiState: StaffListUiState,
    onSelectRoleFilter: (UserRole?) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToCreateStaff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
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
                    text = "Personal interno",
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
                onClick = onNavigateToCreateStaff,
                modifier = Modifier.testTag(StaffListTestTags.NEW_STAFF_BUTTON),
            ) {
                Text(text = "Registrar personal")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = uiState.selectedRoleFilter == null,
                onClick = { onSelectRoleFilter(null) },
                enabled = !uiState.isLoading,
                label = { Text(text = "Todos") },
                modifier = Modifier.testTag(StaffListTestTags.FILTER_ALL),
            )
            FilterChip(
                selected = uiState.selectedRoleFilter == UserRole.PERSONAL_ATENCION,
                onClick = { onSelectRoleFilter(UserRole.PERSONAL_ATENCION) },
                enabled = !uiState.isLoading,
                label = { Text(text = "Personal de atención") },
                modifier = Modifier.testTag(StaffListTestTags.FILTER_AGENT),
            )
            FilterChip(
                selected = uiState.selectedRoleFilter == UserRole.COORDINADOR,
                onClick = { onSelectRoleFilter(UserRole.COORDINADOR) },
                enabled = !uiState.isLoading,
                label = { Text(text = "Coordinador") },
                modifier = Modifier.testTag(StaffListTestTags.FILTER_COORDINATOR),
            )
        }

        if (!uiState.errorMessage.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag(StaffListTestTags.ERROR_BANNER),
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
                    TextButton(onClick = onRetry) {
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
                        modifier = Modifier.fillMaxWidth().testTag(StaffListTestTags.EMPTY_STATE),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    ) {
                        Text(
                            text = "No hay integrantes de personal para el filtro seleccionado.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(StaffListTestTags.LIST_CONTAINER),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isWideLayout) {
                        item(key = "staff_table_header") {
                            StaffTableHeader()
                        }
                    }
                    items(
                        items = uiState.items,
                        key = { item -> item.id },
                    ) { item ->
                        if (isWideLayout) {
                            StaffTableRow(item = item)
                        } else {
                            StaffCard(item = item)
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
                modifier = Modifier.testTag(StaffListTestTags.PREV_PAGE_BUTTON),
            ) {
                Text(text = "Anterior")
            }

            Text(
                text = "Página ${uiState.page} de ${uiState.totalPages} (${uiState.totalItems} registros)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(StaffListTestTags.PAGINATION_LABEL),
            )

            OutlinedButton(
                onClick = onNextPage,
                enabled = !uiState.isLoading && uiState.page < uiState.totalPages,
                modifier = Modifier.testTag(StaffListTestTags.NEXT_PAGE_BUTTON),
            ) {
                Text(text = "Siguiente")
            }
        }
    }
}

@Composable
private fun StaffTableHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Nombre",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.4f),
            )
            Text(
                text = "Correo",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.6f),
            )
            Text(
                text = "Rol",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.1f),
            )
            Text(
                text = "Fecha de registro",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun StaffTableRow(item: StaffItemUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.fullName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.4f),
            )
            Text(
                text = item.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.6f),
            )
            Text(
                text = item.roleLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.1f),
            )
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
private fun StaffCard(item: StaffItemUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.fullName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.roleLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Registrado: ${item.formattedCreatedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
