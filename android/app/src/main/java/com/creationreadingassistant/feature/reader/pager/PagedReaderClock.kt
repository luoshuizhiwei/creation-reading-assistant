package com.creationreadingassistant.feature.reader.pager

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 任务 #15 结构拆分：从 [PagedReaderHost] 抽出的「时间 & 电量」刷新 effect 组。
 *
 * effect 体逐字搬运，key（Unit）与刷新间隔不变：时间每分钟、电量每 30 秒。
 */
@Composable
internal fun PagedReaderClockEffects(
    currentTime: MutableState<String>,
    batteryLevel: MutableIntState,
    context: Context,
) {
    // 每分钟刷新时间
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentTime.value = formatCurrentTime()
        }
    }
    // 每 30 秒刷新电量
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            batteryLevel.intValue = getBatteryLevel(context)
        }
    }
}

internal fun formatCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

internal fun getBatteryLevel(context: Context): Int {
    val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val batteryStatus = context.registerReceiver(null, ifilter)
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
}
