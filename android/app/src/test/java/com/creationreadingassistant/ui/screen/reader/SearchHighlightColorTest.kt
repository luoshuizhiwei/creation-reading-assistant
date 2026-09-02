package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.paperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 搜索命中高亮颜色 / 语义选择 seam（P1-B）：
 * [searchHighlightColor] 是 TXT 滚动、EPUB 滚动、分页、Markdown 共用的统一来源，
 * [ttsSentenceHighlightColor] 继续供 TTS 句高亮使用；二者在任何纸上下都必须可区分，
 * 且搜索色随纸自适应（不是单一硬编码亮色）。
 */
class SearchHighlightColorTest {

    private val palettes: List<ReaderPaperPalette> = listOf(
        paperPalette("white", darkTheme = false),
        paperPalette("warm", darkTheme = false),
        paperPalette("green", darkTheme = false),
        paperPalette("night", darkTheme = true),
        paperPalette("sepia_dark", darkTheme = true),
    )

    @Test
    fun `search highlight differs from tts sentence highlight on every paper`() {
        palettes.forEach { paper ->
            assertNotEquals(
                "搜索命中高亮不得复用 TTS 句高亮底色",
                ttsSentenceHighlightColor(paper),
                searchHighlightColor(paper),
            )
        }
    }

    @Test
    fun `search highlight adapts to paper palette`() {
        val white = searchHighlightColor(paperPalette("white", darkTheme = false))
        val warm = searchHighlightColor(paperPalette("warm", darkTheme = false))
        val green = searchHighlightColor(paperPalette("green", darkTheme = false))
        val night = searchHighlightColor(paperPalette("night", darkTheme = true))

        // 随纸变化：不同纸的「黄」批注实色不同（白/夜读同值由调色板定义，不是硬编码亮色）
        assertNotEquals(white, warm)
        assertNotEquals(warm, green)
        assertNotEquals(green, night)
    }

    @Test
    fun `search highlight is translucent annotation scrim`() {
        palettes.forEach { paper ->
            // Compose Color 将 alpha 量化为 8 位（0.18 → 46/255）
            assertEquals(0.18f, searchHighlightColor(paper).alpha, 0.01f)
        }
    }

    @Test
    fun `tts highlight stays on accent scrim`() {
        palettes.forEach { paper ->
            assertEquals(0.22f, ttsSentenceHighlightColor(paper).alpha, 0.01f)
            assertEquals(paper.accent, ttsSentenceHighlightColor(paper).copy(alpha = 1f))
        }
    }
}
