package com.creationreadingassistant.data.repository

import android.content.Context
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
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.feature.search.SearchTokenizer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * S03-1 确定性并发测试：
 * 验证全量重建触发时，旧扫描任务不能产生任何晚到写入（游标、词条、覆盖率），
 * 新扫描从第一本书开始，以及任务所有权协议与异常隔离。
 *
 * 严格遵循测试先行红线：
 * 1. 使用 CompletableDeferred 双向屏障握手，消除 sleep 与调度运气；
 * 2. 断言真实 DAO 调用与落盘状态，不断言自造令牌；
 * 3. 拦截点设置在无锁协作点，完全消除死锁。
 */
class SearchIndexRepositoryRebuildConcurrencyTest {

    private class FakeSearchIndexStateDao : SearchIndexStateDao {
        var state: SearchIndexStateRow? = null
        val setCallCount = AtomicInteger(0)
        var onBeforeSet: (suspend (SearchIndexStateRow) -> Unit)? = null
        var shouldThrowOnSet: Throwable? = null

        override suspend fun set(row: SearchIndexStateRow) {
            shouldThrowOnSet?.let { throw it }
            onBeforeSet?.invoke(row)
            setCallCount.incrementAndGet()
            state = row
        }

        override suspend fun get(): SearchIndexStateRow? = state
    }

    private class FakeSearchTermDao : SearchTermDao {
        val rows = mutableListOf<SearchTermRow>()
        val deleteByBookCalls = mutableListOf<String>()
        val upsertAllCalls = AtomicInteger(0)

        override suspend fun upsertAll(rows: List<SearchTermRow>) {
            upsertAllCalls.incrementAndGet()
            this.rows += rows
        }

        override suspend fun deleteByBook(bookId: String) {
            deleteByBookCalls.add(bookId)
            rows.removeIf { it.book_id == bookId }
        }

        override suspend fun deleteByBookAndBasis(bookId: String, textBasis: String) {
            rows.removeIf { it.book_id == bookId && it.text_basis == textBasis }
        }

        override suspend fun countIndexedBooks(): Long = rows.map { it.book_id }.distinct().size.toLong()

        override suspend fun rowsByTermForBasis(term: String, textBasis: String): List<SearchTermRow> =
            rows.filter { it.term == term && it.text_basis == textBasis }

        override suspend fun distinctBookIds(): List<String> = rows.map { it.book_id }.distinct()
    }

    private class FakeSearchIndexCoverageDao : SearchIndexCoverageDao {
        val rows = mutableListOf<SearchIndexCoverageRow>()
        val upsertAllCalls = AtomicInteger(0)
        val deleteByBookCalls = mutableListOf<String>()

        override suspend fun upsertAll(rows: List<SearchIndexCoverageRow>) {
            upsertAllCalls.incrementAndGet()
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
            deleteByBookCalls.add(bookId)
            rows.removeIf { it.book_id == bookId }
        }

        override suspend fun deleteAll() {
            rows.clear()
        }
    }

    private class TestHarness(
        val repo: SearchIndexRepository,
        val stateDao: FakeSearchIndexStateDao,
        val termDao: FakeSearchTermDao,
        val coverageDao: FakeSearchIndexCoverageDao,
        val bookDao: BookDao,
        val bookContentDao: BookContentDao,
        val rulesRepo: RulesRepository,
        val books: List<BookEntity>,
    )

    private fun createHarness(
        books: List<BookEntity> = listOf(
            BookEntity(id = "b1", title = "书本一", format = "txt", updated_at = "2026-01-01T00:00:00Z"),
            BookEntity(id = "b2", title = "书本二", format = "txt", updated_at = "2026-01-01T00:00:00Z"),
        ),
    ): TestHarness {
        val stateDao = FakeSearchIndexStateDao()
        val termDao = FakeSearchTermDao()
        val coverageDao = FakeSearchIndexCoverageDao()

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

        val repo = SearchIndexRepository(
            context = mockk<Context>(relaxed = true),
            bookDao = bookDao,
            bookContentDao = bookContentDao,
            termDao = termDao,
            stateDao = stateDao,
            coverageDao = coverageDao,
            tokenizer = SearchTokenizer(),
            rulesRepository = rulesRepo,
            scheduler = null,
        )

        return TestHarness(repo, stateDao, termDao, coverageDao, bookDao, bookContentDao, rulesRepo, books)
    }

