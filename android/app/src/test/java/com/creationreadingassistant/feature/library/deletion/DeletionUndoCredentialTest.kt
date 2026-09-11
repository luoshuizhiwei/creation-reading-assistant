package com.creationreadingassistant.feature.library.deletion

import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话内撤销凭证的有效期、容量与合并行为。
 *
 * 需求明确「第一版可以是带有效期的会话内凭证，且不得全局无界积累」，这几条用例就是把
 * 这句话变成可验证的行为：窗口一到撤销入口必须消失、凭证数量有上限、批量循环要并成
 * 一张与入口提示数量一致的凭证、正文载荷超预算时如实降级而不是假装能恢复。
 */
class DeletionUndoCredentialTest {

    private class TestClock(var now: Long = 0L) : DeletionClock {
        override fun nowMillis(): Long = now
    }

    private val policy = DeletionUndoPolicy(
        undoWindowMillis = 12_000L,
        coalesceWindowMillis = 1_500L,
        maxCredentials = 4,
        maxRetainedContentChars = 4_000_000L,
    )

    private fun store(policy: DeletionUndoPolicy = this.policy): Pair<DeletionUndoStore, TestClock> {
        val clock = TestClock()
        return DeletionUndoStore(policy, clock) to clock
    }

    private fun snapshot(
        bookId: String,
        previewChars: Int = 0,
        deletedAt: String = "2026-09-09T10:00:00Z",
    ): BookDeletionSnapshot {
        val preview = if (previewChars > 0) "x".repeat(previewChars) else null
        return BookDeletionSnapshot(
            bookId = bookId,
            deletedAt = deletedAt,
            book = BookEntity(id = bookId, title = "测试书籍 $bookId", format = "txt", updated_at = deletedAt),
            progress = null,
            sessions = emptyList(),
            notes = emptyList(),
            highlights = emptyList(),
            file = null,
            content = preview?.let { BookContentEntity(book_id = bookId, reader_preview = it) },
            contentPayloadRetained = preview != null,
            tagIds = emptyList(),
            categoryIds = emptyList(),
            shelfLinks = emptyList(),
            chapterReads = emptyList(),
        )
    }

    private fun emptySnapshot(bookId: String) = BookDeletionSnapshot(
        bookId = bookId,
        deletedAt = "2026-09-09T10:00:00Z",
        book = null,
        progress = null,
        sessions = emptyList(),
        notes = emptyList(),
        highlights = emptyList(),
        file = null,
        content = null,
        contentPayloadRetained = false,
        tagIds = emptyList(),
        categoryIds = emptyList(),
        shelfLinks = emptyList(),
        chapterReads = emptyList(),
    )

    @Test
    fun `policy rejects windows and caps that cannot work`() {
        assertThrows(IllegalArgumentException::class.java) { DeletionUndoPolicy(undoWindowMillis = 0) }
        assertThrows(IllegalArgumentException::class.java) { DeletionUndoPolicy(undoWindowMillis = -1) }
        assertThrows(IllegalArgumentException::class.java) { DeletionUndoPolicy(coalesceWindowMillis = -1) }
        assertThrows(IllegalArgumentException::class.java) { DeletionUndoPolicy(maxCredentials = 0) }
        assertThrows(IllegalArgumentException::class.java) { DeletionUndoPolicy(maxRetainedContentChars = -1) }
    }

    @Test
    fun `offer disappears exactly when the window lapses`() {
        val (store, clock) = store()

        store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1"), snapshot("b2")))

        val offer = store.offers.value.single()
        assertEquals(DeletionScope.DELETE_BOOK, offer.scope)
        assertEquals(2, offer.bookCount)
        assertEquals(2, offer.restorableBookCount)
        assertEquals(12_000L, offer.expiresAtMillis)
        assertEquals(12_000L, store.remainingMillis(offer))
        assertNotNull(store.findLive(offer.id))

        // 窗口内最后一刻仍然可撤销。
        clock.now = 11_999L
        assertEquals(1L, store.remainingMillis(offer))
        assertNotNull(store.findLive(offer.id))
        assertEquals(1, store.offers.value.size)

        // 到点即失效：提示条必须消失，撤销必须被拒绝，不能留一个点了没反应的按钮。
        clock.now = 12_000L
        assertEquals(0L, store.remainingMillis(offer))
        assertNull(store.findLive(offer.id))
        assertNull(store.consume(offer.id))
        assertTrue(store.offers.value.isEmpty())
        assertEquals(0, store.liveCredentialCount())
    }

