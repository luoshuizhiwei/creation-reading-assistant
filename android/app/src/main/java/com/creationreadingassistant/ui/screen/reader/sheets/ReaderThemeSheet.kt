package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.PerBookOverrides
import com.creationreadingassistant.data.settings.ReaderOverrideKey
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.ReaderSettingsScope
import com.creationreadingassistant.ui.components.CompactSliderRow
import com.creationreadingassistant.ui.components.CompactSwitchRow
import com.creationreadingassistant.ui.components.SettingBrightnessRow
import com.creationreadingassistant.ui.screen.reader.ReaderPageBackHandler
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.ReaderPaperOptions
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.paperPalette
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

private fun paperToneSubtitle(key: String): String = when (key) {
    "white" -> "清冷墨香 · 纯净白纸"
    "warm" -> "温润米黄 · 舒缓暖纸"
    "parchment" -> "典雅泛黄 · 仿古羊皮"
    "celadon" -> "青瓷素净 · 水墨雅灰"
    "green" -> "草木柔和 · 护眼青绿"
    "sepia_dark" -> "沉静暮色 · 暖棕护眼"
    "ink_night" -> "水墨幽深 · 墨青夜读"
    "night" -> "暗夜纯粹 · 极黑沉浸"
    "follow" -> "跟随系统 · 智能明暗"
    else -> "经典阅读纸面"
}

private fun paperTonePoeticSample(key: String): Pair<String, String> = when (key) {
    "white" -> "窗前竹影摇阶砌" to "墨染霜绡展素心"
    "warm" -> "灯下清芬翻贝叶" to "旧墨微黄泛古香"
    "parchment" -> "案上微黄凝古卷" to "墨痕如铁著春秋"
    "celadon" -> "青瓷素碗浮新茗" to "翠竹虚窗弄晚凉"
    "green" -> "林泉入座清肌骨" to "山翠迎眸解目疲"
    "sepia_dark" -> "暮色闲披棕椟卷" to "炉香初泛静幽思"
    "ink_night" -> "墨色空濛涵宿雾" to "寒光淡荡点寒星"
    "night" -> "静夜寒星沉万籁" to "墨玉凝光照字明"
    "follow" -> "天光向晚随时换" to "云影随风任卷舒"
    else -> "天地玄黄生浩气" to "纸墨相传见古今"
}

/** READER-PAGES-v1 P2：外观页分类。分类切换只替换控制区，状态提升到页级保存。 */
private enum class ReaderThemePageCategory(val label: String) {
    FONT("字体"),
    LAYOUT("排版"),
    PAPER("纸色"),
}

