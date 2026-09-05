package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.unit.dp

class ReaderChromeLayoutTest {
    @Test
    fun `tts and auto paging replace the normal multi row chrome with compact bars`() {
        assertEquals(ReaderBottomChromeMode.NORMAL, readerBottomChromeMode(false, false))
        assertEquals(ReaderBottomChromeMode.AUTO_PAGING, readerBottomChromeMode(false, true))
        assertEquals(ReaderBottomChromeMode.TTS, readerBottomChromeMode(true, true))
    }

    @Test
    fun `reader chrome overlays every content mode instead of shrinking the viewport`() {
        assertEquals(0f, readerContentTopReserve(usesPagedContent = true).value)
        assertEquals(0f, readerContentTopReserve(usesPagedContent = false).value)
    }

    @Test
    fun `paged top padding is pure font safety with no chrome reserve`() {
        // 顶栏为悬浮层，不再永久预留高度（此前 64dp 预留造成正文顶部约 1.5~2 行空白）；
        // 绘制基线垂直居中已保证字形不越行顶，安全区只覆盖墨迹溢出（0.15×字号，2~6dp）
        assertEquals(3.75f, pagedReaderContentTopPaddingDp(fontSizeSp = 25f), 0.001f)
    }

    @Test
    fun `normal tts and auto paging chrome never reserve permanent body height`() {
        assertEquals(0f, readerContentBottomReserve(showTts = false, autoPagingActive = false).value)
        assertEquals(0f, readerContentBottomReserve(showTts = false, autoPagingActive = true).value)
        assertEquals(0f, readerContentBottomReserve(showTts = true, autoPagingActive = false).value)
        assertEquals(0f, readerContentBottomReserve(showTts = true, autoPagingActive = true).value)
    }

    @Test
    fun `font scale and measured chrome cannot leak into pagination dimensions`() {
        assertEquals(0f, readerContentBottomReserve(false, false, fontScale = 1.5f).value, 0.001f)
        assertEquals(0f, readerContentBottomReserve(true, false, fontScale = 1.5f).value, 0.001f)
        assertEquals(0f, readerContentTopReserve(false, measuredTopChrome = 64.dp).value, 0.001f)
    }

    @Test
    fun `paged top safety follows the rendered font scale on accessibility sizes`() {
        assertEquals(
            3.75f,
            pagedReaderContentTopPaddingDp(
                fontSizeSp = 25f,
                fontScale = 1f,
            ),
            0.001f,
        )
        assertEquals(
            6f,
            pagedReaderContentTopPaddingDp(
                fontSizeSp = 25f,
                fontScale = 2f,
            ),
            0.001f,
        )
    }
}