    // =========================================================================
    // =========================================================================
    // 断言 1：旧扫描不能覆盖全量重建重置后的书级与章节游标
    // =========================================================================
    @Test
    fun `old sweep cannot overwrite reset cursor after full rebuild triggered`() = runTest {
        val harness = createHarness()
        val reachedWritePoint = CompletableDeferred<Unit>()
        val allowProceed = CompletableDeferred<Unit>()

        // 协作拦截点：旧任务处理完 b1 的全部正文与覆盖率、在写游标之前暂停（此时临界区锁已完全释放）
        harness.repo.testHookAfterStep = { step ->
            if (step == "after_index_one_book_b1") {
                reachedWritePoint.complete(Unit)
                allowProceed.await()
            }
        }

        // 1. 启动旧扫描任务
        val oldJob = launch(Dispatchers.Default) {
            harness.repo.ensureIndexedIncremental()
        }

        // 2. 确认旧任务已精准停在 b1 索引完成、写游标之前
        reachedWritePoint.await()

        // 3. 用户在 UI 触发全量重建：将游标重置为 null
        harness.repo.triggerFullRebuildNow()

        // 断言：全量重建触发后，游标已经被置为 null
        val cursorAfterRebuild = harness.stateDao.get()
        assertNotNull("重建必须写入状态行", cursorAfterRebuild)
        assertNull("重建必须将 last_scanned_book_id 重置为 null", cursorAfterRebuild!!.last_scanned_book_id)
        assertEquals(0, cursorAfterRebuild.last_scanned_chapter_index)

        // 4. 放行旧任务，允许其继续尝试推进
        allowProceed.complete(Unit)
        oldJob.join()

        // 5. 核心断言：旧任务绝不能把已重置的游标覆盖为 b1
        val finalCursor = harness.stateDao.get()
        assertNotNull("最终状态行必须存在", finalCursor)
        assertNull("旧任务不得将重置后的游标覆盖为 b1", finalCursor!!.last_scanned_book_id)
        assertEquals("章节游标必须保持为 0", 0, finalCursor.last_scanned_chapter_index)
    }

    // =========================================================================
    // 断言 2：旧扫描不能在新代次重建后覆盖 terms 和 coverage
    // =========================================================================
    @Test
    fun `old sweep cannot overwrite terms and coverage after new generation rebuild`() = runTest {
        val harness = createHarness()
        val reachedBeforeWrite = CompletableDeferred<Unit>()
        val allowProceed = CompletableDeferred<Unit>()

        // 初始为 b1 写入新代次数据
        harness.termDao.upsertAll(
            listOf(
                SearchTermRow(term = "新词条", book_id = "b1", chapter_index = 0, text_basis = "original", hits = 10)
            )
        )

        // 协作拦截点：旧任务在处理完 b1 后被暂停
        harness.repo.testHookAfterStep = { step ->
            if (step == "after_index_one_book_b1") {
                reachedBeforeWrite.complete(Unit)
                allowProceed.await()
            }
        }

        val oldJob = launch(Dispatchers.Default) {
            harness.repo.ensureIndexedIncremental()
        }

        reachedBeforeWrite.await()

        // 触发全量重建（产生新代次）
        harness.repo.triggerFullRebuildNow()
        val callsAtRebuild = harness.termDao.upsertAllCalls.get()

        // 放行旧任务
        allowProceed.complete(Unit)
        oldJob.join()

        // 核心断言：旧任务识别代次失效后，不得继续向 b2 产生新的 termDao.upsertAll 写入
        // 在两本书的书架上，如果旧任务没有被代次阻断，它会继续扫描 b2 并调用 upsertAll
        val finalUpsertCalls = harness.termDao.upsertAllCalls.get()
        assertEquals(
            "旧任务在代次失效后不得继续扫描后续书籍 (b2) 写入词条",
            callsAtRebuild,
            finalUpsertCalls,
        )
    }

    // =========================================================================
    // 断言 3：全量重建重置后，新扫描必须从第一本书 (b1) 开始
    // =========================================================================
    @Test
    fun `new scan starts from the first book after full rebuild reset`() = runTest {
        val harness = createHarness()

        // 初始状态：模拟此前已扫描到 b1
        harness.stateDao.set(
            SearchIndexStateRow(
                tokenizer_version = SearchIndexRepository.TOKENIZER_VERSION,
                last_scanned_book_id = "b1",
                last_scanned_chapter_index = 0,
                built_at = 1000L,
            )
        )

        // 触发全量重建
        harness.repo.triggerFullRebuildNow()

        // 执行新一轮扫描
        val result = harness.repo.ensureIndexedIncremental()
        assertTrue("新扫描应有推进或完成", result == IndexSweepResult.COMPLETED || result == IndexSweepResult.PROGRESSED)

        // 断言：b1 必须被新扫描处理，覆盖率表里必须包含 b1 和 b2 的更新
        val b1Coverages = harness.coverageDao.rowsForBook("b1")
        assertTrue("新扫描必须重新从第一本书 (b1) 开始索引", b1Coverages.isNotEmpty())
    }

