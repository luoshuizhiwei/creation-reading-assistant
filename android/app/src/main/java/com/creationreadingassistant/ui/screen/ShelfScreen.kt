package com.creationreadingassistant.ui.screen

import androidx.compose.foundation.background
import java.time.LocalDate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info

import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.AppShapes
import com.creationreadingassistant.ui.theme.ListSkeleton
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.ProgressBarShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.theme.rememberHaptic
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.offset
import androidx.compose.material.pullrefresh.PullRefreshDefaults
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.settings.ImportHistoryEntry
import com.creationreadingassistant.data.settings.ImportHistoryStore
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.theme.AppWarning
import com.creationreadingassistant.ui.viewmodel.ImportTaskUi
import com.creationreadingassistant.ui.viewmodel.ImportBatchUiState
import com.creationreadingassistant.ui.viewmodel.ShelfBookItem
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel
import com.creationreadingassistant.feature.reader.hasLocalBookSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
// B2 拆分：子 Composable 与跨文件工具函数已迁移至 ui/screen/shelf/ 子包（internal 可见）
import com.creationreadingassistant.ui.screen.shelf.ActiveFilterNote
import com.creationreadingassistant.ui.screen.shelf.BatchActionBar
import com.creationreadingassistant.ui.screen.shelf.BatchSheet
import com.creationreadingassistant.ui.screen.shelf.BookActionSheet
import com.creationreadingassistant.ui.screen.shelf.BookDetailSheet
import com.creationreadingassistant.ui.screen.shelf.BookGrid
import com.creationreadingassistant.ui.screen.shelf.DesktopBooksSheet
import com.creationreadingassistant.ui.screen.shelf.FilterSheet
import com.creationreadingassistant.ui.screen.shelf.ImportHistorySheet
import com.creationreadingassistant.ui.screen.shelf.ImportQueueCard
import com.creationreadingassistant.ui.screen.shelf.ImportSourceSheet
import com.creationreadingassistant.ui.screen.shelf.SelectionBar
import com.creationreadingassistant.ui.screen.shelf.ShelfHeader
import com.creationreadingassistant.ui.screen.shelf.ShelfRefreshIndicator
import com.creationreadingassistant.ui.screen.shelf.SortSheet
import com.creationreadingassistant.ui.screen.shelf.StatusRail
import com.creationreadingassistant.ui.screen.shelf.Toolbar
import com.creationreadingassistant.ui.screen.shelf.generateTextCoverDataUrl

private const val DEBOUNCE_SEARCH_MS = 300L

internal enum class ShelfSortMode { RECENT, IMPORTED, TITLE, PROGRESS }
internal enum class ShelfViewMode { GRID, LIST }
internal enum class BatchSheetKind { SHELF, CATEGORY, TAG }

/** 对齐 web shelfStatusOptions（全部/在读/已完成/未开始/本机可读）。 */
internal enum class ShelfStatusFilter { ALL, READING, COMPLETED, UNREAD, READABLE }

/** 对齐 book-status.ts 的就绪状态判定（原生仅用已存在字段）。 */
internal enum class ReadinessTone { READY, CLOUD, ERROR }

internal data class BookReadiness(val label: String, val tone: ReadinessTone)

internal fun BookEntity.isDownloaded(): Boolean {
    return hasLocalBookSource()
}

internal fun BookEntity.readiness(): BookReadiness {
    if (content_status == "failed") return BookReadiness("正文保存失败", ReadinessTone.ERROR)
    if (content_status == "missing") return BookReadiness("正文未在本机", ReadinessTone.CLOUD)
    if (content_status == "downloading") return BookReadiness("正文下载中", ReadinessTone.CLOUD)
    if (isDownloaded()) return BookReadiness("可离线阅读", ReadinessTone.READY)
    if (size <= 0) return BookReadiness("正文为空", ReadinessTone.ERROR)
    return BookReadiness("需下载正文", ReadinessTone.CLOUD)
}

internal fun progressFor(map: Map<String, ReadingProgressEntity>, id: String): Float =
    (map[id]?.progress_percent ?: 0f).coerceIn(0f, 100f)

