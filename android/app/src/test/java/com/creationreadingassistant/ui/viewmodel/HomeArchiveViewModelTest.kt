package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
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
 * HomeArchiveViewModel 首页「已完成 + 灵感归档」聚合流测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeArchiveViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: BookRepository
    private lateinit var inspirationRepository: InspirationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = mockk()
        inspirationRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun book(
        id: String,
        deletedAt: String? = null,
    ): BookEntity = BookEntity(
        id = id,
        title = "书$id",
        format = "txt",
        size = 100,
        content_hash = "h-$id",
        updated_at = "2026-08-01T12:00:00Z",
        deleted_at = deletedAt,
    )

    private fun progress(
        bookId: String,
        percent: Float = 0f,
        state: String = "reading",
        completedAt: Long? = null,
    ): ReadingProgressEntity = ReadingProgressEntity(
        book_id = bookId,
        progress_percent = percent,
        completion_state = state,
        completed_at = completedAt,
        updated_at = "2026-08-01T12:00:00Z",
    )

    private fun createVm(
        books: List<BookEntity> = emptyList(),
        progress: List<ReadingProgressEntity> = emptyList(),
        inspirations: List<InspirationEntity> = emptyList(),
    ): HomeArchiveViewModel {
        every { repository.observeBooks() } returns flowOf(books)
        every { repository.observeProgress() } returns flowOf(progress)
        every { inspirationRepository.observeAllActive() } returns flowOf(inspirations)
        return HomeArchiveViewModel(repository, inspirationRepository, Dispatchers.Unconfined)
    }

    @Test
    fun `completed books include finished and near finished without progress excluded`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm(
            books = listOf(
                book("b1"),                  // finished
                book("b2"),                  // percent >= 99.5
                book("b3"),                  // in reading -> excluded
                book("b4"),                  // no progress -> excluded
                book("b5", deletedAt = "2026-07-01T12:00:00Z"), // deleted finished -> excluded
            ),
            progress = listOf(
                progress("b1", percent = 100f, state = "finished", completedAt = 2000L),
                progress("b2", percent = 99.6f, state = "reading"),
                progress("b3", percent = 30f, state = "reading"),
                progress("b5", percent = 100f, state = "finished"),
            ),
        )
        val job = launch { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        val ids = vm.uiState.value.completedBooks.map { it.book.id }
        assertEquals(listOf("b1", "b2"), ids)
        assertTrue(vm.uiState.value.isReady)

        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `completed books sort by completed time desc`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm(
            books = listOf(book("b1"), book("b2"), book("b3")),
            progress = listOf(
                progress("b1", percent = 100f, state = "finished", completedAt = 1000L),
                progress("b2", percent = 100f, state = "finished", completedAt = 3000L),
                progress("b3", percent = 100f, state = "finished", completedAt = 2000L),
            ),
        )
        val job = launch { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("b2", "b3", "b1"), vm.uiState.value.completedBooks.map { it.book.id })

        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `inspirations sorted by updated at desc`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm(
            inspirations = (1..3).map { i ->
                InspirationEntity(
                    id = "i$i",
                    title = "t",
                    updated_at = "2026-08-0$i" + "T10:00:00Z",
                    created_at = "2026-08-01T10:00:00Z",
                )
            },
        )
        val job = launch { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("i3", "i2", "i1"), vm.uiState.value.inspirations.map { it.id })

        job.cancel()
        testScheduler.advanceUntilIdle()
    }
}