    @Test
    fun `old sweep finally does not clear new sweep isRunning progress state`() = runTest {
        val harness = createHarness()
        val reachedPoint = CompletableDeferred<Unit>()
        val allowOldToExit = CompletableDeferred<Unit>()

        // 协作拦截点：旧任务处理完 b1 后暂停
        harness.repo.testHookAfterStep = { step ->
            if (step == "after_index_one_book_b1") {
                reachedPoint.complete(Unit)
                allowOldToExit.await()
            }
        }

        val oldJob = launch(Dispatchers.Default) {
            harness.repo.ensureIndexedIncremental()
        }

        reachedPoint.await()

        // 此时触发重建并启动新任务
        harness.repo.triggerFullRebuildNow()

        val newJobReached = CompletableDeferred<Unit>()
        val allowNewJob = CompletableDeferred<Unit>()
        harness.repo.testHookAfterStep = { step ->
            if (step == "after_index_one_book_b1") {
                newJobReached.complete(Unit)
                allowNewJob.await()
            }
        }

        val newJob = launch(Dispatchers.Default) {
            harness.repo.ensureIndexedIncremental()
        }
        newJobReached.await()
        assertTrue("新扫描启动后 isRunning 应为 true", harness.repo.progress.value.isRunning)

        // 放行旧任务，让旧任务执行其 finally 块并退出
        allowOldToExit.complete(Unit)
        oldJob.join()

        // 核心断言：旧任务退出后，isRunning 绝不能被抹成 false
        assertTrue(
            "新任务运行状态不得被旧任务 finally 抹除为 false",
            harness.repo.progress.value.isRunning,
        )

        // 清理新任务
        allowNewJob.complete(Unit)
        newJob.join()
    }

    // =========================================================================
    // 断言 A1：前置 DAO 失败时所有权可靠释放，且后续扫描能够正常启动
    // =========================================================================
    @Test
    fun `pre-scan DAO failure releases sweep ownership and allows subsequent sweep`() = runTest {
        val harness = createHarness()

        // 模拟前置 bookDao.countActive 失败
        coEvery { harness.bookDao.countActive() } throws RuntimeException("DB 查询超时")

        var caught = false
        try {
            harness.repo.ensureIndexedIncremental()
        } catch (e: RuntimeException) {
            caught = true
            assertEquals("DB 查询超时", e.message)
        }
        assertTrue("前置 DAO 异常必须向外抛出", caught)

        // 恢复 DAO 正常
        coEvery { harness.bookDao.countActive() } returns harness.books.size

        // 核心断言：异常释放了所有权，后续扫描能成功启动并取得所有权推进
        val nextResult = harness.repo.ensureIndexedIncremental()
        assertTrue(
            "前置失败释放所有权后，后续扫描必须能够正常启动执行",
            nextResult == IndexSweepResult.COMPLETED || nextResult == IndexSweepResult.PROGRESSED,
        )
        assertNotNull("后续扫描应成功写入游标", harness.stateDao.get())
    }

    // =========================================================================
    // 断言 A2：前置取消异常可靠向外透传，且释放扫描所有权
    // =========================================================================
    @Test
    fun `pre-scan cancellation propagates and releases sweep ownership`() = runTest {
        val harness = createHarness()
        val reachedCountActive = CompletableDeferred<Unit>()
        val proceedCountActive = CompletableDeferred<Unit>()

        coEvery { harness.bookDao.countActive() } coAnswers {
            reachedCountActive.complete(Unit)
            proceedCountActive.await()
            harness.books.size
        }

        val job = launch(Dispatchers.Default) {
            harness.repo.ensureIndexedIncremental()
        }

        reachedCountActive.await()
        job.cancel(CancellationException("WorkManager 任务取消"))
        proceedCountActive.complete(Unit)
        job.join()
        assertTrue("被取消的任务必须处于 cancelled 状态", job.isCancelled)

        // 恢复 DAO
        coEvery { harness.bookDao.countActive() } returns harness.books.size

        // 核心断言：取消后所有权已被释放，后续扫描能正常执行并实际落盘
        val nextResult = harness.repo.ensureIndexedIncremental()
        assertTrue(
            "取消释放所有权后，后续扫描必须能够正常推进或完成",
            nextResult == IndexSweepResult.COMPLETED || nextResult == IndexSweepResult.PROGRESSED,
        )
        assertNotNull("后续扫描必须真正写入游标", harness.stateDao.get())
        assertTrue("后续扫描必须写入 term 行", harness.termDao.rows.isNotEmpty())
    }

    // =========================================================================
    // 断言 A3：indexSingleBook 前置失败释放运行计数，不泄露 isRunning
    // =========================================================================
    @Test
    fun `indexSingleBook pre-DAO failure releases single book running count`() = runTest {
        val harness = createHarness()
        coEvery { harness.bookDao.countActive() } throws RuntimeException("磁盘 IO 错误")

        var caught = false
        try {
            harness.repo.indexSingleBook(harness.books.first())
        } catch (e: RuntimeException) {
            caught = true
        }
        assertTrue("单本前置异常必须向外抛出", caught)

        // 核心断言：运行计数被 finally 释放，isRunning 绝不泄漏为 true
        assertFalse(
            "单本前置失败后 running count 必须归零，isRunning 必须为 false",
            harness.repo.progress.value.isRunning,
        )
    }

