package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.formatCompactDuration
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberReducedMotion

private data class CreationMetricItem(
    val icon: ImageVector,
    val iconTint: Color,
    val value: String,
    val label: String,
    val subtitle: String,
)

/**
 * 灵感创作统计微岛：
 * - 顶部 32dp 微彩底座图标与灵光积累摘要胶囊；
 * - 2x2 灵感与笔记微岛卡片，配 32dp 微彩底座与次级说明；
 * - 大数字展示衬线字体，字号与层次明晰。
 */
@Composable
internal fun CreationSection(
    stats: StatsUi,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val scheme = MaterialTheme.colorScheme

    val totalCreations = stats.noteCount + stats.inspirationCount
    val animatedNotes = rememberCountUp(stats.noteCount, reducedMotion)
    val animatedInspirations = rememberCountUp(stats.inspirationCount, reducedMotion)
    val animatedDays = rememberCountUp(stats.readingDays, reducedMotion)

    val items = listOf(
        CreationMetricItem(
            icon = Icons.Outlined.ChatBubble,
            iconTint = Color(0xFF2563EB), // 知性蓝
            value = "$animatedNotes",
            label = "笔记记录",
            subtitle = "读中墨痕",
        ),
        CreationMetricItem(
            icon = Icons.Outlined.AutoAwesome,
            iconTint = Color(0xFFD97706), // 金黄灵光
            value = "$animatedInspirations",
            label = "闪念灵感",
            subtitle = "思维火花",
        ),
        CreationMetricItem(
            icon = Icons.Outlined.AccessTime,
            iconTint = Color(0xFF059669), // 自然翠绿
            value = formatCompactDuration(stats.totalReadingMs),
            label = "沉浸时长",
            subtitle = "心流专注",
        ),
        CreationMetricItem(
            icon = Icons.Outlined.CalendarMonth,
            iconTint = Color(0xFF7C3AED), // 典雅紫
            value = "$animatedDays 天",
            label = "阅读天数",
            subtitle = "开卷有益",
        ),
    )

    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-creation"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFD97706).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        "阅读与创作",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = scheme.surfaceContainerHigh.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = if (totalCreations > 0) "$totalCreations 处墨痕灵光" else "静候初次动笔",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // 2x2 灵感创作微岛卡片
            items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { item ->
                        CreationMicroCard(
                            item = item,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreationMicroCard(
    item: CreationMetricItem,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(item.iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = item.iconTint,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = item.value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
