package com.creationreadingassistant.feature.reader

import com.creationreadingassistant.data.local.entity.BookEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookLocalAvailabilityTest {
    @Test
    fun `zero size epub with local uri remains openable`() {
        val book = book(size = 0, localUri = "content://books/test.epub")

        assertTrue(book.hasLocalBookSource())
    }

    @Test
    fun `missing or failed content is not treated as local`() {
        assertFalse(book(contentStatus = "missing").hasLocalBookSource())
        assertFalse(book(contentStatus = "failed").hasLocalBookSource())
        assertFalse(book(contentStatus = "downloading").hasLocalBookSource())
    }

    @Test
    fun `book without uri or internal path is not treated as local`() {
        assertFalse(book(localUri = null).hasLocalBookSource())
    }

    @Test
    fun `missing file uri is not presented as readable`() {
        assertFalse(book(localUri = "file:///definitely/missing/test.epub").hasLocalBookSource())
    }

    private fun book(
        size: Int = 0,
        localUri: String? = "content://books/test.epub",
        localPath: String? = null,
        contentStatus: String = "available",
    ) = BookEntity(
        id = "epub_test",
        title = "测试书",
        format = "epub",
        size = size,
        local_uri = localUri,
        local_content_path = localPath,
        content_status = contentStatus,
        updated_at = "2026-07-27T00:00:00Z",
    )
}
