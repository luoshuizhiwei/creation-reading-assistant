package com.creationreadingassistant.ui.screen.shelf

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.viewmodel.ShelfBookItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 状态分组条计数与「导入后定位」下标换算。
 *
 * 这两件事都只在真机上表现为「数字对不上」或「滚到隔壁那本」，
 * 靠肉眼回归成本极高，因此把口径抽成纯函数在这里锁住。
 */
class ShelfStatusCountsTest {

    // ── 分组计数 ──────────────────────────────────────────────

    @Test
    fun `counts match status of each book`() {
        val items = listOf(
            item("a"), // 未读
            item("b"), // 在读
            item("c"), // 已完成（真实读到 100%，mergeReaderProgress 已写 FINISHED）
            item("d"), // 已完成（显式标记）
            item("e"), // 搁置
        )
        val progress = mapOf(
            "b" to progress("b", percent = 40f),
            // S05-① 契约：完成必须有显式状态；读到 100% 的书由数据层写入 finished，
            // 进度侧完成样例与显式完成共用 "finished"
            "c" to progress("c", percent = 100f, state = "finished"),
            "d" to progress("d", percent = 0f, state = "finished"),
            "e" to progress("e", percent = 10f, state = "shelved"),
        )

        val counts = shelfStatusCounts(items, progress)

        assertEquals(5, counts[ShelfStatusFilter.ALL])
        assertEquals(1, counts[ShelfStatusFilter.UNREAD])
        assertEquals(1, counts[ShelfStatusFilter.READING])
        assertEquals(2, counts[ShelfStatusFilter.COMPLETED])
        assertEquals(1, counts[ShelfStatusFilter.SHELVED])
    }

    /**
     * S05-① 核心回归：取消读完（`setReadingState(READING)`）后 `markFinished` 抬高的
     * 100% 残余仍在，但书必须计入「在读」分组，不得再报「已完成」。
     */
    @Test
    fun `cancelled finished counts as reading not completed`() {
        val items = listOf(item("x"))
        val progress = mapOf("x" to progress("x", percent = 100f)) // state 默认 "reading"

        val counts = shelfStatusCounts(items, progress)

        assertEquals(1, counts[ShelfStatusFilter.READING])
        assertEquals(0, counts[ShelfStatusFilter.COMPLETED] ?: 0)
    }

    /**
     * 核心回归：本机可读的书也必须有真实阅读状态，不能被「本机可读」吞掉。
     *
     * 这是用户报的「读完的书无法归入已完成」的根因——修复前，凡本机导入的书
     * （`local_uri` 为 `content://`）一律判为 READABLE，已完成计数恒为 0。
     */
    @Test
    fun `locally available books still count by their reading state`() {
        val items = listOf(item("a", localUri = "content://test/a"))
        // 读完的书由数据层写入 finished；这里锁定「就绪态不吞阅读状态」的旧回归
        val progress = mapOf("a" to progress("a", percent = 100f, state = "finished"))

        val counts = shelfStatusCounts(items, progress)

        assertEquals(1, counts[ShelfStatusFilter.ALL])
        assertEquals(1, counts[ShelfStatusFilter.COMPLETED])
        assertEquals(0, counts[ShelfStatusFilter.UNREAD] ?: 0)
    }

    @Test
    fun `empty shelf counts only all as zero`() {
        val counts = shelfStatusCounts(emptyList(), emptyMap())
        assertEquals(0, counts[ShelfStatusFilter.ALL])
        assertNull(counts[ShelfStatusFilter.COMPLETED])
    }

    @Test
    fun `status group options exclude readable`() {
        // 「本机可读」不是互斥阅读状态，不应出现在分组条上
        assertTrue(ShelfStatusFilter.READABLE !in ShelfStatusGroupOptions)
        assertEquals(
            listOf(
                ShelfStatusFilter.ALL,
                ShelfStatusFilter.READING,
                ShelfStatusFilter.UNREAD,
                ShelfStatusFilter.COMPLETED,
                ShelfStatusFilter.SHELVED,
            ),
            ShelfStatusGroupOptions,
        )
    }

    @Test
    fun `legacy readable saved view filters by local availability without hiding completed books`() {
        val readable = item("local", localUri = "content://test/local")
        val missing = item("missing", localUri = "content://test/missing", contentStatus = "missing")
        val progress = mapOf("local" to progress("local", percent = 100f, state = "finished"))

        val result = filterItems(
            items = listOf(readable, missing),
            query = "",
            statusFilter = ShelfStatusFilter.READABLE,
            progressById = progress,
            allowedBookIds = null,
        )

        assertEquals(listOf("local"), result.map { it.book.id })
        assertEquals(ShelfStatusFilter.COMPLETED, bookStatus(readable.book, 100f, "finished"))
    }

    // ── 导入后定位：网格下标换算 ──────────────────────────────

    @Test
    fun `grid index offsets by leading items in normal mode`() {
        val state = state(
            items = listOf(item("a"), item("b"), item("c")),
            showSkeleton = false,
        )
        // 普通模式前置 2 个整行 item（status-rail + toolbar），无占位
        assertEquals(2, shelfGridIndexOfBook(state, "a"))
        assertEquals(3, shelfGridIndexOfBook(state, "b"))
        assertEquals(4, shelfGridIndexOfBook(state, "c"))
    }

    @Test
    fun `grid index offsets by one in selection mode`() {
        val state = state(
            items = listOf(item("a"), item("b")),
            showSkeleton = false,
            selectionMode = true,
        )
        assertEquals(1, shelfGridIndexOfBook(state, "a"))
        assertEquals(2, shelfGridIndexOfBook(state, "b"))
    }

    @Test
    fun `grid index is null while skeleton is showing`() {
        val state = state(items = listOf(item("a")), showSkeleton = true)
        assertNull(shelfGridIndexOfBook(state, "a"))
    }

    @Test
    fun `grid index is null when book is filtered out`() {
        val state = state(items = listOf(item("a")), showSkeleton = false)
        assertNull(shelfGridIndexOfBook(state, "missing"))
    }

    @Test
    fun `grid index is null on empty filtered list`() {
        val state = state(items = emptyList(), showSkeleton = false)
        assertNull(shelfGridIndexOfBook(state, "a"))
    }

    @Test
    fun `leading item count adds placeholder for empty state`() {
        // 空态会渲染一个整行占位 item
        assertEquals(3, shelfLeadingItemCount(state(emptyList(), showSkeleton = false)))
        assertEquals(3, shelfLeadingItemCount(state(listOf(item("a")), showSkeleton = true)))
        assertEquals(2, shelfLeadingItemCount(state(listOf(item("a")), showSkeleton = false)))
    }

    // ── 夹具 ─────────────────────────────────────────────────

    private fun state(
        items: List<ShelfBookItem>,
        showSkeleton: Boolean,
        selectionMode: Boolean = false,
    ) = ShelfUiState(
        shelfBooks = items,
        showSkeleton = showSkeleton,
        selectionMode = selectionMode,
    )

    private fun item(
        id: String,
        localUri: String? = null,
        contentStatus: String = "available",
    ) = ShelfBookItem.from(
        BookEntity(
            id = id,
            title = "书$id",
            format = "txt",
            size = 100,
            content_status = contentStatus,
            local_uri = localUri,
            updated_at = "2026-08-02T00:00:00Z",
        ),
        progress = null,
    )

    private fun progress(id: String, percent: Float, state: String = "reading") =
        ReadingProgressEntity(
            book_id = id,
            progress_percent = percent,
            completion_state = state,
            updated_at = "2026-08-02T00:00:00Z",
        )
}
