package com.creationreadingassistant.ui.screen.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionHeader
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 本周概览指标组（首页第二块）。
 *
 * 视觉规格（2026-08-21 审美体检后修订）：
 * - 四个指标统一收进一张 [SectionCard]，与「累计阅读」「继续阅读」卡片同语言，
 *   不再裸摆在页面背景上（碎片感的来源）。
 * - 数字放大到 22sp Bold（统计大数用 Sans，不用展示衬线），label 降饱和；
 *   列间不设竖 divider，靠等宽留白分隔——更干净，微信读书式统计条。
 * - 数值仍走共享 rememberCountUp（reducedMotion 友好）。
 *
 * 只负责自己的布局：标题 + 指标卡。**不**嵌套外层 Scaffold。
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
        SectionCard(
            modifier = Modifier
                .fillMaxWidth()
                .animateEnter(120, reducedMotion),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
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
                GridStat(
                    Modifier
                        .weight(1f)
                        .testTag("metric-reading"),
                    label = stringResource(R.string.home_reading),
                    value = rememberCountUp(readingCount, reducedMotion).toString(),
                )
                GridStat(
                    Modifier
                        .weight(1f)
                        .testTag("metric-completed"),
                    label = stringResource(R.string.home_finished),
                    value = rememberCountUp(completedCount, reducedMotion).toString(),
                )
                val minutesCountUp = rememberCountUp((todayReadingMs / 60000).toInt(), reducedMotion)
                GridStat(
                    Modifier
                        .weight(1f)
                        .testTag("metric-today"),
                    label = stringResource(R.string.home_today) +
                        if (dailyGoalMinutes > 0) " · / $dailyGoalMinutes 分" else "",
                    value = formatCompactDuration(minutesCountUp.toLong() * 60000L),
                )
            }
        }
    }
}

@Composable
private fun GridStat(modifier: Modifier, label: String, value: String) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
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
