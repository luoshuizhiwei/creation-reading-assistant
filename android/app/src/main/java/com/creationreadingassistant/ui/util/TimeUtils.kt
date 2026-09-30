package com.creationreadingassistant.ui.util

import com.creationreadingassistant.data.settings.DurationUnit
import java.time.Instant

/**
 * UI 层公共时间工具（收敛各模块重复实现）。
 *
 * - [nowIso]：原重复于 ui/viewmodel/ReaderViewModel、ui/screen/reader/ReaderHelpers
 *   与 data/repository/BookRepository 的时间戳生成。
 * - [formatDuration]：原重复于 ShelfUtils / ReaderHelpers / ProfileUtils / HomeArchiveFormatters
 *   与 MyReadingScreen 的时长格式化，统一到这一个实现。
 */

/** 时间戳 ISO-8601 字符串。 */
fun nowIso(): String = Instant.now().toString()

/**
 * 时长的装配样式。**只影响形态，不影响判定**。
 *
 * 为什么要有这个参数：紧凑卡片（首页指标格、统计页微卡、柱顶数值）装不下
 * `"1 小时 30 分钟"` 这种长格式。此前各页面为此各自复制了一套舍入算法，于是同一个
 * 数值在不同页面出现 `"1h 31m"` / `"1.5 小时"` / `"1 小时 30 分钟"` 三种结果。
 * 收敛为参数后，**阈值、舍入、不足一分钟的策略仍由 [formatDuration] 一处产生**，
 * 页面只能选择"说多详细"，不能选择"怎么算"。
 */
enum class DurationStyle {
    /** 完整形态：`"1 小时 30 分钟"` / `"1 天 6 小时"` / `"45 分钟"`。 */
    NORMAL,

    /**
     * 紧凑形态：`"1 小时"` / `"1 天"` / `"45 分钟"`。
     *
     * 只省略**尾数**，不改变量级判定：不足一分钟仍是 `"0 分钟"`，HOURS 仍不进位到天，
     * DAYS 仍在 24 小时进位。因此同一毫秒值在两种样式下必定落在同一量级，
     * 只会是 [NORMAL] 的省略写法。
     */
    COMPACT,

    /**
     * 刻度形态：`"45分"` / `"2时"` / `"1天"`。
     *
     * 给图表柱顶、坐标刻度这类**每格只有二十几 dp** 的位置用：`COMPACT` 的
     * `"45 分钟"` 约 45dp 放不下，会被省略号截成 `"45…"`，比不显示单位更糟。
     * 单字单位（分/时/天）把宽度压到 3 字符以内。
     *
     * 量级判定与 [NORMAL] / [COMPACT] **完全同源**（同一套阈值、同一套向下取整、
     * 同一套不足一分钟策略），只是单位词取单字。页面依旧只选样式，不自行换算。
     */
    TICK,
}

/**
 * 将毫秒时长按 [unit] 格式化。
 *
 * - [DurationUnit.HOURS]（默认）：`"30 小时 15 分钟"` / `"45 分钟"` —— **不进位到天**。
 *   跨天只体现在小时数上，避免「1 天 6 小时」这种需要心算的表述。
 * - [DurationUnit.DAYS]：`"1 天 6 小时"` / `"5 小时 20 分钟"` / `"40 分钟"`。
 *
 * [DurationStyle.NORMAL] 保留「分钟」尾数：阅读时长普遍在小时量级，抹掉分钟会让
 * 59 分钟与 1 小时看起来完全相同。[DurationStyle.COMPACT] 省略尾数以适配紧凑卡片。
 *
 * 两种样式、两种单位都共用同一套判定：
 * - 不足 1 分钟统一显示「0 分钟」，而不是「不足 1 分钟」或「N 秒」；
 * - 分钟数一律向下取整（`ms / 60_000`），不做四舍五入，也不做小数小时进位。
 *
 * 这是全应用时长文本的**单一事实源**：调用方只选择单位与样式，不自行判断量级。
 */
fun formatDuration(
    ms: Long,
    unit: DurationUnit = DurationUnit.HOURS,
    style: DurationStyle = DurationStyle.NORMAL,
): String {
    if (ms <= 0) return "0 分钟"
    val minutes = ms / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when (unit) {
        DurationUnit.HOURS -> when {
            hours > 0 -> when (style) {
                DurationStyle.NORMAL -> "$hours 小时 ${minutes % 60} 分钟"
                DurationStyle.COMPACT -> "$hours 小时"
                DurationStyle.TICK -> "${hours}时"
            }
            else -> when (style) {
                DurationStyle.TICK -> "${minutes}分"
                else -> "$minutes 分钟"
            }
        }
        DurationUnit.DAYS -> when {
            hours >= 24 -> when (style) {
                DurationStyle.NORMAL -> "$days 天 ${hours % 24} 小时"
                DurationStyle.COMPACT -> "$days 天"
                DurationStyle.TICK -> "${days}天"
            }
            hours > 0 -> when (style) {
                DurationStyle.NORMAL -> "$hours 小时 ${minutes % 60} 分钟"
                DurationStyle.COMPACT -> "$hours 小时"
                DurationStyle.TICK -> "${hours}时"
            }
            else -> when (style) {
                DurationStyle.TICK -> "${minutes}分"
                else -> "$minutes 分钟"
            }
        }
    }
}
