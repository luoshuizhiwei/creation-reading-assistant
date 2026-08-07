package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import com.creationreadingassistant.data.repository.StatsRepository
import com.creationreadingassistant.data.repository.startEpochSecondOf
import com.creationreadingassistant.data.settings.ContinueReadingStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.DayOfWeek
import java.time.LocalDate
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
 * HomeViewModel 首页聚合流测试：统计字段、继续阅读过滤、完成列表与灵感排序。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: BookRepository
    private lateinit var continueReadingStore: ContinueReadingStore
    private lateinit var inspirationRepository: InspirationRepository
    private lateinit var statsRepository: StatsRepository

    private val now: LocalDate = LocalDate.now()

    private fun ts(date: LocalDate): String = "$date" + "T12:00:00Z"

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = mockk()
        continueReadingStore = mockk { every { removedIds } returns flowOf(emptyMap()) }
        inspirationRepository = mockk()
        statsRepository = mockk {
            coEvery { sumAllActiveDuration() } returns 0L
            coEvery { sumOccurredDurationBetween(any(), any(), any()) } returns 0L
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(
        books: List<BookEntity> = emptyList(),
        progress: List<ReadingProgressEntity> = emptyList(),
        sessions: List<ReadingSessionEntity> = emptyList(),
        inspirations: List<InspirationEntity> = emptyList(),
    ): HomeViewModel {
        every { repository.observeBooks() } returns flowOf(books)
        every { repository.observeProgress() } returns flowOf(progress)
        every { repository.observeSessions() } returns flowOf(sessions)
        every { inspirationRepository.observeAllActive() } returns flowOf(inspirations)
        // 总时长/今日时长已下推为 SQL SUM：按传入的会话列表模拟与旧内存聚合一致的结果
        val totalMs = sessions.sumOf { it.duration_ms }
        val todayMs = sessions
            .filter { it.started_at == ts(now) || (it.started_at == null && it.created_at == ts(now)) }
            .sumOf { it.duration_ms }
        coEvery { statsRepository.sumAllActiveDuration() } returns totalMs
        coEvery { statsRepository.sumOccurredDurationBetween(any(), any(), any()) } returns todayMs
        return HomeViewModel(
            repository = repository,
            continueReadingStore = continueReadingStore,
            inspirationRepository = inspirationRepository,
            statsRepository = statsRepository,
            defaultDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun book(
        id: String,
        title: String = "书$id",
        importedAt: String? = ts(now),
        contentStatus: String = "available",
        deletedAt: String? = null,
    ): BookEntity = BookEntity(
        id = id,
        title = title,
        format = "txt",
        size = 100,
        content_hash = "hash-$id",
        content_status = contentStatus,
        imported_at = importedAt,
        updated_at = ts(now),
        deleted_at = deletedAt,
    )

    private fun progress(
        bookId: String,
        percent: Float = 0f,
        completionState: String = "reading",
        lastReadAt: String? = null,
        completedAt: Long? = null,
    ): ReadingProgressEntity = ReadingProgressEntity(
        book_id = bookId,
        progress_percent = percent,
        last_read_at = lastReadAt,
        completion_state = completionState,
        updated_at = ts(now),
        completed_at = completedAt,
    )

    private fun session(
        id: String,
        bookId: String,
        durationMs: Long,
        startedAt: String? = ts(now),
    ): ReadingSessionEntity = ReadingSessionEntity(
        id = id,
        book_id = bookId,
        started_at = startedAt,
        duration_ms = durationMs,
        updated_at = ts(now),
    )

    private fun inspiration(id: String, updatedAt: String): InspirationEntity = InspirationEntity(
        id = id,
        title = "灵感$id",
        body = "正文",
        updated_at = updatedAt,
        created_at = updatedAt,
    )

    @Test
    fun `empty sources produce ready state with empty aggregates`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        val job = launch { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isReady)
        assertTrue(state.books.isEmpty())
        assertEquals(0, state.totalReadBooksCount)
        assertEquals(0, state.readingCount)
        assertEquals(0, state.thisWeekNew)
        assertEquals(0L, state.totalReadingMs)
        assertEquals(0L, state.todayReadingMs)
        assertTrue(state.continueBooks.isEmpty())
        assertTrue(state.completedBooks.isEmpty())

        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `stats count read books sessions and week imports`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm(
            books = listOf(
                book("b1", importedAt = ts(now)),
                book("b2", importedAt = ts(now.minusDays(10))),
                book("b3", importedAt = ts(now.minusDays(10))),
                book("b4", importedAt = ts(now.minusDays(10)), contentStatus = "missing"),
            ),
            progress = listOf(
                progress("b1", percent = 30f, lastReadAt = ts(now)),
                progress("b2", percent = 100f, completionState = "finished", completedAt = 1000L),
            ),
            sessions = listOf(
                session("s1", "b1", 600_000L, ts(now)),
                session("s2", "b1", 300_000L, ts(now.minusDays(1))),
                session("s3", "b2", 60_000L, ts(now)),
            ),
        )
        val job = launch { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.totalReadBooksCount)
        assertEquals(1, state.readingCount)
        assertEquals(1, state.thisWeekNew)
        assertEquals(960_000L, state.totalReadingMs)
        assertEquals(660_000L, state.todayReadingMs)
        assertEquals(listOf("b2"), state.completedBooks.map { it.id })
        // 今日窗口必须恰好是 [本地今日零点, 本地明日零点)
        coVerify {
            statsRepository.sumOccurredDurationBetween(
                startEpochSecondOf(LocalDate.now()),
                startEpochSecondOf(LocalDate.now().plusDays(1)),
                any(),
            )
        }

        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `continue books exclude unread completed shelved and removed`() = runTest(mainDispatcher.scheduler) {
        val nowIso = ts(now)
        every { continueReadingStore.removedIds } returns flowOf(
            mapOf("b5" to nowIso),
        )
        val vm = createVm(
            books = listOf(
                book("b1"),   // 在读，最近读 → 应保留
                book("b2"),   // 已完成 → 排除
                book("b3"),   // 未读 → 排除
                book("b4"),   // 搁置 → 排除
                book("b5"),   // 最近读过但被用户移除 → 排除
            ),
            progress = listOf(
                progress("b1", percent = 30f, lastReadAt = nowIso),
                progress("b2", percent = 99.6f, lastReadAt = nowIso),
                progress("b4", percent = 20f, completionState = "shelved", lastReadAt = nowIso),
                progress("b5", percent = 10f, lastReadAt = nowIso),
            ),
        )
        val job = launch { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("b1"), vm.uiState.value.continueBooks.map { it.id })

        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `continue books cap at eight`() = runTest(mainDispatcher.scheduler) {
        val books = (1..10).map { i -> book("b$i") }
        val progress = (1..10).map { i ->
            progress("b$i", percent = i.toFloat(), lastReadAt = ts(now.minusDays(i.toLong())))
        }
        val vm = createVm(books = books, progress = progress)
        val job = launch { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        val continueBooks = vm.uiState.value.continueBooks
        assertEquals(8, continueBooks.size)
        assertEquals("b1", continueBooks.first().id)

        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `recent inspirations keep newest five`() = runTest(mainDispatcher.scheduler) {
        val inspirations = (1..6).map { i ->
            inspiration("i$i", ts(now.minusDays((6 - i).toLong())))
        }
        val vm = createVm(inspirations = inspirations)
        val job = launch { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        val recent = vm.uiState.value.recentInspirations
        assertEquals(listOf("i6", "i5", "i4", "i3", "i2"), recent.map { it.id })

        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `completed books ignore undisplayable entries`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm(
            books = listOf(
                book("b1"),
                book("b2", contentStatus = "failed"),
                book("b3", deletedAt = ts(now)),
            ),
            progress = listOf(
                progress("b1", percent = 100f, completionState = "finished"),
                progress("b2", percent = 100f, completionState = "finished"),
                progress("b3", percent = 100f, completionState = "finished"),
            ),
        )
        val job = launch { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("b1"), vm.uiState.value.completedBooks.map { it.id })

        job.cancel()
        testScheduler.advanceUntilIdle()
    }
}
