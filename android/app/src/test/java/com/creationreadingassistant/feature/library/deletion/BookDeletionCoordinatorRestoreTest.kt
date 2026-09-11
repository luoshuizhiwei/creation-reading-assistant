package com.creationreadingassistant.feature.library.deletion

import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.repository.TaxonomyRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 协调层的端到端行为：一次入口操作 → 一张凭证 → 一次撤销。
 *
 * 这里跑的是真实的内存书库 + 真实的 [com.creationreadingassistant.data.repository.BookRepository]，
 * 只有分类仓储（提供「撤销时哪些关系目标还活着」）是替身。重点验证四件在仓储层看不到的事：
 * 批量收敛成一张凭证、窗口过期后拒绝撤销、恢复失败时凭证仍可重试、关系目标在撤销前实时解析。
 */
class BookDeletionCoordinatorRestoreTest {

    private class TestClock(var now: Long = 0L) : DeletionClock {
        override fun nowMillis(): Long = now
    }

    private lateinit var fixture: DeletionLibraryFixture
    private lateinit var clock: TestClock
    private lateinit var taxonomy: TaxonomyRepository
    private lateinit var coordinator: BookDeletionCoordinator
    private lateinit var store: DeletionUndoStore

    private val policy = DeletionUndoPolicy(
        undoWindowMillis = 12_000L,
        coalesceWindowMillis = 1_500L,
        maxCredentials = 4,
        maxRetainedContentChars = 4_000_000L,
    )

    @Before
    fun setUp() {
        fixture = DeletionLibraryFixture()
        clock = TestClock()
        store = DeletionUndoStore(policy, clock)
        taxonomy = mockk {
            every { observeTags() } returns flowOf(listOf(tag("t1"), tag("t2")))
            every { observeCategories() } returns flowOf(listOf(category("c1")))
            every { observeShelves() } returns flowOf(listOf(shelf("shelf1")))
        }
        coordinator = BookDeletionCoordinator(
            repository = fixture.repository(),
            taxonomyRepository = taxonomy,
            undoStore = store,
            policy = policy,
        )
    }

    private fun tag(id: String) = TagEntity(id = id, name = "测试标签 $id", updated_at = STAMP)
    private fun category(id: String) = CategoryEntity(id = id, name = "测试分类 $id", updated_at = STAMP)
    private fun shelf(id: String) = ShelfEntity(id = id, name = "测试书单 $id", updated_at = STAMP)

    private fun seedFullBook(bookId: String) {
        fixture.addBook(bookId)
        fixture.addProgress(bookId, percent = 55f)
        fixture.addSession("s-$bookId", bookId)
        fixture.addNote("n-$bookId", bookId)
        fixture.addHighlight("h-$bookId", bookId)
        fixture.addContent(bookId, preview = "测试正文缓存")
        fixture.addFile(bookId)
        fixture.linkTag(bookId, "t1")
        fixture.linkCategory(bookId, "c1")
        fixture.linkShelf(bookId, "shelf1", position = 2)
        fixture.markChapterRead(bookId, 1)
    }

