package com.creationreadingassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.LocalComponentSpec

/**
 * 通用指标微卡片（C-1 组件复用）：
 * 统一底座小图标、标签标题、大数字/高光数值以及次级说明文本的排版，
 * 收敛全仓各页面统计指标卡片的样式。
 *
 * @param icon 指标图标（由 [IconPedestal] 承托）
 * @param iconTint 图标色调（底座自动计算微彩半透明背景与细边框）
 * @param label 指标标题/名称
 * @param value 主指标值（如 "12.5 小时"、"42 篇" 等）
 * @param subtitle 次级说明文本（如 "目标达成率 80%"）
 * @param modifier 外部布局修饰符
 * @param valueModifier 只作用于**主值文本**的附加修饰符（默认 [Modifier]，行为不变）。
 *   用于把长按切换时长单位这类手势精确挂在数值自身而不是整张卡：挂在卡上会与卡片
 *   未来的整体点击语义冲突，也让读屏用户以为整卡可操作。
 */
@Composable
fun MetricMicroCard(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueModifier: Modifier = Modifier,
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 图标与标签同侧成组：原先用 SpaceBetween 把图标推到最左、标签推到最右，
            // 数值又在第三行，读起来视线要横跳两次。收拢后一行就能读完「图标+指标名」。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconPedestal(
                    icon = icon,
                    tint = iconTint,
                    size = 20.dp,
                    iconSize = 12.dp,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = value,
                modifier = valueModifier,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
