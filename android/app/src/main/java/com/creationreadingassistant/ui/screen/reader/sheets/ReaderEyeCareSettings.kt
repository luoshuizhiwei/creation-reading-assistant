package com.creationreadingassistant.ui.screen.reader.sheets

import android.app.TimePickerDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.creationreadingassistant.data.settings.ReaderSettings
import java.util.Calendar

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
    MicroSettingsLinkRow(
        icon = Icons.Outlined.Timer,
        tint = Color(0xFF7C3AED),
        title = label,
        subtitle = "点击调整生效时刻",
        value = formatEyeCareMinute(minute),
        onClick = {
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
    )
}

@Composable
internal fun EyeCareSettings(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
) = SettingsList {
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.Visibility,
            title = "护眼滤镜",
            tint = Color(0xFF7C3AED),
        ) {
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Visibility,
                tint = Color(0xFF7C3AED),
                title = "开启护眼滤镜",
                checked = settings.eyeCareFilterEnabled,
                onCheckedChange = { onChange(settings.copy(eyeCareFilterEnabled = it)) },
            )
            MicroSectionDivider()
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Timer,
                tint = Color(0xFF2563EB),
                title = "夜间自动开启",
                checked = settings.eyeCareScheduleEnabled,
                onCheckedChange = { onChange(settings.copy(eyeCareScheduleEnabled = it)) },
            )
            if (settings.eyeCareScheduleEnabled) {
                MicroSectionDivider()
                EyeCareTimeRow(
                    label = "开始时间",
                    minute = settings.eyeCareStartMinute,
                    onChange = { onChange(settings.copy(eyeCareStartMinute = it)) },
                )
                MicroSectionDivider()
                EyeCareTimeRow(
                    label = "结束时间",
                    minute = settings.eyeCareEndMinute,
                    onChange = { onChange(settings.copy(eyeCareEndMinute = it)) },
                )
            }
            if (settings.eyeCareFilterEnabled || settings.eyeCareScheduleEnabled) {
                MicroSectionDivider()
                MicroTrackSliderRow(
                    icon = Icons.Outlined.Colorize,
                    title = "色温",
                    value = settings.eyeCareTemperature.toFloat(),
                    valueLabel = "${settings.eyeCareTemperature} K",
                    onValueChange = { onChange(settings.copy(eyeCareTemperature = ((it / 100).toInt() * 100))) },
                    valueRange = 2600f..5500f,
                    steps = 28,
                    minLabel = "暖",
                    maxLabel = "冷",
                    tint = Color(0xFFD97706),
                )
                MicroSectionDivider()
                MicroTrackSliderRow(
                    icon = Icons.Outlined.BrightnessMedium,
                    title = "强度",
                    value = settings.eyeCareIntensity.toFloat(),
                    valueLabel = "${settings.eyeCareIntensity}%",
                    onValueChange = { onChange(settings.copy(eyeCareIntensity = it.toInt())) },
                    valueRange = 0f..100f,
                    steps = 19,
                    minLabel = "0%",
                    maxLabel = "100%",
                    tint = Color(0xFF7C3AED),
                )
                MicroSectionDivider()
                MicroSettingsSwitchRow(
                    icon = Icons.Outlined.Visibility,
                    tint = Color(0xFF0891B2),
                    title = "OLED 纯黑兼容",
                    checked = settings.eyeCareOledBlackCompat,
                    onCheckedChange = { onChange(settings.copy(eyeCareOledBlackCompat = it)) },
                    subtitle = "夜读模式下保持纯黑像素不被暖色染色，更省电",
                )
            }
        }
    }
    item {
        MicroSettingsSection(
            icon = Icons.Outlined.Timer,
            title = "阅读提醒",
            tint = Color(0xFF059669),
        ) {
            MicroTrackSliderRow(
                icon = Icons.Outlined.Timer,
                title = "护眼提醒",
                value = settings.eyeCareReminderMinutes.toFloat(),
                valueLabel = "${settings.eyeCareReminderMinutes} 分钟",
                onValueChange = { onChange(settings.copy(eyeCareReminderMinutes = it.toInt())) },
                valueRange = 5f..60f,
                steps = 54,
                minLabel = "5m",
                maxLabel = "60m",
                tint = Color(0xFF059669),
            )
            MicroSectionDivider()
            MicroSettingsSwitchRow(
                icon = Icons.Outlined.Timer,
                tint = Color(0xFF2563EB),
                title = "阅读节奏提示",
                checked = settings.readingRhythmReminderEnabled,
                onCheckedChange = { onChange(settings.copy(readingRhythmReminderEnabled = it)) },
                subtitle = "适时提醒休息眼睛和身体",
            )
            if (settings.readingRhythmReminderEnabled) {
                MicroSectionDivider()
                MicroTrackSliderRow(
                    icon = Icons.Outlined.Timer,
                    title = "提示间隔",
                    value = settings.readingRhythmReminderMinutes.toFloat(),
                    valueLabel = "${settings.readingRhythmReminderMinutes} 分钟",
                    onValueChange = { onChange(settings.copy(readingRhythmReminderMinutes = it.toInt())) },
                    valueRange = 5f..60f,
                    steps = 54,
                    minLabel = "5m",
                    maxLabel = "60m",
                    tint = Color(0xFF2563EB),
                )
            }
        }
    }
}
