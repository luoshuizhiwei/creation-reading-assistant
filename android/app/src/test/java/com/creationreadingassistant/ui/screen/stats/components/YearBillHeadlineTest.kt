package com.creationreadingassistant.ui.screen.stats.components

import com.creationreadingassistant.data.settings.DurationUnit
import com.creationreadingassistant.ui.screen.stats.formatCompactDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定 [buildYearHeadline] 的分支逻辑（纯函数，与渲染解耦）。
 *
 * 函数按 when 顺序命中首条满足的分支：
 * 1) readingDays == 0
 * 2) completedBooks >= 12
 * 3) longestStreak >= 30
 * 4) hours >= 50
 * 5) completedBooks >= 3
 * 6) else
 * 其中分支 3 含一个依赖当前年份天数的百分比，用 contains 而非全等断言。
 */
class YearBillHeadlineTest {

    @Test
    fun `no reading days shows opening prompt`() {
        assertEquals(
            "今年还没打开过书，新的一年一起开卷吧。",
            buildYearHeadline(totalMs = 0L, readingDays = 0, completedBooks = 0, longestStreak = 0),
        )
    }

    @Test
    fun `twelve or more completed books highlights monthly pace`() {
        val text = buildYearHeadline(
            totalMs = 400_000_000L,
            readingDays = 10,
            completedBooks = 12,
            longestStreak = 5,
        )
        assertTrue(text.contains("平均每月读完 1 本"))
        assertTrue(text.contains("书是你最稳的年度旅伴"))
    }

    @Test
    fun `long streak thirty plus mentions consecutive days`() {
        val text = buildYearHeadline(
            totalMs = 10_000_000L, // ~2.8h，确保 hours < 50
            readingDays = 10,
            completedBooks = 0,
            longestStreak = 30,
        )
        assertTrue(text.contains("最长连续阅读 30 天"))
    }

    @Test
    fun `fifty plus hours summarizes accumulated time`() {
        val text = buildYearHeadline(
            totalMs = 200_000_000L, // ~55.5h
            readingDays = 10,
            completedBooks = 2,
            longestStreak = 5,
        )
        assertTrue(text.contains("小时"))
        assertTrue(text.contains("开卷"))
    }

    @Test
    fun `three plus completed books names the count`() {
        val text = buildYearHeadline(
            totalMs = 10_000_000L,
            readingDays = 10,
            completedBooks = 3,
            longestStreak = 5,
        )
        assertTrue(text.contains("今年读完了 3 本书"))
    }

    @Test
    fun `few days and few books falls to gentle default`() {
        assertEquals(
            "在 5 天里翻开书页，字句正在悄悄积累。",
            buildYearHeadline(
                totalMs = 1_000_000L,
                readingDays = 5,
                completedBooks = 1,
                longestStreak = 2,
            ),
        )
    }

    // ===================== S15-F12-R1：主值与金句必须同源 =====================
    //
    // 旧实现先把 totalReadingMs 抬成**整数小时**（`roundToInt()`）再回乘成 ms 交给
    // 公共 formatter，而金句直接用原始 ms。于是 50 小时 30 分在满足 `hours >= 50`
    // 分支时，主值显示「51 小时」、金句显示「累计 50 小时」——同一张卡自相矛盾。
    // 下面两条用例把「主值最终文本 == 公共 formatter 对原始时长的结果」钉死。

    private val hourMs = 3_600_000L
    private val minuteMs = 60_000L

    @Test
    fun `card value equals the public formatter result on the original duration`() {
        val samples = listOf(
            0L,
            30 * minuteMs,                       // 不足一小时
            50 * hourMs + 30 * minuteMs,         // R1 复现边界：50.5 小时
            47 * hourMs + 30 * minuteMs,         // DAYS 侧的同一类边界（47.5 小时）
            23 * hourMs + 30 * minuteMs,         // 跨 24 小时门槛前
            24 * hourMs + 30 * minuteMs,         // 跨 24 小时门槛后
            100 * hourMs + 59 * minuteMs,
        )
        for (ms in samples) {
            for (unit in DurationUnit.entries) {
                assertEquals(
                    "主值最终文本必须等于公共 formatter 对原始时长的结果：ms=$ms unit=$unit",
                    formatCompactDuration(ms, unit),
                    yearBillCardValue(ms, unit),
                )
            }
        }
    }

    @Test
    fun `fifty hours thirty minutes never contradicts itself between card and headline`() {
        // 该边界必须命中 `hours >= 50` 分支（completedBooks < 3、streak < 30）。
        val totalMs = 50 * hourMs + 30 * minuteMs
        for (unit in DurationUnit.entries) {
            val cardValue = yearBillCardValue(totalMs, unit)
            val headline = buildYearHeadline(
                totalMs = totalMs,
                readingDays = 10,
                completedBooks = 2,
                longestStreak = 5,
                unit = unit,
            )
            assertFalse("主值不得被抬成 51：unit=$unit card='$cardValue'", cardValue.contains("51"))
            assertTrue(
                "金句必须逐字包含主值的时长文本：unit=$unit card='$cardValue' headline='$headline'",
                headline.contains(cardValue),
            )
        }
    }

    @Test
    fun `days unit rolls over consistently between card and headline`() {
        // 47.5 小时：旧实现先把小时抬成 48（roundToInt）再交给 DAYS，于是主值显示
        // 「2 天」；按原始时长应是 47 小时 → 「1 天」。这是同一类提前进位的回归点。
        val totalMs = 47 * hourMs + 30 * minuteMs
        val cardValue = yearBillCardValue(totalMs, DurationUnit.DAYS)
        assertEquals("1 天", cardValue)
        assertFalse("主值不得提前进位到 2 天：'$cardValue'", cardValue.contains("2 天"))
    }

    @Test
    fun `headline never contradicts the card whenever it mentions a duration`() {
        // 不变量：**只要金句里出现时长，它就必须逐字等于主值**。
        //
        // buildYearHeadline 只有两条分支会写出时长（都用同一个 $spoken）：
        //   - `completedBooks >= 12` →「共翻阅 $spoken」
        //   - `hours >= 50f`         →「累计 $spoken、…」
        // 其余分支只出现「N 天」这类**天数计数**（readingDays），不是时长换算，
        // 因此不在此断言范围内。这里显式按分支语义取样本，不做字符串启发式猜测。
        data class Case(val ms: Long, val books: Int, val streak: Int, val label: String)

        val durationBearing = listOf(
            Case(50 * hourMs + 30 * minuteMs, books = 2, streak = 5, label = "hours>=50 边界"),
            Case(51 * hourMs, books = 2, streak = 5, label = "hours>=50 整点"),
            Case(400_000_000L, books = 12, streak = 5, label = "books>=12 分支"),
        )
        for (case in durationBearing) {
            for (unit in DurationUnit.entries) {
                val cardValue = yearBillCardValue(case.ms, unit)
                val headline = buildYearHeadline(
                    totalMs = case.ms,
                    readingDays = 10,
                    completedBooks = case.books,
                    longestStreak = case.streak,
                    unit = unit,
                )
                assertTrue(
                    "含时长的金句必须逐字包含主值：${case.label} unit=$unit card='$cardValue' headline='$headline'",
                    headline.contains(cardValue),
                )
            }
        }
    }
}
