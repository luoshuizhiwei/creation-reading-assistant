package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import com.creationreadingassistant.ui.components.CompactItemDivider
import com.creationreadingassistant.ui.components.CompactLinkRow
import com.creationreadingassistant.ui.components.CompactOptionPillRow
import com.creationreadingassistant.ui.components.CompactSettingsGroup
import com.creationreadingassistant.ui.components.CompactSliderRow
import com.creationreadingassistant.ui.components.CompactSwitchRow
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Tune
import com.creationreadingassistant.ui.components.AppAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.HeaderFooterItem
import com.creationreadingassistant.data.settings.PerBookOverrides
import com.creationreadingassistant.data.settings.ReaderOverrideKey
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.ReaderSettingsScope
import com.creationreadingassistant.data.settings.ReadingPreset
import com.creationreadingassistant.data.settings.ReadingPresets
import com.creationreadingassistant.ui.components.HairlineDivider
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.ReaderPaperOptions
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.io.File
import kotlin.math.abs

internal enum class ReaderSettingsPage(val title: String) {
    ROOT("阅读设置"),
    TYPOGRAPHY("排版"),
    PAGING("翻页与操作"),
    DISPLAY("显示与页眉页脚"),
    EYE_CARE("护眼与提醒"),
    ADVANCED("高级兼容"),
}

@Composable
internal fun SettingsList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

internal fun nearest(value: Float, options: List<Float>): Float =
    options.minByOrNull { abs(it - value) } ?: options.first()

/**
 * 分节卡片全面微岛化容器（F1：底层对齐全应用统一的 CompactSettingsGroup 规范）。
 */
@Composable
internal fun MicroSettingsSection(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    CompactSettingsGroup(
        title = title,
        icon = icon,
        tint = tint,
        description = description,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        cornerRadius = LocalComponentSpec.current.islandRadius,
    ) {
        content()
    }
}

@Composable
internal fun MicroSectionDivider(modifier: Modifier = Modifier) {
    CompactItemDivider(modifier = modifier, startIndent = 14.dp, endIndent = 14.dp)
}

/**
 * 带刻度指示的平滑微导轨滑块，左侧配备 26dp 独立微彩底座。
 */
@Composable
internal fun MicroTrackSliderRow(
    icon: ImageVector,
    title: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    tint: Color = MaterialTheme.colorScheme.primary,
    minLabel: String? = null,
    maxLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    CompactSliderRow(
        icon = icon,
        title = title,
        value = value,
        valueLabel = valueLabel,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        steps = steps,
        tint = tint,
        minLabel = minLabel,
        maxLabel = maxLabel,
    )
}

/**
 * 圆润微胶囊单选导轨（OptionPill，F1：对齐 CompactOptionPillRow）。
 */
@Composable
internal fun <T> MicroOptionPillRow(
    title: String,
    icon: ImageVector,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    CompactOptionPillRow(
        title = title,
        icon = icon,
        options = options,
        selected = selected,
        onSelect = onSelect,
        tint = tint,
        modifier = modifier,
        subtitle = subtitle,
    )
}

/**
 * F2: 本书覆盖项显性化指示徽标。
 */
