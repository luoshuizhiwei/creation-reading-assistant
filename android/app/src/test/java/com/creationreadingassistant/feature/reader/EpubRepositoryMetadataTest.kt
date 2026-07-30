package com.creationreadingassistant.feature.reader

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.domain.model.EpubBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubRepositoryMetadataTest {
    private val parsed = EpubBook(
        id = "epub_test",
        title = "解析后的书名",
        author = "解析后的作者",
        chapters = emptyList(),
        localUri = "content://books/current.epub",
        cachedEpubPath = "cache/current.epub",
    )

    @Test
    fun `opening existing epub preserves stored metadata when size lookup is unavailable`() {
        val existing = BookEntity(
            id = parsed.id,
            title = "旧书名",
            author = null,
            format = "epub",
            original_file_name = "original.epub",
            content_hash = "hash",
            size = 3_143_847,
            local_uri = "content://books/old.epub",
            cover_data_url = "data:image/png;base64,cover",
            imported_at = "2026-07-01T00:00:00Z",
            payload = """{"source":"import"}""",
            revision = 7,
            updated_at = "2026-07-01T00:00:00Z",
        )

        val merged = mergeEpubBookRecord(
            existing = existing,
            parsed = parsed,
            originalFileName = "current.epub",
            resolvedSize = 0,
            now = "2026-07-27T00:00:00Z",
        )

        assertEquals(3_143_847, merged.size)
        assertEquals("original.epub", merged.original_file_name)
        assertEquals("hash", merged.content_hash)
        assertEquals("data:image/png;base64,cover", merged.cover_data_url)
        assertEquals("""{"source":"import"}""", merged.payload)
        assertEquals(7, merged.revision)
        assertEquals("content://books/current.epub", merged.local_uri)
        assertEquals("available", merged.content_status)
    }

    @Test
    fun `opening historical zero size epub backfills resolved file size`() {
        val existing = BookEntity(
            id = parsed.id,
            title = parsed.title,
            format = "epub",
            size = 0,
            local_uri = parsed.localUri,
            updated_at = "2026-07-01T00:00:00Z",
        )

        val merged = mergeEpubBookRecord(
            existing = existing,
            parsed = parsed,
            originalFileName = "current.epub",
            resolvedSize = 3_143_847,
            now = "2026-07-27T00:00:00Z",
        )

        assertEquals(3_143_847, merged.size)
    }

    @Test
    fun `opening new epub stores size and import timestamp`() {
        val created = mergeEpubBookRecord(
            existing = null,
            parsed = parsed.copy(author = null),
            originalFileName = "current.epub",
            resolvedSize = 42,
            now = "2026-07-27T00:00:00Z",
        )

        assertEquals(42, created.size)
        assertEquals("2026-07-27T00:00:00Z", created.imported_at)
        assertNull(created.author)
    }
}
