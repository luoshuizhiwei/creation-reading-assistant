package com.creationreadingassistant.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Archive
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.util.bookNotReadyLabel
import com.creationreadingassistant.ui.util.formatBookProgressForCard
import com.creationreadingassistant.ui.util.hasBookBeenRead
import com.creationreadingassistant.ui.util.isBookDisplayable
import com.creationreadingassistant.ui.viewmodel.BookViewModel
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.launch
import java.time.Instant

private enum class ContinueSortKey { RECENT, PROGRESS, CREATED, TITLE }

private val SORT_LABELS = mapOf(
    ContinueSortKey.RECENT to "最近阅读",
    ContinueSortKey.PROGRESS to "阅读进度",
    ContinueSortKey.CREATED to "加入书库时间",
    ContinueSortKey.TITLE to "书名",
)

private enum class MenuView { NONE, MORE, SORT }

private data class ContinueItem(
    val book: BookEntity,
    val progress: Float,
    val lastReadAt: String?,
    val originalIndex: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContinueSheet(
    navController: NavHostController,
    books: List<BookEntity>,
    progressById: Map<String, ReadingProgressEntity>,
    sessions: Map<String, List<com.creationreadingassistant.data.local.entity.ReadingSessionEntity>>,
    removedIds: Map<String, String>,
    viewModel: BookViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var menuView by remember { mutableStateOf(MenuView.NONE) }
    var manageMode by remember { mutableStateOf(false) }
    var sortKey by remember { mutableStateOf(ContinueSortKey.RECENT) }
    var sortAsc by remember { mutableStateOf(false) }
    var actionBook by remember { mutableStateOf<BookEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<BookEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)

    val items = remember(books, progressById, sessions, removedIds, sortKey, sortAsc) {
        buildContinueItems(books, progressById, sessions, removedIds)
            .let { list ->
                val comparator = when (sortKey) {
                    ContinueSortKey.RECENT -> compareBy<ContinueItem, String?>(nullsLast()) { it.lastReadAt }
                    ContinueSortKey.PROGRESS -> compareBy { it.progress }
                    ContinueSortKey.CREATED -> compareBy<ContinueItem, String?>(nullsLast()) { it.book.imported_at }
                    ContinueSortKey.TITLE -> compareBy { it.book.title }
                }
                list.sortedWith(if (sortAsc) comparator else comparator.reversed())
            }
    }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.92f),
    ) {
        // 局部捕获非空快照：分支内各回调闭包直接引用已判空的局部 val，
        // 不再对委托状态反复强解包。
        val activeBook = actionBook
        when {
            activeBook != null -> ActionBookPage(
                book = activeBook,
                onBack = { actionBook = null },
                // H4：对齐网页「查看详情」→ 打开书籍详情面板（复用书架 BookDetailSheet）
                onShowDetail = {
                    val id = activeBook.id
                    onDismiss()
                    navController.navigate("shelf/detail/$id")
                },
                onMarkRead = {
                    haptic(HapticFeedbackType.LongPress)
                    viewModel.markRead(activeBook.id)
                    actionBook = null
                },
                onMarkUnread = {
                    viewModel.markUnread(activeBook.id)
                    actionBook = null
                },
                onRemoveFromContinue = {
                    viewModel.removeFromContinue(activeBook.id)
                    actionBook = null
                },
                onShelve = {
                    viewModel.shelve(activeBook.id)
                    actionBook = null
                },
                onDelete = { deleteTarget = actionBook; actionBook = null },
            )
            else -> ListPage(
                items = items,
                menuView = menuView,
                manageMode = manageMode,
                sortKey = sortKey,
                sortAsc = sortAsc,
                onMenuToggle = { menuView = if (menuView == MenuView.MORE) MenuView.NONE else MenuView.MORE },
                onSortOpen = { menuView = MenuView.SORT },
                onSortBack = { menuView = MenuView.MORE },
                onSortKey = { sortKey = it; menuView = MenuView.NONE },
                onSortAsc = { sortAsc = true; menuView = MenuView.NONE },
                onSortDesc = { sortAsc = false; menuView = MenuView.NONE },
                onToggleManage = { manageMode = !manageMode; menuView = MenuView.NONE },
                // H2：打开阅读器前做 readiness 校验，未就绪提示（列表项通常已可读，此处与网页对齐兜底）
                onOpenBook = { book ->
                    viewModel.clearContinueRemoval(book.id)
                    val label = bookNotReadyLabel(book)
                    if (label == null) {
                        onDismiss()
                        navController.navigate("reader/${book.id}")
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("《${book.title}》${label}，暂时无法打开。请检查文件状态或重新导入/下载正文。")
                        }
                    }
                },
                onAction = { actionBook = it },
                onRemove = { viewModel.removeFromContinue(it.id) },
                onDismissMenu = { menuView = MenuView.NONE },
            )
        }
        SnackbarHost(snackbarHostState)
    }

    val pendingDelete = deleteTarget
    if (pendingDelete != null) {
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除书籍") },
            text = { Text("确定从书架删除《${pendingDelete.title}》吗？本地正文文件、阅读进度、书签和笔记会一并移除，删除后可随时从书架恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(pendingDelete.id) {}
                        deleteTarget = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ListPage(
    items: List<ContinueItem>,
    menuView: MenuView,
    manageMode: Boolean,
    sortKey: ContinueSortKey,
    sortAsc: Boolean,
    onMenuToggle: () -> Unit,
    onSortOpen: () -> Unit,
    onSortBack: () -> Unit,
    onSortKey: (ContinueSortKey) -> Unit,
    onSortAsc: () -> Unit,
    onSortDesc: () -> Unit,
    onToggleManage: () -> Unit,
    onOpenBook: (BookEntity) -> Unit,
    onAction: (BookEntity) -> Unit,
    onRemove: (BookEntity) -> Unit,
    onDismissMenu: () -> Unit,
) {
    Box {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SheetHandle()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("继续阅读", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onMenuToggle) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多选项")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                EmptyContinueBody()
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.book.id }) { item ->
                        ContinueListItem(
                            item = item,
                            manageMode = manageMode,
                            onClick = { onOpenBook(item.book) },
                            onAction = { onAction(item.book) },
                            onRemove = { onRemove(item.book) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (menuView != MenuView.NONE) {
            MenuOverlay(
                menuView = menuView,
                sortKey = sortKey,
                sortAsc = sortAsc,
                manageMode = manageMode,
                hasReliableCreatedAt = items.all { it.book.imported_at != null },
                onSortOpen = onSortOpen,
                onSortBack = onSortBack,
                onSortKey = onSortKey,
                onSortAsc = onSortAsc,
                onSortDesc = onSortDesc,
                onToggleManage = onToggleManage,
                onDismiss = onDismissMenu,
            )
        }
    }
}

@Composable
private fun MenuOverlay(
    menuView: MenuView,
    sortKey: ContinueSortKey,
    sortAsc: Boolean,
    manageMode: Boolean,
    hasReliableCreatedAt: Boolean,
    onSortOpen: () -> Unit,
    onSortBack: () -> Unit,
    onSortKey: (ContinueSortKey) -> Unit,
    onSortAsc: () -> Unit,
    onSortDesc: () -> Unit,
    onToggleManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
            // 旧实现用 scrim.copy(alpha = 0.01f) 是反模式：用 scrim 颜色却几乎完全透明，
            // 实际只想做一个能接收点击关闭的透明遮罩。直接用 Transparent 表达真实意图。
            .background(Color.Transparent),
        contentAlignment = Alignment.TopEnd,
    ) {
        SectionCard(
            modifier = Modifier
                .padding(top = 56.dp, end = 8.dp)
                .width(220.dp)
                .clickable(enabled = false) {},
            contentPadding = 0.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                when (menuView) {
                    MenuView.MORE -> {
                        MenuRow(label = "排序方式", hasChild = true, onClick = onSortOpen)
                        MenuRow(
                            label = if (manageMode) "完成管理" else "管理继续阅读",
                            onClick = onToggleManage,
                        )
                    }
                    MenuView.SORT -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onSortBack)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", modifier = Modifier.size(AppIconSize.Small))
                            Spacer(Modifier.width(8.dp))
                            Text("排序方式", style = MaterialTheme.typography.labelLarge)
                        }
                        ContinueSortKey.entries
                            .filter { it != ContinueSortKey.CREATED || hasReliableCreatedAt }
                            .forEach { key ->
                                MenuRow(
                                    label = SORT_LABELS[key] ?: key.name,
                                    selected = sortKey == key,
                                    onClick = { onSortKey(key) },
                                )
                            }
                        Spacer(Modifier.height(8.dp))
                        MenuRow(label = "升序", selected = sortAsc, onClick = onSortAsc)
                        MenuRow(label = "降序", selected = !sortAsc, onClick = onSortDesc)
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    selected: Boolean = false,
    hasChild: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        when {
            selected -> Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(AppIconSize.Small))
            hasChild -> Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(AppIconSize.Small))
        }
    }
}

@Composable
private fun ActionBookPage(
    book: BookEntity,
    onBack: () -> Unit,
    onShowDetail: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onRemoveFromContinue: () -> Unit,
    onShelve: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SheetHandle()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回列表")
            }
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(16.dp))
        ActionButton(icon = { Icon(Icons.Outlined.MenuBook, contentDescription = null) }, label = "查看详情", onClick = onShowDetail)
        ActionButton(icon = { Icon(Icons.Outlined.Check, contentDescription = null) }, label = "标记为已读完", onClick = onMarkRead)
        ActionButton(icon = { Icon(Icons.Outlined.Close, contentDescription = null) }, label = "从继续阅读移除", onClick = onRemoveFromContinue)
        ActionButton(icon = { Icon(Icons.Outlined.Archive, contentDescription = null) }, label = "搁置本书", onClick = onShelve)
        ActionButton(icon = { Icon(Icons.Outlined.Book, contentDescription = null) }, label = "标记为未读", onClick = onMarkUnread)
        ActionButton(
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            label = "删除本地书籍",
            isDanger = true,
            onClick = onDelete,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = icon,
        headlineContent = {
            Text(
                label,
                color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

@Composable
private fun ContinueListItem(
    item: ContinueItem,
    manageMode: Boolean,
    onClick: () -> Unit,
    onAction: () -> Unit,
    onRemove: () -> Unit,
) {
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 0.dp,
        onClick = if (!manageMode) onClick else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 复用共享 BookCover：消除 56dp Box + AsyncImage + 占位 Text 的手搓实现。
            // 56dp 小尺寸下角标/高光会喧宾夺主，关闭 showBadge / showSheen。
            // percent 传 null：本列表是"继续阅读"，不显示读完藏书印（已读完的书本就不在列表里）。
            BookCover(
                book = item.book,
                modifier = Modifier.size(56.dp),
                showBadge = false,
                showSheen = false,
                percent = null,
                fallback = {
                    Text(
                        item.book.title.take(2),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(item.book.author ?: "作者未知", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatBookProgressForCard(item.progress), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (manageMode) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Close, contentDescription = "从继续阅读移除", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = onAction) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "操作")
                }
            }
        }
    }
}

@Composable
private fun EmptyContinueBody() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("暂无可以继续阅读的书籍", style = MaterialTheme.typography.titleMedium)
        Text("开始阅读后，书籍会出现在这里", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun buildContinueItems(
    books: List<BookEntity>,
    progressById: Map<String, ReadingProgressEntity>,
    sessions: Map<String, List<com.creationreadingassistant.data.local.entity.ReadingSessionEntity>>,
    removedIds: Map<String, String>,
): List<ContinueItem> {
    val now = System.currentTimeMillis()
    val msPerDay = 24 * 60 * 60 * 1000L
    val recencyDecayMs = 7 * msPerDay
    val monthAgo = now - 30 * msPerDay

    return books.mapIndexedNotNull { index, book ->
        val progress = progressById[book.id]
        val pct = progress?.progress_percent ?: 0f
        if (!isBookDisplayable(book)) return@mapIndexedNotNull null
        if (!hasBookBeenRead(book, progress, sessions[book.id])) return@mapIndexedNotNull null
        if (progress?.readingState == ReadingCompletionState.SHELVED) return@mapIndexedNotNull null
        if (pct >= 99.5f) return@mapIndexedNotNull null
        val removedAt = removedIds[book.id]
        if (removedAt != null) {
            val lastReadAt = lastReadAtFor(book, progress, sessions[book.id])
            if (lastReadAt == null || lastReadAt <= removedAt) {
                return@mapIndexedNotNull null
            }
        }

        val lastReadAt = lastReadAtFor(book, progress, sessions[book.id])
        val lastReadTime = lastReadAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        val recencyScore = kotlin.math.exp((lastReadTime - now).toDouble() / recencyDecayMs)
        val recentSessions = sessions[book.id]?.count { session ->
            val t = session.created_at?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
            t > monthAgo
        } ?: 0
        val frequencyScore = (recentSessions.coerceAtMost(10)) / 10.0
        val score = recencyScore * 0.6 + frequencyScore * 0.4

        ContinueItem(
            book = book,
            progress = pct,
            lastReadAt = lastReadAt,
            originalIndex = index,
        )
    }
}

private fun lastReadAtFor(
    book: BookEntity,
    progress: ReadingProgressEntity?,
    sessions: List<com.creationreadingassistant.data.local.entity.ReadingSessionEntity>?,
): String? {
    progress?.last_read_at?.let { return it }
    return sessions
        ?.filter { it.book_id == book.id }
        ?.mapNotNull { it.ended_at ?: it.started_at }
        ?.maxOrNull()
}

// isBookReadableOnDevice / bookNotReadyLabel / hasBookBeenRead / formatBookProgress
// 已抽到 ui/util/BookReadiness.kt（统一命名为 isBookDisplayable / formatBookProgressForCard）。
// epochDayOf / weekStartEpochDay 已删（前者 HomeViewModel 有自己的私有版，后者 0 调用）。
