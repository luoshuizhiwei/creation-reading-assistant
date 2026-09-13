package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
