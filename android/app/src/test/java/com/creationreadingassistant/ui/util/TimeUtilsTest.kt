package com.creationreadingassistant.ui.util

import com.creationreadingassistant.data.settings.DurationUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时长格式化测试。
 *
 * 核心不变量：默认单位是小时，**跨天不进位到「天」**；只有显式切到
 * [DurationUnit.DAYS] 时才可能出现「天」。这条不变量就是「阅读时长显示要小时
 * 而不是天」这个需求的回归护栏。
 */
class TimeUtilsTest {

    private val hourMs = 3_600_000L
    private val minuteMs = 60_000L

    @Test
    fun `default unit is hours and never rolls over to days`() {
        // 30 小时：小时制下必须原样显示 30 小时，而不是「1 天 6 小时」。
        assertEquals("30 小时 0 分钟", formatDuration(30 * hourMs))
        assertEquals("30 小时 0 分钟", formatDuration(30 * hourMs, DurationUnit.HOURS))
    }

    @Test
    fun `days unit rolls over at 24 hours`() {
        assertEquals("1 天 6 小时", formatDuration(30 * hourMs, DurationUnit.DAYS))
        assertEquals("2 天 0 小时", formatDuration(48 * hourMs, DurationUnit.DAYS))
    }

    @Test
    fun `days unit keeps hours below one day`() {
        assertEquals("5 小时 20 分钟", formatDuration(5 * hourMs + 20 * minuteMs, DurationUnit.DAYS))
    }

    @Test
    fun `under one hour shows minutes in both units`() {
        assertEquals("45 分钟", formatDuration(45 * minuteMs, DurationUnit.HOURS))
        assertEquals("45 分钟", formatDuration(45 * minuteMs, DurationUnit.DAYS))
    }

    @Test
    fun `minutes remainder is preserved in hour range`() {
        assertEquals("2 小时 5 分钟", formatDuration(2 * hourMs + 5 * minuteMs, DurationUnit.HOURS))
    }

    @Test
    fun `non positive duration shows zero minutes`() {
        assertEquals("0 分钟", formatDuration(0L))
        assertEquals("0 分钟", formatDuration(-1L, DurationUnit.DAYS))
    }

    // ===================== 紧凑样式（S15-F1/F2） =====================
    //
    // COMPACT 只改变装配形态，**不引入第二套阈值/舍入**：不足一分钟、跨天进位、
    // 小时以下显示分钟这三条判定与 NORMAL 完全同源，因此同一毫秒值在两个样式下
    // 不可能出现互相矛盾的分类（只可能是同一分类的简写）。

    @Test
    fun `compact hours style drops the minute tail but keeps the thresholds`() {
        // 不足一分钟 → 与 NORMAL 同一策略
        assertEquals("0 分钟", formatDuration(30_000L, DurationUnit.HOURS, DurationStyle.COMPACT))
        // 不足一小时 → 分钟
        assertEquals("45 分钟", formatDuration(45 * minuteMs, DurationUnit.HOURS, DurationStyle.COMPACT))
        assertEquals("59 分钟", formatDuration(59 * minuteMs, DurationUnit.HOURS, DurationStyle.COMPACT))
        // 恰好一小时 → 小时（不显示 0 分钟尾数）
        assertEquals("1 小时", formatDuration(hourMs, DurationUnit.HOURS, DurationStyle.COMPACT))
        assertEquals("1 小时", formatDuration(hourMs + 30 * minuteMs, DurationUnit.HOURS, DurationStyle.COMPACT))
        // HOURS 永不带「天」
        assertEquals("30 小时", formatDuration(30 * hourMs, DurationUnit.HOURS, DurationStyle.COMPACT))
    }

    @Test
    fun `compact days style rolls over at 24 hours and drops the remainder`() {
        assertEquals("1 天", formatDuration(30 * hourMs, DurationUnit.DAYS, DurationStyle.COMPACT))
        assertEquals("2 天", formatDuration(48 * hourMs, DurationUnit.DAYS, DurationStyle.COMPACT))
        // 不足一天 → 小时
        assertEquals("5 小时", formatDuration(5 * hourMs + 20 * minuteMs, DurationUnit.DAYS, DurationStyle.COMPACT))
        assertEquals("23 小时", formatDuration(23 * hourMs, DurationUnit.DAYS, DurationStyle.COMPACT))
        // 不足一小时 → 分钟
        assertEquals("40 分钟", formatDuration(40 * minuteMs, DurationUnit.DAYS, DurationStyle.COMPACT))
    }

    @Test
    fun `compact style keeps the zero policy of the normal style`() {
        assertEquals("0 分钟", formatDuration(0L, DurationUnit.HOURS, DurationStyle.COMPACT))
        assertEquals("0 分钟", formatDuration(-1L, DurationUnit.HOURS, DurationStyle.COMPACT))
        assertEquals("0 分钟", formatDuration(0L, DurationUnit.DAYS, DurationStyle.COMPACT))
        assertEquals("0 分钟", formatDuration(-1L, DurationUnit.DAYS, DurationStyle.COMPACT))
    }

