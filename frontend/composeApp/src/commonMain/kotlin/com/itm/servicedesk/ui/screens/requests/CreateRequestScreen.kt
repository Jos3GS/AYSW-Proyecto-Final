package com.itm.servicedesk.ui.screens.requests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.data.mapper.RequestUiMapper
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.validation.RequestFormValidator
import com.itm.servicedesk.viewmodel.requests.CreateRequestUiState

object CreateRequestTestTags {
    const val CATEGORY_DROPDOWN = "create_request_category_dropdown"
    const val SUBJECT_INPUT = "create_request_subject_input"
    const val DESCRIPTION_INPUT = "create_request_description_input"
    const val PREVIOUS_CODE_INPUT = "create_request_previous_code_input"
    const val SUBMIT_BUTTON = "create_request_submit_button"
    const val ERROR_BANNER = "create_request_error_banner"

    fun categoryOption(category: RequestCategory): String = "create_request_category_${category.name}"
}

@Composable
fun CreateRequestScreen(
    uiState: CreateRequestUiState,
    onCategorySelected: (RequestCategory) -> Unit,
    onSubjectChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPreviousRequestCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiMapper = remember { RequestUiMapper() }
    var isCategoryMenuExpanded by remember { mutableStateOf(false) }

    val categoryError = uiState.fieldErrors[RequestFormValidator.FIELD_CATEGORY]
    val subjectError = uiState.fieldErrors[RequestFormValidator.FIELD_SUBJECT]
    val descriptionError = uiState.fieldErrors[RequestFormValidator.FIELD_DESCRIPTION]
    val previousCodeError = uiState.fieldErrors[RequestFormValidator.FIELD_PREVIOUS_REQUEST_CODE]

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Nueva solicitud de servicio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Diligencia la categoría, asunto, descripción y opcionalmente una solicitud previa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!uiState.generalError.isNullOrBlank()) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(CreateRequestTestTags.ERROR_BANNER)
                                .semantics(mergeDescendants = true) {},
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                    ) {
                        Text(
                            text = uiState.generalError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    val selectedCategoryLabel =
                        uiState.category?.let(uiMapper::categoryLabel) ?: "Selecciona una categoría"
                    OutlinedTextField(
                        value = selectedCategoryLabel,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(text = "Categoría") },
                        isError = categoryError != null,
                        supportingText = categoryError?.let { msg -> { Text(text = msg) } },
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                disabledTextColor =
                                    if (uiState.category != null) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                disabledBorderColor =
                                    if (categoryError != null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                disabledLabelColor =
                                    if (categoryError != null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                disabledSupportingTextColor = MaterialTheme.colorScheme.error,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(CreateRequestTestTags.CATEGORY_DROPDOWN)
                                .clickable(enabled = !uiState.isSubmitting) {
                                    isCategoryMenuExpanded = true
                                },
                    )
                    DropdownMenu(
                        expanded = isCategoryMenuExpanded,
                        onDismissRequest = { isCategoryMenuExpanded = false },
                    ) {
                        RequestCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(text = uiMapper.categoryLabel(category)) },
                                onClick = {
                                    isCategoryMenuExpanded = false
                                    onCategorySelected(category)
                                },
                                modifier = Modifier.testTag(CreateRequestTestTags.categoryOption(category)),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.subject,
                    onValueChange = onSubjectChange,
                    label = { Text(text = "Asunto") },
                    singleLine = true,
                    enabled = !uiState.isSubmitting,
                    isError = subjectError != null,
                    supportingText = {
                        val trimmedLen = uiState.subject.trim().length
                        val counter = "$trimmedLen / ${RequestFormValidator.MAX_SUBJECT_LENGTH} caracteres (mín. 5)"
                        Text(text = subjectError ?: counter)
                    },
                    modifier = Modifier.fillMaxWidth().testTag(CreateRequestTestTags.SUBJECT_INPUT),
                )

                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = onDescriptionChange,
                    label = { Text(text = "Descripción") },
                    minLines = 5,
                    maxLines = 10,
                    enabled = !uiState.isSubmitting,
                    isError = descriptionError != null,
                    supportingText = {
                        val trimmedLen = uiState.description.trim().length
                        val counter =
                            "$trimmedLen / ${RequestFormValidator.MAX_DESCRIPTION_LENGTH} caracteres (mín. 20)"
                        Text(text = descriptionError ?: counter)
                    },
                    modifier = Modifier.fillMaxWidth().testTag(CreateRequestTestTags.DESCRIPTION_INPUT),
                )

                OutlinedTextField(
                    value = uiState.previousRequestCode,
                    onValueChange = onPreviousRequestCodeChange,
                    label = { Text(text = "Código de solicitud anterior (opcional)") },
                    placeholder = { Text(text = "SOL-2026-00012") },
                    singleLine = true,
                    enabled = !uiState.isSubmitting,
                    isError = previousCodeError != null,
                    supportingText = {
                        Text(
                            text =
                                previousCodeError
                                    ?: "Solo para solicitudes finalizadas (Resuelta o Cerrada).",
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag(CreateRequestTestTags.PREVIOUS_CODE_INPUT),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onSubmit,
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier.fillMaxWidth().testTag(CreateRequestTestTags.SUBMIT_BUTTON),
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(text = "Enviar solicitud")
                    }
                }
            }
        }
    }
}
