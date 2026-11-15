package com.itm.servicedesk.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

object TopBarTestTags {
    const val LOGOUT_BUTTON = "top_bar_logout_button"
    const val BACK_BUTTON = "top_bar_back_button"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedTopBar(
    title: String,
    userFullName: String,
    roleLabel: String,
    onLogoutClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        navigationIcon = {
            if (onBackClick != null) {
                TextButton(
                    onClick = onBackClick,
                    modifier =
                        Modifier
                            .testTag(TopBarTestTags.BACK_BUTTON)
                            .semantics { contentDescription = "Volver a la pantalla anterior" },
                ) {
                    Text(
                        text = "Volver",
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$userFullName · $roleLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            TextButton(
                onClick = onLogoutClick,
                modifier =
                    Modifier
                        .testTag(TopBarTestTags.LOGOUT_BUTTON)
                        .semantics { contentDescription = "Cerrar sesión" },
            ) {
                Text(
                    text = "Cerrar sesión",
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    )
}
