package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.GridView
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material.pullrefresh.PullRefreshDefaults
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.layout.adaptivePageMetrics
import com.creationreadingassistant.ui.theme.ListSkeleton
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * **Shelf 纯 UI 层 Screen**：只有 (state: ShelfUiState, onAction: (ShelfAction) -> Unit)。
 *
 * 验收：
 *  - 禁止 hiltViewModel / NavHostController / collectAsStateWithLifecycle / DAO
 *  - 使用公共 AppScreenScaffold（不创建 Scaffold）
 *  - 不创建第二个顶栏：搜索/多选通过替换 AppScreenScaffold 的 title / actions / navigationIcon / supporting content 实现
 *  - 只有一个主滚动容器（PageLazyColumn），工具区和书网格同级平铺，互不嵌套滚动
 *  - pullRefresh 只包裹 Box + PageLazyColumn（内容区），刷新指示器位于 viewportPadding 下方（不覆盖顶栏）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
internal fun ShelfScreen(
    state: ShelfUiState,
    onAction: (ShelfAction) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 「查看」定位候选：**本批导入成功**的书 id（按导入顺序，由 Route 设置）。
     *
     * 传列表而不是单个 id：本批可能导入多本，取第一本当前可见的即可 —— 否则第一本
     * 恰好被筛选隐藏时，整批都定位不到。
     * 无论是否命中，处理完都由 Route 清空：既避免列表重组时反复滚动，也避免过期请求
     * 在之后某次筛选变化时突然触发一次「跳到旧书」。
     */
    focusBookIds: List<String> = emptyList(),
    /**
     * 定位请求已处理。`reached = true` 表示已滚动到目标；`false` 表示候选全部被当前
     * 搜索 / 筛选排除 —— 调用方据此清理状态**并告知用户**，不得静默无响应。
     */
    onFocusHandled: (reached: Boolean) -> Unit = {},
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    // 网格状态提升到 Screen：导入完成的「查看」定位需要主动 scrollToItem，
    // 而 LazyVerticalGrid 的 state 原本在 ShelfContent 内部创建，外部够不到。
    val gridState = rememberLazyGridState()

    // 定位判定必须读**最新**的 state：effect 体在 delay 中挂起时，组合期捕获的 `state`
    // 是旧快照，用它会误判「书还没进列表」为「被筛选排除」。走 rememberUpdatedState 读状态。
    val currentState by rememberUpdatedState(state)

    // 滚动到目标书。用「目标已可见」而非固定下标作为终止条件：
    // 列表会因筛选、导入、排序变化而位移，一次性 scrollToItem 可能落在错误位置。
    //
    // key 里**刻意不含 state.filtered.size**：下面是有界等待，若按列表长度重启 effect，
    // 每来一次数据就把轮询计数清零，极端情况下能一直重启 —— 等于变相的无限等待。
    // 不带它则单次运行内计数严格单调，上界是硬的；新数据由 50ms 一次的轮询接住
    // （effect 体读的是 currentState，不需要靠重启来刷新）。
    // showSkeleton 必须进 key：骨架期这一轮直接返回、不判定也不清理，
    // 若不等它翻转，下面那轮就永远不会重跑。
    LaunchedEffect(focusBookIds, state.showSkeleton, state.viewMode) {
        if (focusBookIds.isEmpty()) return@LaunchedEffect
        if (currentState.showSkeleton) return@LaunchedEffect
        // 导入落库与书架列表流之间存在天然延迟：书可能已经写入但还没出现在 filtered 里。
        // 此时立刻断言「被筛选排除」会误报，故在**有界**次数内等待它出现（见 awaitFocusTarget）；
        // 等满仍未出现才判定不可达 —— 有界保证不会无限挂起。
        val target = awaitFocusTarget(
            candidates = focusBookIds,
            visibleBookIds = { currentState.filtered.map { it.bookId }.toSet() },
        )
        val index = target?.let { shelfGridIndexOfBook(currentState, it) }
        if (index == null) {
            // 候选全部不可见（或不可滚动到）：必须清掉 focus，否则它会长期残留，
            // 用户之后改筛选时才突然滚动；同时回执 false 让 Route 说明原因。
            onFocusHandled(false)
            return@LaunchedEffect
        }
        gridState.animateScrollToItem(index)
        onFocusHandled(true)
    }

    val title = when {
        state.selectionMode -> "选择书籍"
        else -> "书架"
    }

    val titleContent: (@Composable () -> Unit)? = if (!state.selectionMode) {
        {
            Row(
                modifier = Modifier
                    .clickable { onAction(ShelfAction.OpenOrganizer) }
                    .padding(top = 6.dp, end = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("书架", style = MaterialTheme.typography.headlineLarge, maxLines = 1)
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = "打开书架整理",
                    modifier = Modifier.size(AppIconSize.Medium),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else null

    val navigationIcon: (@Composable () -> Unit)? = if (state.selectionMode) {
        {
            IconButton(onClick = { onAction(ShelfAction.ExitSelection) }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "关闭选择模式",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    } else {
        null
    }

    val actions: @Composable RowScope.() -> Unit = {
        when {
            state.selectionMode -> {}
            else -> {
                IconButton(onClick = { onAction(ShelfAction.OpenSearch) }) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onAction(ShelfAction.OpenImportSource)
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "导入书籍",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.hasActiveImports) {
                            val batch = state.auxiliary.importBatch
                            val progress = if (batch.total > 0) {
                                batch.completed.toFloat() / batch.total.toFloat()
                            } else 0f
                            CircularProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                Box {
                    IconButton(
                        onClick = {
                            onAction(
                                if (state.showPageMenu) ShelfAction.ClosePageMenu
                                else ShelfAction.TogglePageMenu
                            )
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = state.showPageMenu,
                        onDismissRequest = { onAction(ShelfAction.ClosePageMenu) },
                    ) {
                        DropdownMenuItem(
                            text = { Text("书架整理") },
                            onClick = {
                                onAction(ShelfAction.ClosePageMenu)
                                onAction(ShelfAction.OpenOrganizer)
                            },
                            leadingIcon = { Icon(imageVector = Icons.Outlined.Folder, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("批量选择") },
                            onClick = { onAction(ShelfAction.EnterSelection) },
                            leadingIcon = { Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = {
                                val taskCount = state.auxiliary.importTasks.size
                                Text(
                                    if (taskCount > 0) "导入记录（$taskCount）"
                                    else "导入记录",
                                )
                            },
                            onClick = { onAction(ShelfAction.OpenImportHistory) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    AppScreenScaffold(
        modifier = modifier.testTag("shelf-screen"),
        title = title,
        titleContent = titleContent,
        navigationIcon = navigationIcon,
        actions = actions,
        snackbarHost = {
            val local = LocalShelfSnackbar.current
            if (local != null) {
                androidx.compose.material3.SnackbarHost(hostState = local)
            }
        },
    ) { viewportPadding ->
        ShelfContent(
            state = state,
            onAction = onAction,
            viewportPadding = viewportPadding,
            gridState = gridState,
        )
    }
}

/**
 * 搜索输入行：置于 AppScreenScaffold 的 topBarSupportingContent（主顶栏下方）。
 * 避免重复创建第二个 AppTopBar。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputRow(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalLayoutTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = layout.pageHorizontal)
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val focus = remember { FocusRequester() }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("搜索书名 / 作者") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "清空",
                        )
                    }
                }
            },
            singleLine = true,
            shape = LocalComponentSpec.current.pillShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/** 用于带徽标的 IconButton 外层包装（保持通用 RowScope 行为）。 */
@Composable
private fun BadgeBox(
    badge: @Composable (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.size(48.dp)) {
        Box(Modifier.align(Alignment.Center)) { content() }
        if (badge != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 6.dp)) { badge() }
        }
    }
}

/**
 * AppScreenScaffold 内容块。
 *
 * 结构：
 *  Box(pullRefresh)                    ← 只包裹"内容区"，顶栏在 AppScreenScaffold 外层
 *   └── PageLazyColumn（单一主滚动）
 *         ├── ImportQueueCard
 *         ├── SelectionBar / StatusRail+Toolbar / ActiveFilterNote
 *         ├── 骨架 / 空态 / BookGrid
 *         └── 底部计数
 *   ShelfRefreshIndicator（位于 viewportPadding.top 之后，不遮挡顶栏）
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ShelfContent(
    state: ShelfUiState,
    onAction: (ShelfAction) -> Unit,
    viewportPadding: PaddingValues,
    gridState: LazyGridState,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val layout = LocalLayoutTokens.current
    val componentSpec = LocalComponentSpec.current
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { onAction(ShelfAction.TriggerRefresh) },
    )
    val pullThresholdPx = with(LocalDensity.current) { PullRefreshDefaults.RefreshThreshold.toPx() }
    val baseViewConfig = LocalViewConfiguration.current
    val longPressConfig = remember(baseViewConfig) {
        object : ViewConfiguration by baseViewConfig {
            override val longPressTimeoutMillis: Long get() = 500L
        }
    }

    // 稳定化书籍操作回调：避免在 items 循环中每帧为每个卡片实例化新闭包，保证多选切换时未变动卡片 100% 跳过重组
    val onOpenBookStable: (BookEntity) -> Unit = remember(onAction) {
        { book -> onAction(ShelfAction.OpenBook(book)) }
    }
    val onToggleActionsStable: (String) -> Unit = remember(onAction) {
        { bookId -> onAction(ShelfAction.ToggleActions(bookId)) }
    }
    val onToggleSelectedStable: (String) -> Unit = remember(onAction) {
        { bookId -> onAction(ShelfAction.ToggleSelected(bookId)) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
            .testTag("shelf-content-box"),
    ) {
        val adaptive = adaptivePageMetrics(maxWidth, layout)
        val columns = when {
            state.viewMode == ShelfViewMode.LIST -> GridCells.Fixed(1)
            else -> GridCells.Fixed(shelfGridColumnCount(maxWidth, layout))
        }
        CompositionLocalProvider(LocalViewConfiguration provides longPressConfig) {
            LazyVerticalGrid(
                columns = columns,
                state = gridState,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = adaptive.contentWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(viewportPadding)
                    .testTag("shelf-main-lazy"),
                contentPadding = PaddingValues(
                    horizontal = adaptive.horizontalPadding,
                    vertical = layout.pageVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(if (state.viewMode == ShelfViewMode.LIST) 0.dp else layout.contentGap),
                horizontalArrangement = Arrangement.spacedBy(layout.gridGap),
            ) {
                if (state.selectionMode) {
                    item(key = "selection-bar", span = { GridItemSpan(maxLineSpan) }) {
                        SelectionBar(
                            selectedCount = state.selectedIds.size,
                            visibleCount = state.filtered.size,
                            onSelectAll = { onAction(ShelfAction.SelectAllVisible) },
                            onClear = { onAction(ShelfAction.ClearSelection) },
                        )
                    }
                } else {
                    // 状态分组条常驻首屏：这是「读完的书在哪」的主要入口。
                    // 此前唯一路径是「标题▾ → 书架整理 → 阅读状态 → 全屏单选」四跳，
                    // 且入口名不像筛选，用户实际找不到。
                    item(key = "status-rail", span = { GridItemSpan(maxLineSpan) }) {
                        StatusRail(
                            statusFilter = state.statusFilter,
                            onSelect = { onAction(ShelfAction.UpdateStatusFilter(it)) },
                            counts = state.statusCounts,
                        )
                    }
                    item(key = "toolbar-surface", span = { GridItemSpan(maxLineSpan) }) {
                        ShelfCompactToolbar(state = state, onAction = onAction)
                    }
                }

                if (state.showSkeleton) {
                    item(key = "shelf-skeleton", span = { GridItemSpan(maxLineSpan) }) {
                        ListSkeleton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = layout.relatedGap),
                            count = if (state.viewMode == ShelfViewMode.GRID) 6 else 5,
                            reducedMotion = reducedMotion,
                        )
                    }
                } else if (state.filtered.isEmpty()) {
                    val emptyShelf = state.library.books.isEmpty()
                    val searchNoResult = state.library.books.isNotEmpty() && state.debouncedQuery.isNotEmpty()
                    item(key = "shelf-empty", span = { GridItemSpan(maxLineSpan) }) {
                        FullEmptyState(
                            icon = { LineArtBook(modifier = Modifier.size(56.dp)) },
                            title = when {
                                emptyShelf -> "书架还空着"
                                searchNoResult -> "没有找到匹配的书"
                                else -> "当前筛选下没有书籍"
                            },
                            body = when {
                                emptyShelf -> "导入 TXT、Markdown 或 EPUB 开始本地阅读。"
                                searchNoResult -> "换个书名或作者试试。"
                                else -> "切换到“全部”，或清除阅读状态和高级筛选。"
                            },
                            contentPadding = 32.dp,
                            primaryAction = if (emptyShelf) {
                                "导入一本书" to {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onAction(ShelfAction.OpenImportSource)
                                }
                            } else null,
                            secondaryAction = if (emptyShelf || searchNoResult) null else {
                                "显示全部" to { onAction(ShelfAction.ResetAllFilters) }
                            },
                        )
                    }
                } else {
                    items(
                        items = state.filtered,
                        key = { it.bookId },
                        contentType = { state.viewMode },
                        span = if (state.viewMode == ShelfViewMode.LIST) {
                            { GridItemSpan(maxLineSpan) }
                        } else null,
                    ) { item ->
                        val book = item.book
                        val progressEntity = state.library.progressById[book.id]
                        BookTile(
                            book = book,
                            percent = progressFor(state.library.progressById, book.id),
                            chapterLabel = extractChapterLabel(progressEntity),
                            // S05-①：完成标识（「完」徽章/藏书印/「已读完」）由显式状态决定
                            completionState = progressEntity?.completion_state,
                            viewMode = state.viewMode,
                            selectionMode = state.selectionMode,
                            selected = state.selectedIds.contains(book.id),
                            actionsOpen = state.actionBookId == book.id,
                            downloading = state.auxiliary.downloadingIds.contains(book.id),
                            // M-BUG-03：来源文件在系统目录被改过 → 打「可更新」标签
                            contentUpdated = item.bookId in state.contentUpdatedBookIds,
                            // 新导入且未打开过 → 打「新」标签，打开该书后消失
                            isNew = item.bookId in state.newBookIds,
                            onOpenBook = onOpenBookStable,
                            onToggleActions = onToggleActionsStable,
                            onToggleSelected = onToggleSelectedStable,
                        )
                    }
                    item(key = "shelf-count-footer", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "共 ${state.library.books.size} 本书籍 · 当前显示 ${state.filtered.size} 本",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // 下拉刷新指示器：起始位置 = viewportPadding.top（顶栏下方内容区顶部）
        val topOffsetPx = with(LocalDensity.current) { viewportPadding.calculateTopPadding().roundToPx() }
        ShelfRefreshIndicator(
            state = pullRefreshState,
            refreshing = state.isRefreshing,
            thresholdPx = pullThresholdPx,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .testTag("shelf-refresh-indicator")
                .offset {
                    IntOffset(
                        x = 0,
                        y = topOffsetPx + (if (state.isRefreshing) pullThresholdPx else pullRefreshState.progress * pullThresholdPx).roundToInt(),
                    )
                },
        )
    }
}

@Composable
private fun ShelfCompactToolbar(
    state: ShelfUiState,
    onAction: (ShelfAction) -> Unit,
) {
    val sortLabel = when (state.sortMode) {
        ShelfSortMode.RECENT -> "最近阅读"
        ShelfSortMode.IMPORTED -> "最近导入"
        ShelfSortMode.TITLE -> "书名"
        ShelfSortMode.PROGRESS -> "阅读进度"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${state.filtered.size} 本 · ${state.filterSummary} · $sortLabel",
            modifier = Modifier
                .weight(1f)
                .clickable { onAction(ShelfAction.OpenFilterPanel) }
                .testTag("shelf-filter-summary")
                .padding(vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = { onAction(ShelfAction.ToggleViewMode) }) {
            Icon(
                imageVector = if (state.viewMode == ShelfViewMode.GRID) {
                    Icons.AutoMirrored.Outlined.List
                } else {
                    Icons.Outlined.GridView
                },
                contentDescription = if (state.viewMode == ShelfViewMode.GRID) "切换列表视图" else "切换网格视图",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 从「本批导入成功的书」候选中挑出第一个当前可见的；都不可见返回 null。
 *
 * 抽成纯函数是为了可定向测试：这是「查看」定位里**可判定**的那一步，
 * 不需要 Compose 运行时或真机就能覆盖。旧实现（S05-④）直接取
 * `newBookIds.firstOrNull()` —— 那是跨批次的未查看集合，既可能选中上一批遗留的书，
 * 也不看目标是否被筛掉；本函数把「只认本批」与「必须可见」两件事同时钉住。
 *
 * @param candidates 本批导入成功的 bookId，按导入顺序（空表示本批没有新书）。
 * @param visibleBookIds 当前筛选后仍在列表里的 bookId。
 */
internal fun focusImportTarget(
    candidates: List<String>,
    visibleBookIds: Set<String>,
): String? = candidates.firstOrNull { it in visibleBookIds }

/** 「查看」定位等待书出现在列表里的轮询次数上限（见 [awaitFocusTarget]）。 */
internal const val FOCUS_VISIBILITY_ATTEMPTS = 12

/** 每次轮询间隔；12 × 50ms ≈ 600ms 上界。 */
internal const val FOCUS_VISIBILITY_RETRY_MS = 50L

/**
 * 在**有界**次数内等待候选里出现第一本可见的书；等满仍未出现返回 `null`。
 *
 * 为什么需要它：导入写库与书架列表流（Room → Flow → Compose）之间有天然延迟，
 * 刚导入就点「查看」时书可能**已经落库但还没进 `filtered`**。旧实现此时立刻断言
 * 「被筛选排除」，会给出误报提示并清掉请求 —— 用户明明刚导入成功却被告知找不到。
 *
 * 刻意做成**有界**轮询而不是「等到出现为止」：列表也可能永远不出现该书（确实被筛掉），
 * 无限等待会让请求永远挂着，正是要避免的「过期定位」。超时即判定不可达、交回调用方
 * 清理并告知用户。
 *
 * [visibleBookIds] 是取值函数而非一次性集合：每次轮询都要重新读当前的可见列表
 * （effect 体在 delay 中挂起时，组合期捕获的快照已经过期）。
 *
 * 纯逻辑（只依赖 [delay]），可 JVM 定向测试。
 */
internal suspend fun awaitFocusTarget(
    candidates: List<String>,
    visibleBookIds: () -> Set<String>,
    maxAttempts: Int = FOCUS_VISIBILITY_ATTEMPTS,
    retryDelayMillis: Long = FOCUS_VISIBILITY_RETRY_MS,
): String? {
    if (candidates.isEmpty()) return null
    repeat(maxAttempts) { attempt ->
        focusImportTarget(candidates, visibleBookIds())?.let { return it }
        if (attempt < maxAttempts - 1) delay(retryDelayMillis)
    }
    return null
}
