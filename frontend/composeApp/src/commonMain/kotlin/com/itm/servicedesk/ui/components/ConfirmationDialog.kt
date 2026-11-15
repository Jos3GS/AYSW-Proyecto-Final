package com.itm.servicedesk.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

object ConfirmDialogTestTags {
    const val DIALOG = "confirm_dialog"
    const val ACCEPT_BUTTON = "confirm_dialog_accept_button"
    const val CANCEL_BUTTON = "confirm_dialog_cancel_button"
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    cancelText: String = "Cancelar",
    isLoading: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isLoading) {
                onDismiss()
            }
        },
        modifier =
            modifier
                .testTag(ConfirmDialogTestTags.DIALOG)
                .semantics { contentDescription = title },
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
                modifier = Modifier.testTag(ConfirmDialogTestTags.ACCEPT_BUTTON),
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading,
                modifier = Modifier.testTag(ConfirmDialogTestTags.CANCEL_BUTTON),
            ) {
                Text(text = cancelText)
            }
        },
    )
}
