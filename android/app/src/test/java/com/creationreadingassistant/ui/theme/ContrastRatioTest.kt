package com.creationreadingassistant.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
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

    // ── CategoryTint 分类色令牌（QWEN4-ANDROID-VISUAL-IMPL-v1 P1 新增；既有 4 项断言不动）──
    // 浅色沿用现网硬编码值（浅色零回归），深色为实算达标提亮版。下列断言对全部 6 套配色
    // × 浅/深的实际 surface 校验：图标色 ≥ 3:1（WCAG 非文本），文本色 ≥ 4.5:1（AA）。

    @Test
    fun `category tint icon colors meet 3 to 1 on every palette surface`() {
        AppPalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = paletteScheme(palette, dark)
                CategoryTints.forEach { (name, pair) ->
                    val tint = if (dark) pair.dark else pair.light
                    val ratio = contrastRatio(tint, scheme.surface)
                    assertTrue(
                        "$palette dark=$dark category '$name' icon/surface = $ratio, need >= 3.0",
                        ratio >= 3.0,
                    )
                }
            }
        }
    }

    @Test
    fun `category tint dark variants meet text AA on every dark surface`() {
        AppPalette.entries.forEach { palette ->
            val scheme = paletteScheme(palette, darkTheme = true)
            CategoryTints.forEach { (name, pair) ->
                val ratio = contrastRatio(pair.dark, scheme.surface)
                assertTrue(
                    "$palette dark category '$name' text/surface = $ratio, need >= 4.5",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `category tint light text variants meet AA on every light surface`() {
        AppPalette.entries.forEach { palette ->
            val scheme = paletteScheme(palette, darkTheme = false)
            CategoryTints.forEach { (name, pair) ->
                val textColor = pair.lightText ?: pair.light
                val ratio = contrastRatio(textColor, scheme.surface)
                assertTrue(
                    "$palette light category '$name' text/surface = $ratio, need >= 4.5",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `category tint light values are unchanged from the legacy hardcoded colors`() {
        // 浅色零回归守卫：令牌浅值必须与整改前的硬编码值逐位一致（防止误改浅色像素）。
        val legacy = mapOf(
            "violet" to 0xFF6750A4, "teal" to 0xFF00897B, "blue" to 0xFF00639B,
            "cyan" to 0xFF006874, "rust" to 0xFF984715, "slate" to 0xFF5B5B7E,
            "pine" to 0xFF386568, "plum" to 0xFF6B5778, "steel" to 0xFF4C626B,
            "link" to 0xFF1967D2, "sky" to 0xFF0284C7, "purple" to 0xFF7B1FA2,
            "diag" to 0xFF4A6572, "green" to 0xFF2E7D32, "gray" to 0xFF5E6266,
            "amber" to 0xFFD97706, "emerald" to 0xFF059669, "indigo" to 0xFF7C3AED,
            "blue2" to 0xFF2563EB, "rose" to 0xFFBE185D,
        )
        assertEquals(legacy.keys, CategoryTints.keys)
        legacy.forEach { (name, argb) ->
            assertEquals("category '$name' light value drifted", Color(argb), CategoryTints.getValue(name).light)
        }
    }
}
