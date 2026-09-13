package com.creationreadingassistant.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.feature.library.deletion.DeletionScope
import com.creationreadingassistant.feature.library.deletion.DeletionUndoBar
import com.creationreadingassistant.feature.library.deletion.DeletionUndoViewModel
import com.creationreadingassistant.feature.library.deletion.deletionConfirmAction
import com.creationreadingassistant.feature.library.deletion.deletionConfirmBody
import com.creationreadingassistant.feature.library.deletion.deletionConfirmTitle
import com.creationreadingassistant.feature.library.deletion.deletionUndoMessage
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.util.bookNotReadyLabel
import com.creationreadingassistant.ui.util.isBookDisplayable
import com.creationreadingassistant.ui.viewmodel.BookViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContinueSheet(
    navController: NavHostController,
    books: List<BookEntity>,
    progressById: Map<String, ReadingProgressEntity>,
    sessions: Map<String, List<ReadingSessionEntity>>,
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
