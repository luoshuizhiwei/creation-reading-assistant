package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.creationreadingassistant.data.settings.ReaderSettings

@Composable
internal fun AdvancedSettings(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) = SettingsList {
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
