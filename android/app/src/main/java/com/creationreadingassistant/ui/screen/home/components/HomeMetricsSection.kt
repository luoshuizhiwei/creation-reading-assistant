package com.creationreadingassistant.ui.screen.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 本周概览指标组（首页第二块）。
 *
 * 只负责自己的布局：标题 + 四列一行的指标。**不**嵌套外层 Card/Scaffold；
 * 指标数值使用共享的 rememberCountUp（reducedMotion 友好）。
 */
@Composable
fun HomeMetricsSection(
    thisWeekNew: Int,
    readingCount: Int,
    completedCount: Int,
    todayReadingMs: Long,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    Column(modifier) {
        Text(
            "本周概览",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .animateEnter(120, reducedMotion)
                .testTag("metrics-title"),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateEnter(120, reducedMotion)
                .testTag("metrics-row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GridStat(
                Modifier
                    .weight(1f)
                    .testTag("metric-week"),
                label = stringResource(R.string.home_this_week),
                value = rememberCountUp(thisWeekNew, reducedMotion).toString(),
            )
            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            GridStat(
                Modifier
                    .weight(1f)
                    .testTag("metric-reading"),
                label = stringResource(R.string.home_reading),
                value = rememberCountUp(readingCount, reducedMotion).toString(),
            )
            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            GridStat(
                Modifier
                    .weight(1f)
                    .testTag("metric-completed"),
                label = stringResource(R.string.home_finished),
                value = rememberCountUp(completedCount, reducedMotion).toString(),
            )
            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            val minutesCountUp = rememberCountUp((todayReadingMs / 60000).toInt(), reducedMotion)
            GridStat(
                Modifier
                    .weight(1f)
                    .testTag("metric-today"),
                label = stringResource(R.string.home_today),
                value = formatCompactDuration(minutesCountUp.toLong() * 60000L),
            )
        }
    }
}

@Composable
private fun GridStat(modifier: Modifier, label: String, value: String) {
    Column(
        modifier = modifier.padding(10.dp, 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCompactDuration(ms: Long): String {
    if (ms <= 0) return "0 分钟"
    if (ms < 60_000) return "${kotlin.math.round(ms / 1000.0).toInt()} 秒"
    val totalMinutes = kotlin.math.round(ms / 60_000.0).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "$m 分钟"
}
