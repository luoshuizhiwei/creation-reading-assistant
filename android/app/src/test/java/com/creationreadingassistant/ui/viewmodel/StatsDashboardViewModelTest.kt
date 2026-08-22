package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.dao.StatsBookRow
import com.creationreadingassistant.data.local.dao.StatsCreatedRow
import com.creationreadingassistant.data.local.dao.StatsProgressRow
import com.creationreadingassistant.data.local.dao.StatsSessionRow
import com.creationreadingassistant.data.repository.StatsRepository
import com.creationreadingassistant.ui.screen.stats.EMPTY_STATS
import com.creationreadingassistant.ui.screen.stats.StatsPeriod
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * StatsDashboardViewModel 单测：
 * - 周期切换正确性
 * - 空数据（EMPTY_STATS 单例复用，不构建图表结构）
 * - 大量 session 聚合
 * - 相同 DB 结果不得重复触发重算（tables distinctUntilChanged + 图表模型缓存）
 * - 快速来回切换周期后不显示旧状态
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsDashboardViewModelTest {
    private val statsRepository = mockk<StatsRepository>()
    private val goalStore = mockk<com.creationreadingassistant.data.settings.GoalStore>()

    // sessions 用 SharedFlow：MutableStateFlow 会按值相等吞掉等值发射，
    // 无法模拟 Room 无关表失效导致的「等价结果重查重发」。
    private val sessionsFlow = MutableSharedFlow<List<StatsSessionRow>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val progressFlow = MutableStateFlow<List<StatsProgressRow>>(emptyList())
    private val booksFlow = MutableStateFlow<List<StatsBookRow>>(emptyList())
    private val inspirationsFlow = MutableStateFlow<List<StatsCreatedRow>>(emptyList())
    private val notesFlow = MutableStateFlow<List<StatsCreatedRow>>(emptyList())

    private lateinit var vm: StatsDashboardViewModel
    private val testScheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        sessionsFlow.tryEmit(emptyList())
        every { statsRepository.observeStatsSessions() } returns sessionsFlow
        every { statsRepository.observeStatsProgress() } returns progressFlow
        every { statsRepository.observeStatsBooks() } returns booksFlow
        every { statsRepository.observeStatsInspirations() } returns inspirationsFlow
        every { statsRepository.observeStatsNotes() } returns notesFlow
        // 注意：这里不能用 UnconfinedTestDispatcher。
        // 两个缓存回归测试的语义前提是 flowOn 的 dispatcher 必须是独立异步线程池：
        // tables.distinctUntilChanged 与 computeStats 的缓存比较依赖跨上下文切换的发射顺序，
        // UnconfinedTestDispatcher 会把协程 inline 在调用者线程，合入/更新顺序被打乱，
        // 导致缓存失效判据与真实运行时不一致。
        every { goalStore.prefs } returns kotlinx.coroutines.flow.flowOf(
            com.creationreadingassistant.data.settings.ReadingGoalPrefs(),
        )
        vm = StatsDashboardViewModel(
            statsRepository,
            goalStore,
            defaultDispatcher = Dispatchers.Default,
        )
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun isoAt(date: LocalDate): String =
        date.atStartOfDay(ZoneId.systemDefault()).plusHours(12).toInstant().toString()

    private fun session(bookId: String, date: LocalDate, durationMs: Long, progress: Float? = null) =
        StatsSessionRow(
            book_id = bookId,
            occurred_at = isoAt(date),
            duration_ms = durationMs,
            progress_percent = progress,
        )

    /** 等待满足条件的 uiState（真实 Default 线程发射，超时兜底）。 */
    private suspend fun awaitState(
        predicate: (StatsDashboardUiState) -> Boolean,
    ): StatsDashboardUiState = withContext(Dispatchers.Default.limitedParallelism(1)) {
        withTimeout(5_000) { vm.uiState.first(predicate) }
    }

    @Test
    fun `empty data reuses EMPTY_STATS and builds no trend structures`() = runTest(mainDispatcher) {
        val state = awaitState { it.stats != null }
        // 空数据早退：必须是共享单例（不构建趋势桶 / 进度映射等大结构）
        assertSame(EMPTY_STATS, state.stats)
        assertTrue(state.booksEmpty)
        assertTrue(state.stats!!.trend.isEmpty())
    }

    @Test
    fun `period switch recomputes and switching back reuses cached chart model`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        sessionsFlow.tryEmit(listOf(
            session("b1", today, 30 * 60_000L),
            session("b1", today.minusDays(1), 15 * 60_000L),
        ))
        booksFlow.value = listOf(StatsBookRow(id = "b1", size = 30_000, content_status = "available"))

        val week1 = awaitState { it.period == StatsPeriod.WEEK && it.stats != null && it.stats.sessionCount > 0 }

        vm.selectPeriod(StatsPeriod.TOTAL)
        val total = awaitState { it.period == StatsPeriod.TOTAL && it.stats != null }
        assertEquals(45 * 60_000L, total.stats!!.totalReadingMs)

        vm.selectPeriod(StatsPeriod.WEEK)
        val week2 = awaitState { it.period == StatsPeriod.WEEK && it.stats != null }
        // tables 未变化时来回切换周期：图表模型必须是同一实例（缓存复用，不重复 map/group/sort）
        assertSame(week1.stats, week2.stats)
    }

    @Test
    fun `equal db emission does not invalidate chart model cache`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        val rows = listOf(session("b1", today, 10 * 60_000L))
        sessionsFlow.tryEmit(rows)
        booksFlow.value = listOf(StatsBookRow(id = "b1", size = 3_000, content_status = "available"))

        val first = awaitState { it.stats != null && it.stats.sessionCount == 1 }

        // 模拟 Room 无关失效重查：发射等价但不同实例的列表
        sessionsFlow.tryEmit(rows.map { it.copy() })

        // 切走再切回：若 distinctUntilChanged 生效，缓存未被清空，实例复用
        vm.selectPeriod(StatsPeriod.TOTAL)
        awaitState { it.period == StatsPeriod.TOTAL && it.stats != null }
        vm.selectPeriod(StatsPeriod.WEEK)
        val again = awaitState { it.period == StatsPeriod.WEEK && it.stats != null }
        assertSame(first.stats, again.stats)
    }

    @Test
    fun `large session count aggregates correctly`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        val many = (0 until 5_000).map { i ->
            session("b${i % 40}", today.minusDays((i % 7).toLong()), 60_000L)
        }
        sessionsFlow.tryEmit(many)
        booksFlow.value = (0 until 40).map { StatsBookRow(id = "b$it", size = 9_000, content_status = "available") }

        vm.selectPeriod(StatsPeriod.TOTAL)
        val state = awaitState { it.period == StatsPeriod.TOTAL && it.stats != null && it.stats.sessionCount == 5_000 }
        assertEquals(5_000L * 60_000L, state.stats!!.totalReadingMs)
        assertEquals(7, state.stats.readingDays)
    }

    @Test
    fun `rapid period toggling settles on latest selection`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        sessionsFlow.tryEmit(listOf(session("b1", today, 20 * 60_000L)))
        booksFlow.value = listOf(StatsBookRow(id = "b1", size = 6_000, content_status = "available"))

        // 快速连续切换：最终状态必须与最后一次选择一致，不得停留在旧周期
        vm.selectPeriod(StatsPeriod.MONTH)
        vm.selectPeriod(StatsPeriod.YEAR)
        vm.selectPeriod(StatsPeriod.TOTAL)
        vm.selectPeriod(StatsPeriod.MONTH)

        val state = awaitState { it.period == StatsPeriod.MONTH && it.stats != null }
        assertEquals(StatsPeriod.MONTH, state.period)
        assertEquals(20 * 60_000L, state.stats!!.totalReadingMs)
    }

    @Test
    fun `completed and progress stats follow projection rows`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        sessionsFlow.tryEmit(listOf(session("b1", today, 10 * 60_000L, progress = 50f)))
        progressFlow.value = listOf(
            StatsProgressRow(book_id = "b1", progress_percent = 50f, completion_state = "reading"),
            StatsProgressRow(book_id = "b2", progress_percent = 100f, completion_state = "completed"),
        )
        booksFlow.value = listOf(
            StatsBookRow(id = "b1", size = 30_000, content_status = "available"),
            StatsBookRow(id = "b2", size = 12_000, content_status = "available"),
        )

        vm.selectPeriod(StatsPeriod.TOTAL)
        val state = awaitState { it.period == StatsPeriod.TOTAL && it.stats != null && it.stats.completed == 1 }
        assertEquals(1, state.stats!!.completed)
        assertEquals(2, state.stats.status.total)
    }
}
