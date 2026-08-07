package com.creationreadingassistant.ui.util

import java.time.Instant

/**
 * UI 层公共时间工具（收敛各模块重复实现）。
 *
 * - [nowIso]：原重复于 ui/viewmodel/ReaderViewModel、ui/screen/reader/ReaderHelpers
 *   与 data/repository/BookRepository 的时间戳生成。
 * - [formatDuration]：原重复于 ShelfUtils / ReaderHelpers / ProfileUtils 的时长格式化，
 *   统一为语义最完整的「天/小时/分钟」版本。
 */

/** 时间戳 ISO-8601 字符串。 */
fun nowIso(): String = Instant.now().toString()

/** 将毫秒时长格式化为「X 天 Y 小时」/「X 小时 Y 分钟」/「Y 分钟」。 */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0 分钟"
    val minutes = ms / 60000
    val hours = minutes / 60
    return when {
        hours >= 24 -> "${hours / 24} 天 ${hours % 24} 小时"
        hours > 0 -> "$hours 小时 ${minutes % 60} 分钟"
        else -> "$minutes 分钟"
    }
}
