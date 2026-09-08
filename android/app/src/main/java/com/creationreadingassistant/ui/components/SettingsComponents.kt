package com.creationreadingassistant.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.widget.Toast
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.feature.reader.ReaderFontManager
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.io.File

@Composable
fun SettingsScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    AppScreenScaffold(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    ) { padding ->
        PageLazyColumn(scaffoldPadding = padding, content = content)
    }
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layout = LocalLayoutTokens.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(layout.relatedGap),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = DisplayFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsGroup(content = content)
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
    )
}

@Composable
fun SettingSliderRow(
    title: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    val layout = LocalLayoutTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = layout.cardPadding, vertical = layout.relatedGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = layout.minimumTouchTarget),
        )
    }
}

/**
 * 阅读亮度行：跟随系统（-1）与固定亮度（0..100）二选一。
 * 默认跟随系统，避免阅读器强制把屏幕拉满刺眼；固定亮度 0–4% 靠黑色压暗遮罩补足。
 */
@Composable
fun SettingBrightnessRow(
    brightness: Int,
    onBrightnessChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fixedDefault: Int = 65,
) {
    val followSystem = brightness < 0
    Column(modifier.fillMaxWidth()) {
        SettingSwitchRow(
            title = "跟随系统亮度",
            subtitle = if (followSystem) "阅读亮度与手机保持一致" else "关闭后使用下方固定亮度",
            checked = followSystem,
            onCheckedChange = { follow ->
                onBrightnessChange(if (follow) -1 else fixedDefault.coerceIn(0, 100))
            },
        )
        if (!followSystem) {
            SettingSliderRow(
                title = "亮度",
                value = brightness.toFloat(),
                valueLabel = "$brightness%",
                onValueChange = { onBrightnessChange(it.toInt()) },
                valueRange = 0f..100f,
                steps = 99,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingSegmentedRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val layout = LocalLayoutTokens.current
    val haptic = rememberHaptic(rememberReducedMotion())
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = layout.cardPadding, vertical = layout.relatedGap),
        verticalArrangement = Arrangement.spacedBy(layout.relatedGap),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (options.size <= 4) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = selected == value,
                        onClick = { haptic(HapticFeedbackType.TextHandleMove); onSelect(value) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        modifier = Modifier.weight(1f),
                        icon = {},
                        label = {
                            Text(
                                label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(options.size) { index ->
                    val (value, label) = options[index]
                    val isSelected = selected == value
                    androidx.compose.material3.FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onSelect(value)
                        },
                        enabled = enabled,
                        label = {
                            Text(
                                label,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                            enabled = enabled,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun SettingLinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * 正文字体选择行：跟随系统 / SAF 导入 .ttf/.otf / 恢复系统字体。
 * 阅读器设置 sheet 与 Profile 阅读设置共用；导入失败静默（保持系统字体）。
 * 已配置的字体文件被删/损坏时自动清空设置并恢复系统字体（Toast 提示）。
 */
@Composable
fun ReaderFontPickerRow(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val path = ReaderFontManager.installFont(context, uri)
            if (path != null && ReaderFontManager.isFontUsable(path)) {
                ReaderFontManager.clearCache()
                onChange(settings.copy(customFontPath = path))
            } else {
                Toast.makeText(context, "无法使用该字体文件（仅支持 .ttf / .otf）", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val custom = settings.customFontPath
    // 失效兜底：配置了字体但文件已被删除/损坏（卸载、清数据等）→ 清设置回退系统字体
    LaunchedEffect(settings.customFontPath) {
        val path = settings.customFontPath
        if (path.isNotBlank() && !ReaderFontManager.isFontUsable(path)) {
            ReaderFontManager.clearCache()
            onChange(settings.copy(customFontPath = ""))
            Toast.makeText(context, "自定义字体文件已失效，已恢复系统字体", Toast.LENGTH_SHORT).show()
        }
    }
    if (custom.isBlank()) {
        SettingLinkRow(
            title = "正文字体",
            subtitle = "跟随系统字体；支持导入 .ttf / .otf 字体文件",
            value = "跟随系统",
            onClick = { launcher.launch(arrayOf("*/*")) },
        )
    } else {
        SettingLinkRow(
            title = "正文字体",
            subtitle = "已使用自定义字体；点此可更换",
            value = File(custom).name,
            onClick = { launcher.launch(arrayOf("*/*")) },
        )
        // 字形预览：用当前自定义字体渲染示例文本，所见即所得
        val customTypeface = remember(custom) {
            if (custom.isNotBlank()) ReaderFontManager.loadTypeface(custom) else null
        }
        if (customTypeface != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "预览：天地玄黄，宇宙洪荒。山高水长，12345",
                    fontFamily = FontFamily(customTypeface),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SectionDivider()
        SettingLinkRow(
            title = "恢复系统字体",
            subtitle = "换字体后阅读位置会自动保留",
            onClick = {
                ReaderFontManager.clearCache()
                onChange(settings.copy(customFontPath = ""))
            },
        )
    }
}
