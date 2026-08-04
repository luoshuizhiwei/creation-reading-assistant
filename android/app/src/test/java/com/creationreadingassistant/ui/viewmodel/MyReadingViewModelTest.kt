package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.repository.BookRepository
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneId
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
 * MyReadingViewModel 阅读史列表测试：纯聚合函数 buildMyReadingUiState + VM 查询/筛选转发。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyReadingViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val utc = ZoneId.of("UTC")

    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun book(
        id: String,
        title: String = "书$id",
        author: String? = null,
        importedAt: String = "2026-01-01T12:00:00Z",
    ): BookEntity = BookEntity(
        id = id,
        title = title,
        author = author,
        format = "txt",
        size = 100,
        content_hash = "h-$id",
        imported_at = importedAt,
        updated_at = importedAt,
    )

    private fun progress(
        bookId: String,
        percent: Float = 0f,
        state: String = "reading",
        lastReadAt: String? = null,
        totalTimeMs: Long = 0L,
    ): ReadingProgressEntity = ReadingProgressEntity(
        book_id = bookId,
        progress_percent = percent,
        last_read_at = lastReadAt,
        total_reading_time_ms = totalTimeMs,
        completion_state = state,
        updated_at = "2026-08-01T12:00:00Z",
    )

    private fun session(
        id: String,
        bookId: String,
        startedAt: String,
        durationMs: Long = 60_000L,
    ): ReadingSessionEntity = ReadingSessionEntity(
        id = id,
        book_id = bookId,
        started_at = startedAt,
        duration_ms = durationMs,
        updated_at = startedAt,
    )

    @Test
    fun `pure function excludes books without any reading record`() {
        val books = listOf(
            book("b1"),
            book("b2"),
            book("b3"),
        )
        val state = buildMyReadingUiState(
            books = books,
            progress = listOf(
                progress("b2", percent = 0f),
                progress("b3", percent = 5f),
            ),
            sessions = emptyList(),
            query = "",
            filter = MyReadingFilter.ALL,
            zoneId = utc,
        )

        val ids = state.months.flatMap { it.items }.map { it.book.id }
        assertEquals(listOf("b3"), ids)
        assertEquals(1, state.counts[MyReadingFilter.ALL])
        assertTrue(state.isReady)
    }

    @Test
    fun `pure function marks near finished books and counts states`() {
        val state = buildMyReadingUiState(
            books = listOf(book("b1"), book("b2"), book("b3"), book("b4")),
            progress = listOf(
                progress("b1", percent = 99.5f),
                progress("b2", percent = 10f, state = "shelved"),
                progress("b3", percent = 30f),
            ),
            sessions = listOf(session("s1", "b4", "2026-08-01T12:00:00Z")),
            query = "",
            filter = MyReadingFilter.ALL,
            zoneId = utc,
        )

        val byId = state.months.flatMap { it.items }.associateBy { it.book.id }
        assertEquals(ReadingCompletionState.FINISHED, byId["b1"]!!.state)
        assertEquals(ReadingCompletionState.SHELVED, byId["b2"]!!.state)
        assertEquals(ReadingCompletionState.READING, byId["b3"]!!.state)
        assertEquals(4, state.counts[MyReadingFilter.ALL])
        assertEquals(1, state.counts[MyReadingFilter.FINISHED])
        assertEquals(1, state.counts[MyReadingFilter.SHELVED])
        assertEquals(2, state.counts[MyReadingFilter.READING])
    }

    @Test
    fun `pure function groups items into months by start date`() {
        val state = buildMyReadingUiState(
            books = listOf(book("b1"), book("b2"), book("b3")),
            progress = emptyList(),
            sessions = listOf(
                session("s1", "b1", "2026-08-10T12:00:00Z"),
                session("s2", "b2", "2026-07-05T12:00:00Z"),
                session("s3", "b3", "2026-08-20T12:00:00Z"),
            ),
            query = "",
            filter = MyReadingFilter.ALL,
            zoneId = utc,
        )

        assertEquals(listOf(2026 to 8, 2026 to 7), state.months.map { it.year to it.month })
        assertEquals(2, state.months[0].items.size)
        assertEquals(1, state.months[1].items.size)
    }

    @Test
    fun `pure function filters by query and status filter`() {
        val state = buildMyReadingUiState(
            books = listOf(book("b1", title = "三体 第二部", author = "刘慈欣"), book("b2", title = "百年孤独")),
            progress = listOf(
                progress("b1", percent = 30f),
                progress("b2", percent = 100f, state = "finished"),
            ),
            sessions = emptyList(),
            query = " 三体 ",
            filter = MyReadingFilter.READING,
            zoneId = utc,
        )

        val ids = state.months.flatMap { it.items }.map { it.book.id }
        assertEquals(listOf("b1"), ids)
        assertEquals(" 三体 ", state.query)
        assertEquals(MyReadingFilter.READING, state.filter)
    }

    @Test
    fun `pure function sorts items by last activity desc`() {
        val state = buildMyReadingUiState(
            books = listOf(book("b1"), book("b2"), book("b3")),
            progress = listOf(
                progress("b1", percent = 10f, lastReadAt = "2026-08-01T12:00:00Z"),
                progress("b2", percent = 20f, lastReadAt = "2026-08-05T12:00:00Z"),
                progress("b3", percent = 30f, lastReadAt = "2026-08-03T12:00:00Z"),
            ),
            sessions = emptyList(),
            query = "",
            filter = MyReadingFilter.ALL,
            zoneId = utc,
        )

        val ids = state.months.flatMap { it.items }.map { it.book.id }
        assertEquals(listOf("b2", "b3", "b1"), ids)
    }

    @Test
    fun `vm setQuery and setFilter propagate to uiState`() = runTest(mainDispatcher.scheduler) {
        every { repository.observeBooks() } returns flowOf(listOf(book("b1", title = "三体")))
        every { repository.observeProgress() } returns flowOf(listOf(progress("b1", percent = 30f)))
        every { repository.observeSessions() } returns flowOf(emptyList())
        val vm = MyReadingViewModel(repository, Dispatchers.Unconfined)
        val job = launch { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        assertEquals(MyReadingFilter.ALL, vm.uiState.value.filter)
        assertEquals(1, vm.uiState.value.counts[MyReadingFilter.ALL])

        vm.setFilter(MyReadingFilter.READING)
        vm.setQuery("三体")
        testScheduler.advanceUntilIdle()

        assertEquals(MyReadingFilter.READING, vm.uiState.value.filter)
        assertEquals("三体", vm.uiState.value.query)
        assertEquals(1, vm.uiState.value.months.flatMap { it.items }.size)

        vm.setQuery("不存在的书")
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.months.flatMap { it.items }.isEmpty())

        job.cancel()
        testScheduler.advanceUntilIdle()
    }
}
