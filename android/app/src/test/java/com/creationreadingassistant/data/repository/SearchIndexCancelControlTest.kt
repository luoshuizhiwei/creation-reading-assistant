package com.creationreadingassistant.data.repository

import android.content.Context
import android.os.StrictMode
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.creationreadingassistant.ui.screen.profile.ProfileAction
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.data.repository.StatsRepository
import com.creationreadingassistant.data.repository.SyncRepository
import com.creationreadingassistant.data.settings.GoalStore
import com.creationreadingassistant.data.settings.ReadingGoalPrefs
import com.creationreadingassistant.data.settings.SyncPrefs
import com.creationreadingassistant.data.settings.SyncPrefsStore
import com.creationreadingassistant.feature.goal.ReadingGoalScheduler
import com.creationreadingassistant.feature.sync.JsonBridge
import com.creationreadingassistant.feature.sync.LegadoReadingProgressImporter
import com.creationreadingassistant.feature.sync.LocalZipBackup
import com.creationreadingassistant.feature.sync.PairingManager
import com.creationreadingassistant.feature.sync.WebDavBackup
import com.creationreadingassistant.feature.sync.WebDavConfigStore
import com.creationreadingassistant.ui.viewmodel.ProfileViewModel
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.SearchIndexCoverageDao
import com.creationreadingassistant.data.local.dao.SearchIndexCoverageRow
import com.creationreadingassistant.data.local.dao.SearchIndexStateDao
import com.creationreadingassistant.data.local.dao.SearchIndexStateRow
import com.creationreadingassistant.data.local.dao.SearchTermDao
import com.creationreadingassistant.data.local.dao.SearchTermRow
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.feature.search.SearchTokenizer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.io.File

