package com.creationreadingassistant.ui.screen.reader.sheets

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.HeaderFooterItem
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.feature.reader.ReaderFontManager
import com.creationreadingassistant.ui.components.HairlineDivider
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.components.SettingBrightnessRow
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.reader.AUTO_HIDE_SECOND_OPTIONS
import com.creationreadingassistant.ui.screen.reader.autoHideSecondsLabel
import com.creationreadingassistant.ui.screen.reader.nearestAutoHideOption
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.io.File
import java.util.Calendar
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
internal fun SettingsSheet(
    paper: ReaderPaperPalette,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onBookInfo: () -> Unit,
) {
    var page by remember { mutableStateOf(ReaderSettingsPage.ROOT) }
    val reducedMotion = rememberReducedMotion()
    BackHandler(enabled = page != ReaderSettingsPage.ROOT) { page = ReaderSettingsPage.ROOT }

    ReaderSheetScaffold(
        title = page.title,
        onBack = if (page == ReaderSettingsPage.ROOT) null else ({ page = ReaderSettingsPage.ROOT }),
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                }
            },
            label = "reader-settings-page",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            when (target) {
                ReaderSettingsPage.ROOT -> SettingsRoot(
                    paper = paper,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onNavigate = { page = it },
                    onBookInfo = onBookInfo,
                )
                ReaderSettingsPage.TYPOGRAPHY -> TypographySettings(settings, onSettingsChange, paper)
                ReaderSettingsPage.PAGING -> PagingSettings(settings, onSettingsChange)
                ReaderSettingsPage.DISPLAY -> DisplaySettings(settings, onSettingsChange)
                ReaderSettingsPage.EYE_CARE -> EyeCareSettings(settings, onSettingsChange)
                ReaderSettingsPage.ADVANCED -> AdvancedSettings(settings, onSettingsChange)
            }
        }
    }
}

