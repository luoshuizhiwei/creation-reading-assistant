package com.creationreadingassistant.ui.screen

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.screen.shelf.ShelfStatusFilter
import com.creationreadingassistant.ui.screen.shelf.bookStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfStatusFilterTest {
    @Test fun bookStatusPrioritizesLocalAvailabilityThenProgressThresholds() {
        // 已下载（本地正文可用）：无论进度都判为「本机可读」
        assertEquals(ShelfStatusFilter.READABLE, bookStatus(downloaded(), 0f))
        assertEquals(ShelfStatusFilter.READABLE, bookStatus(downloaded(), 50f))
        assertEquals(ShelfStatusFilter.READABLE, bookStatus(downloaded(), 100f))
        // 未下载：按进度区分未读 / 在读 / 已完成（阈值 99.5）
        assertEquals(ShelfStatusFilter.UNREAD, bookStatus(remote(), 0f))
        assertEquals(ShelfStatusFilter.READING, bookStatus(remote(), 20f))
        assertEquals(ShelfStatusFilter.COMPLETED, bookStatus(remote(), 99.6f))
        assertEquals(ShelfStatusFilter.COMPLETED, bookStatus(remote(), 100f))
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
