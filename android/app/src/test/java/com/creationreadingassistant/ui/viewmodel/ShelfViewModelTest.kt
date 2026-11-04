package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.LibrarySourceAvailability
import com.creationreadingassistant.data.repository.BookCacheClearOutcome
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinator
import com.creationreadingassistant.data.repository.SyncRepository
import com.creationreadingassistant.data.repository.TaxonomyRepository
import com.creationreadingassistant.data.settings.ContinueReadingStore
import com.creationreadingassistant.data.settings.ImportHistoryStore
import com.creationreadingassistant.data.settings.SavedShelfFilter
import com.creationreadingassistant.data.settings.ShelfPrefs
import com.creationreadingassistant.feature.library.LibrarySourceIndex
import com.creationreadingassistant.feature.library.LibrarySourceRef
import com.creationreadingassistant.feature.library.ShelfImporter
import com.creationreadingassistant.feature.library.ShelfSourceUpdateChecker
import com.creationreadingassistant.feature.library.SourceRefreshResult
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.ui.screen.shelf.ShelfSortMode
import com.creationreadingassistant.ui.screen.shelf.ShelfStatusFilter
import com.creationreadingassistant.ui.screen.shelf.ShelfViewMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ShelfViewModel 会话状态机 / 列表聚合流 / CRUD 委托测试。
 *
 * 导入管线与书籍文件操作的细节在 ShelfImporterTest / ShelfBookActionsTest 覆盖，
 * 此处只验证 ViewModel 的编排行为（session 状态、流投影、launch 委托与回调）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShelfViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var repository: BookRepository
    private lateinit var bookDao: BookDao
    private lateinit var bookContentDao: BookContentDao
    private lateinit var bookFileDao: BookFileDao
    private lateinit var taxonomyRepository: TaxonomyRepository
    private lateinit var syncRepository: SyncRepository
    private lateinit var epubRepository: EpubRepository
    private lateinit var importHistoryStore: ImportHistoryStore
    private lateinit var shelfPrefs: ShelfPrefs
    private lateinit var savedViewsStore: com.creationreadingassistant.data.settings.ShelfSavedViewsStore
    private lateinit var continueReadingStore: ContinueReadingStore
    private lateinit var viewModeFlow: MutableStateFlow<String>
    private lateinit var sortModeFlow: MutableStateFlow<String>

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        context = mockk()
        repository = mockk {
            coEvery { restoreBook(any()) } returns Unit
            coEvery { deleteBook(any()) } returns Unit
            // SEAM（residue-safe-cleanup 窗口）：clearBookCache 返回类型由 Unit 改为
            // BookCacheClearOutcome，此处仅为编译适配，无断言语义变化。
            coEvery { clearBookCache(any()) } returns BookCacheClearOutcome.Cleared
            coEvery { updateBookInfo(any(), any(), any(), any()) } returns Unit
            coEvery { setReadingState(any(), any()) } returns Unit
        }
        bookDao = mockk()
        bookContentDao = mockk()
        bookFileDao = mockk()
        taxonomyRepository = mockk {
            every { observeTags() } returns flowOf(emptyList())
            every { observeCategories() } returns flowOf(emptyList())
            every { observeShelves() } returns flowOf(emptyList())
        }
        syncRepository = mockk()
        epubRepository = mockk { coEvery { repairMissingLocalFileSizes() } returns 1 }
        importHistoryStore = mockk { every { entries } returns MutableStateFlow(emptyList()) }
        viewModeFlow = MutableStateFlow("grid")
        sortModeFlow = MutableStateFlow("recent")
        savedViewsStore = mockk {
            every { savedFilters } returns MutableStateFlow(emptyList())
            coEvery { save(any()) } returns true
            coEvery { delete(any()) } returns Unit
        }
        shelfPrefs = mockk {
            every { viewMode } returns viewModeFlow
            every { sortMode } returns sortModeFlow
            every { recentSearches } returns MutableStateFlow(emptyList())
            every { privateSearch } returns MutableStateFlow(false)
            // 「新导入未查看」角标集合；ShelfViewModel 在 init 中订阅它
            every { newBookIds } returns MutableStateFlow(emptySet())
            coEvery { setViewMode(any()) } answers { viewModeFlow.value = it.invocation.args[0] as String; Unit }
            coEvery { setSortMode(any()) } answers { sortModeFlow.value = it.invocation.args[0] as String; Unit }
            coEvery { recordSearch(any()) } returns Unit
            coEvery { clearRecentSearches() } returns Unit
            coEvery { replaceRecentSearches(any()) } returns Unit
            coEvery { setPrivateSearch(any()) } returns Unit
            coEvery { addNewBookIds(any()) } returns Unit
            coEvery { clearNewBookId(any()) } returns Unit
            coEvery { clearAllNewBookIds() } returns Unit
        }
        continueReadingStore = mockk { coEvery { clear(any()) } returns Unit }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 构造 ViewModel。
     *
     * [booksFlow] 用于需要**控制发射时机**的用例（M-BUG-09 骨架门护栏）：`ShelfViewModel.books`
     * 在**属性初始化时**就调用 `repository.observeBooks()` 并捕获返回的 Flow，所以「先 createVm()
     * 再重打桩」对已构造的实例**无效**。要换掉这条流，必须在构造前传入。
     */
    private fun createVm(
        books: List<BookEntity> = emptyList(),
        booksFlow: Flow<List<BookEntity>>? = null,
        sourceIndex: LibrarySourceIndex = mockk(relaxed = true),
        sourceUpdateChecker: ShelfSourceUpdateChecker = mockk(relaxed = true),
        shelfImporter: ShelfImporter? = null,
    ): ShelfViewModel {
        every { repository.observeBooks() } returns (booksFlow ?: flowOf(books))
        every { repository.observeProgress() } returns flowOf(emptyList())
        every { repository.observeSessions() } returns flowOf(emptyList())
        every { repository.observeNotes() } returns flowOf(emptyList())
        every { repository.observeHighlights() } returns flowOf(emptyList())
        every { repository.observeInspirations() } returns flowOf(emptyList())
        // 导入管线由 Hilt 注入；测试中默认用真实 ShelfImporter + mock DAO 构造，
        // 仅作为 ViewModel 的依赖替身，也支持传入外部 mock。
        val importer = shelfImporter ?: ShelfImporter(
            context = context,
            repository = repository,
            bookDao = bookDao,
            bookContentDao = bookContentDao,
            bookFileDao = bookFileDao,
            epubRepository = epubRepository,
            importHistoryStore = importHistoryStore,
            sourceIndex = mockk(relaxed = true),
            rootStore = mockk { coEvery { loadRoot() } returns null },
            ioDispatcher = Dispatchers.Unconfined,
            // S04-2：ShelfImporter 新增协调器参数；本测试不断言索引刷新行为，用 relaxed mock。
            indexRefreshCoordinator = mockk(relaxed = true),
        )
        return ShelfViewModel(
            context = context,
            repository = repository,
            taxonomyRepository = taxonomyRepository,
            syncRepository = syncRepository,
            shelfPrefs = shelfPrefs,
            savedViewsStore = savedViewsStore,
            continueReadingStore = continueReadingStore,
            ioDispatcher = Dispatchers.Unconfined,
            defaultDispatcher = Dispatchers.Unconfined,
            shelfImporter = importer,
            // M-BUG-03：来源内容更新检测属书架附加能力，默认用 relaxed 替身。
            sourceIndex = sourceIndex,
            sourceUpdateChecker = sourceUpdateChecker,
        )
    }

    private fun book(id: String, title: String): BookEntity = BookEntity(
        id = id,
        title = title,
        format = "txt",
        updated_at = "2026-01-01T00:00:00Z",
    )

    @Test
    fun `session filters and tags update state`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        testScheduler.advanceUntilIdle()

        vm.setStatusFilter(ShelfStatusFilter.READING)
        vm.setSelectedShelf("s1")
        vm.setSelectedCategory("c1")
        vm.setSelectedTag("t1")
        vm.toggleSelectedTag("t2")
        vm.toggleSelectedTag("t1")

        var session = vm.session.value
        assertEquals(ShelfStatusFilter.READING, session.statusFilter)
        assertEquals("s1", session.selectedShelfId)
        assertEquals("c1", session.selectedCategoryId)
        assertEquals(setOf("t2"), session.selectedTagIds)

        vm.resetShelfFilters()
        session = vm.session.value
        assertEquals(ShelfStatusFilter.ALL, session.statusFilter)
        assertEquals("", session.selectedShelfId)
        assertEquals("", session.selectedCategoryId)
        assertTrue(session.selectedTagIds.isEmpty())
    }

    @Test
    fun `view mode and sort mode persist to prefs`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        testScheduler.advanceUntilIdle()

        vm.setViewMode(ShelfViewMode.LIST)
        vm.setSortMode(ShelfSortMode.TITLE)

        assertEquals(ShelfViewMode.LIST, vm.session.value.viewMode)
        assertEquals(ShelfSortMode.TITLE, vm.session.value.sortMode)
        assertEquals("LIST", viewModeFlow.value)
        assertEquals("TITLE", sortModeFlow.value)
    }

    @Test
    fun `search query set and clear`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        testScheduler.advanceUntilIdle()

        vm.setSearchQuery("三体")
        assertEquals("三体", vm.searchQuery.value)

        vm.clearSearchQuery()
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun `shelfBooks follows sort mode from prefs`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm(
            books = listOf(
                book("b2", "乙书"),
                book("b1", "甲书"),
            )
        )
        testScheduler.advanceUntilIdle()
        val job = launch { vm.shelfBooks.collect { } }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("甲书", "乙书"), vm.shelfBooks.value.map { it.book.title })

        vm.setSortMode(ShelfSortMode.TITLE)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("乙书", "甲书"), vm.shelfBooks.value.map { it.book.title })
        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `shelfBooks loads existing books without a screen collector or new import`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm(books = listOf(book("b1", "已存在的书")))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("已存在的书"), vm.shelfBooks.value.map { it.book.title })
    }

    @Test
    fun `uiState aggregates library and auxiliary`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm(books = listOf(book("b1", "甲书")))
        testScheduler.advanceUntilIdle()
        val job = launch { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        vm.setViewMode(ShelfViewMode.LIST)
        vm.setSortMode(ShelfSortMode.TITLE)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.library.books.size)
        assertEquals("甲书", state.library.books.first().title)
        assertEquals("LIST", state.auxiliary.savedViewMode)
        assertEquals("TITLE", state.auxiliary.savedSortMode)
        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `restore book invokes callback with success message`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        testScheduler.advanceUntilIdle()

        var result: String? = null
        vm.restoreBook("b1") { result = it }
        testScheduler.advanceUntilIdle()

        assertEquals("已恢复书籍", result)
        coVerify(exactly = 1) { repository.restoreBook("b1") }
    }

    @Test
    fun `restore book failure surfaces message`() = runTest(mainDispatcher.scheduler) {
        every { repository.observeBooks() } returns flowOf(emptyList())
        every { repository.observeProgress() } returns flowOf(emptyList())
        every { repository.observeSessions() } returns flowOf(emptyList())
        every { repository.observeNotes() } returns flowOf(emptyList())
        every { repository.observeHighlights() } returns flowOf(emptyList())
        every { repository.observeInspirations() } returns flowOf(emptyList())
        coEvery { repository.restoreBook(any()) } throws IllegalStateException("boom")
        val vm = createVm()
        testScheduler.advanceUntilIdle()

        var result: String? = null
        vm.restoreBook("b1") { result = it }
        testScheduler.advanceUntilIdle()

        assertTrue(result!!.contains("恢复失败"))
        assertTrue(result!!.contains("boom"))
    }

    @Test
    fun `update book info rejects blank title`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        testScheduler.advanceUntilIdle()

        var result: String? = null
        vm.updateBookInfo("b1", " ", null, null) { result = it }
        testScheduler.advanceUntilIdle()

        assertEquals("书名不能为空", result)
        coVerify(exactly = 0) { repository.updateBookInfo(any(), any(), any(), any()) }
    }

    @Test
    fun `delete book delegates to repository`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        testScheduler.advanceUntilIdle()

        vm.deleteBook("b1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteBook("b1") }
    }

    @Test
    fun `refresh resets refreshing flag`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        testScheduler.advanceUntilIdle()

        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertFalse(vm.isRefreshing.value)
    }

    @Test
    fun `shelve book and restore reading invoke callbacks`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        testScheduler.advanceUntilIdle()

        var shelveMsg: String? = null
        var readingMsg: String? = null
        vm.shelveBook("b1") { shelveMsg = it }
        vm.restoreReading("b1") { success, msg -> readingMsg = msg }
        testScheduler.advanceUntilIdle()

        assertEquals("已搁置，阅读记录仍会保留", shelveMsg)
        assertEquals("已恢复为在读", readingMsg)
    }

    // ── L1 动态视图 ─────────────────────────────────────────────────────

    @Test
    fun `apply saved filter replaces session filters and search query`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        testScheduler.advanceUntilIdle()
        vm.toggleSelectedTag("tag-old")
        vm.setSearchQuery("旧关键词")

        vm.applySavedFilter(
            SavedShelfFilter(
                name = "在读 EPUB",
                statusFilter = "READING",
                formatFilter = "epub",
                selectedShelfId = "shelf-1",
                selectedCategoryId = "",
                selectedTagIds = listOf("tag-1", "tag-2"),
                searchQuery = "新关键词",
                createdAt = "2026-09-12T00:00:00Z",
            ),
        )
        testScheduler.advanceUntilIdle()

        assertEquals(ShelfStatusFilter.READING, vm.session.value.statusFilter)
        assertEquals("epub", vm.session.value.formatFilter)
        assertEquals("shelf-1", vm.session.value.selectedShelfId)
        assertEquals(setOf("tag-1", "tag-2"), vm.session.value.selectedTagIds)
        assertEquals("新关键词", vm.searchQuery.value)
    }

    @Test
    fun `save current filter persists via store and rejects when no filters active`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        testScheduler.advanceUntilIdle()

        var rejected: Boolean? = null
        vm.saveCurrentFilter("空白") { rejected = it }
        testScheduler.advanceUntilIdle()
        assertEquals(false, rejected)

        vm.setFormatFilter("epub")
        var saved: Boolean? = null
        vm.saveCurrentFilter("EPUB 书架") { saved = it }
        testScheduler.advanceUntilIdle()
        assertEquals(true, saved)
        coVerify {
            savedViewsStore.save(
                withArg {
                    assertEquals("EPUB 书架", it.name)
                    assertEquals("epub", it.formatFilter)
                },
            )
        }
    }

    @Test
    fun `hasActiveFilters reflects session and query`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        testScheduler.advanceUntilIdle()
        assertEquals(false, vm.hasActiveFilters())
        vm.setStatusFilter(ShelfStatusFilter.READING)
        assertEquals(true, vm.hasActiveFilters())
    }

    @Test
    fun `library loaded stays false until room emits so the shelf cannot flash an empty state`() =
        runTest(mainDispatcher.scheduler) {
            // M-BUG-09 护栏：books 是 stateIn(..., emptyList())，其合成初值与「书架真的没有书」
            // 在 UI 层不可区分。若骨架门改用计时器（原实现 350ms），冷启动首读慢于该时长时
            // 骨架先关、books 仍为空 ⇒ 先渲染空态「书架还空着」再跳到列表（真机可复现）。
            // 因此门必须由**首次真实发射**驱动：这里让 Room 侧一条都不发，libraryLoaded 必须为 false。
            // 注意 booksFlow 必须在**构造前**传入：books 属性在初始化时就捕获了 observeBooks()
            // 的返回值，构造后再重打桩是无效的。
            val roomBooks = MutableSharedFlow<List<BookEntity>>()
            val vm = createVm(booksFlow = roomBooks)

            val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.books.collect { }
            }
            testScheduler.advanceUntilIdle()
            assertFalse(
                "Room 首发射未到达时不得判定为已加载，否则空态会冒充加载态",
                vm.libraryLoaded.value,
            )

            // 空表也算「已发射」—— 空书架理应显示空态，只是不能在数据到达前显示。
            roomBooks.emit(emptyList())
            testScheduler.advanceUntilIdle()
            assertTrue("首次发射到达（哪怕空表）即算已加载", vm.libraryLoaded.value)

            // 显式退订并当场结算：stateIn 的订阅计数簿记会恢复共享协程（作用域是 viewModelScope
            // ＝测试 Main）。拖到 runTest 收尾之后才结算会撞 resetMain()，污染后续测试类。
            collectJob.cancel()
            testScheduler.advanceUntilIdle()
        }

    private fun sourceRef(bookId: String = "b1"): LibrarySourceRef = LibrarySourceRef(
        bookId = bookId,
        rootId = null,
        providerAuthority = "com.android.externalstorage.documents",
        documentId = "primary:Books/$bookId.txt",
        displayName = "$bookId.txt",
        format = "txt",
        sizeBytes = 10L,
        lastModifiedMillis = null,
        contentHash = null,
        candidateFingerprint = null,
        lastSeenAtMillis = 0L,
        availability = LibrarySourceAvailability.AVAILABLE,
    )

    // ===== S04-1：来源刷新前置一次性确认机制 =====

    @Test
    fun `refresh prompts confirmation when source updates detected without refreshing text`() = runTest(mainDispatcher.scheduler) {
        val mockSourceIndex = mockk<LibrarySourceIndex>()
        val mockChecker = mockk<ShelfSourceUpdateChecker>()
        val mockImporter = mockk<ShelfImporter>(relaxed = true)

        coEvery { mockSourceIndex.snapshot() } returns listOf(sourceRef("b1"))
        coEvery { mockChecker.checkUpdated(any()) } returns setOf("b1")

        val vm = createVm(
            books = listOf(book("b1", "测试书")),
            sourceIndex = mockSourceIndex,
            sourceUpdateChecker = mockChecker,
            shelfImporter = mockImporter,
        )
        testScheduler.advanceUntilIdle()

        vm.refresh()
        testScheduler.advanceUntilIdle()

        // 触发确认请求状态
        assertEquals(listOf("b1"), vm.pendingSourceRefreshIds.value)
        assertEquals(setOf("b1"), vm.contentUpdatedBookIds.value)
        // 关键不变量：未确认前，零本正文被替换
        coVerify(exactly = 0) { mockImporter.refreshFromSource(any()) }
    }

    @Test
    fun `cancelling source refresh clears confirmation without modifying text or updated badge`() = runTest(mainDispatcher.scheduler) {
        val mockSourceIndex = mockk<LibrarySourceIndex>()
        val mockChecker = mockk<ShelfSourceUpdateChecker>()
        val mockImporter = mockk<ShelfImporter>(relaxed = true)

        coEvery { mockSourceIndex.snapshot() } returns listOf(sourceRef("b1"))
        coEvery { mockChecker.checkUpdated(any()) } returns setOf("b1")

        val vm = createVm(
            books = listOf(book("b1", "测试书")),
            sourceIndex = mockSourceIndex,
            sourceUpdateChecker = mockChecker,
            shelfImporter = mockImporter,
        )
        testScheduler.advanceUntilIdle()

        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("b1"), vm.pendingSourceRefreshIds.value)

        // 用户取消确认
        vm.cancelSourceRefresh()
        testScheduler.advanceUntilIdle()

        // 确认状态已关闭
        assertEquals(null, vm.pendingSourceRefreshIds.value)
        // 角标集合保持
        assertEquals(setOf("b1"), vm.contentUpdatedBookIds.value)
        // 正文依然 0 次替换
        coVerify(exactly = 0) { mockImporter.refreshFromSource(any()) }
    }

    @Test
    fun `confirming source refresh executes refresh on updated books and returns summary message`() = runTest(mainDispatcher.scheduler) {
        val mockSourceIndex = mockk<LibrarySourceIndex>()
        val mockChecker = mockk<ShelfSourceUpdateChecker>()
        val mockImporter = mockk<ShelfImporter>(relaxed = true)

        coEvery { mockSourceIndex.snapshot() } returns listOf(sourceRef("b1"), sourceRef("b2"))
        coEvery { mockChecker.checkUpdated(any()) } returns setOf("b1", "b2")
        coEvery { mockImporter.refreshFromSource("b1") } returns SourceRefreshResult.Success("《书1》已修复")
        coEvery { mockImporter.refreshFromSource("b2") } returns SourceRefreshResult.Success("《书2》已修复")

        val vm = createVm(
            books = listOf(book("b1", "书1"), book("b2", "书2")),
            sourceIndex = mockSourceIndex,
            sourceUpdateChecker = mockChecker,
            shelfImporter = mockImporter,
        )
        testScheduler.advanceUntilIdle()

        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("b1", "b2"), vm.pendingSourceRefreshIds.value)

        // 用户确认刷新
        var summaryMessage: String? = null
        vm.confirmSourceRefresh { summaryMessage = it }
        testScheduler.advanceUntilIdle()

        // 确认状态已清除
        assertEquals(null, vm.pendingSourceRefreshIds.value)
        // 对更新书籍依次执行刷新
        coVerify(exactly = 1) { mockImporter.refreshFromSource("b1") }
        coVerify(exactly = 1) { mockImporter.refreshFromSource("b2") }
        // 给出汇总反馈
        assertTrue(summaryMessage!!.contains("已同步 2 本书的源文件更新"))
    }

    @Test
    fun `confirming source refresh keeps failure reason instead of dropping it`() = runTest(mainDispatcher.scheduler) {
        // S04：审查 P2 指出 Failed(message) 的原因被丢弃，用户只看到「N 本刷新失败：书名」，
        // 不知道替换残留导致「本次已停止、重试无效」。本用例钉住原因随汇总带出的接线。
        val mockSourceIndex = mockk<LibrarySourceIndex>()
        val mockChecker = mockk<ShelfSourceUpdateChecker>()
        val mockImporter = mockk<ShelfImporter>(relaxed = true)
        // uiState 的 combine 会订阅导入器暴露的三个 StateFlow；relaxed mock 的 StateFlow
        // 会让 combine 永不发射，书名查找只能回退「未知书籍」。显式钉住真实空流。
        every { mockImporter.importTasks } returns MutableStateFlow(emptyList())
        every { mockImporter.importBatch } returns MutableStateFlow(ImportBatchUiState())
        every { mockImporter.importHistory } returns MutableStateFlow(emptyList())

        val residue = "修复失败：检测到未完成的替换残留（x.backup），本次替换已停止；" +
            "旧正文若仍存在，则未被本次操作修改，残留备份已保留。重复重试不会自动恢复该备份"
        coEvery { mockSourceIndex.snapshot() } returns listOf(sourceRef("b1"), sourceRef("b2"), sourceRef("b3"))
        coEvery { mockChecker.checkUpdated(any()) } returns setOf("b1", "b2", "b3")
        coEvery { mockImporter.refreshFromSource("b1") } returns SourceRefreshResult.Success("《书1》正文已修复")
        coEvery { mockImporter.refreshFromSource("b2") } returns SourceRefreshResult.Failed(residue)
        coEvery { mockImporter.refreshFromSource("b3") } returns SourceRefreshResult.Failed(residue)

        val vm = createVm(
            books = listOf(book("b1", "书1"), book("b2", "书2"), book("b3", "书3")),
            sourceIndex = mockSourceIndex,
            sourceUpdateChecker = mockChecker,
            shelfImporter = mockImporter,
        )
        // uiState 是 WhileSubscribed(5s)：生产中书架 UI 恒订阅它，confirmSourceRefresh
        // 才能从 uiState 拿到书名；测试必须复现同样的订阅态，否则书名回退成「未知书籍」。
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("b1", "b2", "b3"), vm.pendingSourceRefreshIds.value)

        var summaryMessage: String? = null
        vm.confirmSourceRefresh { summaryMessage = it }
        testScheduler.advanceUntilIdle()

        assertEquals(null, vm.pendingSourceRefreshIds.value)
        val message = summaryMessage!!
        assertTrue("成功数如实统计: $message", message.contains("已同步 1 本书的源文件更新"))
        assertTrue("失败书名保留: $message", message.contains("2 本刷新失败：书2、书3"))
        assertTrue("失败原因必须随汇总带出: $message", message.contains("本次替换已停止"))
        assertTrue("「反复重试无效」语义必须在: $message", message.contains("重复重试不会自动恢复该备份"))
        assertTrue("备份未被销毁的事实必须在: $message", message.contains("残留备份已保留"))
        // 不得引导用户删书 / 清备份 / 宣称能自动恢复
        assertFalse("不得引导删书: $message", message.contains("删除"))
        assertFalse("不得引导清空备份: $message", message.contains("清空"))
        assertFalse("不得宣称能恢复: $message", message.contains("可以恢复"))
    }

    @Test
    fun `refresh without source updates completes directly with feedback and without confirmation`() = runTest(mainDispatcher.scheduler) {
        val mockSourceIndex = mockk<LibrarySourceIndex>()
        val mockChecker = mockk<ShelfSourceUpdateChecker>()
        val mockImporter = mockk<ShelfImporter>(relaxed = true)

        coEvery { mockSourceIndex.snapshot() } returns listOf(sourceRef("b1"))
        coEvery { mockChecker.checkUpdated(any()) } returns emptySet()

        val vm = createVm(
            books = listOf(book("b1", "测试书")),
            sourceIndex = mockSourceIndex,
            sourceUpdateChecker = mockChecker,
            shelfImporter = mockImporter,
        )
        testScheduler.advanceUntilIdle()

        var feedbackMessage: String? = null
        vm.refresh { feedbackMessage = it }
        testScheduler.advanceUntilIdle()

        // 不触发确认状态
        assertEquals(null, vm.pendingSourceRefreshIds.value)
        // 正文刷新未调用
        coVerify(exactly = 0) { mockImporter.refreshFromSource(any()) }
        // 给出即时反馈
        assertEquals("书架已是最新，没有检测到源文件更新", feedbackMessage)
    }
}
