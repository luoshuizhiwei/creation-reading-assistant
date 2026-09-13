package com.creationreadingassistant.ui.screen.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.components.SectionHeader
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 本周概览指标组（首页第二块）。
 *
 * 纸墨微岛质感升级：
 * - 4 列独立纸墨微岛（surfaceContainerLow + outlineVariant.copy(alpha = 0.35f)），
 *   大字 Bold 高光，配次级说明文本；
 * - 数值仍走共享 rememberCountUp（reducedMotion 友好）。
 */
@Composable
fun HomeMetricsSection(
    thisWeekNew: Int,
    readingCount: Int,
    completedCount: Int,
    todayReadingMs: Long,
    dailyGoalMinutes: Int = 0,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    Column(modifier) {
        SectionHeader(
            title = "本周概览",
            modifier = Modifier
                .animateEnter(120, reducedMotion)
                .testTag("metrics-title"),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateEnter(120, reducedMotion)
                .testTag("metrics-row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GridStat(
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric-week"),
                label = stringResource(R.string.home_this_week),
                value = rememberCountUp(thisWeekNew, "home.metrics.week", reducedMotion).toString(),
                subtext = "本周新读",
            )
            GridStat(
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric-reading"),
                label = stringResource(R.string.home_reading),
                value = rememberCountUp(readingCount, "home.metrics.reading", reducedMotion).toString(),
                subtext = "在读书籍",
            )
            GridStat(
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric-completed"),
                label = stringResource(R.string.home_finished),
                value = rememberCountUp(completedCount, "home.metrics.completed", reducedMotion).toString(),
                subtext = "翻越终章",
            )
            val minutesCountUp = rememberCountUp((todayReadingMs / 60000).toInt(), "home.metrics.today", reducedMotion)
            GridStat(
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric-today"),
                label = if (dailyGoalMinutes > 0) {
                    "今日 /${dailyGoalMinutes}分"
                } else {
                    stringResource(R.string.home_today)
                },
                value = formatCompactDuration(minutesCountUp.toLong() * 60000L),
                subtext = if (dailyGoalMinutes > 0) "目标进度" else "今日沉浸",
            )
        }
    }
}

@Composable
private fun GridStat(
    modifier: Modifier,
    label: String,
    value: String,
    subtext: String,
) {
    val spec = LocalComponentSpec.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(spec.hintRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            spec.hairlineBorderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
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