/** 对齐 book-status.ts 的「阅读状态」判定，用于状态筛选条。 */
internal fun bookStatus(book: BookEntity, percent: Float): ShelfStatusFilter {
    val p = percent.coerceIn(0f, 100f)
    return when {
        book.isDownloaded() -> ShelfStatusFilter.READABLE
        p >= 99.5f -> ShelfStatusFilter.COMPLETED
        p > 0f -> ShelfStatusFilter.READING
        else -> ShelfStatusFilter.UNREAD
    }
}

/** 仅做筛选，不做排序；排序已在 ViewModel 后台完成。 */
internal fun filterItems(
    items: List<ShelfBookItem>,
    query: String,
    statusFilter: ShelfStatusFilter,
    progressById: Map<String, ReadingProgressEntity>,
    allowedBookIds: Set<String>?,
): List<ShelfBookItem> {
    val trimmed = query.trim()
    // 常见路径快速返回：避免遍历整库，排序切换时直接复用 ViewModel 后台已排序的列表。
    if (trimmed.isEmpty() && statusFilter == ShelfStatusFilter.ALL && allowedBookIds == null) {
        return items
    }
    val lower = trimmed.lowercase()
    return items.filter { item ->
        val book = item.book
        val matchesQuery = lower.isEmpty() ||
            "${book.title} ${book.author ?: ""} ${book.original_file_name ?: ""}".lowercase().contains(lower)
        val matchesFilter = allowedBookIds?.contains(book.id) ?: true
        val matchesStatus = statusFilter == ShelfStatusFilter.ALL ||
            bookStatus(book, progressFor(progressById, book.id)) == statusFilter
        matchesQuery && matchesFilter && matchesStatus
    }
}

