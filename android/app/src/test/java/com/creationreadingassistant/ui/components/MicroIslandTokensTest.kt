package com.creationreadingassistant.ui.components

import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.theme.DefaultComponentSpec
import com.creationreadingassistant.ui.theme.paperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 微岛令牌护栏单测：锁定 [DefaultComponentSpec] 新增微岛档位默认值与阅读器语义令牌，
 * 防止后续「一处调参」时被无意漂移。风格参照既有 AppPaletteTest / ReaderPaperPaletteTest。
 *
 * 这些默认值取现网最常见值（先做等价替换），任何改动都应是有意的全域设计决策。
 */
class MicroIslandTokensTest {

    @Test
    fun islandRadiusTokensKeepCanonicalDefaults() {
        assertEquals(14.dp, DefaultComponentSpec.islandRadius)
        assertEquals(18.dp, DefaultComponentSpec.dockRadius)
        assertEquals(9.dp, DefaultComponentSpec.pedestalRadius)
        assertEquals(12.dp, DefaultComponentSpec.hintRadius)
    }

    @Test
    fun hairlineTokensKeepCanonicalDefaults() {
        assertEquals(0.6.dp, DefaultComponentSpec.hairlineBorderWidth)
        assertEquals(0.35f, DefaultComponentSpec.hairlineAlpha, 0.0001f)
    }

    @Test
    fun hairlineBorderIsFinerThanCardBorder() {
        // 发丝边框应比卡片主边框更细，否则失去「发丝」语义。
        assertTrue(
            "hairlineBorderWidth(${DefaultComponentSpec.hairlineBorderWidth}) 应 < borderWidth(${DefaultComponentSpec.borderWidth})",
            DefaultComponentSpec.hairlineBorderWidth < DefaultComponentSpec.borderWidth,
        )
    }

    @Test
    fun hairlineAlphaStaysInValidRange() {
        val alpha = DefaultComponentSpec.hairlineAlpha
        assertTrue("hairlineAlpha 应在 (0,1] 区间，实际=$alpha", alpha > 0f && alpha <= 1f)
    }

    @Test
    fun readerPanelElevationIsDecorationFreeZero() {
        // 双轨关键：阅读器面板恒定 0dp 无阴影，避免翻页动画层帧预算被侵蚀。
        assertEquals(0.dp, paperPalette("white", darkTheme = false).panelElevation)
        assertEquals(0.dp, paperPalette("night", darkTheme = true).panelElevation)
    }

    @Test
    fun readerSemanticTokensDeriveFromPaperNotShell() {
        // 阅读器 chrome/sheet 语义令牌随纸变化，且与外壳 ColorScheme 解耦。
        val white = paperPalette("white", darkTheme = false)
        val night = paperPalette("night", darkTheme = true)
        assertEquals(white.panelStrong, white.chipBg)
        assertEquals(white.outlineVariant, white.divider)
        assertEquals(white.panel, white.sheetSectionBg)
        // 不同纸张档应派生出不同的 chip / divider 色，证明未硬编码到单一外壳色。
        assertTrue(white.chipBg != night.chipBg)
        assertTrue(white.divider != night.divider)
    }
}