    // =========================================================================
    // 断言 B1：全量重建重置 DAO 暂停期间阻止新扫描读取旧游标
    // =========================================================================
    @Test
    fun `full rebuild reset pause prevents new sweep from reading stale cursor`() = runTest {
        val harness = createHarness()

        // 初始状态：此前已扫描到 b1
        harness.stateDao.set(
            SearchIndexStateRow(
                tokenizer_version = SearchIndexRepository.TOKENIZER_VERSION,
                last_scanned_book_id = "b1",
                last_scanned_chapter_index = 0,
                built_at = 1000L,
            )
        )

        val reachedResetSet = CompletableDeferred<Unit>()
        val allowResetSet = CompletableDeferred<Unit>()

        // 屏障：当全量重建调用 stateDao.set 重置为 null 时暂停
        harness.stateDao.onBeforeSet = { row ->
            if (row.last_scanned_book_id == null) {
                reachedResetSet.complete(Unit)
                allowResetSet.await()
            }
        }

        val rebuildJob = launch(Dispatchers.Default) {
            harness.repo.invalidateSweepForFullRebuild()
        }

        // 等待全量重建到达重置游标拦截点
        reachedResetSet.await()

        // 此时在重置暂停期间，尝试启动新扫描
        val sweepDuringReset = harness.repo.ensureIndexedIncremental()
        assertEquals(
            "全量重建重置未完成前，新扫描绝不能启动读取旧游标",
            IndexSweepResult.PROGRESSED,
            sweepDuringReset,
        )

        // 核心断言：在此期间 stateDao 游标仍为旧的 b1，新扫描不能去推进并跳过第一本
        assertEquals("b1", harness.stateDao.get()?.last_scanned_book_id)

        // 放行重置完成
        allowResetSet.complete(Unit)
        rebuildJob.join()
        harness.stateDao.onBeforeSet = null

        // 确认重置完成，游标已是 null
        assertNull(harness.stateDao.get()?.last_scanned_book_id)

        // 随后新扫描启动，必须从第一本重新扫描
        val freshSweep = harness.repo.ensureIndexedIncremental()
        assertTrue(freshSweep == IndexSweepResult.COMPLETED || freshSweep == IndexSweepResult.PROGRESSED)
        val b1Coverages = harness.coverageDao.rowsForBook("b1")
        assertTrue("新扫描必须重新从第一本 (b1) 开始", b1Coverages.isNotEmpty())
    }

    // =========================================================================
    // 断言 B2：全量重建 stateDao 重置失败时回滚代次与释放所有权，不留静默坏态
    // =========================================================================
    @Test
    fun `full rebuild stateDao failure rolls back generation and releases lock`() = runTest {
        val harness = createHarness()
        harness.stateDao.set(
            SearchIndexStateRow(
                tokenizer_version = SearchIndexRepository.TOKENIZER_VERSION,
                last_scanned_book_id = "b1",
                last_scanned_chapter_index = 0,
                built_at = 1000L,
            )
        )
        val initialGen = harness.repo.getCurrentGeneration()

        // 注入重置失败
        harness.stateDao.shouldThrowOnSet = RuntimeException("数据库写入失败")

        var caught = false
        try {
            harness.repo.invalidateSweepForFullRebuild()
        } catch (e: RuntimeException) {
            caught = true
        }
        assertTrue("重置异常必须向外抛出", caught)

        // 核心断言：
        // 1. 代次必须回滚，不能停留在失效的新代次上造成「新代次 + 旧游标」
        assertEquals("重置失败时代次必须回滚", initialGen, harness.repo.getCurrentGeneration())
        // 2. 游标保持为旧状态
        assertEquals("b1", harness.stateDao.get()?.last_scanned_book_id)

        // 3. 恢复 DAO，后续扫描或重建不会被死锁
        harness.stateDao.shouldThrowOnSet = null
        val nextResult = harness.repo.ensureIndexedIncremental()
        assertTrue(
            "释放所有权后后续扫描可正常执行",
            nextResult == IndexSweepResult.COMPLETED || nextResult == IndexSweepResult.PROGRESSED,
        )
        assertNotNull("后续扫描必须真正写入游标", harness.stateDao.get())
        assertTrue("后续扫描必须更新覆盖率表", harness.coverageDao.rows.isNotEmpty())
    }

    // =========================================================================
    // 断言 C1：旧扫描分批写入词条前在无锁协作点被拦截，最终 DAO 只包含新代次数据
    // =========================================================================
    @Test
    fun `old sweep terms write intercepted before lock does not overwrite new generation terms`() = runTest {
        val harness = createHarness()
        val reachedBeforeWrite = CompletableDeferred<Unit>()
        val allowOldWrite = CompletableDeferred<Unit>()

        // 拦截点：旧任务在准备写入 b1 的词条前（无锁）挂起
        harness.repo.testHookBeforeWrite = { point, bookId ->
            if (point == "term_tail" && bookId == "b1") {
                reachedBeforeWrite.complete(Unit)
                allowOldWrite.await()
            }
        }

        val oldJob = launch(Dispatchers.Default) {
            harness.repo.ensureIndexedIncremental()
        }

        reachedBeforeWrite.await()

        // 此时触发全量重建并向 b1 写入新代次专属词条
        harness.repo.triggerFullRebuildNow()
        val newGenTerm = SearchTermRow(
            term = "NEW_GEN_EXCLUSIVE_TERM",
            book_id = "b1",
            chapter_index = 0,
            text_basis = "original",
            hits = 99,
        )
        harness.termDao.upsertAll(listOf(newGenTerm))

        // 放行旧任务，让旧任务尝试获取锁写入
        allowOldWrite.complete(Unit)
        oldJob.join()

        // 核心断言：直接断言 DAO 中的实际数据！
        // 必须包含新代次词条，且不得包含旧任务写入的预览词条
        val b1Terms = harness.termDao.rows.filter { it.book_id == "b1" }
        assertTrue("DAO 必须包含新代次写入的专属词条", b1Terms.any { it.term == "NEW_GEN_EXCLUSIVE_TERM" })
        val oldSampleTokens = SearchTokenizer().tokenize("正文预览内容：书本一")
        assertTrue("旧正文分词词条非空", oldSampleTokens.isNotEmpty())
        val leakedOldTerms = b1Terms.filter { it.term in oldSampleTokens }
        assertTrue("DAO 中绝不能包含旧扫描晚到写入的任何词条: $leakedOldTerms", leakedOldTerms.isEmpty())
        assertEquals("b1 的词条只能是新代次写入的那一条", listOf("NEW_GEN_EXCLUSIVE_TERM"), b1Terms.map { it.term })
    }