@Composable
internal fun toneColor(tone: ReadinessTone): Color = when (tone) {
    ReadinessTone.READY -> AppSuccess
    ReadinessTone.CLOUD -> MaterialTheme.colorScheme.primary
    ReadinessTone.ERROR -> AppError
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ShelfScreen(
    navController: NavHostController,
    viewModel: ShelfViewModel = hiltViewModel(),
    /** H4：从首页继续阅读「查看详情」跳入时，打开该书的书籍详情面板。 */
    initialDetailBookId: String? = null,
) {
    val taxonomyVm: com.creationreadingassistant.ui.viewmodel.TaxonomyViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shelfBooks by viewModel.shelfBooks.collectAsStateWithLifecycle()
    val books = uiState.library.books
    val tags = uiState.library.tags
    val categories = uiState.library.categories
    val shelves = uiState.library.shelves
    val progressById = uiState.library.progressById
    val sessionsByBook = uiState.activity.sessionsByBook
    val notesByBook = uiState.activity.notesByBook
    val highlightsByBook = uiState.activity.highlightsByBook
    val inspirationsByBook = uiState.activity.inspirationsByBook
    val importTasks = uiState.auxiliary.importTasks
    val importBatch = uiState.auxiliary.importBatch
    val downloadingIds = uiState.auxiliary.downloadingIds
    val importHistory = uiState.auxiliary.importHistory
    val savedViewMode = uiState.auxiliary.savedViewMode
    val savedSortMode = uiState.auxiliary.savedSortMode
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf(ShelfViewMode.GRID) }
    var sortMode by remember { mutableStateOf(ShelfSortMode.RECENT) }
    var statusFilter by remember { mutableStateOf(ShelfStatusFilter.ALL) }

    // S7：视图/排序偏好持久化（对齐网页 localStorage）
    var viewModeInit by remember { mutableStateOf(false) }
    var sortModeInit by remember { mutableStateOf(false) }
    LaunchedEffect(savedViewMode) {
        if (!viewModeInit && savedViewMode in listOf("grid", "list")) {
            viewMode = ShelfViewMode.valueOf(savedViewMode.uppercase())
            viewModeInit = true
        }
    }
    LaunchedEffect(savedSortMode) {
        if (!sortModeInit && savedSortMode in listOf("recent", "imported", "title", "progress")) {
            sortMode = ShelfSortMode.valueOf(savedSortMode.uppercase())
            sortModeInit = true
        }
    }
    LaunchedEffect(viewMode) { viewModel.setShelfViewMode(viewMode.name) }
    LaunchedEffect(sortMode) { viewModel.setShelfSortMode(sortMode.name) }
    var searchActive by remember { mutableStateOf(false) }
    var showPageMenu by remember { mutableStateOf(false) }
    var selectedShelfId by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") }
    var selectedTagId by remember { mutableStateOf("") }
    var detailBookId by remember { mutableStateOf(initialDetailBookId) }
    var actionBookId by remember { mutableStateOf<String?>(null) }

    val detailShelfIds by taxonomyVm.observeShelfIdsForBook(detailBookId ?: "")
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val detailCategoryIds by taxonomyVm.observeCategoryIdsForBook(detailBookId ?: "")
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val detailTagIds by taxonomyVm.observeTagIdsForBook(detailBookId ?: "")
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var batchSheet by remember { mutableStateOf<BatchSheetKind?>(null) }
    var showImportHistory by remember { mutableStateOf(false) }
    var showImportSource by remember { mutableStateOf(false) }
    var showFilterPanel by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showDesktopBooks by remember { mutableStateOf(false) }
    var confirmDeleteIds by remember { mutableStateOf<List<String>?>(null) }
    var filteredBookIds by remember { mutableStateOf<Set<String>?>(null) }

    // A 档打磨：首屏加载占位 + 系统「减少动态效果」感知
    val layout = LocalLayoutTokens.current
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(350); firstLoad = false }
    LaunchedEffect(books) { if (books.isNotEmpty()) firstLoad = false }
    val showSkeleton = firstLoad && books.isEmpty()

    // B3：导入任务由 processing → done 完成时触感确认（首帧不响，仅在新增完成时触发）
    var importDoneSeeded by remember { mutableStateOf(false) }
    val prevImportDoneIds = remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(importTasks) {
        val doneIds = importTasks.filter { it.status == "done" }.map { it.id }.toSet()
        val newlyDone = doneIds - prevImportDoneIds.value
        if (newlyDone.isNotEmpty() && importDoneSeeded) {
            haptic(HapticFeedbackType.LongPress)
        }
        prevImportDoneIds.value = doneIds
        importDoneSeeded = true
    }

    // B2：主书架页下拉刷新
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
    )
    val pullThresholdPx = with(LocalDensity.current) { PullRefreshDefaults.RefreshThreshold.toPx() }
    var reselectBookId by remember { mutableStateOf<String?>(null) }
    var coverBookId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun showMessage(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }

    val importFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let(viewModel::importFolder)
    }

    val reselectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val bid = reselectBookId
        if (uri != null && bid != null) {
            viewModel.reselectFile(bid, uri) { showMessage(it) }
        }
        reselectBookId = null
    }

    val coverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val bid = coverBookId
        if (uri != null && bid != null) {
            viewModel.updateBookCover(bid, uri) { showMessage(it) }
        }
        coverBookId = null
    }
    fun enterSelection() { selectionMode = true; selectedIds.clear(); showPageMenu = false }
    fun exitSelection() { selectionMode = false; selectedIds.clear(); batchSheet = null }
    fun toggleSelected(id: String) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
    }

    LaunchedEffect(query) { delay(DEBOUNCE_SEARCH_MS); debouncedQuery = query }

    LaunchedEffect(importBatch.id, importBatch.isRunning) {
        if (importBatch.id.isNotBlank() && !importBatch.isRunning && importBatch.hasResult) {
            showImportHistory = true
        }
    }

    LaunchedEffect(selectedShelfId, selectedCategoryId, selectedTagId, books) {
        val byShelf = if (selectedShelfId.isEmpty()) null else taxonomyVm.getBookIdsByShelf(selectedShelfId).toSet()
        val byCategory = if (selectedCategoryId.isEmpty()) null else taxonomyVm.getBookIdsByCategory(selectedCategoryId).toSet()
        val byTag = if (selectedTagId.isEmpty()) null else taxonomyVm.getBookIdsByTag(selectedTagId).toSet()
        val filters = listOfNotNull(byShelf, byCategory, byTag)
        filteredBookIds = if (filters.isEmpty()) null else filters.reduce { acc, set -> acc.intersect(set) }
    }

    // 只在依赖变化时重新筛选，并复用 filterItems 的快速路径避免排序时遍历整库。
    val filtered = remember(shelfBooks, debouncedQuery, statusFilter, progressById, filteredBookIds) {
        filterItems(shelfBooks, debouncedQuery, statusFilter, progressById, filteredBookIds)
    }
    val hasActiveImports = importBatch.isRunning || importTasks.any { it.status == "processing" }
    val detailBook = detailBookId?.let { books.find { b -> b.id == it } }
    val actionBook = actionBookId?.let { books.find { b -> b.id == it } }
    val selectedBooks = books.filter { selectedIds.contains(it.id) }
    val selectedBookIds = selectedBooks.map { it.id }
    val showFilterButton = shelves.isNotEmpty() || categories.isNotEmpty() || tags.isNotEmpty()

    fun requestDelete(ids: List<String>) { confirmDeleteIds = ids }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 书架顶栏移入下拉内容区首子：不再独占 Scaffold 的 topBar 上层，
            // 与指示器同处内容层、且指示器最后绘制，故永不被固定顶栏遮挡。
            ShelfHeader(
                selectionMode = selectionMode,
                searchActive = searchActive,
                query = query,
                onQueryChange = { query = it },
                onOpenSearch = { searchActive = true },
                onExitSearch = { searchActive = false; query = "" },
                selectedCount = selectedIds.size,
                hasActiveImports = hasActiveImports,
                showPageMenu = showPageMenu,
                onTogglePageMenu = { showPageMenu = !showPageMenu },
                onImport = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    showImportSource = true
                },
                onEnterSelection = ::enterSelection,
                onExitSelection = ::exitSelection,
                importBadge = importTasks.size,
                onOpenImportHistory = { showPageMenu = false; showImportHistory = true },
                onClosePageMenu = { showPageMenu = false },
                onOpenDesktopBooks = { showPageMenu = false; showDesktopBooks = true },
            )
            if (hasActiveImports && !selectionMode) {
                ImportQueueCard(
                    tasks = importTasks.filter { it.status == "processing" },
                    batch = importBatch,
                )
            }

            if (selectionMode) {
                SelectionBar(
                    selectedCount = selectedIds.size,
                    visibleCount = filtered.size,
                    onSelectAll = { selectedIds.clear(); selectedIds.addAll(filtered.map { it.bookId }) },
                    onClear = { selectedIds.clear() },
                )
            } else {
                StatusRail(statusFilter = statusFilter, onSelect = { statusFilter = it })
                Toolbar(
                    totalCount = books.size,
                    showFilterButton = showFilterButton,
                    sortMode = sortMode,
                    viewMode = viewMode,
                    onOpenSort = { showSortSheet = true },
                    onOpenFilter = { showFilterPanel = true },
                    onToggleView = { viewMode = if (viewMode == ShelfViewMode.GRID) ShelfViewMode.LIST else ShelfViewMode.GRID },
                )
                if (selectedShelfId.isNotEmpty() || selectedCategoryId.isNotEmpty() || selectedTagId.isNotEmpty() || statusFilter != ShelfStatusFilter.ALL || debouncedQuery.isNotEmpty()) {
                    ActiveFilterNote(
                        onReset = {
                            selectedShelfId = ""; selectedCategoryId = ""; selectedTagId = ""; statusFilter = ShelfStatusFilter.ALL; query = ""
                        },
                    )
                }
            }

            if (showSkeleton) {
                ListSkeleton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap),
                    count = if (viewMode == ShelfViewMode.GRID) 6 else 5,
                    reducedMotion = reducedMotion,
                )
            } else if (filtered.isEmpty()) {
                val emptyShelf = books.isEmpty()
                val searchNoResult = books.isNotEmpty() && debouncedQuery.isNotEmpty()
                FullEmptyState(
                    icon = { LineArtBook(modifier = Modifier.size(56.dp)) },
                    title = if (emptyShelf) "书架还空着" else if (searchNoResult) "没有找到匹配的书" else "当前筛选下没有书籍",
                    body = if (emptyShelf) "导入 TXT、Markdown 或 EPUB 开始本地阅读。"
                    else if (searchNoResult) "换个书名或作者试试。" else "切换到“全部”，或清除书单、分类和标签筛选。",
                    contentPadding = 32.dp,
                    primaryAction = if (emptyShelf) "导入一本书" to {
                        haptic(HapticFeedbackType.TextHandleMove)
                        showImportSource = true
                    } else null,
                    secondaryAction = if (emptyShelf || searchNoResult) null else "显示全部" to {
                        selectedShelfId = ""; selectedCategoryId = ""; selectedTagId = ""; statusFilter = ShelfStatusFilter.ALL; query = ""
                    },
                )
            } else {
                val baseViewConfig = LocalViewConfiguration.current
                val longPressConfig = remember(baseViewConfig) {
                    object : ViewConfiguration by baseViewConfig {
                        override val longPressTimeoutMillis: Long get() = 500L
                    }
                }
                CompositionLocalProvider(LocalViewConfiguration provides longPressConfig) {
                // 稳定事件回调：避免 ShelfScreen 因搜索/弹层等状态变化重组时，BookGrid 子项被误判为需要重组。
                val onOpenBook: (BookEntity) -> Unit by rememberUpdatedState {
                    val book = it
                    if (book.readiness().tone == ReadinessTone.READY) {
                        navController.navigate("reader/${book.id}")
                    } else {
                        showMessage("《${book.title}》暂无可离线正文，请先导入或下载。")
                    }
                }
                val onToggleActions: (String) -> Unit by rememberUpdatedState { id ->
                    actionBookId = if (actionBookId == id) null else id
                }
                val onToggleSelected: (String) -> Unit by rememberUpdatedState { toggleSelected(it) }
                // 选择集合 snapshot：SnapshotStateList 是不稳定类型，转成稳定 Set 后 Lazy 子项只在选中状态变化时重组。
                val selectedIdSet by remember { derivedStateOf { selectedIds.toSet() } }
                BookGrid(
                    books = filtered,
                    progressById = progressById,
                    viewMode = viewMode,
                    selectionMode = selectionMode,
                    selectedIds = selectedIdSet,
                    actionBookId = actionBookId,
                    downloadingIds = downloadingIds,
                    onOpenBook = onOpenBook,
                    onToggleActions = onToggleActions,
                    onToggleSelected = onToggleSelected,
                )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "共 ${books.size} 本书籍 · 当前显示 ${filtered.size} 本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
            ShelfRefreshIndicator(
                state = pullRefreshState,
                refreshing = isRefreshing,
                thresholdPx = pullThresholdPx,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    // header 现已在 pullRefresh 内容首子内，指示器恒绘制在内容上方（含 header 之上），永不被遮挡
                    .offset { IntOffset(0, (if (isRefreshing) pullThresholdPx else pullRefreshState.progress * pullThresholdPx).roundToInt()) },
            )
        }
    }

    // 底部批量操作栏
    if (selectionMode) {
        BatchActionBar(
            selectedCount = selectedIds.size,
            onAddToShelf = { batchSheet = BatchSheetKind.SHELF },
            onSetCategory = { batchSheet = BatchSheetKind.CATEGORY },
            onTag = { batchSheet = BatchSheetKind.TAG },
            onDownload = { selectedBooks.forEach { b -> viewModel.downloadBookContent(b.id) { showMessage(it) } } },
            onClearCache = {
                if (selectedBookIds.isNotEmpty()) {
                    viewModel.clearCacheForBooks(selectedBookIds) { showMessage(it) }
                    exitSelection()
                } else {
                    showMessage("请先选择要清理缓存的书籍")
                }
            },
            onDelete = { requestDelete(selectedIds.toList()) },
        )
    }

    // 书籍操作弹层
    if (actionBook != null) {
        BookActionSheet(
            book = actionBook,
            progressById = progressById,
            onDismiss = { actionBookId = null },
            onContinue = {
                if (it.readiness().tone == ReadinessTone.READY) navController.navigate("reader/${it.id}")
                else showMessage("《${it.title}》暂无可离线正文，请先导入或下载。")
                actionBookId = null
            },
            onDownload = { viewModel.downloadBookContent(it.id) { showMessage(it) } },
            onRepair = {
                actionBookId = null
                reselectBookId = it.id
                reselectLauncher.launch(arrayOf("application/epub+zip", "text/plain", "text/markdown"))
            },
            onOpenDetail = { id -> actionBookId = null; detailBookId = id },
            onDelete = { id -> actionBookId = null; requestDelete(listOf(id)) },
        )
    }

    // 书籍详情弹层
    if (detailBook != null) {
        val currentDetailBook = detailBook
        BookDetailSheet(
            book = currentDetailBook,
            progressById = progressById,
            sessions = sessionsByBook[currentDetailBook.id] ?: emptyList(),
            notes = notesByBook[currentDetailBook.id] ?: emptyList(),
            highlights = highlightsByBook[currentDetailBook.id] ?: emptyList(),
            inspirations = inspirationsByBook[currentDetailBook.id] ?: emptyList(),
            shelves = shelves.filter { detailShelfIds.contains(it.id) },
            categories = categories.filter { detailCategoryIds.contains(it.id) },
            allTags = tags,
            assignedTagIds = detailTagIds,
            onDismiss = { detailBookId = null },
            onContinue = {
                if (it.readiness().tone == ReadinessTone.READY) navController.navigate("reader/${it.id}")
                else showMessage("《${it.title}》暂无可离线正文，请先导入或下载。")
                detailBookId = null
            },
            onDownload = { viewModel.downloadBookContent(it.id) { showMessage(it) } },
            onDelete = { id -> detailBookId = null; requestDelete(listOf(id)) },
            onMessage = ::showMessage,
            onRemoveShelf = { taxonomyVm.removeBookFromShelf(currentDetailBook.id, it); showMessage("已移除书单") },
            onRemoveCategory = { taxonomyVm.removeCategoryFromBook(currentDetailBook.id, it); showMessage("已移除分类") },
            onRemoveTag = { taxonomyVm.removeTagFromBook(currentDetailBook.id, it); showMessage("已移除标签") },
            onAddTag = { tagId ->
                taxonomyVm.addTagToBook(currentDetailBook.id, tagId)
                showMessage("已给《${currentDetailBook.title}》添加标签")
            },
            onUpdateBook = { title, author, description ->
                viewModel.updateBookInfo(currentDetailBook.id, title, author, description) { showMessage(it) }
            },
            onChangeCover = {
                coverBookId = currentDetailBook.id
                coverLauncher.launch(arrayOf("image/*"))
            },
            onChangeTextCover = {
                viewModel.setBookTextCover(currentDetailBook.id, generateTextCoverDataUrl(currentDetailBook.title)) { showMessage(it) }
            },
            onResetCover = {
                viewModel.resetBookCover(currentDetailBook.id) { showMessage(it) }
            },
        )
    }

    // 排序弹层
    if (showSortSheet) {
        SortSheet(
            current = sortMode,
            onSelect = { mode -> sortMode = mode; showSortSheet = false },
            onDismiss = { showSortSheet = false },
        )
    }

    // 筛选弹层（分类/标签/书单映射降级：仅展示，不改变列表）
    if (showFilterPanel) {
        FilterSheet(
            shelves = shelves,
            categories = categories,
            tags = tags,
            selectedShelfId = selectedShelfId,
            selectedCategoryId = selectedCategoryId,
            selectedTagId = selectedTagId,
            onSelectShelf = { selectedShelfId = if (selectedShelfId == it) "" else it },
            onSelectCategory = { selectedCategoryId = if (selectedCategoryId == it) "" else it },
            onSelectTag = { selectedTagId = if (selectedTagId == it) "" else it },
            onDismiss = { showFilterPanel = false },
        )
    }

    // 批量编辑弹层
    val currentBatch = batchSheet
    if (currentBatch != null) {
        val batchBookIds = selectedBooks.map { it.id }
        BatchSheet(
            kind = currentBatch,
            shelves = shelves,
            categories = categories,
            tags = tags,
            onDismiss = { batchSheet = null },
            onSelect = { id ->
                when (currentBatch) {
                    BatchSheetKind.SHELF -> taxonomyVm.addBooksToShelf(batchBookIds, id)
                    BatchSheetKind.CATEGORY -> taxonomyVm.setCategoryForBooks(batchBookIds, id)
                    BatchSheetKind.TAG -> taxonomyVm.addTagToBooks(batchBookIds, id)
                }
                exitSelection(); showMessage("已更新")
            },
            onRemoveTag = { id ->
                batchBookIds.forEach { taxonomyVm.removeTagFromBook(it, id) }
                exitSelection(); showMessage("已移除标签")
            },
            onCreate = { name ->
                when (currentBatch) {
                    BatchSheetKind.SHELF -> scope.launch {
                        val id = taxonomyVm.createShelfAndGetId(name)
                        taxonomyVm.addBooksToShelf(batchBookIds, id)
                        exitSelection()
                        showMessage("已创建书单并添加选中书籍")
                    }
                    BatchSheetKind.CATEGORY -> scope.launch {
                        val id = taxonomyVm.createCategoryAndGetId(name)
                        taxonomyVm.setCategoryForBooks(batchBookIds, id)
                        exitSelection()
                        showMessage("已创建分类并应用到选中书籍")
                    }
                    BatchSheetKind.TAG -> scope.launch {
                        val id = taxonomyVm.createTagAndGetId(name)
                        taxonomyVm.addTagToBooks(batchBookIds, id)
                        exitSelection()
                        showMessage("已创建并添加到选中书籍")
                    }
                }
            },
            showMessage = ::showMessage,
            selectedCount = selectedIds.size,
        )
    }

    // 导入历史弹层
    if (showImportHistory) {
        ImportHistorySheet(
            tasks = importTasks,
            history = importHistory,
            batch = importBatch,
            onRetryFailed = viewModel::retryFailedImports,
            onDismissBatch = viewModel::dismissImportBatchSummary,
            onClear = { viewModel.clearImportHistory() },
            onDismiss = { showImportHistory = false },
        )
    }

    if (showImportSource) {
        ImportSourceSheet(
            onSelectFiles = {
                showImportSource = false
                importLauncher.launch(arrayOf("application/epub+zip", "text/plain", "text/markdown"))
            },
            onSelectFolder = {
                showImportSource = false
                importFolderLauncher.launch(null)
            },
            onDismiss = { showImportSource = false },
        )
    }

    // 从电脑下载弹层
    if (showDesktopBooks) {
        DesktopBooksSheet(
            onDismiss = { showDesktopBooks = false },
            onDownload = { bookId -> viewModel.downloadBookContent(bookId) { showMessage(it) } },
            listBooks = { viewModel.listDesktopBooks() },
        )
    }

    // 删除确认
    val deleteIds = confirmDeleteIds
    if (deleteIds != null) {
        GlassAlertDialog(
            onDismissRequest = { confirmDeleteIds = null },
            title = { Text(if (deleteIds.size > 1) "批量删除书籍" else "删除书籍") },
            text = {
                Text(
                    if (deleteIds.size > 1) "确定从书架删除选中的 ${deleteIds.size} 本书吗？本地正文与阅读数据会一并移除。"
                    else "确定从书架删除这本书吗？本地正文与阅读数据会一并移除，此操作不可撤销。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic(HapticFeedbackType.LongPress)
                        val ids = deleteIds
                        confirmDeleteIds = null
                        ids.forEach { viewModel.deleteBook(it) }
                        if (selectionMode) exitSelection()
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = if (ids.size > 1) "已删除 ${ids.size} 本书" else "已删除该书",
                                actionLabel = "撤销",
                            )
                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                ids.forEach { viewModel.restoreBook(it) { showMessage(it) } }
                            }
                        }
                    }
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteIds = null }) { Text("取消") } },
        )
    }
}