/**
 * 阅读外观**预览调节页**（READER-PAGES-v1 P2）。
 *
 * 与旧外观底部弹层的差异：
 * - 满高页面：顶栏「返回 / 阅读外观 / 完成」——「完成」表示结束调节，设置本就即时生效，
 *   不伪装成一次事务提交；
 * - 上方**真实正文预览**：书名 + 当前章 + 开页时捕获的 ≤800 字符稳定片段，按当前
 *   字号/行距/纸色渲染；只消费设置样式，不加载文件、不启动分页、不写进度；
 * - 亮度保留独立紧凑调节行（常驻）；下方「字体 / 排版 / 纸色」分类切换只替换控制区；
 * - 作用范围「仅本书 / 全局」保持可见，沿 [onScopeChange] 原路径；
 * - 「完整阅读设置」进入 SETTINGS 内层：Back 回本页并保留分类/预览（[innerSettingsOpen]）。
 *
 * 横屏或可用高度不足时预览折叠为一行摘要（可展开），释放控制区（合同 §4）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemePage(
    settings: ReaderSettings,
    appDark: Boolean,
    scope: ReaderSettingsScope,
    onScopeChange: (ReaderSettingsScope) -> Unit,
    overrides: PerBookOverrides,
    previewBookTitle: String,
    previewChapterTitle: String,
    previewText: String,
    onBackground: (String) -> Unit,
    onFontSize: (Float) -> Unit,
    onBrightness: (Int) -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onClose: () -> Unit,
    innerSettingsOpen: Boolean,
    onInnerSettingsOpenChange: (Boolean) -> Unit,
    innerSettings: @Composable () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)

    // 分类选择：页级保存，内层设置往返后保留。声明必须在内层 early-return **之前**：
    // rememberSaveable 在离开组合（条件分支卸载）时即注销、重进不还原，放早一行才
    // 在整个 THEME 会话期间持续在组合内。
    var category by rememberSaveable { mutableStateOf(ReaderThemePageCategory.FONT.name) }

    // THEME 的 Back 链（唯一 BackHandler）：内层设置 → 本页；本页根 → 关附页回正文。
    ReaderPageBackHandler {
        if (innerSettingsOpen) onInnerSettingsOpenChange(false) else onClose()
    }
    if (innerSettingsOpen) {
        // THEME→SETTINGS 内层（合同 P2）：Back 回 THEME 并保留分类/预览。
        innerSettings()
        return
    }

    ReaderSheetScaffold(
        title = "阅读外观",
        fullScreen = true,
        onBack = onClose,
        trailing = {
            TextButton(onClick = onClose) { Text("完成") }
        },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 横屏或可用高度不足：预览折叠为一行摘要，释放控制区（合同 §4）。
            val compactPreview = maxHeight < 480.dp || maxWidth > maxHeight
            val previewHeight = if (compactPreview) {
                160.dp
            } else {
                (maxHeight / 3f).coerceIn(128.dp, 240.dp)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 0. 作用范围：仅本书 / 全局（保持可见，沿原 onScopeChange 路径）。
                SettingsScopeSection(scope = scope, onScopeChange = onScopeChange)

                // 1. 真实正文预览（特色结构：真实书名/章节/已加载文字片段）。
                if (compactPreview) {
                    CompactThemePreviewSummary(
                        previewBookTitle = previewBookTitle,
                        previewChapterTitle = previewChapterTitle,
                        previewHeight = previewHeight,
                        settings = settings,
                        appDark = appDark,
                        previewText = previewText,
                    )
                } else {
                    ThemePreviewSurface(
                        height = previewHeight,
                        previewBookTitle = previewBookTitle,
                        previewChapterTitle = previewChapterTitle,
                        previewText = previewText,
                        settings = settings,
                        appDark = appDark,
                    )
                }

                // 2. 亮度：独立紧凑调节行（不随分类切换，合同 P2）。
                SettingBrightnessRow(
                    brightness = settings.brightness,
                    fixedDefault = settings.lastFixedBrightness,
                    onBrightnessChange = onBrightness,
                )

                // 3. 分类导轨：字体 / 排版 / 纸色。
                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ReaderThemePageCategory.entries.forEach { entry ->
                            val selected = entry.name == category
                            val interaction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = {
                                    if (!selected) {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        category = entry.name
                                    }
                                },
                                shape = PillShape,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                border = if (selected) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                } else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .bounceable(interaction),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = entry.label,
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

                // 4. 分类内容：切换只替换控制区，120ms 淡入淡出（减少动态效果时瞬时）。
                Crossfade(
                    targetState = category,
                    animationSpec = if (reducedMotion) tween(0) else tween(120),
                    label = "theme-category",
                ) { target ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (target) {
                            ReaderThemePageCategory.FONT.name -> {
                                CompactSliderRow(
                                    icon = Icons.Outlined.FormatSize,
                                    title = "字号",
                                    value = settings.fontSize,
                                    valueLabel = "${settings.fontSize.toInt()} 号",
                                    onValueChange = onFontSize,
                                    valueRange = 12f..40f,
                                    steps = 27,
                                    minLabel = "小",
                                    maxLabel = "大",
                                )
                                CompactSwitchRow(
                                    icon = Icons.Outlined.FormatBold,
                                    tint = MaterialTheme.colorScheme.primary,
                                    title = "加粗显示",
                                    checked = settings.fontWeightBold,
                                    onCheckedChange = { onSettingsChange(settings.copy(fontWeightBold = it)) },
                                    subtitle = if (overrides.containsKey(ReaderOverrideKey.FONT_SIZE) ||
                                        overrides.containsKey(ReaderOverrideKey.FONT_BOLD)
                                    ) {
                                        "本书覆盖"
                                    } else {
                                        null
                                    },
                                )
                            }

                            ReaderThemePageCategory.LAYOUT.name -> {
                                MicroOptionPillRow(
                                    title = "行距比例",
                                    icon = Icons.Outlined.FormatLineSpacing,
                                    options = listOf(1.5f to "紧凑", 1.85f to "标准", 2.1f to "宽松"),
                                    selected = nearest(settings.lineHeight, listOf(1.5f, 1.85f, 2.1f)),
                                    onSelect = { onSettingsChange(settings.copy(lineHeight = it)) },
                                    tint = Color(0xFF2563EB),
                                    subtitle = if (overrides.containsKey(ReaderOverrideKey.LINE_HEIGHT)) "本书" else null,
                                )
                                MicroOptionPillRow(
                                    title = "翻页效果",
                                    icon = Icons.Outlined.Animation,
                                    options = listOf(
                                        "none" to "无",
                                        "fade" to "淡入",
                                        "slide" to "平移",
                                        "cover" to "仿真",
                                        "reveal" to "揭示",
                                    ),
                                    selected = settings.pageTurnEffect,
                                    onSelect = { onSettingsChange(settings.copy(pageTurnEffect = it)) },
                                    tint = Color(0xFF059669),
                                    subtitle = if (overrides.containsKey(ReaderOverrideKey.PAGE_TURN_EFFECT)) "本书" else null,
                                )
                            }

                            ReaderThemePageCategory.PAPER.name -> {
                                // 动态取色 / 深色跟随精致微胶囊导轨（沿用原外观导轨）。
                                ThemePaperModeRail(
                                    background = settings.background,
                                    appDark = appDark,
                                    isNight = !paperPalette(settings.background, appDark).isLight,
                                    onBackground = onBackground,
                                )
                                // 色盘圆润微岛卡片网格（沿用原外观网格）。
                                ThemePaperGrid(
                                    background = settings.background,
                                    appDark = appDark,
                                    onBackground = onBackground,
                                )
                            }
                        }
                    }
                }

                // 5. 完整阅读设置：进入 SETTINGS 内层（Back 回本页，保留分类/预览）。
                MicroSettingsLinkRow(
                    icon = Icons.Outlined.Tune,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "完整阅读设置",
                    subtitle = "翻页与按键、显示与页眉页脚、护眼与提醒、高级兼容",
                    onClick = { onInnerSettingsOpenChange(true) },
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** 横屏/矮屏：预览折叠为一行摘要，点击页内展开（合同 §4）。 */
@Composable
private fun CompactThemePreviewSummary(
    previewBookTitle: String,
    previewChapterTitle: String,
    previewHeight: Dp,
    settings: ReaderSettings,
    appDark: Boolean,
    previewText: String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "$previewBookTitle · ${previewChapterTitle.ifBlank { "正文" }} · 正文预览",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起预览" else "展开预览",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(120)) + expandVertically(tween(120)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
        ) {
            ThemePreviewSurface(
                height = previewHeight,
                previewBookTitle = previewBookTitle,
                previewChapterTitle = previewChapterTitle,
                previewText = previewText,
                settings = settings,
                appDark = appDark,
            )
        }
    }
}