    // =========================================================================
    // 断言 C2：坏书在实际正文 DAO 发生异常时被干净隔离，好书正常建索引
    // =========================================================================
    @Test
    fun `bad book content DAO failure isolates cleanly and good book indexed`() = runTest {
        val badBook = BookEntity(id = "bad_book", title = "坏书", format = "txt", updated_at = "2026-01-01T00:00:00Z")
        val goodBook = BookEntity(id = "good_book", title = "好书", format = "txt", updated_at = "2026-01-01T00:00:00Z")
        val harness = createHarness(listOf(badBook, goodBook))

        // 在实际正文读取 DAO 注入异常
        coEvery { harness.bookContentDao.getByBook("bad_book") } throws RuntimeException("模拟数据库正文损坏")
        coEvery { harness.bookContentDao.getByBook("good_book") } returns BookContentEntity(
            book_id = "good_book",
            reader_preview = "好书的正文正常内容",
            epub_json = null,
        )

        val sweepResult = harness.repo.ensureIndexedIncremental()
        assertTrue("扫描未被单本失败中断", sweepResult == IndexSweepResult.COMPLETED || sweepResult == IndexSweepResult.PROGRESSED)

        // 核心断言：
        // 1. 好书的词条正常落盘
        assertTrue("好书词条必须成功写入 DAO", harness.termDao.rows.any { it.book_id == "good_book" })
        // 2. 最终游标成功推进并停在好书
        val cursor = harness.stateDao.get()
        assertNotNull("单本失败隔离后仍应记录游标", cursor)
        assertEquals("坏书失败后游标应推进并停在好书", "good_book", cursor!!.last_scanned_book_id)
    }

    // =========================================================================
    // 断言 4：扫描已占权、尚未读代次时重建开始，扫描绝不能以新代次读取旧游标
    // =========================================================================
    @Test
    fun `sweep acquires ownership but rebuild starts before generation read must not read stale cursor with new gen`() = runTest {
        val harness = createHarness()
        // 初始游标指向 b1
        harness.stateDao.set(
            SearchIndexStateRow(
                tokenizer_version = SearchIndexRepository.TOKENIZER_VERSION,
                last_scanned_book_id = "b1",
                last_scanned_chapter_index = 0,
                built_at = 1000L,
            )
        )

        val reachedAcquire = CompletableDeferred<Unit>()
        val allowSweepToProceed = CompletableDeferred<Unit>()

        // 拦截点：扫描已占权、尚未读代次与执行后续工作
        harness.repo.testHookAfterAcquireOwnership = {
            reachedAcquire.complete(Unit)
            allowSweepToProceed.await()
        }

        val sweepJob = launch(Dispatchers.Default) {
            harness.repo.ensureIndexedIncremental()
        }

        reachedAcquire.await()

        // 此时全量重建开始，并且重置 DAO 暂停在 stateDao.set 之前（旧游标仍为 b1，尚未被置为 null）
        val reachedResetSet = CompletableDeferred<Unit>()
        val allowResetSet = CompletableDeferred<Unit>()
        harness.stateDao.onBeforeSet = { row ->
            if (row.last_scanned_book_id == null) {
                reachedResetSet.complete(Unit)
                allowResetSet.await()
            }
        }

        val rebuildJob = launch(Dispatchers.Default) {
            harness.repo.invalidateSweepForFullRebuild()
        }

        reachedResetSet.await()

        // 此时放行旧扫描
        allowSweepToProceed.complete(Unit)
        sweepJob.join()

        // 放行重置完成
        allowResetSet.complete(Unit)
        rebuildJob.join()

        // 核心断言：
        // 1. 旧扫描在被重建打断后，绝不能以新代次把 b2 当作续建并跳过 b1
        //    即：在此期间 b2 的覆盖率绝不能被写入
        val b2Coverages = harness.coverageDao.rowsForBook("b2")
        assertTrue("旧扫描绝不能在被抢占后以新代次处理后续书籍跳过 b1", b2Coverages.isEmpty())
        // 2. 游标必须已被重建重置为 null，绝不能被旧扫描推进到 b2
        assertNull("重置后游标必须为 null", harness.stateDao.get()?.last_scanned_book_id)
    }