    @Test
    fun `the two styles never disagree about the magnitude class of the same value`() {
        // 同一毫秒值在两个样式下必须落在同一量级：都不足一分钟 → 都输出分钟；
        // 都跨天 → COMPACT 只是 NORMAL 的省略写法。这条是「不同页面策略不得互相矛盾」的护栏。
        val samples = listOf(
            0L, 30_000L, 59_000L, minuteMs, 45 * minuteMs, 59 * minuteMs,
            hourMs, hourMs + 30 * minuteMs, 5 * hourMs + 20 * minuteMs,
            23 * hourMs, 24 * hourMs, 30 * hourMs, 48 * hourMs, 100 * hourMs,
        )
        for (ms in samples) {
            for (unit in DurationUnit.entries) {
                val normal = formatDuration(ms, unit, DurationStyle.NORMAL)
                val compact = formatDuration(ms, unit, DurationStyle.COMPACT)
                // 两条不变量：COMPACT 是 NORMAL 的前缀；且量级词（分钟/小时/天）一致。
                assertTrue(
                    "COMPACT 必须是 NORMAL 的前缀：ms=$ms unit=$unit normal='$normal' compact='$compact'",
                    normal.startsWith(compact),
                )
                assertEquals(
                    "同值同单位下量级词必须一致：ms=$ms unit=$unit normal='$normal' compact='$compact'",
                    magnitudeWord(normal),
                    magnitudeWord(compact),
                )
            }
        }
    }

    @Test
    fun `tick style keeps the magnitude but uses single character units`() {
        // 柱顶/刻度用：宽度必须压到 3 字符内，否则 12 根柱的窄格会截断。
        assertEquals("0 分钟", formatDuration(0L, DurationUnit.HOURS, DurationStyle.TICK))
        assertEquals("45分", formatDuration(45 * minuteMs, DurationUnit.HOURS, DurationStyle.TICK))
        assertEquals("1时", formatDuration(hourMs, DurationUnit.HOURS, DurationStyle.TICK))
        assertEquals("30时", formatDuration(30 * hourMs, DurationUnit.HOURS, DurationStyle.TICK))
        assertEquals("40分", formatDuration(40 * minuteMs, DurationUnit.DAYS, DurationStyle.TICK))
        assertEquals("5时", formatDuration(5 * hourMs + 20 * minuteMs, DurationUnit.DAYS, DurationStyle.TICK))
        assertEquals("1天", formatDuration(30 * hourMs, DurationUnit.DAYS, DurationStyle.TICK))
        assertEquals("2天", formatDuration(48 * hourMs, DurationUnit.DAYS, DurationStyle.TICK))
    }

    @Test
    fun `tick style stays within the width budget of a chart column`() {
        // 12 根柱的年度视图每格约 26dp；10sp 下超过 4 个字符就会触发省略。
        // 样本上界取「一格能代表的物理最大时长」：趋势柱是日/周/月聚合，
        // 月视图最坏情形是整月不睡不停（31 天 × 24 小时 = 744 小时）→「744时」4 字符。
        // 更大的值（如 9999 小时 ≈ 1.14 年）在单格里不可能出现，不属本约束范围。
        val maxMsPerBucket = 31L * 24 * hourMs
        val samples = listOf(0L, 59_000L, minuteMs, 59 * minuteMs, hourMs, 23 * hourMs, maxMsPerBucket)
        for (ms in samples) {
            for (unit in DurationUnit.entries) {
                val tick = formatDuration(ms, unit, DurationStyle.TICK)
                assertTrue(
                    "TICK 形态必须 ≤4 字符：ms=$ms unit=$unit tick='$tick'",
                    tick.length <= 4,
                )
            }
        }
    }

    @Test
    fun `all three styles agree on the magnitude of the same value`() {
        val samples = listOf(
            0L, 30_000L, 59_000L, minuteMs, 45 * minuteMs, 59 * minuteMs,
            hourMs, hourMs + 30 * minuteMs, 5 * hourMs + 20 * minuteMs,
            23 * hourMs, 24 * hourMs, 30 * hourMs, 48 * hourMs, 100 * hourMs,
        )
        for (ms in samples) {
            for (unit in DurationUnit.entries) {
                val words = DurationStyle.entries.map { magnitudeWord(formatDuration(ms, unit, it)) }
                assertEquals(
                    "同值同单位下三种样式必须同量级：ms=$ms unit=$unit -> $words",
                    1,
                    words.toSet().size,
                )
            }
        }
    }

    /** 取时长文本的量级词（天 > 小时 > 分钟），用于跨样式一致性断言。 */
    private fun magnitudeWord(text: String): String = when {
        text.contains("天") -> "天"
        text.contains("小时") || text.contains("时") -> "小时"
        else -> "分钟"
    }

    @Test
    fun `formatRelativeTime handles null and empty safely`() {
        assertEquals("", formatRelativeTime(null))
        assertEquals("", formatRelativeTime(""))
        assertEquals("", formatRelativeTime("   "))
    }

    @Test
    fun `formatRelativeTime formats recent intervals accurately`() {
        val now = 1_700_000_000_000L // arbitrary epoch millis

        // 30 seconds ago -> 刚刚
        assertEquals("刚刚", formatRelativeTime((now - 30_000L).toString(), now))
        // future timestamp -> 刚刚
        assertEquals("刚刚", formatRelativeTime((now + 10_000L).toString(), now))

        // 15 minutes ago
        assertEquals("15 分钟前", formatRelativeTime((now - 15 * 60_000L).toString(), now))

        // 3 hours ago (same day)
        assertEquals("3 小时前", formatRelativeTime((now - 3 * 3_600_000L).toString(), now))

        // 1 day ago -> 昨天
        assertEquals("昨天", formatRelativeTime((now - 24 * 3_600_000L).toString(), now))

        // 2 days ago -> 前天
        assertEquals("前天", formatRelativeTime((now - 48 * 3_600_000L).toString(), now))

        // 4 days ago -> 4 天前
        assertEquals("4 天前", formatRelativeTime((now - 4 * 24 * 3_600_000L).toString(), now))
    }

    @Test
    fun `formatRelativeTime falls back on invalid input`() {
        assertEquals("2026-01-01", formatRelativeTime("2026-01-01-invalid-tail"))
        assertEquals("random-str", formatRelativeTime("random-string-that-fails-parsing"))
    }
}

