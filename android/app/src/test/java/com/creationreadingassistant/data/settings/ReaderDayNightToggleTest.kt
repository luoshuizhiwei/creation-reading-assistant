package com.creationreadingassistant.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一键日/夜切换（对齐 QQ 阅读阅读页灯绳）的纯逻辑验收。
 *
 * 覆盖三类不变量：
 * 1. **夜间态判定**按纸张明暗，`follow` 交给外壳解析；
 * 2. **切换目标**取「上次该侧用过的纸」，不把用户的暖纸/护眼静默变成白纸；
 * 3. **记忆更新**只在选中明确属于某一侧的纸时发生，`follow` 不构成记忆。
 */
class ReaderDayNightToggleTest {

    // ── 夜间态判定 ──────────────────────────────────────────────

    @Test
    fun `亮纸一律判为日间，与外壳明暗无关`() {
        listOf("white", "warm", "green").forEach { paper ->
            assertFalse("$paper 应为日间", ReaderDayNightToggle.isNight(paper, appDark = false))
            assertFalse("$paper 应为日间（外壳深色也不改变）", ReaderDayNightToggle.isNight(paper, appDark = true))
        }
    }

    @Test
    fun `暗纸一律判为夜间，与外壳明暗无关`() {
        listOf("night", "sepia_dark").forEach { paper ->
            assertTrue("$paper 应为夜间", ReaderDayNightToggle.isNight(paper, appDark = true))
            assertTrue("$paper 应为夜间（外壳浅色也不改变）", ReaderDayNightToggle.isNight(paper, appDark = false))
        }
    }

    @Test
    fun `follow 纸张的明暗由外壳解析`() {
        assertTrue(ReaderDayNightToggle.isNight("follow", appDark = true))
        assertFalse(ReaderDayNightToggle.isNight("follow", appDark = false))
    }

    @Test
    fun `未知纸张 key 按外壳明暗兜底，不崩溃`() {
        assertTrue(ReaderDayNightToggle.isNight("no-such-paper", appDark = true))
        assertFalse(ReaderDayNightToggle.isNight("no-such-paper", appDark = false))
    }

    // ── 切换目标 ────────────────────────────────────────────────

    @Test
    fun `从日间切到夜间时落到上次暗纸`() {
        val target = ReaderDayNightToggle.next(
            background = "warm",
            appDark = false,
            lastLightPaper = "warm",
            lastDarkPaper = "sepia_dark",
        )
        assertEquals("sepia_dark", target.paper)
        assertTrue(target.dark)
    }

    @Test
    fun `从夜间切回日间时落到上次亮纸，暖纸不会变成白纸`() {
        val target = ReaderDayNightToggle.next(
            background = "night",
            appDark = true,
            lastLightPaper = "warm",
            lastDarkPaper = "night",
        )
        assertEquals("warm", target.paper)
        assertFalse(target.dark)
    }

    @Test
    fun `从未选过暗纸时回退夜读`() {
        val target = ReaderDayNightToggle.next(
            background = "white",
            appDark = false,
            lastLightPaper = "white",
            lastDarkPaper = "",
        )
        assertEquals(ReaderDayNightToggle.DEFAULT_DARK_PAPER, target.paper)
        assertEquals("night", target.paper)
    }

    @Test
    fun `从未选过亮纸时回退白纸`() {
        val target = ReaderDayNightToggle.next(
            background = "night",
            appDark = true,
            lastLightPaper = "",
            lastDarkPaper = "night",
        )
        assertEquals(ReaderDayNightToggle.DEFAULT_LIGHT_PAPER, target.paper)
        assertEquals("white", target.paper)
    }

    @Test
    fun `记忆值非法时回退默认，不把坏值当目标纸色`() {
        // 例如旧版本/手改写下的脏数据：切到夜间时不能把 "follow" 或乱码写成目标
        val target = ReaderDayNightToggle.next(
            background = "white",
            appDark = false,
            lastLightPaper = "white",
            lastDarkPaper = "follow",
        )
        assertEquals("night", target.paper)
    }

    @Test
    fun `记忆值串侧时也会被拒（亮纸不能当夜间目标）`() {
        val target = ReaderDayNightToggle.next(
            background = "white",
            appDark = false,
            lastLightPaper = "white",
            lastDarkPaper = "warm",
        )
        assertEquals("night", target.paper)
    }

    @Test
    fun `follow 纸张上拉灯绳会落到具体纸色，不留 follow`() {
        // 写 follow 会让纸张明暗仍由外壳决定，而外壳也在同一次动作里被改写 —— 会互相依赖
        val toDark = ReaderDayNightToggle.next(
            background = "follow",
            appDark = false,
            lastLightPaper = "green",
            lastDarkPaper = "night",
        )
        assertEquals("night", toDark.paper)
        assertTrue(toDark.dark)

        val toLight = ReaderDayNightToggle.next(
            background = "follow",
            appDark = true,
            lastLightPaper = "green",
            lastDarkPaper = "night",
        )
        assertEquals("green", toLight.paper)
        assertFalse(toLight.dark)
    }

    @Test
    fun `来回切换是幂等往返的`() {
        var paper = "warm"
        var dark = false
        repeat(4) {
            val target = ReaderDayNightToggle.next(
                background = paper,
                appDark = dark,
                lastLightPaper = "warm",
                lastDarkPaper = "sepia_dark",
            )
            paper = target.paper
            dark = target.dark
        }
        // 偶数次切换后回到起点
        assertEquals("warm", paper)
        assertFalse(dark)
    }

    // ── 记忆更新 ────────────────────────────────────────────────

    @Test
    fun `选亮纸只更新亮侧记忆`() {
        val remembered = ReaderDayNightToggle.rememberPaper("warm")
        assertEquals("warm", remembered?.light)
        assertNull(remembered?.dark)
    }

    @Test
    fun `选暗纸只更新暗侧记忆`() {
        val remembered = ReaderDayNightToggle.rememberPaper("sepia_dark")
        assertEquals("sepia_dark", remembered?.dark)
        assertNull(remembered?.light)
    }

    @Test
    fun `选 follow 不构成记忆，两侧都保持原值`() {
        // follow 没表达「偏好哪种亮/暗纸」，拿去当记忆值会抹掉上一次的真实偏好
        assertNull(ReaderDayNightToggle.rememberPaper("follow"))
    }

    @Test
    fun `未知纸张不构成记忆`() {
        assertNull(ReaderDayNightToggle.rememberPaper(""))
        assertNull(ReaderDayNightToggle.rememberPaper("no-such-paper"))
    }

    @Test
    fun `明暗词表互不重叠，且都不含 follow`() {
        assertTrue(ReaderDayNightToggle.LIGHT_PAPERS.intersect(ReaderDayNightToggle.DARK_PAPERS).isEmpty())
        assertFalse("follow" in ReaderDayNightToggle.LIGHT_PAPERS)
        assertFalse("follow" in ReaderDayNightToggle.DARK_PAPERS)
    }
}
