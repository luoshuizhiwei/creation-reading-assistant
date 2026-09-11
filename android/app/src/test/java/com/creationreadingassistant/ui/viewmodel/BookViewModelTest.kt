package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.ContinueReadingStore
import com.creationreadingassistant.feature.library.deletion.BookDeletionCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * BookViewModel 书籍管理动作与流投影测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: BookRepository
    private lateinit var continueStore: ContinueReadingStore
    private lateinit var deletions: BookDeletionCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = mockk {
            coEvery { deleteBook(any()) } returns Unit
            coEvery { updateReadingProgress(any(), any(), any()) } returns Unit
            coEvery { setReadingState(any(), any()) } returns Unit
        }
        continueStore = mockk {
            every { removedIds } returns flowOf(emptyMap())
            coEvery { remove(any()) } returns Unit
            coEvery { clear(any()) } returns Unit
        }
        deletions = mockk {
            coEvery { deleteBook(any()) } returns null
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(
        books: List<BookEntity> = emptyList(),
        progressRows: List<ReadingProgressEntity> = emptyList(),
        sessions: List<ReadingSessionEntity> = emptyList(),
    ): BookViewModel {
        every { repository.observeBooks() } returns flowOf(books)
        every { repository.observeProgress() } returns flowOf(progressRows)
        every { repository.observeSessions() } returns flowOf(sessions)
        return BookViewModel(repository, continueStore, deletions, Dispatchers.Unconfined)
    }

    @Test
    fun `deleteBook success reports deleted message`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        var result: String? = null
        vm.deleteBook("b1") { result = it }
        testScheduler.advanceUntilIdle()

        assertEquals("已删除", result)
        coVerify(exactly = 1) { deletions.deleteBook("b1") }
    }

    @Test
    fun `deleteBook failure reports failure message`() = runTest(mainDispatcher.scheduler) {
        coEvery { deletions.deleteBook(any()) } throws IllegalStateException("boom")
        val vm = createVm()

        var result: String? = null
        vm.deleteBook("b1") { result = it }
        testScheduler.advanceUntilIdle()

        assertTrue(result!!.contains("删除失败"))
        assertTrue(result!!.contains("boom"))
    }

    @Test
    fun `markRead and markUnread update progress state`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        vm.markRead("b1")
        vm.markUnread("b2")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateReadingProgress("b1", 100f, "finished") }
        coVerify(exactly = 1) { repository.updateReadingProgress("b2", 0f, "reading") }
    }

    @Test
    fun `shelve sets shelved state and clears continue entry`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        vm.shelve("b1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.setReadingState("b1", ReadingCompletionState.SHELVED) }
        coVerify(exactly = 1) { continueStore.clear("b1") }
    }

    @Test
    fun `restoreReading sets reading state and clears continue entry`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        vm.restoreReading("b1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.setReadingState("b1", ReadingCompletionState.READING) }
        coVerify(exactly = 1) { continueStore.clear("b1") }
    }

    @Test
    fun `removeFromContinue and clearContinueRemoval delegate to store`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        vm.removeFromContinue("b1")
        vm.clearContinueRemoval("b2")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { continueStore.remove("b1") }
        coVerify(exactly = 1) { continueStore.clear("b2") }
    }

    @Test
    fun `progressById groups progress rows by book`() = runTest(mainDispatcher.scheduler) {
        val progress = listOf(
            ReadingProgressEntity(book_id = "b1", progress_percent = 10f, updated_at = "2026-08-01T12:00:00Z"),
            ReadingProgressEntity(book_id = "b2", progress_percent = 20f, updated_at = "2026-08-01T12:00:00Z"),
        )
        val vm = createVm(progressRows = progress)
        val job = launch { vm.progressById.collect { } }
        testScheduler.advanceUntilIdle()

        assertEquals(setOf("b1", "b2"), vm.progressById.value.keys)
        assertEquals(10f, vm.progressById.value["b1"]!!.progress_percent)

        job.cancel()
        testScheduler.advanceUntilIdle()
    }
}
