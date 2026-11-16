package com.itm.servicedesk.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itm.servicedesk.viewmodel.reports.AssigneeBreakdownRowUi
import com.itm.servicedesk.viewmodel.reports.BreakdownRowUi
import com.itm.servicedesk.viewmodel.reports.CoordinationReportSummaryUi

object ReportSummarySectionTestTags {
    const val SECTION = "report_summary_section"
    const val GENERATED_AT = "report_summary_generated_at"
    const val KPI_TOTAL = "report_kpi_total"
    const val KPI_OVERDUE = "report_kpi_overdue"
    const val KPI_WITHIN_SLA = "report_kpi_within_sla"
    const val KPI_OUTSIDE_SLA = "report_kpi_outside_sla"
    const val STATUS_CARD = "report_breakdown_status_card"
    const val CATEGORY_CARD = "report_breakdown_category_card"
    const val PRIORITY_CARD = "report_breakdown_priority_card"
    const val ASSIGNEE_CARD = "report_breakdown_assignee_card"
}

@Composable
fun ReportSummarySection(
    summary: CoordinationReportSummaryUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(ReportSummarySectionTestTags.SECTION),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Resumen consolidado",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Generado: ${summary.generatedAtFormatted}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ReportSummarySectionTestTags.GENERATED_AT),
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWide = maxWidth >= 640.dp
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KpiCard(
                        title = "Total de solicitudes",
                        value = summary.totalRequests.toString(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        testTag = ReportSummarySectionTestTags.KPI_TOTAL,
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        title = "Vencidas activas",
                        value = summary.overdueCount.toString(),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        testTag = ReportSummarySectionTestTags.KPI_OVERDUE,
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        title = "Resueltas dentro del plazo",
                        value = summary.resolvedWithinSlaCount.toString(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        testTag = ReportSummarySectionTestTags.KPI_WITHIN_SLA,
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        title = "Resueltas fuera del plazo",
                        value = summary.resolvedOutsideSlaCount.toString(),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        testTag = ReportSummarySectionTestTags.KPI_OUTSIDE_SLA,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KpiCard(
                            title = "Total de solicitudes",
                            value = summary.totalRequests.toString(),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            testTag = ReportSummarySectionTestTags.KPI_TOTAL,
                            modifier = Modifier.weight(1f),
                        )
                        KpiCard(
                            title = "Vencidas activas",
                            value = summary.overdueCount.toString(),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            testTag = ReportSummarySectionTestTags.KPI_OVERDUE,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KpiCard(
                            title = "Resueltas dentro del plazo",
                            value = summary.resolvedWithinSlaCount.toString(),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            testTag = ReportSummarySectionTestTags.KPI_WITHIN_SLA,
                            modifier = Modifier.weight(1f),
                        )
                        KpiCard(
                            title = "Resueltas fuera del plazo",
                            value = summary.resolvedOutsideSlaCount.toString(),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            testTag = ReportSummarySectionTestTags.KPI_OUTSIDE_SLA,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isTwoColumn = maxWidth >= 720.dp
            if (isTwoColumn) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BreakdownCard(
                            title = "Por estado",
                            rows = summary.statusRows,
                            testTag = ReportSummarySectionTestTags.STATUS_CARD,
                            modifier = Modifier.weight(1f),
                        )
                        BreakdownCard(
                            title = "Por categoría",
                            rows = summary.categoryRows,
                            testTag = ReportSummarySectionTestTags.CATEGORY_CARD,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BreakdownCard(
                            title = "Por prioridad",
                            rows = summary.priorityRows,
                            testTag = ReportSummarySectionTestTags.PRIORITY_CARD,
                            modifier = Modifier.weight(1f),
                        )
                        AssigneeBreakdownCard(
                            rows = summary.assigneeRows,
                            testTag = ReportSummarySectionTestTags.ASSIGNEE_CARD,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BreakdownCard(
                        title = "Por estado",
                        rows = summary.statusRows,
                        testTag = ReportSummarySectionTestTags.STATUS_CARD,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BreakdownCard(
                        title = "Por categoría",
                        rows = summary.categoryRows,
                        testTag = ReportSummarySectionTestTags.CATEGORY_CARD,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BreakdownCard(
                        title = "Por prioridad",
                        rows = summary.priorityRows,
                        testTag = ReportSummarySectionTestTags.PRIORITY_CARD,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AssigneeBreakdownCard(
                        rows = summary.assigneeRows,
                        testTag = ReportSummarySectionTestTags.ASSIGNEE_CARD,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .testTag(testTag)
                .semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun BreakdownCard(
    title: String,
    rows: List<BreakdownRowUi>,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            rows.forEach { row ->
                ProportionBarRow(
                    label = row.label,
                    valueText = row.count.toString(),
                    ratio = row.ratio,
                )
            }
        }
    }
}

@Composable
private fun AssigneeBreakdownCard(
    rows: List<AssigneeBreakdownRowUi>,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Carga por responsable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            rows.forEach { row ->
                val countLabel =
                    if (row.overdueAssigned > 0L) {
                        "${row.totalAssigned} (${row.overdueAssigned} vencidas)"
                    } else {
                        row.totalAssigned.toString()
                    }
                ProportionBarRow(
                    label = row.assigneeName,
                    valueText = countLabel,
                    ratio = row.ratio,
                )
            }
        }
    }
}

@Composable
private fun ProportionBarRow(
    label: String,
    valueText: String,
    ratio: Float,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
