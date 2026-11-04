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

import android.content.BroadcastReceiver
import androidx.compose.runtime.DisposableEffect

/**
 * 任务 #15 结构拆分：从 [PagedReaderHost] 抽出的「时间 & 电量」精准对齐刷新 effect 组。
 *
 * 优化特性：
 * 1. 毫秒级时间对齐：首次加载与每轮 delay 严格对齐至系统下一个整分钟边界，消除入书后最多 59s 的时间滞后；
 * 2. 系统事件即时响应：通过 DisposableEffect 注册 ACTION_TIME_TICK / ACTION_BATTERY_CHANGED / 充电插拔广播；
 * 3. 离开阅读器自动安全注销，零泄露风险。
 */
@Composable
internal fun PagedReaderClockEffects(
    currentTime: MutableState<String>,
    batteryLevel: MutableIntState,
    context: Context,
) {
    // 毫秒级对齐系统整分钟刷新时间
    LaunchedEffect(Unit) {
        currentTime.value = formatCurrentTime()
        while (true) {
            val now = System.currentTimeMillis()
            val delayToNextMinute = (60_000L - (now % 60_000L)).coerceIn(500L, 60_000L)
            delay(delayToNextMinute)
            currentTime.value = formatCurrentTime()
        }
    }
    // 每 30 秒兜底轮询电量
    LaunchedEffect(Unit) {
        batteryLevel.intValue = getBatteryLevel(context)
        while (true) {
            delay(30_000L)
            batteryLevel.intValue = getBatteryLevel(context)
        }
    }
    // 系统时间/电量/充电状态动态广播监听
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                        currentTime.value = formatCurrentTime()
                    }
                    Intent.ACTION_BATTERY_CHANGED, Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                        batteryLevel.intValue = getBatteryLevel(context)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
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
