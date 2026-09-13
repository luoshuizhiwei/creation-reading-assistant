package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.settings.PerBookOverrides
import com.creationreadingassistant.data.settings.ReaderOverrideKey
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.ReaderSettingsScope
import com.creationreadingassistant.data.settings.ReadingPreset
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@Composable
internal fun SettingsSheet(
    paper: ReaderPaperPalette,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onBookInfo: () -> Unit,
    /** R3-P1：修改作用范围（本书 / 全局）。 */
    scope: ReaderSettingsScope = ReaderSettingsScope.BOOK,
    onScopeChange: (ReaderSettingsScope) -> Unit = {},
    /** R3-P1：本书显式覆盖项，用于标注「哪一项来自本书」。 */
    overrides: PerBookOverrides = emptyMap(),
    /** R3-P1：全局设置（不含本书覆盖），用于展示「跟随全局」时的实际取值。 */
    globalSettings: ReaderSettings = ReaderSettings(),
    onClearOverride: (ReaderOverrideKey) -> Unit = {},
    onClearAllOverrides: () -> Unit = {},
    onApplyPreset: (ReadingPreset) -> Unit = {},
    onResetGlobalDefaults: () -> Unit = {},
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
                    scope = scope,
                    onScopeChange = onScopeChange,
                    overrides = overrides,
                    globalSettings = globalSettings,
                    onClearOverride = onClearOverride,
                    onClearAllOverrides = onClearAllOverrides,
                    onApplyPreset = onApplyPreset,
                    onResetGlobalDefaults = onResetGlobalDefaults,
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
    scope: ReaderSettingsScope,
    onScopeChange: (ReaderSettingsScope) -> Unit,
    overrides: PerBookOverrides,
    globalSettings: ReaderSettings,
    onClearOverride: (ReaderOverrideKey) -> Unit,
    onClearAllOverrides: () -> Unit,
    onApplyPreset: (ReadingPreset) -> Unit,
    onResetGlobalDefaults: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 0. R3-P1 作用范围：书内调整默认只改当前这本书，可显式切换到全局
        item(key = "settings-scope") {
            SettingsScopeSection(scope = scope, onScopeChange = onScopeChange)
        }

        // 0b. R3-P1 本书覆盖明细 + 预设 + 恢复默认
        item(key = "book-overrides") {
            BookOverrideSection(
                scope = scope,
                overrides = overrides,
                settings = settings,
                globalSettings = globalSettings,
                onClearOverride = onClearOverride,
                onClearAllOverrides = onClearAllOverrides,
                onApplyPreset = onApplyPreset,
                onResetGlobalDefaults = onResetGlobalDefaults,
            )
        }
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
