package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomableProgressScrubberTest {

    @Test
    fun `preview label always describes the current chapter percentage`() {
        assertEquals("本章 · 0%", chapterProgressPreviewLabel(-1f))
        assertEquals("本章 · 52%", chapterProgressPreviewLabel(52.8f))
        assertEquals("本章 · 100%", chapterProgressPreviewLabel(101f))
    }
}