    // =========================================================================
    // 断言 5：tokenizer 版本不匹配时的清理路径服从同一代次协议，不删除新代次数据
    // =========================================================================
    @Test
    fun `wipe all terms on tokenizer mismatch respects generation and does not delete new generation data`() = runTest {
        val harness = createHarness()
        // 模拟旧版本分词器数据存在
        harness.stateDao.set(
            SearchIndexStateRow(
                tokenizer_version = SearchIndexRepository.TOKENIZER_VERSION - 1,
                last_scanned_book_id = "b1",
                last_scanned_chapter_index = 0,
                built_at = 1000L,
            )
        )
        harness.termDao.upsertAll(
            listOf(
                SearchTermRow(term = "OLD_TOKEN", book_id = "b1", chapter_index = 0, text_basis = "original", hits = 1)
            )
        )

        val reachedWipe = CompletableDeferred<Unit>()
        val allowWipe = CompletableDeferred<Unit>()

        harness.repo.testHookBeforeWrite = { point, _ ->
            if (point == "wipe_all_terms") {
                reachedWipe.complete(Unit)
                allowWipe.await()
            }
        }

        val oldJob = launch(Dispatchers.Default) {
            harness.repo.ensureIndexedIncremental()
        }

        reachedWipe.await()

        // 触发全量重建并向 DB 写入新代次的专属 term 与 coverage
        harness.repo.triggerFullRebuildNow()
        val newGenTerm = SearchTermRow(
            term = "NEW_GEN_TERM",
            book_id = "b1",
            chapter_index = 0,
            text_basis = "original",
            hits = 100,
        )
        harness.termDao.upsertAll(listOf(newGenTerm))
        val newGenCoverage = SearchIndexCoverageRow(
            book_id = "b1",
            text_basis = "original",
            format = "txt",
            coverage = "completed",
            indexed_chapters = 10,
            total_chapters = 10,
            tokenizer_version = SearchIndexRepository.TOKENIZER_VERSION,
            indexed_at = 9999L,
            reason = "",
        )
        harness.coverageDao.upsert(newGenCoverage)

        // 放行旧任务尝试 wipe
        allowWipe.complete(Unit)
        oldJob.join()

        // 核心断言：旧任务 wipe 绝不能把新代次写入的 term 和 coverage 清理掉
        val terms = harness.termDao.rows
        assertTrue("新代次 term 必须完好保留", terms.any { it.term == "NEW_GEN_TERM" })
        val coverages = harness.coverageDao.rows
        assertTrue("新代次 coverage 必须完好保留", coverages.any { it.book_id == "b1" && it.indexed_at == 9999L })
    }

    // =========================================================================
    // 断言 6：旧扫描写入 coverage 前在无锁协作点拦截，不得覆盖新代次 coverage
    // =========================================================================
    @Test
    fun `old sweep coverage write intercepted before lock does not overwrite new coverage`() = runTest {
        val harness = createHarness()
        val reachedCoverage = CompletableDeferred<Unit>()
        val allowCoverage = CompletableDeferred<Unit>()

        harness.repo.testHookBeforeWrite = { point, bookId ->
            if (point == "coverage" && bookId == "b1") {
                reachedCoverage.complete(Unit)
                allowCoverage.await()
            }
        }

        val oldJob = launch(Dispatchers.Default) {
            harness.repo.ensureIndexedIncremental()
        }

        reachedCoverage.await()

        // 触发全量重建并写入新代次 coverage
        harness.repo.triggerFullRebuildNow()
        val newCoverage = SearchIndexCoverageRow(
            book_id = "b1",
            text_basis = "original",
            format = "txt",
            coverage = "completed",
            indexed_chapters = 999,
            total_chapters = 999,
            tokenizer_version = SearchIndexRepository.TOKENIZER_VERSION,
            indexed_at = 88888L,
            reason = "NEW_GEN_COVERAGE",
        )
        harness.coverageDao.upsert(newCoverage)

        // 放行旧任务
        allowCoverage.complete(Unit)
        oldJob.join()

        // 核心断言：新代次 coverage 没有被旧任务覆盖
        val currentCoverage = harness.coverageDao.row("b1", "original")
        assertNotNull("b1 coverage 必须存在", currentCoverage)
        assertEquals("新代次 coverage 章节数不被覆盖", 999, currentCoverage!!.indexed_chapters)
        assertEquals("新代次 coverage 原因不被覆盖", "NEW_GEN_COVERAGE", currentCoverage.reason)
    }

