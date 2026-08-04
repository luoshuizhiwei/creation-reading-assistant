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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.listItemEnter
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.GridView
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
import androidx.compose.runtime.remember
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
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.ListSkeleton
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
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
) {
    val haptic = rememberHaptic(rememberReducedMotion())

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
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "打开书架整理",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else null

    val navigationIcon: (@Composable () -> Unit)? = if (state.selectionMode) {
        {
            IconButton(onClick = { onAction(ShelfAction.ExitSelection) }) {
                Icon(
                    imageVector = Icons.Filled.Close,
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
                            imageVector = Icons.Filled.Add,
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
                            text = { Text("批量选择") },
                            onClick = { onAction(ShelfAction.EnterSelection) },
                            leadingIcon = { Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null) },
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
                                    imageVector = Icons.Filled.History,
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
                            imageVector = Icons.Filled.Close,
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

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
            .testTag("shelf-content-box"),
    ) {
        val columns = when {
            state.viewMode == ShelfViewMode.LIST -> GridCells.Fixed(1)
            maxWidth >= layout.wideScreenBreakpoint -> GridCells.Adaptive(112.dp)
            else -> GridCells.Fixed(3)
        }
        CompositionLocalProvider(LocalViewConfiguration provides longPressConfig) {
            LazyVerticalGrid(
                columns = columns,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(viewportPadding)
                    .testTag("shelf-main-lazy"),
                contentPadding = PaddingValues(
                    horizontal = layout.pageHorizontal,
                    vertical = layout.pageVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(layout.contentGap),
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
                    val shelfSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? =
                        if (state.viewMode == ShelfViewMode.LIST) {
                            { GridItemSpan(maxLineSpan) }
                        } else null
                    itemsIndexed(
                        items = state.filtered,
                        key = { _, item -> item.bookId },
                        contentType = { _, _ -> state.viewMode },
                        span = shelfSpan,
                    ) { index, item ->
                        val book = item.book
                        BookTile(
                            book = book,
                            percent = progressFor(state.library.progressById, book.id),
                            viewMode = state.viewMode,
                            selectionMode = state.selectionMode,
                            selected = state.selectedIds.contains(book.id),
                            actionsOpen = state.actionBookId == book.id,
                            downloading = state.auxiliary.downloadingIds.contains(book.id),
                            onOpenBook = { onAction(ShelfAction.OpenBook(it)) },
                            onToggleActions = { onAction(ShelfAction.ToggleActions(it)) },
                            onToggleSelected = { onAction(ShelfAction.ToggleSelected(it)) },
                            modifier = Modifier.listItemEnter(index, reducedMotion),
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
                .clickable { onAction(ShelfAction.OpenOrganizer) }
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
