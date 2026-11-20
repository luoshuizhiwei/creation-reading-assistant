package com.creationreadingassistant.ui.theme

import androidx.compose.ui.graphics.Color
import com.creationreadingassistant.ui.theme.lab.ThemeLabDirection
import com.creationreadingassistant.ui.theme.lab.colorScheme
import com.creationreadingassistant.ui.theme.lab.labDirection
import com.creationreadingassistant.ui.theme.lab.paperDark
import com.creationreadingassistant.ui.theme.lab.paperLight
import com.creationreadingassistant.ui.theme.lab.sealColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Theme Lab v2 · P1 候选方向对比度门禁（与既有 [ContrastRatioTest] 同口径，文件名匹配 `*ContrastRatioTest*`）。
 *
 * 门槛（P1 合同）：正文 / 次要文字 ≥ 4.5；图标与可交互边界 ≥ 3.0；强调色对背景 ≥ 3.0。
 * 既有 ContrastRatioTest 的 primary/background ≥ 4.5 更严，这里同样执行。
 * 色值与 contrast_calc.py 实算表一一对应（504 项全过）；任何方向突破门槛即淘汰，不放宽。
 */
class ThemeLabContrastRatioTest {

    private fun luminance(c: Color): Double {
        fun linearize(channel: Float): Double {
            val v = channel.toDouble()
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linearize(c.red) + 0.7152 * linearize(c.green) + 0.0722 * linearize(c.blue)
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val lFg = luminance(fg)
        val lBg = luminance(bg)
        return (maxOf(lFg, lBg) + 0.05) / (minOf(lFg, lBg) + 0.05)
    }

    private fun assertRatio(tag: String, fg: Color, bg: Color, need: Double) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$tag = $ratio, need >= $need", ratio >= need)
    }

    /** 新 entry 与方向映射一致，且 paletteScheme 分支取的就是方向 scheme（单一真源守卫）。 */
    @Test
    fun `lab palette entries map to directions and schemes`() {
        val expected = mapOf(
            AppPalette.LAB_XUAN_GRAY to ThemeLabDirection.XUAN_GRAY,
            AppPalette.LAB_YIN_SEAL to ThemeLabDirection.YIN_SEAL,
            AppPalette.LAB_CHEN_GUANG to ThemeLabDirection.CHEN_GUANG,
            AppPalette.LAB_QING_CI to ThemeLabDirection.QING_CI,
            AppPalette.LAB_GARDEN to ThemeLabDirection.GARDEN,
        )
        expected.forEach { (palette, direction) ->
            assertEquals(direction, palette.labDirection)
            listOf(false, true).forEach { dark ->
                // ColorScheme 无结构化 equals（引用相等），逐槽位比对证明 paletteScheme 委托方向 scheme。
                val a = direction.colorScheme(dark)
                val b = paletteScheme(palette, dark)
                val tag = "${direction.name} dark=$dark"
                assertEquals("$tag primary", a.primary, b.primary)
                assertEquals("$tag onPrimary", a.onPrimary, b.onPrimary)
                assertEquals("$tag primaryContainer", a.primaryContainer, b.primaryContainer)
                assertEquals("$tag onPrimaryContainer", a.onPrimaryContainer, b.onPrimaryContainer)
                assertEquals("$tag background", a.background, b.background)
                assertEquals("$tag onBackground", a.onBackground, b.onBackground)
                assertEquals("$tag surface", a.surface, b.surface)
                assertEquals("$tag onSurface", a.onSurface, b.onSurface)
                assertEquals("$tag surfaceVariant", a.surfaceVariant, b.surfaceVariant)
                assertEquals("$tag onSurfaceVariant", a.onSurfaceVariant, b.onSurfaceVariant)
                assertEquals("$tag outline", a.outline, b.outline)
                assertEquals("$tag outlineVariant", a.outlineVariant, b.outlineVariant)
                assertEquals("$tag inversePrimary", a.inversePrimary, b.inversePrimary)
                assertEquals("$tag error", a.error, b.error)
            }
        }
        // 旧配色不进 lab（回退边界）。
        assertEquals(null, AppPalette.PAPER_INK.labDirection)
        assertEquals(null, AppPalette.CLEAR_BLUE.labDirection)
    }

