package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.data.settings.HeaderFooterItem

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SettingsSheet(
    paper: ReaderPaperPalette,
    fontSize: Float,
    lineHeight: Float,
    background: String,
    bold: Boolean,
    brightness: Int,
    readerMode: String,
    pagerEngineMode: String,
    epubPagerEngineMode: String,
    pageTurnEffect: String,
    tapZoneMode: String,
    pageMargin: Float,
    paragraphSpacing: Float,
    eyeCareMin: Int,
    eyeFilterEnabled: Boolean,
    eyeTemperature: Int,
    eyeIntensity: Int,
    eyeScheduleEnabled: Boolean,
    volumeKeyPaging: Boolean,
    volumeKeyPagingDuringTts: Boolean,
    autoPageSpeed: Int,
    rhythmEnabled: Boolean,
    rhythmMin: Int,
    onFontSize: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onBackground: (String) -> Unit,
    onBrightness: (Int) -> Unit,
    onBold: (Boolean) -> Unit,
    onReaderMode: (String) -> Unit,
    onPagerEngineMode: (String) -> Unit,
    onEpubPagerEngineMode: (String) -> Unit,
    onPageTurnEffect: (String) -> Unit,
    onTapZoneMode: (String) -> Unit,
    onPageMargin: (Float) -> Unit,
    onParagraphSpacing: (Float) -> Unit,
    onEyeCareMin: (Int) -> Unit,
    onEyeFilterEnabled: (Boolean) -> Unit,
    onEyeTemperature: (Int) -> Unit,
    onEyeIntensity: (Int) -> Unit,
    onEyeScheduleEnabled: (Boolean) -> Unit,
    onVolumeKeyPaging: (Boolean) -> Unit,
    onVolumeKeyPagingDuringTts: (Boolean) -> Unit,
    onAutoPageSpeed: (Int) -> Unit,
    onRhythmEnabled: (Boolean) -> Unit,
    onRhythmMin: (Int) -> Unit,
    immersiveMode: Boolean = false,
    showReaderInfo: Boolean = true,
    chineseTypography: Boolean = true,
    keepAwake: Boolean = false,
    showProgressBar: Boolean = true,
    autoHideSeconds: Int = 4,
    onImmersive: (Boolean) -> Unit = {},
    onShowInfo: (Boolean) -> Unit = {},
    onChineseTypo: (Boolean) -> Unit = {},
    onKeepAwake: (Boolean) -> Unit = {},
    onShowProgress: (Boolean) -> Unit = {},
    onAutoHide: (Int) -> Unit = {},
    onBookInfo: () -> Unit,
    headerLeft: HeaderFooterItem = HeaderFooterItem.CHAPTER_TITLE,
    headerRight: HeaderFooterItem = HeaderFooterItem.NONE,
    footerLeft: HeaderFooterItem = HeaderFooterItem.CHAPTER_TITLE,
    footerRight: HeaderFooterItem = HeaderFooterItem.PROGRESS,
    onHeaderLeft: (HeaderFooterItem) -> Unit = {},
    onHeaderRight: (HeaderFooterItem) -> Unit = {},
    onFooterLeft: (HeaderFooterItem) -> Unit = {},
    onFooterRight: (HeaderFooterItem) -> Unit = {},
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = paper.accent,
        activeTrackColor = paper.accent,
        inactiveTrackColor = paper.accent.copy(alpha = 0.32f),
    )
    Column(Modifier.fillMaxWidth().padding(LocalLayoutTokens.current.cardPadding).verticalScroll(rememberScrollState())) {
        Text("阅读设置", style = MaterialTheme.typography.titleLarge)

        Text("阅读模式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OptionPill(selected = readerMode == "paged", label = "左右翻页", onClick = { onReaderMode("paged") })
            OptionPill(selected = readerMode == "scroll", label = "上下滚动", onClick = { onReaderMode("scroll") })
        }
        Text("TXT 新分页引擎", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("off" to "关闭", "auto" to "自动", "on" to "强制开启").forEach { (value, label) ->
                OptionPill(pagerEngineMode == value, label) { onPagerEngineMode(value) }
            }
        }
        Text("EPUB 新分页引擎", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("off" to "关闭", "auto" to "自动", "on" to "强制开启").forEach { (value, label) ->
                OptionPill(epubPagerEngineMode == value, label) { onEpubPagerEngineMode(value) }
            }
        }

        Text("翻页与点击", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "none" to "无动画",
                "fade" to "柔和淡入",
                "slide" to "左右滑动",
                "cover" to "覆盖翻页",
            ).forEach { (v, label) ->
                OptionPill(selected = pageTurnEffect == v, label = label, onClick = { onPageTurnEffect(v) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            OptionPill(selected = tapZoneMode == "three-zone", label = "左中右三区", onClick = { onTapZoneMode("three-zone") })
            OptionPill(selected = tapZoneMode == "five-zone", label = "上下扩展五区", onClick = { onTapZoneMode("five-zone") })
        }

        Text("字号", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onFontSize((fontSize - 1f).coerceAtLeast(12f)) }) { Text("A-") }
            Text(fontSize.toInt().toString(), Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { onFontSize((fontSize + 1f).coerceAtMost(32f)) }) { Text("A+") }
        }

        Text("行距", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(1.5f to "紧凑", 1.85f to "标准", 2.1f to "宽松").forEach { (v, label) ->
                OptionPill(selected = kotlin.math.abs(lineHeight - v) < 0.01f, label = label, onClick = { onLineHeight(v) })
            }
        }

        Text("段距", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0.8f to "小", 1.1f to "中", 1.5f to "大").forEach { (v, label) ->
                OptionPill(selected = kotlin.math.abs(paragraphSpacing - v) < 0.01f, label = label, onClick = { onParagraphSpacing(v) })
            }
        }

        Text("页边距", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = pageMargin,
                onValueChange = { onPageMargin(it) },
                valueRange = 10f..42f,
                steps = 32,
                colors = sliderColors,
                modifier = Modifier.weight(1f),
            )
            Text("${pageMargin.toInt()}", Modifier.padding(start = 8.dp))
        }

        Text("亮度", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = brightness.toFloat(),
                onValueChange = { onBrightness(it.toInt()) },
                valueRange = 5f..100f,
                steps = 18,
                colors = sliderColors,
                modifier = Modifier.weight(1f),
            )
            Text("${brightness}%", Modifier.padding(start = 8.dp))
        }

        Text("夜间护眼滤镜", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        SettingsSwitchRow("手动开启", eyeFilterEnabled) { onEyeFilterEnabled(it) }
        SettingsSwitchRow("按时间自动开启（22:00–07:00）", eyeScheduleEnabled) { onEyeScheduleEnabled(it) }
        Text("色温 ${eyeTemperature}K", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = eyeTemperature.toFloat(),
            onValueChange = { onEyeTemperature((it / 100).toInt() * 100) },
                valueRange = 2600f..5500f,
                steps = 28,
                colors = sliderColors,
            )
        Text("强度 ${eyeIntensity}%", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = eyeIntensity.toFloat(),
            onValueChange = { onEyeIntensity(it.toInt()) },
                valueRange = 0f..100f,
                steps = 19,
                colors = sliderColors,
            )

        // R7：阅读内快捷开关（沉浸 / 安静信息 / 中文排版 / 常亮 / 进度条 / 自动隐藏）
        Text("阅读辅助", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        SettingsSwitchRow("音量键翻页", volumeKeyPaging) { onVolumeKeyPaging(it) }
        SettingsSwitchRow(
            "朗读时音量键仍翻页",
            volumeKeyPagingDuringTts,
        ) { onVolumeKeyPagingDuringTts(it) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("自动翻页速度", Modifier.weight(1f))
            Slider(
                value = autoPageSpeed.toFloat(),
                onValueChange = { onAutoPageSpeed(it.toInt().coerceIn(1, 10)) },
                valueRange = 1f..10f,
                steps = 8,
                colors = sliderColors,
                modifier = Modifier.weight(1f),
            )
            Text("$autoPageSpeed", Modifier.padding(start = 8.dp))
        }
        SettingsSwitchRow("沉浸模式", immersiveMode) { onImmersive(it) }
        SettingsSwitchRow("安静阅读信息", showReaderInfo) { onShowInfo(it) }

        // 页眉页脚配置
        if (showReaderInfo) {
            Text("页眉显示", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderFooterDropdown(
                    label = "左侧",
                    selected = headerLeft,
                    onSelect = onHeaderLeft,
                    modifier = Modifier.weight(1f),
                )
                HeaderFooterDropdown(
                    label = "右侧",
                    selected = headerRight,
                    onSelect = onHeaderRight,
                    modifier = Modifier.weight(1f),
                )
            }
            Text("页脚显示", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderFooterDropdown(
                    label = "左侧",
                    selected = footerLeft,
                    onSelect = onFooterLeft,
                    modifier = Modifier.weight(1f),
                )
                HeaderFooterDropdown(
                    label = "右侧",
                    selected = footerRight,
                    onSelect = onFooterRight,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SettingsSwitchRow("中文排版优化", chineseTypography) { onChineseTypo(it) }
        SettingsSwitchRow("常亮显示", keepAwake) { onKeepAwake(it) }
        SettingsSwitchRow("显示进度条", showProgressBar) { onShowProgress(it) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("菜单自动隐藏（秒）", Modifier.weight(1f))
            Slider(
                value = autoHideSeconds.toFloat(),
                onValueChange = { onAutoHide(it.toInt()) },
                valueRange = 0f..8f,
                steps = 8,
                colors = sliderColors,
                modifier = Modifier.weight(1f),
            )
            Text("${autoHideSeconds}", Modifier.padding(start = 8.dp))
        }

        Text("阅读提醒", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("护眼提醒（分钟）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$eyeCareMin", style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = eyeCareMin.toFloat(),
                onValueChange = { onEyeCareMin(it.toInt()) },
                valueRange = 5f..60f,
                steps = 55,
                colors = sliderColors,
                modifier = Modifier.weight(1f),
            )
        }
        SettingsSwitchRow(
            label = "阅读节奏提示",
            checked = rhythmEnabled,
            onCheckedChange = onRhythmEnabled,
            subtitle = "每 ${rhythmMin} 分钟轻提示休息",
        )
        if (rhythmEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                value = rhythmMin.toFloat(),
                onValueChange = { onRhythmMin(it.toInt()) },
                valueRange = 5f..60f,
                steps = 55,
                colors = sliderColors,
                modifier = Modifier.weight(1f),
                )
            }
        }

        SettingsSwitchRow(label = "粗体文字", checked = bold, onCheckedChange = onBold)

        Button(onClick = onBookInfo, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("书籍信息")
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HeaderFooterDropdown(
    label: String,
    selected: HeaderFooterItem,
    onSelect: (HeaderFooterItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = HeaderFooterItem.entries.toList()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        TextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}
