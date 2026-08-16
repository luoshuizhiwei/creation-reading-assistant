package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具栏/自动翻页的选项模型：自动隐藏时间与自动翻页速度档位。
 *
 * 规格（用户反馈 2）：自动隐藏时间必须明确显示并可选 0/3/4/5/8 秒；当前值（默认 4）
 * 显示准确；0 = 不定时隐藏。
 */
class ReaderChromeOptionsTest {

    @Test
    fun `auto hide options include zero and the default four seconds`() {
        assertEquals(listOf(0, 3, 4, 5, 8), AUTO_HIDE_SECOND_OPTIONS)
        assertTrue(AUTO_HIDE_SECOND_OPTIONS.contains(4))
        assertEquals(0, AUTO_HIDE_SECOND_OPTIONS.first())
    }

    @Test
    fun `zero means never auto hide`() {
        assertEquals("不隐藏", autoHideSecondsLabel(0))
    }

    @Test
    fun `labels show exact seconds`() {
        assertEquals("3 秒", autoHideSecondsLabel(3))
        assertEquals("4 秒", autoHideSecondsLabel(4))
        assertEquals("5 秒", autoHideSecondsLabel(5))
        assertEquals("8 秒", autoHideSecondsLabel(8))
    }

    @Test
    fun `nearest option snaps stored values without losing the default`() {
        assertEquals(4, nearestAutoHideOption(4))
        assertEquals(0, nearestAutoHideOption(0))
        assertEquals(5, nearestAutoHideOption(6))
        assertEquals(3, nearestAutoHideOption(2))
    }

    @Test
    fun `auto page speed clamps to one through ten`() {
        assertEquals(1, clampAutoPageSpeed(-5))
        assertEquals(10, clampAutoPageSpeed(99))
        assertEquals(5, clampAutoPageSpeed(5))
        assertEquals(AUTO_PAGE_SPEED_MIN, 1)
        assertEquals(AUTO_PAGE_SPEED_MAX, 10)
    }

    @Test
    fun `auto page speed step stays within one through ten`() {
        // 工具栏 -/+ 在调用回调前步进并钳制：绝不传出 0/11。
        assertEquals(1, clampAutoPageSpeed(1 - 1))
        assertEquals(1, clampAutoPageSpeed(0 - 1))
        assertEquals(10, clampAutoPageSpeed(10 + 1))
        assertEquals(10, clampAutoPageSpeed(99 + 1))
        assertEquals(4, clampAutoPageSpeed(5 - 1))
        assertEquals(6, clampAutoPageSpeed(5 + 1))
    }
}
