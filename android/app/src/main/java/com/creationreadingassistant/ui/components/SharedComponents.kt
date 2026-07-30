package com.creationreadingassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.theme.CardContainer
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.resolve
import com.creationreadingassistant.ui.layout.LocalLayoutTokens

/**
 * 统一区块卡片。消费 [LocalComponentSpec]，自动适配三套主题的圆角/边框/底色/阴影。
 *
 * 取代各页面手写重复的 `Card(shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, outlineVariant), ...)`
 * 以及 `Surface(shape = ..., color = surfaceContainerLowest, ...)` 等散落实现。
 *
 * 形状统一读 [com.creationreadingassistant.ui.theme.ComponentSpec.cardShape]
 * （Apple = squircle，DEFAULT/WEB = RoundedCornerShape），不再手写 `RoundedCornerShape(spec.cardRadius)`。
 *
 * 注意：SectionCard 是「非玻璃」卡片；Apple 下的毛玻璃近似由 [GlassCard]（基于 [GlassSurface]）提供。
 *
 * @param onClick 传入则卡片可点击（带 ripple），为 null 则静态
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = LocalComponentSpec.current
    val scheme = MaterialTheme.colorScheme
    val container = spec.cardContainer.resolve()
    val borderColor = if (spec.borderSubtle) scheme.outlineVariant else scheme.outline
    val shape = spec.cardShape
    val border = BorderStroke(spec.borderWidth, borderColor)
    val pad = contentPadding ?: LocalLayoutTokens.current.cardPadding

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = container,
            border = border,
            tonalElevation = spec.cardElevation,
            shadowElevation = spec.cardElevationAmbient,
            modifier = modifier,
        ) {
            Column(Modifier.padding(pad), content = content)
        }
    } else {
        Surface(
            shape = shape,
            color = container,
            border = border,
            tonalElevation = spec.cardElevation,
            shadowElevation = spec.cardElevationAmbient,
            modifier = modifier,
        ) {
            Column(Modifier.padding(pad), content = content)
        }
    }
}

/**
 * Apple 毛玻璃近似卡片（frosted glass）。
 *
 * 委托 [GlassSurface] 实现：半透明表面（取 spec.cardContainer × spec.glassTint）+ 发丝边 +
 * 环境阴影 + 顶部 specular 高光层（仅 APPLE 且 [com.creationreadingassistant.ui.theme.LocalGlassPalette] 非 null 时）。
 *
 * 真背景模糊在 minSdk 24 上不可用（需 API 31+ 的 Window 模糊，仅 Dialog 容器启用）；
 * 卡片本身恒定不调用真模糊。
 *
 * @param onClick 传入则卡片可点击（带 ripple + 弹性按压），为 null 则静态
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = LocalComponentSpec.current
    val layout = LocalLayoutTokens.current
    GlassSurface(
        modifier = modifier,
        shape = spec.cardShape,
        onClick = onClick,
        content = content,
    )
}

/**
 * 统一分割线。厚度来自 [LocalComponentSpec.dividerThickness]（iOS 0.5dp / 其他 1dp）。
 */
@Composable
fun SectionDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    val spec = LocalComponentSpec.current
    HorizontalDivider(
        modifier = modifier,
        thickness = spec.dividerThickness,
        color = color,
    )
}

/**
 * 统一可点击胶囊（筛选 chip / 分段选项 / 类型标签）。
 * 取代 Inspiration/Search/StatusRail/OptionPill 各自手写的 999.dp + outlineVariant 逻辑。
 *
 * 形状读 [com.creationreadingassistant.ui.theme.ComponentSpec.pillShape]，并叠加 [bounceable] 弹性按压。
 *
 * @param selected 是否选中（选中用 secondaryContainer，未选用 surfaceContainerLow）
 */
@Composable
fun SelectablePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonalSelectedColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    tonalSelectedContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    unselectedContent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedBorderTint: Color = MaterialTheme.colorScheme.primary,
) {
    val spec = LocalComponentSpec.current
    val scheme = MaterialTheme.colorScheme
    val container = if (selected) tonalSelectedColor else unselectedColor
    val contentColor = if (selected) tonalSelectedContent else unselectedContent
    val border = if (selected) {
        BorderStroke(spec.borderWidth, selectedBorderTint.copy(alpha = 0.4f))
    } else {
        BorderStroke(spec.borderWidth, scheme.outlineVariant)
    }
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = spec.pillShape,
        color = container,
        border = border,
        contentColor = contentColor,
        modifier = modifier.bounceable(interactionSource),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/**
 * 统一弹层把手（ModalBottomSheet / 抽屉的顶部小横条）。
 * 宽度/高度/圆角/颜色均来自 [LocalComponentSpec] 与 MaterialTheme，
 * 消灭各页面手写 `Box(40.dp × 4.dp, RoundedCornerShape(2.dp), outline)` 的重复。
 */
@Composable
fun SheetHandle(
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(scheme.outline),
        )
    }
}

/**
 * 统一设置行（图标 + 标题 + 右侧值/开关/箭头）。
 * 取代 ProfileScreen 中约 20+ 处重复的 ToggleRow / SettingRow 结构。
 *
 * 形状读 [com.creationreadingassistant.ui.theme.ComponentSpec.listItemShape]，
 * 可点击时叠加 [bounceable] 弹性按压。
 *
 * @param leading 左侧图标（可选）
 * @param trailing 右侧内容（如 Switch、Text、Icon），可选
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val spec = LocalComponentSpec.current
    val layout = LocalLayoutTokens.current
    val shape = spec.listItemShape
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(
                min = if (subtitle == null) {
                    layout.singleLineRowHeight
                } else {
                    layout.supportingRowHeight
                },
            )
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                        .bounceable(interactionSource)
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = layout.cardPadding,
                vertical = layout.relatedGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(layout.contentGap),
    ) {
        if (leading != null) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leading() }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(layout.microGap))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * 统一空状态提示（虚线框）。缺内容时比实心卡片更诚实。
 *
 * 形状读 [com.creationreadingassistant.ui.theme.ComponentSpec.listItemShape]，可点击时叠加 [bounceable]。
 */
@Composable
fun EmptyStateHint(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val radius = LocalComponentSpec.current.listItemShape
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        interactionSource = interactionSource,
        shape = radius,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = scheme.outlineVariant,
        ),
        modifier = modifier.then(if (onClick != null) Modifier.bounceable(interactionSource) else Modifier),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(LocalLayoutTokens.current.cardPadding),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
