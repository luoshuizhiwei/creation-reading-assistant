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
import com.creationreadingassistant.feature.library.LibrarySourceIndex
import com.creationreadingassistant.feature.library.ShelfImporter
import com.creationreadingassistant.feature.library.ShelfSourceUpdateChecker
import com.creationreadingassistant.feature.library.SourceRefreshResult
import com.creationreadingassistant.feature.library.deletion.BookDeletionCoordinator
import com.creationreadingassistant.feature.library.deletion.DeletionUndoOffer
import com.creationreadingassistant.feature.library.deletion.DeletionUndoOutcome
import com.creationreadingassistant.data.settings.ImportHistoryEntry
import com.creationreadingassistant.data.settings.SavedShelfFilter
import com.creationreadingassistant.data.settings.ShelfPrefs
import com.creationreadingassistant.data.settings.ShelfSavedViewsStore
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
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ===== 来源刷新汇总文案（S04，依据 2026-09-25 EPUB 残留 UI 流向审查 P2） =====

/** 汇总里最多展开的书名数；其余折叠为「等 N 本」，避免批量失败把 Snackbar 撑爆。 */
internal const val SOURCE_REFRESH_SUMMARY_MAX_TITLES = 3

/** 非残留类单条原因的字符上限；超出截断加省略号（完整原因永远可在导入记录里看到）。 */
internal const val SOURCE_REFRESH_REASON_MAX_CHARS = 60

/** 单个书名在汇总里的字符上限；超出截断加省略号（书卡上本就展示完整书名）。 */
internal const val SOURCE_REFRESH_TITLE_MAX_CHARS = 30

/** 残留守卫消息的判定标记：EPUB（`EpubRepository`）与 TXT/MD（`ShelfImporter`）守卫共用同一句式。 */
private const val SOURCE_REFRESH_RESIDUE_MARKER = "替换残留"

/** 一本刷新失败的书的展示信息：书名 + 底层原因原文。 */
internal data class SourceRefreshFailureUi(
    val title: String,
    val reason: String,
)

/** 通用截断：超出上限截断加省略号；不改变未超限的文本。 */
private fun truncateForSummary(text: String, maxChars: Int): String =
    if (text.length <= maxChars) text else text.take(maxChars) + "…"

/**
 * 压缩单条失败原因，供 Snackbar 汇总使用（纯函数）。
 *
 * - **残留场景必须语义完整**：短句保留「本次替换已停止」「残留备份已保留」
 *   「重复重试不会自动恢复该备份」三个关键事实——这正是审查要求用户必须读到的部分；
 *   被去掉的只有括号里的备份文件名与「旧正文若仍存在…」的中间从句。
 *   **绝不**引导删书 / 清备份，也**绝不**宣称能自动恢复。
 * - 其他原因截断到 [SOURCE_REFRESH_REASON_MAX_CHARS]；空原因显式写「原因未知」，
 *   不产出「（原因：）」这种空括号。
 */
internal fun summarizeSourceRefreshFailureReason(reason: String): String {
    if (reason.contains(SOURCE_REFRESH_RESIDUE_MARKER)) {
        return "检测到未完成的替换残留，本次替换已停止；残留备份已保留，重复重试不会自动恢复该备份"
    }
    val trimmed = reason.trim()
    if (trimmed.isEmpty()) return "原因未知"
    return truncateForSummary(trimmed, SOURCE_REFRESH_REASON_MAX_CHARS)
}

/**
 * 构建来源刷新（入口 B）的汇总文案（纯函数）。
 *
 * 产品口径：
 * - 成功数如实统计（0 本也如实说「已同步 0 本」，绝不把失败报成成功）；
 * - 失败必须带**首条原因**，不能再只报书名——残留场景用户必须看到「重试无效」；
 * - 书名只展开前 [SOURCE_REFRESH_SUMMARY_MAX_TITLES] 个，多本失败折叠计数；
 * - 原因只展开第一条；失败原因不止一种时提示「详见导入记录」，不逐条铺开；
 * - 书名是书架既有展示信息（书卡上本来就显示），可进提示；书籍正文内容永不进提示。
 */
