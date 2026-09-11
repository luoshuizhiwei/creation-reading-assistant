package com.creationreadingassistant.feature.library.deletion

import android.content.Context
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.ContinueReadingStore
import com.creationreadingassistant.ui.viewmodel.ShelfBookActions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ShelfBookActions] 的删除委托契约。
 *
 * 协调器是可选依赖（Kotlin 默认参数），既有调用方——BookOperationsViewModel、既有单测——
 * 仍然只传三个参数。这几条用例锁住两件事：没有协调器时逐本委托的老语义一字不变，
 * 有协调器时整批只走一次调用（一次事务、一张凭证），不会退化成 forEach 多次删除。
 */
class ShelfBookDeletionDelegationTest {

    private val context: Context = mockk(relaxed = true)
    private val repository: BookRepository = mockk(relaxed = true)
    private val continueReadingStore: ContinueReadingStore = mockk(relaxed = true)
    private val coordinator: BookDeletionCoordinator = mockk(relaxed = true)

    private fun actions(withCoordinator: Boolean) = ShelfBookActions(
        context = context,
        repository = repository,
        continueReadingStore = continueReadingStore,
        deletions = if (withCoordinator) coordinator else null,
    )

    private fun credential(vararg bookIds: String) = BookDeletionCredential(
        id = "del-test",
        scope = DeletionScope.DELETE_BOOK,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
        expiresAtMillis = 12_000L,
        snapshots = bookIds.map { bookId ->
            BookDeletionSnapshot(
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
        },
    )

    @Test
    fun `without a coordinator the legacy per-book delegation is preserved`() = runTest {
        val ok = actions(withCoordinator = false).deleteBooks(listOf("a", "b", "a"))

        assertTrue(ok)
        // 老语义：去重后逐本调仓储的 deleteBook，不碰批量接口。
        coVerify(exactly = 1) { repository.deleteBook("a") }
        coVerify(exactly = 1) { repository.deleteBook("b") }
        coVerify(exactly = 0) { repository.deleteBooksScoped(any(), any()) }
    }

    @Test
    fun `without a coordinator undo is unavailable rather than silently wrong`() = runTest {
        val actions = actions(withCoordinator = false)

        // 没有凭证登记处就没有会话内撤销；返回 null 让界面层如实说「撤销窗口已结束」，
        // 而不是去调仓储那条恢复不了关联的兜底路径却宣称撤销成功。
        assertNull(actions.undoDeletion("del-test"))
        actions.dismissDeletionUndo("del-test")
    }

    @Test
    fun `with a coordinator the whole batch goes through a single call`() = runTest {
        coEvery { coordinator.deleteBooks(listOf("a", "b")) } returns credential("a", "b")

        val ok = actions(withCoordinator = true).deleteBooks(listOf("a", "b"))

        assertTrue(ok)
        coVerify(exactly = 1) { coordinator.deleteBooks(listOf("a", "b")) }
        // 不退化成逐本删除：那会登记多张凭证，一次撤销只拿回其中一批。
        coVerify(exactly = 0) { repository.deleteBook(any()) }
    }

    @Test
    fun `a single delete is just a batch of one`() = runTest {
        coEvery { coordinator.deleteBooks(listOf("a")) } returns credential("a")

        assertTrue(actions(withCoordinator = true).deleteBook("a"))
        coVerify(exactly = 1) { coordinator.deleteBooks(listOf("a")) }
    }

    @Test
    fun `an empty batch registers no credential and is not reported as a deletion`() = runTest {
        coEvery { coordinator.deleteBooks(emptyList()) } returns null

        assertFalse(actions(withCoordinator = true).deleteBooks(emptyList()))
        coVerify(exactly = 0) { repository.deleteBook(any()) }
    }

    @Test
    fun `a coordinator failure surfaces instead of being reported as success`() = runTest {
        coEvery { coordinator.deleteBooks(any()) } throws IllegalStateException("database is locked")

        val actions = actions(withCoordinator = true)

        // ShelfBookActions 是薄委托层：错误向上抛，由 ViewModel 转成「删除失败」文案。
        // 在这里吞掉会让书架明明没变却告诉用户删成功了。
        val failure = runCatching { actions.deleteBooks(listOf("a")) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun `undo and dismiss are forwarded to the coordinator`() = runTest {
        val outcome = DeletionUndoOutcome(restored = true)
        coEvery { coordinator.undo("del-test") } returns outcome

        val actions = actions(withCoordinator = true)

        assertEquals(outcome, actions.undoDeletion("del-test"))
        actions.dismissDeletionUndo("del-test")
        verify(exactly = 1) { coordinator.dismiss("del-test") }
    }
}
