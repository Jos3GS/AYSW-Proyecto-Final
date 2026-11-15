package com.itm.servicedesk.ui.screens.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.validation.UserFormValidator
import com.itm.servicedesk.ui.components.ConfirmationDialog
import com.itm.servicedesk.ui.components.PasswordTextField
import com.itm.servicedesk.viewmodel.CreateStaffUiState

object CreateStaffTestTags {
    const val FULL_NAME_INPUT = "create_staff_fullname_input"
    const val DOCUMENT_INPUT = "create_staff_document_input"
    const val EMAIL_INPUT = "create_staff_email_input"
    const val PHONE_INPUT = "create_staff_phone_input"
    const val PASSWORD_INPUT = "create_staff_password_input"
    const val ROLE_SELECTOR = "create_staff_role_selector"
    const val ROLE_AGENT_OPTION = "create_staff_role_agent"
    const val ROLE_COORDINATOR_OPTION = "create_staff_role_coordinator"
    const val SUBMIT_BUTTON = "create_staff_submit_button"
    const val ERROR_BANNER = "create_staff_error_banner"
}

@Composable
fun CreateStaffScreen(
    uiState: CreateStaffUiState,
    confirmationRoleLabel: String,
    onFullNameChange: (String) -> Unit,
    onDocumentNumberChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onRoleSelected: (UserRole) -> Unit,
    onRequestSubmit: () -> Unit,
    onConfirmSubmit: () -> Unit,
    onDismissConfirmDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fullNameError = uiState.fieldErrors[UserFormValidator.FIELD_FULL_NAME]
    val documentError = uiState.fieldErrors[UserFormValidator.FIELD_DOCUMENT_NUMBER]
    val emailError = uiState.fieldErrors[UserFormValidator.FIELD_EMAIL]
    val phoneError = uiState.fieldErrors[UserFormValidator.FIELD_PHONE]
    val passwordError = uiState.fieldErrors[UserFormValidator.FIELD_PASSWORD]
    val roleError = uiState.fieldErrors[UserFormValidator.FIELD_ROLE]

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Registrar personal interno",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Asigna un rol de atención o coordinación.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!uiState.generalError.isNullOrBlank()) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(CreateStaffTestTags.ERROR_BANNER)
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

                OutlinedTextField(
                    value = uiState.fullName,
                    onValueChange = onFullNameChange,
                    label = { Text(text = "Nombre completo") },
                    singleLine = true,
                    enabled = !uiState.isSubmitting,
                    isError = fullNameError != null,
                    supportingText = fullNameError?.let { msg -> { Text(text = msg) } },
                    modifier = Modifier.fillMaxWidth().testTag(CreateStaffTestTags.FULL_NAME_INPUT),
                )

                OutlinedTextField(
                    value = uiState.documentNumber,
                    onValueChange = onDocumentNumberChange,
                    label = { Text(text = "Número de documento") },
                    singleLine = true,
                    enabled = !uiState.isSubmitting,
                    isError = documentError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = documentError?.let { msg -> { Text(text = msg) } },
                    modifier = Modifier.fillMaxWidth().testTag(CreateStaffTestTags.DOCUMENT_INPUT),
                )

                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = onEmailChange,
                    label = { Text(text = "Correo electrónico") },
                    singleLine = true,
                    enabled = !uiState.isSubmitting,
                    isError = emailError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    supportingText = emailError?.let { msg -> { Text(text = msg) } },
                    modifier = Modifier.fillMaxWidth().testTag(CreateStaffTestTags.EMAIL_INPUT),
                )

                OutlinedTextField(
                    value = uiState.phone,
                    onValueChange = onPhoneChange,
                    label = { Text(text = "Teléfono") },
                    singleLine = true,
                    enabled = !uiState.isSubmitting,
                    isError = phoneError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = phoneError?.let { msg -> { Text(text = msg) } },
                    modifier = Modifier.fillMaxWidth().testTag(CreateStaffTestTags.PHONE_INPUT),
                )

                PasswordTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    isPasswordVisible = uiState.isPasswordVisible,
                    onTogglePasswordVisibility = onTogglePasswordVisibility,
                    errorMessage = passwordError,
                    enabled = !uiState.isSubmitting,
                    inputTestTag = CreateStaffTestTags.PASSWORD_INPUT,
                )

                Column(
                    modifier = Modifier.fillMaxWidth().testTag(CreateStaffTestTags.ROLE_SELECTOR),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Rol del personal",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = uiState.selectedRole == UserRole.PERSONAL_ATENCION,
                            onClick = { onRoleSelected(UserRole.PERSONAL_ATENCION) },
                            enabled = !uiState.isSubmitting,
                            label = { Text(text = "Personal de atención") },
                            modifier = Modifier.testTag(CreateStaffTestTags.ROLE_AGENT_OPTION),
                        )
                        FilterChip(
                            selected = uiState.selectedRole == UserRole.COORDINADOR,
                            onClick = { onRoleSelected(UserRole.COORDINADOR) },
                            enabled = !uiState.isSubmitting,
                            label = { Text(text = "Coordinador") },
                            modifier = Modifier.testTag(CreateStaffTestTags.ROLE_COORDINATOR_OPTION),
                        )
                    }
                    if (roleError != null) {
                        Text(
                            text = roleError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onRequestSubmit,
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier.fillMaxWidth().testTag(CreateStaffTestTags.SUBMIT_BUTTON),
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(text = "Registrar personal")
                    }
                }
            }
        }
    }

    if (uiState.isConfirmDialogOpen) {
        ConfirmationDialog(
            title = "Confirmar registro de personal",
            message = "¿Confirmas el registro de ${uiState.fullName.trim()} con rol $confirmationRoleLabel?",
            confirmText = "Confirmar",
            isLoading = uiState.isSubmitting,
            onConfirm = onConfirmSubmit,
            onDismiss = onDismissConfirmDialog,
        )
    }
}
