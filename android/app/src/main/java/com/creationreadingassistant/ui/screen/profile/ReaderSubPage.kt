package com.creationreadingassistant.ui.screen.profile

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.BatteryTipStyle
import com.creationreadingassistant.ui.components.HeaderFooterPicker
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.settingsMetrics
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
import com.creationreadingassistant.ui.theme.paperPalette
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
    val metrics = settingsMetrics()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.verticalPadding),
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
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = metrics.titleSize,
                    lineHeight = metrics.titleLineHeight,
                ),
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
 * 页内锚点胶囊：label 显示文案 + 对应 LazyColumn item 下标。
 *
 * 下标规则：作用范围标注占 0、锚点胶囊行自身占 1，各分组卡从 2 起按声明顺序排。
 * 本页 item 全是顶层且无条件增删，下标稳定；若增删顶层 item 必须同步更新这里。
 */
private data class ReaderSectionAnchor(val label: String, val itemIndex: Int)

private val readerSectionAnchors = listOf(
    ReaderSectionAnchor("排版", 2),
    ReaderSectionAnchor("模式与纸张", 3),
    ReaderSectionAnchor("翻页与手势", 4),
    ReaderSectionAnchor("夜读", 5),
    ReaderSectionAnchor("护眼", 6),
    ReaderSectionAnchor("分页引擎", 7),
    ReaderSectionAnchor("听书朗读", 8),
    ReaderSectionAnchor("重置", 9),
)

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
    // 设置子页共用 Route 下发的列表状态：搜索命中项需要它来滚动定位
    val settingsListState = rememberSettingsPageListState()
    val anchorScope = rememberCoroutineScope()

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        listState = settingsListState,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 0. 作用范围标注：本页改的是全局
        item(key = "reader_scope_note") {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateEnter(reducedMotion = reducedMotion),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "改的是全局默认；单书设置在阅读器底栏「设置」里切换。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 0.5 页内锚点胶囊行：48 个设置项一页放不下，胶囊点击滚到对应卡片。
        // itemIndex 是硬编码的 LazyColumn 下标（含锚点行自身占位 1 + 作用范围标注 0）；
        // 本页 item 全是顶层且无条件增删，下标稳定 —— 若增删顶层 item，必须同步更新这里。
        item(key = "reader_section_anchors") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(readerSectionAnchors) { anchor ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        border = BorderStroke(
                            0.6.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier.clickable {
                            anchorScope.launch { settingsListState.animateScrollToItem(anchor.itemIndex) }
                        },
                    ) {
                        Text(
                            anchor.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        // 1. 排版设置微岛（淡紫底座文字图标）
        item(key = "reader_typography_section") {
        SettingsAnchor("reader_typography_section") {
                SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SubPageSectionTitle(
                            title = "排版设置",
                            icon = Icons.Outlined.FormatSize,
                            iconTint = Color(0xFF8B5CF6),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )

                        // 排版实时预览卡片：随字号/行距/字重实时响应
                        TypographyPreviewCard(
                            fontSize = settings.fontSize,
                            lineHeight = settings.lineHeight,
                            bold = settings.fontWeightBold,
                            background = settings.background,
                            isDark = isSystemInDarkTheme(),
                        )

                        SectionDivider()

                        SettingsAnchor("reader_font_size") {
                            SettingSliderRow(
                                title = "字号",
                                value = settings.fontSize,
                                valueLabel = "${settings.fontSize.toInt()} sp",
                                onValueChange = { onAction(ProfileAction.UpdateReader { copy(fontSize = it) }) },
                                valueRange = 12f..40f,
                                steps = 27,
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_font_family") {
                            ReaderFontPickerRow(settings) { next ->
                                onAction(ProfileAction.UpdateReader { next })
                            }
                        }

                        SectionDivider()

                        SettingsAnchor("reader_line_height") {
                            SettingSegmentedRow(
                                title = "行距",
                                options = listOf(1.5f to "紧凑", 1.85f to "标准", 2.1f to "宽松"),
                                selected = nearest(settings.lineHeight, listOf(1.5f, 1.85f, 2.1f)),
                                onSelect = { onAction(ProfileAction.UpdateReader { copy(lineHeight = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_paragraph_spacing") {
                            SettingSegmentedRow(
                                title = "段距",
                                options = listOf(0.8f to "小", 1.1f to "中", 1.5f to "大"),
                                selected = nearest(settings.paragraphSpacing, listOf(0.8f, 1.1f, 1.5f)),
                                onSelect = { onAction(ProfileAction.UpdateReader { copy(paragraphSpacing = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_page_margin") {
                            SettingSliderRow(
                                title = "页边距",
                                value = settings.pageMargin,
                                valueLabel = "${settings.pageMargin.toInt()} dp",
                                onValueChange = { onAction(ProfileAction.UpdateReader { copy(pageMargin = it) }) },
                                valueRange = 10f..42f,
                                steps = 31,
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_bold") {
                            SettingSwitchRow(
                                title = "粗体正文",
                                checked = settings.fontWeightBold,
                                subtitle = "增加字重，强光下更易读",
                                onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(fontWeightBold = it) }) },
                            )
                        }
                    }
                }
            }
        }

        // 2. 阅读模式与纸张微岛（天蓝底座翻页图标）
        item(key = "reader_mode_section") {
        SettingsAnchor("reader_mode_section") {
                SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SubPageSectionTitle(
                            title = "阅读模式",
                            icon = Icons.Outlined.AutoStories,
                            iconTint = Color(0xFF0EA5E9),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )

                        SettingsAnchor("reader_page_turn_mode") {
                            SettingSegmentedRow(
                                title = "翻页模式",
                                options = listOf("paged" to "左右翻页", "scroll" to "上下滚动"),
                                selected = settings.readerMode,
                                onSelect = { onAction(ProfileAction.UpdateReader { copy(readerMode = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_page_turn_effect") {
                            SettingSegmentedRow(
                                title = "翻页动画",
                                options = listOf("none" to "无动画", "fade" to "淡入", "slide" to "平滑滑动", "cover" to "仿真覆盖", "reveal" to "揭示下一页"),
                                selected = settings.pageTurnEffect,
                                onSelect = { onAction(ProfileAction.UpdateReader { copy(pageTurnEffect = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_paper_follow") {
                            SettingSwitchRow(
                                title = "跟随应用外观纸张",
                                checked = settings.background == "follow",
                                subtitle = "浅色使用白纸素笺，深色自动转为柔和深灰；阅读更平滑自然",
                                onCheckedChange = { follow ->
                                    onAction(ProfileAction.UpdateReader { copy(background = if (follow) "follow" else "warm") })
                                },
                            )
                        }

                        if (settings.background != "follow") {
                            SectionDivider()
                            SettingsAnchor("reader_paper_fixed") {
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
            }
        }

        // 3. 翻页与手势微岛（暖橙底座手势图标）
        item(key = "reader_gesture_section") {
        SettingsAnchor("reader_gesture_section") {
                SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SubPageSectionTitle(
                            title = "翻页与手势",
                            icon = Icons.Outlined.TouchApp,
                            iconTint = Color(0xFFF97316),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )

                        SettingsAnchor("reader_tap_zone") {
                            SettingSegmentedRow(
                                title = "触控区域划分",
                                options = listOf("three-zone" to "左中右 (3 区)", "five-zone" to "上下扩展 (5 区)"),
                                selected = settings.tapZoneMode,
                                subtitle = "左右轻触翻页，点击中间区域呼出沉浸菜单",
                                onSelect = { onAction(ProfileAction.UpdateReader { copy(tapZoneMode = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_volume_key") {
                            SettingSwitchRow(
                                title = "音量键翻页",
                                checked = settings.volumeKeyPaging,
                                subtitle = "使用手机侧边音量键单手翻页，减少屏幕遮挡",
                                onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(volumeKeyPaging = it) }) },
                            )
                        }

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

                        SettingsAnchor("reader_auto_page_speed") {
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
            }
        }

        // 4. 夜读沉浸与自动翻页 / 护眼与定时微岛（暖金底座时钟图标）
        item(key = "reader_night_and_display_section") {
        SettingsAnchor("reader_night_and_display_section") {
                SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SubPageSectionTitle(
                            title = "夜读沉浸与定时",
                            icon = Icons.Outlined.Schedule,
                            iconTint = Color(0xFFF59E0B),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )

                        SettingsAnchor("reader_brightness") {
                            SettingBrightnessRow(
                                brightness = settings.brightness,
                                onBrightnessChange = { v ->
                                    onAction(ProfileAction.UpdateReader { copy(brightness = v, lastFixedBrightness = if (v >= 0) v else lastFixedBrightness) })
                                },
                                fixedDefault = settings.lastFixedBrightness,
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_immersive") {
                            SettingSwitchRow(
                                title = "全屏沉浸模式",
                                checked = settings.immersiveMode,
                                subtitle = "隐藏系统状态栏与底部导航栏，阅读无干扰",
                                onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(immersiveMode = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_info") {
                            SettingSwitchRow(
                                title = "安静阅读信息",
                                checked = settings.showReaderInfo,
                                subtitle = "页眉页脚显示自定义状态信息",
                                onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(showReaderInfo = it) }) },
                            )
                        }

                        if (settings.showReaderInfo) {
                            SectionDivider()
                            SettingsAnchor("reader_battery_style") {
                                SettingSegmentedRow(
                                    title = "电量显示方式",
                                    options = BatteryTipStyle.entries.map { it to it.label },
                                    selected = settings.batteryTipStyle,
                                    onSelect = { onAction(ProfileAction.UpdateReader { copy(batteryTipStyle = it) }) },
                                )
                            }
                            SectionDivider()
                            SettingsAnchor("reader_header_left") {
                                HeaderFooterPicker(
                                    label = "页眉左侧",
                                    template = settings.effectiveHeaderLeft(),
                                    onTemplateChange = { newTip: String -> onAction(ProfileAction.UpdateReader { copy(customTipHeaderLeft = newTip) }) },
                                )
                            }
                            SectionDivider()
                            SettingsAnchor("reader_header_center") {
                                HeaderFooterPicker(
                                    label = "页眉中间",
                                    template = settings.effectiveHeaderCenter(),
                                    onTemplateChange = { newTip: String -> onAction(ProfileAction.UpdateReader { copy(customTipHeaderCenter = newTip) }) },
                                )
                            }
                            SectionDivider()
                            SettingsAnchor("reader_header_right") {
                                HeaderFooterPicker(
                                    label = "页眉右侧",
                                    template = settings.effectiveHeaderRight(),
                                    onTemplateChange = { newTip: String -> onAction(ProfileAction.UpdateReader { copy(customTipHeaderRight = newTip) }) },
                                )
                            }
                            SectionDivider()
                            SettingsAnchor("reader_footer_left") {
                                HeaderFooterPicker(
                                    label = "页脚左侧",
                                    template = settings.effectiveFooterLeft(),
                                    onTemplateChange = { newTip: String -> onAction(ProfileAction.UpdateReader { copy(customTipFooterLeft = newTip) }) },
                                )
                            }
                            SectionDivider()
                            SettingsAnchor("reader_footer_center") {
                                HeaderFooterPicker(
                                    label = "页脚中间",
                                    template = settings.effectiveFooterCenter(),
                                    onTemplateChange = { newTip: String -> onAction(ProfileAction.UpdateReader { copy(customTipFooterCenter = newTip) }) },
                                )
                            }
                            SectionDivider()
                            SettingsAnchor("reader_footer_right") {
                                HeaderFooterPicker(
                                    label = "页脚右侧",
                                    template = settings.effectiveFooterRight(),
                                    onTemplateChange = { newTip: String -> onAction(ProfileAction.UpdateReader { copy(customTipFooterRight = newTip) }) },
                                )
                            }
                        }

                        SectionDivider()

                        SettingsAnchor("reader_auto_hide") {
                            SettingSegmentedRow(
                                title = "菜单自动隐藏",
                                options = listOf(0 to "常驻", 3 to "3 秒", 5 to "5 秒", 8 to "8 秒"),
                                selected = listOf(0, 3, 5, 8).minByOrNull { abs(it - settings.autoHideSeconds) } ?: 0,
                                onSelect = { onAction(ProfileAction.UpdateReader { copy(autoHideSeconds = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_keep_awake") {
                            SettingSwitchRow(
                                title = "屏幕常亮",
                                checked = settings.keepAwake,
                                subtitle = "阅读期间防止屏幕自动熄灭锁定",
                                onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(keepAwake = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_progress_bar") {
                            SettingSwitchRow(
                                title = "显示进度条",
                                checked = settings.showProgressBar,
                                subtitle = "在菜单栏展示全书进度指示线",
                                onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(showProgressBar = it) }) },
                            )
                        }
                    }
                }
            }
        }

        // 4.5 护眼滤镜独立微岛
        item(key = "reader_eye_care_section") {
        SettingsAnchor("reader_eye_care_section") {
                SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SubPageSectionTitle(
                            title = "护眼滤镜",
                            icon = Icons.Outlined.Visibility,
                            iconTint = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )

                        SettingsAnchor("reader_eye_care_filter") {
                            SettingSwitchRow(
                                title = "夜间护眼滤镜",
                                checked = settings.eyeCareFilterEnabled,
                                subtitle = "降低冷光辐射，夜间阅读更舒适防疲劳",
                                onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareFilterEnabled = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_eye_care_schedule") {
                            SettingSwitchRow(
                                title = "每日定时自动开启",
                                checked = settings.eyeCareScheduleEnabled,
                                subtitle = "到点自动开启护眼滤镜",
                                onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareScheduleEnabled = it) }) },
                            )
                        }

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

                        SettingsAnchor("reader_eye_care_reminder") {
                            SettingSliderRow(
                                title = "护眼休息提醒",
                                value = settings.eyeCareReminderMinutes.toFloat(),
                                valueLabel = "${settings.eyeCareReminderMinutes} 分钟",
                                onValueChange = { onAction(ProfileAction.UpdateReader { copy(eyeCareReminderMinutes = it.toInt()) }) },
                                valueRange = 5f..60f,
                                steps = 54,
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_rhythm_reminder") {
                            SettingSwitchRow(
                                title = "阅读节奏轻触提醒",
                                checked = settings.readingRhythmReminderEnabled,
                                subtitle = "定期轻微触感提示放松眼肌与坐姿",
                                onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(readingRhythmReminderEnabled = it) }) },
                            )
                        }

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
        }

        // 5. 高级排版与引擎兼容微岛
        item(key = "reader_engine_section") {
        SettingsAnchor("reader_engine_section") {
                SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SubPageSectionTitle(
                            title = "引擎与排版兼容",
                            icon = Icons.Outlined.Tune,
                            iconTint = Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )

                        SettingsAnchor("reader_txt_pager") {
                            SettingSegmentedRow(
                                title = "TXT 分页引擎",
                                options = listOf("off" to "关闭", "auto" to "智能自动", "on" to "强制开启"),
                                selected = settings.pagerEngineMode,
                                onSelect = { onAction(ProfileAction.UpdateReader { copy(pagerEngineMode = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_epub_pager") {
                            SettingSegmentedRow(
                                title = "EPUB 分页引擎",
                                options = listOf("off" to "关闭", "auto" to "智能自动", "on" to "强制开启"),
                                selected = settings.epubPagerEngineMode,
                                onSelect = { onAction(ProfileAction.UpdateReader { copy(epubPagerEngineMode = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_chinese_typography") {
                            SettingSwitchRow(
                                title = "中文标点排版优化",
                                checked = settings.chineseTypography,
                                subtitle = "优化孤行孤字、避头尾法则与全角标点行距吸附",
                                onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(chineseTypography = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_traditional_chinese") {
                            SettingSwitchRow(
                                title = "繁体中文显示",
                                checked = settings.traditionalChinese,
                                subtitle = "仅视觉转换显示为繁体字符；原文书签与高亮依然准确对齐",
                                onCheckedChange = { onAction(ProfileAction.UpdateReader { copy(traditionalChinese = it) }) },
                            )
                        }
                    }
                }
            }
        }

        // 5.5 听书与朗读（TTS）微岛
        item(key = "reader_tts_section") {
        SettingsAnchor("reader_tts_section") {
                SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SubPageSectionTitle(
                            title = "听书与朗读",
                            icon = Icons.Outlined.RecordVoiceOver,
                            iconTint = Color(0xFF0EA5E9),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )

                        SettingsAnchor("reader_tts_engine") {
                            SettingSegmentedRow(
                                title = "朗读引擎",
                                options = listOf("system" to "系统 TTS", "edge" to "Edge 在线"),
                                selected = settings.ttsEngine,
                                onSelect = { onAction(ProfileAction.UpdateReader { copy(ttsEngine = it) }) },
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_tts_speed") {
                            SettingSliderRow(
                                title = "语速",
                                value = settings.ttsSpeechRate,
                                valueLabel = "${String.format("%.1f", settings.ttsSpeechRate)}×",
                                onValueChange = { onAction(ProfileAction.UpdateReader { copy(ttsSpeechRate = it) }) },
                                valueRange = 0.5f..3.0f,
                                steps = 24,
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_tts_pitch") {
                            SettingSliderRow(
                                title = "音调 (Pitch)",
                                value = settings.ttsPitch,
                                valueLabel = String.format("%.1f", settings.ttsPitch),
                                onValueChange = { onAction(ProfileAction.UpdateReader { copy(ttsPitch = it) }) },
                                valueRange = 0.5f..2.0f,
                                steps = 14,
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_tts_volume") {
                            SettingSliderRow(
                                title = "朗读音量",
                                value = settings.ttsVolume,
                                valueLabel = "${(settings.ttsVolume * 100).toInt()}%",
                                onValueChange = { onAction(ProfileAction.UpdateReader { copy(ttsVolume = it) }) },
                                valueRange = 0f..1f,
                                steps = 19,
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_tts_timer") {
                            SettingSegmentedRow(
                                title = "定时停止",
                                options = listOf(0 to "不定时", 15 to "15 分钟", 30 to "30 分钟", 60 to "60 分钟"),
                                selected = listOf(0, 15, 30, 60).minByOrNull { abs(it - settings.ttsTimedStopMinutes) } ?: 0,
                                onSelect = { onAction(ProfileAction.UpdateReader { copy(ttsTimedStopMinutes = it) }) },
                                subtitle = "朗读到设定时长后自动暂停",
                            )
                        }

                        SectionDivider()

                        SettingsAnchor("reader_tts_voice") {
                            Column(modifier = Modifier.padding(horizontal = settingsMetrics().horizontalPadding, vertical = settingsMetrics().verticalPadding)) {
                                Text(
                                    "音色",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = settingsMetrics().titleSize,
                                        lineHeight = settingsMetrics().titleLineHeight,
                                    ),
                                )
                                Text(
                                    "系统 TTS 语音标识，留空则用引擎默认音色。",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = settingsMetrics().subtitleSize,
                                        lineHeight = settingsMetrics().subtitleLineHeight,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = settings.ttsVoiceId,
                                    onValueChange = { v -> onAction(ProfileAction.UpdateReader { copy(ttsVoiceId = v) }) },
                                    singleLine = true,
                                    placeholder = { Text("如 zh-CN-XiaoxiaoNeural") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. 重置微岛
        item(key = "reader_reset_section") {
        SettingsAnchor("reader_reset_section") {
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
                            text = "只恢复阅读显示、排版与手势默认值，不动书籍文件、进度、书签与笔记。",
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
}

private fun nearest(value: Float, options: List<Float>): Float =
    options.minByOrNull { abs(it - value) } ?: options.first()

/**
 * 排版实时预览卡片。
 *
 * 在"排版设置"微岛内以真实的纸张底色 + 正文字号 + 行距渲染示例文字，
 * 用户拖动滑块时实时感知变化，无需退出设置打开书籍来回确认。
 * 使用固定示例文案（陶渊明《饮酒》），纯粹用于排版预览。
 */
@Composable
private fun TypographyPreviewCard(
    fontSize: Float,
    lineHeight: Float,
    bold: Boolean,
    background: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val paper = remember(background, isDark) { paperPalette(background, isDark) }
    val lineHeightSp = (fontSize * lineHeight).sp

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = paper.bg,
        border = BorderStroke(0.8.dp, paper.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(paper.accent, CircleShape),
                )
                Text(
                    text = "排版实时效果",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = paper.fgMuted,
                )
            }
            Text(
                // 预览只需一行即可判断字号/行距/字重/纸张；第二行不增加信息，却让卡片多占约 40dp。
                text = "山气日夕佳，飞鸟相与还。",
                color = paper.fg,
                fontSize = fontSize.sp,
                lineHeight = lineHeightSp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