    /** 外壳正文 / 次要文字 / 强调 / 印章 / 按钮，明暗双套。 */
    @Test
    fun `lab shell schemes meet P1 contrast gates`() {
        ThemeLabDirection.entries.forEach { direction ->
            listOf(false, true).forEach { dark ->
                val s = direction.colorScheme(dark)
                val tag = "${direction.name} dark=$dark"
                assertRatio("$tag onSurface/surface", s.onSurface, s.surface, 4.5)
                assertRatio("$tag onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant, 4.5)
                assertRatio("$tag onBackground/background", s.onBackground, s.background, 4.5)
                assertRatio("$tag primary/background", s.primary, s.background, 4.5)
                assertRatio("$tag onSurfaceVariant/surface", s.onSurfaceVariant, s.surface, 4.5)
                assertRatio("$tag onSurfaceVariant/background", s.onSurfaceVariant, s.background, 4.5)
                assertRatio("$tag primary/surface", s.primary, s.surface, 3.0)
                assertRatio("$tag onPrimary/primary", s.onPrimary, s.primary, 4.5)
                assertRatio("$tag onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer, 4.5)
                assertRatio("$tag seal/background", direction.sealColor(dark), s.background, 3.0)
                assertRatio("$tag seal/surface", direction.sealColor(dark), s.surface, 3.0)
                assertRatio("$tag onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer, 4.5)
            }
        }
    }

    /** 既有 20 分类色对全部新 surface：图标 ≥ 3.0、文本 ≥ 4.5（与既有门禁同口径）。 */
    @Test
    fun `category tints meet gates on every lab surface`() {
        ThemeLabDirection.entries.forEach { direction ->
            listOf(false, true).forEach { dark ->
                val s = direction.colorScheme(dark)
                CategoryTints.forEach { (name, pair) ->
                    val tint = if (dark) pair.dark else pair.light
                    assertRatio("${direction.name} dark=$dark tint[$name] icon/surface", tint, s.surface, 3.0)
                    val textColor = if (dark) pair.dark else (pair.lightText ?: pair.light)
                    assertRatio("${direction.name} dark=$dark tint[$name] text/surface", textColor, s.surface, 4.5)
                }
            }
        }
    }

    /** 方向阅读纸张（follow remap）：正文 / 次要 / chrome 图标边界 / 实边，明暗双套。 */
    @Test
    fun `lab reader papers meet P1 contrast gates`() {
        ThemeLabDirection.entries.forEach { direction ->
            listOf(direction.paperLight to "light", direction.paperDark to "dark").forEach { (paper, mode) ->
                val tag = "${direction.name} $mode paper"
                assertRatio("$tag fg/bg", paper.fg, paper.bg, 4.5)
                assertRatio("$tag fgMuted/bg", paper.fgMuted, paper.bg, 4.5)
                assertRatio("$tag fgMuted/panelStrong", paper.fgMuted, paper.panelStrong, 4.5)
                assertRatio("$tag fg/panel", paper.fg, paper.panel, 4.5)
                assertRatio("$tag accent/bg", paper.accent, paper.bg, 4.5)
                assertRatio("$tag accent/panel", paper.accent, paper.panel, 4.5)
                assertRatio("$tag accent/panelStrong", paper.accent, paper.panelStrong, 4.5)
                assertRatio("$tag onAccent/accent", paper.onAccent, paper.accent, 4.5)
                assertRatio("$tag outline/panel", paper.outline, paper.panel, 3.0)
                assertRatio("$tag outline/bg", paper.outline, paper.bg, 3.0)
            }
        }
    }
}