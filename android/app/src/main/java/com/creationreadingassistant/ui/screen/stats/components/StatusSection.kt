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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.components.IslandSectionHeader
import com.creationreadingassistant.ui.screen.stats.BookStatus
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion

private data class StatusItemData(
    val label: String,
    val count: Int,
    val color: Color,
)

/**
 * 书籍状态分布微岛：
 * - 顶部 32dp 微彩底座与总本数摘要胶囊；
 * - 统一聚合的水平切片分布条；
 * - 状态分布图例小胶囊，色彩层级和谐；
 * - 细腻圆角单项进度条与明确数据对比。
 */
@Composable
internal fun StatusSection(
    status: BookStatus,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val spec = LocalComponentSpec.current
    val scheme = MaterialTheme.colorScheme

    val readingColor = scheme.primary
    val completedColor = Color(0xFF059669) // 自然翡翠绿
    val shelvedColor = Color(0xFF64748B)   // 岩青灰
    val unreadColor = Color(0xFF94A3B8)    // 素墨浅灰
    val errorColor = scheme.error          // 警示红

    val items = buildList {
        add(StatusItemData("在读", status.reading, readingColor))
        if (status.shelved > 0) {
            add(StatusItemData("搁置", status.shelved, shelvedColor))
        }
        add(StatusItemData("已读完", status.completed, completedColor))
        add(StatusItemData("未开始", status.unread, unreadColor))
        if (status.unreadable > 0) {
            add(StatusItemData("不可读", status.unreadable, errorColor))
        }
    }

    IslandCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-status"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 标题行：收敛到共享 IslandSectionHeader（32dp IconPedestal 底座）+ 令牌化摘要胶囊
            IslandSectionHeader(
                title = "书籍状态",
                icon = Icons.Outlined.CollectionsBookmark,
                tint = completedColor,
                trailing = {
                    Surface(
                        shape = PillShape,
                        color = scheme.surfaceContainerHigh.copy(alpha = 0.45f),
                        border = BorderStroke(
                            spec.hairlineBorderWidth,
                            scheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                        ),
                    ) {
                        Text(
                            text = "共 ${status.total} 本",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                },
            )

            // 聚合水平切片分布条
            if (status.total > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(PillShape)
                        .background(scheme.surfaceContainerHighest.copy(alpha = 0.45f)),
                ) {
                    items.forEach { item ->
                        if (item.count > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(item.count.toFloat())
                                    .height(10.dp)
                                    .background(item.color),
                            )
                        }
                    }
                }
            }

            // 状态分布图例小胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = PillShape,
                        color = scheme.surfaceContainerLow,
                        border = BorderStroke(
                            spec.hairlineBorderWidth,
                            scheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(item.color),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${item.label} ${item.count}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = scheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // 细分条目列表
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    StatusBar(
                        label = item.label,
                        count = item.count,
                        total = status.total,
                        color = item.color,
                    )
                }
            }

            if (status.unreadable > 0) {
                Surface(
                    shape = RoundedCornerShape(spec.hintRadius),
                    color = scheme.errorContainer.copy(alpha = 0.25f),
                    border = BorderStroke(spec.hairlineBorderWidth, scheme.error.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = scheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "不可读包括同步占位、导入失败或文件缺失的书籍。",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(label: String, count: Int, total: Int, color: Color) {
    val fraction = if (total > 0) (count.toFloat() / total) else 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color = color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(52.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = fraction)
                        .height(8.dp)
                        .clip(PillShape)
                        .background(color = color),
                )
            }
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