@Composable
internal fun OverrideBadge(
    isOverridden: Boolean,
    modifier: Modifier = Modifier,
    globalValue: String? = null,
) {
    if (isOverridden) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = modifier.padding(start = 4.dp),
        ) {
            Text(
                text = if (globalValue != null) "本书 (全局 $globalValue)" else "本书",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * 快捷字号加减与加粗行（F2：支持显式覆盖项标注）。
 */
@Composable
internal fun QuickFontSizeRow(
    fontSize: Float,
    isBold: Boolean,
    onFontSizeChange: (Float) -> Unit,
    onBoldChange: (Boolean) -> Unit,
    isOverridden: Boolean = false,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconPedestal(
                icon = Icons.Outlined.FormatSize,
                tint = MaterialTheme.colorScheme.primary,
                size = 26.dp,
                iconSize = 14.dp,
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("字号大小", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    OverrideBadge(isOverridden)
                }
                Text("${fontSize.toInt()} 号", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val minusInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    val next = (fontSize - 1f).coerceIn(12f, 40f)
                    onFontSizeChange(next)
                },
                enabled = fontSize > 12f,
                shape = RoundedCornerShape(spec.hintRadius),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                modifier = Modifier
                    .size(width = 54.dp, height = 34.dp)
                    .bounceable(minusInteraction),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "A -",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (fontSize > 12f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    )
                }
            }

            val plusInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    val next = (fontSize + 1f).coerceIn(12f, 40f)
                    onFontSizeChange(next)
                },
                enabled = fontSize < 40f,
                shape = RoundedCornerShape(spec.hintRadius),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                modifier = Modifier
                    .size(width = 54.dp, height = 34.dp)
                    .bounceable(plusInteraction),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "A +",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (fontSize < 40f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    )
                }
            }

            val boldInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onBoldChange(!isBold)
                },
                shape = RoundedCornerShape(spec.hintRadius),
                color = if (isBold) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(spec.borderWidth, if (isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                modifier = Modifier
                    .size(width = 44.dp, height = 34.dp)
                    .bounceable(boldInteraction),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "B",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 带有独立微彩底座的链接行。
 */
@Composable
internal fun MicroSettingsLinkRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    value: String? = null,
) {
    CompactLinkRow(
        icon = icon,
        tint = tint,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        value = value,
    )
}

/**
 * 带有独立微彩底座的开关行。
 */
@Composable
internal fun MicroSettingsSwitchRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    CompactSwitchRow(
        icon = icon,
        tint = tint,
        title = title,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        subtitle = subtitle,
    )
}

/**
 * 「修改作用范围」切换：书内调整默认只改当前这本书（[ReaderSettingsScope.BOOK]），
 * 可显式切到全局。采用紧凑双态胶囊导轨，减少垂直占用。
 */
