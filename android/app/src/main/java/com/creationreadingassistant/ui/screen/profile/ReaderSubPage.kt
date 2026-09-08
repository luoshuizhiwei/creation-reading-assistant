package com.creationreadingassistant.ui.screen.profile

import android.app.TimePickerDialog
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.ReaderFontPickerRow
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SettingBrightnessRow
import com.creationreadingassistant.ui.components.SettingSegmentedRow
import com.creationreadingassistant.ui.components.SettingSliderRow
import com.creationreadingassistant.ui.components.SettingSwitchRow
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.ReaderPaperOptions
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.util.Calendar
import kotlin.math.abs

private fun readerSubPageFormatMin(minute: Int): String {
    val clamped = minute.coerceIn(0, 1439)
    return "%02d:%02d".format(clamped / 60, clamped % 60)
}

/**
 * 高质感圆角微胶囊时间选择器行组件。
 */
@Composable
private fun ReaderCapsuleTimeRow(
    label: String,
    minute: Int,
    icon: ImageVector = Icons.Outlined.AccessTime,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 高质感微胶囊时间展示块
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier
                .bounceable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    haptic(HapticFeedbackType.TextHandleMove)
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, minute / 60)
                    cal.set(Calendar.MINUTE, minute % 60)
                    TimePickerDialog(
                        ctx,
                        { _, h, m -> onChange((h * 60 + m).coerceIn(0, 1439)) },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true,
                    ).show()
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = readerSubPageFormatMin(minute),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * 深度系统偏好：阅读器偏好微岛化子页。
 */
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
        // 1. 排版设置微岛（淡紫底座文字图标）
        item(key = "reader_typography_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SubPageSectionTitle(
                        title = "排版设置",
                        icon = Icons.Outlined.FormatSize,
                        iconTint = Color(0xFF8B5CF6),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    SettingSliderRow(
                        title = "字号",
                        value = settings.fontSize,
                        valueLabel = "${settings.fontSize.toInt()} sp",
                        onValueChange = { onAction(ProfileAction.UpdateReader { copy(fontSize = it) }) },
                        valueRange = 12f..40f,
                        steps = 27,
                    )

                    SectionDivider()

                    ReaderFontPickerRow(settings) { next ->
                        onAction(ProfileAction.UpdateReader { next })
                    }

                    SectionDivider()

                    SettingSegmentedRow(
                        title = "行距",
                        options = listOf(1.5f to "紧凑", 1.85f to "标准", 2.1f to "宽松"),
                        selected = nearest(settings.lineHeight, listOf(1.5f, 1.85f, 2.1f)),
                        onSelect = { onAction(ProfileAction.UpdateReader { copy(lineHeight = it) }) },
                    )

                    SectionDivider()

                    SettingSegmentedRow(
                        title = "段距",
                        options = listOf(0.8f to "小", 1.1f to "中", 1.5f to "大"),
                        selected = nearest(settings.paragraphSpacing, listOf(0.8f, 1.1f, 1.5f)),
                        onSelect = { onAction(ProfileAction.UpdateReader { copy(paragraphSpacing = it) }) },
                    )

                    SectionDivider()

                    SettingSliderRow(
                        title = "页边距",
                        value = settings.pageMargin,
                        valueLabel = "${settings.pageMargin.toInt()} dp",
                        onValueChange = { onAction(ProfileAction.UpdateReader { copy(pageMargin = it) }) },
                        valueRange = 10f..42f,
                        steps = 31,
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "粗体正文",
                        checked = settings.fontWeightBold,
                        subtitle = "增强正文字重，在强光或护眼模式下文字更清晰沉着",
                        onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(fontWeightBold = it) }) },
                    )
                }
            }
        }

        // 2. 阅读模式与纸张微岛（天蓝底座翻页图标）
        item(key = "reader_mode_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SubPageSectionTitle(
                        title = "阅读模式",
                        icon = Icons.Outlined.AutoStories,
                        iconTint = Color(0xFF0EA5E9),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    SettingSegmentedRow(
                        title = "翻页模式",
                        options = listOf("paged" to "左右翻页", "scroll" to "上下滚动"),
                        selected = settings.readerMode,
                        onSelect = { onAction(ProfileAction.UpdateReader { copy(readerMode = it) }) },
                    )

                    SectionDivider()

                    SettingSegmentedRow(
                        title = "翻页动画",
                        options = listOf("none" to "无动画", "fade" to "淡入", "slide" to "平滑滑动", "cover" to "仿真覆盖", "reveal" to "揭示下一页"),
                        selected = settings.pageTurnEffect,
                        onSelect = { onAction(ProfileAction.UpdateReader { copy(pageTurnEffect = it) }) },
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "跟随应用外观纸张",
                        checked = settings.background == "follow",
                        subtitle = "浅色使用白纸素笺，深色自动转为柔和深灰；阅读更平滑自然",
                        onCheckedChange = { follow ->
                            onAction(ProfileAction.UpdateReader { copy(background = if (follow) "follow" else "warm") })
                        },
                    )

                    if (settings.background != "follow") {
                        SectionDivider()
                        SettingSegmentedRow(
                            title = "固定阅读纸张",
                            options = ReaderPaperOptions.filterNot { it.key == "follow" }.map { it.key to it.label },
                            selected = settings.background,
                            onSelect = { onAction(ProfileAction.UpdateReader { copy(background = it) }) },
                        )
                    }
                }
            }
        }

        // 3. 翻页与手势微岛（暖橙底座手势图标）
        item(key = "reader_gesture_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SubPageSectionTitle(
                        title = "翻页与手势",
                        icon = Icons.Outlined.TouchApp,
                        iconTint = Color(0xFFF97316),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    SettingSegmentedRow(
                        title = "触控区域划分",
                        options = listOf("three-zone" to "左中右 (3 区)", "five-zone" to "上下扩展 (5 区)"),
                        selected = settings.tapZoneMode,
                        subtitle = "左右轻触翻页，点击中间区域呼出沉浸菜单",
                        onSelect = { onAction(ProfileAction.UpdateReader { copy(tapZoneMode = it) }) },
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "音量键翻页",
                        checked = settings.volumeKeyPaging,
                        subtitle = "使用手机侧边音量键单手翻页，减少屏幕遮挡",
                        onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(volumeKeyPaging = it) }) },
                    )

                    if (settings.volumeKeyPaging) {
                        SectionDivider()
                        SettingSwitchRow(
                            title = "朗读时仍响应音量键翻页",
                            checked = settings.volumeKeyPagingDuringTts,
                            subtitle = "TTS 朗读时拦截音量按键用于翻页，而非调节媒体音量",
                            onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(volumeKeyPagingDuringTts = it) }) },
                        )
                    }

                    SectionDivider()

                    SettingSliderRow(
                        title = "自动翻页速度",
                        value = settings.autoPageSpeed.toFloat(),
                        valueLabel = "${settings.autoPageSpeed} 档",
                        onValueChange = { onAction(ProfileAction.UpdateReader { copy(autoPageSpeed = it.toInt()) }) },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                }
            }
        }

        // 4. 夜读沉浸与自动翻页 / 护眼与定时微岛（暖金底座时钟图标）
        item(key = "reader_night_and_display_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SubPageSectionTitle(
                        title = "夜读沉浸与定时",
                        icon = Icons.Outlined.Schedule,
                        iconTint = Color(0xFFF59E0B),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    SettingBrightnessRow(
                        brightness = settings.brightness,
                        onBrightnessChange = { v ->
                            onAction(ProfileAction.UpdateReader { copy(brightness = v, lastFixedBrightness = if (v >= 0) v else lastFixedBrightness) })
                        },
                        fixedDefault = settings.lastFixedBrightness,
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "全屏沉浸模式",
                        checked = settings.immersiveMode,
                        subtitle = "隐藏系统状态栏与底部导航栏，阅读无干扰",
                        onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(immersiveMode = it) }) },
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "安静阅读信息",
                        checked = settings.showReaderInfo,
                        subtitle = "在页脚以低饱和度微光显示章节名、电量与当前时间",
                        onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(showReaderInfo = it) }) },
                    )

                    SectionDivider()

                    SettingSegmentedRow(
                        title = "菜单自动隐藏",
                        options = listOf(0 to "常驻", 3 to "3 秒", 5 to "5 秒", 8 to "8 秒"),
                        selected = listOf(0, 3, 5, 8).minByOrNull { abs(it - settings.autoHideSeconds) } ?: 0,
                        onSelect = { onAction(ProfileAction.UpdateReader { copy(autoHideSeconds = it) }) },
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "屏幕常亮",
                        checked = settings.keepAwake,
                        subtitle = "阅读期间防止屏幕自动熄灭锁定",
                        onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(keepAwake = it) }) },
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "显示底部进度条",
                        checked = settings.showProgressBar,
                        subtitle = "在正文边缘展示精细章节进度指示线",
                        onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(showProgressBar = it) }) },
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "夜间护眼滤镜",
                        checked = settings.eyeCareFilterEnabled,
                        subtitle = "降低冷光辐射，夜间阅读更舒适防疲劳",
                        onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareFilterEnabled = it) }) },
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "每日定时自动开启",
                        checked = settings.eyeCareScheduleEnabled,
                        subtitle = "到达设定时段后平滑过渡开启护眼温和滤镜",
                        onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareScheduleEnabled = it) }) },
                    )

                    // 显式圆角微胶囊时间选择器
                    if (settings.eyeCareScheduleEnabled) {
                        SectionDivider()
                        ReaderCapsuleTimeRow(
                            label = "开始生效时间",
                            minute = settings.eyeCareStartMinute,
                            onChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareStartMinute = it) }) },
                        )
                        SectionDivider()
                        ReaderCapsuleTimeRow(
                            label = "结束恢复时间",
                            minute = settings.eyeCareEndMinute,
                            onChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareEndMinute = it) }) },
                        )
                    }

                    if (settings.eyeCareFilterEnabled || settings.eyeCareScheduleEnabled) {
                        SectionDivider()
                        SettingSliderRow(
                            title = "滤镜色温",
                            value = settings.eyeCareTemperature.toFloat(),
                            valueLabel = "${settings.eyeCareTemperature} K",
                            onValueChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareTemperature = ((it / 100).toInt() * 100)) }) },
                            valueRange = 2600f..5500f,
                            steps = 28,
                        )

                        SectionDivider()
                        SettingSliderRow(
                            title = "滤镜暖度强度",
                            value = settings.eyeCareIntensity.toFloat(),
                            valueLabel = "${settings.eyeCareIntensity}%",
                            onValueChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareIntensity = it.toInt()) }) },
                            valueRange = 0f..100f,
                            steps = 19,
                        )

                        SectionDivider()
                        SettingSwitchRow(
                            title = "色卡预览同步滤镜",
                            checked = settings.eyeCareSyncPaperPreview,
                            subtitle = "纸张色卡同步叠加真实滤镜效果，所见即所得",
                            onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareSyncPaperPreview = it) }) },
                        )

                        SectionDivider()
                        SettingSwitchRow(
                            title = "OLED 纯黑兼容",
                            checked = settings.eyeCareOledBlackCompat,
                            subtitle = "深色模式下纯黑底色不被染黄，更深邃且低功耗",
                            onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareOledBlackCompat = it) }) },
                        )
                    }

                    SectionDivider()

                    SettingSliderRow(
                        title = "护眼休息提醒",
                        value = settings.eyeCareReminderMinutes.toFloat(),
                        valueLabel = "${settings.eyeCareReminderMinutes} 分钟",
                        onValueChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareReminderMinutes = it.toInt()) }) },
                        valueRange = 5f..60f,
                        steps = 54,
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "阅读节奏轻触提醒",
                        checked = settings.readingRhythmReminderEnabled,
                        subtitle = "定期轻微触感提示放松眼肌与坐姿",
                        onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(readingRhythmReminderEnabled = it) }) },
                    )

                    if (settings.readingRhythmReminderEnabled) {
                        SectionDivider()
                        SettingSliderRow(
                            title = "节奏提醒周期",
                            value = settings.readingRhythmReminderMinutes.toFloat(),
                            valueLabel = "${settings.readingRhythmReminderMinutes} 分钟",
                            onValueChange = { onAction(ProfileAction.UpdateReader { copy(readingRhythmReminderMinutes = it.toInt()) }) },
                            valueRange = 5f..60f,
                            steps = 54,
                        )
                    }
                }
            }
        }

        // 5. 高级排版与引擎兼容微岛
        item(key = "reader_engine_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SubPageSectionTitle(
                        title = "引擎与排版兼容",
                        icon = Icons.Outlined.Tune,
                        iconTint = Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    SettingSegmentedRow(
                        title = "TXT 分页引擎",
                        options = listOf("off" to "关闭", "auto" to "智能自动", "on" to "强制开启"),
                        selected = settings.pagerEngineMode,
                        onSelect = { onAction(ProfileAction.UpdateReader { copy(pagerEngineMode = it) }) },
                    )

                    SectionDivider()

                    SettingSegmentedRow(
                        title = "EPUB 分页引擎",
                        options = listOf("off" to "关闭", "auto" to "智能自动", "on" to "强制开启"),
                        selected = settings.epubPagerEngineMode,
                        onSelect = { onAction(ProfileAction.UpdateReader { copy(epubPagerEngineMode = it) }) },
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "中文标点排版优化",
                        checked = settings.chineseTypography,
                        subtitle = "优化孤行孤字、避头尾法则与全角标点行距吸附",
                        onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(chineseTypography = it) }) },
                    )

                    SectionDivider()

                    SettingSwitchRow(
                        title = "繁体中文显示",
                        checked = settings.traditionalChinese,
                        subtitle = "仅视觉转换显示为繁体字符；原文书签与高亮依然准确对齐",
                        onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(traditionalChinese = it) }) },
                    )
                }
            }
        }

        // 6. 重置微岛
        item(key = "reader_reset_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(layout.relatedGap),
                ) {
                    SubPageSectionTitle(
                        title = "重置偏好",
                        icon = Icons.Outlined.RestartAlt,
                        iconTint = MaterialTheme.colorScheme.error,
                    )

                    Text(
                        text = "仅恢复阅读显示、排版与手势默认设置；不会影响任何书籍文件、阅读进度、书签或笔记记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )

                    Spacer(Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { onAction(ProfileAction.ResetReader) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = "恢复阅读默认设置",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}

private fun nearest(value: Float, options: List<Float>): Float =
    options.minByOrNull { abs(it - value) } ?: options.first()
