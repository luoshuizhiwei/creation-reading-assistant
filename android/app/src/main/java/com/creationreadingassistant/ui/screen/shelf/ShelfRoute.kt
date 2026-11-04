package com.creationreadingassistant.ui.screen.shelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.feature.library.ImportBatchSuccesses
import com.creationreadingassistant.feature.library.deletion.DeletionScope
import com.creationreadingassistant.feature.library.deletion.deletionConfirmAction
import com.creationreadingassistant.feature.library.deletion.deletionConfirmBody
import com.creationreadingassistant.feature.library.deletion.deletionConfirmTitle
import com.creationreadingassistant.feature.library.deletion.deletionFailedMessage
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel
import com.creationreadingassistant.ui.viewmodel.TaxonomyViewModel
import kotlinx.coroutines.launch

/**
 * S05-④ 复核补强：「查看」点按时取出**与提示所对应批次一致**的成功 id。
 *
 * 只有 [successes] 记录的批次就是 [batchId] 时才返回其 id；否则返回**空** ——
 * 批次已经换代（用户点了旧提示、而期间又开始了新导入）时，绝不能把新批次的成功 id
 * 当成旧提示的目标，否则用户会被带去这一批根本没导入的书。
 *
 * 返回空同时还覆盖「批次一致但 id 列表异常为空」这一路：调用方必须据此给出明确反馈，
 * 不得静默结束。
 *
 * 抽成纯函数以便定向测试：这是「查看」定位里可判定的一步，不需要 Compose 运行时。
 */
internal fun focusIdsForBatch(
    successes: ImportBatchSuccesses,
    batchId: String,
): List<String> = if (successes.batchId == batchId) successes.bookIds else emptyList()

/**
 * S04：来源更新确认弹窗对「阅读进度」的说明。
 *
 * 旧文案是「同步后将重置章节与阅读进度」，与代码不符 —— 措辞边界由
 * [ShelfSourceRefreshCopyTest] 钉住，改动前请先读那里的失败信息。
 *
 * 为什么「重置」不成立：替换链路
 * （`ShelfImporter.refreshFromSource` → `repairFileTyped` → `reselectEpub` /
 * `reselectPlainText`）**不写** `reading_progress`，且 `ReadingProgressDao` 根本没有
 * delete / clear 方法，全仓也没有任何重置调用方 —— 不存在「重置」这个操作。
 *
 * 为什么也不能反过来写「进度完全不变」：正文换掉后，阅读器重新加载会按**新**内容夹取
 * 旧坐标（EPUB 走 `coerceIn(0, chapters.lastIndex)`，TXT/MD 走
 * `resolveImportedTextOffset`），闸门打开后夹取后的位置会被写回。真实契约是
 * 「记录行保留、坐标按新正文重新映射」—— 两种说法都不实。
 *
 * 因此这里只陈述可确认的一半（不主动清除），再用「可能变化」覆盖不确定的重映射，
 * 不做超出代码事实的承诺。
 */
internal const val SOURCE_REFRESH_PROGRESS_NOTE =
    "同步不会主动清除已有阅读记录；但正文更新后章节会重新划分，阅读位置也可能随之变化。"

/**
 * 来源更新确认弹窗的正文。抽成纯函数以便定向测试：文案边界是这里唯一可判定的部分，
 * 不需要 Compose 运行时。
 */
internal fun sourceRefreshConfirmBody(count: Int, titleText: String): String =
    "检测到 ${count} 本书${titleText}的源文件已被修改，是否重新同步正文？$SOURCE_REFRESH_PROGRESS_NOTE"

/**
 * Shelf 页 View 层：收集 ViewModel Flow、维护 Activity Launcher、Snackbar、导航、
 * 删除确认、分类学(Taxonomy)、readiness 检查等所有副作用。
 */
