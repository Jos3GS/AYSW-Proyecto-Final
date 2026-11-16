package com.itm.servicedesk.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.data.mapper.AttentionUiMapper

object OverdueBadgeTestTags {
    const val BADGE = "overdue_badge"
}

@Composable
fun OverdueBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier =
            modifier
                .testTag(OverdueBadgeTestTags.BADGE)
                .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = AttentionUiMapper.OVERDUE_BADGE_LABEL,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
