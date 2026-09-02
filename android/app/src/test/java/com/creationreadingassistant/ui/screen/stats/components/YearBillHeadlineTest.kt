package com.creationreadingassistant.ui.screen.stats.components

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
}