    @Test
    fun `prune drops expired credentials so the bar cannot linger`() {
        val (store, clock) = store()
        val credential = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1")))

        clock.now = 30_000L
        store.prune()

        assertTrue(store.offers.value.isEmpty())
        assertNull(store.latestOffer())
        assertNull(store.consume(credential.id))
    }

    @Test
    fun `credentials never accumulate beyond the cap`() {
        val (store, clock) = store()

        // 六次互不合并的删除（间隔超过合并窗口），全都还在有效期内。
        val ids = (0 until 6).map { index ->
            clock.now = index * 2_000L
            store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b$index"))).id
        }

        assertEquals(4, store.liveCredentialCount())
        assertEquals(4, store.offers.value.size)
        // 淘汰的是最旧的：最早两张已经撤不了，最近四张仍然可撤销。
        assertNull(store.findLive(ids[0]))
        assertNull(store.findLive(ids[1]))
        ids.drop(2).forEach { assertNotNull(store.findLive(it)) }
        // offers 最新在前。
        assertEquals(ids[5], store.offers.value.first().id)
        assertEquals(ids[2], store.offers.value.last().id)
    }

    @Test
    fun `a burst of single deletes coalesces into one credential covering the whole batch`() {
        val (store, clock) = store()

        // 既有入口的批量删除是 ids.forEach { deleteBook(it) } 形式的循环调用。
        clock.now = 0L
        val first = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1")))
        clock.now = 400L
        val second = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b2")))
        clock.now = 900L
        val third = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b3")))

        // 合并成同一张凭证：撤销范围与入口提示的「3 本」一致，而不是只恢复最后一本。
        assertEquals(first.id, second.id)
        assertEquals(first.id, third.id)
        assertEquals(1, store.liveCredentialCount())
        val offer = store.offers.value.single()
        assertEquals(3, offer.bookCount)
        assertEquals(listOf("b1", "b2", "b3"), store.findLive(first.id)?.bookIds)
        // 有效期从最近一次并入算起，循环进行中不会中途断开。
        assertEquals(900L + 12_000L, offer.expiresAtMillis)
    }

    @Test
    fun `coalescing stops once the burst window lapses`() {
        val (store, clock) = store()

        clock.now = 0L
        val first = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1")))
        clock.now = 1_500L
        val edge = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b2")))
        clock.now = 3_100L
        val later = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b3")))

        // 1500ms 恰好等于窗口，仍然合并；再往后就断开了。
        assertEquals(first.id, edge.id)
        assertEquals(2, edge.snapshots.size)
        assertFalse(later.id == edge.id)
        assertEquals(2, store.liveCredentialCount())
        assertEquals(1, store.offers.value.first { it.id == later.id }.bookCount)
    }

    @Test
    fun `different scopes never merge`() {
        val (store, clock) = store()

        clock.now = 0L
        val delete = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1")))
        clock.now = 100L
        val removeContent = store.publish(DeletionScope.REMOVE_CONTENT, listOf(snapshot("b2")))

        assertFalse(delete.id == removeContent.id)
        assertEquals(2, store.liveCredentialCount())
        assertEquals(setOf(DeletionScope.DELETE_BOOK, DeletionScope.REMOVE_CONTENT),
            store.offers.value.map { it.scope }.toSet())
    }

    @Test
    fun `merging keeps the informative snapshot when a book is deleted twice`() {
        val (store, clock) = store()

        clock.now = 0L
        store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1", previewChars = 8)))
        clock.now = 200L
        // 第二次捕获必然是空的：资料已经被上一次删除抹掉了。
        val merged = store.publish(DeletionScope.DELETE_BOOK, listOf(emptySnapshot("b1")))

        assertEquals(1, merged.snapshots.size)
        assertEquals(1, merged.bookCount)
        // 用空快照覆盖会让撤销丢掉真正的恢复依据。
        assertTrue(merged.snapshots.single().contentPayloadRetained)
        assertFalse(merged.snapshots.single().isEmpty)
        assertEquals(1, merged.restorableBookCount)
    }

