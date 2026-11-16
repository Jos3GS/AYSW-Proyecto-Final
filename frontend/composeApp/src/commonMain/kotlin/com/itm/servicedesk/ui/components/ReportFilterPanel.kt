package com.itm.servicedesk.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.itm.servicedesk.data.mapper.ReportUiMapper
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.StaffMember
import com.itm.servicedesk.viewmodel.reports.ReportFilterDraft

object ReportFilterPanelTestTags {
    const val PANEL = "report_filter_panel"
    const val FROM_DATE_INPUT = "report_filter_from_date"
    const val TO_DATE_INPUT = "report_filter_to_date"
    const val STATUS_DROPDOWN = "report_filter_status_dropdown"
    const val CATEGORY_DROPDOWN = "report_filter_category_dropdown"
    const val PRIORITY_DROPDOWN = "report_filter_priority_dropdown"
    const val ASSIGNEE_DROPDOWN = "report_filter_assignee_dropdown"
    const val APPLY_BUTTON = "report_filter_apply_button"
    const val CLEAR_BUTTON = "report_filter_clear_button"

    fun statusOption(status: RequestStatus?): String = "report_filter_status_${status?.name ?: "ALL"}"

    fun categoryOption(category: RequestCategory?): String = "report_filter_category_${category?.name ?: "ALL"}"

    fun priorityOption(priority: RequestPriority?): String = "report_filter_priority_${priority?.name ?: "ALL"}"

    fun assigneeOption(assigneeId: String?): String = "report_filter_assignee_${assigneeId ?: "ALL"}"
}

