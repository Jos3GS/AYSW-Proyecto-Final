package com.itm.servicedesk.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.domain.validation.RequestFormValidator

object CancelDialogTestTags {
    const val DIALOG = "cancel_request_dialog"
    const val REASON_INPUT = "cancel_dialog_reason_input"
    const val CONFIRM_BUTTON = "cancel_dialog_confirm_button"
    const val DISMISS_BUTTON = "cancel_dialog_dismiss_button"
}

@Composable
fun CancelRequestDialog(
    requestCode: String,
    reason: String,
    reasonError: String?,
    isCancelling: Boolean,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isCancelling) {
                onDismiss()
            }
        },
        modifier =
            modifier
                .testTag(CancelDialogTestTags.DIALOG)
                .semantics { contentDescription = "Cancelar solicitud $requestCode" },
        title = { Text(text = "Cancelar solicitud") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text =
                        "¿Estás seguro de cancelar la solicitud $requestCode? " +
                            "Esta acción no se puede deshacer.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text(text = "Motivo de cancelación (opcional)") },
                    enabled = !isCancelling,
                    isError = reasonError != null,
                    minLines = 2,
                    maxLines = 4,
                    supportingText = {
                        val trimmedLen = reason.trim().length
                        val counterText = "$trimmedLen / ${RequestFormValidator.MAX_CANCEL_REASON_LENGTH}"
                        Text(text = reasonError ?: counterText)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(CancelDialogTestTags.REASON_INPUT),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isCancelling,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                modifier = Modifier.testTag(CancelDialogTestTags.CONFIRM_BUTTON),
            ) {
                if (isCancelling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Text(text = "Confirmar cancelación")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCancelling,
                modifier = Modifier.testTag(CancelDialogTestTags.DISMISS_BUTTON),
            ) {
                Text(text = "Volver")
            }
        },
    )
}
