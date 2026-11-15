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
import com.itm.servicedesk.viewmodel.LoginUiState

object LoginTestTags {
    const val EMAIL_INPUT = "login_email_input"
    const val PASSWORD_INPUT = "login_password_input"
    const val SUBMIT_BUTTON = "login_submit_button"
    const val GO_REGISTER_BUTTON = "login_go_register_button"
    const val ERROR_BANNER = "login_error_banner"
    const val NOTICE_BANNER = "login_notice_banner"
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    noticeMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emailError = uiState.fieldErrors[UserFormValidator.FIELD_EMAIL]
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "ITM Service Desk",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Iniciar sesión",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!noticeMessage.isNullOrBlank()) {
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(LoginTestTags.NOTICE_BANNER)
                                    .semantics(mergeDescendants = true) {},
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                        ) {
                            Text(
                                text = noticeMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    if (!uiState.generalError.isNullOrBlank()) {
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(LoginTestTags.ERROR_BANNER)
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
                        value = uiState.email,
                        onValueChange = onEmailChange,
                        label = { Text(text = "Correo electrónico") },
                        singleLine = true,
                        enabled = !uiState.isSubmitting,
                        isError = emailError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        supportingText = emailError?.let { msg -> { Text(text = msg) } },
                        modifier = Modifier.fillMaxWidth().testTag(LoginTestTags.EMAIL_INPUT),
                    )

                    PasswordTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChange,
                        isPasswordVisible = uiState.isPasswordVisible,
                        onTogglePasswordVisibility = onTogglePasswordVisibility,
                        errorMessage = passwordError,
                        enabled = !uiState.isSubmitting,
                        inputTestTag = LoginTestTags.PASSWORD_INPUT,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onSubmit,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().testTag(LoginTestTags.SUBMIT_BUTTON),
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(text = "Ingresar")
                        }
                    }

                    TextButton(
                        onClick = onNavigateToRegister,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().testTag(LoginTestTags.GO_REGISTER_BUTTON),
                    ) {
                        Text(text = "¿No tienes cuenta? Regístrate")
                    }
                }
            }
        }
    }
}