@Composable
private fun SettingsRoot(
    paper: ReaderPaperPalette,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onNavigate: (ReaderSettingsPage) -> Unit,
    onBookInfo: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. 快捷排版微岛：字号与加粗、行距微导轨、翻页效果微胶囊单选导轨
        item(key = "quick-typography") {
            MicroSettingsSection(
                icon = Icons.Outlined.FormatSize,
                title = "快捷排版",
                tint = MaterialTheme.colorScheme.primary,
            ) {
                QuickFontSizeRow(
                    fontSize = settings.fontSize,
                    isBold = settings.fontWeightBold,
                    onFontSizeChange = { onSettingsChange(settings.copy(fontSize = it)) },
                    onBoldChange = { onSettingsChange(settings.copy(fontWeightBold = it)) },
                )
                MicroSectionDivider()
                MicroOptionPillRow(
                    title = "行距比例",
                    icon = Icons.Outlined.FormatLineSpacing,
                    options = listOf(1.5f to "紧凑", 1.85f to "标准", 2.1f to "宽松"),
                    selected = nearest(settings.lineHeight, listOf(1.5f, 1.85f, 2.1f)),
                    onSelect = { onSettingsChange(settings.copy(lineHeight = it)) },
                    tint = Color(0xFF2563EB),
                )
                MicroSectionDivider()
                MicroOptionPillRow(
                    title = "翻页效果",
                    icon = Icons.Outlined.Animation,
                    options = listOf("none" to "无", "fade" to "平移", "slide" to "滑动", "cover" to "仿真", "reveal" to "揭示"),
                    selected = settings.pageTurnEffect,
                    onSelect = {
                        onSettingsChange(settings.copy(pageTurnEffect = it))
                    },
                    tint = Color(0xFF059669),
                )
                MicroSectionDivider()
                // 翻页速度：绑定 ReaderSettings.pageTurnSpeed，经 PagedReaderPageSurface 透传给
                // PageTurner(speed = ...)。它是单页满位移的基础时长（实际时长 = speed × 剩余
                // 位移比例），值越小翻页越快，故左侧标「最快」、右侧标「最慢」。
                MicroTrackSliderRow(
                    icon = Icons.Outlined.Speed,
                    title = "翻页速度",
                    value = settings.pageTurnSpeed,
                    valueLabel = "${settings.pageTurnSpeed.toInt()} ms",
                    onValueChange = { onSettingsChange(settings.copy(pageTurnSpeed = it)) },
                    valueRange = 150f..800f,
                    steps = 12,
                    minLabel = "最快",
                    maxLabel = "最慢",
                    tint = Color(0xFF059669),
                    modifier = Modifier.testTag("reader-page-turn-speed"),
                )
            }
        }

        // 2. 高级与偏好设置导航微岛群
        item(key = "groups") {
            MicroSettingsSection(
                icon = Icons.Outlined.Tune,
                title = "高级与偏好设置",
                tint = Color(0xFF7C3AED),
            ) {
                val entries = listOf(
                    Triple(ReaderSettingsPage.TYPOGRAPHY, "排版与版式", "字号滑块、段距、页边距与字体"),
                    Triple(ReaderSettingsPage.PAGING, "翻页与按键", "点击区域、屏幕方向与自动翻页"),
                    Triple(ReaderSettingsPage.DISPLAY, "显示与页眉页脚", "沉浸模式、页眉页脚与菜单显示"),
                    Triple(ReaderSettingsPage.EYE_CARE, "护眼与阅读提醒", "护眼滤镜、色温调节与阅读节奏"),
                    Triple(ReaderSettingsPage.ADVANCED, "排版引擎兼容", "自研分页引擎模式与中文排版"),
                )
                val icons = listOf(
                    Icons.Outlined.TextFields to Color(0xFF2563EB),
                    Icons.Outlined.TouchApp to Color(0xFF059669),
                    Icons.Outlined.Devices to Color(0xFFD97706),
                    Icons.Outlined.Visibility to Color(0xFF7C3AED),
                    Icons.Outlined.Tune to Color(0xFF0891B2),
                )
                entries.forEachIndexed { index, (target, title, subtitle) ->
                    val (icon, tint) = icons[index]
                    MicroSettingsLinkRow(
                        icon = icon,
                        tint = tint,
                        title = title,
                        subtitle = subtitle,
                        onClick = { onNavigate(target) },
                    )
                    if (index != entries.lastIndex) {
                        MicroSectionDivider()
                    }
                }
            }
        }

        // 3. 底部“查看书籍信息”微岛操作保持优雅过渡
        item(key = "book-info") {
            val bookInfoInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onBookInfo()
                },
                shape = RoundedCornerShape(spec.islandRadius),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceable(bookInfoInteraction),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconPedestal(
                            icon = Icons.Outlined.Info,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 28.dp,
                            iconSize = 16.dp,
                        )
                        Column {
                            Text(
                                "查看书籍信息",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "篇幅字数、已读进度与阅读时段统计",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "进入书籍信息",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * 分节卡片全面微岛化容器。
 */
@Composable
private fun MicroSettingsSection(
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
private fun MicroSectionDivider() {
    // 微岛收敛：分割线统一委托 HairlineDivider（厚度取 ComponentSpec.dividerThickness、
    // 颜色取 outlineVariant@hairlineAlpha），消灭本文件手写的 0.5dp 孤值。
    HairlineDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
}

/**
 * 带刻度指示的平滑微导轨滑块，左侧配备 26dp 独立微彩底座。
 */
@Composable
private fun MicroTrackSliderRow(
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
private fun <T> MicroOptionPillRow(
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
private fun QuickFontSizeRow(
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
private fun MicroSettingsLinkRow(
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
private fun MicroSettingsSwitchRow(
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
 * 正文字体选择列表项微岛化，提供中文字体预览与选中微指示。
 */
@Composable
private fun MicroFontPickerSection(
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
private fun ReaderTypePreview(paper: ReaderPaperPalette, settings: ReaderSettings) {
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
private fun TypographySettings(
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

@Composable
private fun PagingSettings(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) = SettingsList {
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.TouchApp,
            title = "翻页动画与点击区域",
            tint = Color(0xFF059669),
        ) {
            MicroOptionPillRow(
                title = "阅读模式",
                icon = Icons.Outlined.AutoStories,
                options = listOf("paged" to "左右翻页", "scroll" to "上下滚动"),
                selected = settings.readerMode,
                onSelect = { onChange(settings.copy(readerMode = it)) },
                tint = Color(0xFF059669),
            )
            MicroSectionDivider()
            MicroOptionPillRow(
                title = "翻页效果",
                icon = Icons.Outlined.Animation,
                options = listOf("none" to "无", "fade" to "平移", "slide" to "滑动", "cover" to "仿真", "reveal" to "揭示"),
                selected = settings.pageTurnEffect,
                onSelect = { onChange(settings.copy(pageTurnEffect = it)) },
                tint = Color(0xFF2563EB),
            )
            MicroSectionDivider()
            // 翻页速度滑块（与快捷排版微岛同一设置项）：150..800ms，50ms 一档。
            MicroTrackSliderRow(
                icon = Icons.Outlined.Speed,
                title = "翻页速度",
                value = settings.pageTurnSpeed,
                valueLabel = "${settings.pageTurnSpeed.toInt()} ms",
                onValueChange = { onChange(settings.copy(pageTurnSpeed = it)) },
                valueRange = 150f..800f,
                steps = 12,
                minLabel = "最快",
                maxLabel = "最慢",
                tint = Color(0xFF2563EB),
                modifier = Modifier.testTag("reader-page-turn-speed-paging"),
            )
            MicroSectionDivider()
            MicroOptionPillRow(
                title = "点击区域",
                icon = Icons.Outlined.TouchApp,
                options = listOf("three-zone" to "左中右", "five-zone" to "上下扩展"),
                selected = settings.tapZoneMode,
                onSelect = { onChange(settings.copy(tapZoneMode = it)) },
                tint = Color(0xFFD97706),
            )
            MicroSectionDivider()
            MicroOptionPillRow(
                title = "屏幕方向",
                icon = Icons.Outlined.ScreenRotation,
                options = listOf("system" to "跟随系统", "portrait" to "竖屏", "landscape" to "横屏"),
                selected = settings.screenOrientation,
                onSelect = { onChange(settings.copy(screenOrientation = it)) },
                tint = Color(0xFF7C3AED),
            )
        }
    }
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.Speed,
            title = "自动翻页与按键",
            tint = Color(0xFF2563EB),
        ) {
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.TouchApp,
                tint = Color(0xFF2563EB),
                title = "音量键翻页",
                checked = settings.volumeKeyPaging,
                onCheckedChange = { onChange(settings.copy(volumeKeyPaging = it)) },
            )
            if (settings.volumeKeyPaging) {
                MicroSectionDivider()
                MicroSettingsSwitchRow(
                    icon = Icons.Outlined.TouchApp,
                    tint = Color(0xFF0891B2),
                    title = "朗读时音量键仍翻页",
                    checked = settings.volumeKeyPagingDuringTts,
                    onCheckedChange = { onChange(settings.copy(volumeKeyPagingDuringTts = it)) },
                )
            }
            MicroSectionDivider()
            MicroTrackSliderRow(
                icon = Icons.Outlined.Speed,
                title = "自动翻页速度",
                value = settings.autoPageSpeed.toFloat(),
                valueLabel = "${settings.autoPageSpeed} 档",
                onValueChange = { onChange(settings.copy(autoPageSpeed = it.toInt())) },
                valueRange = 1f..10f,
                steps = 8,
                minLabel = "慢",
                maxLabel = "快",
                tint = Color(0xFF2563EB),
            )
        }
    }
}

@Composable
private fun DisplaySettings(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) = SettingsList {
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.Devices,
            title = "屏幕显示",
            tint = Color(0xFFD97706),
        ) {
            SettingBrightnessRow(
                settings.brightness,
                { v ->
                    onChange(settings.copy(brightness = v, lastFixedBrightness = if (v >= 0) v else settings.lastFixedBrightness))
                },
                fixedDefault = settings.lastFixedBrightness,
            )
            MicroSectionDivider()
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Devices,
                tint = Color(0xFF059669),
                title = "沉浸模式",
                checked = settings.immersiveMode,
                onCheckedChange = { onChange(settings.copy(immersiveMode = it)) },
                subtitle = "隐藏系统状态栏与导航栏",
            )
            MicroSectionDivider()
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Devices,
                tint = Color(0xFF2563EB),
                title = "显示底部进度条",
                checked = settings.showProgressBar,
                onCheckedChange = { onChange(settings.copy(showProgressBar = it)) },
            )
            MicroSectionDivider()
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Devices,
                tint = Color(0xFFD97706),
                title = "屏幕常亮",
                checked = settings.keepAwake,
                onCheckedChange = { onChange(settings.copy(keepAwake = it)) },
            )
        }
    }
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.Timer,
            title = "菜单隐藏",
            tint = Color(0xFF7C3AED),
        ) {
            MicroOptionPillRow(
                title = "自动隐藏",
                icon = Icons.Outlined.Timer,
                options = AUTO_HIDE_SECOND_OPTIONS.map { it to autoHideSecondsLabel(it) },
                selected = nearestAutoHideOption(settings.autoHideSeconds),
                onSelect = { onChange(settings.copy(autoHideSeconds = it)) },
                tint = Color(0xFF7C3AED),
                subtitle = "0 秒 = 不定时隐藏",
            )
        }
    }
    item {
        MicroSettingsSection(
            icon = Icons.AutoMirrored.Outlined.ViewSidebar,
            title = "页眉页脚",
            tint = Color(0xFF0891B2),
        ) {
            MicroSettingsSwitchRow(
                icon = Icons.AutoMirrored.Outlined.ViewSidebar,
                tint = Color(0xFF0891B2),
                title = "显示安静阅读信息",
                checked = settings.showReaderInfo,
                onCheckedChange = { onChange(settings.copy(showReaderInfo = it)) },
            )
            if (settings.showReaderInfo) {
                MicroSectionDivider()
                HeaderFooterPicker("页眉左侧", settings.headerLeft) { onChange(settings.copy(headerLeft = it)) }
                MicroSectionDivider()
                HeaderFooterPicker("页眉右侧", settings.headerRight) { onChange(settings.copy(headerRight = it)) }
                MicroSectionDivider()
                HeaderFooterPicker("页脚左侧", settings.footerLeft) { onChange(settings.copy(footerLeft = it)) }
                MicroSectionDivider()
                HeaderFooterPicker("页脚右侧", settings.footerRight) { onChange(settings.copy(footerRight = it)) }
            }
        }
    }
}

