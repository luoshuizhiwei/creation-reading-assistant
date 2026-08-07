package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.SyncRepository
import com.creationreadingassistant.data.repository.TaxonomyRepository
import com.creationreadingassistant.data.settings.ContinueReadingStore
import com.creationreadingassistant.data.settings.ImportHistoryStore
import com.creationreadingassistant.data.settings.ShelfPrefs
import com.creationreadingassistant.feature.library.ShelfImporter
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
    private lateinit var continueReadingStore: ContinueReadingStore
    private lateinit var viewModeFlow: MutableStateFlow<String>
    private lateinit var sortModeFlow: MutableStateFlow<String>

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        context = mockk()
        repository = mockk {
            coEvery { seedSampleIfEmpty() } returns Unit
            coEvery { restoreBook(any()) } returns Unit
            coEvery { deleteBook(any()) } returns Unit
            coEvery { clearBookCache(any()) } returns Unit
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
        shelfPrefs = mockk {
            every { viewMode } returns viewModeFlow
            every { sortMode } returns sortModeFlow
            every { recentSearches } returns MutableStateFlow(emptyList())
            every { privateSearch } returns MutableStateFlow(false)
            coEvery { setViewMode(any()) } answers { viewModeFlow.value = it.invocation.args[0] as String; Unit }
            coEvery { setSortMode(any()) } answers { sortModeFlow.value = it.invocation.args[0] as String; Unit }
            coEvery { recordSearch(any()) } returns Unit
            coEvery { clearRecentSearches() } returns Unit
            coEvery { replaceRecentSearches(any()) } returns Unit
            coEvery { setPrivateSearch(any()) } returns Unit
        }
        continueReadingStore = mockk { coEvery { clear(any()) } returns Unit }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(books: List<BookEntity> = emptyList()): ShelfViewModel {
        every { repository.observeBooks() } returns flowOf(books)
        every { repository.observeProgress() } returns flowOf(emptyList())
        every { repository.observeSessions() } returns flowOf(emptyList())
        every { repository.observeNotes() } returns flowOf(emptyList())
        every { repository.observeHighlights() } returns flowOf(emptyList())
        every { repository.observeInspirations() } returns flowOf(emptyList())
        // 导入管线由 Hilt 注入；测试中用真实 ShelfImporter + mock DAO 构造，
        // 仅作为 ViewModel 的依赖替身，本测试不断言其内部行为。
        val importer = ShelfImporter(
            context = context,
            repository = repository,
            bookDao = bookDao,
            bookContentDao = bookContentDao,
            bookFileDao = bookFileDao,
            epubRepository = epubRepository,
            importHistoryStore = importHistoryStore,
            ioDispatcher = Dispatchers.Unconfined,
        )
        return ShelfViewModel(
            context = context,
            repository = repository,
            taxonomyRepository = taxonomyRepository,
            syncRepository = syncRepository,
            shelfPrefs = shelfPrefs,
            continueReadingStore = continueReadingStore,
            ioDispatcher = Dispatchers.Unconfined,
            defaultDispatcher = Dispatchers.Unconfined,
            shelfImporter = importer,
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
}