    @Test
    fun `content budget trims payloads instead of holding the whole batch in memory`() {
        val small = policy.copy(maxRetainedContentChars = 10L)

        val kept = applyContentBudget(
            listOf(snapshot("b1", previewChars = 8), snapshot("b2", previewChars = 8)),
            small.maxRetainedContentChars,
        )

        // 预算按顺序耗尽：第一本保住载荷，第二本如实标记为未保留。
        assertTrue(kept[0].contentPayloadRetained)
        assertEquals(8, kept[0].content?.reader_preview?.length)
        assertFalse(kept[1].contentPayloadRetained)
        assertNull(kept[1].content?.reader_preview)
        // 资料与阅读数据不计入预算，裁剪只动正文载荷。
        assertNotNull(kept[1].book)
        assertEquals(2, kept.size)
    }

    @Test
    fun `content budget is re-applied after a merge`() {
        val small = policy.copy(maxRetainedContentChars = 12L)
        val (store, clock) = store(small)

        clock.now = 0L
        store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1", previewChars = 8)))
        clock.now = 100L
        // 单批合规、合并后超预算：必须在合并结果上重新收敛。
        val merged = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b2", previewChars = 8)))

        assertEquals(2, merged.snapshots.size)
        assertTrue(merged.snapshots[0].contentPayloadRetained)
        assertFalse(merged.snapshots[1].contentPayloadRetained)
        assertTrue(merged.snapshots.sumOf { it.contentPayloadChars } <= small.maxRetainedContentChars)
    }

    @Test
    fun `a zero budget keeps every book restorable except its content cache`() {
        val (store, clock) = store(policy.copy(maxRetainedContentChars = 0L))

        clock.now = 0L
        val credential = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1", previewChars = 100)))

        assertEquals(1, credential.bookCount)
        assertFalse(credential.snapshots.single().contentPayloadRetained)
        assertTrue(credential.snapshots.single().content?.reader_preview == null)
        // 正文缓存是可重建的派生数据，丢掉它不影响资料本身可撤销。
        assertEquals(1, credential.restorableBookCount)
    }

    @Test
    fun `findLive peeks without consuming so a failed restore can be retried`() {
        val (store, clock) = store()
        val credential = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1")))

        assertNotNull(store.findLive(credential.id))
        assertNotNull(store.findLive(credential.id))
        assertEquals(1, store.liveCredentialCount())

        assertNotNull(store.consume(credential.id))
        // 取走即移除：重复撤销天然幂等，不会把同一批资料写回两遍。
        assertNull(store.consume(credential.id))
        assertNull(store.findLive(credential.id))
        assertEquals(0, store.liveCredentialCount())
        assertTrue(store.offers.value.isEmpty())
    }

    @Test
    fun `consume rejects an expired credential even though it is still registered`() {
        val (store, clock) = store()
        val credential = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1")))

        clock.now = 12_001L

        assertNull(store.consume(credential.id))
        assertTrue(store.offers.value.isEmpty())
    }

    @Test
    fun `dismiss removes only the credential the user closed`() {
        val (store, clock) = store()

        clock.now = 0L
        val first = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1")))
        clock.now = 5_000L
        val second = store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b2")))

        store.dismiss(first.id)

        assertEquals(1, store.liveCredentialCount())
        assertNull(store.findLive(first.id))
        assertNotNull(store.findLive(second.id))
        assertEquals(listOf(second.id), store.offers.value.map { it.id })
    }

    @Test
    fun `clear drops every credential`() {
        val (store, clock) = store()

        clock.now = 0L
        store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b1")))
        clock.now = 5_000L
        store.publish(DeletionScope.DELETE_BOOK, listOf(snapshot("b2")))

        store.clear()

        assertTrue(store.offers.value.isEmpty())
        assertEquals(0, store.liveCredentialCount())
    }

    @Test
    fun `an empty snapshot batch still yields a live credential with nothing restorable`() {
        val (store, clock) = store()

        val credential = store.publish(DeletionScope.DELETE_BOOK, listOf(emptySnapshot("b1")))

        assertEquals(1, credential.bookCount)
        assertEquals(0, credential.restorableBookCount)
        assertTrue(credential.isLiveAt(clock.now))
        assertEquals(0, store.offers.value.single().restorableBookCount)
    }
}
