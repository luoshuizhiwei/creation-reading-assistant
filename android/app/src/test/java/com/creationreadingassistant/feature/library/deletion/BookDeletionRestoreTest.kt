package com.creationreadingassistant.feature.library.deletion

import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.repository.BookRepository
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 删除作用范围 + 撤销完整性的行为回归。
 *
 * 每条用例都先在内存书库里摆出真实资料，再跑 [BookRepository] 的删除/恢复，断言库里的
 * 最终状态——不是断言某个方法被调了几次。覆盖 QODER.md R1 要求的回归点：关联与已读章节
 * 的恢复、此前已删的高亮不被复活、批量与单本、连续删除、重复撤销、事务失败、撤销窗口内
 * 关系目标被删、删除前就没有正文/记录。
 */
class BookDeletionRestoreTest {

    private lateinit var fixture: DeletionLibraryFixture
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        fixture = DeletionLibraryFixture()
        repository = fixture.repository()
    }

    /** 一本「什么都不缺」的书：资料、进度、会话、笔记、高亮、正文、文件、四类关联全都有。 */
    private fun seedFullBook(bookId: String = "b1") {
        fixture.addBook(bookId)
        fixture.addProgress(bookId, percent = 37f)
        fixture.addSession("s-$bookId", bookId)
        fixture.addNote("n-$bookId", bookId)
        fixture.addHighlight("h-$bookId", bookId)
        fixture.addContent(bookId, preview = "测试正文缓存")
        fixture.addFile(bookId)
        fixture.linkTag(bookId, "t1")
        fixture.linkCategory(bookId, "c1")
        fixture.linkShelf(bookId, "shelf1", position = 3)
        fixture.markChapterRead(bookId, 0)
        fixture.markChapterRead(bookId, 4)
    }

    private fun allTargetsLive() = LiveRelationTargets(
        tagIds = setOf("t1"),
        categoryIds = setOf("c1"),
        shelfIds = setOf("shelf1"),
    )

    @Test
    fun `delete removes every relation and undo puts them all back`() = runTest {
        seedFullBook()

        val snapshot = repository.deleteBookScoped("b1", DeletionLibraryFixture.STAMP_FIRST)

        // 删除后：书架上看不见，四类关联在库里彻底没有痕迹（连接表是硬删除）。
        assertFalse(fixture.isBookActive("b1"))
        assertEquals(DeletionLibraryFixture.STAMP_FIRST, fixture.bookStamp("b1"))
        assertTrue(fixture.tagIdsOf("b1").isEmpty())
        assertTrue(fixture.categoryIdsOf("b1").isEmpty())
        assertTrue(fixture.shelfLinksOf("b1").isEmpty())
        assertTrue(fixture.chapterIndexesOf("b1").isEmpty())
        assertTrue(fixture.activeNoteIds("b1").isEmpty())
        assertTrue(fixture.activeHighlightIds("b1").isEmpty())
        assertTrue(fixture.activeSessionIds("b1").isEmpty())
        assertEquals(DeletionLibraryFixture.STAMP_FIRST, fixture.progress["b1"]?.deleted_at)
        assertEquals(DeletionLibraryFixture.STAMP_FIRST, fixture.files["b1"]?.deleted_at)
        assertNull(fixture.contents["b1"]?.reader_preview)
        assertEquals(setOf("n-b1"), snapshot.notes.map { it.id }.toSet())
        assertEquals(setOf(0, 4), snapshot.chapterReads.map { it.chapter_index }.toSet())

        val report = repository.restoreDeletion(snapshot, allTargetsLive())

        assertTrue(report.restoredBook)
        assertTrue(fixture.isBookActive("b1"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b1"))
        assertEquals(setOf("c1"), fixture.categoryIdsOf("b1"))
        assertEquals(listOf(ShelfBookEntity("shelf1", "b1", position = 3)), fixture.shelfLinksOf("b1"))
        assertEquals(setOf(0, 4), fixture.chapterIndexesOf("b1"))
        assertEquals(setOf("n-b1"), fixture.activeNoteIds("b1"))
        assertEquals(setOf("h-b1"), fixture.activeHighlightIds("b1"))
        assertEquals(setOf("s-b1"), fixture.activeSessionIds("b1"))
        assertNull(fixture.progress["b1"]?.deleted_at)
        assertEquals(37f, fixture.progress["b1"]?.progress_percent ?: -1f, 0f)
        assertNull(fixture.files["b1"]?.deleted_at)
        assertEquals("测试正文缓存", fixture.contents["b1"]?.reader_preview)
        assertTrue(report.restoredProgress)
        assertTrue(report.restoredFile)
        assertTrue(report.restoredContent)
        assertEquals(1, report.restoredTagCount)
        assertEquals(1, report.restoredCategoryCount)
        assertEquals(1, report.restoredShelfLinkCount)
        assertEquals(2, report.restoredChapterReadCount)
        assertEquals(3, report.restoredRelationCount)
        assertEquals(0, report.skippedNewerChangeCount)
        assertTrue(report.skippedRelationTargetIds.isEmpty())
    }

    @Test
    fun `highlights and notes deleted before this operation are not resurrected`() = runTest {
        seedFullBook()
        // 本次删除之前，用户早就单独删掉了这三条；它们带着别的时间戳。
        fixture.addHighlight("h-old", "b1", deletedAt = DeletionLibraryFixture.STAMP_OTHER)
        fixture.addNote("n-old", "b1", deletedAt = DeletionLibraryFixture.STAMP_OTHER)
        fixture.addSession("s-old", "b1", deletedAt = DeletionLibraryFixture.STAMP_OTHER)

        val snapshot = repository.deleteBookScoped("b1", DeletionLibraryFixture.STAMP_FIRST)

        assertEquals(setOf("h-b1"), snapshot.highlights.map { it.id }.toSet())
        assertEquals(setOf("n-b1"), snapshot.notes.map { it.id }.toSet())
        assertEquals(setOf("s-b1"), snapshot.sessions.map { it.id }.toSet())

        val report = repository.restoreDeletion(snapshot, allTargetsLive())

        assertEquals(setOf("h-b1"), fixture.activeHighlightIds("b1"))
        assertEquals(setOf("n-b1"), fixture.activeNoteIds("b1"))
        assertEquals(setOf("s-b1"), fixture.activeSessionIds("b1"))
        // 旧戳原样保留：撤销没有把它们复活，也没有改写它们的删除时间。
        assertEquals(DeletionLibraryFixture.STAMP_OTHER, fixture.highlights["h-old"]?.deleted_at)
        assertEquals(DeletionLibraryFixture.STAMP_OTHER, fixture.notes["n-old"]?.deleted_at)
        assertEquals(DeletionLibraryFixture.STAMP_OTHER, fixture.sessions["s-old"]?.deleted_at)
        assertEquals(1, report.restoredHighlightCount)
        assertEquals(1, report.restoredNoteCount)
        assertEquals(1, report.restoredSessionCount)
        assertEquals(0, report.skippedNewerChangeCount)
    }

    @Test
    fun `legacy stamp-scoped restoreBook also leaves pre-deleted rows alone`() = runTest {
        // 没有快照的兜底路径（旧调用方仍在用 restoreBook）：靠同一个 deleted_at 戳界定范围。
        fixture.addBook("b1")
        fixture.addNote("n-live", "b1")
        fixture.addHighlight("h-live", "b1")
        fixture.addHighlight("h-old", "b1", deletedAt = DeletionLibraryFixture.STAMP_OTHER)

        repository.deleteBook("b1")

        val stamp = fixture.bookStamp("b1")
        assertNotNull(stamp)
        // 本次删除影响到的行都打在同一个戳上，这个戳就是作用范围。
        assertEquals(stamp, fixture.notes["n-live"]?.deleted_at)
        assertEquals(stamp, fixture.highlights["h-live"]?.deleted_at)
        assertEquals(DeletionLibraryFixture.STAMP_OTHER, fixture.highlights["h-old"]?.deleted_at)

        repository.restoreBook("b1")

        assertTrue(fixture.isBookActive("b1"))
        assertEquals(setOf("n-live"), fixture.activeNoteIds("b1"))
        assertEquals(setOf("h-live"), fixture.activeHighlightIds("b1"))
        // 早于本次删除就被删掉的高亮不会被笼统复活。
        assertEquals(DeletionLibraryFixture.STAMP_OTHER, fixture.highlights["h-old"]?.deleted_at)
        assertFalse(fixture.activeHighlightIds("b1").contains("h-old"))
    }

    @Test
    fun `batch delete captures one snapshot per book and undo restores exactly that batch`() = runTest {
        seedFullBook("b1")
        seedFullBook("b2")
        fixture.addBook("b3")
        fixture.linkTag("b3", "t1")

        val snapshots = repository.deleteBooksScoped(
            listOf("b1", "b2", "b1"),
            DeletionLibraryFixture.STAMP_FIRST,
        )

        // 重复 id 去重：撤销范围 == 入口提示的数量。
        assertEquals(listOf("b1", "b2"), snapshots.map { it.bookId })
        assertFalse(fixture.isBookActive("b1"))
        assertFalse(fixture.isBookActive("b2"))
        assertTrue(fixture.isBookActive("b3"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b3"))
        assertEquals(setOf("n-b1"), snapshots[0].notes.map { it.id }.toSet())
        assertEquals(setOf("n-b2"), snapshots[1].notes.map { it.id }.toSet())

        val reports = repository.restoreDeletions(snapshots, allTargetsLive())

        assertEquals(2, reports.size)
        assertTrue(reports.all { it.restoredBook })
        assertTrue(fixture.isBookActive("b1"))
        assertTrue(fixture.isBookActive("b2"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b1"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b2"))
        assertEquals(setOf(0, 4), fixture.chapterIndexesOf("b2"))
        assertEquals(setOf("n-b2"), fixture.activeNoteIds("b2"))
        // 没被删的书不受影响。
        assertTrue(fixture.isBookActive("b3"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b3"))
    }

    @Test
    fun `batch delete reads only target-book relationships instead of whole tables`() = runTest {
        seedFullBook("b1")
        seedFullBook("b2")
        seedFullBook("outside")

        repository.deleteBooksScoped(listOf("b1", "b2"), DeletionLibraryFixture.STAMP_FIRST)

        val targetIds = listOf("b1", "b2")
        coVerify(exactly = 1) { fixture.bookTagDao.getByBookIds(targetIds) }
        coVerify(exactly = 1) { fixture.bookCategoryDao.getByBookIds(targetIds) }
        coVerify(exactly = 1) { fixture.shelfBookDao.getByBookIds(targetIds) }
        coVerify(exactly = 1) { fixture.chapterReadDao.getByBookIds(targetIds) }
        coVerify(exactly = 1) { fixture.noteDao.getActiveByBookIds(targetIds) }
        coVerify(exactly = 1) { fixture.highlightDao.getActiveByBookIds(targetIds) }
        coVerify(exactly = 0) { fixture.bookTagDao.getAllActive() }
        coVerify(exactly = 0) { fixture.bookCategoryDao.getAllActive() }
        coVerify(exactly = 0) { fixture.shelfBookDao.getAllActive() }
        coVerify(exactly = 0) { fixture.chapterReadDao.getAll() }
        coVerify(exactly = 0) { fixture.noteDao.observeAllActive() }
        coVerify(exactly = 0) { fixture.highlightDao.observeAllActive() }
        assertTrue(fixture.isBookActive("outside"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("outside"))
        assertEquals(setOf("n-outside"), fixture.activeNoteIds("outside"))
        assertEquals(setOf("h-outside"), fixture.activeHighlightIds("outside"))
    }

    @Test
    fun `undo keeps legitimate changes made after the delete`() = runTest {
        seedFullBook()
        val snapshot = repository.deleteBookScoped("b1", DeletionLibraryFixture.STAMP_FIRST)

        // 删除之后：用户重新把书加回书单并排到了别的位置，也重新缓存了正文，
        // 书籍主行则被另一条路径（同步/详情恢复）先救回来了。
        fixture.linkShelf("b1", "shelf1", position = 9)
        fixture.contents["b1"] = BookContentEntity(book_id = "b1", reader_preview = "删除后重建的正文")
        fixture.books["b1"] = fixture.books.getValue("b1").copy(deleted_at = null)

        val report = repository.restoreDeletion(snapshot, allTargetsLive())

        assertTrue(report.bookAlreadyActive)
        assertFalse(report.restoredBook)
        // 书单位置不被快照改回 3，正文不被改回旧缓存。
        assertEquals(listOf(ShelfBookEntity("shelf1", "b1", position = 9)), fixture.shelfLinksOf("b1"))
        assertEquals("删除后重建的正文", fixture.contents["b1"]?.reader_preview)
        assertFalse(report.restoredContent)
        assertEquals(0, report.restoredShelfLinkCount)
        assertEquals(1, report.restoredTagCount)
        assertTrue(report.skippedNewerChangeCount > 0)
        // 进度仍是本次删除删掉的，照常恢复。
        assertTrue(report.restoredProgress)
        assertNull(fixture.progress["b1"]?.deleted_at)
    }

    @Test
    fun `undo skips relation targets that were deleted during the window`() = runTest {
        seedFullBook()
        fixture.linkTag("b1", "t2")
        val snapshot = repository.deleteBookScoped("b1", DeletionLibraryFixture.STAMP_FIRST)
        assertEquals(setOf("t1", "t2"), snapshot.tagIds.toSet())

        // 撤销之前，分类 c1 和标签 t2 被用户删掉了。
        val targets = LiveRelationTargets(
            tagIds = setOf("t1"),
            categoryIds = emptySet(),
            shelfIds = setOf("shelf1"),
        )

        val report = repository.restoreDeletion(snapshot, targets)

        assertTrue(fixture.isBookActive("b1"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b1"))
        // 不产生指向已删除目标的孤儿关系。
        assertTrue(fixture.categoryIdsOf("b1").isEmpty())
        assertEquals(1, report.restoredTagCount)
        assertEquals(0, report.restoredCategoryCount)
        assertEquals(1, report.restoredShelfLinkCount)
        assertEquals(setOf("c1", "t2"), report.skippedRelationTargetIds.toSet())
    }

    @Test
    fun `consecutive deletes - undoing the earlier one does not cancel the later one`() = runTest {
        seedFullBook()
        val first = repository.deleteBookScoped("b1", DeletionLibraryFixture.STAMP_FIRST)
        // 用户先撤销，然后又删了一次：第二次的戳盖掉了第一次。
        repository.restoreDeletion(first, allTargetsLive())
        assertTrue(fixture.isBookActive("b1"))
        val second = repository.deleteBookScoped("b1", DeletionLibraryFixture.STAMP_SECOND)
        assertEquals(DeletionLibraryFixture.STAMP_SECOND, fixture.bookStamp("b1"))

        val staleReport = repository.restoreDeletion(first, allTargetsLive())

        assertFalse(staleReport.restoredBook)
        assertFalse(staleReport.restoredAnything)
        assertEquals(1, staleReport.skippedNewerChangeCount)
        assertFalse(fixture.isBookActive("b1"))
        assertEquals(DeletionLibraryFixture.STAMP_SECOND, fixture.bookStamp("b1"))
        // 过期凭证连关联都不能写回去，否则会和第二次删除的范围打架。
        assertTrue(fixture.tagIdsOf("b1").isEmpty())

        val currentReport = repository.restoreDeletion(second, allTargetsLive())

        assertTrue(currentReport.restoredBook)
        assertTrue(fixture.isBookActive("b1"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b1"))
        assertEquals(setOf(0, 4), fixture.chapterIndexesOf("b1"))
        assertEquals(setOf("n-b1"), fixture.activeNoteIds("b1"))
    }

    @Test
    fun `repeat undo is idempotent and does not duplicate relations`() = runTest {
        seedFullBook()
        val snapshot = repository.deleteBookScoped("b1", DeletionLibraryFixture.STAMP_FIRST)

        val first = repository.restoreDeletion(snapshot, allTargetsLive())
        val shelfLinksAfterFirst = fixture.shelfLinksOf("b1")
        val tagsAfterFirst = fixture.tagIdsOf("b1")
        val chaptersAfterFirst = fixture.chapterIndexesOf("b1")

        val second = repository.restoreDeletion(snapshot, allTargetsLive())

        assertTrue(first.restoredAnything)
        assertFalse(second.restoredAnything)
        assertTrue(second.bookAlreadyActive)
        assertEquals(shelfLinksAfterFirst, fixture.shelfLinksOf("b1"))
        assertEquals(tagsAfterFirst, fixture.tagIdsOf("b1"))
        assertEquals(chaptersAfterFirst, fixture.chapterIndexesOf("b1"))
        // 连接表按主键 REPLACE 且只补当前不存在的：重复撤销不会造出重复关系。
        assertEquals(1, fixture.bookTags.values.count { it.book_id == "b1" })
        assertEquals(1, fixture.bookCategories.values.count { it.book_id == "b1" })
        assertEquals(1, fixture.shelfBooks.values.count { it.book_id == "b1" })
        assertEquals(2, fixture.chapterReads.values.count { it.book_id == "b1" })
        assertEquals(1, fixture.sessions.values.count { it.book_id == "b1" && it.deleted_at == null })
    }

    @Test
    fun `a book with no content and no records before delete still round-trips`() = runTest {
        // 只有书架资料，从没打开过：没有进度、会话、笔记、高亮、正文、文件、关联。
        fixture.addBook("b-empty")

        val snapshot = repository.deleteBookScoped("b-empty", DeletionLibraryFixture.STAMP_FIRST)

        assertNull(snapshot.progress)
        assertNull(snapshot.content)
        assertNull(snapshot.file)
        assertTrue(snapshot.sessions.isEmpty())
        assertTrue(snapshot.notes.isEmpty())
        assertTrue(snapshot.highlights.isEmpty())
        assertTrue(snapshot.tagIds.isEmpty())
        assertTrue(snapshot.chapterReads.isEmpty())
        assertFalse(snapshot.isEmpty)

        val report = repository.restoreDeletion(snapshot, LiveRelationTargets.EMPTY)

        assertTrue(report.restoredBook)
        assertTrue(fixture.isBookActive("b-empty"))
        assertFalse(report.restoredProgress)
        assertFalse(report.restoredContent)
        assertFalse(report.contentPayloadDropped)
        assertEquals(0, report.restoredSessionCount)
        assertEquals(0, report.restoredRelationCount)
        assertEquals(0, report.skippedNewerChangeCount)
        // 空 IN () 会被 SQLite 拒绝：没有会话时不能去批量查会话。
        coVerify(exactly = 0) { fixture.sessionDao.getByIds(match { it.isEmpty() }) }
    }

    @Test
    fun `failed delete transaction leaves the library exactly as it was`() = runTest {
        seedFullBook()
        // chapterReadDao.clearForBook 是 applyDeletion 的最后一步：
        // 抛错时前面已经写进去的软删除与已经清掉的关联都必须回滚。
        fixture.failOnChapterReadClear = true

        val failure = runCatching {
            repository.deleteBookScoped("b1", DeletionLibraryFixture.STAMP_FIRST)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(fixture.isBookActive("b1"))
        assertNull(fixture.bookStamp("b1"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b1"))
        assertEquals(setOf("c1"), fixture.categoryIdsOf("b1"))
        assertEquals(1, fixture.shelfLinksOf("b1").size)
        assertEquals(setOf(0, 4), fixture.chapterIndexesOf("b1"))
        assertEquals(setOf("n-b1"), fixture.activeNoteIds("b1"))
        assertEquals(setOf("h-b1"), fixture.activeHighlightIds("b1"))
        assertEquals(setOf("s-b1"), fixture.activeSessionIds("b1"))
        assertNull(fixture.progress["b1"]?.deleted_at)
        assertEquals("测试正文缓存", fixture.contents["b1"]?.reader_preview)
        assertNull(fixture.files["b1"]?.deleted_at)
    }

    @Test
    fun `dropped content payload is reported instead of silently pretending to restore`() = runTest {
        seedFullBook()
        val snapshot = repository.deleteBookScoped("b1", DeletionLibraryFixture.STAMP_FIRST)
        // 内存预算把正文载荷裁掉了：资料与阅读数据仍然要完整恢复。
        val trimmed = snapshot.copy(
            content = snapshot.content?.copy(reader_preview = null, epub_json = null),
            contentPayloadRetained = false,
        )

        val report = repository.restoreDeletion(trimmed, allTargetsLive())

        assertTrue(report.restoredBook)
        assertTrue(report.contentPayloadDropped)
        assertFalse(report.restoredContent)
        assertEquals(setOf("t1"), fixture.tagIdsOf("b1"))
        assertEquals(setOf(0, 4), fixture.chapterIndexesOf("b1"))
        assertEquals(setOf("n-b1"), fixture.activeNoteIds("b1"))
        assertNull(fixture.contents["b1"]?.reader_preview)
    }

    @Test
    fun `a book row gone from the database is re-inserted from the snapshot`() = runTest {
        seedFullBook()
        val snapshot = repository.deleteBookScoped("b1", DeletionLibraryFixture.STAMP_FIRST)
        // 行被彻底清掉（例如同步覆盖）：没有主行兜着，子表写回会变成外键孤儿。
        fixture.books.remove("b1")

        val report = repository.restoreDeletion(snapshot, allTargetsLive())

        assertTrue(report.restoredBook)
        assertTrue(fixture.isBookActive("b1"))
        assertEquals(setOf("t1"), fixture.tagIdsOf("b1"))
        assertEquals(setOf("n-b1"), fixture.activeNoteIds("b1"))
        assertEquals(setOf(0, 4), fixture.chapterIndexesOf("b1"))
    }
}
