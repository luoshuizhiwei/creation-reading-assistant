package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.ContinueReadingStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ShelfBookActions 书籍级写操作的返回值与委托测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShelfBookActionsTest {

    private lateinit var repository: BookRepository
    private lateinit var continueReadingStore: ContinueReadingStore
    private lateinit var actions: ShelfBookActions

    private fun uriMock(s: String): Uri {
        val u = mockk<Uri>()
        every { u.toString() } returns s
        return u
    }

    @Before
    fun setUp() {
        repository = mockk()
        continueReadingStore = mockk()
        val context = mockk<Context>()
        actions = ShelfBookActions(context, repository, continueReadingStore)
    }

    @Test
    fun `restore book returns success message`() = runTest {
        coEvery { repository.restoreBook("b1") } returns Unit

        val msg = actions.restoreBook("b1")

        assertEquals("已恢复书籍", msg)
    }

    @Test
    fun `restore book returns failure message on error`() = runTest {
        coEvery { repository.restoreBook("b1") } throws IllegalStateException("boom")

        val msg = actions.restoreBook("b1")

        assertTrue(msg.contains("恢复失败"))
        assertTrue(msg.contains("boom"))
    }

    @Test
    fun `shelve book clears continue reading entry`() = runTest {
        coEvery { repository.setReadingState("b1", ReadingCompletionState.SHELVED) } returns Unit
        coEvery { continueReadingStore.clear("b1") } returns Unit

        val msg = actions.shelveBook("b1")

        assertEquals("已搁置，阅读记录仍会保留", msg)
        coVerify(exactly = 1) { continueReadingStore.clear("b1") }
    }

    @Test
    fun `shelve book reports failure`() = runTest {
        coEvery { repository.setReadingState(any(), any()) } throws IllegalStateException("boom")

        val msg = actions.shelveBook("b1")

        assertTrue(msg.contains("搁置失败"))
    }

    @Test
    fun `restore reading maps to success result`() = runTest {
        coEvery { repository.setReadingState("b1", ReadingCompletionState.READING) } returns Unit
        coEvery { continueReadingStore.clear("b1") } returns Unit

        val result = actions.restoreReading("b1")

        assertTrue(result.isSuccess)
        assertEquals("已恢复为在读", result.getOrNull())
    }

    @Test
    fun `restore reading maps to failure result`() = runTest {
        coEvery { repository.setReadingState(any(), any()) } throws IllegalStateException("boom")

        val result = actions.restoreReading("b1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("boom"))
    }

    @Test
    fun `update book info rejects blank title`() = runTest {
        val msg = actions.updateBookInfo("b1", title = "   ", author = null)

        assertEquals("书名不能为空", msg)
        coVerify(exactly = 0) { repository.updateBookInfo(any(), any(), any(), any()) }
    }

    @Test
    fun `update book info trims values before saving`() = runTest {
        coEvery { repository.updateBookInfo(any(), any(), any(), any()) } returns Unit

        val msg = actions.updateBookInfo("b1", title = "  新标题 ", author = "  作者 ", description = " 简介 ")

        assertEquals("已更新书籍信息", msg)
        coVerify(exactly = 1) {
            repository.updateBookInfo("b1", "新标题", "作者", "简介")
        }
    }

    @Test
    fun `update book cover delegates to repository`() = runTest {
        coEvery { repository.updateBookCover("b1", "content://covers/1") } returns Unit

        val msg = actions.updateBookCover("b1", uriMock("content://covers/1"))

        assertEquals("已更新封面", msg)
        coVerify(exactly = 1) { repository.updateBookCover("b1", "content://covers/1") }
    }

    @Test
    fun `text cover and reset cover delegate`() = runTest {
        coEvery { repository.updateBookCover(any(), any()) } returns Unit

        assertEquals("已生成文字封面", actions.setBookTextCover("b1", "data:image/svg+xml"))
        assertEquals("已重置封面", actions.resetBookCover("b1"))

        coVerify(exactly = 1) { repository.updateBookCover("b1", "data:image/svg+xml") }
        coVerify(exactly = 1) { repository.updateBookCover("b1", null) }
    }

    @Test
    fun `clear cache returns count message`() = runTest {
        coEvery { repository.clearBookCache(any()) } returns Unit

        val msg = actions.clearCacheForBooks(listOf("b1", "b2"))

        assertEquals("已清理 2 本书的本地正文缓存", msg)
        coVerify(exactly = 1) { repository.clearBookCache("b1") }
        coVerify(exactly = 1) { repository.clearBookCache("b2") }
    }

    @Test
    fun `delete book delegates to repository`() = runTest {
        coEvery { repository.deleteBook("b1") } returns Unit

        actions.deleteBook("b1")

        coVerify(exactly = 1) { repository.deleteBook("b1") }
    }
}