/**
 * 真实正文预览：纸面 bg/fg 随当前纸张；正文按现有字号/行距/加粗渲染并内部滚动
 * （大字时不裁剪文本）。无正文显示真实空态，不编造样张。
 */
@Composable
private fun ThemePreviewSurface(
    height: Dp,
    previewBookTitle: String,
    previewChapterTitle: String,
    previewText: String,
    settings: ReaderSettings,
    appDark: Boolean,
) {
    val palette = paperPalette(settings.background, appDark)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(16.dp),
        color = palette.bg,
        contentColor = palette.fg,
        border = BorderStroke(1.dp, palette.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = previewBookTitle,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = DisplayFontFamily),
                color = palette.fgMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = previewChapterTitle.ifBlank { "正文" },
                style = MaterialTheme.typography.labelSmall,
                color = palette.fgMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(
                color = palette.outlineVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            if (previewText.isBlank()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "正文尚未加载，加载完成后此处显示真实内容预览",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.fgMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = settings.fontSize.sp,
                            lineHeight = (settings.fontSize * settings.lineHeight).sp,
                            fontWeight = if (settings.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = palette.fg,
                    )
                }
            }
            Text(
                text = "内容预览 · 实际排版以正文为准",
                style = MaterialTheme.typography.labelSmall,
                color = palette.fgMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 跟随系统 / 日间 / 夜读微胶囊导轨（自原外观弹层原样保留）。 */
@Composable
private fun ThemePaperModeRail(
    background: String,
    appDark: Boolean,
    isNight: Boolean,
    onBackground: (String) -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val isFollow = background == "follow"
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ThemePaperModeChip(
                label = "跟随系统",
                icon = Icons.Outlined.BrightnessAuto,
                selected = isFollow,
                onClick = { if (!isFollow) onBackground("follow") },
                modifier = Modifier.weight(1f),
            )
            val isDayActive = !isNight && !isFollow
            ThemePaperModeChip(
                label = "日间明朗",
                icon = Icons.Outlined.LightMode,
                selected = isDayActive,
                onClick = { if (!isDayActive) onBackground("white") },
                modifier = Modifier.weight(1f),
            )
            val isNightActive = isNight && !isFollow
            ThemePaperModeChip(
                label = "夜读沉浸",
                icon = Icons.Outlined.DarkMode,
                selected = isNightActive,
                onClick = { if (!isNightActive) onBackground("night") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemePaperModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = {
            if (!selected) {
                haptic(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        },
        shape = PillShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null,
        modifier = modifier
            .height(36.dp)
            .bounceable(interaction),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 纸色卡片网格（自原外观弹层原样保留：样张/描述/批注色点）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePaperGrid(
    background: String,
    appDark: Boolean,
    onBackground: (String) -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReaderPaperOptions.forEach { option ->
            val palette = paperPalette(option.key, appDark)
            val selected = option.key == background
            val cardInteraction = remember { MutableInteractionSource() }

            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onBackground(option.key)
                },
                modifier = Modifier
                    .weight(1f)
                    .bounceable(cardInteraction),
                shape = RoundedCornerShape(16.dp),
                color = palette.bg,
                contentColor = palette.fg,
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) palette.accent else palette.outlineVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 顶行：标题与选中徽章
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = DisplayFontFamily),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = palette.fg,
                        )

                        if (selected) {
                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(palette.accent.copy(alpha = 0.22f), shape = CircleShape),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(palette.bg, shape = CircleShape)
                                        .border(1.5.dp, palette.accent, shape = CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = "已选择",
                                        tint = palette.accent,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(palette.panelStrong, shape = CircleShape)
                                    .border(1.dp, palette.outlineVariant.copy(alpha = 0.65f), shape = CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Aa",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.fgMuted,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    // 副标题：意境纸墨描述
                    Text(
                        text = paperToneSubtitle(option.key),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.fgMuted,
                        maxLines = 1,
                    )

                    // 样张文字微岛展示
                    val sample = paperTonePoeticSample(option.key)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.panel.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = sample.first,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = palette.fg,
                                maxLines = 1,
                            )
                            Text(
                                text = sample.second,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = palette.fgMuted,
                                maxLines = 1,
                            )
                        }
                    }

                    // 底行：随纸 5 色微小批注色点预览
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        palette.highlightColors.take(4).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .padding(start = 3.dp)
                                    .size(7.dp)
                                    .background(color, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}