@Composable
fun ReportFilterPanel(
    draftFilter: ReportFilterDraft,
    availableAgents: List<StaffMember>,
    isLoading: Boolean,
    isLoadingAgents: Boolean,
    onFromDateChanged: (String) -> Unit,
    onToDateChanged: (String) -> Unit,
    onStatusFilterChanged: (RequestStatus?) -> Unit,
    onCategoryFilterChanged: (RequestCategory?) -> Unit,
    onPriorityFilterChanged: (RequestPriority?) -> Unit,
    onAssigneeFilterChanged: (String?) -> Unit,
    onApplyFilters: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiMapper = remember { ReportUiMapper() }

    Card(
        modifier = modifier.fillMaxWidth().testTag(ReportFilterPanelTestTags.PANEL),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Filtros dinámicos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 600.dp

                if (isWide) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DateFilterTextField(
                                value = draftFilter.fromDateInput,
                                label = "Desde (dd/mm/aaaa)",
                                errorText = draftFilter.fromDateError,
                                enabled = !isLoading,
                                onValueChange = onFromDateChanged,
                                testTag = ReportFilterPanelTestTags.FROM_DATE_INPUT,
                                modifier = Modifier.weight(1f),
                            )
                            DateFilterTextField(
                                value = draftFilter.toDateInput,
                                label = "Hasta (dd/mm/aaaa)",
                                errorText = draftFilter.toDateError,
                                enabled = !isLoading,
                                onValueChange = onToDateChanged,
                                testTag = ReportFilterPanelTestTags.TO_DATE_INPUT,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatusFilterDropdown(
                                selectedStatus = draftFilter.selectedStatus,
                                uiMapper = uiMapper,
                                enabled = !isLoading,
                                onSelected = onStatusFilterChanged,
                                modifier = Modifier.weight(1f),
                            )
                            CategoryFilterDropdown(
                                selectedCategory = draftFilter.selectedCategory,
                                uiMapper = uiMapper,
                                enabled = !isLoading,
                                onSelected = onCategoryFilterChanged,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PriorityFilterDropdown(
                                selectedPriority = draftFilter.selectedPriority,
                                uiMapper = uiMapper,
                                enabled = !isLoading,
                                onSelected = onPriorityFilterChanged,
                                modifier = Modifier.weight(1f),
                            )
                            AssigneeFilterDropdown(
                                selectedAssigneeId = draftFilter.selectedAssigneeId,
                                availableAgents = availableAgents,
                                isLoadingAgents = isLoadingAgents,
                                enabled = !isLoading,
                                onSelected = onAssigneeFilterChanged,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DateFilterTextField(
                            value = draftFilter.fromDateInput,
                            label = "Desde (dd/mm/aaaa)",
                            errorText = draftFilter.fromDateError,
                            enabled = !isLoading,
                            onValueChange = onFromDateChanged,
                            testTag = ReportFilterPanelTestTags.FROM_DATE_INPUT,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DateFilterTextField(
                            value = draftFilter.toDateInput,
                            label = "Hasta (dd/mm/aaaa)",
                            errorText = draftFilter.toDateError,
                            enabled = !isLoading,
                            onValueChange = onToDateChanged,
                            testTag = ReportFilterPanelTestTags.TO_DATE_INPUT,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        StatusFilterDropdown(
                            selectedStatus = draftFilter.selectedStatus,
                            uiMapper = uiMapper,
                            enabled = !isLoading,
                            onSelected = onStatusFilterChanged,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CategoryFilterDropdown(
                            selectedCategory = draftFilter.selectedCategory,
                            uiMapper = uiMapper,
                            enabled = !isLoading,
                            onSelected = onCategoryFilterChanged,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PriorityFilterDropdown(
                            selectedPriority = draftFilter.selectedPriority,
                            uiMapper = uiMapper,
                            enabled = !isLoading,
                            onSelected = onPriorityFilterChanged,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AssigneeFilterDropdown(
                            selectedAssigneeId = draftFilter.selectedAssigneeId,
                            availableAgents = availableAgents,
                            isLoadingAgents = isLoadingAgents,
                            enabled = !isLoading,
                            onSelected = onAssigneeFilterChanged,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onClearFilters,
                    enabled = !isLoading,
                    modifier = Modifier.testTag(ReportFilterPanelTestTags.CLEAR_BUTTON),
                ) {
                    Text(text = "Limpiar filtros")
                }
                Button(
                    onClick = onApplyFilters,
                    enabled = !isLoading,
                    modifier = Modifier.testTag(ReportFilterPanelTestTags.APPLY_BUTTON),
                ) {
                    Text(text = "Aplicar filtros")
                }
            }
        }
    }
}

@Composable
private fun DateFilterTextField(
    value: String,
    label: String,
    errorText: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        placeholder = { Text(text = "dd/mm/aaaa") },
        singleLine = true,
        enabled = enabled,
        isError = errorText != null,
        supportingText = errorText?.let { msg -> { Text(text = msg) } },
        modifier = modifier.testTag(testTag),
    )
}

@Composable
private fun StatusFilterDropdown(
    selectedStatus: RequestStatus?,
    uiMapper: ReportUiMapper,
    enabled: Boolean,
    onSelected: (RequestStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = selectedStatus?.let(uiMapper::statusLabel) ?: "Todos"

    FilterDropdownField(
        label = "Estado",
        displayText = displayText,
        hasSelection = selectedStatus != null,
        enabled = enabled,
        expanded = expanded,
        onToggleExpand = { expanded = true },
        onDismiss = { expanded = false },
        dropdownTestTag = ReportFilterPanelTestTags.STATUS_DROPDOWN,
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = { Text(text = "Todos") },
            onClick = {
                expanded = false
                onSelected(null)
            },
            modifier = Modifier.testTag(ReportFilterPanelTestTags.statusOption(null)),
        )
        RequestStatus.entries.forEach { status ->
            DropdownMenuItem(
                text = { Text(text = uiMapper.statusLabel(status)) },
                onClick = {
                    expanded = false
                    onSelected(status)
                },
                modifier = Modifier.testTag(ReportFilterPanelTestTags.statusOption(status)),
            )
        }
    }
}

@Composable
private fun CategoryFilterDropdown(
    selectedCategory: RequestCategory?,
    uiMapper: ReportUiMapper,
    enabled: Boolean,
    onSelected: (RequestCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = selectedCategory?.let(uiMapper::categoryLabel) ?: "Todas"

    FilterDropdownField(
        label = "Categoría",
        displayText = displayText,
        hasSelection = selectedCategory != null,
        enabled = enabled,
        expanded = expanded,
        onToggleExpand = { expanded = true },
        onDismiss = { expanded = false },
        dropdownTestTag = ReportFilterPanelTestTags.CATEGORY_DROPDOWN,
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = { Text(text = "Todas") },
            onClick = {
                expanded = false
                onSelected(null)
            },
            modifier = Modifier.testTag(ReportFilterPanelTestTags.categoryOption(null)),
        )
        RequestCategory.entries.forEach { category ->
            DropdownMenuItem(
                text = { Text(text = uiMapper.categoryLabel(category)) },
                onClick = {
                    expanded = false
                    onSelected(category)
                },
                modifier = Modifier.testTag(ReportFilterPanelTestTags.categoryOption(category)),
            )
        }
    }
}

@Composable
private fun PriorityFilterDropdown(
    selectedPriority: RequestPriority?,
    uiMapper: ReportUiMapper,
    enabled: Boolean,
    onSelected: (RequestPriority?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = selectedPriority?.let(uiMapper::priorityLabel) ?: "Todas"

    FilterDropdownField(
        label = "Prioridad",
        displayText = displayText,
        hasSelection = selectedPriority != null,
        enabled = enabled,
        expanded = expanded,
        onToggleExpand = { expanded = true },
        onDismiss = { expanded = false },
        dropdownTestTag = ReportFilterPanelTestTags.PRIORITY_DROPDOWN,
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = { Text(text = "Todas") },
            onClick = {
                expanded = false
                onSelected(null)
            },
            modifier = Modifier.testTag(ReportFilterPanelTestTags.priorityOption(null)),
        )
        RequestPriority.entries.forEach { priority ->
            DropdownMenuItem(
                text = { Text(text = uiMapper.priorityLabel(priority)) },
                onClick = {
                    expanded = false
                    onSelected(priority)
                },
                modifier = Modifier.testTag(ReportFilterPanelTestTags.priorityOption(priority)),
            )
        }
    }
}

@Composable
private fun AssigneeFilterDropdown(
    selectedAssigneeId: String?,
    availableAgents: List<StaffMember>,
    isLoadingAgents: Boolean,
    enabled: Boolean,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAgent = availableAgents.firstOrNull { it.id == selectedAssigneeId }
    val displayText =
        when {
            selectedAgent != null -> selectedAgent.fullName
            isLoadingAgents -> "Cargando responsables..."
            else -> "Todos"
        }

    FilterDropdownField(
        label = "Responsable",
        displayText = displayText,
        hasSelection = selectedAgent != null,
        enabled = enabled && !isLoadingAgents,
        expanded = expanded,
        onToggleExpand = { expanded = true },
        onDismiss = { expanded = false },
        dropdownTestTag = ReportFilterPanelTestTags.ASSIGNEE_DROPDOWN,
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = { Text(text = "Todos") },
            onClick = {
                expanded = false
                onSelected(null)
            },
            modifier = Modifier.testTag(ReportFilterPanelTestTags.assigneeOption(null)),
        )
        availableAgents.forEach { agent ->
            DropdownMenuItem(
                text = { Text(text = agent.fullName) },
                onClick = {
                    expanded = false
                    onSelected(agent.id)
                },
                modifier = Modifier.testTag(ReportFilterPanelTestTags.assigneeOption(agent.id)),
            )
        }
    }
}

@Composable
private fun FilterDropdownField(
    label: String,
    displayText: String,
    hasSelection: Boolean,
    enabled: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    dropdownTestTag: String,
    modifier: Modifier = Modifier,
    menuContent: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(text = label) },
            colors =
                OutlinedTextFieldDefaults.colors(
                    disabledTextColor =
                        if (hasSelection) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(dropdownTestTag)
                    .clickable(enabled = enabled, onClick = onToggleExpand),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
        ) {
            menuContent()
        }
    }
}