@Composable
internal fun ShelfRoute(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: ShelfViewModel = hiltViewModel(),
    taxonomyVm: TaxonomyViewModel = hiltViewModel(),
    initialDetailBookId: String? = null,
) {
    // ======= ViewModel 源数据 collect =======
    val vmState by viewModel.uiState.collectAsStateWithLifecycle()
    val shelfBooks by viewModel.shelfBooks.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val books = vmState.library.books
    val tags = vmState.library.tags
    val categories = vmState.library.categories
    val shelves = vmState.library.shelves
    val progressById = vmState.library.progressById
    val sessionsByBook = vmState.activity.sessionsByBook
    val notesByBook = vmState.activity.notesByBook
    val highlightsByBook = vmState.activity.highlightsByBook
    val inspirationsByBook = vmState.activity.inspirationsByBook
    val importTasks = vmState.auxiliary.importTasks
    val importBatch = vmState.auxiliary.importBatch
    val downloadingIds = vmState.auxiliary.downloadingIds
    val importHistory = vmState.auxiliary.importHistory

    // ======= Snackbar + 协程 scope =======
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun showMessage(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }

    // ======= Launchers（4 个）=======
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }
    val importFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> uri?.let(viewModel::importFolder) }

    var reselectBookId by remember { mutableStateOf<String?>(null) }
    var coverBookId by remember { mutableStateOf<String?>(null) }
    val reselectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val bid = reselectBookId
        if (uri != null && bid != null) viewModel.reselectFile(bid, uri) { showMessage(it) }
        reselectBookId = null
    }
    val coverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val bid = coverBookId
        if (uri != null && bid != null) viewModel.updateBookCover(bid, uri) { showMessage(it) }
        coverBookId = null
    }

    // ======= UI 状态：搜索 / 筛选 / 排序 / 视图模式 =======
    val viewMode = session.viewMode
    val sortMode = session.sortMode
    val statusFilter = session.statusFilter
    // 骨架门（M-BUG-09）：只由「库数据是否已收到首次真实发射」驱动，**不用计时器**。
    // 原实现用 350ms 定时器关骨架，冷启动首读慢于 350ms 时骨架先关、而 books 仍为空，
    // 于是先渲染空态「书架还空着」再跳到列表（真机可复现）。对齐 InspirationRoute 的做法：
    // 骨架只由首次 emission 驱动，Loaded(空) 才落空态。
    val libraryLoaded by viewModel.libraryLoaded.collectAsStateWithLifecycle(initialValue = false)

    // ======= 顶栏：搜索 / 菜单 / 选择模式 =======
    var showPageMenu by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    // ======= L1 动态视图：已保存的筛选组合 =======
    val savedViews by viewModel.savedFilters.collectAsStateWithLifecycle()

    // ======= 分类学筛选：书单 / 分类 / 标签 =======
    val selectedShelfId = session.selectedShelfId
    val selectedCategoryId = session.selectedCategoryId
    val selectedTagIds = session.selectedTagIds
    var filteredBookIds by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(selectedShelfId, selectedCategoryId, selectedTagIds, books) {
        val byShelf = if (selectedShelfId.isEmpty()) null else taxonomyVm.getBookIdsByShelf(selectedShelfId).toSet()
        val byCategory = if (selectedCategoryId.isEmpty()) null else taxonomyVm.getBookIdsByCategory(selectedCategoryId).toSet()
        val byTags = selectedTagIds.map { tagId -> taxonomyVm.getBookIdsByTag(tagId).toSet() }
        filteredBookIds = combineTaxonomyFilterIds(byShelf, byCategory, byTags)
    }

    // ======= 弹层开关 =======
    var detailBookId by remember { mutableStateOf(initialDetailBookId) }
    var actionBookId by remember { mutableStateOf<String?>(null) }
    var batchSheet by remember { mutableStateOf<BatchSheetKind?>(null) }
    var showImportHistory by remember { mutableStateOf(false) }
    var showImportSource by remember { mutableStateOf(false) }
    var showFilterPanel by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showDesktopBooks by remember { mutableStateOf(false) }
    var confirmDeleteIds by remember { mutableStateOf<List<String>?>(null) }
    var restorePromptBookId by remember { mutableStateOf<String?>(null) }

    // ======= 新导入未查看标签 =======
    val newBookIds by viewModel.newBookIds.collectAsStateWithLifecycle(initialValue = emptySet())

    /**
     * 导入完成点「查看」后的定位候选（本批成功 id；Screen 处理完即清空）。
     *
     * 这里**不**收集导入器的流：定位目标必须在点按那一刻从导入器同步读取并按批次核对，
     * 见下方 [focusIdsForBatch] 与点击处注释。
     */
    var focusNewBookIds by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(importBatch.id, importBatch.isRunning) {
        if (
            importBatch.id.isNotBlank() &&
            !importBatch.isRunning &&
            importBatch.hasResult &&
            !importBatch.summaryNotified
        ) {
            val summary = importBatchSnackbarMessage(importBatch)
            viewModel.markImportBatchSummaryNotified(importBatch.id)
            // 有成功导入时给一个「查看」入口，直接滚到新书位置。
            // 用户抱怨过「新导入的找不到」，仅靠排序把新书放最前还不够——
            // 书多了以后列表首屏未必在顶部（上次浏览位置），需要一个显式定位动作。
            if (importBatch.succeeded > 0) {
                val result = snackbarHostState.showSnackbar(
                    message = summary,
                    actionLabel = "查看",
                    withDismissAction = false,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    // 在**点按这一刻同步读**导入器，而不是用 Compose 异步收集到的最近一帧：
                    // 后者不能保证与「这条提示所对应的批次」原子同步，可能批次 id 已是新的、
                    // id 列表还是旧的（或反之），从而把用户带去错误批次的书。
                    val successes = viewModel.importer.batchSuccesses.value
                    val ids = focusIdsForBatch(successes, importBatch.id)
                    focusNewBookIds = ids
                    if (ids.isEmpty()) {
                        // succeeded > 0 却取不到可定位的 id：批次已换代，或列表异常为空。
                        // 必须明确反馈且请求已清理，不能静默结束（用户会以为界面卡住了）。
                        showMessage(
                            if (successes.batchId != importBatch.id) {
                                "这一批已不是最近一次导入，请在书架上按「新」角标查找"
                            } else {
                                "暂时定位不到这一批新书，请在书架上按「新」角标查找"
                            },
                        )
                    }
                }
            } else {
                snackbarHostState.showSnackbar(summary)
            }
        }
    }

    // ======= detail taxonomy ids（for BookDetailSheet）=======
    val detailShelfIds by taxonomyVm.observeShelfIdsForBook(detailBookId ?: "")
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val detailCategoryIds by taxonomyVm.observeCategoryIdsForBook(detailBookId ?: "")
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val detailTagIds by taxonomyVm.observeTagIdsForBook(detailBookId ?: "")
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // ======= 来源内容更新标签（M-BUG-03）=======
    val contentUpdatedBookIds by viewModel.contentUpdatedBookIds
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val pendingSourceRefreshIds by viewModel.pendingSourceRefreshIds
        .collectAsStateWithLifecycle(initialValue = null)

    // ======= 聚合：ShelfUiState =======
    val uiState = ShelfUiState(
        library = vmState.library,
        activity = vmState.activity,
        auxiliary = vmState.auxiliary,
        shelfBooks = shelfBooks,
        isRefreshing = isRefreshing,
        contentUpdatedBookIds = contentUpdatedBookIds,
        newBookIds = newBookIds,
        query = "",
        debouncedQuery = "",
        viewMode = viewMode,
        sortMode = sortMode,
        statusFilter = statusFilter,
        formatFilter = session.formatFilter,
        searchActive = false,
        showPageMenu = showPageMenu,
        selectionMode = selectionMode,
        selectedIds = selectedIds.toSet(),
        selectedShelfId = selectedShelfId,
        selectedCategoryId = selectedCategoryId,
        selectedTagIds = selectedTagIds,
        filteredBookIds = filteredBookIds,
        showSkeleton = !libraryLoaded,
        showSortSheet = showSortSheet,
        showFilterPanel = showFilterPanel,
        showImportHistory = showImportHistory,
        showImportSource = showImportSource,
        showDesktopBooks = showDesktopBooks,
        batchSheet = batchSheet,
        detailBookId = detailBookId,
        actionBookId = actionBookId,
        confirmDeleteIds = confirmDeleteIds,
        confirmSourceRefreshIds = pendingSourceRefreshIds,
    )

    fun enterSelection() { selectionMode = true; selectedIds.clear(); showPageMenu = false }
    fun exitSelection() { selectionMode = false; selectedIds.clear(); batchSheet = null }
    fun toggleSelected(id: String) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
    }
    fun requestDelete(ids: List<String>) { confirmDeleteIds = ids }

    // ======= 纯 Screen + Snackbar（用 Local 桥）=======
    androidx.compose.foundation.layout.Box(modifier = modifier.testTag("shelf-route")) {
        CompositionLocalProvider(LocalShelfSnackbar provides snackbarHostState) {
            ShelfScreen(
                state = uiState,
                focusBookIds = focusNewBookIds,
                onFocusHandled = { reached ->
                    // 无论是否滚动成功都清空：留下的过期定位会在用户之后改筛选时突然触发。
                    focusNewBookIds = emptyList()
                    if (!reached) {
                        // 不能静默无响应：说明为什么没动，以及用户能做什么。
                        showMessage("新导入的书不在当前筛选结果里，可调整筛选后再查看")
                    }
                },
                onAction = { action ->
                    if (action is ShelfAction.OpenBook &&
                        progressById[action.book.id]?.readingState == ReadingCompletionState.SHELVED
                    ) {
                        // 搁置的书走「恢复阅读状态？」对话框，不直接进阅读器。
                        // 「新」角标在此一并清除：用户已经点了这本书，无论是否恢复都算已查看。
                        viewModel.clearNewBookId(action.book.id)
                        restorePromptBookId = action.book.id
                    } else handleShelfAction(
                        action = action,
                        context = context,
                        navController = navController,
                        snackbarHostState = snackbarHostState,
                        scope = scope,
                        viewModel = viewModel,
                        taxonomyVm = taxonomyVm,
                        setQuery = viewModel::setSearchQuery,
                        setDebouncedQuery = {},
                        setViewMode = viewModel::setViewMode,
                        setSortMode = viewModel::setSortMode,
                        setStatusFilter = viewModel::setStatusFilter,
                        setSearchActive = {},
                        setShowPageMenu = { showPageMenu = it },
                        setSelectionMode = { selectionMode = it },
                        clearSelected = { selectedIds.clear() },
                        addSelectedAll = { ids -> selectedIds.clear(); selectedIds.addAll(ids) },
                        toggleSelectedId = ::toggleSelected,
                        visibleBookIds = { uiState.filtered.map { it.bookId } },
                        currentActionBookId = { actionBookId },
                        setSelectedShelfId = viewModel::setSelectedShelf,
                        setSelectedCategoryId = viewModel::setSelectedCategory,
                        setSelectedTagId = viewModel::setSelectedTag,
                        resetFilters = viewModel::resetShelfFilters,
                        setBatchSheet = { batchSheet = it },
                        setDetailBookId = { detailBookId = it },
                        setActionBookId = { actionBookId = it },
                        setConfirmDeleteIds = { ids -> ids?.let { requestDelete(it) } },
                        setShowImportHistory = { showImportHistory = it },
                        setShowImportSource = { showImportSource = it },
                        setShowFilterPanel = { showFilterPanel = it },
                        setShowSortSheet = { showSortSheet = it },
                        setShowDesktopBooks = { showDesktopBooks = it },
                        exitSelection = ::exitSelection,
                        launchImportFiles = {
                            importLauncher.launch(arrayOf("application/epub+zip", "text/plain", "text/markdown"))
                        },
                        launchImportFolder = { importFolderLauncher.launch(null) },
                        launchReselectFile = { bid ->
                            reselectBookId = bid
                            reselectLauncher.launch(arrayOf("application/epub+zip", "text/plain", "text/markdown"))
                        },
                        launchChangeCover = { bid ->
                            coverBookId = bid
                            coverLauncher.launch(arrayOf("image/*"))
                        },
                    )
                },
            )
        }

        if (selectionMode) {
            BatchActionBar(
                selectedCount = selectedIds.size,
                onAddToShelf = { batchSheet = BatchSheetKind.SHELF },
                onSetCategory = { batchSheet = BatchSheetKind.CATEGORY },
                onTag = { batchSheet = BatchSheetKind.TAG },
                onDownload = { selectedIds.toSet().forEach { bid -> viewModel.downloadBookContent(bid) { showMessage(it) } } },
                onClearCache = {
                    val ids = selectedIds.toList()
                    if (ids.isNotEmpty()) {
                        viewModel.clearCacheForBooks(ids) { showMessage(it) }; exitSelection()
                    } else showMessage("请先选择要清理缓存的书籍")
                },
                onDelete = { requestDelete(selectedIds.toList()) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
        }
    }

    // ======= 各类底部弹层 / 对话框 =======
    val detailBook = detailBookId?.let { bid -> books.find { it.id == bid } }
    val actionBook = actionBookId?.let { bid -> books.find { it.id == bid } }
    val selectedBookIds = selectedIds.toList()
    fun dismissDetailDestination() {
        detailBookId = null
        if (initialDetailBookId != null) navController.popBackStack()
    }

    if (actionBook != null) {
        BookActionSheet(
            book = actionBook,
            progressById = progressById,
            onDismiss = { actionBookId = null },
            onContinue = {
                if (progressById[it.id]?.readingState == ReadingCompletionState.SHELVED) {
                    restorePromptBookId = it.id
                } else if (bookNotReadyLabel(it) == null) navController.navigate("reader/${it.id}")
                else showMessage("《${it.title}》暂无可离线正文，请先导入或下载。")
                actionBookId = null
            },
            onDownload = { viewModel.downloadBookContent(it.id) { showMessage(it) } },
            onRepair = {
                actionBookId = null; reselectBookId = it.id
                reselectLauncher.launch(arrayOf("application/epub+zip", "text/plain", "text/markdown"))
            },
            onOpenDetail = { id -> actionBookId = null; detailBookId = id },
            onDelete = { id -> actionBookId = null; requestDelete(listOf(id)) },
            // 状态动作与长按面板内的按钮一一对应；面板只发动作，写库统一走 ViewModel。
            onMarkFinished = { id -> actionBookId = null; viewModel.markFinished(id) { showMessage(it) } },
            onMarkReading = { id -> actionBookId = null; viewModel.markReading(id) { showMessage(it) } },
            onShelve = { id -> actionBookId = null; viewModel.shelveBook(id) { showMessage(it) } },
        )
    }
    if (detailBook != null) {
        val b = detailBook
        BookDetailSheet(
            book = b,
            progressById = progressById,
            sessions = sessionsByBook[b.id] ?: emptyList(),
            notes = notesByBook[b.id] ?: emptyList(),
            highlights = highlightsByBook[b.id] ?: emptyList(),
            inspirations = inspirationsByBook[b.id] ?: emptyList(),
            shelves = shelves.filter { detailShelfIds.contains(it.id) },
            categories = categories.filter { detailCategoryIds.contains(it.id) },
            allTags = tags,
            assignedTagIds = detailTagIds,
            onDismiss = ::dismissDetailDestination,
            onContinue = {
                if (progressById[it.id]?.readingState == ReadingCompletionState.SHELVED) {
                    restorePromptBookId = it.id
                } else if (bookNotReadyLabel(it) == null) {
                    if (initialDetailBookId != null) navController.popBackStack()
                    navController.navigate("reader/${it.id}")
                }
                else showMessage("《${it.title}》暂无可离线正文，请先导入或下载。")
                detailBookId = null
            },
            onDownload = { viewModel.downloadBookContent(it.id) { showMessage(it) } },
            onDelete = { id -> detailBookId = null; requestDelete(listOf(id)) },
            onMessage = ::showMessage,
            onRemoveShelf = { taxonomyVm.removeBookFromShelf(b.id, it); showMessage("已移除书单") },
            onRemoveCategory = { taxonomyVm.removeCategoryFromBook(b.id, it); showMessage("已移除分类") },
            onRemoveTag = { taxonomyVm.removeTagFromBook(b.id, it); showMessage("已移除标签") },
            onAddTag = { tagId ->
                taxonomyVm.addTagToBook(b.id, tagId); showMessage("已给《${b.title}》添加标签")
            },
            onUpdateBook = { title, author, desc -> viewModel.updateBookInfo(b.id, title, author, desc) { showMessage(it) } },
            onChangeCover = { coverBookId = b.id; coverLauncher.launch(arrayOf("image/*")) },
            onChangeTextCover = {
                viewModel.setBookTextCover(b.id, generateTextCoverDataUrl(b.title)) { showMessage(it) }
            },
            onResetCover = { viewModel.resetBookCover(b.id) { showMessage(it) } },
        )
    }
    if (showSortSheet) {
        SortSheet(
            current = sortMode,
            onSelect = { mode -> viewModel.setSortMode(mode); showSortSheet = false },
            onDismiss = { showSortSheet = false },
        )
    }
    if (showFilterPanel) {
        FilterSheet(
            shelves = shelves,
            categories = categories,
            tags = tags,
            selectedShelfId = selectedShelfId,
            selectedCategoryId = selectedCategoryId,
            selectedTagIds = selectedTagIds,
            formatFilter = session.formatFilter,
            onSelectShelf = { viewModel.setSelectedShelf(if (selectedShelfId == it) "" else it) },
            onSelectCategory = { viewModel.setSelectedCategory(if (selectedCategoryId == it) "" else it) },
            onToggleTag = viewModel::toggleSelectedTag,
            onSelectFormat = viewModel::setFormatFilter,
            onDismiss = { showFilterPanel = false },
            // L1 动态视图：保存当前筛选组合 / 一键套用 / 删除
            savedViews = savedViews,
            canSaveCurrent = viewModel.hasActiveFilters(),
            onSaveCurrent = { name ->
                viewModel.saveCurrentFilter(name) { ok ->
                    if (ok) showMessage("已保存动态视图「$name」。") else showMessage("保存失败：名称不能为空。")
                }
            },
            onApplySaved = { view ->
                viewModel.applySavedFilter(view)
                showFilterPanel = false
                showMessage("已套用动态视图「${view.name}」。")
            },
            onDeleteSaved = { name -> viewModel.deleteSavedFilter(name) },
        )
    }
    val restorePromptBook = restorePromptBookId?.let { id -> books.find { it.id == id } }
    if (restorePromptBook != null) {
        GlassAlertDialog(
            onDismissRequest = { restorePromptBookId = null },
            title = { androidx.compose.material3.Text("恢复阅读状态？") },
            text = { androidx.compose.material3.Text("这本书已搁置。恢复为在读后会保留原有进度、时长、笔记和灵感，并从当前位置继续。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val book = restorePromptBook
                        restorePromptBookId = null
                        viewModel.restoreReading(book.id) { success, message ->
                            showMessage(message)
                            if (success && bookNotReadyLabel(book) == null) navController.navigate("reader/${book.id}")
                        }
                    },
                ) { androidx.compose.material3.Text("恢复并阅读") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { restorePromptBookId = null }) {
                    androidx.compose.material3.Text("暂不恢复")
                }
            },
        )
    }
    val bs = batchSheet
    if (bs != null) {
        BatchSheet(
            kind = bs,
            shelves = shelves,
            categories = categories,
            tags = tags,
            onDismiss = { batchSheet = null },
            onSelect = { id ->
                when (bs) {
                    BatchSheetKind.SHELF -> taxonomyVm.addBooksToShelf(selectedBookIds, id)
                    BatchSheetKind.CATEGORY -> taxonomyVm.setCategoryForBooks(selectedBookIds, id)
                    BatchSheetKind.TAG -> taxonomyVm.addTagToBooks(selectedBookIds, id)
                }
                exitSelection(); showMessage("已更新")
            },
            onRemoveTag = { id ->
                selectedBookIds.forEach { taxonomyVm.removeTagFromBook(it, id) }
                exitSelection(); showMessage("已移除标签")
            },
            onCreate = { name ->
                when (bs) {
                    BatchSheetKind.SHELF -> scope.launch {
                        val id = taxonomyVm.createShelfAndGetId(name)
                        taxonomyVm.addBooksToShelf(selectedBookIds, id)
                        exitSelection(); showMessage("已创建书单并添加选中书籍")
                    }
                    BatchSheetKind.CATEGORY -> scope.launch {
                        val id = taxonomyVm.createCategoryAndGetId(name)
                        taxonomyVm.setCategoryForBooks(selectedBookIds, id)
                        exitSelection(); showMessage("已创建分类并应用到选中书籍")
                    }
                    BatchSheetKind.TAG -> scope.launch {
                        val id = taxonomyVm.createTagAndGetId(name)
                        taxonomyVm.addTagToBooks(selectedBookIds, id)
                        exitSelection(); showMessage("已创建并添加到选中书籍")
                    }
                }
            },
            showMessage = ::showMessage,
            selectedCount = selectedIds.size,
        )
    }
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
            onOpenLibrary = {
                showImportSource = false
                navController.navigate(SHELF_LIBRARY_ROUTE)
            },
            onSelectFiles = {
                showImportSource = false
                importLauncher.launch(arrayOf("application/epub+zip", "text/plain", "text/markdown"))
            },
            onImportFromDesktop = {
                showImportSource = false
                showDesktopBooks = true
            },
            onDismiss = { showImportSource = false },
        )
    }
    if (showDesktopBooks) {
        DesktopBooksSheet(
            onDismiss = { showDesktopBooks = false },
            onDownload = { bid -> viewModel.downloadBookContent(bid) { showMessage(it) } },
            listBooks = { viewModel.listDesktopBooks() },
        )
    }
    val delIds = confirmDeleteIds
    if (delIds != null) {
        GlassAlertDialog(
            onDismissRequest = { confirmDeleteIds = null },
            title = {
                androidx.compose.material3.Text(
                    deletionConfirmTitle(context, DeletionScope.DELETE_BOOK),
                )
            },
            text = {
                androidx.compose.material3.Text(
                    deletionConfirmBody(
                        context = context,
                        scope = DeletionScope.DELETE_BOOK,
                        bookCount = delIds.size,
                        undoSeconds = viewModel.undoWindowSeconds,
                    ),
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        val ids = delIds
                        confirmDeleteIds = null
                        if (selectionMode) exitSelection()
                        // 整批走一次事务、一张凭证：撤销范围与这里提示的数量一致。
                        viewModel.deleteBooks(ids) { ok ->
                            if (!ok) showMessage(deletionFailedMessage(context))
                        }
                    },
                ) {
                    androidx.compose.material3.Text(
                        deletionConfirmAction(context, DeletionScope.DELETE_BOOK),
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDeleteIds = null }) {
                    androidx.compose.material3.Text("取消")
                }
            },
        )
    }
    val refreshIds = pendingSourceRefreshIds
    if (refreshIds != null) {
        val count = refreshIds.size
        val updatedTitles = refreshIds.mapNotNull { id -> books.find { it.id == id }?.title }
        val titleText = if (updatedTitles.isNotEmpty()) {
            "（${updatedTitles.joinToString("、")}）"
        } else ""
        GlassAlertDialog(
            onDismissRequest = { viewModel.cancelSourceRefresh() },
            title = { androidx.compose.material3.Text("同步源文件更新？") },
            text = {
                androidx.compose.material3.Text(
                    sourceRefreshConfirmBody(count, titleText)
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        viewModel.confirmSourceRefresh(refreshIds) { showMessage(it) }
                    },
                ) {
                    androidx.compose.material3.Text("重新同步")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.cancelSourceRefresh() }) {
                    androidx.compose.material3.Text("取消")
                }
            },
        )
    }
}

