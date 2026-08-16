package com.creationreadingassistant.ui.screen.reader

import kotlin.math.abs

/**
 * 阅读器 chrome 相关选项模型（纯逻辑，无 Android 依赖）。
 *
 * 自动隐藏时间与自动翻页速度档位共用同一事实源，设置页与工具栏直接引用，
 * 避免两处选项列表/标签漂移。
 */

/**
 * 自动隐藏可选项（秒）。0 = 不定时隐藏。
 * 默认值 4 秒必须在选项中，保证设置页"当前值显示准确"（用户反馈 2）。
 */
internal val AUTO_HIDE_SECOND_OPTIONS: List<Int> = listOf(0, 3, 4, 5, 8)

/** 自动隐藏选项标签：0 = 不隐藏；其余显示具体秒数。 */
internal fun autoHideSecondsLabel(seconds: Int): String = when (seconds) {
    0 -> "不隐藏"
    else -> "$seconds 秒"
}

/** 设置页当前值吸附到最近档（迁移/旧值兜底），避免 UI 显示错值。 */
internal fun nearestAutoHideOption(seconds: Int): Int =
    AUTO_HIDE_SECOND_OPTIONS.minByOrNull { abs(it - seconds) } ?: AUTO_HIDE_SECOND_OPTIONS.first()

/** 自动翻页速度档位范围（1..10，与 SettingsStore 持久化钳制一致）。 */
internal const val AUTO_PAGE_SPEED_MIN: Int = 1
internal const val AUTO_PAGE_SPEED_MAX: Int = 10

/** 自动翻页速度档位钳制（工具栏 -/+ 与持久化写入共用）。 */
internal fun clampAutoPageSpeed(speed: Int): Int =
    speed.coerceIn(AUTO_PAGE_SPEED_MIN, AUTO_PAGE_SPEED_MAX)
