package com.creationreadingassistant.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2.1 对比度自动化验证：确保所有配色主题的核心文本对满足 AA 标准（≥ 4.5:1）。
 *
 * 遍历 [AppPalette.entries] × light/dark，通过 [paletteScheme] 获取完整 ColorScheme，
 * 对 4 项核心前景/背景对计算对比度并断言。未来新增主题自动被覆盖。
 */
class ContrastRatioTest {

    /**
     * WCAG 2.1 相对亮度。
     * sRGB 通道先线性化（gamma 解码），再加权求和。
     */
    private fun luminance(c: Color): Double {
        fun linearize(channel: Float): Double {
            val v = channel.toDouble()
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linearize(c.red) + 0.7152 * linearize(c.green) + 0.0722 * linearize(c.blue)
    }

    /**
     * WCAG 对比度比 = (L_lighter + 0.05) / (L_darker + 0.05)
     */
    private fun contrastRatio(fg: Color, bg: Color): Double {
        val lFg = luminance(fg)
        val lBg = luminance(bg)
        val lighter = maxOf(lFg, lBg)
        val darker = minOf(lFg, lBg)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `all palettes meet WCAG AA for onSurface vs surface`() {
        AppPalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = paletteScheme(palette, dark)
                val ratio = contrastRatio(scheme.onSurface, scheme.surface)
                assertTrue(
                    "${palette.displayName} dark=$dark onSurface/surface = $ratio, need >= 4.5",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `all palettes meet WCAG AA for onSurfaceVariant vs surfaceVariant`() {
        AppPalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = paletteScheme(palette, dark)
                val ratio = contrastRatio(scheme.onSurfaceVariant, scheme.surfaceVariant)
                assertTrue(
                    "${palette.displayName} dark=$dark onSurfaceVariant/surfaceVariant = $ratio, need >= 4.5",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `all palettes meet WCAG AA for onBackground vs background`() {
        AppPalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = paletteScheme(palette, dark)
                val ratio = contrastRatio(scheme.onBackground, scheme.background)
                assertTrue(
                    "${palette.displayName} dark=$dark onBackground/background = $ratio, need >= 4.5",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `all palettes meet WCAG AA for primary vs background`() {
        AppPalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = paletteScheme(palette, dark)
                val ratio = contrastRatio(scheme.primary, scheme.background)
                assertTrue(
                    "${palette.displayName} dark=$dark primary/background = $ratio, need >= 4.5",
                    ratio >= 4.5,
                )
            }
        }
    }
}