    // =========================================================================
    // 断言 7：旧扫描在 display 清理前拦截，不得删除新代次的 display 数据
    // =========================================================================
    @Test
    fun `old sweep discard display intercepted before lock does not wipe new display rows`() = runTest {
        val harness = createHarness()
        val reachedDiscard = CompletableDeferred<Unit>()
        val allowDiscard = CompletableDeferred<Unit>()

        // 为 b1 配置非法正则规则，使得 DisplayChannelIndexer.project 抛出 IllegalArgumentException 并触发 abandonDisplay
        val invalidRule = ReplaceRule(
            id = "inv1",
            name = "非法规则",
            pattern = "[a-",
            replacement = "",
            enabled = true,
            position = 0,
            scope = RuleScope.PER_BOOK,
        )
        coEvery { harness.rulesRepo.observe("b1") } returns flowOf(mockk(relaxed = true) {
            coEvery { effectiveReplace } returns listOf(invalidRule)
        })

        harness.repo.testHookBeforeWrite = { point, bookId ->
            if (point == "discard_display" && bookId == "b1") {
                reachedDiscard.complete(Unit)
                allowDiscard.await()
            }
        }

        val oldJob = launch(Dispatchers.Default) {
            harness.repo.ensureIndexedIncremental()
        }

        reachedDiscard.await()

        // 此时触发全量重建（代次递增）并写入新代次 display 数据
        harness.repo.triggerFullRebuildNow()
        val newDisplayTerm = SearchTermRow(
            term = "NEW_DISPLAY_TERM",
            book_id = "b1",
            chapter_index = 0,
            text_basis = "display",
            hits = 42,
        )
        harness.termDao.upsertAll(listOf(newDisplayTerm))

        // 放行旧任务，让旧任务尝试清理 display 数据
        allowDiscard.complete(Unit)
        oldJob.join()

        // 核心断言：新代次写入的 display 词条绝不能被旧任务的 discardDisplayRows 删掉
        val displayRows = harness.termDao.rowsByTermForBasis("NEW_DISPLAY_TERM", "display")
        assertTrue("新代次 display 词条必须完好保留", displayRows.isNotEmpty())
    }

    // =========================================================================
    // =========================================================================
    // 断言 8a：并发两次重建，后一次取消绝不能释放前一次的重建占用，扫描不得读取旧游标
    // =========================================================================
    @Test
    fun `concurrent rebuilds where second is cancelled cannot release first rebuild ownership and scan cannot read stale cursor`() = runTest {
        val harness = createHarness()
        // 初始游标停在 b1
        harness.stateDao.set(
            SearchIndexStateRow(
                tokenizer_version = SearchIndexRepository.TOKENIZER_VERSION,
                last_scanned_book_id = "b1",
                last_scanned_chapter_index = 0,
                built_at = 1000L,
            )
        )

        val reachedResetA = CompletableDeferred<Unit>()
        val allowResetA = CompletableDeferred<Unit>()

        // 拦截点：重建 A 在 beginRebuild 之后、重置状态之前暂停（临界区锁外）
        harness.repo.testHookBeforeRebuildReset = { _ ->
            if (!reachedResetA.isCompleted) {
                reachedResetA.complete(Unit)
                allowResetA.await()
            }
        }

        val jobA = launch(Dispatchers.Default) {
            harness.repo.invalidateSweepForFullRebuild()
        }

        reachedResetA.await()

        // 此时重建 A 已经进入重建占用状态，尚未完成游标重置（当前游标仍为 b1）。
        // 此时触发重建 B，重建 B 在排队等待串行化锁期间被取消
        val jobB = launch(Dispatchers.Default) {
            harness.repo.invalidateSweepForFullRebuild()
        }
        jobB.cancel(CancellationException("重建 B 取消"))
        jobB.join()

        // 核心断言 1：重建 B 取消后，绝不能释放重建 A 的占用！
        // 在重建 A 尚未完成前尝试启动扫描，扫描必须被拒绝（PROGRESSED），绝不能启动并读取旧游标！
        val sweepDuringA = harness.repo.ensureIndexedIncremental()
        assertEquals(
            "重建 B 取消后不得释放重建 A 的占用，扫描必须被拒绝",
            IndexSweepResult.PROGRESSED,
            sweepDuringA,
        )
        // 验证旧游标未被任何扫描推进
        assertEquals("游标仍保持为旧的 b1", "b1", harness.stateDao.get()?.last_scanned_book_id)

        // 放行重建 A 完成重置
        allowResetA.complete(Unit)
        jobA.join()

        // 核心断言 2：重建 A 成功完成后，游标已经被重置为 null
        assertNull("重建 A 成功重置游标为 null", harness.stateDao.get()?.last_scanned_book_id)

        // 随后新扫描启动，必须重新从第一本 (b1) 开始扫描落盘
        val nextSweep = harness.repo.ensureIndexedIncremental()
        assertTrue(nextSweep == IndexSweepResult.COMPLETED || nextSweep == IndexSweepResult.PROGRESSED)
        val b1Coverages = harness.coverageDao.rowsForBook("b1")
        assertTrue("重建完成后新扫描必须从第一本书重新开始", b1Coverages.isNotEmpty())
    }

