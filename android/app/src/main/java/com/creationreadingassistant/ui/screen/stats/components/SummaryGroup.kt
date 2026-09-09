package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.formatCompactDuration
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberReducedMotion

private data class SummaryMetric(
    val icon: ImageVector,
    val formattedValue: String,
    val label: String,
    val subtitle: String,
    val iconTint: Color,
)

/**
 * 4 项大盘指标微岛卡片化：
 * - 采用 2x2 纸墨微岛卡片布局，大数字 Bold 高光，配 32dp 微彩底座小图标与次级说明文字；
 * - 底部附带连续打卡轻量微胶囊，保持关键数据完整呈现。
 */
@Composable
internal fun SummaryGroup(
    stats: StatsUi,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val spec = LocalComponentSpec.current
    val totalMinutes = (stats.totalReadingMs / 60000).toInt()
    val animatedMinutes = rememberCountUp(totalMinutes, reducedMotion)
    val animatedDays = rememberCountUp(stats.readingDays, reducedMotion)
    val animatedBooks = rememberCountUp(stats.readBooks, reducedMotion)
    val animatedCompleted = rememberCountUp(stats.completed, reducedMotion)

    val metrics = listOf(
        SummaryMetric(
            icon = Icons.Outlined.AccessTime,
            formattedValue = formatCompactDuration(animatedMinutes.toLong() * 60000),
            label = "阅读时长",
            subtitle = "累计沉浸",
            iconTint = Color(0xFFD97706), // 暖琥珀
        ),
        SummaryMetric(
            icon = Icons.Outlined.CalendarMonth,
            formattedValue = "$animatedDays 天",
            label = "阅读天数",
            subtitle = "开卷打卡",
            iconTint = Color(0xFF2563EB), // 晴空蓝
        ),
        SummaryMetric(
            icon = Icons.Outlined.Book,
            formattedValue = "$animatedBooks 本",
            label = "读过书籍",
            subtitle = "阅读广度",
            iconTint = Color(0xFF7C3AED), // 典雅紫
        ),
        SummaryMetric(
            icon = Icons.Outlined.CheckCircle,
            formattedValue = "$animatedCompleted 本",
            label = "已读完",
            subtitle = "翻越终章",
            iconTint = Color(0xFF059669), // 自然翠绿
        ),
    )

    IslandCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-summary"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 2x2 四大核心指标微岛卡片
            metrics.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { item ->
                        SummaryMicroCard(
                            item = item,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 连续阅读微胶囊横条（当有连续天数数据时呈现）
            if (stats.streakCurrent > 0 || stats.streakLongest > 0) {
                Surface(
                    shape = RoundedCornerShape(spec.hintRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        spec.hairlineBorderWidth,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                    ),
                    modifier = Modifier.fillMaxWidth(),
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
                            text = "当前连续 ${stats.streakCurrent} 天",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "最长连续 ${stats.streakLongest} 天",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMicroCard(
    item: SummaryMetric,
    modifier: Modifier = Modifier,
) {
    val spec = LocalComponentSpec.current
    Surface(
        modifier = modifier,
        shape = spec.listItemShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            spec.hairlineBorderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 32dp 微彩底座小图标：收敛到共享 IconPedestal（pedestalRadius + hairline 边框）
                IconPedestal(
                    icon = item.icon,
                    tint = item.iconTint,
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(2.dp))

            // 大数字 Bold 高光
            Text(
                text = item.formattedValue,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

            // 次级说明文字
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
