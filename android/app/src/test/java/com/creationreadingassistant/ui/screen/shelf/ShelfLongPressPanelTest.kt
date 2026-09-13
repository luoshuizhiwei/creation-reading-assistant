package com.creationreadingassistant.ui.screen.shelf

import com.creationreadingassistant.data.local.entity.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长按书籍面板（[BookActionSheet]）与网格/列表条目（BookTile）的纯逻辑。
 *
 * 这些判定原本埋在 Composable 里无法断言，抽成纯函数后才能在 JVM 上锁住：
 * 一旦有人改动就绪态口径或去冗余规则，这里会立刻失败，而不是等到真机才发现。
 */
class ShelfLongPressPanelTest {

    // ── 去冗余判定：主行动按钮是否已经陈述了同一个就绪态 ──────────────

    @Test
    fun `ready state is already stated by the continue-reading action`() {
        assertTrue(readinessStatedByAction(BookReadiness("可离线阅读", ReadinessTone.READY), "available"))
    }

    @Test
    fun `missing content is already stated by the download action`() {
        assertTrue(readinessStatedByAction(BookReadiness("正文未在本机", ReadinessTone.CLOUD), "missing"))
    }

    @Test
    fun `cloud state with null status is already stated by the download action`() {
        assertTrue(readinessStatedByAction(BookReadiness("需下载正文", ReadinessTone.CLOUD), null))
    }

    /**
     * 关键例外：正在下载时，按钮说的是「下载正文」（一个动作），
     * 胶囊说的是「正文下载中」（一个进行中的状态）——两者陈述不同，
     * 去掉胶囊会让「正在下载」这一事实凭空消失。
     */
    @Test
    fun `downloading progress is NOT restated by the download action`() {
        assertFalse(readinessStatedByAction(BookReadiness("正文下载中", ReadinessTone.CLOUD), "downloading"))
    }

    @Test
    fun `error states have no primary action so the capsule must stay`() {
        assertFalse(readinessStatedByAction(BookReadiness("正文保存失败", ReadinessTone.ERROR), "failed"))
        assertFalse(readinessStatedByAction(BookReadiness("正文为空", ReadinessTone.ERROR), null))
    }

    /**
     * 每个就绪态都必须被规则**明确**判定（不留未定义分支），且语义正确：
     * - READY：主行动「继续阅读」已陈述就绪态 → 永远去冗余（胶囊隐藏）
     * - CLOUD：非下载中由「下载正文」陈述 → 去冗余；下载中由胶囊「正文下载中」陈述 → 保留
     * - ERROR：无主行动按钮 → 胶囊永远保留，不去冗余
     */
    @Test
    fun `every tone has a defined and correct readinessStatedByAction outcome`() {
        // READY —— 永远去冗余
        assertTrue(readinessStatedByAction(BookReadiness("x", ReadinessTone.READY), "available"))
        assertTrue(readinessStatedByAction(BookReadiness("x", ReadinessTone.READY), "downloading"))
        assertTrue(readinessStatedByAction(BookReadiness("x", ReadinessTone.READY), null))
        // CLOUD —— 只有「下载中」保留胶囊
        assertTrue(readinessStatedByAction(BookReadiness("x", ReadinessTone.CLOUD), "missing"))
        assertTrue(readinessStatedByAction(BookReadiness("x", ReadinessTone.CLOUD), null))
        assertFalse(readinessStatedByAction(BookReadiness("x", ReadinessTone.CLOUD), "downloading"))
        // ERROR —— 永远保留胶囊
        assertFalse(readinessStatedByAction(BookReadiness("x", ReadinessTone.ERROR), "failed"))
        assertFalse(readinessStatedByAction(BookReadiness("x", ReadinessTone.ERROR), "downloading"))
        assertFalse(readinessStatedByAction(BookReadiness("x", ReadinessTone.ERROR), null))
    }

    // ── 就绪态映射（网格/列表条目与长按面板共用）────────────────────

    @Test
    fun `failed content is an error tone and never offered as downloadable`() {
        val readiness = book(contentStatus = "failed", size = 1000).readiness()
        assertEquals(ReadinessTone.ERROR, readiness.tone)
        assertEquals("正文保存失败", readiness.label)
    }

    @Test
    fun `missing content is a cloud tone`() {
        val readiness = book(contentStatus = "missing", size = 1000).readiness()
        assertEquals(ReadinessTone.CLOUD, readiness.tone)
        assertEquals("正文未在本机", readiness.label)
    }

    @Test
    fun `downloading content stays in the cloud tone but keeps its own label`() {
        val readiness = book(contentStatus = "downloading", size = 1000).readiness()
        assertEquals(ReadinessTone.CLOUD, readiness.tone)
        assertEquals("正文下载中", readiness.label)
    }

    @Test
    fun `a book with a local source is ready to read offline`() {
        val readiness = book(contentStatus = "available", size = 1000, localUri = "content://test/local").readiness()
        assertEquals(ReadinessTone.READY, readiness.tone)
        assertEquals("可离线阅读", readiness.label)
    }

    @Test
    fun `an empty book is an error rather than a download prompt`() {
        val readiness = book(contentStatus = "available", size = 0).readiness()
        assertEquals(ReadinessTone.ERROR, readiness.tone)
        assertEquals("正文为空", readiness.label)
    }

    private fun book(
        contentStatus: String,
        size: Int,
        localUri: String? = null,
    ) = BookEntity(
        id = "book",
        title = "测试书籍",
        format = "txt",
        size = size,
        content_status = contentStatus,
        local_uri = localUri,
        updated_at = "2026-08-02T00:00:00Z",
    )
}
