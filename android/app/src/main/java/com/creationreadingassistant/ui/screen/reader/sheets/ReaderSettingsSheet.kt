package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import com.creationreadingassistant.data.settings.HeaderFooterItem
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.ui.screen.reader.AUTO_HIDE_SECOND_OPTIONS
import com.creationreadingassistant.ui.screen.reader.autoHideSecondsLabel
import com.creationreadingassistant.ui.screen.reader.nearestAutoHideOption
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.ReaderFontPickerRow
import com.creationreadingassistant.ui.components.SettingBrightnessRow
import com.creationreadingassistant.ui.components.SettingLinkRow
import com.creationreadingassistant.ui.components.SettingSegmentedRow
import com.creationreadingassistant.ui.components.SettingSliderRow
import com.creationreadingassistant.ui.components.SettingSwitchRow
import com.creationreadingassistant.ui.components.SettingsGroup
import com.creationreadingassistant.ui.components.SettingsSection
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.rememberReducedMotion
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
    val layout = LocalLayoutTokens.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(layout.cardPadding),
        verticalArrangement = Arrangement.spacedBy(layout.contentGap),
    ) {
        item(key = "groups") {
            SettingsSection(title = "更多设置") {
                val entries = listOf(
                    ReaderSettingsPage.TYPOGRAPHY to "字号、行距、段距和页边距",
                    ReaderSettingsPage.PAGING to "翻页方式、点击区域和自动翻页",
                    ReaderSettingsPage.DISPLAY to "沉浸、页眉页脚和菜单显示",
                    ReaderSettingsPage.EYE_CARE to "滤镜、色温与阅读提醒",
                    ReaderSettingsPage.ADVANCED to "分页兼容与中文排版",
                )
                entries.forEachIndexed { index, (target, subtitle) ->
                    SettingLinkRow(target.title, subtitle = subtitle, onClick = { onNavigate(target) })
                    if (index != entries.lastIndex) SectionDivider()
                }
            }
        }
        item(key = "book-info") {
            OutlinedButton(onClick = onBookInfo, modifier = Modifier.fillMaxWidth()) {
                Text("查看书籍信息")
            }
        }
    }
}

@Composable
private fun ReaderTypePreview(paper: ReaderPaperPalette, settings: ReaderSettings) {
    val size = settings.fontSize.coerceIn(12f, 40f)
    val lineHeightSp = (size * settings.lineHeight).sp
    val paragraphDp: Dp = with(LocalDensity.current) {
        (size * 0.4f * settings.paragraphSpacing).sp.toDp()
    }
    val weight = if (settings.fontWeightBold) FontWeight.Bold else FontWeight.Normal
    SectionCard(contentPadding = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(paper.bg)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "排版预览",
                style = MaterialTheme.typography.labelMedium,
                color = paper.fgMuted,
            )
            Text(
                "窗前的纸页安静展开，每一个字都落在恰好的位置，行与行之间留出呼吸的余地，读起来就不费眼睛。",
                color = paper.fg,
                fontSize = size.sp,
                lineHeight = lineHeightSp,
                fontWeight = weight,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(paragraphDp))
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
        SettingsSection("文字") {
            SettingSliderRow(
                title = "字号",
                value = settings.fontSize,
                valueLabel = "${settings.fontSize.toInt()} 号",
                onValueChange = { onChange(settings.copy(fontSize = it)) },
                valueRange = 12f..40f,
                steps = 27,
            )
            SectionDivider()
            ReaderFontPickerRow(settings, onChange)
            SectionDivider()
            SettingSwitchRow("粗体文字", settings.fontWeightBold, { onChange(settings.copy(fontWeightBold = it)) })
            SectionDivider()
            SettingBrightnessRow(
                brightness = settings.brightness,
                onBrightnessChange = { onChange(settings.copy(brightness = it)) },
                fixedDefault = settings.lastFixedBrightness,
            )
        }
    }
    item {
        SettingsSection("行文节奏") {
            SettingSegmentedRow(
                title = "行距",
                options = listOf(1.5f to "紧凑", 1.85f to "标准", 2.1f to "宽松"),
                selected = nearest(settings.lineHeight, listOf(1.5f, 1.85f, 2.1f)),
                onSelect = { onChange(settings.copy(lineHeight = it)) },
            )
            SectionDivider()
            SettingSegmentedRow(
                title = "段距",
                options = listOf(0.8f to "小", 1.1f to "中", 1.5f to "大"),
                selected = nearest(settings.paragraphSpacing, listOf(0.8f, 1.1f, 1.5f)),
                onSelect = { onChange(settings.copy(paragraphSpacing = it)) },
            )
            SectionDivider()
            SettingSliderRow(
                title = "页边距",
                value = settings.pageMargin,
                valueLabel = "${settings.pageMargin.toInt()} dp",
                valueRange = 10f..42f,
                steps = 31,
                onValueChange = { onChange(settings.copy(pageMargin = it)) },
            )
        }
    }
}

