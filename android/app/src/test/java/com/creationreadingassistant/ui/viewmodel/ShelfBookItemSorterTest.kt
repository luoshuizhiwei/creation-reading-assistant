package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfBookItemSorterTest {

    private fun book(
        id: String,
        title: String,
        author: String? = null,
        importedAt: String? = null,
        updatedAt: String,
        format: String = "txt",
        contentStatus: String = "available",
        cover: String? = null,
    ): BookEntity = BookEntity(
        id = id,
        title = title,
        author = author,
        format = format,
        imported_at = importedAt,
        updated_at = updatedAt,
        cover_data_url = cover,
    )

    private fun progress(
        bookId: String,
        percent: Float = 0f,
        lastReadAt: String? = null,
        updatedAt: String = "2026-01-01T00:00:00Z",
    ): ReadingProgressEntity = ReadingProgressEntity(
        book_id = bookId,
        progress_percent = percent,
        last_read_at = lastReadAt,
        updated_at = updatedAt,
    )

    @Test
    fun `sort by title matches original unicode order with deterministic tie breaker`() {
        val books = listOf(
            book("b1", "三国演义", updatedAt = "2026-01-01T00:00:00Z"),
            book("b2", "西游记", updatedAt = "2026-01-01T00:00:00Z"),
            book("b3", "红楼梦", updatedAt = "2026-01-01T00:00:00Z"),
            book("b4", "水浒传", updatedAt = "2026-01-01T00:00:00Z"),
            book("b5", "A Tale of Two Cities", updatedAt = "2026-01-01T00:00:00Z"),
            book("b6", "123 数字书", updatedAt = "2026-01-01T00:00:00Z"),
            book("b7", "", updatedAt = "2026-01-01T00:00:00Z"),
            book("b8", "超长标题".repeat(50), updatedAt = "2026-01-01T00:00:00Z"),
        )
        val progress = emptyMap<String, ReadingProgressEntity>()
        val items = ShelfBookSorter.projectAll(books, progress)
        val sorted = ShelfBookSorter.sort(items, "title").map { it.bookId }

        val expected = books
            .sortedWith(compareBy<BookEntity> { it.title }.thenBy { it.id })
            .map { it.id }
        assertEquals(expected, sorted)
    }

    @Test
    fun `same title books are ordered by bookId`() {
        val books = listOf(
            book("z", "同名书", updatedAt = "2026-01-01T00:00:00Z"),
            book("a", "同名书", updatedAt = "2026-01-01T00:00:00Z"),
            book("m", "同名书", updatedAt = "2026-01-01T00:00:00Z"),
        )
        val items = ShelfBookSorter.projectAll(books, emptyMap())
        val sorted = ShelfBookSorter.sort(items, "title").map { it.bookId }
        assertEquals(listOf("a", "m", "z"), sorted)
    }

    @Test
    fun `sort by progress descending then by id`() {
        val books = listOf(
            book("b1", "书1", updatedAt = "2026-01-01T00:00:00Z"),
            book("b2", "书2", updatedAt = "2026-01-01T00:00:00Z"),
            book("b3", "书3", updatedAt = "2026-01-01T00:00:00Z"),
            book("b4", "书4", updatedAt = "2026-01-01T00:00:00Z"),
        )
        val progress = mapOf(
            "b1" to progress("b1", percent = 10f),
            "b2" to progress("b2", percent = 100f),
            "b3" to progress("b3", percent = 10f),
            "b4" to progress("b4", percent = 50f),
        )
        val items = ShelfBookSorter.projectAll(books, progress)
        val sorted = ShelfBookSorter.sort(items, "progress").map { it.bookId }
        assertEquals(listOf("b2", "b4", "b1", "b3"), sorted)
    }

    @Test
    fun `sort by imported descending uses imported_at fallback to updated_at`() {
        val books = listOf(
            book("b1", "书1", importedAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-05T00:00:00Z"),
            book("b2", "书2", importedAt = "2026-01-04T00:00:00Z", updatedAt = "2026-01-02T00:00:00Z"),
            book("b3", "书3", importedAt = null, updatedAt = "2026-01-03T00:00:00Z"),
        )
        val items = ShelfBookSorter.projectAll(books, emptyMap())
        val sorted = ShelfBookSorter.sort(items, "imported").map { it.bookId }
        assertEquals(listOf("b2", "b3", "b1"), sorted)
    }

    @Test
    fun `sort by recent uses last_read_at then updated_at`() {
        val books = listOf(
            book("b1", "书1", updatedAt = "2026-01-01T00:00:00Z"),
            book("b2", "书2", updatedAt = "2026-01-03T00:00:00Z"),
            book("b3", "书3", updatedAt = "2026-01-02T00:00:00Z"),
        )
        val progress = mapOf(
            "b1" to progress("b1", lastReadAt = "2026-01-05T00:00:00Z"),
            "b2" to progress("b2", lastReadAt = "2026-01-04T00:00:00Z"),
            "b3" to progress("b3", lastReadAt = null),
        )
        val items = ShelfBookSorter.projectAll(books, progress)
        val sorted = ShelfBookSorter.sort(items, "recent").map { it.bookId }
        assertEquals(listOf("b1", "b2", "b3"), sorted)
    }

    @Test
    fun `202 mixed books can be projected and sorted without error`() {
        val books = (1..202).map { index ->
            book(
                id = "book-${index.toString().padStart(3, '0')}",
                title = when (index % 5) {
                    0 -> "中文标题 $index"
                    1 -> "English Title $index"
                    2 -> "123 数字 $index"
                    3 -> ""
                    else -> "混合 Mixed ${index} 标题"
                },
                author = "作者 ${index % 20}",
                importedAt = "2026-01-${((index % 28) + 1).toString().padStart(2, '0')}T00:00:00Z",
                updatedAt = "2026-02-${((index % 28) + 1).toString().padStart(2, '0')}T00:00:00Z",
                format = if (index % 3 == 0) "epub" else "txt",
            )
        }
        val progress = books.associate {
            it.id to progress(it.id, percent = (it.id.hashCode() % 101).toFloat().coerceAtLeast(0f))
        }
        val items = ShelfBookSorter.projectAll(books, progress)
        assertEquals(202, ShelfBookSorter.sort(items, "title").size)
        assertEquals(202, ShelfBookSorter.sort(items, "progress").size)
        assertEquals(202, ShelfBookSorter.sort(items, "imported").size)
        assertEquals(202, ShelfBookSorter.sort(items, "recent").size)
    }

    @Test
    fun `1000 mixed books sort deterministically`() {
        val books = (1..1000).map { index ->
            book(
                id = "id-$index",
                title = "书 $index",
                updatedAt = "2026-01-01T00:00:00Z",
            )
        }
        val items = ShelfBookSorter.projectAll(books, emptyMap())
        val first = ShelfBookSorter.sort(items, "title").map { it.bookId }
        val second = ShelfBookSorter.sort(items, "title").map { it.bookId }
        assertEquals(first, second)
    }

    @Test
    fun `projected item holds expected keys`() {
        val book = book(
            id = "b1",
            title = "Title",
            author = "Author",
            importedAt = "2026-01-02T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )
        val progress = progress("b1", percent = 42f, lastReadAt = "2026-01-03T00:00:00Z")
        val item = ShelfBookItem.from(book, progress)

        assertEquals("b1", item.bookId)
        assertEquals("Title", item.titleKey)
        assertEquals("Author", item.authorKey)
        assertEquals("2026-01-02T00:00:00Z", item.importedAtKey)
        assertEquals(42f, item.progressKey, 0f)
        assertEquals("2026-01-03T00:00:00Z", item.lastReadAtKey)
        assertEquals("2026-01-01T00:00:00Z", item.updatedAtKey)
    }
}
