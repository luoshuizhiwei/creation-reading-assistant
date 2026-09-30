package com.creationreadingassistant.ui.screen

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.screen.shelf.ShelfStatusFilter
import com.creationreadingassistant.ui.screen.shelf.bookStatus
import com.creationreadingassistant.ui.screen.shelf.isDisplayFinished
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阅读状态判定口径。
 *
 * **回归背景（勿回退）：** 旧实现把「本机可读」放在进度判断之前，导致本机导入的书
 * （`local_uri` 是 `content://`，`hasLocalBookSource()` 一律放行）永远判为 READABLE，
 * `COMPLETED` 成为死分支——读完整本书在书架上看不到「已完成」，也无法归入该分组。
 * 本文件的用例锁住修复后的口径。
 *
 * **S05-① 契约（2026-09-28 裁决）：显式状态列权威，百分比不再推断「已完成」。**
 * 所有写 `progress_percent >= 99.5` 的路径（`mergeReaderProgress` / `updateReadingProgress` /
 * `markFinished`）都在同一行写入 `finished`；导入器把外部估算钳制在 99.4%；同步不落远端百分比。
 * 因此 `percent >= 99.5 ∧ state = reading` 在本应用内只能由「取消读完 / 恢复在读」
 * （`setReadingState(READING)`）产生——那是用户的最新显式意图，必须归入「在读」，
 * 不能再被残余百分比改判回「已完成」。
 */
class ShelfStatusFilterTest {

    // ── 互斥阅读状态：与「能不能离线读」无关 ──────────────────────────

    @Test
    fun `local availability does not swallow reading state`() {
        // 关键回归：本地正文可用的书，仍须给出真实阅读状态（而不是被就绪态吞掉）
        assertEquals(ShelfStatusFilter.UNREAD, bookStatus(downloaded(), 0f))
        assertEquals(ShelfStatusFilter.READING, bookStatus(downloaded(), 50f))
        // 无显式状态行时，满进度只表示「读到过末尾」，不推断完成（S05-① 契约）
        assertEquals(ShelfStatusFilter.READING, bookStatus(downloaded(), 100f))
    }

    @Test
    fun `progress decides unread and reading while completion needs explicit state`() {
        assertEquals(ShelfStatusFilter.UNREAD, bookStatus(remote(), 0f))
        assertEquals(ShelfStatusFilter.READING, bookStatus(remote(), 20f))
        // 99.5% 边界已上移到数据层（mergeReaderProgress / updateReadingProgress 写 FINISHED）；
        // 展示层没有显式状态时不得再从百分比推断完成
        assertEquals(ShelfStatusFilter.READING, bookStatus(remote(), 99.4f))
        assertEquals(ShelfStatusFilter.READING, bookStatus(remote(), 99.5f))
        assertEquals(ShelfStatusFilter.READING, bookStatus(remote(), 99.6f))
        assertEquals(ShelfStatusFilter.READING, bookStatus(remote(), 100f))
        assertEquals(ShelfStatusFilter.COMPLETED, bookStatus(remote(), 100f, "finished"))
    }

    @Test
    fun `cancelling finished stays reading at full percent`() {
        // S05-① 复现：markFinished 把 30% 抬到 100，取消读完（setReadingState READING）
        // 后 percent 仍是 100——书架必须归入「在读」，不得留在「已完成」、不得再显示「完」
        assertEquals(ShelfStatusFilter.READING, bookStatus(remote(), 100f, "reading"))
        assertEquals(ShelfStatusFilter.READING, bookStatus(downloaded(), 100f, "reading"))
        assertFalse(isDisplayFinished(100f, "reading"))
    }

    @Test
    fun `finished badge follows explicit state not stale percent`() {
        assertTrue(isDisplayFinished(100f, "finished"))
        assertTrue(isDisplayFinished(0f, "finished")) // 手动标记读完但进度未到
        assertTrue(isDisplayFinished(100f, "completed")) // 历史值兼容
        assertFalse(isDisplayFinished(30f, "reading"))
        assertFalse(isDisplayFinished(100f, "shelved")) // 搁置书不盖「完」
    }

    // ── 显式状态：用户手动标记必须被尊重 ──────────────────────────────

    @Test
    fun `explicit finished wins over low progress`() {
        // 用户「标记为已读完」时进度可能仍是 0，不能因此显示为「未读」
        assertEquals(ShelfStatusFilter.COMPLETED, bookStatus(remote(), 0f, "finished"))
        assertEquals(ShelfStatusFilter.COMPLETED, bookStatus(downloaded(), 0f, "finished"))
    }

    @Test
    fun `legacy completed value is treated as finished`() {
        // ReadingCompletionState.fromStorage 兼容历史 "completed"
        assertEquals(ShelfStatusFilter.COMPLETED, bookStatus(remote(), 0f, "completed"))
    }

    @Test
    fun `shelved outranks finished`() {
        // 用户明确搁置的书，即使进度 100% 且状态为 finished，也算搁置（when 首分支）
        assertEquals(ShelfStatusFilter.SHELVED, bookStatus(remote(), 100f, "shelved"))
        assertEquals(ShelfStatusFilter.SHELVED, bookStatus(remote(), 0f, "shelved"))
        assertEquals(ShelfStatusFilter.SHELVED, bookStatus(downloaded(), 100f, "shelved"))
    }

    @Test
    fun `unknown or null completion state falls back to progress`() {
        assertEquals(ShelfStatusFilter.UNREAD, bookStatus(remote(), 0f, null))
        assertEquals(ShelfStatusFilter.UNREAD, bookStatus(remote(), 0f, ""))
        assertEquals(ShelfStatusFilter.READING, bookStatus(remote(), 30f, "reading"))
        assertEquals(ShelfStatusFilter.READING, bookStatus(remote(), 30f, "nonsense"))
    }

    @Test
    fun `out of range percent is clamped before thresholding`() {
        assertEquals(ShelfStatusFilter.READING, bookStatus(remote(), 140f))
        assertEquals(ShelfStatusFilter.UNREAD, bookStatus(remote(), -20f))
    }

    private fun downloaded() = BookEntity(
        id = "local-book",
        title = "本地书",
        format = "txt",
        size = 1000,
        content_status = "available",
        local_uri = "content://test/local",
        updated_at = "2026-08-02T00:00:00Z",
    )

    private fun remote() = BookEntity(
        id = "remote-book",
        title = "云端书",
        format = "txt",
        size = 0,
        content_status = "missing",
        updated_at = "2026-08-02T00:00:00Z",
    )
}