@Composable
private fun PagingSettings(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) = SettingsList {
    item {
        SettingsSection("阅读方式") {
            SettingSegmentedRow("阅读模式", listOf("paged" to "左右翻页", "scroll" to "上下滚动"), settings.readerMode, { onChange(settings.copy(readerMode = it)) })
            SectionDivider()
            SettingSegmentedRow(
                "翻页效果",
                listOf("none" to "无", "fade" to "淡入", "slide" to "滑动", "cover" to "覆盖"),
                settings.pageTurnEffect,
                { onChange(settings.copy(pageTurnEffect = it)) },
            )
            SectionDivider()
            SettingSegmentedRow("点击区域", listOf("three-zone" to "左中右", "five-zone" to "上下扩展"), settings.tapZoneMode, { onChange(settings.copy(tapZoneMode = it)) })
            SectionDivider()
            SettingSegmentedRow(
                "屏幕方向",
                listOf("system" to "跟随系统", "portrait" to "竖屏", "landscape" to "横屏"),
                settings.screenOrientation,
                { onChange(settings.copy(screenOrientation = it)) },
            )
        }
    }
    item {
        SettingsSection("按键与自动翻页") {
            SettingSwitchRow("音量键翻页", settings.volumeKeyPaging, { onChange(settings.copy(volumeKeyPaging = it)) })
            if (settings.volumeKeyPaging) {
                SectionDivider()
                SettingSwitchRow("朗读时音量键仍翻页", settings.volumeKeyPagingDuringTts, { onChange(settings.copy(volumeKeyPagingDuringTts = it)) })
            }
            SectionDivider()
            SettingSliderRow(
                "自动翻页速度",
                settings.autoPageSpeed.toFloat(),
                "${settings.autoPageSpeed} 档",
                { onChange(settings.copy(autoPageSpeed = it.toInt())) },
                valueRange = 1f..10f,
                steps = 8,
            )
        }
    }
}

