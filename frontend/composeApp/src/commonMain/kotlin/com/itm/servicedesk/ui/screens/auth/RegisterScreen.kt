package com.itm.servicedesk.ui.screens.auth

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.domain.validation.UserFormValidator
import com.itm.servicedesk.ui.components.PasswordTextField
import com.itm.servicedesk.viewmodel.RegisterUiState

object RegisterTestTags {
    const val FULL_NAME_INPUT = "register_fullname_input"
    const val DOCUMENT_INPUT = "register_document_input"
    const val EMAIL_INPUT = "register_email_input"
    const val PHONE_INPUT = "register_phone_input"
    const val PASSWORD_INPUT = "register_password_input"
    const val SUBMIT_BUTTON = "register_submit_button"
    const val GO_LOGIN_BUTTON = "register_go_login_button"
    const val ERROR_BANNER = "register_error_banner"
}

@Composable
fun RegisterScreen(
    uiState: RegisterUiState,
    onFullNameChange: (String) -> Unit,
    onDocumentNumberChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fullNameError = uiState.fieldErrors[UserFormValidator.FIELD_FULL_NAME]
    val documentError = uiState.fieldErrors[UserFormValidator.FIELD_DOCUMENT_NUMBER]
    val emailError = uiState.fieldErrors[UserFormValidator.FIELD_EMAIL]
    val phoneError = uiState.fieldErrors[UserFormValidator.FIELD_PHONE]
    val passwordError = uiState.fieldErrors[UserFormValidator.FIELD_PASSWORD]

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                        text = "Crear cuenta",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Registro de solicitante",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!uiState.generalError.isNullOrBlank()) {
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(RegisterTestTags.ERROR_BANNER)
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
                        modifier = Modifier.fillMaxWidth().testTag(RegisterTestTags.FULL_NAME_INPUT),
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
                        modifier = Modifier.fillMaxWidth().testTag(RegisterTestTags.DOCUMENT_INPUT),
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
                        modifier = Modifier.fillMaxWidth().testTag(RegisterTestTags.EMAIL_INPUT),
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
                        modifier = Modifier.fillMaxWidth().testTag(RegisterTestTags.PHONE_INPUT),
                    )

                    PasswordTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChange,
                        isPasswordVisible = uiState.isPasswordVisible,
                        onTogglePasswordVisibility = onTogglePasswordVisibility,
                        errorMessage = passwordError,
                        enabled = !uiState.isSubmitting,
                        inputTestTag = RegisterTestTags.PASSWORD_INPUT,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onSubmit,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().testTag(RegisterTestTags.SUBMIT_BUTTON),
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(text = "Registrarse")
                        }
                    }

                    TextButton(
                        onClick = onNavigateToLogin,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().testTag(RegisterTestTags.GO_LOGIN_BUTTON),
                    ) {
                        Text(text = "¿Ya tienes cuenta? Inicia sesión")
                    }
                }
            }
        }
    }
}
