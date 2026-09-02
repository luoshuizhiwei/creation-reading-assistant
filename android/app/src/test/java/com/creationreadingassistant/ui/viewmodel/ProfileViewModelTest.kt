package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.dao.StatsCreatedRow
import com.creationreadingassistant.data.local.dao.StatsProgressRow
import com.creationreadingassistant.data.local.dao.StatsSessionRow
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.StatsRepository
import com.creationreadingassistant.data.repository.SyncRepository
import com.creationreadingassistant.feature.sync.JsonBridge
import com.creationreadingassistant.feature.sync.LocalZipBackup
import com.creationreadingassistant.feature.sync.PairingManager
import com.creationreadingassistant.feature.sync.WebDavBackup
import com.creationreadingassistant.feature.sync.WebDavConfigStore
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ProfileViewModel 单测：
 * - 首页仅订阅 homeSummary（窄投影），不得触发子页专用实体流
 *   （书籍 / 进度 / 会话 / 笔记完整实体、缓存统计）的收集。
 * - homeSummary 聚合口径正确（时长求和 / finished 或 >=99.5 完读 / 灵感条数）。
 * - libraryState 在被订阅（子页打开）后才收集实体流。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val bookRepository = mockk<BookRepository>()
    private val statsRepository = mockk<StatsRepository>()

    // 子页专用实体流是否被收集的标记
    private val bookEntitiesCollected = AtomicBoolean(false)
    private val progressEntitiesCollected = AtomicBoolean(false)
    private val sessionEntitiesCollected = AtomicBoolean(false)
    private val noteEntitiesCollected = AtomicBoolean(false)
    private val cacheCollected = AtomicBoolean(false)

    private val sessionRows = MutableStateFlow<List<StatsSessionRow>>(emptyList())
    private val progressRows = MutableStateFlow<List<StatsProgressRow>>(emptyList())
    private val inspirationRows = MutableStateFlow<List<StatsCreatedRow>>(emptyList())

    private lateinit var vm: ProfileViewModel
    private val testScheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)

        // 实体流：收集即置位标记
        every { bookRepository.observeBooks() } returns
            flow<List<BookEntity>> { emit(emptyList()) }.onStart { bookEntitiesCollected.set(true) }
        every { bookRepository.observeProgress() } returns
            flow<List<ReadingProgressEntity>> { emit(emptyList()) }.onStart { progressEntitiesCollected.set(true) }
        every { bookRepository.observeSessions() } returns
            flow<List<ReadingSessionEntity>> { emit(emptyList()) }.onStart { sessionEntitiesCollected.set(true) }
        every { bookRepository.observeNotes() } returns
            flow<List<NoteEntity>> { emit(emptyList()) }.onStart { noteEntitiesCollected.set(true) }
        every { bookRepository.observeCachedCount() } returns
            flow { emit(0) }.onStart { cacheCollected.set(true) }
        every { bookRepository.observeCachedBytes() } returns
            flow { emit(0L) }.onStart { cacheCollected.set(true) }

        // 首页摘要用的窄投影流
        every { statsRepository.observeStatsSessions() } returns sessionRows
        every { statsRepository.observeStatsProgress() } returns progressRows
        every { statsRepository.observeStatsInspirations() } returns inspirationRows
        val goalStore = mockk<com.creationreadingassistant.data.settings.GoalStore>()
        every { goalStore.prefs } returns kotlinx.coroutines.flow.flowOf(
            com.creationreadingassistant.data.settings.ReadingGoalPrefs(),
        )
        val goalScheduler = mockk<com.creationreadingassistant.feature.goal.ReadingGoalScheduler>(relaxed = true)

        val configStore = mockk<SyncConfigStore>(relaxed = true)
        every { configStore.config } returns null
        val webDavConfigStore = mockk<WebDavConfigStore>(relaxed = true)
        every { webDavConfigStore.config } returns null

        vm = ProfileViewModel(
            jsonBridge = mockk<JsonBridge>(relaxed = true),
            localZipBackup = mockk<LocalZipBackup>(relaxed = true),
            pairingManager = mockk<PairingManager>(relaxed = true),
            syncRepository = mockk<SyncRepository>(relaxed = true),
            configStore = configStore,
            webDavConfigStore = webDavConfigStore,
            webDavBackup = mockk<WebDavBackup>(relaxed = true),
            aiClient = mockk<AiClient>(relaxed = true),
            bookRepository = bookRepository,
            statsRepository = statsRepository,
            goalStore = goalStore,
            goalScheduler = goalScheduler,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private suspend fun awaitSummary(
        predicate: (ProfileHomeSummary) -> Boolean,
    ): ProfileHomeSummary = withContext(Dispatchers.Default.limitedParallelism(1)) {
        withTimeout(5_000) { vm.homeSummary.first(predicate) }
    }

    @Test
    fun `home summary subscription must not trigger sub-page entity loads`() = runTest(mainDispatcher) {
        sessionRows.value = listOf(
            StatsSessionRow(book_id = "b1", occurred_at = null, duration_ms = 60_000L, progress_percent = null),
            StatsSessionRow(book_id = "b2", occurred_at = null, duration_ms = 30_000L, progress_percent = null),
        )
        progressRows.value = listOf(
            StatsProgressRow(book_id = "b1", progress_percent = 100f, completion_state = "finished"),
            StatsProgressRow(book_id = "b2", progress_percent = 99.7f, completion_state = "reading"),
            StatsProgressRow(book_id = "b3", progress_percent = 10f, completion_state = "reading"),
        )
        inspirationRows.value = listOf(StatsCreatedRow("2026-01-01T00:00:00Z"))

        // 只订阅首页摘要（模拟停留在「我的」首页）
        val summary = awaitSummary { it.totalDurationMs > 0 }

        assertEquals(90_000L, summary.totalDurationMs)
        assertEquals(2, summary.completedBookCount) // finished + >=99.5
        assertEquals(1, summary.inspirationCount)

        // 首页不得触发子页专用加载
        assertFalse("首页不得物化书籍实体", bookEntitiesCollected.get())
        assertFalse("首页不得物化进度实体", progressEntitiesCollected.get())
        assertFalse("首页不得物化会话实体", sessionEntitiesCollected.get())
        assertFalse("首页不得物化笔记实体", noteEntitiesCollected.get())
        assertFalse("首页不得触发缓存统计查询", cacheCollected.get())
    }

    @Test
    fun `library state collects entity flows only after sub-page subscribes`() = runTest(mainDispatcher) {
        // 构造后（未订阅任何流）不得收集实体流
        assertFalse(bookEntitiesCollected.get())
        assertFalse(cacheCollected.get())

        // 模拟打开子页：保持对 libraryState 的订阅，直到实体流确实被收集
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val job = launch { vm.libraryState.collect { } }
            withTimeout(5_000) {
                while (!bookEntitiesCollected.get() || !cacheCollected.get()) {
                    delay(10)
                }
            }
            job.cancel()
        }

        assertTrue("子页打开后应加载书籍实体", bookEntitiesCollected.get())
        assertTrue("子页打开后应加载缓存统计", cacheCollected.get())
    }

    @Test
    fun `empty projections produce zero summary`() = runTest(mainDispatcher) {
        val summary = awaitSummary { true }
        assertEquals(0L, summary.totalDurationMs)
        assertEquals(0, summary.completedBookCount)
        assertEquals(0, summary.inspirationCount)
    }
}