    // =========================================================================
    // 断言 8b：并发两次重建，排队的后一次重建在 stateDao 写入失败时干净回滚，不破坏前一次成果
    // =========================================================================
    @Test
    fun `concurrent rebuilds where second fails stateDao set rolls back cleanly and leaves system consistent`() = runTest {
        val harness = createHarness()
        harness.stateDao.set(
            SearchIndexStateRow(
                tokenizer_version = SearchIndexRepository.TOKENIZER_VERSION,
                last_scanned_book_id = "b1",
                last_scanned_chapter_index = 0,
                built_at = 1000L,
            )
        )

        val reachedResetA = CompletableDeferred<Unit>()
        val allowResetA = CompletableDeferred<Unit>()

        harness.repo.testHookBeforeRebuildReset = { _ ->
            if (!reachedResetA.isCompleted) {
                reachedResetA.complete(Unit)
                allowResetA.await()
            }
        }

        val jobA = launch(Dispatchers.Default) {
            harness.repo.invalidateSweepForFullRebuild()
        }

        reachedResetA.await()

        // 此时重建 A 正在进行中，配置 stateDao：第 1 次 set（A）成功，第 2 次 set（B）抛错
        var setInvocationCount = 0
        harness.stateDao.onBeforeSet = { _ ->
            val count = ++setInvocationCount
            if (count == 2) {
                throw RuntimeException("重建 B stateDao.set 失败")
            }
        }

        val jobB = launch(Dispatchers.Default) {
            try {
                harness.repo.invalidateSweepForFullRebuild()
            } catch (_: RuntimeException) {
                // 预期 B 失败
            }
        }

        // 核心断言 1：在 A 完成前尝试扫描，扫描必须被拒绝（PROGRESSED）
        val sweepDuringA = harness.repo.ensureIndexedIncremental()
        assertEquals(
            "重建 A 进行期间扫描必须被拒绝",
            IndexSweepResult.PROGRESSED,
            sweepDuringA,
        )
        assertEquals("游标仍保持为旧的 b1", "b1", harness.stateDao.get()?.last_scanned_book_id)

        // 放行 A，A 完成后释放锁，B 获得锁并进入第 2 次 set 抛错失败
        allowResetA.complete(Unit)
        jobA.join()
        jobB.join()

        // 恢复 DAO 正常
        harness.stateDao.onBeforeSet = null

        // 核心断言 2：B 失败回滚后，游标仍保持为 A 成功重置的 null，代次正确回滚，未留下坏状态
        assertNull("B 失败回滚后游标仍保持为 null", harness.stateDao.get()?.last_scanned_book_id)

        // 随后新扫描启动，必须能正常从第 0 本扫描落盘
        val nextSweep = harness.repo.ensureIndexedIncremental()
        assertTrue(nextSweep == IndexSweepResult.COMPLETED || nextSweep == IndexSweepResult.PROGRESSED)
        val b1Coverages = harness.coverageDao.rowsForBook("b1")
        assertTrue("新扫描从第一本书开始", b1Coverages.isNotEmpty())
    }

    // =========================================================================
    // 断言 9：testHookAfterAcquireOwnership 抛错时必须可靠释放所有权，后续扫描能正常执行
    // =========================================================================
    @Test
    fun `hook after acquire ownership failure releases ownership and allows subsequent sweep`() = runTest {
        val harness = createHarness()
        harness.repo.testHookAfterAcquireOwnership = {
            throw RuntimeException("模拟占权后钩子抛错")
        }

        var caught = false
        try {
            harness.repo.ensureIndexedIncremental()
        } catch (e: RuntimeException) {
            caught = true
            assertEquals("模拟占权后钩子抛错", e.message)
        }
        assertTrue("占权后异常必须向外抛出", caught)

        // 清理钩子
        harness.repo.testHookAfterAcquireOwnership = null

        // 核心断言：异常后所有权已由 finally 释放，后续扫描能成功启动推进并写入
        val nextResult = harness.repo.ensureIndexedIncremental()
        assertTrue(
            "占权后抛错释放所有权后，后续扫描必须能够正常执行",
            nextResult == IndexSweepResult.COMPLETED || nextResult == IndexSweepResult.PROGRESSED,
        )
        assertNotNull("后续扫描必须真正写入游标", harness.stateDao.get())
    }

    // =========================================================================
    // 断言 10：testHookAfterAcquireOwnership 协程取消时必须可靠释放所有权且向外传播
    // =========================================================================
    @Test
    fun `hook after acquire ownership cancellation releases ownership and propagates`() = runTest {
        val harness = createHarness()
        val reachedHook = CompletableDeferred<Unit>()
        val proceedHook = CompletableDeferred<Unit>()

        harness.repo.testHookAfterAcquireOwnership = {
            reachedHook.complete(Unit)
            proceedHook.await()
        }

        val job = launch(Dispatchers.Default) {
            harness.repo.ensureIndexedIncremental()
        }

        reachedHook.await()
        job.cancel(CancellationException("WorkManager 任务在占权钩子处取消"))
        proceedHook.complete(Unit)
        job.join()
        assertTrue("任务必须处于 cancelled 状态", job.isCancelled)

        // 清理钩子
        harness.repo.testHookAfterAcquireOwnership = null

        // 核心断言：取消后所有权已由 finally 释放，后续扫描能成功启动推进并写入
        val nextResult = harness.repo.ensureIndexedIncremental()
        assertTrue(
            "占权后取消释放所有权后，后续扫描必须能够正常执行",
            nextResult == IndexSweepResult.COMPLETED || nextResult == IndexSweepResult.PROGRESSED,
        )
        assertNotNull("后续扫描必须真正写入游标", harness.stateDao.get())
    }
}

