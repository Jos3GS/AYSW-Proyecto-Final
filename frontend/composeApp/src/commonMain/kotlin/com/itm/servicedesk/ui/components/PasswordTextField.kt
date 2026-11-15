package com.itm.servicedesk.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    label: String = "Contraseña",
    errorMessage: String? = null,
    enabled: Boolean = true,
    inputTestTag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        enabled = enabled,
        isError = errorMessage != null,
        visualTransformation =
            if (isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(
                onClick = onTogglePasswordVisibility,
                enabled = enabled,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            if (isPasswordVisible) {
                                "Ocultar contraseña"
                            } else {
                                "Mostrar contraseña"
                            }
                    },
            ) {
                Text(text = if (isPasswordVisible) "Ocultar" else "Mostrar")
            }
        },
        supportingText =
            errorMessage?.let { message ->
                { Text(text = message) }
            },
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(inputTestTag),
    )
}
