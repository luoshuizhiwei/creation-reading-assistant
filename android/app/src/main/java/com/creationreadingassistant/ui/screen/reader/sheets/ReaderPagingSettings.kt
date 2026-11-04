package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.ReaderSettings

@Composable
internal fun PagingSettings(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) = SettingsList {
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
                // 文案与 ReaderSubPage 保持一致：fade 是「淡入」，slide 才是「平移/滑动」。
                // 旧文案把 fade 标成「平移」，与设置页互相矛盾，用户据此排查问题时会被误导。
                options = listOf("none" to "无", "fade" to "淡入", "slide" to "平移", "cover" to "仿真", "reveal" to "揭示"),
                selected = settings.pageTurnEffect,
                onSelect = { onChange(settings.copy(pageTurnEffect = it)) },
                tint = Color(0xFF2563EB),
            )
            if (settings.pageTurnEffect in listOf("slide", "cover", "reveal")) {
                Text(
                    text = "💡 复杂动画每帧需GPU合成多张位图，如遇掉帧或发热建议切回「无」或「淡入」。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp),
                )
            }
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
                title = "热区动作",
                icon = Icons.Outlined.TouchApp,
                options = listOf(
                    com.creationreadingassistant.feature.reader.pager.TapActionPreset.STANDARD to "标准",
                    com.creationreadingassistant.feature.reader.pager.TapActionPreset.LEFT_HAND to "左手模式",
                    com.creationreadingassistant.feature.reader.pager.TapActionPreset.BOTH_NEXT to "双向推进",
                ),
                selected = settings.tapActionPreset,
                onSelect = { onChange(settings.copy(tapActionPreset = it)) },
                tint = Color(0xFFD97706),
            )
            TapZonePreviewCard(
                tapZoneMode = settings.tapZoneMode,
                tapActionPreset = settings.tapActionPreset,
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
                    tint = Color(0xFF7C3AED),
                    title = "反转音量键方向",
                    subtitle = "默认上键上一页、下键下一页；开启后上键翻下一页",
                    checked = settings.volumeKeyInvert,
                    onCheckedChange = { onChange(settings.copy(volumeKeyInvert = it)) },
                    modifier = Modifier.testTag("reader-volume-key-invert"),
                )
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
