package com.creationreadingassistant.ui.screen.reader.sheets

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
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * 分节卡片全面微岛化容器。
 */
@Composable
internal fun MicroSettingsSection(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val spec = LocalComponentSpec.current
    Surface(
        shape = RoundedCornerShape(spec.islandRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconPedestal(icon = icon, tint = tint, size = 22.dp, iconSize = 13.dp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
internal fun MicroSectionDivider() {
    HairlineDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
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
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconPedestal(icon = icon, tint = tint, size = 26.dp, iconSize = 14.dp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 读数指示微徽章
            Surface(
                shape = PillShape,
                color = tint.copy(alpha = 0.12f),
                border = BorderStroke(spec.hairlineBorderWidth, tint.copy(alpha = spec.hairlineAlpha)),
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (minLabel != null) {
                Text(
                    text = minLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f),
                onValueChangeFinished = {
                    haptic(HapticFeedbackType.TextHandleMove)
                },
            )
            if (maxLabel != null) {
                Text(
                    text = maxLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/**
 * 圆润微胶囊单选导轨（OptionPill）。
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
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconPedestal(icon = icon, tint = tint, size = 26.dp, iconSize = 14.dp)
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 微胶囊单选导轨
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { (value, label) ->
                    val isSelected = selected == value
                    val pillInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onSelect(value)
                        },
                        shape = PillShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (isSelected) BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .bounceable(pillInteraction),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
 * 快捷字号加减与加粗行。
 */
@Composable
internal fun QuickFontSizeRow(
    fontSize: Float,
    isBold: Boolean,
    onFontSizeChange: (Float) -> Unit,
    onBoldChange: (Boolean) -> Unit,
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
                Text("字号大小", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
    val haptic = rememberHaptic(rememberReducedMotion())
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .bounceable(interaction),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                IconPedestal(icon = icon, tint = tint, size = 26.dp, iconSize = 14.dp)
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    Text(
                        value,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            IconPedestal(icon = icon, tint = tint, size = 26.dp, iconSize = 14.dp)
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * 「修改作用范围」切换：书内调整默认只改当前这本书（[ReaderSettingsScope.BOOK]），
 * 可显式切到全局。范围只影响**可被本书覆盖**的项（排版 / 显示）；亮度、护眼、TTS 等
 * 设备相关项永远只改全局，由 [com.creationreadingassistant.data.settings.ReaderSettingsRouter] 保证。
 */
@Composable
internal fun SettingsScopeSection(
    scope: ReaderSettingsScope,
    onScopeChange: (ReaderSettingsScope) -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    MicroSettingsSection(
        icon = Icons.Outlined.Tune,
        title = "修改作用范围",
        tint = Color(0xFF2563EB),
        description = when (scope) {
            ReaderSettingsScope.BOOK -> "当前：只改本书。下方改动只影响这一本书，其它书保持原样。"
            ReaderSettingsScope.GLOBAL -> "当前：改全局。下方改动会成为所有书籍的默认值。"
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("reader-settings-scope"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(
                        spec.borderWidth,
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
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
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = if (option == ReaderSettingsScope.BOOK) "只改本书" else "改全局",
                                style = MaterialTheme.typography.labelMedium,
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
 * 预设 / 恢复默认 / 本书覆盖明细。
 *
 * 预设与恢复都作用于当前选择的范围；本书覆盖明细逐项列出「本书值 vs 全局值」，
 * 每一项都能单独「跟随全局」，满足「操作后 UI 立即反映真实来源」。
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

    if (confirmGlobalReset) {
        AlertDialog(
            onDismissRequest = { confirmGlobalReset = false },
            title = { Text("恢复全局默认设置？") },
            text = {
                Text(
                    "作用范围：**全局**。会把排版与显示相关的 ${ReaderOverrideKey.entries.size} 项" +
                        "恢复为出厂默认值（字号、行距、段距、页边距、纸张、粗细、中文排版、繁简、" +
                        "字体、阅读模式、翻页效果、沉浸、页眉页脚、进度条、菜单隐藏）。\n\n" +
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

    MicroSettingsSection(
        icon = Icons.Outlined.Restore,
        title = "预设与恢复",
        tint = Color(0xFFD97706),
        description = "预设与恢复默认都作用于上方选择的「${scope.label}」。",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconPedestal(icon = Icons.Outlined.Check, tint = Color(0xFFD97706), size = 26.dp, iconSize = 14.dp)
            Text(
                "套用预设",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .testTag("reader-settings-presets"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        .height(34.dp)
                        .bounceable(interaction),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            preset.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        MicroSectionDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "本书覆盖",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${overrides.size} 项",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (overrides.isEmpty()) {
            Text(
                "当前书籍没有覆盖任何设置，全部跟随全局（字号 ${settings.fontSize.toInt()} 号）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            )
        } else {
            overrides.forEach { (key, raw) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            key.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "本书 ${key.displayValue(raw)} · 全局 ${key.displayValue(key.read(globalSettings))}",
                            style = MaterialTheme.typography.bodySmall,
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
    ReaderOverrideKey.BACKGROUND -> when (raw) {
        "white" -> "白纸"
        "warm" -> "暖纸"
        "green" -> "护眼绿"
        "night" -> "夜读"
        "follow" -> "跟随外观"
        else -> raw
    }
    ReaderOverrideKey.CUSTOM_FONT_PATH -> if (raw.isBlank()) "跟随系统" else File(raw).name
    ReaderOverrideKey.READER_MODE -> when (raw) {
        "paged" -> "左右翻页"
        "scroll" -> "上下滚动"
        else -> raw
    }
    ReaderOverrideKey.PAGE_TURN_EFFECT -> when (raw) {
        "none" -> "无"
        "fade" -> "平移"
        "slide" -> "滑动"
        "cover" -> "仿真"
        "reveal" -> "揭示"
        else -> raw
    }
    ReaderOverrideKey.AUTO_HIDE_SECONDS -> if (raw == "0") "不自动隐藏" else "$raw 秒"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeaderFooterPicker(
    label: String,
    selected: HeaderFooterItem,
    onSelect: (HeaderFooterItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        TextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HeaderFooterItem.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}