internal fun buildSourceRefreshSummary(
    succeeded: Int,
    unlocatable: Int,
    failures: List<SourceRefreshFailureUi>,
): String = buildString {
    append("已同步 $succeeded 本书的源文件更新")
    if (unlocatable > 0) append("；$unlocatable 本无法定位来源，请长按选择「重新定位文件」")
    if (failures.isNotEmpty()) {
        append("；${failures.size} 本刷新失败：")
        val shown = failures.take(SOURCE_REFRESH_SUMMARY_MAX_TITLES)
        append(shown.joinToString("、") { truncateForSummary(it.title, SOURCE_REFRESH_TITLE_MAX_CHARS) })
        if (failures.size > shown.size) append(" 等 ${failures.size} 本")
        append("（原因：")
        append(summarizeSourceRefreshFailureReason(failures.first().reason))
        append("）")
        if (failures.map { it.reason }.distinct().size > 1) {
            append("；其余失败原因详见导入记录")
        }
    }
}

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
    private val savedViewsStore: ShelfSavedViewsStore,
    private val continueReadingStore: ContinueReadingStore,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    shelfImporter: ShelfImporter,
    private val sourceIndex: LibrarySourceIndex,
    private val sourceUpdateChecker: ShelfSourceUpdateChecker,
    private val deletions: BookDeletionCoordinator? = null,
) : ViewModel() {

    val importer: ShelfImporter = shelfImporter

    private val bookActions: ShelfBookActions = ShelfBookActions(
        context = context,
        repository = repository,
        continueReadingStore = continueReadingStore,
        deletions = deletions,
    )

    /** 会话内可撤销的删除提示；为空表示当前不该显示任何撤销入口。 */
    val deletionUndoOffers: StateFlow<List<DeletionUndoOffer>> =
        deletions?.offers ?: MutableStateFlow(emptyList())

    /** 撤销窗口秒数，供确认框文案与实际凭证有效期保持同源。 */
    val undoWindowSeconds: Int get() = deletions?.undoWindowSeconds ?: 0

    private val _session = MutableStateFlow(ShelfSessionState())
    internal val session: StateFlow<ShelfSessionState> = _session.asStateFlow()

    init {
        // 冷启动自动同步持久化的排序模式与视图模式到会话状态，消除 UI 展示与底层排序列脱节
        viewModelScope.launch {
            shelfPrefs.sortMode.collect { modeStr ->
                val mode = when (modeStr.trim().lowercase()) {
                    "title" -> ShelfSortMode.TITLE
                    "progress" -> ShelfSortMode.PROGRESS
                    "imported" -> ShelfSortMode.IMPORTED
                    else -> ShelfSortMode.RECENT
                }
                if (_session.value.sortMode != mode) {
                    _session.value = _session.value.copy(sortMode = mode)
                }
            }
        }
        viewModelScope.launch {
            shelfPrefs.viewMode.collect { modeStr ->
                val mode = when (modeStr.trim().lowercase()) {
                    "list" -> ShelfViewMode.LIST
                    else -> ShelfViewMode.GRID
                }
                if (_session.value.viewMode != mode) {
                    _session.value = _session.value.copy(viewMode = mode)
                }
            }
        }
    }

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

    // ── L1 书架动态视图（保存的筛选条件组合）────────────────────────────

    /** 已保存的动态视图（DataStore 持久化，跨会话可用）。 */
    val savedFilters: StateFlow<List<SavedShelfFilter>> = savedViewsStore.savedFilters

    /** 是否有任何筛选/搜索生效（决定「保存为动态视图」入口的可见性）。 */
    fun hasActiveFilters(): Boolean {
        val s = _session.value
        return s.statusFilter != ShelfStatusFilter.ALL ||
            s.formatFilter.isNotBlank() ||
            s.selectedShelfId.isNotBlank() ||
            s.selectedCategoryId.isNotBlank() ||
            s.selectedTagIds.isNotEmpty() ||
            _searchQuery.value.isNotBlank()
    }

    /** 把当前筛选组合保存为具名动态视图；同名覆盖。 */
    fun saveCurrentFilter(name: String, onDone: (Boolean) -> Unit = {}) {
        if (!hasActiveFilters()) { onDone(false); return }
        val s = _session.value
        val snapshot = SavedShelfFilter(
            name = name,
            statusFilter = s.statusFilter.name,
            formatFilter = s.formatFilter,
            selectedShelfId = s.selectedShelfId,
            selectedCategoryId = s.selectedCategoryId,
            selectedTagIds = s.selectedTagIds.toList(),
            searchQuery = _searchQuery.value,
            createdAt = java.time.Instant.now().toString(),
        )
        viewModelScope.launch { onDone(savedViewsStore.save(snapshot)) }
    }

    /** 套用一条动态视图：整体替换当前筛选与关键词（不是叠加）。 */
    fun applySavedFilter(filter: SavedShelfFilter) {
        _session.value = _session.value.copy(
            statusFilter = ShelfStatusFilter.entries.firstOrNull { it.name == filter.statusFilter }
                ?: ShelfStatusFilter.ALL,
            formatFilter = filter.formatFilter,
            selectedShelfId = filter.selectedShelfId,
            selectedCategoryId = filter.selectedCategoryId,
            selectedTagIds = filter.selectedTagIds.toSet(),
        )
        setSearchQuery(filter.searchQuery)
    }

    fun deleteSavedFilter(name: String) {
        viewModelScope.launch { savedViewsStore.delete(name) }
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

    /**
     * 书架库数据是否已收到**首次真实发射**（骨架门用，M-BUG-09）。
     *
     * [books] 是 `stateIn(..., emptyList())`，其合成初值与「书架真的没有书」在 UI 层**不可区分**，
     * 所以 Route 不能拿 `books.isEmpty()` 当「还没加载」用。冷启动时 Room 首读若慢于骨架超时，
     * 书架会先渲染空态「书架还空着」再跳到真实列表（真机可复现）。
     *
     * 因此在**冷流源头**翻一次标志位：只有 `observeBooks()` 真正吐过一次值（哪怕空表）才算加载完成。
     * 用 `onEach` 挂在 `stateIn` 之前，不额外订阅一次 Room 查询。
     *
     * 置真后**不回落**：ViewModel 存活期间数据已就绪，反复进出书架不该再闪骨架。
     */
    private val _libraryLoaded = MutableStateFlow(false)
    val libraryLoaded: StateFlow<Boolean> = _libraryLoaded.asStateFlow()

    val books: StateFlow<List<BookEntity>> = repository.observeBooks()
        .onEach { _libraryLoaded.value = true }
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
            // 书架与首页共用 books 表，但卡片还要经过投影/排序。让这条派生流在
            // ViewModel 创建后立即订阅，避免首个 UI collector 的生命周期切换使它
            // 长时间停留在 emptyList；之后必须等一次导入写入才收到 Room 失效通知。
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    // ===== 导入（真实 SAF 导入 / 文件夹扫描 / 修复 / 历史）—— 委托 ShelfImporter =====
    val importTasks: StateFlow<List<ImportTaskUi>> = importer.importTasks
    val importBatch: StateFlow<ImportBatchUiState> = importer.importBatch
    val importHistory: StateFlow<List<ImportHistoryEntry>> = importer.importHistory

    /**
     * 尚未打开过的新导入书籍 id（书卡「新」角标）。
     *
     * 持久化在 [ShelfPrefs]：导入后直接杀进程再打开，角标仍在，
     * 否则「哪几本是刚加的」这个信息会在重启后丢失。
     */
    val newBookIds: StateFlow<Set<String>> = shelfPrefs.newBookIds

    /** 用户打开某本书后清除其「新」角标。 */
    fun clearNewBookId(id: String) = viewModelScope.launch { shelfPrefs.clearNewBookId(id) }

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

    fun markImportBatchSummaryNotified(batchId: String) =
        importer.markImportBatchSummaryNotified(batchId)

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

    /**
     * 批量删除：一次事务、一张撤销凭证。
     *
     * 入口提示「已删除 N 本」时撤销恢复的正是这 N 本；不用 `forEach { deleteBook(it) }`，
     * 那会登记多张凭证，一次撤销只拿回其中一批。撤销提示本身由 [deletionUndoOffers] 驱动。
     */
    fun deleteBooks(ids: Collection<String>, onResult: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
            // 不用 runCatching：它会把协程取消也吞成「删除失败」，让已取消的作用域继续跑。
            val ok = try {
                bookActions.deleteBooks(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                false
            }
            onResult(ok)
        }

    /** 撤销一次删除；结果交由界面层转成文案，因为「恢复了多少」必须如实分项说明。 */
    fun undoDeletion(credentialId: String, onResult: (DeletionUndoOutcome) -> Unit) =
        viewModelScope.launch {
            val outcome = bookActions.undoDeletion(credentialId)
                ?: DeletionUndoOutcome.NOTHING_TO_UNDO
            onResult(outcome)
        }

    /** 用户关闭撤销提示条：放弃撤销，不动数据。 */
    fun dismissDeletionUndo(credentialId: String) {
        bookActions.dismissDeletionUndo(credentialId)
    }

    /** 倒计时归零时清理凭证，避免提示条滞留在界面上。 */
    fun pruneDeletionUndo() {
        deletions?.prune()
    }

    /**
     * 无凭证时的撤销兜底：按书籍行的删除时间戳恢复同批软删除的资料。
     *
     * 语义正确（不会复活本次操作之前就删掉的笔记/高亮），但分类、标签、书单、已读章节
     * 在删除时是硬删除，这条路径带不回来；需要完整撤销请走 [undoDeletion]。
     */
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

    /** 标记单本为已读完。 */
    fun markFinished(id: String, onResult: (String) -> Unit = {}) = viewModelScope.launch {
        onResult(bookActions.markFinished(id))
    }

    /** 取消单本的已读完状态。 */
    fun markReading(id: String, onResult: (String) -> Unit = {}) = viewModelScope.launch {
        onResult(bookActions.markReading(id))
    }

    /** 批量标记读完（书架编辑模式的批量动作）。 */
    fun markFinishedBatch(ids: Collection<String>, onResult: (String) -> Unit = {}) = viewModelScope.launch {
        onResult(bookActions.markFinishedBatch(ids))
    }

    /** 批量取消读完。 */
    fun markReadingBatch(ids: Collection<String>, onResult: (String) -> Unit = {}) = viewModelScope.launch {
        onResult(bookActions.markReadingBatch(ids))
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

    // 来源更新前置确认状态（S04-1）：检测到有源文件变动的书籍列表；非 null 时提示用户确认是否覆盖正文
    private val _pendingSourceRefreshIds = MutableStateFlow<List<String>?>(null)
    val pendingSourceRefreshIds: StateFlow<List<String>?> = _pendingSourceRefreshIds.asStateFlow()

    /**
     * 下拉刷新。做三件事，全程保持刷新指示器可见：
     * 1. 重采底层 Room 查询（让外部写入 / 同步落地后立即反映）；
     * 2. 检测来源文件是否在系统目录被改过；
     * 3. **前置一次性确认**：检测到有更新时不直接替换正文，而是进入待确认状态（[pendingSourceRefreshIds]），
     *    待用户确认后才批量执行刷新，避免误刷新覆盖阅读进度（S04-1）。
     *
     * [onResult] 把结果交给 Snackbar。**无更新时也必须给出明确反馈**，否则用户无法
     * 区分「确实没有更新」与「手势没生效 / 来源没记录」；有更新时由确认弹窗接管。
     */
    fun refresh(onResult: (String) -> Unit = {}) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                runCatching {
                    withContext(ioDispatcher) {
                        repository.observeBooks().first()
                        repository.observeProgress().first()
                    }
                }
                delay(350) // 保证刷新反馈有最短可见时长
                checkUpdatedSources(onResult)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * 检测来源文件是否有更新。无更新时直接给出 Snackbar 反馈；有更新时打「可更新」标并发出确认请求。
     */
    private suspend fun checkUpdatedSources(onResult: (String) -> Unit) {
        val refs = try {
            sourceIndex.snapshot()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onResult("刷新失败：无法读取来源索引")
            return
        }
        if (refs.isEmpty()) {
            _contentUpdatedBookIds.value = emptySet()
            _pendingSourceRefreshIds.value = null
            onResult("没有可同步的来源记录（这些书是用系统选择器导入的，无法自动定位原文件）")
            return
        }
        val updated = try {
            sourceUpdateChecker.checkUpdated(refs)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onResult("刷新失败：无法读取来源文件状态")
            return
        }
        if (updated.isEmpty()) {
            _contentUpdatedBookIds.value = emptySet()
            _pendingSourceRefreshIds.value = null
            onResult("书架已是最新，没有检测到源文件更新")
            return
        }
        // 有源文件更新：打标并发出前置确认请求，绝不直接替换正文（S04-1）
        _contentUpdatedBookIds.value = updated
        _pendingSourceRefreshIds.value = updated.toList()
    }

    /**
     * 用户确认后执行正文同步（S04-1）。
     *
     * 串行调用 [importer.refreshFromSource] 依次替换正文并推进基线，完成后给出汇总反馈。
     * 汇总由 [buildSourceRefreshSummary] 构造：失败书名 + 首条原因（S04，残留场景
     * 必须让用户读到「本次已停止、重试无效」，且多书失败时控制总长度）。
     */
    fun confirmSourceRefresh(
        bookIds: Collection<String>? = null,
        onResult: (String) -> Unit = {},
    ) = viewModelScope.launch {
        val targets = bookIds ?: _pendingSourceRefreshIds.value ?: return@launch
        if (targets.isEmpty()) return@launch
        _pendingSourceRefreshIds.value = null

        val titleById = uiState.value.library.books.associate { it.id to it.title }
        var succeeded = 0
        var unlocatable = 0
        // S04：失败不再只留书名——底层原因（如替换残留）必须随汇总带出，
        // 否则用户只看到「N 本刷新失败」却不知道本次已停止、重试无效。
        val failures = mutableListOf<SourceRefreshFailureUi>()
        for (bookId in targets) {
            when (val result = importer.refreshFromSource(bookId)) {
                is SourceRefreshResult.Success -> succeeded++
                SourceRefreshResult.NoSourceRef -> unlocatable++
                is SourceRefreshResult.Failed -> failures += SourceRefreshFailureUi(
                    title = titleById[bookId] ?: "未知书籍",
                    reason = result.message,
                )
            }
        }
        refreshSourceUpdates()
        onResult(buildSourceRefreshSummary(succeeded, unlocatable, failures))
    }

    /**
     * 用户取消刷新确认：关闭确认状态，保持书卡角标不变，零本正文被替换（S04-1）。
     */
    fun cancelSourceRefresh() {
        _pendingSourceRefreshIds.value = null
    }

    // ===== 来源内容更新自动检测（M-BUG-03）=====
    // 「来源文件在系统目录被改过」的书，在书卡上打「可更新」标签。
    // 只读 provider 的 size/last_modified 与导入基线比对，绝不回写基线、绝不改正文；
    // 任何解析/授权失败都静默跳过（不误报）。失败不影响书架其余功能。
    private val _contentUpdatedBookIds = MutableStateFlow<Set<String>>(emptySet())
    val contentUpdatedBookIds: StateFlow<Set<String>> = _contentUpdatedBookIds.asStateFlow()
    private var sourceUpdateJob: Job? = null

    /**
     * 进书架 / 下拉刷新时调用。逐本 stat 已记录的来源文档，结果只用于打标。
     *
     * 上一次检测未结束就再次触发时取消旧任务，避免堆叠 provider 查询。
     *
     * 这里刻意**不用 `runCatching`** 包住 suspend 调用：`runCatching` 会把
     * `CancellationException` 一并吞掉，于是被取消的旧任务会在取消瞬间把结果写成空集，
     * 有机会覆盖新任务刚写入的结果（并破坏结构化并发）。改为显式区分取消与真实失败。
     */
    fun refreshSourceUpdates() {
        sourceUpdateJob?.cancel()
        sourceUpdateJob = viewModelScope.launch {
            val refs = try {
                sourceIndex.snapshot()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (refs.isEmpty()) {
                _contentUpdatedBookIds.value = emptySet()
                return@launch
            }
            val updated = try {
                sourceUpdateChecker.checkUpdated(refs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptySet()
            }
            _contentUpdatedBookIds.value = updated
        }
    }

    init {
        importer.booksProvider = { books.value }
        // 成功导入一本新书即登记「新」角标。写 DataStore 是短事务，
        // 即使用户在导入途中切走页面，已发出的写入仍会完成。
        importer.onBookImported = { bookId -> viewModelScope.launch { shelfPrefs.addNewBookIds(listOf(bookId)) } }
        viewModelScope.launch {
            shelfViewMode.collect { raw ->
                val value = ShelfViewMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ShelfViewMode.GRID
                _session.value = _session.value.copy(viewMode = value)
            }
        }
        viewModelScope.launch {
            shelfSortMode.collect { raw ->
                // 兜底与 ShelfPrefs.DEFAULT_SORT_MODE 保持一致：未读新书 last_read_at 为空，
                // 按 RECENT 倒序会沉底，故默认取 IMPORTED（M-FEAT-01）。
                val value = ShelfSortMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ShelfSortMode.IMPORTED
                _session.value = _session.value.copy(sortMode = value)
            }
        }

        // EPUB size 修复已收敛为进程级一次性任务（EpubSizeRepairTask，App.onCreate 启动），
        // 不再由每个 ShelfViewModel 实例 init 重复触发。

        // 进书架即做一次来源内容更新检测（M-BUG-03）。只 stat 单文档、串行、失败静默，
        // 不阻塞列表首帧（在独立协程里跑，结果到达后自然刷新标签）。
        refreshSourceUpdates()
    }
}