    @Test
    fun `one entry-point delete yields one credential and one undo restores it all`() = runTest {
        seedFullBook("b1")

        val credential = coordinator.deleteBooks(listOf("b1"))

        assertNotNull(credential)
        assertEquals(DeletionScope.DELETE_BOOK, credential!!.scope)
        assertFalse(fixture.isBookActive("b1"))
        val offer = coordinator.offers.value.single()
        assertEquals(1, offer.bookCount)
        assertEquals(1, offer.restorableBookCount)
        assertEquals(12_000L, coordinator.remainingMillis(offer))

        val outcome = coordinator.undo(credential.id)

        assertTrue(outcome.restored)
        assertNull(outcome.failure)
        assertEquals(1, outcome.restoredBookCount)
        assertEquals(0, outcome.skippedNewerChangeCount)
        assertEquals(0, outcome.skippedRelationTargetCount)
        assertFalse(outcome.contentPayloadDropped)
        assertTrue(fixture.isBookActive("b1"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b1"))
        assertEquals(setOf("c1"), fixture.categoryIdsOf("b1"))
        assertEquals(setOf(1), fixture.chapterIndexesOf("b1"))
        assertEquals(setOf("n-b1"), fixture.activeNoteIds("b1"))
        assertEquals(setOf("h-b1"), fixture.activeHighlightIds("b1"))
        assertEquals("测试正文缓存", fixture.contents["b1"]?.reader_preview)
        // 凭证被取走：撤销入口消失。
        assertTrue(coordinator.offers.value.isEmpty())
    }

    @Test
    fun `repeat undo is refused and writes nothing`() = runTest {
        seedFullBook("b1")
        val credential = coordinator.deleteBooks(listOf("b1"))!!
        assertTrue(coordinator.undo(credential.id).restored)
        val tagsAfterFirstUndo = fixture.tagIdsOf("b1")
        val chaptersAfterFirstUndo = fixture.chapterIndexesOf("b1")

        val second = coordinator.undo(credential.id)

        assertFalse(second.restored)
        assertEquals(DeletionUndoFailure.UNAVAILABLE, second.failure)
        assertTrue(second.reports.isEmpty())
        // 幂等：库里没有第二次写入的痕迹。
        assertEquals(tagsAfterFirstUndo, fixture.tagIdsOf("b1"))
        assertEquals(chaptersAfterFirstUndo, fixture.chapterIndexesOf("b1"))
        assertEquals(1, fixture.bookTags.values.count { it.book_id == "b1" })
        assertEquals(1, fixture.chapterReads.values.count { it.book_id == "b1" })
    }

    @Test
    fun `undo after the window expires is refused and the book stays deleted`() = runTest {
        seedFullBook("b1")
        val credential = coordinator.deleteBooks(listOf("b1"))!!

        clock.now = 12_000L
        coordinator.prune()
        assertTrue(coordinator.offers.value.isEmpty())

        val outcome = coordinator.undo(credential.id)

        assertFalse(outcome.restored)
        assertEquals(DeletionUndoFailure.UNAVAILABLE, outcome.failure)
        assertFalse(fixture.isBookActive("b1"))
        assertTrue(fixture.tagIdsOf("b1").isEmpty())
    }

    @Test
    fun `a failed restore rolls back, keeps the credential live and succeeds on retry`() = runTest {
        seedFullBook("b1")
        val credential = coordinator.deleteBooks(listOf("b1"))!!
        // 恢复事务在最后一步（写回已读章节）失败。
        fixture.failOnChapterReadRestore = true

        val failed = coordinator.undo(credential.id)

        assertFalse(failed.restored)
        assertEquals(DeletionUndoFailure.RESTORE_FAILED, failed.failure)
        assertNotNull(failed.error)
        // 事务回滚：数据仍是删除后的状态，没有半截恢复。
        assertFalse(fixture.isBookActive("b1"))
        assertTrue(fixture.tagIdsOf("b1").isEmpty())
        assertTrue(fixture.activeNoteIds("b1").isEmpty())
        assertTrue(fixture.chapterIndexesOf("b1").isEmpty())
        assertNull(fixture.contents["b1"]?.reader_preview)
        // 凭证没被烧掉：用户还能在剩余有效期内重试，提示条也还在。
        assertNotNull(store.findLive(credential.id))
        assertEquals(listOf(credential.id), coordinator.offers.value.map { it.id })

        fixture.failOnChapterReadRestore = false
        val retried = coordinator.undo(credential.id)

        assertTrue(retried.restored)
        assertNull(retried.failure)
        assertTrue(fixture.isBookActive("b1"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b1"))
        assertEquals(setOf(1), fixture.chapterIndexesOf("b1"))
        assertEquals(setOf("n-b1"), fixture.activeNoteIds("b1"))
        assertEquals("测试正文缓存", fixture.contents["b1"]?.reader_preview)
        assertTrue(coordinator.offers.value.isEmpty())
    }

    @Test
    fun `batch delete exposes one offer whose count matches the prompt and undoes the whole batch`() = runTest {
        seedFullBook("b1")
        seedFullBook("b2")
        seedFullBook("b3")

        val credential = coordinator.deleteBooks(listOf("b1", "b2", "b3"))!!

        assertEquals(3, credential.bookCount)
        assertEquals(3, coordinator.offers.value.single().bookCount)
        assertFalse(fixture.isBookActive("b1"))
        assertFalse(fixture.isBookActive("b2"))
        assertFalse(fixture.isBookActive("b3"))

        val outcome = coordinator.undo(credential.id)

        assertTrue(outcome.restored)
        assertEquals(3, outcome.restoredBookCount)
        assertEquals(3, outcome.reports.size)
        listOf("b1", "b2", "b3").forEach { id ->
            assertTrue(fixture.isBookActive(id))
            assertEquals(setOf("t1"), fixture.tagIdsOf(id))
            assertEquals(setOf("n-$id"), fixture.activeNoteIds(id))
            assertEquals(setOf(1), fixture.chapterIndexesOf(id))
        }
    }

    @Test
    fun `duplicate and blank ids collapse so the credential matches the real batch`() = runTest {
        seedFullBook("b1")

        val credential = coordinator.deleteBooks(listOf("b1", " b1 ", "", "   "))

        assertNotNull(credential)
        assertEquals(listOf("b1"), credential!!.bookIds)
        assertEquals(1, credential.bookCount)
        assertEquals(1, coordinator.offers.value.single().bookCount)
    }

    @Test
    fun `an all-blank batch deletes nothing and registers no credential`() = runTest {
        seedFullBook("b1")

        assertNull(coordinator.deleteBooks(emptyList()))
        assertNull(coordinator.deleteBooks(listOf("", "  ")))
        assertTrue(coordinator.offers.value.isEmpty())
        assertTrue(fixture.isBookActive("b1"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b1"))
    }

    @Test
    fun `deleting a book that does not exist yields a credential with nothing restorable`() = runTest {
        val credential = coordinator.deleteBooks(listOf("ghost"))!!

        assertEquals(1, credential.bookCount)
        assertEquals(0, credential.restorableBookCount)
        assertEquals(0, coordinator.offers.value.single().restorableBookCount)

        val outcome = coordinator.undo(credential.id)

        assertTrue(outcome.restored)
        assertEquals(0, outcome.restoredBookCount)
        assertNull(fixture.books["ghost"])
        assertTrue(coordinator.offers.value.isEmpty())
    }

    @Test
    fun `relation targets are resolved live at undo time, not at delete time`() = runTest {
        seedFullBook("b1")
        fixture.linkTag("b1", "t2")
        val credential = coordinator.deleteBooks(listOf("b1"))!!
        assertEquals(setOf("t1", "t2"), credential.snapshots.single().tagIds.toSet())

        // 撤销之前用户删掉了标签 t2 和书单 shelf1。
        every { taxonomy.observeTags() } returns flowOf(listOf(tag("t1")))
        every { taxonomy.observeShelves() } returns flowOf(emptyList())

        val outcome = coordinator.undo(credential.id)

        assertTrue(outcome.restored)
        assertEquals(setOf("t1"), fixture.tagIdsOf("b1"))
        assertTrue(fixture.shelfLinksOf("b1").isEmpty())
        assertEquals(setOf("c1"), fixture.categoryIdsOf("b1"))
        // 两个消失的目标被如实报出来，UI 才能告诉用户「这些关联没能恢复」。
        assertEquals(2, outcome.skippedRelationTargetCount)
        assertEquals(setOf("t2", "shelf1"), outcome.reports.single().skippedRelationTargetIds.toSet())
    }

    @Test
    fun `dismiss gives up the undo without touching the data`() = runTest {
        seedFullBook("b1")
        val credential = coordinator.deleteBooks(listOf("b1"))!!

        coordinator.dismiss(credential.id)

        assertTrue(coordinator.offers.value.isEmpty())
        assertFalse(fixture.isBookActive("b1"))
        assertTrue(fixture.tagIdsOf("b1").isEmpty())
        val outcome = coordinator.undo(credential.id)
        assertFalse(outcome.restored)
        assertEquals(DeletionUndoFailure.UNAVAILABLE, outcome.failure)
    }

    @Test
    fun `undo window seconds drives the confirm copy and never understates the window`() {
        assertEquals(12, coordinator.undoWindowSeconds)

        val rounded = BookDeletionCoordinator(
            repository = fixture.repository(),
            taxonomyRepository = taxonomy,
            undoStore = DeletionUndoStore(policy.copy(undoWindowMillis = 12_500L), clock),
            policy = policy.copy(undoWindowMillis = 12_500L),
        )
        // 12.5 秒向上取整成 13：宁可多说半秒，也不能承诺一个已经过去的时刻。
        assertEquals(13, rounded.undoWindowSeconds)
    }

    @Test
    fun `a burst of single-book deletes still collapses into one undoable batch`() = runTest {
        // 既有入口里还有 forEach { deleteBook(it) } 形式的批量循环，合并是它的兜底。
        seedFullBook("b1")
        seedFullBook("b2")

        val first = coordinator.deleteBook("b1")!!
        clock.now = 300L
        val second = coordinator.deleteBook("b2")!!

        assertEquals(first.id, second.id)
        assertEquals(1, coordinator.offers.value.size)
        assertEquals(2, coordinator.offers.value.single().bookCount)

        val outcome = coordinator.undo(first.id)

        assertEquals(2, outcome.restoredBookCount)
        assertTrue(fixture.isBookActive("b1"))
        assertTrue(fixture.isBookActive("b2"))
    }

    private companion object {
        const val STAMP = "2026-09-09T08:00:00Z"
    }
}