@Composable
private fun DisplaySettings(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) = SettingsList {
    item {
        SettingsSection("屏幕显示") {
            SettingBrightnessRow(
                settings.brightness,
                { onChange(settings.copy(brightness = it)) },
                fixedDefault = settings.lastFixedBrightness,
            )
            SectionDivider()
            SettingSwitchRow("沉浸模式", settings.immersiveMode, { onChange(settings.copy(immersiveMode = it)) })
            SectionDivider()
            SettingSwitchRow("显示底部进度条", settings.showProgressBar, { onChange(settings.copy(showProgressBar = it)) })
            SectionDivider()
            SettingSwitchRow("屏幕常亮", settings.keepAwake, { onChange(settings.copy(keepAwake = it)) })
        }
    }
    item {
        SettingsSection("菜单") {
            SettingSegmentedRow(
                "自动隐藏",
                AUTO_HIDE_SECOND_OPTIONS.map { it to autoHideSecondsLabel(it) },
                nearestAutoHideOption(settings.autoHideSeconds),
                { onChange(settings.copy(autoHideSeconds = it)) },
                subtitle = "0 秒 = 不定时隐藏",
            )
        }
    }
    item {
        SettingsSection("页眉页脚") {
            SettingSwitchRow("显示安静阅读信息", settings.showReaderInfo, { onChange(settings.copy(showReaderInfo = it)) })
            if (settings.showReaderInfo) {
                SectionDivider()
                HeaderFooterPicker("页眉左侧", settings.headerLeft) { onChange(settings.copy(headerLeft = it)) }
                SectionDivider()
                HeaderFooterPicker("页眉右侧", settings.headerRight) { onChange(settings.copy(headerRight = it)) }
                SectionDivider()
                HeaderFooterPicker("页脚左侧", settings.footerLeft) { onChange(settings.copy(footerLeft = it)) }
                SectionDivider()
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
    SettingLinkRow(
        title = label,
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
        SettingsSection("护眼滤镜") {
            SettingSwitchRow("开启护眼滤镜", settings.eyeCareFilterEnabled, { onChange(settings.copy(eyeCareFilterEnabled = it)) })
            SectionDivider()
            SettingSwitchRow(
                "夜间自动开启",
                settings.eyeCareScheduleEnabled,
                { onChange(settings.copy(eyeCareScheduleEnabled = it)) },
            )
            if (settings.eyeCareScheduleEnabled) {
                SectionDivider()
                EyeCareTimeRow(
                    label = "开始时间",
                    minute = settings.eyeCareStartMinute,
                    onChange = { onChange(settings.copy(eyeCareStartMinute = it)) },
                )
                SectionDivider()
                EyeCareTimeRow(
                    label = "结束时间",
                    minute = settings.eyeCareEndMinute,
                    onChange = { onChange(settings.copy(eyeCareEndMinute = it)) },
                )
            }
            if (settings.eyeCareFilterEnabled || settings.eyeCareScheduleEnabled) {
                SectionDivider()
                SettingSliderRow("色温", settings.eyeCareTemperature.toFloat(), "${settings.eyeCareTemperature} K", { onChange(settings.copy(eyeCareTemperature = ((it / 100).toInt() * 100))) }, valueRange = 2600f..5500f, steps = 28)
                SectionDivider()
                SettingSliderRow("强度", settings.eyeCareIntensity.toFloat(), "${settings.eyeCareIntensity}%", { onChange(settings.copy(eyeCareIntensity = it.toInt())) }, valueRange = 0f..100f, steps = 19)
                SectionDivider()
                SettingSwitchRow(
                    "OLED 纯黑兼容",
                    settings.eyeCareOledBlackCompat,
                    { onChange(settings.copy(eyeCareOledBlackCompat = it)) },
                    subtitle = "夜读模式下保持纯黑像素不被暖色染色，更省电",
                )
            }
        }
    }
    item {
        SettingsSection("阅读提醒") {
            SettingSliderRow("护眼提醒", settings.eyeCareReminderMinutes.toFloat(), "${settings.eyeCareReminderMinutes} 分钟", { onChange(settings.copy(eyeCareReminderMinutes = it.toInt())) }, valueRange = 5f..60f, steps = 54)
            SectionDivider()
            SettingSwitchRow("阅读节奏提示", settings.readingRhythmReminderEnabled, { onChange(settings.copy(readingRhythmReminderEnabled = it)) }, subtitle = "适时提醒休息眼睛和身体")
            if (settings.readingRhythmReminderEnabled) {
                SectionDivider()
                SettingSliderRow("提示间隔", settings.readingRhythmReminderMinutes.toFloat(), "${settings.readingRhythmReminderMinutes} 分钟", { onChange(settings.copy(readingRhythmReminderMinutes = it.toInt())) }, valueRange = 5f..60f, steps = 54)
            }
        }
    }
}

@Composable
private fun AdvancedSettings(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) = SettingsList {
    item {
        SettingsSection(
            title = "分页兼容",
            description = "新版本默认开启自研分页引擎。特定书籍分页异常时，可临时切回“自动”或“关闭”。",
        ) {
            SettingSegmentedRow("TXT 分页兼容模式", listOf("off" to "关闭", "auto" to "自动", "on" to "强制"), settings.pagerEngineMode, { onChange(settings.copy(pagerEngineMode = it)) })
            SectionDivider()
            SettingSegmentedRow("EPUB 分页兼容模式", listOf("off" to "关闭", "auto" to "自动", "on" to "强制"), settings.epubPagerEngineMode, { onChange(settings.copy(epubPagerEngineMode = it)) })
        }
    }
    item {
        SettingsSection("文字处理") {
            SettingSwitchRow("中文排版优化", settings.chineseTypography, { onChange(settings.copy(chineseTypography = it)) }, subtitle = "优化中文标点、行首行尾与段落显示")
        }
    }
}

@Composable
private fun SettingsList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val layout = LocalLayoutTokens.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(layout.cardPadding),
        verticalArrangement = Arrangement.spacedBy(layout.contentGap),
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
            .padding(horizontal = layout.cardPadding, vertical = layout.relatedGap),
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