internal val LocalShelfSnackbar =
    staticCompositionLocalOf<SnackbarHostState?> { null }

// ======= ShelfAction → 副作用分发器 =======
@Suppress("LongParameterList")
private fun handleShelfAction(
    action: ShelfAction,
    context: android.content.Context,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    viewModel: ShelfViewModel,
    taxonomyVm: TaxonomyViewModel,
    setQuery: (String) -> Unit,
    setDebouncedQuery: (String) -> Unit,
    setViewMode: (ShelfViewMode) -> Unit,
    setSortMode: (ShelfSortMode) -> Unit,
    setStatusFilter: (ShelfStatusFilter) -> Unit,
    setSearchActive: (Boolean) -> Unit,
    setShowPageMenu: (Boolean) -> Unit,
    setSelectionMode: (Boolean) -> Unit,
    clearSelected: () -> Unit,
    addSelectedAll: (Collection<String>) -> Unit,
    toggleSelectedId: (String) -> Unit,
    visibleBookIds: () -> Collection<String>,
    currentActionBookId: () -> String?,
    setSelectedShelfId: (String) -> Unit,
    setSelectedCategoryId: (String) -> Unit,
    setSelectedTagId: (String) -> Unit,
    resetFilters: () -> Unit,
    setBatchSheet: (BatchSheetKind?) -> Unit,
    setDetailBookId: (String?) -> Unit,
    setActionBookId: (String?) -> Unit,
    setConfirmDeleteIds: (List<String>?) -> Unit,
    setShowImportHistory: (Boolean) -> Unit,
    setShowImportSource: (Boolean) -> Unit,
    setShowFilterPanel: (Boolean) -> Unit,
    setShowSortSheet: (Boolean) -> Unit,
    setShowDesktopBooks: (Boolean) -> Unit,
    exitSelection: () -> Unit,
    launchImportFiles: () -> Unit,
    launchImportFolder: () -> Unit,
    launchReselectFile: (String) -> Unit,
    launchChangeCover: (String) -> Unit,
) {
    fun showMessage(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }
    when (action) {
        ShelfAction.OpenOrganizer -> navController.navigate("shelf/organizer")
        ShelfAction.OpenSearch -> navController.navigate("shelf/search")
        ShelfAction.ExitSearch -> { setSearchActive(false); setQuery(""); setDebouncedQuery("") }
        is ShelfAction.UpdateQuery -> setQuery(action.query)
        ShelfAction.TogglePageMenu -> setShowPageMenu(true)
        ShelfAction.ClosePageMenu -> setShowPageMenu(false)
        ShelfAction.EnterSelection -> { setSelectionMode(true); clearSelected(); setShowPageMenu(false) }
        ShelfAction.ExitSelection -> exitSelection()
        ShelfAction.OpenImportSource -> navController.navigate("shelf/import")
        ShelfAction.OpenImportHistory -> { setShowPageMenu(false); navController.navigate("shelf/import") }
        ShelfAction.OpenDesktopBooks -> { setShowPageMenu(false); setShowDesktopBooks(true) }

        ShelfAction.OpenSortSheet -> navController.navigate("shelf/organizer/select/sort")
        is ShelfAction.SelectSort -> { setSortMode(action.sort); setShowSortSheet(false) }
        ShelfAction.DismissSortSheet -> setShowSortSheet(false)
        ShelfAction.OpenFilterPanel -> setShowFilterPanel(true)
        is ShelfAction.SelectShelf -> setSelectedShelfId(action.id)
        is ShelfAction.SelectCategory -> setSelectedCategoryId(action.id)
        is ShelfAction.SelectTag -> setSelectedTagId(action.id)
        ShelfAction.DismissFilterPanel -> setShowFilterPanel(false)
        ShelfAction.ToggleViewMode -> {
            val current = viewModel.session.value.viewMode
            setViewMode(if (current == ShelfViewMode.GRID) ShelfViewMode.LIST else ShelfViewMode.GRID)
        }
        is ShelfAction.UpdateStatusFilter -> setStatusFilter(action.status)
        ShelfAction.ResetAllFilters -> resetFilters()

        ShelfAction.SelectAllVisible -> {
            // 全选当前筛选可见的书（聚合后 uiState.filtered 已按筛选/查询计算）
            addSelectedAll(visibleBookIds())
        }
        ShelfAction.ClearSelection -> clearSelected()
        is ShelfAction.ToggleSelected -> toggleSelectedId(action.bookId)

        is ShelfAction.OpenBook -> {
            // 打开即视为「已查看」，清掉「新」角标。放在导航之前：
            // 正文缺失时用户也已看到这本书，角标继续挂着没有意义。
            viewModel.clearNewBookId(action.book.id)
            val label = bookNotReadyLabel(action.book)
            if (label == null) navController.navigate("reader/${action.book.id}")
            else showMessage("《${action.book.title}》${label}，暂时无法打开。请检查文件状态或重新导入/下载正文。")
        }
        is ShelfAction.ToggleActions -> {
            // 原实现误用 lambda 类名比较恒 false：改为同书点击关闭弹层
            setActionBookId(if (action.bookId == currentActionBookId()) null else action.bookId)
        }
        is ShelfAction.DismissActionSheet -> setActionBookId(null)
        is ShelfAction.ContinueFromActionSheet -> {
            viewModel.clearNewBookId(action.book.id)
            if (bookNotReadyLabel(action.book) == null)
                navController.navigate("reader/${action.book.id}")
            else showMessage("《${action.book.title}》暂无可离线正文，请先导入或下载。")
            setActionBookId(null)
        }
        is ShelfAction.DownloadBook -> viewModel.downloadBookContent(action.bookId) { msg -> showMessage(msg) }
        is ShelfAction.ReselectFile -> launchReselectFile(action.bookId)
        is ShelfAction.OpenDetail -> { setActionBookId(null); setDetailBookId(action.bookId) }
        ShelfAction.DismissDetailSheet -> setDetailBookId(null)
        is ShelfAction.DeleteSingle -> setConfirmDeleteIds(listOf(action.bookId))

        // 阅读状态动作：统一在写库后关掉长按面板，并把结果消息交给 snackbar。
        is ShelfAction.MarkFinished -> {
            setActionBookId(null)
            viewModel.markFinished(action.bookId) { showMessage(it) }
        }
        is ShelfAction.MarkReading -> {
            setActionBookId(null)
            viewModel.markReading(action.bookId) { showMessage(it) }
        }
        is ShelfAction.ShelveBook -> {
            setActionBookId(null)
            viewModel.shelveBook(action.bookId) { showMessage(it) }
        }

        is ShelfAction.BatchAddToShelf -> setBatchSheet(BatchSheetKind.SHELF)
        is ShelfAction.BatchSetCategory -> setBatchSheet(BatchSheetKind.CATEGORY)
        is ShelfAction.BatchAddTag -> setBatchSheet(BatchSheetKind.TAG)
        is ShelfAction.BatchDownload -> action.ids.forEach { bid -> viewModel.downloadBookContent(bid) { showMessage(it) } }
        is ShelfAction.BatchClearCache -> {
            val ids = action.ids.toList()
            if (ids.isNotEmpty()) { viewModel.clearCacheForBooks(ids) { showMessage(it) }; exitSelection() }
            else showMessage("请先选择要清理缓存的书籍")
        }
        is ShelfAction.BatchRequestDelete -> setConfirmDeleteIds(action.ids.toList())
        is ShelfAction.BatchMarkFinished -> {
            val ids = action.ids.toList()
            if (ids.isNotEmpty()) { viewModel.markFinishedBatch(ids) { showMessage(it) }; exitSelection() }
            else showMessage("请先选择要标记的书籍")
        }
        is ShelfAction.BatchMarkReading -> {
            val ids = action.ids.toList()
            if (ids.isNotEmpty()) { viewModel.markReadingBatch(ids) { showMessage(it) }; exitSelection() }
            else showMessage("请先选择要取消的书籍")
        }
        is ShelfAction.SelectBatchSheetKind -> setBatchSheet(action.kind)
        ShelfAction.DismissBatchSheet -> setBatchSheet(null)

        is ShelfAction.BatchApplyTaxonomy -> Unit
        is ShelfAction.BatchRemoveTag -> {
            action.bookIds.forEach { taxonomyVm.removeTagFromBook(it, action.tagId) }
            exitSelection(); showMessage("已移除标签")
        }
        is ShelfAction.BatchCreateTaxonomy -> {
            when (action.kind) {
                BatchSheetKind.SHELF -> scope.launch {
                    val id = taxonomyVm.createShelfAndGetId(action.name)
                    taxonomyVm.addBooksToShelf(emptyList(), id)
                    exitSelection(); showMessage("已创建书单并添加选中书籍")
                }
                BatchSheetKind.CATEGORY -> scope.launch {
                    val id = taxonomyVm.createCategoryAndGetId(action.name)
                    taxonomyVm.setCategoryForBooks(emptyList(), id)
                    exitSelection(); showMessage("已创建分类并应用到选中书籍")
                }
                BatchSheetKind.TAG -> scope.launch {
                    val id = taxonomyVm.createTagAndGetId(action.name)
                    taxonomyVm.addTagToBooks(emptyList(), id)
                    exitSelection(); showMessage("已创建并添加到选中书籍")
                }
            }
        }

        is ShelfAction.DetailRemoveShelf -> {
            taxonomyVm.removeBookFromShelf(action.bookId, action.shelfId); showMessage("已移除书单")
        }
        is ShelfAction.DetailRemoveCategory -> {
            taxonomyVm.removeCategoryFromBook(action.bookId, action.categoryId); showMessage("已移除分类")
        }
        is ShelfAction.DetailRemoveTag -> {
            taxonomyVm.removeTagFromBook(action.bookId, action.tagId); showMessage("已移除标签")
        }
        is ShelfAction.DetailAddTag -> {
            taxonomyVm.addTagToBook(action.bookId, action.tagId)
        }
        is ShelfAction.DetailUpdateBook -> viewModel.updateBookInfo(action.bookId, action.title, action.author, action.description) { showMessage(it) }
        is ShelfAction.DetailChangeCover -> launchChangeCover(action.bookId)
        is ShelfAction.DetailChangeTextCover -> {
            val title = (viewModel.uiState.value.library.books.firstOrNull { it.id == action.bookId })?.title ?: ""
            viewModel.setBookTextCover(action.bookId, generateTextCoverDataUrl(title)) { showMessage(it) }
        }
        is ShelfAction.DetailResetCover -> viewModel.resetBookCover(action.bookId) { showMessage(it) }
        is ShelfAction.DetailContinue -> {
            viewModel.clearNewBookId(action.book.id)
            if (bookNotReadyLabel(action.book) == null)
                navController.navigate("reader/${action.book.id}")
            else showMessage("《${action.book.title}》暂无可离线正文，请先导入或下载。")
            setDetailBookId(null)
        }
        is ShelfAction.DetailDownload -> viewModel.downloadBookContent(action.bookId) { showMessage(it) }
        is ShelfAction.DetailDelete -> { setDetailBookId(null); setConfirmDeleteIds(listOf(action.bookId)) }

        is ShelfAction.ImportFiles -> if (action.uris.isNotEmpty()) viewModel.importFiles(action.uris)
        is ShelfAction.ImportFolder -> viewModel.importFolder(action.uri)
        ShelfAction.RetryFailedImports -> viewModel.retryFailedImports()
        ShelfAction.DismissImportBatchSummary -> viewModel.dismissImportBatchSummary()
        ShelfAction.ClearImportHistory -> viewModel.clearImportHistory()
        ShelfAction.DismissImportSource -> setShowImportSource(false)
        ShelfAction.DismissImportHistory -> setShowImportHistory(false)
        ShelfAction.DismissDesktopBooks -> setShowDesktopBooks(false)
        is ShelfAction.DesktopDownloadBook -> viewModel.downloadBookContent(action.bookId) { showMessage(it) }

        ShelfAction.CancelDeleteConfirm -> setConfirmDeleteIds(null)
        is ShelfAction.ConfirmDelete -> {
            setConfirmDeleteIds(null)
            // 与确认框同一条路径：整批一次事务、一张凭证，撤销范围等于提示的数量。
            viewModel.deleteBooks(action.ids) { ok ->
                if (!ok) showMessage(deletionFailedMessage(context))
            }
        }
        ShelfAction.CancelSourceRefreshConfirm -> viewModel.cancelSourceRefresh()
        is ShelfAction.ConfirmSourceRefresh -> viewModel.confirmSourceRefresh(action.bookIds) { showMessage(it) }
        is ShelfAction.ReselectFileResult -> viewModel.reselectFile(action.bookId, action.uri) { showMessage(it) }
        is ShelfAction.CoverChangeResult -> viewModel.updateBookCover(action.bookId, action.uri) { showMessage(it) }

        // 下拉刷新同时同步源文件更新（M-BUG-03 第二阶段）：结果通过 Snackbar 反馈，
        // 「无更新」也必须有回执，否则用户无法区分「没更新」与「手势没生效」。
        ShelfAction.TriggerRefresh -> viewModel.refresh { showMessage(it) }
        else -> Unit
    }
}
