package com.creationreadingassistant.ui.screen.reader.sheets

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.feature.reader.ReaderFontManager
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.components.SettingBrightnessRow
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.io.File

/**
 * 正文字体选择列表项微岛化，提供中文字体预览与选中微指示。
 */
@Composable
internal fun MicroFontPickerSection(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val path = ReaderFontManager.installFont(context, uri)
            if (path != null && ReaderFontManager.isFontUsable(path)) {
                ReaderFontManager.clearCache()
                onChange(settings.copy(customFontPath = path))
                Toast.makeText(context, "已应用自定义字体", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "无法使用该字体文件（仅支持 .ttf / .otf）", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val custom = settings.customFontPath

    LaunchedEffect(settings.customFontPath) {
        val path = settings.customFontPath
        if (path.isNotBlank() && !ReaderFontManager.isFontUsable(path)) {
            ReaderFontManager.clearCache()
            onChange(settings.copy(customFontPath = ""))
            Toast.makeText(context, "自定义字体文件已失效，已恢复系统字体", Toast.LENGTH_SHORT).show()
        }
    }

    val customTypeface = remember(custom) {
        if (custom.isNotBlank()) ReaderFontManager.loadTypeface(custom) else null
    }

    Column(
        modifier = Modifier
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
                IconPedestal(
                    icon = Icons.Outlined.FontDownload,
                    tint = Color(0xFF0891B2),
                    size = 26.dp,
                    iconSize = 14.dp,
                )
                Text(
                    "正文字体",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 当前字体微胶囊
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
            ) {
                Text(
                    text = if (custom.isBlank()) "跟随系统" else File(custom).name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        // 中文字体实时微岛样张预览
        Surface(
            shape = RoundedCornerShape(spec.hintRadius),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "中文字体预览",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Text(
                            if (customTypeface != null) "已生效自定义" else "系统原生",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text = "白日依山尽，黄河入海流。欲穷千里目，更上一层楼。",
                    fontFamily = if (customTypeface != null) FontFamily(customTypeface) else FontFamily.Default,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 字体微岛操作按钮组
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val importInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    launcher.launch(arrayOf("*/*"))
                },
                shape = PillShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .bounceable(importInteraction),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.FormatColorText,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "导入字体 (.ttf/.otf)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (custom.isNotBlank()) {
                val restoreInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        ReaderFontManager.clearCache()
                        onChange(settings.copy(customFontPath = ""))
                        Toast.makeText(context, "已恢复系统字体", Toast.LENGTH_SHORT).show()
                    },
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                    modifier = Modifier
                        .height(34.dp)
                        .bounceable(restoreInteraction),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "恢复系统",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 排版预览微岛卡片。
 */
@Composable
internal fun ReaderTypePreview(paper: ReaderPaperPalette, settings: ReaderSettings) {
    val size = settings.fontSize.coerceIn(12f, 40f)
    val lineHeightSp = (size * settings.lineHeight).sp
    val paragraphDp: Dp = with(LocalDensity.current) {
        (size * 0.4f * settings.paragraphSpacing).sp.toDp()
    }
    val weight = if (settings.fontWeightBold) FontWeight.Bold else FontWeight.Normal
    val spec = LocalComponentSpec.current
    Surface(
        shape = RoundedCornerShape(spec.islandRadius),
        color = paper.bg,
        border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "排版预览",
                    style = MaterialTheme.typography.labelMedium,
                    color = paper.fgMuted,
                )
                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        "${size.toInt()} 号 · ${if (settings.fontWeightBold) "粗体" else "常规"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                "窗前的纸页安静展开，每一个字都落在恰好的位置，行与行之间留出呼吸的余地，读起来就不费眼睛。",
                color = paper.fg,
                fontSize = size.sp,
                lineHeight = lineHeightSp,
                fontWeight = weight,
            )
            Spacer(Modifier.height(paragraphDp))
            Text(
                "段落之间的空隙也会按当前设置自动拉开，让段落边界看得清、又不至于断开节奏。",
                color = paper.fg,
                fontSize = size.sp,
                lineHeight = lineHeightSp,
                fontWeight = weight,
            )
        }
    }
}

@Composable
internal fun TypographySettings(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    paper: ReaderPaperPalette,
) = SettingsList {
    item { ReaderTypePreview(paper, settings) }
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.TextFields,
            title = "文字与字体",
            tint = Color(0xFF2563EB),
        ) {
            MicroTrackSliderRow(
                icon = Icons.Outlined.FormatSize,
                title = "字号大小",
                value = settings.fontSize,
                valueLabel = "${settings.fontSize.toInt()} 号",
                onValueChange = { onChange(settings.copy(fontSize = it)) },
                valueRange = 12f..40f,
                steps = 27,
                minLabel = "12",
                maxLabel = "40",
                tint = Color(0xFF2563EB),
            )
            MicroSectionDivider()
            MicroFontPickerSection(settings, onChange)
            MicroSectionDivider()
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.FormatSize,
                tint = Color(0xFF059669),
                title = "粗体文字",
                checked = settings.fontWeightBold,
                onCheckedChange = { onChange(settings.copy(fontWeightBold = it)) },
                subtitle = "加重笔画黑度，提升弱光可读性",
            )
            MicroSectionDivider()
            SettingBrightnessRow(
                brightness = settings.brightness,
                onBrightnessChange = { v ->
                    onChange(settings.copy(brightness = v, lastFixedBrightness = if (v >= 0) v else settings.lastFixedBrightness))
                },
                fixedDefault = settings.lastFixedBrightness,
            )
        }
    }
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.FormatLineSpacing,
            title = "间距与版面",
            tint = Color(0xFF059669),
        ) {
            MicroOptionPillRow(
                title = "行距比例",
                icon = Icons.Outlined.FormatLineSpacing,
                options = listOf(1.5f to "紧凑", 1.85f to "标准", 2.1f to "宽松"),
                selected = nearest(settings.lineHeight, listOf(1.5f, 1.85f, 2.1f)),
                onSelect = { onChange(settings.copy(lineHeight = it)) },
                tint = Color(0xFF059669),
            )
            MicroSectionDivider()
            MicroOptionPillRow(
                title = "段间间距",
                icon = Icons.Outlined.SpaceBar,
                options = listOf(0.8f to "小", 1.1f to "中", 1.5f to "大"),
                selected = nearest(settings.paragraphSpacing, listOf(0.8f, 1.1f, 1.5f)),
                onSelect = { onChange(settings.copy(paragraphSpacing = it)) },
                tint = Color(0xFFD97706),
            )
            MicroSectionDivider()
            MicroTrackSliderRow(
                icon = Icons.AutoMirrored.Outlined.ViewSidebar,
                title = "页边边距",
                value = settings.pageMargin,
                valueLabel = "${settings.pageMargin.toInt()} dp",
                onValueChange = { onChange(settings.copy(pageMargin = it)) },
                valueRange = 10f..42f,
                steps = 31,
                minLabel = "10dp",
                maxLabel = "42dp",
                tint = Color(0xFF7C3AED),
            )
        }
    }
}
