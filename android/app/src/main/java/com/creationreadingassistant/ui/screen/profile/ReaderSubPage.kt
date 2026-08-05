package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SettingSegmentedRow
import com.creationreadingassistant.ui.components.SettingBrightnessRow
import com.creationreadingassistant.ui.components.SettingSliderRow
import com.creationreadingassistant.ui.components.SettingSwitchRow
import com.creationreadingassistant.ui.components.SettingsSection
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.ReaderPaperOptions
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlin.math.abs

/** Full-screen reader settings. The in-reader sheet reuses the same row language. */
@Composable
internal fun ReaderSettingsSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val layout = LocalLayoutTokens.current
    val settings = state.reader
    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SettingsSection("排版", modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                SettingSliderRow("字号", settings.fontSize, "${settings.fontSize.toInt()} 字号", { onAction(ProfileAction.UpdateReader { copy(fontSize = it) }) }, valueRange = 12f..32f, steps = 19)
                SectionDivider()
                SettingSegmentedRow("行距", listOf(1.5f to "紧凑", 1.85f to "标准", 2.1f to "宽松"), nearest(settings.lineHeight, listOf(1.5f, 1.85f, 2.1f)), { onAction(ProfileAction.UpdateReader { copy(lineHeight = it) }) })
                SectionDivider()
                SettingSegmentedRow("段距", listOf(0.8f to "小", 1.1f to "中", 1.5f to "大"), nearest(settings.paragraphSpacing, listOf(0.8f, 1.1f, 1.5f)), { onAction(ProfileAction.UpdateReader { copy(paragraphSpacing = it) }) })
                SectionDivider()
                SettingSliderRow("页边距", settings.pageMargin, "${settings.pageMargin.toInt()} dp", { onAction(ProfileAction.UpdateReader { copy(pageMargin = it) }) }, valueRange = 10f..42f, steps = 31)
                SectionDivider()
                SettingSwitchRow("粗体文字", settings.fontWeightBold, { onAction(ProfileAction.UpdateReader { copy(fontWeightBold = it) }) })
            }
        }

        item {
            SettingsSection("阅读方式", modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                SettingSegmentedRow("阅读模式", listOf("paged" to "左右翻页", "scroll" to "上下滚动"), settings.readerMode, { onAction(ProfileAction.UpdateReader { copy(readerMode = it) }) })
                SectionDivider()
                SettingSegmentedRow("翻页效果", listOf("none" to "无", "fade" to "淡入", "slide" to "滑动", "cover" to "覆盖"), settings.pageTurnEffect, { onAction(ProfileAction.UpdateReader { copy(pageTurnEffect = it) }) })
                SectionDivider()
                SettingSegmentedRow("点击区域", listOf("three-zone" to "左中右", "five-zone" to "上下扩展"), settings.tapZoneMode, { onAction(ProfileAction.UpdateReader { copy(tapZoneMode = it) }) })
                SectionDivider()
                SettingSwitchRow("音量键翻页", settings.volumeKeyPaging, { onAction(ProfileAction.UpdateReader { copy(volumeKeyPaging = it) }) })
                if (settings.volumeKeyPaging) {
                    SectionDivider()
                    SettingSwitchRow("朗读时音量键仍翻页", settings.volumeKeyPagingDuringTts, { onAction(ProfileAction.UpdateReader { copy(volumeKeyPagingDuringTts = it) }) })
                }
                SectionDivider()
                SettingSliderRow("自动翻页速度", settings.autoPageSpeed.toFloat(), "${settings.autoPageSpeed} 档", { onAction(ProfileAction.UpdateReader { copy(autoPageSpeed = it.toInt()) }) }, valueRange = 1f..10f, steps = 8)
            }
        }

        item {
            SettingsSection("阅读纸张", modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                SettingSwitchRow(
                    title = "跟随应用外观",
                    checked = settings.background == "follow",
                    subtitle = "浅色使用白纸，深色自动切换夜读",
                    onCheckedChange = { follow -> onAction(ProfileAction.UpdateReader { copy(background = if (follow) "follow" else "warm") }) },
                )
                if (settings.background != "follow") {
                    SectionDivider()
                    SettingSegmentedRow(
                        title = "固定纸张",
                        options = ReaderPaperOptions.filterNot { it.key == "follow" }.map { it.key to it.label },
                        selected = settings.background,
                        onSelect = { onAction(ProfileAction.UpdateReader { copy(background = it) }) },
                    )
                }
            }
        }

        item {
            SettingsSection("显示", modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                SettingBrightnessRow(
                    settings.brightness,
                    { onAction(ProfileAction.UpdateReader { copy(brightness = it) }) },
                    fixedDefault = settings.lastFixedBrightness,
                )
                SectionDivider()
                SettingSwitchRow("沉浸模式", settings.immersiveMode, { onAction(ProfileAction.UpdateReader { copy(immersiveMode = it) }) })
                SectionDivider()
                SettingSwitchRow("安静阅读信息", settings.showReaderInfo, { onAction(ProfileAction.UpdateReader { copy(showReaderInfo = it) }) })
                SectionDivider()
                SettingSegmentedRow("菜单自动隐藏", listOf(0 to "不隐藏", 3 to "3 秒", 5 to "5 秒", 8 to "8 秒"), listOf(0, 3, 5, 8).minByOrNull { abs(it - settings.autoHideSeconds) } ?: 0, { onAction(ProfileAction.UpdateReader { copy(autoHideSeconds = it) }) })
                SectionDivider()
                SettingSwitchRow("屏幕常亮", settings.keepAwake, { onAction(ProfileAction.UpdateReader { copy(keepAwake = it) }) })
                SectionDivider()
                SettingSwitchRow("显示进度条", settings.showProgressBar, { onAction(ProfileAction.UpdateReader { copy(showProgressBar = it) }) })
            }
        }

        item {
            SettingsSection("护眼与提醒", modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                SettingSwitchRow("护眼滤镜", settings.eyeCareFilterEnabled, { onAction(ProfileAction.UpdateReader { copy(eyeCareFilterEnabled = it) }) })
                SectionDivider()
                SettingSwitchRow("夜间自动开启", settings.eyeCareScheduleEnabled, { onAction(ProfileAction.UpdateReader { copy(eyeCareScheduleEnabled = it) }) }, subtitle = "默认时段 22:00–07:00")
                if (settings.eyeCareFilterEnabled || settings.eyeCareScheduleEnabled) {
                    SectionDivider()
                    SettingSliderRow("色温", settings.eyeCareTemperature.toFloat(), "${settings.eyeCareTemperature} K", { onAction(ProfileAction.UpdateReader { copy(eyeCareTemperature = ((it / 100).toInt() * 100)) }) }, valueRange = 2600f..5500f, steps = 28)
                    SectionDivider()
                    SettingSliderRow("滤镜强度", settings.eyeCareIntensity.toFloat(), "${settings.eyeCareIntensity}%", { onAction(ProfileAction.UpdateReader { copy(eyeCareIntensity = it.toInt()) }) }, valueRange = 0f..100f, steps = 19)
                }
                SectionDivider()
                SettingSliderRow("护眼提醒", settings.eyeCareReminderMinutes.toFloat(), "${settings.eyeCareReminderMinutes} 分钟", { onAction(ProfileAction.UpdateReader { copy(eyeCareReminderMinutes = it.toInt()) }) }, valueRange = 5f..60f, steps = 54)
                SectionDivider()
                SettingSwitchRow("阅读节奏提示", settings.readingRhythmReminderEnabled, { onAction(ProfileAction.UpdateReader { copy(readingRhythmReminderEnabled = it) }) })
            }
        }

        item {
            SettingsSection(
                modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
                title = "高级兼容",
                description = "保持'自动'最稳妥。只在特定书籍分页异常时调整。",
            ) {
                SettingSegmentedRow("TXT 分页兼容模式", listOf("off" to "关闭", "auto" to "自动", "on" to "强制"), settings.pagerEngineMode, { onAction(ProfileAction.UpdateReader { copy(pagerEngineMode = it) }) })
                SectionDivider()
                SettingSegmentedRow("EPUB 分页兼容模式", listOf("off" to "关闭", "auto" to "自动", "on" to "强制"), settings.epubPagerEngineMode, { onAction(ProfileAction.UpdateReader { copy(epubPagerEngineMode = it) }) })
                SectionDivider()
                SettingSwitchRow("中文排版优化", settings.chineseTypography, { onAction(ProfileAction.UpdateReader { copy(chineseTypography = it) }) }, subtitle = "优化中文标点与行首行尾")
            }
        }

        item {
            SettingsSection("重置", modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(verticalArrangement = Arrangement.spacedBy(layout.relatedGap)) {
                    Text(
                        "只恢复阅读显示与操作设置，不会删除书籍、进度、书签或笔记。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { onAction(ProfileAction.ResetReader) }, modifier = Modifier.fillMaxWidth()) {
                        Text("重置阅读设置", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun nearest(value: Float, options: List<Float>): Float = options.minByOrNull { abs(it - value) } ?: options.first()
