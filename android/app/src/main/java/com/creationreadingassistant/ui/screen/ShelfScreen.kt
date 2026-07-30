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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.FloatingActionButton
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
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.theme.ListSkeleton
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
import androidx.compose.ui.layout.ContentScale
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
import com.creationreadingassistant.ui.components.SizedAsyncImage
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.theme.AppWarning
import com.creationreadingassistant.ui.theme.SealMark
import com.creationreadingassistant.ui.viewmodel.ImportTaskUi
import com.creationreadingassistant.ui.viewmodel.ImportBatchUiState
import com.creationreadingassistant.ui.viewmodel.ShelfBookItem
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel
import com.creationreadingassistant.feature.reader.hasLocalBookSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DEBOUNCE_SEARCH_MS = 300L

enum class ShelfSortMode { RECENT, IMPORTED, TITLE, PROGRESS }
enum class ShelfViewMode { GRID, LIST }
enum class BatchSheetKind { SHELF, CATEGORY, TAG }

/** 对齐 web shelfStatusOptions（全部/在读/已完成/未开始/本机可读）。 */
enum class ShelfStatusFilter { ALL, READING, COMPLETED, UNREAD, READABLE }

private val SORT_OPTIONS = listOf(
    ShelfSortMode.RECENT to "最近阅读",
    ShelfSortMode.IMPORTED to "导入时间",
    ShelfSortMode.TITLE to "书名",
    ShelfSortMode.PROGRESS to "进度",
)

/** 对齐 book-status.ts 的就绪状态判定（原生仅用已存在字段）。 */
private enum class ReadinessTone { READY, CLOUD, ERROR }

private data class BookReadiness(val label: String, val tone: ReadinessTone)

private fun BookEntity.isDownloaded(): Boolean {
    return hasLocalBookSource()
}

private fun BookEntity.readiness(): BookReadiness {
    if (content_status == "failed") return BookReadiness("正文保存失败", ReadinessTone.ERROR)
    if (content_status == "missing") return BookReadiness("正文未在本机", ReadinessTone.CLOUD)
    if (content_status == "downloading") return BookReadiness("正文下载中", ReadinessTone.CLOUD)
    if (isDownloaded()) return BookReadiness("可离线阅读", ReadinessTone.READY)
    if (size <= 0) return BookReadiness("正文为空", ReadinessTone.ERROR)
    return BookReadiness("需下载正文", ReadinessTone.CLOUD)
}

private fun progressFor(map: Map<String, ReadingProgressEntity>, id: String): Float =
    (map[id]?.progress_percent ?: 0f).coerceIn(0f, 100f)

/** 对齐 book-status.ts 的「阅读状态」判定，用于状态筛选条。 */
private fun bookStatus(book: BookEntity, percent: Float): ShelfStatusFilter {
    val p = percent.coerceIn(0f, 100f)
    return when {
        book.isDownloaded() -> ShelfStatusFilter.READABLE
        p >= 99.5f -> ShelfStatusFilter.COMPLETED
        p > 0f -> ShelfStatusFilter.READING
        else -> ShelfStatusFilter.UNREAD
    }
}

