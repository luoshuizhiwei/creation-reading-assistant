package com.creationreadingassistant.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookFileClassifierTest {
    @Test
    fun `accepts supported extensions without depending on mime type`() {
        assertTrue(BookFileClassifier.isSupported("测试.TXT"))
        assertTrue(BookFileClassifier.isSupported("notes.Markdown"))
        assertTrue(BookFileClassifier.isSupported("book.epub", "application/octet-stream"))
    }

    @Test
    fun `accepts supported mime type when provider hides extension`() {
        assertTrue(BookFileClassifier.isSupported("download", "application/epub+zip"))
        assertTrue(BookFileClassifier.isSupported("document", "text/plain"))
    }

    @Test
    fun `skips hidden temporary and unsupported files`() {
        assertFalse(BookFileClassifier.isSupported(".nomedia", "text/plain"))
        assertFalse(BookFileClassifier.isSupported("draft.txt.part", "text/plain"))
        assertFalse(BookFileClassifier.isSupported("cover.jpg", "image/jpeg"))
    }
}