private fun formatEyeCareMinute(minute: Int): String {
    val clamped = minute.coerceIn(0, 1439)
    return "%02d:%02d".format(clamped / 60, clamped % 60)
}

@Composable
private fun EyeCareTimeRow(
    label: String,
    minute: Int,
    onChange: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    MicroSettingsLinkRow(
        icon = Icons.Outlined.Timer,
        tint = Color(0xFF7C3AED),
        title = label,
        subtitle = "点击调整生效时刻",
        value = formatEyeCareMinute(minute),
        onClick = {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, minute / 60)
            cal.set(Calendar.MINUTE, minute % 60)
            android.app.TimePickerDialog(
                ctx,
                { _, h, m -> onChange((h * 60 + m).coerceIn(0, 1439)) },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true,
            ).show()
        },
    )
}

@Composable
private fun EyeCareSettings(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) = SettingsList {
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.Visibility,
            title = "护眼滤镜",
            tint = Color(0xFF7C3AED),
        ) {
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Visibility,
                tint = Color(0xFF7C3AED),
                title = "开启护眼滤镜",
                checked = settings.eyeCareFilterEnabled,
                onCheckedChange = { onChange(settings.copy(eyeCareFilterEnabled = it)) },
            )
            MicroSectionDivider()
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Timer,
                tint = Color(0xFF2563EB),
                title = "夜间自动开启",
                checked = settings.eyeCareScheduleEnabled,
                onCheckedChange = { onChange(settings.copy(eyeCareScheduleEnabled = it)) },
            )
            if (settings.eyeCareScheduleEnabled) {
                MicroSectionDivider()
                EyeCareTimeRow(
                    label = "开始时间",
                    minute = settings.eyeCareStartMinute,
                    onChange = { onChange(settings.copy(eyeCareStartMinute = it)) },
                )
                MicroSectionDivider()
                EyeCareTimeRow(
                    label = "结束时间",
                    minute = settings.eyeCareEndMinute,
                    onChange = { onChange(settings.copy(eyeCareEndMinute = it)) },
                )
            }
            if (settings.eyeCareFilterEnabled || settings.eyeCareScheduleEnabled) {
                MicroSectionDivider()
                MicroTrackSliderRow(
                    icon = Icons.Outlined.Colorize,
                    title = "色温",
                    value = settings.eyeCareTemperature.toFloat(),
                    valueLabel = "${settings.eyeCareTemperature} K",
                    onValueChange = { onChange(settings.copy(eyeCareTemperature = ((it / 100).toInt() * 100))) },
                    valueRange = 2600f..5500f,
                    steps = 28,
                    minLabel = "暖",
                    maxLabel = "冷",
                    tint = Color(0xFFD97706),
                )
                MicroSectionDivider()
                MicroTrackSliderRow(
                    icon = Icons.Outlined.BrightnessMedium,
                    title = "强度",
                    value = settings.eyeCareIntensity.toFloat(),
                    valueLabel = "${settings.eyeCareIntensity}%",
                    onValueChange = { onChange(settings.copy(eyeCareIntensity = it.toInt())) },
                    valueRange = 0f..100f,
                    steps = 19,
                    minLabel = "0%",
                    maxLabel = "100%",
                    tint = Color(0xFF7C3AED),
                )
                MicroSectionDivider()
                MicroSettingsSwitchRow(
                    icon = Icons.Outlined.Visibility,
                    tint = Color(0xFF0891B2),
                    title = "OLED 纯黑兼容",
                    checked = settings.eyeCareOledBlackCompat,
                    onCheckedChange = { onChange(settings.copy(eyeCareOledBlackCompat = it)) },
                    subtitle = "夜读模式下保持纯黑像素不被暖色染色，更省电",
                )
            }
        }
    }
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.Timer,
            title = "阅读提醒",
            tint = Color(0xFF059669),
        ) {
            MicroTrackSliderRow(
                icon = Icons.Outlined.Timer,
                title = "护眼提醒",
                value = settings.eyeCareReminderMinutes.toFloat(),
                valueLabel = "${settings.eyeCareReminderMinutes} 分钟",
                onValueChange = { onChange(settings.copy(eyeCareReminderMinutes = it.toInt())) },
                valueRange = 5f..60f,
                steps = 54,
                minLabel = "5m",
                maxLabel = "60m",
                tint = Color(0xFF059669),
            )
            MicroSectionDivider()
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Timer,
                tint = Color(0xFF2563EB),
                title = "阅读节奏提示",
                checked = settings.readingRhythmReminderEnabled,
                onCheckedChange = { onChange(settings.copy(readingRhythmReminderEnabled = it)) },
                subtitle = "适时提醒休息眼睛和身体",
            )
            if (settings.readingRhythmReminderEnabled) {
                MicroSectionDivider()
                MicroTrackSliderRow(
                    icon = Icons.Outlined.Timer,
                    title = "提示间隔",
                    value = settings.readingRhythmReminderMinutes.toFloat(),
                    valueLabel = "${settings.readingRhythmReminderMinutes} 分钟",
                    onValueChange = { onChange(settings.copy(readingRhythmReminderMinutes = it.toInt())) },
                    valueRange = 5f..60f,
                    steps = 54,
                    minLabel = "5m",
                    maxLabel = "60m",
                    tint = Color(0xFF2563EB),
                )
            }
        }
    }
}

