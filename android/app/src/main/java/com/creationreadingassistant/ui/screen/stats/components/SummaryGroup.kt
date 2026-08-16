package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.formatCompactDuration
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberReducedMotion

private data class SummaryMetric(
    val icon: ImageVector,
    val value: Int,
    val format: (Int) -> String,
    val label: String,
)

@Composable
internal fun SummaryGroup(
    stats: StatsUi,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val metrics = listOf(
        SummaryMetric(
            Icons.Outlined.AccessTime,
            (stats.totalReadingMs / 60000).toInt(),
            { formatCompactDuration(it.toLong() * 60000) },
            "阅读时长",
        ),
        SummaryMetric(Icons.Outlined.CalendarMonth, stats.readingDays, { "$it 天" }, "阅读天数"),
        SummaryMetric(Icons.Outlined.Book, stats.readBooks, { "$it 本" }, "读过书籍"),
        SummaryMetric(Icons.Outlined.CheckCircle, stats.completed, { "$it 本" }, "已读完"),
        SummaryMetric(Icons.Outlined.BarChart, stats.streakCurrent, { "$it 天" }, "当前连续"),
        SummaryMetric(Icons.Outlined.CalendarMonth, stats.streakLongest, { "$it 天" }, "最长连续"),
    )
    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-summary"),
        contentPadding = 0.dp,
    ) {
        metrics.chunked(2).forEachIndexed { rowIndex, rowItems ->
            if (rowIndex > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEachIndexed { columnIndex, item ->
                    if (columnIndex > 0) {
                        VerticalDivider(
                            modifier = Modifier.height(52.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    SummaryMetricCell(
                        item = item,
                        modifier = Modifier.weight(1f),
                        reducedMotion = reducedMotion,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCell(
    item: SummaryMetric,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val layout = LocalLayoutTokens.current
    Row(
        modifier = modifier
            .heightIn(min = 76.dp)
            .padding(layout.compactCardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(layout.relatedGap),
    ) {
        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        androidx.compose.foundation.layout.Column {
            val display = rememberCountUp(item.value, reducedMotion)
            // 统计数字用展示衬线，做成可被记住的「纸墨签名」数字。
            Text(item.format(display), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFontFamily))
            Text(
                item.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
