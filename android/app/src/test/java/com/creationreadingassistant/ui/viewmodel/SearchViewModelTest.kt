package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.data.settings.SearchHistoryStore
import com.creationreadingassistant.feature.search.SearchCoverageState
import com.creationreadingassistant.feature.search.SearchHit
import com.creationreadingassistant.feature.search.SearchTextBasis
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val bookRepository = mockk<BookRepository>()
    private val inspirationRepository = mockk<InspirationRepository>()
    private val noteRepository = mockk<NoteRepository>()
    private val searchIndexRepository = mockk<SearchIndexRepository>()
    private val historyStore = mockk<SearchHistoryStore>()
    private lateinit var vm: SearchViewModel
    private val testScheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        every { historyStore.history } returns MutableStateFlow(emptyList())
        coEvery { noteRepository.searchHighlights(any()) } returns emptyList()
        coEvery { bookRepository.getById(any()) } returns null
        coEvery { bookRepository.getByIds(any()) } returns emptyList()
        coEvery { searchIndexRepository.searchContent(any(), any(), any()) } returns emptyList()
        coEvery { searchIndexRepository.countIndexedBooks() } returns 0L
        vm = SearchViewModel(
            bookRepository,
            inspirationRepository,
            noteRepository,
            searchIndexRepository,
            historyStore,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private suspend fun awaitResults(predicate: (SearchResults) -> Boolean): SearchResults =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { vm.results.first(predicate) }
        }

    @Test
    fun `blank query should clear results`() = runTest(mainDispatcher) {
        vm.search("")
        assertEquals(0, vm.results.value.books.size + vm.results.value.inspirations.size + vm.results.value.notes.size)
        assertFalse(vm.loading.value)
    }

    @Test
    fun `valid query should return matching books`() = runTest(mainDispatcher) {
        val book = BookEntity(id = "b1", title = "三体", format = "epub", updated_at = "now")
        coEvery { bookRepository.search("三体") } returns listOf(book)
        coEvery { inspirationRepository.search("三体") } returns emptyList()
        coEvery { noteRepository.searchNotes("三体") } returns emptyList()

        vm.search("三体")
        advanceTimeBy(400) // 等 debounce
        val result = awaitResults { it.books.isNotEmpty() }
        assertEquals(1, result.books.size)
        assertEquals("三体", result.books[0].title)
        assertFalse(vm.loading.value)
    }

    @Test
    fun `concurrent search across three tables`() = runTest(mainDispatcher) {
        coEvery { bookRepository.search("关键词") } returns listOf(
            BookEntity(id = "b1", title = "书A", format = "txt", updated_at = "n"),
        )
        coEvery { inspirationRepository.search("关键词") } returns listOf(
            InspirationEntity(id = "i1", title = "灵感A", body = "...", type = "note", status = "inbox", created_at = "n", updated_at = "n"),
        )
        coEvery { noteRepository.searchNotes("关键词") } returns listOf(
            NoteEntity(id = "n1", title = "笔记A", body = "原文", created_at = "n", updated_at = "n"),
        )

        vm.search("关键词")
        advanceTimeBy(400)
        val result = awaitResults { it.books.isNotEmpty() }
        assertEquals(1, result.books.size)
        assertEquals(1, result.inspirations.size)
        assertEquals(1, result.notes.size)
    }

    // ── R2-S1.3：全文索引接线（此前 searchContent 是死 seam） ──────────

    private fun hit(
        bookId: String,
        coverage: SearchCoverageState?,
        chapter: Int = 3,
    ) = SearchHit(
        bookId = bookId,
        score = 7,
        textBasis = SearchTextBasis.ORIGINAL,
        indexChapterIndex = chapter,
        charOffset = 120,
        matchLength = 2,
        coverage = coverage,
        coverageReason = null,
    )

    @Test
    fun `content hits are surfaced with their book titles`() = runTest(mainDispatcher) {
        coEvery { bookRepository.search("世界") } returns emptyList()
        coEvery { inspirationRepository.search("世界") } returns emptyList()
        coEvery { noteRepository.searchNotes("世界") } returns emptyList()
        coEvery { searchIndexRepository.countIndexedBooks() } returns 1L
        coEvery { searchIndexRepository.searchContent(any(), any(), any()) } returns listOf(
            hit("b9", SearchCoverageState.FULL),
        )
        coEvery { bookRepository.getByIds(any()) } returns listOf(
            BookEntity(id = "b9", title = "某书", format = "txt", updated_at = "n"),
        )

        vm.search("世界")
        advanceTimeBy(400)
        val result = awaitResults { it.contentHits.isNotEmpty() }
        assertEquals(1, result.contentHits.size)
        assertEquals("b9", result.contentHits[0].bookId)
        assertEquals("正文命中且元数据没命中的书要补书名", "某书", result.bookTitles["b9"])
        assertNull("全量覆盖不应产生提示", result.notice)
    }

    @Test
    fun `unbuilt index is reported instead of silently returning metadata only`() = runTest(mainDispatcher) {
        coEvery { bookRepository.search(any()) } returns emptyList()
        coEvery { inspirationRepository.search(any()) } returns emptyList()
        coEvery { noteRepository.searchNotes(any()) } returns emptyList()
        coEvery { searchIndexRepository.countIndexedBooks() } returns 0L

        vm.search("世界")
        advanceTimeBy(400)
        val result = awaitResults { it.notice != null }
        assertEquals(SearchNoticeKind.INDEX_NOT_BUILT, result.notice?.kind)
    }

    @Test
    fun `incomplete coverage counts only known non full states`() = runTest(mainDispatcher) {
        coEvery { bookRepository.search(any()) } returns emptyList()
        coEvery { inspirationRepository.search(any()) } returns emptyList()
        coEvery { noteRepository.searchNotes(any()) } returns emptyList()
        coEvery { searchIndexRepository.countIndexedBooks() } returns 5L
        coEvery { searchIndexRepository.searchContent(any(), any(), any()) } returns listOf(
            hit("full", SearchCoverageState.FULL),
            hit("partial", SearchCoverageState.PREVIEW_ONLY),
            hit("unknown", null), // 覆盖未知不得被当成「未完成」虚报
        )

        vm.search("世界")
        advanceTimeBy(400)
        val result = awaitResults { it.contentHits.isNotEmpty() }
        assertEquals(SearchNoticeKind.COVERAGE_INCOMPLETE, result.notice?.kind)
        assertEquals(1, result.notice?.affectedBooks)
    }

    @Test
    fun `books matched both in metadata and content are flagged for dedup`() = runTest(mainDispatcher) {
        coEvery { bookRepository.search("世界") } returns listOf(
            BookEntity(id = "b1", title = "世界史", format = "txt", updated_at = "n"),
        )
        coEvery { inspirationRepository.search("世界") } returns emptyList()
        coEvery { noteRepository.searchNotes("世界") } returns emptyList()
        coEvery { searchIndexRepository.countIndexedBooks() } returns 1L
        coEvery { searchIndexRepository.searchContent(any(), any(), any()) } returns listOf(
            hit("b1", SearchCoverageState.FULL),
        )

        vm.search("世界")
        advanceTimeBy(400)
        val result = awaitResults { it.contentHits.isNotEmpty() }
        assertTrue("同一本书的两种命中要标记出来，供「全部」页去重", result.booksAlsoMatchedInContent.contains("b1"))
    }

    @Test
    fun `content rows keep their title even when the book also matched metadata`() = runTest(mainDispatcher) {
        // 回归：neededIds 曾只取「正文命中 - 元数据命中」的差集，
        // 导致双命中的书在「全部」页里只剩正文行、却又拿不到书名，退化成显示裸 bookId。
        coEvery { bookRepository.search("世界") } returns listOf(
            BookEntity(id = "b1", title = "世界史", format = "txt", updated_at = "n"),
        )
        coEvery { inspirationRepository.search("世界") } returns emptyList()
        coEvery { noteRepository.searchNotes("世界") } returns emptyList()
        coEvery { searchIndexRepository.countIndexedBooks() } returns 1L
        coEvery { searchIndexRepository.searchContent(any(), any(), any()) } returns listOf(
            hit("b1", SearchCoverageState.FULL),
        )
        coEvery { bookRepository.getByIds(any()) } returns listOf(
            BookEntity(id = "b1", title = "世界史", format = "txt", updated_at = "n"),
        )

        vm.search("世界")
        advanceTimeBy(400)
        val result = awaitResults { it.contentHits.isNotEmpty() }
        assertEquals("双命中的书在「全部」页只以正文行出现，书名必须取得到", "世界史", result.bookTitles["b1"])
    }

    @Test
    fun `index failure is reported while metadata results remain available`() = runTest(mainDispatcher) {
        coEvery { bookRepository.search("世界") } returns listOf(
            BookEntity(id = "b1", title = "世界史", format = "txt", updated_at = "n"),
        )
        coEvery { inspirationRepository.search("世界") } returns emptyList()
        coEvery { noteRepository.searchNotes("世界") } returns emptyList()
        coEvery { searchIndexRepository.searchContent(any(), any(), any()) } throws RuntimeException("boom")

        vm.search("世界")
        advanceTimeBy(400)
        val result = awaitResults { it.books.isNotEmpty() }
        assertEquals("索引挂了也不能让元数据搜索一起失效", 1, result.books.size)
        assertTrue(result.contentHits.isEmpty())
        assertEquals(SearchNoticeKind.CONTENT_SEARCH_FAILED, result.notice?.kind)
        assertFalse(vm.loading.value)
        coVerify(exactly = 0) { searchIndexRepository.countIndexedBooks() }
    }

    @Test
    fun `successful search with no content hits is not reported as a failure`() = runTest(mainDispatcher) {
        stubQuery("zero", listOf(book("b1", "测试 TXT")))
        coEvery { searchIndexRepository.countIndexedBooks() } returns 1L

        vm.search("zero")
        advanceUntilIdle()

        assertEquals(listOf("b1"), vm.results.value.books.map { it.id })
        assertTrue(vm.results.value.contentHits.isEmpty())
        assertNull(vm.results.value.notice)
        assertFalse(vm.loading.value)
    }

    @Test
    fun `content query cancellation does not publish results or a failure notice`() = runTest(mainDispatcher) {
        stubQuery("cancelled", listOf(book("b1", "测试 TXT")))
        coEvery { searchIndexRepository.searchContent(any(), any(), any()) } throws
            CancellationException("cancelled content query")

        vm.search("cancelled")
        advanceUntilIdle()

        assertTrue(vm.results.value.books.isEmpty())
        assertNull(vm.results.value.notice)
        coVerify(exactly = 0) { searchIndexRepository.countIndexedBooks() }

        vm.search("")
        assertFalse(vm.loading.value)
    }

    // ── R2-S1.6：双通道口径（原文 + 替换显示文都搜，命中标注来源） ──────────

    @Test
    fun `search requests both original and display text bases`() = runTest(mainDispatcher) {
        coEvery { bookRepository.search("世界") } returns emptyList()
        coEvery { inspirationRepository.search("世界") } returns emptyList()
        coEvery { noteRepository.searchNotes("世界") } returns emptyList()
        coEvery { searchIndexRepository.countIndexedBooks() } returns 1L
        val bases = slot<Set<SearchTextBasis>>()
        coEvery { searchIndexRepository.searchContent(any(), capture(bases), any()) } returns emptyList()

        vm.search("世界")
        advanceTimeBy(400)
        advanceUntilIdle()

        coVerify(exactly = 1) { searchIndexRepository.searchContent(any(), any(), any()) }
        assertTrue("searchContent 必须同时检索 original + display 两种文本基准", bases.isCaptured)
        assertEquals(setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY), bases.captured)
    }

    // ── R2-S1.4：命中 → 精确到达 ───────────────────────────────────────

    @Test
    fun `precise offset is resolved on demand and passed through`() = runTest(mainDispatcher) {
        val target = hit("b1", SearchCoverageState.FULL)
        coEvery { searchIndexRepository.resolveLegacyOffset(target) } returns 4242
        assertEquals(4242, vm.resolvePreciseOffset(target))
    }

    @Test
    fun `unresolvable hit yields null so the caller can fall back to opening the book`() =
        runTest(mainDispatcher) {
            val target = hit("b1", SearchCoverageState.FULL)
            coEvery { searchIndexRepository.resolveLegacyOffset(target) } returns null
            assertNull("换算不出偏移必须返回 null，让调用方降级，而不是伪造 0", vm.resolvePreciseOffset(target))
        }

    @Test
    fun `offset resolution failure never crashes the caller`() = runTest(mainDispatcher) {
        val target = hit("b1", SearchCoverageState.FULL)
        coEvery { searchIndexRepository.resolveLegacyOffset(target) } throws RuntimeException("boom")
        assertNull(vm.resolvePreciseOffset(target))
    }

    // ── R2-S1.5：命中 → 可返回（跨导航往返保留查询/分类/滚动） ──────────────

    @Test
    fun `query and tab state default and are retained on the view model`() = runTest(mainDispatcher) {
        assertEquals("VM 默认搜索词应为空", "", vm.queryState.value)
        assertEquals("VM 默认分类应为全部", "all", vm.tabState.value)
        // 模拟在搜索页输入并切换分类
        vm.queryState.value = "世界"
        vm.tabState.value = "content"
        assertEquals("世界", vm.queryState.value)
        assertEquals("content", vm.tabState.value)
    }

    @Test
    fun `list state is held on the view model so scroll survives navigation`() = runTest(mainDispatcher) {
        assertNotNull("LazyListState 须持有在 VM 中，跨搜索页→阅读器→返回的往返保留滚动位置", vm.listState)
    }

    @Test
    fun `repeating the same query does not re-run the search or re-trigger loading`() =
        runTest(mainDispatcher) {
            coEvery { bookRepository.search("世界") } returns emptyList()
            coEvery { inspirationRepository.search("世界") } returns emptyList()
            coEvery { noteRepository.searchNotes("世界") } returns emptyList()
            coEvery { searchIndexRepository.countIndexedBooks() } returns 0L

            vm.search("世界")
            advanceTimeBy(400)
            awaitResults { it.notice != null } // 等首次搜索落定
            assertFalse("首次搜索结束后不应仍转圈", vm.loading.value)

            // 模拟「从阅读器临时查阅返回搜索页」时 LaunchedEffect(query) 重新触发 search(同一词)
            vm.search("世界")
            assertFalse("返回搜索页不应重新进入 loading 闪烁", vm.loading.value)
            coVerify(exactly = 1) { bookRepository.search("世界") }
        }

    @Test
    fun `loading state transitions correctly`() = runTest(mainDispatcher) {
        coEvery { bookRepository.search(any()) } returns emptyList()
        coEvery { inspirationRepository.search(any()) } returns emptyList()
        coEvery { noteRepository.searchNotes(any()) } returns emptyList()

        vm.results.test {
            vm.search("test")
            advanceTimeBy(400)
            advanceUntilIdle()
            val final = expectMostRecentItem()
            assertTrue(final.books.isEmpty())
        }
    }

    // ── 取消与「上次已完成查询」的状态冲突（任务58-v1） ──────────────────

    /**
     * 非协作挂起点：所在 Job 被取消后仍保持挂起，只在显式 [release] 时恢复。
     *
     * 用它复现「在途仓储调用不响应取消 → 取消后仍然走完并写回结果/复位 loading」的真实
     * 危害；不依赖 sleep、随机时序或注入私有字段。
     */
    private class SearchGate<T> {
        private var continuation: Continuation<T>? = null

        val isWaiting: Boolean get() = continuation != null

        suspend fun await(): T = suspendCoroutine { continuation = it }

        fun release(value: T) {
            val pending = continuation ?: error("gate 当前没有挂起的等待者")
            continuation = null
            pending.resume(value)
        }
    }

    private fun stubQuery(query: String, books: List<BookEntity>) {
        coEvery { bookRepository.search(query) } returns books
        coEvery { inspirationRepository.search(query) } returns emptyList()
        coEvery { noteRepository.searchNotes(query) } returns emptyList()
    }

    private fun book(id: String, title: String) =
        BookEntity(id = id, title = title, format = "txt", updated_at = "n")

    @Test
    fun `switching back to a completed query cancels the in flight one and settles loading`() =
        runTest(mainDispatcher) {
            stubQuery("A", listOf(book("a1", "book A")))
            vm.search("A")
            advanceUntilIdle()
            advanceUntilIdle()
            assertEquals("book A", vm.results.value.books.firstOrNull()?.title)
            assertFalse(vm.loading.value)
            val gate = SearchGate<List<BookEntity>>()
            coEvery { bookRepository.search("B") } coAnswers { gate.await() }
            coEvery { inspirationRepository.search("B") } returns emptyList()
            coEvery { noteRepository.searchNotes("B") } returns emptyList()
            vm.search("B")
            advanceUntilIdle()
            try {
                assertTrue(vm.loading.value)
                vm.search("A")
                assertFalse("Returning to A must settle loading", vm.loading.value)
                assertEquals("book A", vm.results.value.books.firstOrNull()?.title)
                gate.release(listOf(book("b1", "book B")))
                advanceUntilIdle()
                assertEquals("book A", vm.results.value.books.firstOrNull()?.title)
                assertFalse(vm.loading.value)
            } finally {
                if (gate.isWaiting) gate.release(listOf(book("b1", "book B")))
            }
        }

    @Test
    fun `clearing the query while another search is in flight clears results and loading`() =
        runTest(mainDispatcher) {
            vm.search("")
            val gate = SearchGate<List<BookEntity>>()
            coEvery { bookRepository.search("B") } coAnswers { gate.await() }
            coEvery { inspirationRepository.search("B") } returns emptyList()
            coEvery { noteRepository.searchNotes("B") } returns emptyList()
            vm.search("B")
            advanceUntilIdle()
            try {
                assertTrue(vm.loading.value)
                vm.search("")
                assertFalse("Clearing must settle loading", vm.loading.value)
                assertTrue(vm.results.value.books.isEmpty())
                gate.release(listOf(book("b1", "book B")))
                advanceUntilIdle()
                assertTrue(vm.results.value.books.isEmpty())
                assertFalse(vm.loading.value)
            } finally {
                if (gate.isWaiting) gate.release(listOf(book("b1", "book B")))
            }
        }

    @Test
    fun `a superseded search cannot overwrite the newer query result or its loading`() =
        runTest(mainDispatcher) {
            val gateB = SearchGate<List<BookEntity>>()
            val gateC = SearchGate<List<BookEntity>>()
            coEvery { bookRepository.search("B") } coAnswers { gateB.await() }
            coEvery { bookRepository.search("C") } coAnswers { gateC.await() }
            for (q in listOf("B", "C")) {
                coEvery { inspirationRepository.search(q) } returns emptyList()
                coEvery { noteRepository.searchNotes(q) } returns emptyList()
            }
            vm.search("B")
            advanceUntilIdle()
            try {
                assertTrue(vm.loading.value)
                vm.search("C")
                advanceUntilIdle()
                assertTrue("C must remain pending at this checkpoint", vm.loading.value)
                gateB.release(listOf(book("b1", "book B")))
                advanceUntilIdle()
                assertTrue("B must not settle C loading", vm.loading.value)
                assertTrue("B must not publish stale results", vm.results.value.books.isEmpty())
                gateC.release(listOf(book("c1", "book C")))
                advanceUntilIdle()
                assertEquals("book C", vm.results.value.books.firstOrNull()?.title)
                assertFalse(vm.loading.value)
            } finally {
                if (gateB.isWaiting) gateB.release(listOf(book("b1", "book B")))
                if (gateC.isWaiting) gateC.release(listOf(book("c1", "book C")))
            }
        }
}