/** 仅做筛选，不做排序；排序已在 ViewModel 后台完成。 */
private fun filterItems(
    items: List<ShelfBookItem>,
    query: String,
    statusFilter: ShelfStatusFilter,
    progressById: Map<String, ReadingProgressEntity>,
    allowedBookIds: Set<String>?,
): List<ShelfBookItem> {
    val lower = query.trim().lowercase()
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

private fun formatBytes(size: Int): String {
    if (size <= 0) return "未知"
    val kb = size / 1024.0
    return if (kb < 1024) "%.1f KB".format(kb) else "%.2f MB".format(kb / 1024)
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0 分钟"
    val minutes = ms / 60000
    val hours = minutes / 60
    return when {
        hours >= 24 -> "${hours / 24} 天 ${hours % 24} 小时"
        hours > 0 -> "$hours 小时 ${minutes % 60} 分钟"
        else -> "$minutes 分钟"
    }
}

/**
 * 生成文字封面 DataURL（SVG），对齐网页版 BookDetailSheet.generateTextCoverDataUrl。
 * 取书名前 4 个字符，使用与 BookCover 文字回退一致的稳定色相，避免独立实现算法不一致。
 */
private fun generateTextCoverDataUrl(title: String): String {
    val safeTitle = (title.ifBlank { "未命名" }).trim()
    val display = safeTitle.take(4)
    val hue = ((safeTitle.hashCode()).rem(360) + 360).rem(360)
    val bg = "hsl($hue, 42%, 45%)"
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val svg = """
        <svg xmlns="http://www.w3.org/2000/svg" width="240" height="336" viewBox="0 0 240 336">
          <rect width="240" height="336" fill="$bg"/>
          <text x="20" y="120" font-family="sans-serif" font-size="48" font-weight="700" fill="rgba(255,255,255,0.96)">${esc(display)}</text>
          <text x="20" y="312" font-family="sans-serif" font-size="16" fill="rgba(255,255,255,0.78)">${esc(safeTitle.take(12))}</text>
        </svg>
    """.trimIndent()
    return "data:image/svg+xml;utf8," + java.net.URLEncoder.encode(svg, "UTF-8").replace("+", "%20")
}

@Composable
private fun toneColor(tone: ReadinessTone): Color = when (tone) {
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    importLauncher.launch(arrayOf("application/epub+zip", "text/plain", "text/markdown"))
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "导入")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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
                EmptyState(
                    isEmptyShelf = books.isEmpty(),
                    isSearchNoResult = books.isNotEmpty() && debouncedQuery.isNotEmpty(),
                onImport = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    showImportSource = true
                },
                    onShowAll = {
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

// ===================== 顶部 Header =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelfHeader(
    selectionMode: Boolean,
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onExitSearch: () -> Unit,
    selectedCount: Int,
    hasActiveImports: Boolean,
    showPageMenu: Boolean,
    onTogglePageMenu: () -> Unit,
    onImport: () -> Unit,
    onEnterSelection: () -> Unit,
    onExitSelection: () -> Unit,
    importBadge: Int,
    onOpenImportHistory: () -> Unit,
    onClosePageMenu: () -> Unit,
    onOpenDesktopBooks: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                IconButton(onClick = onExitSelection) { Icon(Icons.Filled.Close, contentDescription = "退出多选") }
                Text("选择书籍", style = MaterialTheme.typography.titleLarge)
            } else if (searchActive) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("搜索书名、作者或文件名", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "清空搜索", modifier = Modifier.size(18.dp)) }
                }
                TextButton(onClick = onExitSearch) { Text("取消") }
            } else {
                Text(
                    "书架",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索书架", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onImport, enabled = !hasActiveImports) {
                        Icon(Icons.Filled.Add, contentDescription = "导入本地书籍", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Box {
                        IconButton(onClick = onTogglePageMenu) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = "书架更多操作", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        DropdownMenu(expanded = showPageMenu, onDismissRequest = onClosePageMenu) {
                            DropdownMenuItem(
                                text = { Text("批量管理") },
                                onClick = onEnterSelection,
                                leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                            DropdownMenuItem(
                                text = { Text("从电脑下载") },
                                onClick = onOpenDesktopBooks,
                                leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                            DropdownMenuItem(
                                text = { Text("导入历史") },
                                onClick = onOpenImportHistory,
                                leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = if (importBadge > 0) ({ Text("$importBadge") }) else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===================== 导入队列浮动卡片 =====================
@Composable
private fun ImportQueueCard(
    tasks: List<ImportTaskUi>,
    batch: ImportBatchUiState,
) {
    val progress = if (batch.total > 0) {
        batch.completed.toFloat() / batch.total.toFloat()
    } else {
        0f
    }
    SectionCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    when {
                        batch.isScanning -> "正在扫描文件夹"
                        batch.total > 0 -> "正在导入 ${batch.completed}/${batch.total}"
                        else -> "正在准备导入"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (batch.total > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            tasks.take(3).forEach { task ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(task.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(task.phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tasks.size > 3) {
                Text("还有 ${tasks.size - 3} 本等待中……", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ===================== 多选栏 =====================
@Composable
private fun SelectionBar(
    selectedCount: Int,
    visibleCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已选 $selectedCount 本", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onSelectAll, enabled = visibleCount > 0) { Text(if (visibleCount > 0) "全选($visibleCount)" else "全选") }
            TextButton(onClick = onClear, enabled = selectedCount > 0) { Text("清空") }
        }
    }
}

// ===================== 工具栏 =====================
@Composable
private fun Toolbar(
    totalCount: Int,
    showFilterButton: Boolean,
    sortMode: ShelfSortMode,
    viewMode: ShelfViewMode,
    onOpenSort: () -> Unit,
    onOpenFilter: () -> Unit,
    onToggleView: () -> Unit,
) {
    val spec = LocalComponentSpec.current
    val haptic = rememberHaptic(rememberReducedMotion())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$totalCount 本书", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onOpenSort) { Text(SORT_OPTIONS.first { it.first == sortMode }.second) }
        Spacer(modifier = Modifier.weight(1f))
        if (showFilterButton) {
            OutlinedButton(onClick = onOpenFilter, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("筛选")
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Row(
            modifier = Modifier
                .clip(spec.listItemShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            IconButton(onClick = { if (viewMode != ShelfViewMode.GRID) { haptic(HapticFeedbackType.TextHandleMove); onToggleView() } }) {
                Icon(Icons.Outlined.GridView, contentDescription = "网格视图", tint = if (viewMode == ShelfViewMode.GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { if (viewMode != ShelfViewMode.LIST) { haptic(HapticFeedbackType.TextHandleMove); onToggleView() } }) {
                Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "列表视图", tint = if (viewMode == ShelfViewMode.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ActiveFilterNote(onReset: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("当前已应用筛选", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(6.dp))
        TextButton(onClick = onReset, contentPadding = PaddingValues(0.dp)) { Text("重置全部") }
    }
}

// ===================== 状态筛选条（对齐 web .shelf-status-rail） =====================
@Composable
private fun StatusRail(
    statusFilter: ShelfStatusFilter,
    onSelect: (ShelfStatusFilter) -> Unit,
) {
    val options = listOf(
        ShelfStatusFilter.ALL to "全部",
        ShelfStatusFilter.READING to "在读",
        ShelfStatusFilter.COMPLETED to "已完成",
        ShelfStatusFilter.UNREAD to "未开始",
        ShelfStatusFilter.READABLE to "本机可读",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
            options.forEach { (value, label) ->
                val active = statusFilter == value
                SelectablePill(text = label, selected = active, onClick = { onSelect(value) })
            }
    }
}

// ===================== 书封网格 / 列表 =====================
@Composable
private fun BookGrid(
    books: List<ShelfBookItem>,
    progressById: Map<String, ReadingProgressEntity>,
    viewMode: ShelfViewMode,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    actionBookId: String?,
    downloadingIds: Set<String>,
    onOpenBook: (BookEntity) -> Unit,
    onToggleActions: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    if (viewMode == ShelfViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = layout.pageHorizontal),
            contentPadding = PaddingValues(vertical = layout.relatedGap),
            verticalArrangement = Arrangement.spacedBy(layout.gridGap),
            horizontalArrangement = Arrangement.spacedBy(layout.gridGap),
        ) {
            items(books, key = { it.bookId }) { item ->
                val book = item.book
                BookTile(
                    book = book,
                    percent = progressFor(progressById, book.id),
                    viewMode = ShelfViewMode.GRID,
                    selectionMode = selectionMode,
                    selected = selectedIds.contains(book.id),
                    actionsOpen = actionBookId == book.id,
                    downloading = downloadingIds.contains(book.id),
                    onOpenBook = onOpenBook,
                    onToggleActions = onToggleActions,
                    onToggleSelected = onToggleSelected,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = layout.pageHorizontal),
            contentPadding = PaddingValues(vertical = layout.relatedGap),
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
        ) {
            items(books, key = { it.bookId }) { item ->
                val book = item.book
                BookTile(
                    book = book,
                    percent = progressFor(progressById, book.id),
                    viewMode = ShelfViewMode.LIST,
                    selectionMode = selectionMode,
                    selected = selectedIds.contains(book.id),
                    actionsOpen = actionBookId == book.id,
                    downloading = downloadingIds.contains(book.id),
                    onOpenBook = onOpenBook,
                    onToggleActions = onToggleActions,
                    onToggleSelected = onToggleSelected,
                )
            }
        }
    }
}

// ===================== B2：下拉刷新指示器（克制细弧 + 墨线文字，非默认 spinner）=====================
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ShelfRefreshIndicator(
    state: PullRefreshState,
    refreshing: Boolean,
    thresholdPx: Float,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val color = MaterialTheme.colorScheme.primary
    val visible = refreshing || state.progress > 0.01f
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            val useIndeterminate = !reducedMotion && refreshing
            if (useIndeterminate) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = color,
                    strokeWidth = 2.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            } else {
                val p = if (reducedMotion) 1f else state.progress.coerceIn(0f, 1f)
                CircularProgressIndicator(
                    progress = { p },
                    modifier = Modifier.size(20.dp),
                    color = color,
                    strokeWidth = 2.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (refreshing) "刷新中" else "下拉刷新",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookTile(
    book: BookEntity,
    percent: Float,
    viewMode: ShelfViewMode,
    selectionMode: Boolean,
    selected: Boolean,
    actionsOpen: Boolean,
    downloading: Boolean,
    onOpenBook: (BookEntity) -> Unit,
    onToggleActions: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
) {
    val readiness = book.readiness()
    val spec = LocalComponentSpec.current
    val layout = LocalLayoutTokens.current
    val haptic = rememberHaptic(rememberReducedMotion())
    val onClick = {
        if (selectionMode) {
            haptic(HapticFeedbackType.TextHandleMove)
            onToggleSelected(book.id)
        } else onOpenBook(book)
    }
    val tileModifier = Modifier
        .fillMaxWidth()
        .semantics { contentDescription = "打开书籍" }
        .combinedClickable(
            onClick = onClick,
            onLongClick = {
                if (!selectionMode) {
                    haptic(HapticFeedbackType.LongPress)
                    onToggleActions(book.id)
                }
            },
        )

    if (viewMode == ShelfViewMode.GRID) {
        Column(
            modifier = tileModifier,
            verticalArrangement = Arrangement.spacedBy(layout.relatedGap),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(spec.cardShape),
            ) {
                BookCover(book = book, percent = percent, modifier = Modifier.fillMaxSize(), sealSize = 30.dp)
                if (downloading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .align(Alignment.TopStart),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(
                    onClick = { onToggleActions(book.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(layout.minimumTouchTarget),
                ) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "管理《${book.title}》",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val label = if (downloading) {
                "下载中…"
            } else if (readiness.tone == ReadinessTone.READY) {
                "${percent.toInt()}%"
            } else {
                readiness.label
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.relatedGap),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = toneColor(readiness.tone),
                    maxLines = 1,
                )
                ProgressLine(percent = percent, modifier = Modifier.weight(1f))
            }
        }
    } else {
        SectionCard(
            modifier = tileModifier,
            contentPadding = layout.compactCardPadding,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    BookCover(book = book, percent = percent, modifier = Modifier.size(56.dp, 78.dp))
                    if (downloading) {
                        Box(
                            modifier = Modifier
                                .size(56.dp, 78.dp)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(book.author ?: "作者未知", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Spacer(modifier = Modifier.height(4.dp))
                    ProgressLine(percent = percent)
                }
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                } else {
                    IconButton(onClick = { onToggleActions(book.id) }) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "管理《${book.title}》", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCover(
    book: BookEntity,
    percent: Float,
    modifier: Modifier = Modifier,
    sealSize: Dp = 22.dp,
) {
    val spec = LocalComponentSpec.current
    val scheme = MaterialTheme.colorScheme
    val hasImage = !book.cover_data_url.isNullOrBlank()
    // 无封面回退：取主题 surface 容器色做柔和渐变（永不用土黄/羊皮纸色），与主题一致
    val coverFallback = Brush.linearGradient(
        colorStops = arrayOf(
            0.0f to scheme.surfaceContainerHigh,
            1.0f to scheme.surfaceVariant,
        ),
    )
    // 左上→右下极淡白色斜向高光，强化实体书质感（对齐 web .book-cover 叠加层）
    val sheen = Brush.linearGradient(
        colorStops = arrayOf(
            0.0f to Color.White.copy(alpha = 0.16f),
            0.42f to Color.White.copy(alpha = 0.0f),
        ),
    )
    Box(
        modifier = modifier
            .clip(spec.listItemShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (hasImage) {
            SizedAsyncImage(
                data = book.cover_data_url,
                cacheKey = "cover:${book.id}:${book.updated_at}",
                contentDescription = "《${book.title}》封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(coverFallback)
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(book.title.take(4), color = scheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(modifier = Modifier.height(4.dp))
                Text(book.format.uppercase(), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
        // 格式角标：图片封面右下、文字回退左下（对齐 web .book-cover-image em / .book-cover > em）
        Surface(
            color = Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(if (hasImage) 6.dp else 4.dp),
            modifier = Modifier
                .align(if (hasImage) Alignment.BottomEnd else Alignment.BottomStart)
                .padding(if (hasImage) 6.dp else 4.dp),
        ) {
            Text(book.format.uppercase(), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        // 封面斜向高光叠加层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(sheen),
        )
        // 藏书印：读完才盖。必须画在高光层之上，否则会被叠加层压住。
        if (percent >= 99.5f) {
            SealMark(
                size = sealSize,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun ProgressLine(
    percent: Float,
    modifier: Modifier = Modifier,
) {
    val p = percent.coerceIn(0f, 100f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(p / 100f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

// ===================== 空状态 =====================
@Composable
private fun EmptyState(
    isEmptyShelf: Boolean,
    isSearchNoResult: Boolean,
    onImport: () -> Unit,
    onShowAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = rememberReducedMotion())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineArtBook(modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (isEmptyShelf) "书架还空着" else if (isSearchNoResult) "没有找到匹配的书" else "当前筛选下没有书籍",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (isEmptyShelf) "导入 TXT、Markdown 或 EPUB 开始本地阅读。"
            else if (isSearchNoResult) "换个书名或作者试试。" else "切换到“全部”，或清除书单、分类和标签筛选。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (isEmptyShelf) {
            Button(onClick = onImport) { Text("导入一本书") }
        } else {
            OutlinedButton(onClick = onShowAll) { Text("显示全部") }
        }
    }
}

// ===================== 批量操作栏 =====================
@Composable
private fun BatchActionBar(
    selectedCount: Int,
    onAddToShelf: () -> Unit,
    onSetCategory: () -> Unit,
    onTag: () -> Unit,
    onDownload: () -> Unit,
    onClearCache: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val enabled = selectedCount > 0
            BatchButton("加入书单", enabled = enabled, onClick = onAddToShelf)
            BatchButton("设置分类", enabled = enabled, onClick = onSetCategory)
            BatchButton("标签", enabled = enabled, onClick = onTag)
            BatchButton("下载", enabled = enabled, onClick = onDownload)
            BatchButton("清缓存", enabled = enabled, onClick = onClearCache)
            BatchButton("删除", enabled = enabled, onClick = onDelete, danger = true)
        }
    }
}

@Composable
private fun BatchButton(text: String, enabled: Boolean, onClick: () -> Unit, danger: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (danger) AppError else MaterialTheme.colorScheme.onSurface)
    }
}

// ===================== 底部弹层：书籍操作 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookActionSheet(
    book: BookEntity,
    progressById: Map<String, ReadingProgressEntity>,
    onDismiss: () -> Unit,
    onContinue: (BookEntity) -> Unit,
    onDownload: (BookEntity) -> Unit,
    onRepair: (BookEntity) -> Unit,
    onOpenDetail: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(book = book, percent = progressFor(progressById, book.id), modifier = Modifier.size(48.dp, 66.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${book.author ?: "作者未知"} · ${book.readiness().label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            val r = book.readiness()
            if (r.tone == ReadinessTone.READY) {
                ActionRow(icon = Icons.AutoMirrored.Filled.MenuBook, title = "继续阅读", subtitle = "从上次保存的位置打开", primary = true, onClick = { onContinue(book) })
            } else {
                if (r.tone == ReadinessTone.CLOUD) {
                    ActionRow(icon = Icons.Filled.Download, title = "下载正文", subtitle = "保存到本机后离线阅读", onClick = { onDownload(book) })
                }
            }
            ActionRow(
                icon = Icons.Filled.Refresh,
                title = "重新定位文件",
                subtitle = if (r.tone == ReadinessTone.READY) "更换本地文件并保留阅读记录" else "修复缺失的本地正文",
                onClick = { onRepair(book) },
            )
            ActionRow(icon = Icons.Outlined.Info, title = "书籍详情与管理", subtitle = "编辑信息、封面、分类和书单", onClick = { onOpenDetail(book.id) })
            ActionRow(icon = Icons.Filled.Delete, title = "删除书籍", subtitle = "同时移除本机正文和阅读数据", danger = true, onClick = { onDelete(book.id) })
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                danger -> AppError
                primary -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) AppError else MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

// ===================== 底部弹层：书籍详情 =====================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BookDetailSheet(
    book: BookEntity,
    progressById: Map<String, ReadingProgressEntity>,
    sessions: List<ReadingSessionEntity>,
    notes: List<NoteEntity>,
    highlights: List<HighlightEntity>,
    inspirations: List<InspirationEntity>,
    shelves: List<ShelfEntity>,
    categories: List<CategoryEntity>,
    allTags: List<TagEntity>,
    assignedTagIds: List<String>,
    onDismiss: () -> Unit,
    onContinue: (BookEntity) -> Unit,
    onDownload: (BookEntity) -> Unit,
    onDelete: (String) -> Unit,
    onMessage: (String) -> Unit,
    onRemoveShelf: (String) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onUpdateBook: (String, String?, String?) -> Unit,
    onChangeCover: () -> Unit,
    onChangeTextCover: () -> Unit,
    onResetCover: () -> Unit,
) {
    val spec = LocalComponentSpec.current
    val percent = progressFor(progressById, book.id)
    val readiness = book.readiness()
    val progress = progressById[book.id]
    var editing by remember { mutableStateOf(false) }
    var editTitle by remember(book.title) { mutableStateOf(book.title) }
    var editAuthor by remember(book.author) { mutableStateOf(book.author ?: "") }
    var editDescription by remember(book.description) { mutableStateOf(book.description ?: "") }
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    BookCover(book = book, percent = percent, modifier = Modifier.size(64.dp, 90.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(24.dp),
                    ) {
                        IconButton(onClick = onChangeCover, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = "更换封面", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (editing) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("书名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editAuthor,
                            onValueChange = { editAuthor = it },
                            label = { Text("作者") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editDescription,
                            onValueChange = { editDescription = it },
                            label = { Text("简介") },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(book.author ?: "作者未知", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!book.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(book.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(color = toneColor(readiness.tone).copy(alpha = 0.15f), shape = spec.pillShape) {
                        Text(readiness.label, color = toneColor(readiness.tone), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                IconButton(onClick = {
                    if (editing) {
                        onUpdateBook(editTitle, editAuthor.takeIf { it.isNotBlank() }, editDescription.takeIf { it.isNotBlank() })
                    }
                    editing = !editing
                }) {
                    Icon(if (editing) Icons.Filled.CheckCircle else Icons.Outlined.Edit, contentDescription = if (editing) "保存" else "编辑")
                }
            }
            // S2：文字封面 / 重置封面
            if (!editing) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onChangeTextCover, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("文字封面")
                    }
                    if (!book.cover_data_url.isNullOrBlank()) {
                        OutlinedButton(onClick = onResetCover, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重置封面")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { if (readiness.tone == ReadinessTone.READY) onContinue(book) else onDownload(book) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (readiness.tone == ReadinessTone.READY) (if (percent > 0) "继续阅读" else "开始阅读") else "下载后阅读")
                }
                OutlinedButton(onClick = { onDownload(book) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (readiness.tone == ReadinessTone.READY) "正文已下载" else "下载正文")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("阅读统计")
                InfoRow("总阅读时长", formatDuration(progress?.total_reading_time_ms ?: 0L))
                InfoRow("上次阅读", (progress?.last_read_at ?: "从未阅读").take(19).replace("T", " "))
                InfoRow("阅读进度", "${"%.1f".format(percent)}%")
                InfoRow("阅读次数", "${sessions.size} 次")
                Spacer(modifier = Modifier.height(8.dp))
                ReadingHistoryChart(sessions = sessions)
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                // S3：逐条阅读记录（按日期聚合，可展开明细）
                val sortedSessions = sessions.sortedByDescending { it.started_at ?: it.created_at ?: "" }
                SectionTitle("阅读记录 (${sortedSessions.size})")
                if (sortedSessions.isEmpty()) {
                    Text("还没有阅读记录。开始阅读后，这里会显示每次阅读。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // S3：按日期二级分组（对齐网页 BookDetailSheet 按日聚合）
                    val todayStr = LocalDate.now().toString()
                    val yesterdayStr = LocalDate.now().minusDays(1).toString()
                    val grouped = sortedSessions.groupBy { s ->
                        (s.started_at ?: s.created_at ?: "").take(10)
                    }
                    val dateOrder = grouped.keys.sortedDescending()
                    dateOrder.forEach { date ->
                        val label = when (date) {
                            todayStr -> "今天"
                            yesterdayStr -> "昨天"
                            else -> date
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        grouped[date]?.forEach { s ->
                            ExpandableRow(
                                title = (s.started_at ?: s.created_at ?: "未知时间").take(16).replace("T", " "),
                                subtitle = "${formatDuration(s.duration_ms)} · 进度 ${(s.progress_percent ?: 0f).toInt()}%",
                            ) {
                                InfoRow("开始", (s.started_at ?: "-").take(19).replace("T", " "))
                                InfoRow("结束", (s.ended_at ?: "-").take(19).replace("T", " "))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                // S3：书签与笔记 / 高亮区块
                val bookmarks = notes.filter { it.kind == "bookmark" }
                val noteList = notes.filter { it.kind != "bookmark" }
                SectionTitle("书签与笔记 · ${bookmarks.size} 书签 · ${noteList.size + highlights.size} 条")
                if (bookmarks.isEmpty() && noteList.isEmpty() && highlights.isEmpty()) {
                    Text("阅读时点“书签”或“笔记”，这本书的沉淀会集中在这里。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    bookmarks.forEach { n ->
                        ExpandableRow(
                            title = "书签 · ${(n.progress_percent ?: 0f).toInt()}%",
                            subtitle = n.excerpt ?: n.body ?: n.chapter_title ?: "当前位置",
                        ) {
                            if (!n.chapter_title.isNullOrBlank()) InfoRow("章节", n.chapter_title)
                            if (!n.body.isBlank()) InfoRow("正文", n.body)
                        }
                    }
                    noteList.forEach { n ->
                        ExpandableRow(
                            title = "笔记 · ${(n.progress_percent ?: 0f).toInt()}%",
                            subtitle = n.excerpt ?: n.body ?: n.chapter_title ?: "当前位置",
                        ) {
                            if (!n.chapter_title.isNullOrBlank()) InfoRow("章节", n.chapter_title)
                            if (!n.body.isBlank()) InfoRow("正文", n.body)
                        }
                    }
                    highlights.forEach { h ->
                        ExpandableRow(
                            title = "高亮 · ${(h.progress_percent ?: 0f).toInt()}%",
                            subtitle = h.text,
                        ) {
                            if (!h.note.isNullOrBlank()) InfoRow("笔记", h.note)
                            if (!h.chapter_title.isNullOrBlank()) InfoRow("章节", h.chapter_title)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                // S3：灵感区块
                SectionTitle("灵感 (${inspirations.size})")
                if (inspirations.isEmpty()) {
                    Text("还没有与本书相关的灵感。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    inspirations.forEach { ins ->
                        ExpandableRow(title = ins.title, subtitle = ins.body.take(40)) {
                            InfoRow("类型", ins.type)
                            if (ins.body.isNotBlank()) InfoRow("正文", ins.body)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("文件信息")
                InfoRow("原始文件名", book.original_file_name ?: "未知")
                InfoRow("导入时间", (book.imported_at ?: "未知").take(19).replace("T", " "))
                InfoRow("文件大小", formatBytes(book.size))
                InfoRow("格式", book.format.uppercase())
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("所在书单")
                if (shelves.isEmpty()) Text("尚未加入书单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    shelves.forEach { shelf ->
                        InputChip(
                            selected = false, onClick = {},
                            label = { Text(shelf.name) },
                            trailingIcon = { IconButton(modifier = Modifier.size(18.dp), onClick = { onRemoveShelf(shelf.id) }) { Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(14.dp)) } },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("所属分类")
                if (categories.isEmpty()) Text("尚未设置分类", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { category ->
                        InputChip(
                            selected = false, onClick = {},
                            label = { Text(category.name) },
                            trailingIcon = { IconButton(modifier = Modifier.size(18.dp), onClick = { onRemoveCategory(category.id) }) { Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(14.dp)) } },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                // S4：书籍标签可增删（点击切换：已分配则移除，未分配则添加）
                SectionTitle("书籍标签 (${assignedTagIds.size})")
                if (allTags.isEmpty()) Text("还没有书籍标签。可以在“我的 / 标签管理”里创建类型为“书籍”的标签。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    allTags.forEach { tag ->
                        val assigned = assignedTagIds.contains(tag.id)
                        InputChip(
                            selected = assigned,
                            onClick = { if (assigned) onRemoveTag(tag.id) else onAddTag(tag.id) },
                            label = { Text(tag.name) },
                            trailingIcon = if (assigned) {
                                { IconButton(modifier = Modifier.size(18.dp), onClick = { onRemoveTag(tag.id) }) { Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(14.dp)) } }
                            } else null,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = AppError.copy(alpha = 0.08f),
                shape = LocalComponentSpec.current.listItemShape,
                border = BorderStroke(LocalComponentSpec.current.borderWidth, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().clickable { onDelete(book.id) },
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = AppError)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("删除本书", style = MaterialTheme.typography.bodyLarge, color = AppError)
                        Text("同时移除本机正文、进度、书签和笔记", style = MaterialTheme.typography.bodySmall, color = AppError)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ExpandableRow(
    title: String,
    subtitle: String? = null,
    expandedContent: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = LocalComponentSpec.current.listItemShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) { expandedContent() }
            }
        }
    }
}

@Composable
private fun ReadingHistoryChart(sessions: List<ReadingSessionEntity>) {
    if (sessions.isEmpty()) {
        Text("暂无阅读记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val dayLabel = java.time.format.DateTimeFormatter.ofPattern("M/d")
    val now = java.time.LocalDate.now()
    val days = (6 downTo 0).map { now.minusDays(it.toLong()) }
    val durationsByDay = sessions.groupBy { session ->
        val instant = runCatching { java.time.Instant.parse(session.created_at ?: session.started_at) }.getOrNull()
        instant?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()?.toString() ?: ""
    }.mapValues { entry -> entry.value.sumOf { it.duration_ms } }
    val maxDuration = durationsByDay.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val key = day.toString()
            val duration = durationsByDay[key] ?: 0L
            val fraction = (duration.toFloat() / maxDuration).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    )
                }
                Text(dayLabel.format(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ChipRow(names: List<String>, active: List<String>, onClick: () -> Unit) {
    if (names.isEmpty()) {
        Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { name ->
            val isActive = active.contains(name)
            SelectablePill(
                text = (if (isActive) "✓ " else "") + name,
                selected = isActive,
                onClick = onClick,
            )
        }
    }
}

// ===================== 底部弹层：排序 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(current: ShelfSortMode, onSelect: (ShelfSortMode) -> Unit, onDismiss: () -> Unit) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("排序方式", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            SORT_OPTIONS.forEach { (mode, label) ->
                SettingRow(
                    title = label,
                    trailing = { if (current == mode) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

// ===================== 底部弹层：筛选 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    shelves: List<ShelfEntity>,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    selectedShelfId: String,
    selectedCategoryId: String,
    selectedTagId: String,
    onSelectShelf: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("筛选", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            if (shelves.isNotEmpty()) {
                Text("书单", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                FilterChipRow(shelves.map { it.id to it.name }, selectedShelfId) { onSelectShelf(it) }
            }
            if (categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("分类", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                FilterChipRow(categories.map { it.id to it.name }, selectedCategoryId) { onSelectCategory(it) }
            }
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("标签", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                FilterChipRow(tags.map { it.id to it.name }, selectedTagId) { onSelectTag(it) }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FilterChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    // G 档：筛选 chips 离散选择加轻触感
    val haptic = rememberHaptic(rememberReducedMotion())
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected.isEmpty(), onClick = { haptic(HapticFeedbackType.TextHandleMove); onSelect("") }, label = { Text("全部") })
        options.forEach { (id, name) ->
            FilterChip(selected = selected == id, onClick = { haptic(HapticFeedbackType.TextHandleMove); onSelect(id) }, label = { Text(name) })
        }
    }
}

// ===================== 底部弹层：批量编辑 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchSheet(
    kind: BatchSheetKind,
    shelves: List<ShelfEntity>,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRemoveTag: (String) -> Unit = {},
    onCreate: (String) -> Unit,
    showMessage: (String) -> Unit,
    selectedCount: Int = 0,
) {
    val title = when (kind) { BatchSheetKind.SHELF -> "加入书单"; BatchSheetKind.CATEGORY -> "设置分类"; BatchSheetKind.TAG -> "管理标签" }
    val items: List<Pair<String, String>> = when (kind) {
        BatchSheetKind.SHELF -> shelves.map { it.id to it.name }
        BatchSheetKind.CATEGORY -> categories.map { it.id to it.name }
        BatchSheetKind.TAG -> tags.map { it.id to it.name }
    }
    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            // S5：批量操作显示已选计数（对齐网页「已选 X 本」）
            if (kind == BatchSheetKind.TAG) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("已选 $selectedCount 本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (items.isEmpty() && !showCreate) {
                Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (kind == BatchSheetKind.TAG) {
                // S5：标签批量支持「添加 / 移除」
                items.forEach { (id, name) ->
                    SettingRow(
                        title = name,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { onSelect(id) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    enabled = selectedCount > 0,
                                ) { Text("添加") }
                                Spacer(modifier = Modifier.width(6.dp))
                                TextButton(
                                    onClick = { onRemoveTag(id) },
                                    enabled = selectedCount > 0,
                                ) { Text("移除", color = AppError) }
                            }
                        },
                    )
                }
            } else {
                items.forEach { (id, name) ->
                    SettingRow(
                        title = name,
                        onClick = { onSelect(id) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SectionDivider()
            Spacer(modifier = Modifier.height(8.dp))
            if (showCreate) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        placeholder = { Text("输入名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        if (newName.isNotBlank()) { onCreate(newName.trim()); showCreate = false; newName = ""; showMessage("已创建") }
                    }) { Text("创建") }
                    TextButton(onClick = { showCreate = false; newName = "" }) { Text("取消") }
                }
            } else {
                TextButton(onClick = { showCreate = true }) { Text("+ 创建新的") }
            }
        }
    }
}

// ===================== 底部弹层：从电脑下载 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopBooksSheet(
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    listBooks: suspend () -> List<com.creationreadingassistant.data.remote.SyncContract.BookFileManifest>,
) {
    var books by remember { mutableStateOf<List<com.creationreadingassistant.data.remote.SyncContract.BookFileManifest>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() { scope.launch { loading = true; books = listBooks(); loading = false } }
    LaunchedEffect(Unit) { load() }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("从电脑下载", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { load() }) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            else if (books.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("暂无可下载书籍", style = MaterialTheme.typography.bodyMedium)
                    Text("请确认电脑端已开启同步服务并有可下载的书籍正文。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                books.forEach { book ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onDownload(book.bookId) }.padding(vertical = 10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(book.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${book.format.uppercase()} · ${formatBytes(book.size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.Download, contentDescription = "下载", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ===================== 导入来源 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSourceSheet(
    onSelectFiles: () -> Unit,
    onSelectFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("添加到书架", style = MaterialTheme.typography.titleMedium)
            Text(
                "可以一次选择多本书，也可以扫描一个文件夹。只会读取 EPUB、TXT 和 Markdown。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            ImportSourceRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "选择书籍",
                description = "一次选择一本或多本文件",
                onClick = onSelectFiles,
            )
            SectionDivider()
            ImportSourceRow(
                icon = Icons.Filled.Folder,
                title = "扫描文件夹",
                description = "包含子文件夹，自动跳过其他文件",
                onClick = onSelectFolder,
            )
        }
    }
}

@Composable
private fun ImportSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ===================== 底部弹层：导入历史 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportHistorySheet(
    tasks: List<ImportTaskUi>,
    history: List<ImportHistoryEntry>,
    batch: ImportBatchUiState,
    onRetryFailed: () -> Unit,
    onDismissBatch: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    // G 档：清空历史是结果性动作，加确认触感
    val haptic = rememberHaptic(rememberReducedMotion())
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                Text("导入历史", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    IconButton(onClick = { haptic(HapticFeedbackType.LongPress); onClear() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空历史", modifier = Modifier.size(16.dp), tint = AppError)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (batch.hasResult) {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            if (batch.isRunning) "正在处理 ${batch.sourceLabel}" else "本次导入结果",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            buildList {
                                add("成功 ${batch.succeeded}")
                                if (batch.duplicates > 0) add("重复 ${batch.duplicates}")
                                if (batch.skipped > 0) add("跳过 ${batch.skipped}")
                                if (batch.failed > 0) add("失败 ${batch.failed}")
                            }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (batch.truncated || batch.unreadableFolders > 0) {
                            Text(
                                buildList {
                                    if (batch.truncated) add("文件较多，已按安全上限停止扫描")
                                    if (batch.unreadableFolders > 0) add("${batch.unreadableFolders} 个子文件夹无法读取")
                                }.joinToString("；"),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppWarning,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (!batch.isRunning) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (batch.failures.isNotEmpty()) {
                                    TextButton(onClick = onRetryFailed) { Text("重试失败项") }
                                }
                                TextButton(onClick = onDismissBatch) { Text("知道了") }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (tasks.isEmpty() && history.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    LineArtBook(modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("还没有导入记录", style = MaterialTheme.typography.bodyMedium)
                    Text("导入书籍后，这里会显示每次导入的结果、编码识别和失败原因。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                if (tasks.isNotEmpty()) {
                    SectionTitle("本次导入队列（${tasks.size}）")
                    tasks.forEach { task ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val taskColor = when (task.status) {
                                "error" -> AppError
                                "duplicate", "skipped" -> AppWarning
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Icon(
                                when (task.status) {
                                    "processing" -> Icons.Filled.Refresh
                                    "error", "skipped" -> Icons.Filled.Warning
                                    else -> Icons.Filled.CheckCircle
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = taskColor,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(task.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(task.phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (history.isNotEmpty()) {
                    val successCount = history.count { it.status == "success" }
                    val failedCount = history.count { it.status == "failed" }
                    val duplicateCount = history.count { it.isDuplicate || it.status == "duplicate" }
                    val skippedCount = history.count { it.status == "skipped" }
                    SectionTitle(
                        buildList {
                            add("历史记录（${history.size}）")
                            add("成功 $successCount")
                            if (duplicateCount > 0) add("重复 $duplicateCount")
                            if (skippedCount > 0) add("跳过 $skippedCount")
                            if (failedCount > 0) add("失败 $failedCount")
                        }.joinToString(" · ")
                    )
                    history.forEach { entry ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (entry.status == "success") Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = when (entry.status) {
                                    "success" -> AppSuccess
                                    "duplicate", "skipped" -> AppWarning
                                    else -> AppError
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val meta = buildList {
                                    if (entry.fileSize > 0) add(formatBytes(entry.fileSize))
                                    if (entry.format.isNotBlank()) add(entry.format.uppercase())
                                    if (!entry.encoding.isNullOrBlank()) add(entry.encoding)
                                    if (entry.bookTitle != null) add("《${entry.bookTitle}》")
                                    if (entry.error != null) add(entry.error)
                                }.joinToString(" · ")
                                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(entry.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