/**
 * CRA-SEARCH-INDEX-CANCEL-CONTROL-v1 —— 用户取消后台索引构建的定向 JVM 测试。
 *
 * ## 这一层验证的是**真实生产实现**
 *
 * 全部断言打在真实 `SearchIndexRepository.cancelIndexing()` /
 * `SearchIndexScheduler.cancelPendingSweep()` / `ProfileViewModel.cancelSearchIndex()`
 * 上，通过真实 DAO 替身（记录实际调用与落盘状态）与真实 `WorkManager` 替身观察行为，
 * 不自行计算期望值，也不检查源码文本。
 *
 * ## 覆盖的 S1 合同点
 *
 * 1. 取消**确实发出**停止请求（WorkManager 一次性扫描任务，不动周期任务）；
 * 2. 取消后 `progress.isRunning` 如实变 false，**不写任何完成标记**（不把取消说成完成）；
 * 3. 已提交的索引行与扫描断点（`last_scanned_book_id` / `last_scanned_chapter_index`）**保留**；
 * 4. 取消后的下一次扫描**从断点续建**，不是清空后重建；
 * 5. 取消**不改变**显式全量重建的既有语义（仍重置游标 + `triggerNow(force=true)`）；
 * 6. 取消与重试/新任务的竞态：被取消任务的迟到写入一律被拒，不覆盖新任务状态；
 * 7. `ProfileViewModel` 的用户反馈文案如实描述「已请求停止」，不谎报完成。
 *
 * 并发用例用 `CompletableDeferred` 双向屏障握手排序（仓库既有 `testHookAfterStep`
 * 接缝），不使用 sleep、不依赖调度运气。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchIndexCancelControlTest {

    /**
     * `ProfileViewModel.cancelSearchIndex` 走 `viewModelScope`，其默认调度器是
     * `Dispatchers.Main.immediate`；JVM 单测必须先装上测试 Main，否则协程根本不会启动。
     */
    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        installStrictModeStubOnce()
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // StrictMode 存根（缺陷 D）
    //
    // v2 为了绕过 JVM 存根，在**生产** `allowDiskReads` 里包了 `catch (Throwable)`，
    // 把策略恢复失败也一并吞掉。v3 把生产恢复成真实的「get → 放开 → 执行 → 严格恢复」，
    // 改由**测试侧**用 mockk 顶替 android.os.StrictMode：
    //   - `getThreadPolicy()` / `setThreadPolicy()` 是静态方法 → mockkStatic；
    //   - `ThreadPolicy.Builder` 是 new 出来的，存根方法默认返回 null（链式调用会 NPE）
    //     → mockkConstructor 顶替构造出来的实例。
    // 这样生产不再为「框架在 JVM 上不可用」让路，也不吞任何 Throwable。
    // =========================================================================

    private var strictModeStubInstalled = false

    private fun installStrictModeStubOnce() {
        if (strictModeStubInstalled) return
        val policy = mockk<StrictMode.ThreadPolicy>(relaxed = true)
        val afterReads = mockk<StrictMode.ThreadPolicy.Builder>(relaxed = true)
        val afterWrites = mockk<StrictMode.ThreadPolicy.Builder>(relaxed = true)

        mockkConstructor(StrictMode.ThreadPolicy.Builder::class)
        every { anyConstructed<StrictMode.ThreadPolicy.Builder>().permitDiskReads() } returns afterReads
        every { afterReads.permitDiskWrites() } returns afterWrites
        every { afterWrites.build() } returns policy

        mockkStatic(StrictMode::class)
        every { StrictMode.getThreadPolicy() } returns policy
        every { StrictMode.setThreadPolicy(any()) } just Runs

        strictModeStubInstalled = true
    }

    // =========================================================================
    // 真实 DAO 替身（记录实际调用，不参与期望值计算）
    // =========================================================================

    private class FakeStateDao : SearchIndexStateDao {
        var state: SearchIndexStateRow? = null
        val setCallCount = AtomicInteger(0)
        val writtenStates = mutableListOf<SearchIndexStateRow>()

        /**
         * 写前拦截点（测试侧屏障）：在**真正落盘之前**挂起。
         *
         * 调用方此时已经持有调用链上的锁（游标写入在 `runIfCurrentGeneration` 的
         * `sweepLock` 内、重置写入在 `invalidateSweepForFullRebuild` 的 `sweepLock` 内），
         * 因此它是精确的「已在临界区内、尚未提交」屏障 —— 不需要 sleep。
         */
        var onBeforeSet: (suspend (SearchIndexStateRow) -> Unit)? = null

        override suspend fun set(row: SearchIndexStateRow) {
            onBeforeSet?.invoke(row)
            setCallCount.incrementAndGet()
            writtenStates.add(row)
            state = row
        }

        override suspend fun get(): SearchIndexStateRow? = state
    }

    private class FakeTermDao : SearchTermDao {
        var bookActive: (String) -> Boolean = { true }
        override suspend fun upsertAllIfBookActive(rows: List<SearchTermRow>): Boolean {
            val id = rows.firstOrNull()?.book_id ?: return false
            if (!bookActive(id)) return false
            upsertAll(rows)
            return true
        }
        override suspend fun countActiveBook(bookId: String): Int = if (bookActive(bookId)) 1 else 0

        val rows = mutableListOf<SearchTermRow>()
        val deleteByBookCalls = mutableListOf<String>()

        override suspend fun upsertAll(rows: List<SearchTermRow>) {
            this.rows += rows
        }

        override suspend fun deleteByBook(bookId: String) {
            deleteByBookCalls.add(bookId)
            rows.removeIf { it.book_id == bookId }
        }

        override suspend fun deleteByBookAndBasis(bookId: String, textBasis: String) {
            rows.removeIf { it.book_id == bookId && it.text_basis == textBasis }
        }

        override suspend fun countIndexedBooks(): Long =
            rows.map { it.book_id }.distinct().size.toLong()

        override suspend fun rowsByTermForBasis(term: String, textBasis: String): List<SearchTermRow> =
            rows.filter { it.term == term && it.text_basis == textBasis }

        override suspend fun distinctBookIds(): List<String> = rows.map { it.book_id }.distinct()
    }

    private class FakeCoverageDao : SearchIndexCoverageDao {
        var bookActive: (String) -> Boolean = { true }
        override suspend fun upsertAllIfBookActive(rows: List<SearchIndexCoverageRow>): Boolean {
            val id = rows.firstOrNull()?.book_id ?: return false
            if (!bookActive(id)) return false
            upsertAll(rows)
            return true
        }
        override suspend fun countActiveBook(bookId: String): Int = if (bookActive(bookId)) 1 else 0

        val rows = mutableListOf<SearchIndexCoverageRow>()

        override suspend fun upsertAll(rows: List<SearchIndexCoverageRow>) {
            for (r in rows) {
                val idx = this.rows.indexOfFirst { it.book_id == r.book_id && it.text_basis == r.text_basis }
                if (idx >= 0) this.rows[idx] = r else this.rows.add(r)
            }
        }

        override suspend fun upsert(row: SearchIndexCoverageRow) = upsertAll(listOf(row))

        override suspend fun rowsForBook(bookId: String): List<SearchIndexCoverageRow> =
            rows.filter { it.book_id == bookId }

        override suspend fun row(bookId: String, textBasis: String): SearchIndexCoverageRow? =
            rows.firstOrNull { it.book_id == bookId && it.text_basis == textBasis }

        override suspend fun all(): List<SearchIndexCoverageRow> = rows.toList()

        override suspend fun rowsByStates(states: List<String>): List<SearchIndexCoverageRow> =
            rows.filter { it.coverage in states }

        override suspend fun countByStates(states: List<String>): Long = rowsByStates(states).size.toLong()

        override suspend fun distinctBookIds(): List<String> = rows.map { it.book_id }.distinct()

        override suspend fun deleteByBook(bookId: String) {
            rows.removeIf { it.book_id == bookId }
        }

        override suspend fun deleteAll() {
            rows.clear()
        }
    }

    /**
     * 只拦截 `countActive()` / `activeIds()` 的 BookDao 装饰器（其余方法原样委托给 mockk 替身）。
     *
     * 用途：在 `triggerFullRebuildNow` 的两个**挂起点**上放精确屏障 —— 生产里
     * `countActive()`（epoch 校验之后、真正入队之前）与 `activeIds()`（公开 trigger 的
     * `purgeOrphanIndexRows` 前置清理内）都是真实的 Room 挂起查询，测试里必须有等价的挂起点，
     * 否则取消插不进这些窗口。不用 sleep、不改生产逻辑。
     */
    private class GatedCountActiveBookDao(
        private val delegate: BookDao,
    ) : BookDao by delegate {
        var onCountActive: (suspend () -> Unit)? = null

        /** 公开 trigger 的 purge 窗口屏障：`purgeOrphanIndexRows` 在这里挂起。 */
        var onActiveIds: (suspend () -> Unit)? = null

        override suspend fun countActive(): Int {
            onCountActive?.invoke()
            return delegate.countActive()
        }

        override suspend fun activeIds(): List<String> {
            onActiveIds?.invoke()
            return delegate.activeIds()
        }
    }

    private class Harness(
        val repo: SearchIndexRepository,
        val stateDao: FakeStateDao,
        val termDao: FakeTermDao,
        val coverageDao: FakeCoverageDao,
        val scheduler: SearchIndexScheduler?,
        val books: List<BookEntity>,
        val bookDao: GatedCountActiveBookDao,
    )

    private fun anchorRow(bookId: String, chapter: Int = 0) = SearchIndexStateRow(
        tokenizer_version = SearchIndexRepository.TOKENIZER_VERSION,
        last_scanned_book_id = bookId,
        last_scanned_chapter_index = chapter,
        built_at = 1L,
    )

    private fun createHarness(
        books: List<BookEntity> = listOf(
            BookEntity(id = "b1", title = "书本一", format = "txt", updated_at = "2026-01-01T00:00:00Z"),
            BookEntity(id = "b2", title = "书本二", format = "txt", updated_at = "2026-01-01T00:00:00Z"),
        ),
        scheduler: SearchIndexScheduler? = null,
        initialState: SearchIndexStateRow? = null,
        onCountActive: (suspend () -> Unit)? = null,
    ): Harness {
        val stateDao = FakeStateDao().apply { state = initialState }
        val termDao = FakeTermDao()
        val coverageDao = FakeCoverageDao()

        val bookDao = mockk<BookDao>(relaxed = true)
        coEvery { bookDao.countActive() } returns books.size
        coEvery { bookDao.observeAllActive() } returns flowOf(books)
        coEvery { bookDao.activeIds() } returns books.map { it.id }
        books.forEach { b -> coEvery { bookDao.getById(b.id) } returns b }

        val bookContentDao = mockk<BookContentDao>(relaxed = true)
        books.forEach { b ->
            coEvery { bookContentDao.getByBook(b.id) } returns BookContentEntity(
                book_id = b.id,
                reader_preview = "正文预览内容：${b.title}",
                epub_json = null,
            )
        }

        val rulesRepo = mockk<RulesRepository>(relaxed = true)
        coEvery { rulesRepo.observe(any()) } returns flowOf(mockk(relaxed = true))

        val effectiveBookDao = GatedCountActiveBookDao(bookDao).apply {
            this.onCountActive = onCountActive
        }

        val repo = SearchIndexRepository(
            context = mockk<Context>(relaxed = true),
            bookDao = effectiveBookDao,
            bookContentDao = bookContentDao,
            termDao = termDao,
            stateDao = stateDao,
            coverageDao = coverageDao,
            tokenizer = SearchTokenizer(),
            rulesRepository = rulesRepo,
            scheduler = scheduler,
        )
        return Harness(repo, stateDao, termDao, coverageDao, scheduler, books, effectiveBookDao)
    }

    // =========================================================================
    // 1. 取消确实发出停止请求（真实 WorkManager 调用，只作用于一次性扫描任务）
    // =========================================================================

    @Test
    fun `cancel pending sweep issues work manager cancel for once work only`() {
        val wm = mockk<WorkManager>(relaxed = true)
        val scheduler = SearchIndexScheduler(mockk(relaxed = true))
        scheduler.workManagerProvider = { wm }

        scheduler.cancelPendingSweep()

        verify(exactly = 1) { wm.cancelUniqueWork(SearchIndexScheduler.UNIQUE_ONCE_WORK) }
        verify(exactly = 0) { wm.cancelUniqueWork(SearchIndexScheduler.UNIQUE_PERIODIC_WORK) }
    }

    @Test
    fun `cancel indexing asks the scheduler to cancel the pending sweep`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler)

        val result = h.repo.cancelIndexing()

        assertFalse("空闲时取消必须如实返回「没有在跑」", result.wasRunning)
        assertFalse("空闲且无排队时不得谎报撤下了排队", result.wasQueued)
        verify(exactly = 1) { scheduler.cancelPendingSweep() }
    }

    // =========================================================================
    // 2/3. 取消中止在跑任务：迟到游标写入被拒、断点与已提交行保留、isRunning 如实
    // =========================================================================

    @Test
    fun `cancel while sweeping rejects the late cursor write and keeps the anchor`() = runTest {
        // 断点停在 b1 ⇒ 本次扫描从锚点之后（b2）继续；被取消的「在跑书」就是 b2。
        val anchor = anchorRow("b1")
        val h = createHarness(initialState = anchor)

        val reachedStep = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        h.repo.testHookAfterStep = { step ->
            if (step == "after_index_one_book_b2") {
                reachedStep.complete(Unit)
                release.await()
            }
        }

        val sweep = async { h.repo.ensureIndexedIncremental() }
        reachedStep.await()

        // 取消点现场：此时在跑书 b2 的索引行已落盘（其后才轮到游标写入）
        val termsAtCancelPoint = h.termDao.rows.size
        val coverageAtCancelPoint = h.coverageDao.rows.size
        assertTrue("取消前必须已有已提交索引行，否则本断言无意义", termsAtCancelPoint > 0)

        val cancelResult = h.repo.cancelIndexing()
        assertTrue("在跑时取消必须如实返回「确有构建在跑」", cancelResult.wasRunning)
        assertFalse("取消后 isRunning 必须如实为 false", h.repo.progress.value.isRunning)
        assertFalse("取消后不得留下排队标记", h.repo.progress.value.isQueued)

        release.complete(Unit)
        sweep.await()

        assertEquals(
            "取消后旧任务的迟到游标写入必须被拒（断点不被旧任务改写）",
            0,
            h.stateDao.setCallCount.get(),
        )
        assertEquals("断点必须原样保留", anchor, h.stateDao.state)
        assertEquals(
            "取消不得删除取消点之前已提交的索引行",
            termsAtCancelPoint,
            h.termDao.rows.size,
        )
        assertEquals(
            "取消不得删除取消点之前已提交的覆盖度行",
            coverageAtCancelPoint,
            h.coverageDao.rows.size,
        )
    }

    @Test
    fun `cancel does not write any completion marker`() = runTest {
        val h = createHarness(initialState = anchorRow("b1"))

        val reachedStep = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        h.repo.testHookAfterStep = { step ->
            if (step == "after_index_one_book_b2") {
                reachedStep.complete(Unit)
                release.await()
            }
        }
        val sweep = async { h.repo.ensureIndexedIncremental() }
        reachedStep.await()
        h.repo.cancelIndexing()
        release.complete(Unit)
        sweep.await()

        assertTrue(
            "取消不得写入任何状态行（既不写完成、也不清空断点）：" + h.stateDao.writtenStates,
            h.stateDao.writtenStates.isEmpty(),
        )
    }

    // =========================================================================
    // 4. 取消后下一次扫描从断点续建，而不是清空后重建
    // =========================================================================

    @Test
    fun `sweep after cancel resumes from the kept anchor instead of restarting`() = runTest {
        val h = createHarness(initialState = anchorRow("b1"))

        val visited = mutableListOf<String>()
        h.repo.testHookAfterStep = { step -> visited.add(step) }

        assertFalse(h.repo.cancelIndexing().wasRunning)

        h.repo.ensureIndexedIncremental()

        assertFalse("续建不得回访锚点书 b1（那不是清空重建）", visited.any { it.endsWith("_b1") })
        assertTrue("续建必须从锚点之后继续到 b2", visited.any { it.endsWith("_b2") })
    }

    // =========================================================================
    // 5. 取消不改变显式全量重建的既有语义
    // =========================================================================

    @Test
    fun `explicit full rebuild after cancel still resets the cursor`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler, initialState = anchorRow("b1"))

        h.repo.cancelIndexing()
        h.repo.triggerFullRebuildNow(scheduler)

        val state = h.stateDao.state
        assertNotNull(state)
        assertEquals("显式全量重建仍必须把游标重置到起点", null, state!!.last_scanned_book_id)
        assertEquals(0, state.last_scanned_chapter_index)
        verify(exactly = 1) { scheduler.triggerNow(force = true) }
    }

    // =========================================================================
    // 6. 取消 / 新任务竞态：旧任务的迟到写入不得覆盖新任务状态
    // =========================================================================

    @Test
    fun `cancelled task late writes never overwrite the newer sweep state`() = runTest {
        val h = createHarness()

        val firstReached = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstSweepDone = CompletableDeferred<Unit>()

        var firstSweep = true
        h.repo.testHookAfterStep = { step ->
            if (firstSweep && step == "after_index_one_book_b1") {
                firstReached.complete(Unit)
                releaseFirst.await()
            }
        }

        val oldSweep = async {
            val r = h.repo.ensureIndexedIncremental()
            firstSweepDone.complete(Unit)
            r
        }
        firstReached.await()

        // 用户取消：旧任务立即失效
        h.repo.cancelIndexing()

        // 取消后立刻发起的新任务：必须能取得执行权并完整跑完
        firstSweep = false
        val newResult = h.repo.ensureIndexedIncremental()
        assertEquals(IndexSweepResult.COMPLETED, newResult)

        val writesAfterNewSweep = h.stateDao.writtenStates.toList()
        val anchorAfterNewSweep = h.stateDao.state

        // 放行旧任务：它的迟到写入必须被代次校验拒绝
        releaseFirst.complete(Unit)
        oldSweep.await()
        firstSweepDone.await()

        assertEquals(
            "被取消任务的迟到写入不得再追加任何状态行",
            writesAfterNewSweep,
            h.stateDao.writtenStates.toList(),
        )
        assertEquals("新任务写入的断点不得被旧任务覆盖", anchorAfterNewSweep, h.stateDao.state)
        assertNotNull("新任务必须留下断点", h.stateDao.state?.last_scanned_book_id)
    }

    // =========================================================================
    // 7. ProfileViewModel 的用户反馈如实（不把取消说成完成）
    // =========================================================================

    @Test
    fun `profile cancel reports a stop request and never claims completion`() = runTest {
        val repo = mockk<SearchIndexRepository>(relaxed = true)
        coEvery { repo.cancelIndexing() } returns CancelIndexingResult(
            wasRunning = true,
            wasQueued = false,
            anchorPreserved = true,
        )
        val vm = createProfileViewModel(repo)

        val messages = mutableListOf<String>()
        vm.cancelSearchIndex { messages += it }

        coVerify(exactly = 1) { repo.cancelIndexing() }
        assertEquals(1, messages.size)
        val msg = messages.single()
        assertTrue("必须如实说明是「请求停止」：$msg", msg.contains("停止"))
        assertTrue("必须说明已完成的章节与断点保留：$msg", msg.contains("保留"))
        assertTrue("必须说明下次从此处继续：$msg", msg.contains("继续"))
        for (claim in listOf("索引完成", "重建完成", "已完成索引", "构建完成")) {
            assertFalse("取消不得被说成完成（$claim）：$msg", msg.contains(claim))
        }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `profile cancel reports honestly when nothing is running`() = runTest {
        val repo = mockk<SearchIndexRepository>(relaxed = true)
        coEvery { repo.cancelIndexing() } returns CancelIndexingResult(
            wasRunning = false,
            wasQueued = false,
            anchorPreserved = true,
        )
        val vm = createProfileViewModel(repo)

        val messages = mutableListOf<String>()
        vm.cancelSearchIndex { messages += it }

        val msg = messages.single()
        assertTrue("无构建在跑时必须如实说明：$msg", msg.contains("没有正在进行的后台索引构建"))
        assertFalse("不得谎报已停止：$msg", msg.contains("已请求停止"))
        vm.viewModelScope.cancel()
    }

    // =========================================================================
    // v2 缺陷复现 1：词项未全部落盘但覆盖度已记 FULL
    //
    // 顺序缺陷：indexOneBook 先写 coverage（FULL），再写 tail terms。若取消发生在
    // 两者之间，coverage=FULL 已落盘但 terms 缺失 —— 虚假 FULL。
    // 正确行为：terms 未全部落盘时 coverage 不得记 FULL。
    // =========================================================================

    @Test
    fun `issue1 - coverage must not be FULL when tail terms are not committed`() = runTest {
        val contentFile = File.createTempFile("issue1_test", ".txt")
        try {
            contentFile.writeText("第一章 测试章节\n这是测试正文内容，用于验证索引覆盖度的诚实性。")
            val h = createHarness(books = listOf(
                BookEntity(
                    id = "b1",
                    title = "书本一",
                    format = "txt",
                    size = contentFile.length().toInt(),
                    local_content_path = contentFile.absolutePath,
                    updated_at = "2026-01-01T00:00:00Z",
                ),
            ))

            val reachedTermTail = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            h.repo.testHookBeforeWrite = { point, bookId ->
                if (point == "term_tail" && bookId == "b1") {
                    reachedTermTail.complete(Unit)
                    release.await()
                }
            }

            val sweep = async { h.repo.ensureIndexedIncremental() }
            reachedTermTail.await()

            // 取消点：coverage 已写但 tail terms 尚未落盘
            h.repo.cancelIndexing()
            release.complete(Unit)
            sweep.await()

            val originalCoverage = h.coverageDao.rows.firstOrNull { it.text_basis == "original" }
            assertTrue(
                "terms 未全部落盘时 coverage 不得记 FULL —— issue1 红色证据: $originalCoverage",
                originalCoverage == null || originalCoverage.coverage != "full",
            )
        } finally {
            contentFile.delete()
        }
    }

    // =========================================================================
    // v2 缺陷复现 2：用户取消被当作预算推进 → Worker 自动重试
    //
    // b1 完整索引后取消发生在 b2 的 tail terms 写入前。旧代码返回 PROGRESSED
    // （因为 reached=1 > from=0）→ Worker 调 Result.retry() → 立即重试。
    // 正确行为：用户取消应返回 COMPLETED（优雅停止，不重试不报失败）。
    // =========================================================================

    @Test
    fun `issue2 - user cancel must not cause worker auto-retry`() = runTest {
        val h = createHarness(books = listOf(
            BookEntity(id = "b1", title = "书本一", format = "txt", updated_at = "2026-01-01T00:00:00Z"),
            BookEntity(id = "b2", title = "书本二", format = "txt", updated_at = "2026-01-01T00:00:00Z"),
        ))

        val reachedTermTailB2 = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        h.repo.testHookBeforeWrite = { point, bookId ->
            if (point == "term_tail" && bookId == "b2") {
                reachedTermTailB2.complete(Unit)
                release.await()
            }
        }

        val sweep = async { h.repo.ensureIndexedIncremental() }
        reachedTermTailB2.await()

        // b1 已完整索引，b2 的 coverage 已写但 tail terms 未落盘 → 用户取消
        h.repo.cancelIndexing()
        release.complete(Unit)
        val result = sweep.await()

        assertEquals(
            "用户取消后扫描应返回 COMPLETED（优雅停止，不重试不报失败）—— issue2 红色证据",
            IndexSweepResult.COMPLETED,
            result,
        )
    }

    // =========================================================================
    // v2 缺陷复现 3：取消与全量重建回滚交错 → 回滚复活被取消的旧扫描
    //
    // 时序：sweep(generation=G) 运行 → beginRebuild(G→G+1, isRebuilding=true)
    // → cancel(G+1→G+2) → rebuild 失败 → rollback 恢复 generation=G
    // → 旧扫描的迟到游标写入通过代次校验（G==G）→ 被取消的旧任务复活。
    // 正确行为：rollback 不得恢复旧 generation，被取消任务的迟到写入必须被拒。
    // =========================================================================

    @Test
    fun `issue3 - rebuild rollback must not revive cancelled sweep generation`() = runTest {
        val h = createHarness(books = listOf(
            BookEntity(id = "b1", title = "书本一", format = "txt", updated_at = "2026-01-01T00:00:00Z"),
            BookEntity(id = "b2", title = "书本二", format = "txt", updated_at = "2026-01-01T00:00:00Z"),
        ))

        val sweepReached = CompletableDeferred<Unit>()
        val releaseSweep = CompletableDeferred<Unit>()

        h.repo.testHookAfterStep = { step ->
            if (step == "after_index_one_book_b1") {
                sweepReached.complete(Unit)
                releaseSweep.await()
            }
        }
        h.repo.testHookBeforeRebuildReset = {
            // 重建已接管（isRebuilding=true）时取消旧扫描，然后让重建失败 → 触发 rollback
            h.repo.cancelIndexing()
            throw RuntimeException("simulated rebuild failure")
        }

        val sweep = async { h.repo.ensureIndexedIncremental() }
        sweepReached.await()

        // 触发全量重建：beginRebuild → hook(cancel + throw) → rollback
        runCatching { h.repo.triggerFullRebuildNow() }

        // 放行旧扫描：它的迟到游标写入应被拒
        releaseSweep.complete(Unit)
        sweep.await()

        assertEquals(
            "回滚不得复活被取消的旧扫描代次 → 旧扫描的迟到游标写入必须被拒 —— issue3 红色证据",
            0,
            h.stateDao.setCallCount.get(),
        )
    }

    // =========================================================================
    // v2 缺陷复现 4：UI 无取消动作入口
    //
    // ProfileAction 没有 CancelSearchIndex 动作 → StorageSubPage / DiagnosticsSubPage
    // 无法把用户取消意图分发到 ProfileViewModel.cancelSearchIndex()。
    // 正确行为：ProfileAction 必须有 CancelSearchIndex 动作。
    // =========================================================================

    @Test
    fun `issue4 - ProfileAction must have CancelSearchIndex entry`() {
        val cancelAction = try {
            Class.forName("com.creationreadingassistant.ui.screen.profile.ProfileAction\$CancelSearchIndex")
        } catch (e: ClassNotFoundException) {
            null
        }
        assertNotNull(
            "ProfileAction 必须有 CancelSearchIndex 动作 —— issue4 红色证据",
            cancelAction,
        )
    }

    // =========================================================================
    // v3 缺陷 A：共享取消标志竞态 —— 取消必须归属**具体执行**
    //
    // v2 的 `userCancelRequested` 是全局共享布尔：[cancelIndexing] 置 true 后，
    // 任何**新**占权的扫描都会把它清成 false。于是「旧 sweep 阻塞 → 取消 →
    // 新 sweep 占权并清标志 → 旧 sweep 恢复」这条交错里，旧 sweep 读到的已经
    // 是 false，退回 `indexSweepResult` 判定 → PROGRESSED（Worker retry）。
    //
    // 交错用 `CompletableDeferred` 双向屏障握手排序，不用 sleep。
    // =========================================================================

    @Test
    fun `issueA1 - cancelled sweep after acquire must not fall back to PROGRESSED`() = runTest {
        val h = createHarness()

        val oldReached = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        h.repo.testHookAfterAcquireOwnership = {
            oldReached.complete(Unit)
            releaseOld.await()
        }

        val oldSweep = async { h.repo.ensureIndexedIncremental() }
        oldReached.await()
        // 旧 sweep 已占权、尚未开始扫书：取消点名它（jobId）并推进 cancelEpoch
        h.repo.testHookAfterAcquireOwnership = null
        h.repo.cancelIndexing()

        // 新 sweep 占权并完整跑完：不得擦掉旧执行的取消事实
        val newResult = h.repo.ensureIndexedIncremental()
        assertEquals("取消后新触发的扫描必须正常完成（不被旧取消事实影响）", IndexSweepResult.COMPLETED, newResult)

        releaseOld.complete(Unit)
        val oldResult = oldSweep.await()

        assertEquals(
            "被取消的旧扫描必须优雅停止（COMPLETED），不得退回 PROGRESSED 触发 Worker retry —— 缺陷A",
            IndexSweepResult.COMPLETED,
            oldResult,
        )
    }

    @Test
    fun `issueA2 - cancelled sweep blocked mid-sweep must stop without retry or stall`() = runTest {
        val h = createHarness()

        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var blocking = true
        h.repo.testHookAfterStep = { step ->
            if (blocking && step == "after_index_one_book_b1") {
                reached.complete(Unit)
                release.await()
            }
        }

        val oldSweep = async { h.repo.ensureIndexedIncremental() }
        reached.await()
        blocking = false
        h.repo.cancelIndexing()

        // 新 sweep 跑完，旧 sweep 仍在屏障里
        val newResult = h.repo.ensureIndexedIncremental()
        assertEquals(IndexSweepResult.COMPLETED, newResult)

        release.complete(Unit)
        val oldResult = oldSweep.await()

        assertEquals(
            "扫描中途被取消的旧执行必须返回 COMPLETED（不 retry、不误报 STALLED 失败）—— 缺陷A",
            IndexSweepResult.COMPLETED,
            oldResult,
        )
    }

    @Test
    fun `normal retry path still returns PROGRESSED when ownership is held`() = runTest {
        val h = createHarness()

        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var blocking = true
        h.repo.testHookAfterStep = { step ->
            if (blocking && step == "after_index_one_book_b1") {
                reached.complete(Unit)
                release.await()
            }
        }

        val sweep = async { h.repo.ensureIndexedIncremental() }
        reached.await()
        blocking = false

        // 没有任何取消：只是拿不到执行权 → 正常重试语义必须保留（PROGRESSED）
        val second = h.repo.ensureIndexedIncremental()
        assertEquals("未取消时「拿不到执行权」仍必须走正常重试（PROGRESSED）", IndexSweepResult.PROGRESSED, second)

        release.complete(Unit)
        sweep.await()
    }

    @Test
    fun `future sweep triggered after cancel is not cancelled and completes`() = runTest {
        val h = createHarness(initialState = anchorRow("b1"))

        h.repo.cancelIndexing()

        // 取消之后（冷启动 / 周期任务）新触发的续建不得被旧的取消事实误伤
        val result = h.repo.ensureIndexedIncremental()
        assertEquals("取消之后新触发的续建必须正常完成", IndexSweepResult.COMPLETED, result)
        assertNotNull("续建必须继续推进断点", h.stateDao.state?.last_scanned_book_id)
    }

    // =========================================================================
    // v3 缺陷 B：重建 reset 与重新排队的先后合同
    //
    // v2：`testHookBeforeRebuildReset` 之后仍无条件清游标；`triggerFullRebuildNow`
    // 之后仍无条件 force 排队 ⇒ 取消先发生时，断点照样被清、请求照样复活。
    // 合同：取消先于 reset ⇒ 不清断点、不排队；reset 已提交 ⇒ 反馈如实（不能
    // 声称断点保留），且排队前又取消则不再入队。
    // =========================================================================

    @Test
    fun `issueB1 - cancel before rebuild reset must not clear the cursor nor enqueue`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler, initialState = anchorRow("b1"))

        // 取消**先于**重置到达（不抛异常，让重建流程继续走完）
        h.repo.testHookBeforeRebuildReset = { h.repo.cancelIndexing() }

        val launch = h.repo.triggerFullRebuildNow(scheduler)

        assertEquals("取消先于重置时必须放弃重置并如实返回", FullRebuildLaunch.SKIPPED_CANCELLED, launch)
        assertEquals(
            "取消先发生时不得清断点",
            "b1",
            h.stateDao.state?.last_scanned_book_id,
        )
        assertEquals("取消先发生时不得写任何状态行", 0, h.stateDao.setCallCount.get())
        verify(exactly = 0) { scheduler.triggerNow(any()) }
        verify(exactly = 1) { scheduler.cancelPendingSweep() }
        assertFalse("取消后不得留下排队标记（不得复活旧请求）", h.repo.progress.value.isQueued)
    }

    @Test
    fun `issueB2 - cancel after reset commit must not enqueue and must report honestly`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler, initialState = anchorRow("b1"))

        // reset **已提交**之后、排队之前取消
        h.repo.testHookAfterRebuildReset = { h.repo.cancelIndexing() }

        val launch = h.repo.triggerFullRebuildNow(scheduler)

        assertEquals("reset 已提交但排队前被取消 ⇒ 不得再入队", FullRebuildLaunch.RESET_NOT_QUEUED, launch)
        assertEquals("reset 确实已提交：游标已被重置到起点", null, h.stateDao.state?.last_scanned_book_id)
        verify(exactly = 0) { scheduler.triggerNow(any()) }
        assertFalse("不得留下排队标记", h.repo.progress.value.isQueued)

        assertFalse(
            "reset 已提交后取消不得声称断点保留",
            h.repo.cancelIndexing().anchorPreserved,
        )
    }

    @Test
    fun `normal explicit rebuild still resets the cursor and enqueues`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler, initialState = anchorRow("b1"))

        val launch = h.repo.triggerFullRebuildNow(scheduler)

        assertEquals("正常显式重建必须重置 + 排队", FullRebuildLaunch.RESET_QUEUED, launch)
        assertEquals(null, h.stateDao.state?.last_scanned_book_id)
        assertEquals(0, h.stateDao.state?.last_scanned_chapter_index)
        verify(exactly = 1) { scheduler.triggerNow(force = true) }
        assertTrue("已排队必须如实体现在 progress 上（UI 才给得出取消入口）", h.repo.progress.value.isQueued)
    }

    // =========================================================================
    // v3 缺陷 C：queued / 等待重试阶段的取消入口与真实分发
    //
    // v2 的 UI 只在 `isRunning` 时给停止入口；排队 / 等待重试时没有任何入口。
    // v3：`Progress.isQueued` + `canCancel`，并用 ViewModel 的**真实分发入口**
    // `handleProfileAction`（ProfileRoute 与测试共用同一份 when）验证取消确实
    // 被一路派发到 Repository —— 不是 Class.forName 检查类存在。
    // =========================================================================

    @Test
    fun `issueC1 - queued rebuild is cancellable and reports honestly`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler, initialState = anchorRow("b1"))

        assertEquals(FullRebuildLaunch.RESET_QUEUED, h.repo.triggerFullRebuildNow(scheduler))
        assertTrue("已排队必须在 progress 上如实可见", h.repo.progress.value.isQueued)
        assertTrue("在跑或已排队都必须能取消", h.repo.progress.value.canCancel)

        val result = h.repo.cancelIndexing()

        assertTrue("取消排队构建必须如实报告撤下了排队", result.wasQueued)
        assertTrue("撤下排队后 stoppedSomething 必须为真（不得说「没事发生」）", result.stoppedSomething)
        assertFalse("撤下排队后不得还留着排队标记", h.repo.progress.value.isQueued)
        verify(exactly = 1) { scheduler.cancelPendingSweep() }
    }

    @Test
    fun `issueC2 - real dispatch of CancelSearchIndex reaches repository cancel`() = runTest {
        val repo = mockk<SearchIndexRepository>(relaxed = true)
        coEvery { repo.cancelIndexing() } returns CancelIndexingResult(
            wasRunning = true,
            wasQueued = false,
            anchorPreserved = true,
        )
        val vm = createProfileViewModel(repo)

        val messages = mutableListOf<String>()
        // 真实生产分发入口：ProfileRoute 的 Compose when 与这里共用同一份逻辑
        vm.handleProfileAction(ProfileAction.CancelSearchIndex) { messages += it }

        coVerify(exactly = 1) { repo.cancelIndexing() }
        assertEquals(1, messages.size)
        assertTrue(
            "真实分发后的反馈必须说明已请求停止：${messages.single()}",
            messages.single().contains("已请求停止索引构建"),
        )
        vm.viewModelScope.cancel()
    }

    @Test
    fun `issueC3 - real dispatch of RebuildSearchIndex reaches repository rebuild`() = runTest {
        val repo = mockk<SearchIndexRepository>(relaxed = true)
        coEvery { repo.triggerFullRebuildNow(any()) } returns FullRebuildLaunch.RESET_QUEUED
        val vm = createProfileViewModel(repo)

        val messages = mutableListOf<String>()
        vm.handleProfileAction(ProfileAction.RebuildSearchIndex) { messages += it }

        coVerify(exactly = 1) { repo.triggerFullRebuildNow(any()) }
        assertTrue(
            "正常显式重建必须如实反馈已重置：${messages.single()}",
            messages.single().contains("已重置索引进度"),
        )
        vm.viewModelScope.cancel()
    }

    @Test
    fun `issueC4 - rebuild feedback is honest when cancel won the race`() = runTest {
        val repo = mockk<SearchIndexRepository>(relaxed = true)
        coEvery { repo.triggerFullRebuildNow(any()) } returns FullRebuildLaunch.SKIPPED_CANCELLED
        val vm = createProfileViewModel(repo)

        val messages = mutableListOf<String>()
        vm.handleProfileAction(ProfileAction.RebuildSearchIndex) { messages += it }

        val msg = messages.single()
        assertTrue("取消先到时不得声称已重置：$msg", msg.contains("重建未执行"))
        assertFalse("取消先到时不得声称已重置：$msg", msg.contains("已重置索引进度"))
        vm.viewModelScope.cancel()
    }

    @Test
    fun `issueC5 - cancel feedback must not claim anchor preserved after committed reset`() = runTest {
        val repo = mockk<SearchIndexRepository>(relaxed = true)
        coEvery { repo.cancelIndexing() } returns CancelIndexingResult(
            wasRunning = true,
            wasQueued = false,
            anchorPreserved = false,
        )
        val vm = createProfileViewModel(repo)

        val messages = mutableListOf<String>()
        vm.handleProfileAction(ProfileAction.CancelSearchIndex) { messages += it }

        val msg = messages.single()
        assertFalse("reset 已提交后不得声称断点保留：$msg", msg.contains("断点保留"))
        assertTrue("reset 已提交后必须如实说明下次从头扫描：$msg", msg.contains("从头扫描"))
        vm.viewModelScope.cancel()
    }

    @Test
    fun `issueC6 - queued-only cancel feedback mentions the queue`() = runTest {
        val repo = mockk<SearchIndexRepository>(relaxed = true)
        coEvery { repo.cancelIndexing() } returns CancelIndexingResult(
            wasRunning = false,
            wasQueued = true,
            anchorPreserved = true,
        )
        val vm = createProfileViewModel(repo)

        val messages = mutableListOf<String>()
        vm.handleProfileAction(ProfileAction.CancelSearchIndex) { messages += it }

        val msg = messages.single()
        assertTrue("排队取消必须提到排队：$msg", msg.contains("排队"))
        assertFalse("排队取消不得说成「没有正在进行的后台索引构建」：$msg", msg.contains("没有正在进行的后台索引构建"))
        vm.viewModelScope.cancel()
    }

    // =========================================================================
    // v4 两个动作窗口：检查之后、动作之前的缝隙
    //
    // v3 的两处校验都在动作之前，但**不是**与动作同一个临界区：
    //   · 窗口1：校验通过后去等 `sweepLock`（清断点的提交资格）→ 取消可在此期间到达，
    //     取到锁之后仍旧清断点；
    //   · 窗口2：校验通过后挂起 `bookDao.countActive()` → 取消先 `cancelUniqueWork`，
    //     旧请求随后仍 `triggerNow` 重新排队（复活刚取消的重建）。
    //
    // 这里的屏障全部是**真实生产路径上的位置**：
    //   · 游标写入 = `runIfCurrentGeneration` 持有 `sweepLock` 时（FakeStateDao 在落盘前挂起）；
    //   · 重置写入 = `invalidateSweepForFullRebuild` 持有 `sweepLock` 时；
    //   · countActive 挂起 = `triggerFullRebuildNow` 的挂起 DAO 调用本身。
    // 不使用 sleep、不改生产逻辑、不做故障突变（v3 的 make_red_baseline 那种做法本轮不用）。
    // =========================================================================

    @Test
    fun `window1 - cancel while reset waits for commit qualification must not clear the cursor`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler)

        val sweepInCursorWrite = CompletableDeferred<Unit>()
        val releaseSweep = CompletableDeferred<Unit>()
        var armed = true
        h.stateDao.onBeforeSet = { row ->
            // 游标行（非重置行）：此刻 sweep 正持有 sweepLock、尚未提交
            if (armed && row.last_scanned_book_id != null) {
                armed = false
                sweepInCursorWrite.complete(Unit)
                releaseSweep.await()
            }
        }

        val sweep = async { h.repo.ensureIndexedIncremental() }
        sweepInCursorWrite.await()

        // 重建：校验通过 → 停在等 sweepLock（即「检查之后、提交之前」的窗口内）
        val rebuild = async { h.repo.invalidateSweepForFullRebuild() }
        advanceUntilIdle()

        val cancel = h.repo.cancelIndexing() // 取消先取得资格
        assertTrue("窗口1：取消时确有构建在跑", cancel.wasRunning)

        releaseSweep.complete(Unit)
        val committed = rebuild.await()
        sweep.await()

        assertFalse("取消先取得资格时重置不得提交 —— 窗口1", committed)
        assertTrue(
            "取消先取得资格时不得写重置行（断点必须原样保留）—— 窗口1：" + h.stateDao.writtenStates,
            h.stateDao.writtenStates.none { it.last_scanned_book_id == null },
        )
        assertNotNull("断点必须仍然是 sweep 写下的游标", h.stateDao.state?.last_scanned_book_id)
    }

    @Test
    fun `window2 - cancel during countActive must not enqueue afterwards`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val countEntered = CompletableDeferred<Unit>()
        val releaseCount = CompletableDeferred<Unit>()
        val h = createHarness(
            scheduler = scheduler,
            initialState = anchorRow("b1"),
            onCountActive = {
                countEntered.complete(Unit)
                releaseCount.await()
            },
        )

        val launchDeferred = async { h.repo.triggerFullRebuildNow(scheduler) }
        // 已在 epoch 校验之后、真正入队之前的挂起点里
        countEntered.await()

        val cancel = h.repo.cancelIndexing()
        verify(exactly = 1) { scheduler.cancelPendingSweep() } // 撤下动作确实先发生了

        releaseCount.complete(Unit)
        val launch = launchDeferred.await()

        assertEquals(
            "取消先取得资格时旧重建请求不得再入队（否则复活刚取消的重建）—— 窗口2",
            FullRebuildLaunch.RESET_NOT_QUEUED,
            launch,
        )
        verify(exactly = 0) { scheduler.triggerNow(any()) }
        assertFalse("取消后不得留下排队标记", h.repo.progress.value.isQueued)
        // 反馈如实：此刻既没有在跑的扫描、也没有已排队的构建（请求尚未入队），
        // 因此取消回报「没有正在进行的后台索引构建」是准确的；但它**必须**仍然阻止随后的入队。
        assertFalse("该时刻确实没有在跑的构建", cancel.wasRunning)
        assertFalse("该时刻确实还没有已排队的构建", cancel.wasQueued)
    }

    // =========================================================================
    // CRA-SEARCH-REBUILD-CANCEL-EPOCH-FIX-v1（NEW R2）：
    // 请求起点 → 重置资格之间的两个挂起窗口。
    //
    // 取消归属判据是「本次重建请求之后发生的取消才算数」。旧实现在**取得重置资格时**
    // （`invalidateSweepForFullRebuild` 取到 rebuildLock 之后）才重新读 cancelEpoch，
    // 于是请求起点到这一刻之间的取消被洗掉，旧请求照样清断点。
    // 下面两个窗口都在**公开 trigger** 上构造，屏障是真实的挂起 DAO 调用（不用 sleep）。
    // =========================================================================

    private fun indexedRow(bookId: String) = SearchTermRow(
        term = "甲",
        book_id = bookId,
        chapter_index = 0,
        text_basis = "original",
        hits = 1,
    )

    @Test
    fun `window3 - cancel during the public trigger purge must keep the anchor and not enqueue`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler, initialState = anchorRow("b1"))
        // purge 只有存在已建行时才会走到 `bookDao.activeIds()` 这个挂起点
        h.termDao.rows += indexedRow("b1")

        val purgeEntered = CompletableDeferred<Unit>()
        val releasePurge = CompletableDeferred<Unit>()
        h.bookDao.onActiveIds = {
            purgeEntered.complete(Unit)
            releasePurge.await()
        }

        val launchDeferred = async { h.repo.triggerFullRebuildNow(scheduler) }
        purgeEntered.await() // 重建请求已经发生，正卡在前置清理的挂起 DAO 里

        h.repo.cancelIndexing()
        releasePurge.complete(Unit)
        val launch = launchDeferred.await()

        assertEquals(
            "purge 窗口内到达的取消必须先于重置资格：放弃重置并如实返回 —— 窗口3",
            FullRebuildLaunch.SKIPPED_CANCELLED,
            launch,
        )
        assertEquals("purge 窗口取消不得清断点 —— 窗口3", "b1", h.stateDao.state?.last_scanned_book_id)
        assertEquals("purge 窗口取消不得写任何状态行 —— 窗口3", 0, h.stateDao.setCallCount.get())
        verify(exactly = 0) { scheduler.triggerNow(any()) }
        assertFalse("取消后不得留下排队标记", h.repo.progress.value.isQueued)
    }

    @Test
    fun `window4 - second rebuild cancelled while queued on rebuildLock must keep the anchor`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler, initialState = anchorRow("b1"))

        val firstInReset = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var armed = true
        h.repo.testHookBeforeRebuildReset = {
            if (armed) {
                armed = false
                firstInReset.complete(Unit)
                releaseFirst.await()
            }
        }

        // 第一次重建：已进入 invalidateSweepForFullRebuild、持有 rebuildLock，停在重置写入之前
        val first = async { h.repo.triggerFullRebuildNow(scheduler) }
        firstInReset.await()

        // 第二次重建：过了前置清理，排在 rebuildLock 上等第一次
        val second = async { h.repo.triggerFullRebuildNow(scheduler) }
        advanceUntilIdle()

        h.repo.cancelIndexing()

        releaseFirst.complete(Unit)
        val firstLaunch = first.await()
        val secondLaunch = second.await()

        assertEquals("第一次重建也应在重置资格处放弃 —— 窗口4", FullRebuildLaunch.SKIPPED_CANCELLED, firstLaunch)
        assertEquals(
            "排在锁上的第二次重建同样不得清断点 —— 窗口4",
            FullRebuildLaunch.SKIPPED_CANCELLED,
            secondLaunch,
        )
        assertEquals("断点必须原样保留 —— 窗口4", "b1", h.stateDao.state?.last_scanned_book_id)
        assertEquals("两次请求都不得写任何状态行 —— 窗口4", 0, h.stateDao.setCallCount.get())
        verify(exactly = 0) { scheduler.triggerNow(any()) }
        assertFalse("取消后不得留下排队标记", h.repo.progress.value.isQueued)
    }

    @Test
    fun `window4 committed - second rebuild cancelled while queued reports the committed reset honestly`() =
        runTest {
            val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
            val h = createHarness(scheduler = scheduler, initialState = anchorRow("b1"))

            val firstCommitted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            var armed = true
            h.repo.testHookAfterRebuildReset = {
                if (armed) {
                    armed = false
                    firstCommitted.complete(Unit)
                    releaseFirst.await()
                }
            }

            // 第一次重建已提交重置（断点已清），仍持有 rebuildLock
            val first = async { h.repo.triggerFullRebuildNow(scheduler) }
            firstCommitted.await()
            assertNull("前提：第一次重建已把断点重置到起点", h.stateDao.state?.last_scanned_book_id)

            // 第二次重建排在锁上，此刻用户取消
            val second = async { h.repo.triggerFullRebuildNow(scheduler) }
            advanceUntilIdle()
            h.repo.cancelIndexing()

            releaseFirst.complete(Unit)
            val firstLaunch = first.await()
            val secondLaunch = second.await()

            assertEquals("第一次重建在排队前被取消 ⇒ 不得入队", FullRebuildLaunch.RESET_NOT_QUEUED, firstLaunch)
            assertEquals(
                "断点已被更早的重置清除时，不得声称断点原样保留 —— 窗口4-已提交",
                FullRebuildLaunch.RESET_NOT_QUEUED,
                secondLaunch,
            )
            assertEquals(
                "被取消的第二次请求不得再写一次重置行 —— 窗口4-已提交",
                1,
                h.stateDao.setCallCount.get(),
            )
            verify(exactly = 0) { scheduler.triggerNow(any()) }
        }

    @Test
    fun `after a purge-window cancel a new explicit rebuild still resets and enqueues`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler, initialState = anchorRow("b1"))
        h.termDao.rows += indexedRow("b1")

        val purgeEntered = CompletableDeferred<Unit>()
        val releasePurge = CompletableDeferred<Unit>()
        var armed = true
        h.bookDao.onActiveIds = {
            if (armed) {
                armed = false
                purgeEntered.complete(Unit)
                releasePurge.await()
            }
        }

        val cancelled = async { h.repo.triggerFullRebuildNow(scheduler) }
        purgeEntered.await()
        h.repo.cancelIndexing()
        releasePurge.complete(Unit)

        assertEquals("前提：第一次重建被取消", FullRebuildLaunch.SKIPPED_CANCELLED, cancelled.await())
        assertEquals("前提：断点仍在", "b1", h.stateDao.state?.last_scanned_book_id)

        // 用户随后重新主动发起：不得被上一次取消永久封死
        val relaunch = h.repo.triggerFullRebuildNow(scheduler)

        assertEquals("取消之后重新发起的重建必须仍能重置 + 排队", FullRebuildLaunch.RESET_QUEUED, relaunch)
        assertNull("重新发起时必须真的清断点", h.stateDao.state?.last_scanned_book_id)
        assertEquals(0, h.stateDao.state?.last_scanned_chapter_index)
        verify(exactly = 1) { scheduler.triggerNow(force = true) }
        assertTrue("已排队必须如实体现在 progress 上", h.repo.progress.value.isQueued)
    }

    // ---- 相反顺序：确保不是靠「阻塞一切」通过 ----

    @Test
    fun `window1 reverse - cancel after reset already got commit qualification reports committed honestly`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler, initialState = anchorRow("b1"))

        val resetEntered = CompletableDeferred<Unit>()
        val releaseReset = CompletableDeferred<Unit>()
        var armed = true
        h.stateDao.onBeforeSet = { row ->
            // 重置行：已经取得提交资格、正在写入
            if (armed && row.last_scanned_book_id == null) {
                armed = false
                resetEntered.complete(Unit)
                releaseReset.await()
            }
        }

        val launchDeferred = async { h.repo.triggerFullRebuildNow(scheduler) }
        resetEntered.await()

        val firstCancel = h.repo.cancelIndexing()
        assertTrue("DAO 尚未完成时必须明确报告提交中", firstCancel.resetPending)
        assertFalse("提交中不得承诺保留断点", firstCancel.anchorPreserved)
        val vm = createProfileViewModel(h.repo)
        val messages = mutableListOf<String>()
        try {
            vm.cancelSearchIndex { messages += it }
            assertEquals(
                "重置正在提交；已请求停止后续索引构建，扫描断点以提交结果为准。",
                messages.single(),
            )
        } finally {
            vm.viewModelScope.cancel()
            releaseReset.complete(Unit)
        }
        val launch = launchDeferred.await()

        assertEquals(
            "重置先取得资格 ⇒ 如实报「已提交但未排队」，且不得再入队",
            FullRebuildLaunch.RESET_NOT_QUEUED,
            launch,
        )
        assertNull("重置确实已提交：断点已被清（反馈不得声称保留）", h.stateDao.state?.last_scanned_book_id)
        verify(exactly = 0) { scheduler.triggerNow(any()) }
        val settledCancel = h.repo.cancelIndexing()
        assertFalse("reset 已提交后取消不得声称断点保留", settledCancel.anchorPreserved)
        assertFalse("reset 已提交后不得还报告提交中", settledCancel.resetPending)
    }

    @Test
    fun `window2 reverse - cancel after enqueue still withdraws the queued work`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler, initialState = anchorRow("b1"))

        assertEquals(
            "入队先取得资格 ⇒ 正常排队（不得靠阻塞一切通过）",
            FullRebuildLaunch.RESET_QUEUED,
            h.repo.triggerFullRebuildNow(scheduler),
        )
        verify(exactly = 1) { scheduler.triggerNow(force = true) }
        assertTrue("正常入队后必须如实显示已排队", h.repo.progress.value.isQueued)

        val cancel = h.repo.cancelIndexing()

        assertTrue("已排队的构建必须能被撤下", cancel.wasQueued)
        assertFalse("撤下后不得还留着排队标记", h.repo.progress.value.isQueued)
        verify(exactly = 1) { scheduler.cancelPendingSweep() }
    }

    @Test
    fun `after the reset window abort a new explicit rebuild and a new sweep still work`() = runTest {
        val scheduler = mockk<SearchIndexScheduler>(relaxed = true)
        val h = createHarness(scheduler = scheduler)

        val sweepInCursorWrite = CompletableDeferred<Unit>()
        val releaseSweep = CompletableDeferred<Unit>()
        var armed = true
        h.stateDao.onBeforeSet = { row ->
            if (armed && row.last_scanned_book_id != null) {
                armed = false
                sweepInCursorWrite.complete(Unit)
                releaseSweep.await()
            }
        }

        val sweep = async { h.repo.ensureIndexedIncremental() }
        sweepInCursorWrite.await()
        val rebuild = async { h.repo.invalidateSweepForFullRebuild() }
        advanceUntilIdle()
        h.repo.cancelIndexing()
        releaseSweep.complete(Unit)
        rebuild.await()
        sweep.await()

        // 旧请求作废之后：用户新触发的显式重建必须仍然可用（旧请求不得挡住新请求）
        assertEquals(
            "取消之后用户新触发的显式重建必须仍然可用",
            FullRebuildLaunch.RESET_QUEUED,
            h.repo.triggerFullRebuildNow(scheduler),
        )
        verify(exactly = 1) { scheduler.triggerNow(force = true) }

        // 续建也必须仍然可用
        assertEquals(
            "取消之后的续建必须仍然可用",
            IndexSweepResult.COMPLETED,
            h.repo.ensureIndexedIncremental(),
        )
        assertNotNull("续建必须继续推进断点", h.stateDao.state?.last_scanned_book_id)
    }

    @Test
    fun `failed reset clears pending feedback and preserves the original anchor`() = runTest {
        val h = createHarness(initialState = anchorRow("b1"))
        val resetEntered = CompletableDeferred<Unit>()
        val releaseReset = CompletableDeferred<Unit>()
        h.stateDao.onBeforeSet = { row ->
            if (row.last_scanned_book_id == null) {
                resetEntered.complete(Unit)
                releaseReset.await()
                throw IllegalStateException("reset write failed")
            }
        }
        val rebuild = async { runCatching { h.repo.invalidateSweepForFullRebuild() } }
        resetEntered.await()
        val vm = createProfileViewModel(h.repo)
        val pendingMessages = mutableListOf<String>()
        try {
            vm.cancelSearchIndex { pendingMessages += it }
        } finally {
            releaseReset.complete(Unit)
        }
        assertEquals("reset write failed", rebuild.await().exceptionOrNull()?.message)
        val afterFailure = h.repo.cancelIndexing()
        assertFalse("失败后不得永久停留在提交中", afterFailure.resetPending)
        assertTrue("失败不得报告已清断点", afterFailure.anchorPreserved)
        assertEquals("b1", h.stateDao.state?.last_scanned_book_id)
        val settledMessages = mutableListOf<String>()
        vm.cancelSearchIndex { settledMessages += it }
        vm.viewModelScope.cancel()
        assertEquals(
            "重置正在提交；已请求停止后续索引构建，扫描断点以提交结果为准。",
            pendingMessages.single(),
        )
        assertEquals("当前没有正在进行的后台索引构建。", settledMessages.single())
        // 清除失败替身后，新显式重建仍能取得执行权。
        h.stateDao.onBeforeSet = null
        assertTrue(h.repo.invalidateSweepForFullRebuild())
        assertFalse(h.repo.cancelIndexing().anchorPreserved)
    }

    @Test
    fun `failed reset after a committed reset restores committed anchor state`() = runTest {
        val h = createHarness(initialState = anchorRow("b1"))
        assertTrue(h.repo.invalidateSweepForFullRebuild())
        h.stateDao.onBeforeSet = { throw IllegalStateException("reset write failed") }
        val failure = runCatching { h.repo.invalidateSweepForFullRebuild() }
        assertEquals("reset write failed", failure.exceptionOrNull()?.message)
        val result = h.repo.cancelIndexing()
        assertFalse("之前已重置时，失败不能伪报旧断点保留", result.anchorPreserved)
        assertFalse("失败必须退出提交中状态", result.resetPending)
    }

    private fun createProfileViewModel(
        searchIndexRepository: SearchIndexRepository,
    ): ProfileViewModel {
        val dispatcher = UnconfinedTestDispatcher()
        val bookRepository = mockk<BookRepository>(relaxed = true)
        coEvery { bookRepository.observeBooks() } returns flowOf(emptyList())
        coEvery { bookRepository.observeProgress() } returns flowOf(emptyList())
        coEvery { bookRepository.observeSessions() } returns flowOf(emptyList())
        coEvery { bookRepository.observeNotes() } returns flowOf(emptyList())
        coEvery { bookRepository.observeHighlights() } returns flowOf(emptyList())
        coEvery { bookRepository.observeCachedCount() } returns flowOf(0)
        coEvery { bookRepository.observeCachedBytes() } returns flowOf(0L)
        val statsRepository = mockk<StatsRepository>(relaxed = true)
        coEvery { statsRepository.observeStatsSessions() } returns flowOf(emptyList())
        coEvery { statsRepository.observeStatsProgress() } returns flowOf(emptyList())
        coEvery { statsRepository.observeStatsInspirations() } returns flowOf(emptyList())
        val goalStore = mockk<GoalStore>(relaxed = true)
        coEvery { goalStore.prefs } returns flowOf(ReadingGoalPrefs())
        val syncPrefsStore = mockk<SyncPrefsStore>(relaxed = true)
        coEvery { syncPrefsStore.prefs } returns flowOf(SyncPrefs())
        val configStore = mockk<SyncConfigStore>(relaxed = true)
        coEvery { configStore.config } returns null
        val webDavConfigStore = mockk<WebDavConfigStore>(relaxed = true)
        coEvery { webDavConfigStore.config } returns null
        return ProfileViewModel(
            jsonBridge = mockk<JsonBridge>(relaxed = true),
            localZipBackup = mockk<LocalZipBackup>(relaxed = true),
            pairingManager = mockk<PairingManager>(relaxed = true),
            syncRepository = mockk<SyncRepository>(relaxed = true),
            configStore = configStore,
            webDavConfigStore = webDavConfigStore,
            webDavBackup = mockk<WebDavBackup>(relaxed = true),
            legadoImporter = mockk<LegadoReadingProgressImporter>(relaxed = true),
            aiClient = mockk<AiClient>(relaxed = true),
            bookRepository = bookRepository,
            noteRepository = mockk<NoteRepository>(relaxed = true),
            statsRepository = statsRepository,
            searchIndexRepository = searchIndexRepository,
            searchIndexScheduler = mockk<SearchIndexScheduler>(relaxed = true),
            goalStore = goalStore,
            goalScheduler = mockk<ReadingGoalScheduler>(relaxed = true),
            syncPrefsStore = syncPrefsStore,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
    }
}
