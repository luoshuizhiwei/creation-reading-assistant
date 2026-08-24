package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderInitialChapterStateTest {

    @Test
    fun `loaded EPUB chapter is available before the first pager frame`() {
        assertEquals(1, initialReaderChapterIndex(savedEpubChapterIndex = 1))
    }

    @Test
    fun `non EPUB reader starts from chapter zero`() {
        assertEquals(0, initialReaderChapterIndex(savedEpubChapterIndex = null))
    }
}
