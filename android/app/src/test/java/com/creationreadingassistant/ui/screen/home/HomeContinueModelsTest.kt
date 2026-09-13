package com.creationreadingassistant.ui.screen.home

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「继续阅读」候选集构造的**回归锁**。
 *
 * 背景：`HomeContinueSheet.kt` 拆分时把 `buildContinueItems` / `lastReadAtFor`
 * 整体搬到了 `HomeContinueModels.kt`（原文件 −516 行）。搬文件本身不是功能完成，
 * 这组用例把搬运后仍然必须成立的过滤契约逐条钉死：
 * 未读、已搁置、已接近读完、本机无正文的书都不应出现；
 * 「从继续阅读移除」的语义是**按最后阅读时间比较**，而不是无条件隐藏。
 */
class HomeContinueModelsTest {

    private fun book(
        id: String,
        contentStatus: String = "available",
        size: Int = 1024,
        localPath: String? = "/tmp/$id",
        deletedAt: String? = null,
    ) = BookEntity(
        id = id,
        title = "书-$id",
        format = "txt",
        size = size,
        local_content_path = localPath,
        content_status = contentStatus,
        deleted_at = deletedAt,
        updated_at = "2026-01-01T00:00:00Z",
    )

    private fun progress(
        bookId: String,
        percent: Float = 10f,
        lastReadAt: String? = null,
        completionState: String = "reading",
    ) = ReadingProgressEntity(
        book_id = bookId,
        progress_percent = percent,
        last_read_at = lastReadAt,
        completion_state = completionState,
        updated_at = "2026-01-01T00:00:00Z",
    )

    private fun session(
        bookId: String,
        startedAt: String?,
        endedAt: String?,
    ) = ReadingSessionEntity(
        id = "s-$bookId-${startedAt ?: endedAt}",
        book_id = bookId,
        started_at = startedAt,
        ended_at = endedAt,
        updated_at = "2026-01-01T00:00:00Z",
    )

    private fun idsOf(
        books: List<BookEntity>,
        progressById: Map<String, ReadingProgressEntity> = emptyMap(),
        sessions: Map<String, List<ReadingSessionEntity>> = emptyMap(),
        removedIds: Map<String, String> = emptyMap(),
    ) = buildContinueItems(books, progressById, sessions, removedIds).map { it.book.id }

    @Test
    fun neverReadBookIsExcluded() {
        val b = book("b1")
        assertTrue(idsOf(listOf(b)).isEmpty())
    }

    @Test
    fun shelvedBookIsExcluded() {
        val b = book("b1")
        val p = progress("b1", completionState = "shelved")
        assertTrue(idsOf(listOf(b), mapOf("b1" to p)).isEmpty())
    }

    @Test
    fun nearlyFinishedBookIsExcluded() {
        val b = book("b1")
        val p99 = progress("b1", percent = 99.6f)
        val p98 = progress("b1", percent = 98f)
        assertTrue(idsOf(listOf(b), mapOf("b1" to p99)).isEmpty())
        assertEquals(listOf("b1"), idsOf(listOf(b), mapOf("b1" to p98)))
    }

    @Test
    fun bookWithoutLocalContentIsExcluded() {
        val missing = book("missing", contentStatus = "missing")
        val emptySize = book("empty", size = 0)
        val noSource = book("nosrc", localPath = null)
        val ok = book("ok")
        val p = { id: String -> progress(id) }
        val result = idsOf(
            listOf(missing, emptySize, noSource, ok),
            mapOf("missing" to p("missing"), "empty" to p("empty"), "nosrc" to p("nosrc"), "ok" to p("ok")),
        )
        assertEquals(listOf("ok"), result)
    }

    @Test
    fun removedBookReturnsOnlyWhenReadAfterRemoval() {
        val b = book("b1")
        val removedAt = "2026-05-01T00:00:00Z"
        val removedIds = mapOf("b1" to removedAt)

        val readAfter = progress("b1", lastReadAt = "2026-06-01T00:00:00Z")
        assertEquals(
            listOf("b1"),
            idsOf(listOf(b), mapOf("b1" to readAfter), removedIds = removedIds),
        )

        val readBefore = progress("b1", lastReadAt = "2026-04-01T00:00:00Z")
        assertTrue(
            idsOf(listOf(b), mapOf("b1" to readBefore), removedIds = removedIds).isEmpty(),
        )

        // 移除时间恰好等于最后阅读时间：不算「之后又读过」，保持隐藏
        val readSame = progress("b1", lastReadAt = removedAt)
        assertTrue(
            idsOf(listOf(b), mapOf("b1" to readSame), removedIds = removedIds).isEmpty(),
        )
    }

    @Test
    fun lastReadAtFallsBackToLatestSessionEnd() {
        val b = book("b1")
        val p = progress("b1", lastReadAt = null)
        val sessions = mapOf(
            "b1" to listOf(
                session("b1", "2026-03-01T10:00:00Z", "2026-03-01T10:30:00Z"),
                session("b1", "2026-04-01T10:00:00Z", "2026-04-01T10:30:00Z"),
            ),
        )
        val item = buildContinueItems(listOf(b), mapOf("b1" to p), sessions, emptyMap()).single()
        assertEquals("2026-04-01T10:30:00Z", item.lastReadAt)
    }

    @Test
    fun originalIndexFollowsInputOrder() {
        val books = listOf(book("a"), book("b"), book("c"))
        val progressById = books.associate { it.id to progress(it.id) }
        val items = buildContinueItems(books, progressById, emptyMap(), emptyMap())
        assertEquals(listOf("a", "b", "c"), items.map { it.book.id })
        assertEquals(listOf(0, 1, 2), items.map { it.originalIndex })
    }
}