@Composable
private fun AdvancedSettings(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) = SettingsList {
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.Tune,
            title = "分页兼容模式",
            tint = Color(0xFF0891B2),
            description = "新版本默认开启自研分页引擎。特定书籍分页异常时，可临时切回“自动”或“关闭”。",
        ) {
            MicroOptionPillRow(
                title = "TXT 分页兼容模式",
                icon = Icons.Outlined.Tune,
                options = listOf("off" to "关闭", "auto" to "自动", "on" to "强制"),
                selected = settings.pagerEngineMode,
                onSelect = { onChange(settings.copy(pagerEngineMode = it)) },
                tint = Color(0xFF0891B2),
            )
            MicroSectionDivider()
            MicroOptionPillRow(
                title = "EPUB 分页兼容模式",
                icon = Icons.Outlined.Tune,
                options = listOf("off" to "关闭", "auto" to "自动", "on" to "强制"),
                selected = settings.epubPagerEngineMode,
                onSelect = { onChange(settings.copy(epubPagerEngineMode = it)) },
                tint = Color(0xFF2563EB),
            )
        }
    }
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.TextFields,
            title = "文字处理",
            tint = Color(0xFF059669),
        ) {
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.TextFields,
                tint = Color(0xFF059669),
                title = "中文排版优化",
                checked = settings.chineseTypography,
                onCheckedChange = { onChange(settings.copy(chineseTypography = it)) },
                subtitle = "优化中文标点、行首行尾与段落显示",
            )
        }
    }
}

@Composable
private fun SettingsList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

private fun nearest(value: Float, options: List<Float>): Float = options.minByOrNull { abs(it - value) } ?: options.first()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderFooterPicker(
    label: String,
    selected: HeaderFooterItem,
    onSelect: (HeaderFooterItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val layout = LocalLayoutTokens.current
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
