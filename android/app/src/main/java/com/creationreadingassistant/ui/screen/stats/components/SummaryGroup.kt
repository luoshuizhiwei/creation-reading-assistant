package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.components.MetricMicroCard
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.formatCompactDuration
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.util.LocalDurationUnit
import com.creationreadingassistant.ui.util.durationUnitToggle

/**
 * 4 项大盘指标微岛卡片化：
 * - 采用 2x2 纸墨微岛卡片布局，大数字 Bold 高光，配 32dp 微彩底座小图标与次级说明文字；
 * - 底部附带连续打卡轻量微胶囊（独立重组边界，数字动画期间完全跳过重组）。
 */
@Composable
internal fun SummaryGroup(
    stats: StatsUi,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val spec = LocalComponentSpec.current
    val totalMinutes = (stats.totalReadingMs / 60000).toInt()
    val animatedMinutes = rememberCountUp(totalMinutes, "stats.summary.minutes", reducedMotion)
    val animatedDays = rememberCountUp(stats.readingDays, "stats.summary.days", reducedMotion)
    val animatedBooks = rememberCountUp(stats.readBooks, "stats.summary.books", reducedMotion)
    val animatedCompleted = rememberCountUp(stats.completed, "stats.summary.completed", reducedMotion)

    IslandCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-summary"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 2x2 四大核心指标微岛卡片（静态网格声明，消灭动态 listOf 与 chunked(2) 开销）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricMicroCard(
                    icon = Icons.Outlined.AccessTime,
                    iconTint = Color(0xFFD97706), // 暖琥珀
                    label = "阅读时长",
                    value = formatCompactDuration(
                        animatedMinutes.toLong() * 60000,
                        LocalDurationUnit.current,
                    ),
                    subtitle = "累计沉浸",
                    modifier = Modifier.weight(1f),
                    // 阅读时长是本组唯一的时长展示位 ⇒ 本区块只此一个切换入口；
                    // 「阅读天数」是整数计数（不经过 formatter），不挂入口。
                    valueModifier = Modifier.durationUnitToggle(),
                )
                MetricMicroCard(
                    icon = Icons.Outlined.CalendarMonth,
                    iconTint = Color(0xFF2563EB), // 晴空蓝
                    label = "阅读天数",
                    value = "$animatedDays 天",
                    subtitle = "开卷打卡",
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricMicroCard(
                    icon = Icons.Outlined.Book,
                    iconTint = Color(0xFF7C3AED), // 典雅紫
                    label = "读过书籍",
                    value = "$animatedBooks 本",
                    subtitle = "阅读广度",
                    modifier = Modifier.weight(1f),
                )
                MetricMicroCard(
                    icon = Icons.Outlined.CheckCircle,
                    iconTint = Color(0xFF059669), // 自然翠绿
                    label = "已读完",
                    value = "$animatedCompleted 本",
                    subtitle = "翻越终章",
                    modifier = Modifier.weight(1f),
                )
            }

            // 连续阅读微胶囊横条（独立可跳过组件，数字滚动动画递增时自动跳过重组）
            if (stats.streakCurrent > 0 || stats.streakLongest > 0) {
                StreakMicroCapsule(
                    streakCurrent = stats.streakCurrent,
                    streakLongest = stats.streakLongest,
                    hintRadius = spec.hintRadius,
                    hairlineBorderWidth = spec.hairlineBorderWidth,
                    hairlineAlpha = spec.hairlineAlpha,
                )
            }
        }
    }
}

/** 连续阅读微胶囊：独立重组作用域，参数稳定时自动跳过重组。 */
@Composable
private fun StreakMicroCapsule(
    streakCurrent: Int,
    streakLongest: Int,
    hintRadius: Dp,
    hairlineBorderWidth: Dp,
    hairlineAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(hintRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            hairlineBorderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = hairlineAlpha),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconPedestal(
                icon = Icons.Outlined.LocalFireDepartment,
                tint = Color(0xFFEA580C),
                size = 24.dp,
                iconSize = 15.dp,
            )
            Text(
                text = "当前连续 $streakCurrent 天",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "最长连续 $streakLongest 天",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
