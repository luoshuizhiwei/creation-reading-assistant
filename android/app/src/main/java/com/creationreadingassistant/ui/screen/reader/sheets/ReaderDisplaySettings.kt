package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.creationreadingassistant.data.settings.BatteryTipStyle
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.ui.components.HeaderFooterPicker
import com.creationreadingassistant.ui.components.SettingBrightnessRow
import com.creationreadingassistant.ui.screen.reader.AUTO_HIDE_SECOND_OPTIONS
import com.creationreadingassistant.ui.screen.reader.autoHideSecondsLabel
import com.creationreadingassistant.ui.screen.reader.nearestAutoHideOption

@Composable
internal fun DisplaySettings(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) = SettingsList {
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.Devices,
            title = "屏幕显示",
            tint = Color(0xFFD97706),
        ) {
            SettingBrightnessRow(
                settings.brightness,
                { v ->
                    onChange(settings.copy(brightness = v, lastFixedBrightness = if (v >= 0) v else settings.lastFixedBrightness))
                },
                fixedDefault = settings.lastFixedBrightness,
            )
            MicroSectionDivider()
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Devices,
                tint = Color(0xFF059669),
                title = "沉浸模式",
                checked = settings.immersiveMode,
                onCheckedChange = { onChange(settings.copy(immersiveMode = it)) },
                subtitle = "隐藏系统状态栏与导航栏",
            )
            MicroSectionDivider()
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Devices,
                tint = Color(0xFF2563EB),
                title = "显示进度条",
                checked = settings.showProgressBar,
                onCheckedChange = { onChange(settings.copy(showProgressBar = it)) },
            )
            MicroSectionDivider()
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Devices,
                tint = Color(0xFFD97706),
                title = "屏幕常亮",
                checked = settings.keepAwake,
                onCheckedChange = { onChange(settings.copy(keepAwake = it)) },
            )
        }
    }
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.Timer,
            title = "菜单隐藏",
            tint = Color(0xFF7C3AED),
        ) {
            MicroOptionPillRow(
                title = "自动隐藏",
                icon = Icons.Outlined.Timer,
                options = AUTO_HIDE_SECOND_OPTIONS.map { it to autoHideSecondsLabel(it) },
                selected = nearestAutoHideOption(settings.autoHideSeconds),
                onSelect = { onChange(settings.copy(autoHideSeconds = it)) },
                tint = Color(0xFF7C3AED),
                subtitle = "0 秒 = 不定时隐藏",
            )
        }
    }
    item {
        MicroSettingsSection(
            icon = Icons.AutoMirrored.Outlined.ViewSidebar,
            title = "页眉页脚",
            tint = Color(0xFF0891B2),
        ) {
            MicroSettingsSwitchRow(
                icon = Icons.AutoMirrored.Outlined.ViewSidebar,
                tint = Color(0xFF0891B2),
                title = "显示安静阅读信息",
                checked = settings.showReaderInfo,
                onCheckedChange = { onChange(settings.copy(showReaderInfo = it)) },
            )
            if (settings.showReaderInfo) {
                MicroSectionDivider()
                MicroOptionPillRow(
                    title = "电量显示方式",
                    icon = Icons.Outlined.Devices,
                    options = BatteryTipStyle.entries.map { it to it.label },
                    selected = settings.batteryTipStyle,
                    onSelect = { onChange(settings.copy(batteryTipStyle = it)) },
                    tint = Color(0xFF0891B2),
                    subtitle = "作用于模板里的 {BatteryPercent}",
                )
                MicroSectionDivider()
                HeaderFooterPicker(
                    label = "页眉左侧",
                    template = settings.effectiveHeaderLeft(),
                    onTemplateChange = { onChange(settings.copy(customTipHeaderLeft = it)) },
                )
                MicroSectionDivider()
                HeaderFooterPicker(
                    label = "页眉中间",
                    template = settings.effectiveHeaderCenter(),
                    onTemplateChange = { onChange(settings.copy(customTipHeaderCenter = it)) },
                )
                MicroSectionDivider()
                HeaderFooterPicker(
                    label = "页眉右侧",
                    template = settings.effectiveHeaderRight(),
                    onTemplateChange = { onChange(settings.copy(customTipHeaderRight = it)) },
                )
                MicroSectionDivider()
                HeaderFooterPicker(
                    label = "页脚左侧",
                    template = settings.effectiveFooterLeft(),
                    onTemplateChange = { onChange(settings.copy(customTipFooterLeft = it)) },
                )
                MicroSectionDivider()
                HeaderFooterPicker(
                    label = "页脚中间",
                    template = settings.effectiveFooterCenter(),
                    onTemplateChange = { onChange(settings.copy(customTipFooterCenter = it)) },
                )
                MicroSectionDivider()
                HeaderFooterPicker(
                    label = "页脚右侧",
                    template = settings.effectiveFooterRight(),
                    onTemplateChange = { onChange(settings.copy(customTipFooterRight = it)) },
                )
            }
        }
    }
}
