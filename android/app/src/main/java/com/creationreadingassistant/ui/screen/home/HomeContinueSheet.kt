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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.components.MutedCoverFallback
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.feature.library.deletion.DeletionScope
import com.creationreadingassistant.feature.library.deletion.DeletionUndoBar
import com.creationreadingassistant.feature.library.deletion.DeletionUndoViewModel
import com.creationreadingassistant.feature.library.deletion.deletionConfirmAction
import com.creationreadingassistant.feature.library.deletion.deletionConfirmBody
import com.creationreadingassistant.feature.library.deletion.deletionConfirmTitle
import com.creationreadingassistant.feature.library.deletion.deletionUndoMessage
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.theme.LocalComponentSpec
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
    deletions: DeletionUndoViewModel = hiltViewModel(),
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
    val context = LocalContext.current
    val deletionUndoOffers by deletions.offers.collectAsStateWithLifecycle()
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

    val hiddenBooks = remember(books, removedIds) {
        if (removedIds.isEmpty()) emptyList()
        else books.filter { it.id in removedIds.keys && isBookDisplayable(it) }
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
                hiddenBooks = hiddenBooks,
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
                onRestore = { viewModel.clearContinueRemoval(it.id) },
                onDismissMenu = { menuView = MenuView.NONE },
            )
        }
        SnackbarHost(snackbarHostState)
        // ModalBottomSheet 在独立 Dialog 窗口中渲染；全局宿主位于其下方，
        // 因此这里复用同一个凭证源提供可见的撤销入口，关掉面板后由全局宿主接管。
        DeletionUndoBar(
            offer = deletionUndoOffers.firstOrNull(),
            onUndo = { id ->
                deletions.undo(id) { outcome ->
                    scope.launch { snackbarHostState.showSnackbar(deletionUndoMessage(context, outcome)) }
                }
            },
            onDismiss = deletions::dismiss,
            onExpired = deletions::prune,
        )
    }

    val pendingDelete = deleteTarget
    if (pendingDelete != null) {
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(deletionConfirmTitle(context, DeletionScope.DELETE_BOOK))
            },
            text = {
                Text(
                    deletionConfirmBody(
                        context = context,
                        scope = DeletionScope.DELETE_BOOK,
                        bookCount = 1,
                        undoSeconds = deletions.undoWindowSeconds,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(pendingDelete.id) {}
                        deleteTarget = null
                    },
                ) {
                    Text(
                        deletionConfirmAction(context, DeletionScope.DELETE_BOOK),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ListPage(
    items: List<ContinueItem>,
    hiddenBooks: List<BookEntity>,
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
    onRestore: (BookEntity) -> Unit,
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
                Text(
                    text = "继续阅读",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onMenuToggle) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多选项")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty() && hiddenBooks.isEmpty()) {
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

                    // 已隐藏书籍列表微胶囊卡片与恢复按钮
                    if (hiddenBooks.isNotEmpty()) {
                        item(key = "hidden-books-header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "已隐藏书籍 (${hiddenBooks.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(hiddenBooks, key = { "hidden-${it.id}" }) { hiddenBook ->
                            HiddenBookCapsuleCard(
                                book = hiddenBook,
                                onRestore = { onRestore(hiddenBook) },
                            )
                        }
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
        ActionButton(icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) }, label = "查看详情", onClick = onShowDetail)
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
    val spec = LocalComponentSpec.current
    Surface(
        shape = RoundedCornerShape(spec.hintRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ContinueListItem(
    item: ContinueItem,
    manageMode: Boolean,
    onClick: () -> Unit,
    onAction: () -> Unit,
    onRemove: () -> Unit,
) {
    val spec = LocalComponentSpec.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = spec.listItemShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
        onClick = onClick,
        enabled = !manageMode,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BookCover(
                book = item.book,
                modifier = Modifier
                    .size(52.dp, 72.dp)
                    .clip(RoundedCornerShape(spec.pedestalRadius))
                    .border(
                        width = spec.hairlineBorderWidth,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                        shape = RoundedCornerShape(spec.pedestalRadius),
                    ),
                shape = RoundedCornerShape(spec.pedestalRadius),
                showSheen = true,
                percent = null,
                fallback = {
                    MutedCoverFallback(book = item.book, maxTitleChars = 6, showFormat = false)
                },
                overlay = {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.10f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.book.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val author = item.book.author
                if (!author.isNullOrBlank()) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = spec.pillShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ) {
                    Text(
                        text = formatBookProgressForCard(item.progress),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
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
private fun HiddenBookCapsuleCard(
    book: BookEntity,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalComponentSpec.current
    Surface(
        shape = RoundedCornerShape(spec.hintRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .size(38.dp, 52.dp)
                    .clip(RoundedCornerShape(spec.pedestalRadius))
                    .border(
                        width = spec.hairlineBorderWidth,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                        shape = RoundedCornerShape(spec.pedestalRadius),
                    ),
                shape = RoundedCornerShape(spec.pedestalRadius),
                showSheen = false,
                percent = null,
                fallback = {
                    Text(
                        text = book.title.take(1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "已从继续阅读隐藏",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = spec.pillShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                onClick = onRestore,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "恢复",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
