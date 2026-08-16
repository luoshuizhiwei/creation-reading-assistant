package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Trace
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.TaxonomyRepository
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.library.ShelfImporter
import com.creationreadingassistant.data.settings.ImportHistoryEntry
import com.creationreadingassistant.data.settings.ShelfPrefs
import com.creationreadingassistant.data.settings.ContinueReadingStore
import com.creationreadingassistant.data.repository.SyncRepository
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.ui.screen.shelf.ShelfSortMode
import com.creationreadingassistant.ui.screen.shelf.ShelfStatusFilter
import com.creationreadingassistant.ui.screen.shelf.ShelfViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 书架页 ViewModel —— 仅依赖 Repository 层，不直接注入 DAO（APP_MODULE_PLAN §7.2）。
 *
 * 职责边界（2026-08 拆分后）：
 * - 会话状态（筛选/排序/搜索/多选）、书架列表聚合流、偏好持久化、下拉刷新、同步下载；
 * - 导入管线 / 修复文件 / 导入历史 → [ShelfImporter]（Hilt 注入，其内部 DAO 依赖属导入管线范围）；
 * - 书籍级写操作（删除/恢复/搁置/封面/缓存）→ [ShelfBookActions]。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShelfViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BookRepository,
    private val taxonomyRepository: TaxonomyRepository,
    private val syncRepository: SyncRepository,
    private val shelfPrefs: ShelfPrefs,
    private val continueReadingStore: ContinueReadingStore,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    shelfImporter: ShelfImporter,
) : ViewModel() {

    val importer: ShelfImporter = shelfImporter

    private val bookActions: ShelfBookActions = ShelfBookActions(
        context = context,
        repository = repository,
        continueReadingStore = continueReadingStore,
    )

    private val _session = MutableStateFlow(ShelfSessionState())
    internal val session: StateFlow<ShelfSessionState> = _session.asStateFlow()

    val recentSearches: StateFlow<List<String>> = shelfPrefs.recentSearches
    val privateSearch: StateFlow<Boolean> = shelfPrefs.privateSearch

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(value: String) { _searchQuery.value = value }
    fun clearSearchQuery() { _searchQuery.value = "" }
    fun recordSearch(query: String = _searchQuery.value) = viewModelScope.launch { shelfPrefs.recordSearch(query) }
    fun clearRecentSearches() = viewModelScope.launch { shelfPrefs.clearRecentSearches() }
    fun restoreRecentSearches(values: List<String>) = viewModelScope.launch { shelfPrefs.replaceRecentSearches(values) }
    fun setPrivateSearch(enabled: Boolean) = viewModelScope.launch { shelfPrefs.setPrivateSearch(enabled) }

    internal fun setStatusFilter(value: ShelfStatusFilter) { _session.value = _session.value.copy(statusFilter = value) }
    internal fun setSelectedShelf(value: String) { _session.value = _session.value.copy(selectedShelfId = value) }
    internal fun setFormatFilter(value: String) {
        _session.value = _session.value.copy(formatFilter = if (_session.value.formatFilter == value) "" else value)
    }
    internal fun setSelectedCategory(value: String) { _session.value = _session.value.copy(selectedCategoryId = value) }
    internal fun setSelectedTag(value: String) {
        _session.value = _session.value.copy(selectedTagIds = value.takeIf(String::isNotBlank)?.let(::setOf).orEmpty())
    }
    internal fun toggleSelectedTag(value: String) {
        if (value.isBlank()) {
            clearSelectedTags()
            return
        }
        val selected = _session.value.selectedTagIds
        _session.value = _session.value.copy(
            selectedTagIds = if (value in selected) selected - value else selected + value,
        )
    }
    internal fun clearSelectedTags() {
        _session.value = _session.value.copy(selectedTagIds = emptySet())
    }
    internal fun setViewMode(value: ShelfViewMode) {
        _session.value = _session.value.copy(viewMode = value)
        setShelfViewMode(value.name)
    }
    internal fun setSortMode(value: ShelfSortMode) {
        _session.value = _session.value.copy(sortMode = value)
        setShelfSortMode(value.name)
    }
    internal fun resetShelfFilters() {
        _session.value = _session.value.clearFilters()
    }

    val books: StateFlow<List<BookEntity>> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<TagEntity>> = taxonomyRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = taxonomyRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shelves: StateFlow<List<ShelfEntity>> = taxonomyRepository.observeShelves()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progressById: StateFlow<Map<String, ReadingProgressEntity>> =
        repository.observeProgress()
            .map { list -> list.associateBy { it.book_id } }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val sessionsByBook: StateFlow<Map<String, List<ReadingSessionEntity>>> =
        repository.observeSessions()
            .map { list -> list.groupBy { it.book_id } }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 笔记（书签/笔记）按 bookId 分组，供详情区块展示。 */
    val notesByBook: StateFlow<Map<String, List<NoteEntity>>> =
        repository.observeNotes()
            .map { list -> list.groupBy { it.book_id ?: "" } }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 高亮/标注按 bookId 分组，供详情区块展示。 */
    val highlightsByBook: StateFlow<Map<String, List<HighlightEntity>>> =
        repository.observeHighlights()
            .map { list -> list.groupBy { it.book_id } }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 灵感按 source_book_id 分组，供详情区块展示。 */
    val inspirationsByBook: StateFlow<Map<String, List<InspirationEntity>>> =
        repository.observeInspirations()
            .map { list -> list.groupBy { it.source_book_id ?: "" } }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ===== 书架视图 / 排序偏好（持久化，对齐网页 localStorage）=====
    val shelfViewMode: StateFlow<String> = shelfPrefs.viewMode
    val shelfSortMode: StateFlow<String> = shelfPrefs.sortMode

    fun setShelfViewMode(mode: String) = viewModelScope.launch { shelfPrefs.setViewMode(mode) }
    fun setShelfSortMode(mode: String) = viewModelScope.launch { shelfPrefs.setSortMode(mode) }

    /**
     * 书架列表的模型投影：在后台线程把数据库实体转为稳定 [ShelfBookItem]，
     * 并通过 distinctUntilChanged 让纯排序切换不必重复投影。
     */
    private val projectedBooks: Flow<List<ShelfBookItem>> = combine(
        books,
        progressById,
    ) { bookList, progress ->
        Trace.beginSection("ShelfItemProjection")
        try {
            ShelfBookSorter.projectAll(bookList, progress)
        } finally {
            Trace.endSection()
        }
    }
        .flowOn(defaultDispatcher)
        .distinctUntilChanged()

    /**
     * 书架渲染用稳定列表：后台线程完成排序，新排序请求自动取消旧请求。
     *
     * 只依赖投影后的书籍与排序方式；搜索词、弹层、选择状态、导入任务变化不会触发此 Flow。
     */
    val shelfBooks: StateFlow<List<ShelfBookItem>> = combine(
        projectedBooks,
        shelfSortMode,
    ) { projected, sortMode -> projected to sortMode }
        .mapLatest { (projected, sortMode) ->
            Trace.beginSection("ShelfListPublish")
            try {
                withContext(defaultDispatcher) {
                    Trace.beginSection("ShelfSort")
                    try {
                        ShelfBookSorter.sort(projected, sortMode)
                    } finally {
                        Trace.endSection()
                    }
                }
            } finally {
                Trace.endSection()
            }
        }
        .flowOn(defaultDispatcher)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // ===== 导入（真实 SAF 导入 / 文件夹扫描 / 修复 / 历史）—— 委托 ShelfImporter =====
    val importTasks: StateFlow<List<ImportTaskUi>> = importer.importTasks
    val importBatch: StateFlow<ImportBatchUiState> = importer.importBatch
    val importHistory: StateFlow<List<ImportHistoryEntry>> = importer.importHistory

    /** 兼容单文件入口；实际统一走顺序批处理，避免同时解析多本大书抢占内存。 */
    fun importFile(uri: Uri) = importFiles(listOf(uri))

    /** 导入文件选择器返回的多本书。 */
    fun importFiles(uris: List<Uri>, sourceLabel: String = "所选文件") = viewModelScope.launch {
        importer.importFiles(uris, sourceLabel)
    }

    /** 扫描用户明确授权的 SAF 文件夹，再顺序导入支持的书籍文件。 */
    fun importFolder(treeUri: Uri) = viewModelScope.launch {
        importer.importFolder(treeUri)
    }

    fun retryFailedImports() = viewModelScope.launch {
        importer.retryFailedImports()
    }

    fun dismissImportBatchSummary() = importer.dismissImportBatchSummary()

    /** 安全停止：不取消当前解析/写库，只在当前文件完成后停止剩余队列。 */
    fun requestStopImport() = importer.requestStopImport()

    fun clearImportHistory() = viewModelScope.launch {
        importer.clearImportHistory()
    }

    /** 重新选择文件（修复缺失正文）。 */
    fun reselectFile(bookId: String, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(importer.repairFile(bookId, uri))
    }

    // ===== 书籍级写操作（删除/恢复/搁置/封面/缓存）—— 委托 ShelfBookActions =====
    fun deleteBook(id: String) = viewModelScope.launch {
        bookActions.deleteBook(id)
    }

    /** 撤销删除：恢复书籍及其关联数据。 */
    fun restoreBook(id: String, onResult: ((String) -> Unit)? = null) = viewModelScope.launch {
        onResult?.invoke(bookActions.restoreBook(id))
    }

    fun shelveBook(id: String, onResult: (String) -> Unit = {}) = viewModelScope.launch {
        onResult(bookActions.shelveBook(id))
    }

    fun restoreReading(id: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) = viewModelScope.launch {
        bookActions.restoreReading(id)
            .onSuccess { onResult(true, it) }
            .onFailure { onResult(false, "恢复失败：${it.message}") }
    }

    /** 更新书名/作者/简介。 */
    fun updateBookInfo(
        bookId: String,
        title: String,
        author: String?,
        description: String? = null,
        onResult: (String) -> Unit,
    ) = viewModelScope.launch {
        onResult(bookActions.updateBookInfo(bookId, title, author, description))
    }

    /** 更新封面（SAF Uri 字符串）。 */
    fun updateBookCover(bookId: String, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(bookActions.updateBookCover(bookId, uri))
    }

    /** 设置文字封面（由前端生成的 SVG DataURL，对照网页「文字封面」）。 */
    fun setBookTextCover(bookId: String, dataUrl: String, onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(bookActions.setBookTextCover(bookId, dataUrl))
    }

    /** 重置封面（清空封面图，回退为文字封面）。 */
    fun resetBookCover(bookId: String, onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(bookActions.resetBookCover(bookId))
    }

    /** 清理本地正文缓存（保留书架元数据）。 */
    fun clearCacheForBooks(ids: List<String>, onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(bookActions.clearCacheForBooks(ids))
    }

    // ===== 同步下载 =====
    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    fun downloadBookContent(bookId: String, onResult: (String) -> Unit) = viewModelScope.launch {
        // 防重复下载
        if (_downloadingIds.value.contains(bookId)) {
            onResult("正在下载中…")
            return@launch
        }
        _downloadingIds.value = _downloadingIds.value + bookId
        syncRepository.downloadBookContent(bookId)
            .onSuccess { onResult("下载成功") }
            .onFailure { onResult("下载失败：${it.message}") }
        _downloadingIds.value = _downloadingIds.value - bookId
    }

    suspend fun listDesktopBooks(): List<SyncContract.BookFileManifest> = syncRepository.listDesktopBooks()

    /**
     * 书架渲染所需的单一只读状态。
     *
     * 搜索词、当前弹层和选择模式仍属于页面会话状态，不在这里持久化；这样既减少
     * Composable 对十余条 Flow 的分散订阅，也不会让 ViewModel 承担平台选择器状态。
     */
    val uiState: StateFlow<ShelfUiState> = combine(
        combine(books, tags, categories, shelves, progressById) { bookList, tagList, categoryList, shelfList, progress ->
            ShelfLibraryState(
                books = bookList,
                tags = tagList,
                categories = categoryList,
                shelves = shelfList,
                progressById = progress,
            )
        },
        combine(sessionsByBook, notesByBook, highlightsByBook, inspirationsByBook) { sessions, notes, highlights, inspirations ->
            ShelfActivityState(
                sessionsByBook = sessions,
                notesByBook = notes,
                highlightsByBook = highlights,
                inspirationsByBook = inspirations,
            )
        },
        combine(
            combine(importTasks, importBatch) { tasks, batch -> tasks to batch },
            downloadingIds,
            importHistory,
            shelfViewMode,
            shelfSortMode,
        ) { (tasks, batch), downloading, history, viewMode, sortMode ->
            ShelfAuxiliaryState(
                importTasks = tasks,
                importBatch = batch,
                downloadingIds = downloading,
                importHistory = history,
                savedViewMode = viewMode,
                savedSortMode = sortMode,
            )
        },
    ) { library, activity, auxiliary ->
        ShelfUiState(
            library = library,
            activity = activity,
            auxiliary = auxiliary,
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ShelfUiState(),
        )

    // ===== 下拉刷新（主书架页）=====
    // 书架数据来自 Room 热流，本身已实时；此刷新用于手动重采底层查询，
    // 让外部写入 / 同步落地后能通过下拉手势立即反映，并给出可见的刷新反馈。
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching {
                withContext(ioDispatcher) {
                    repository.observeBooks().first()
                    repository.observeProgress().first()
                }
            }
            delay(350) // 保证刷新反馈有最短可见时长
            _isRefreshing.value = false
        }
    }

    init {
        importer.booksProvider = { books.value }
        viewModelScope.launch {
            shelfViewMode.collect { raw ->
                val value = ShelfViewMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ShelfViewMode.GRID
                _session.value = _session.value.copy(viewMode = value)
            }
        }
        viewModelScope.launch {
            shelfSortMode.collect { raw ->
                val value = ShelfSortMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ShelfSortMode.RECENT
                _session.value = _session.value.copy(sortMode = value)
            }
        }

        // EPUB size 修复已收敛为进程级一次性任务（EpubSizeRepairTask，App.onCreate 启动），
        // 不再由每个 ShelfViewModel 实例 init 重复触发。
    }
}
