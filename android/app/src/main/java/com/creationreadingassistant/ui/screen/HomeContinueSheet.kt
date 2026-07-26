package com.creationreadingassistant.ui.screen

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.viewmodel.BookViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var menuView by remember { mutableStateOf(MenuView.NONE) }
    var manageMode by remember { mutableStateOf(false) }
    var sortKey by remember { mutableStateOf(ContinueSortKey.RECENT) }
    var sortAsc by remember { mutableStateOf(false) }
    var actionBook by remember { mutableStateOf<BookEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<BookEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.85f),
    ) {
        when {
            actionBook != null -> ActionBookPage(
                book = actionBook!!,
                onBack = { actionBook = null },
                // H4：对齐网页「查看详情」→ 打开书籍详情面板（复用书架 BookDetailSheet）
                onShowDetail = {
                    val id = actionBook!!.id
                    onDismiss()
                    navController.navigate("shelf?detailBookId=$id")
                },
                onMarkRead = {
                    viewModel.markRead(actionBook!!.id)
                    actionBook = null
                },
                onMarkUnread = {
                    viewModel.markUnread(actionBook!!.id)
                    actionBook = null
                },
                onRemoveFromContinue = {
                    viewModel.removeFromContinue(actionBook!!.id)
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

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除书籍") },
            text = { Text("确定从书架删除《${deleteTarget!!.title}》吗？本地正文文件、阅读进度、书签和笔记会一并移除，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(deleteTarget!!.id) {}
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
                    Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
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
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.01f)),
        contentAlignment = Alignment.TopEnd,
    ) {
        Card(
            modifier = Modifier
                .padding(top = 56.dp, end = 8.dp)
                .width(220.dp)
                .clickable(enabled = false) {},
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
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
            selected -> Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            hasChild -> Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
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
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SheetHandle()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回列表")
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
        ActionButton(icon = { Icon(Icons.Filled.MenuBook, contentDescription = null) }, label = "查看详情", onClick = onShowDetail)
        ActionButton(icon = { Icon(Icons.Default.Check, contentDescription = null) }, label = "标记为已读完", onClick = onMarkRead)
        ActionButton(icon = { Icon(Icons.Default.Close, contentDescription = null) }, label = "从继续阅读移除", onClick = onRemoveFromContinue)
        ActionButton(icon = { Icon(Icons.Default.Book, contentDescription = null) }, label = "标记为未读", onClick = onMarkUnread)
        ActionButton(
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !manageMode, onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                if (item.book.cover_data_url != null) {
                    AsyncImage(
                        model = item.book.cover_data_url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(item.book.title.take(2), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(item.book.author ?: "作者未知", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatBookProgress(item.progress), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (manageMode) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "从继续阅读移除", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = onAction) {
                    Icon(Icons.Default.MoreVert, contentDescription = "操作")
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
            Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("暂无可以继续阅读的书籍", style = MaterialTheme.typography.titleMedium)
        Text("开始阅读后，书籍会出现在这里", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, shape = CircleShape),
        )
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
        if (!isBookReadableOnDevice(book)) return@mapIndexedNotNull null
        if (!hasBookBeenRead(book, progress, sessions[book.id])) return@mapIndexedNotNull null
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

private fun isBookReadableOnDevice(book: BookEntity): Boolean {
    if (book.deleted_at != null) return false
    return when (book.content_status) {
        "failed", "missing", "downloading" -> false
        else -> book.size > 0 && (book.local_content_path != null || book.local_uri != null || book.content_hash != null)
    }
}

/** H2：对齐网页 getBookReadiness。未就绪返回中文提示，已就绪返回 null。 */
private fun bookNotReadyLabel(book: BookEntity): String? {
    if (book.deleted_at != null) return "正文未在本机"
    return when (book.content_status) {
        "failed" -> "正文保存失败"
        "missing" -> "正文未在本机"
        "downloading" -> "正文下载中"
        else -> {
            val readable = book.size > 0 &&
                (book.local_content_path != null || book.local_uri != null || book.content_hash != null)
            if (!readable) "需下载正文" else null
        }
    }
}

private fun hasBookBeenRead(
    book: BookEntity,
    progress: ReadingProgressEntity?,
    sessions: List<com.creationreadingassistant.data.local.entity.ReadingSessionEntity>?,
): Boolean {
    if ((progress?.progress_percent ?: 0f) > 0f) return true
    return sessions?.any { it.book_id == book.id } == true
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

private fun formatBookProgress(progress: Float): String {
    val normalized = progress.coerceIn(0f, 100f)
    return when {
        normalized <= 0.05f -> "未读"
        normalized >= 99.5f -> "已读完"
        normalized >= 10f -> "${normalized.toInt()}%"
        else -> "${"%.1f".format(normalized)}%"
    }
}

private fun epochDayOf(iso: String?): Long {
    if (iso.isNullOrBlank()) return -1
    return runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }.getOrElse { -1 }
}

/** 返回本周一（对齐网页版周起始）。 */
fun weekStartEpochDay(): Long {
    val today = LocalDate.now()
    val dayOfWeek = today.dayOfWeek.value // 1=Mon .. 7=Sun
    return today.toEpochDay() - (dayOfWeek - 1)
}