@Composable
internal fun SettingsScopeSection(
    scope: ReaderSettingsScope,
    onScopeChange: (ReaderSettingsScope) -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    CompactSettingsGroup(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        cornerRadius = LocalComponentSpec.current.islandRadius,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("reader-settings-scope"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderSettingsScope.entries.forEach { option ->
                val selected = option == scope
                val interaction = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        if (!selected) {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onScopeChange(option)
                        }
                    },
                    shape = PillShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    border = if (selected) {
                        BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .bounceable(interaction),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = if (option == ReaderSettingsScope.BOOK) "只改本书" else "改全局默认",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 预设 / 恢复默认 / 本书覆盖明细（支持紧凑折叠收纳）。
 *
 * 默认折叠展示覆盖状态摘要，点击平滑展开预设快速套用与逐项明细。
 */
@Composable
internal fun BookOverrideSection(
    scope: ReaderSettingsScope,
    overrides: PerBookOverrides,
    settings: ReaderSettings,
    globalSettings: ReaderSettings,
    onClearOverride: (ReaderOverrideKey) -> Unit,
    onClearAllOverrides: () -> Unit,
    onApplyPreset: (ReadingPreset) -> Unit,
    onResetGlobalDefaults: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    var confirmGlobalReset by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    if (confirmGlobalReset) {
        AppAlertDialog(
            onDismissRequest = { confirmGlobalReset = false },
            title = { Text("恢复全局默认设置？") },
            text = {
                Text(
                    "作用范围：**全局**。会把排版与显示相关的 ${ReaderOverrideKey.entries.size} 项" +
                        "恢复为出厂默认值（字号、行距、段距、页边距、纸张、粗细、中文排版、繁简、" +
                        "字体、阅读模式、TXT/Markdown 分页兼容模式、EPUB 分页兼容模式、" +
                        "翻页效果、沉浸、页眉页脚、进度条、菜单隐藏、屏幕方向）。\n\n" +
                        "不会改动阅读进度、高亮、笔记、替换规则与阅读统计；" +
                        "也不会清除各本书已有的覆盖。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmGlobalReset = false
                        onResetGlobalDefaults()
                    },
                ) { Text("恢复默认") }
            },
            dismissButton = {
                TextButton(onClick = { confirmGlobalReset = false }) { Text("取消") }
            },
        )
    }

    CompactSettingsGroup(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        cornerRadius = LocalComponentSpec.current.islandRadius,
    ) {
        // 头部摘要栏，点击展开/折叠
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic(HapticFeedbackType.TextHandleMove)
                    expanded = !expanded
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconPedestal(icon = Icons.Outlined.Restore, tint = Color(0xFFD97706), size = 22.dp, iconSize = 12.dp)
                Text(
                    text = "预设与覆盖",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (overrides.isEmpty()) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color(0xFFD97706).copy(alpha = 0.12f),
                ) {
                    Text(
                        text = if (overrides.isEmpty()) "全部跟随全局" else "覆盖 ${overrides.size} 项",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = if (overrides.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFD97706),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (expanded) 90f else 0f),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                MicroSectionDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "套用预设",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                        .testTag("reader-settings-presets"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ReadingPresets.ALL.forEach { preset ->
                        val interaction = remember { MutableInteractionSource() }
                        val spec = LocalComponentSpec.current
                        Surface(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onApplyPreset(preset)
                            },
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                spec.borderWidth,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .bounceable(interaction),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    preset.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                MicroSectionDivider()

                if (overrides.isEmpty()) {
                    Text(
                        "当前书籍没有覆盖任何设置，全部跟随全局（字号 ${settings.fontSize.toInt()} 号）。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                } else {
                    overrides.forEach { (key, raw) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    key.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "本书 ${key.displayValue(raw)} · 全局 ${key.displayValue(key.read(globalSettings))}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            MicroActionPill(text = "跟随全局") { onClearOverride(key) }
                        }
                    }
                }

                MicroSectionDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MicroActionPill(
                        text = "清除本书覆盖",
                        enabled = overrides.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { onClearAllOverrides() }
                    if (scope == ReaderSettingsScope.GLOBAL) {
                        MicroActionPill(
                            text = "恢复全局默认",
                            primary = false,
                            modifier = Modifier.weight(1f),
                        ) { confirmGlobalReset = true }
                    } else if (scope == ReaderSettingsScope.BOOK) {
                        MicroActionPill(
                            text = "全部跟随全局",
                            primary = false,
                            enabled = overrides.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { onClearAllOverrides() }
                    }
                }
            }
        }
    }
}

/** 统一风格的微胶囊动作按钮（设置面板内的次要操作）。 */
@Composable
internal fun MicroActionPill(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        shape = PillShape,
        color = if (primary) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        border = BorderStroke(
            spec.borderWidth,
            if (primary) {
                MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)
            },
        ),
        modifier = modifier
            .height(34.dp)
            .bounceable(interaction),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    primary -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 覆盖值的可读展示（设置面板内「本书 x · 全局 y」）。 */
internal fun ReaderOverrideKey.displayValue(raw: String): String = when (this) {
    ReaderOverrideKey.FONT_SIZE -> "${raw.toFloatOrNull()?.toInt() ?: raw} 号"
    ReaderOverrideKey.PAGE_MARGIN -> "${raw.toFloatOrNull()?.toInt() ?: raw} dp"
    ReaderOverrideKey.LINE_HEIGHT, ReaderOverrideKey.PARAGRAPH_SPACING -> raw
    ReaderOverrideKey.BACKGROUND -> ReaderPaperOptions.firstOrNull { it.key == raw }?.label ?: raw
    ReaderOverrideKey.CUSTOM_FONT_PATH -> if (raw.isBlank()) "跟随系统" else File(raw).name
    ReaderOverrideKey.READER_MODE -> when (raw) {
        "paged" -> "左右翻页"
        "scroll" -> "上下滚动"
        else -> raw
    }
    ReaderOverrideKey.PAGE_TURN_EFFECT -> when (raw) {
        "none" -> "无"
        "fade" -> "淡入"
        "slide" -> "平移"
        "cover" -> "仿真"
        "reveal" -> "揭示"
        else -> raw
    }
    ReaderOverrideKey.AUTO_HIDE_SECONDS -> if (raw == "0") "不自动隐藏" else "$raw 秒"
    ReaderOverrideKey.SCREEN_ORIENTATION -> when (raw) {
        "system" -> "跟随系统"
        "portrait" -> "竖屏"
        "landscape" -> "横屏"
        else -> raw
    }
    ReaderOverrideKey.HEADER_LEFT,
    ReaderOverrideKey.HEADER_RIGHT,
    ReaderOverrideKey.FOOTER_LEFT,
    ReaderOverrideKey.FOOTER_RIGHT,
    -> HeaderFooterItem.entries.firstOrNull { it.name == raw }?.label ?: raw

    else -> when (raw) {
        "true" -> "开"
        "false" -> "关"
        else -> raw
    }
}

