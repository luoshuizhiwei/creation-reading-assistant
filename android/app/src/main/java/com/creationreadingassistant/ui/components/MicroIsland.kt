package com.creationreadingassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec

/**
 * 微岛化共享组件库（Micro-Island Component Library）。
 *
 * 收敛此前散落在 10+ 文件内联手写的「岛屿卡片 / 区块头（图标底座+标题）/ 悬浮 Dock /
 * 信息提示微岛 / 发丝分割线」等图元，消除数值漂移（圆角 6/7/8/9/10/12/14/16/18dp、
 * 底色 alpha 0.28/0.32/0.35、边框 0.5/0.6/0.8/1dp 混用），一处调参全局生效。
 *
 * **所有组件均消费 [LocalComponentSpec] + [LocalLayoutTokens]，内部不硬编码 dp**
 * （圆角 / 边框 / alpha / 间距全部走令牌；仅底座/图标尺寸等组件固有量作为可覆盖的参数默认值）。
 *
 * ## 双轨说明
 * 本文件面向**外壳（app chrome）与深层静态页**，卡片带纸墨装饰（委托 [SectionCard]）。
 * 阅读器路径请使用 [com.creationreadingassistant.ui.theme.ReaderPanelSurface]（无装饰薄面板），
 * **禁止引用 [SectionCard] / [IslandCard]**——其 3 层装饰会在翻页动画层侵蚀帧预算。
 */

/**
 * 岛屿卡片：带纸墨装饰（渐变 + 噪点 + 发丝边 + 柔影 + luma 深浅判定）的岛屿卡片容器，
 * 用于外壳深层静态页。
 *
 * **委托现有 [SectionCard]**，复用其已验证的 luma + 材质处理逻辑，不新写一套深色判定；
 * 与 [SectionCard] 公开签名保持一致，作为微岛库统一的卡片入口，便于全域视觉收敛复用。
 *
 * @param onClick 传入则卡片可点击（带 ripple + 弹性按压），为 null 则静态。
 * @param contentPadding 内部 padding，为 null 时取 [com.creationreadingassistant.ui.layout.LayoutTokens.cardPadding]。
 */
@Composable
fun IslandCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * 图标微彩底座：分色微彩的圆角图标托座（现网 32dp 底座 / 9dp 圆角样本收敛）。
 *
 * 圆角走 [com.creationreadingassistant.ui.theme.ComponentSpec.pedestalRadius]，
 * 边框宽度走 [com.creationreadingassistant.ui.theme.ComponentSpec.hairlineBorderWidth]，
 * 供 [IslandSectionHeader] 与卡片内部复用。
 *
 * @param icon 图标矢量。
 * @param tint 分色（决定底座底色与图标着色）。
 * @param size 底座边长（组件固有量，默认 32dp，可覆盖）。
 * @param iconSize 图标边长（组件固有量，默认 17dp，可覆盖）。
 * @param enabled 禁用时降级为中性 surfaceVariant 底 + 弱化图标。
 */
@Composable
fun IconPedestal(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconSize: Dp = 17.dp,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val spec = LocalComponentSpec.current
    val scheme = MaterialTheme.colorScheme
    val shape: Shape = RoundedCornerShape(spec.pedestalRadius)
    val bg = if (enabled) tint.copy(alpha = 0.14f) else scheme.surfaceVariant.copy(alpha = 0.3f)
    val borderColor = if (enabled) {
        tint.copy(alpha = spec.hairlineAlpha)
    } else {
        scheme.outlineVariant.copy(alpha = 0.25f)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bg)
            .border(spec.hairlineBorderWidth, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else scheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * 区块头：图标微彩底座 + 标题（+ 可选 trailing），统一 32dp 底座 / pedestalRadius。
 *
 * 取代各页面手写「Box(底座) + Text(标题)」重复结构，间距走 [LocalLayoutTokens]。
 *
 * @param title 标题文本（titleSmall / SemiBold）。
 * @param icon 左侧底座图标。
 * @param tint 底座分色，默认取 primary。
 * @param trailing 右侧内容插槽（如「查看全部」按钮 / Switch），可选。
 */
@Composable
fun IslandSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
) {
    val layout = LocalLayoutTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(layout.relatedGap),
    ) {
        if (compact) {
            // 二级子页区块头：图标底座收到 24dp、标题让位给内容而非抢版面。
            IconPedestal(icon = icon, tint = tint, size = 24.dp, iconSize = 14.dp)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            IconPedestal(icon = icon, tint = tint)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        trailing?.invoke()
    }
}



/**
 * 发丝分割线：极细发丝线（委托 [SectionDivider]，厚度走
 * [com.creationreadingassistant.ui.theme.ComponentSpec.dividerThickness]），
 * 默认着 hairlineAlpha 淡化的 outlineVariant，比 [SectionDivider] 默认色更轻。
 */
@Composable
fun HairlineDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = LocalComponentSpec.current.hairlineAlpha,
    ),
) {
    SectionDivider(modifier = modifier, color = color)
}
