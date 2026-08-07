package com.creationreadingassistant.ui.screen.homearchive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.shelf.bookNotReadyLabel
import com.creationreadingassistant.ui.screen.shelf.BookActionSheet
import com.creationreadingassistant.ui.viewmodel.BookOperationsViewModel
import com.creationreadingassistant.ui.viewmodel.CompletedArchiveItem
import com.creationreadingassistant.ui.viewmodel.HomeArchiveViewModel
import java.time.Instant
import java.time.ZoneId

private enum class InspirationArchiveSort { UPDATED, CREATED, TITLE }
private enum class CompletedArchiveSort { COMPLETED, TITLE }

@Composable
fun HomeInspirationsRoute(
    navController: NavHostController,
    viewModel: HomeArchiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(InspirationArchiveSort.UPDATED) }
    val visible = remember(state.inspirations, query, sort) {
        state.inspirations.filter {
            query.isBlank() || "${it.title.orEmpty()} ${it.body}".contains(query.trim(), ignoreCase = true)
        }.let { list ->
            when (sort) {
                InspirationArchiveSort.UPDATED -> list.sortedByDescending { it.updated_at }
                InspirationArchiveSort.CREATED -> list.sortedByDescending { it.created_at }
                InspirationArchiveSort.TITLE -> list.sortedBy { it.title.orEmpty() }
            }
        }
    }
    InspirationArchiveScreen(
        inspirations = visible,
        totalCount = state.inspirations.size,
        query = query,
        sortLabel = when (sort) {
            InspirationArchiveSort.UPDATED -> "最近更新"
            InspirationArchiveSort.CREATED -> "创建时间"
            InspirationArchiveSort.TITLE -> "标题"
        },
        onBack = { navController.popBackStack() },
        onQueryChange = { query = it },
        onToggleSort = {
            sort = when (sort) {
                InspirationArchiveSort.UPDATED -> InspirationArchiveSort.CREATED
                InspirationArchiveSort.CREATED -> InspirationArchiveSort.TITLE
                InspirationArchiveSort.TITLE -> InspirationArchiveSort.UPDATED
            }
        },
        onOpen = { navController.navigate("home/inspiration/${it.id}") },
    )
}

@Composable
fun HomeInspirationDetailRoute(
    navController: NavHostController,
    inspirationId: String,
    viewModel: HomeArchiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InspirationDetailScreen(
        inspiration = state.inspirations.firstOrNull { it.id == inspirationId },
        onBack = { navController.popBackStack() },
    )
}

@Composable
fun HomeCompletedRoute(
    navController: NavHostController,
    archiveViewModel: HomeArchiveViewModel = hiltViewModel(),
    bookOps: BookOperationsViewModel = hiltViewModel(),
) {
    val state by archiveViewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(CompletedArchiveSort.COMPLETED) }
    var managedBook by remember { mutableStateOf<BookEntity?>(null) }
    var deletePrompt by remember { mutableStateOf<BookEntity?>(null) }
    var repairBookId by remember { mutableStateOf<String?>(null) }
    val repairLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val id = repairBookId
        if (uri != null && id != null) bookOps.reselectFile(id, uri) {}
        repairBookId = null
    }
    val visible = remember(state.completedBooks, query, sort) {
        state.completedBooks.filter {
            query.isBlank() || "${it.book.title} ${it.book.author.orEmpty()}".contains(query.trim(), ignoreCase = true)
        }.let { list ->
            when (sort) {
                CompletedArchiveSort.COMPLETED -> list.sortedByDescending { it.progress.completed_at ?: 0L }
                CompletedArchiveSort.TITLE -> list.sortedBy { it.book.title }
            }
        }
    }
    CompletedArchiveScreen(
        items = visible,
        totalCount = state.completedBooks.size,
        query = query,
        sortLabel = if (sort == CompletedArchiveSort.COMPLETED) "完成时间" else "书名",
        onBack = { navController.popBackStack() },
        onQueryChange = { query = it },
        onToggleSort = {
            sort = if (sort == CompletedArchiveSort.COMPLETED) CompletedArchiveSort.TITLE else CompletedArchiveSort.COMPLETED
        },
        onOpen = { book ->
            if (bookNotReadyLabel(book) == null) navController.navigate("reader/${book.id}") else managedBook = book
        },
        onManage = { managedBook = it },
    )

    val managed = managedBook
    if (managed != null) {
        val progressById = state.completedBooks.associate { it.book.id to it.progress }
        BookActionSheet(
            book = managed,
            progressById = progressById,
            onDismiss = { managedBook = null },
            onContinue = {
                managedBook = null
                if (bookNotReadyLabel(it) == null) navController.navigate("reader/${it.id}")
            },
            onDownload = { bookOps.downloadBookContent(it.id) {}; managedBook = null },
            onRepair = {
                managedBook = null
                repairBookId = it.id
                repairLauncher.launch(arrayOf("application/epub+zip", "text/plain", "text/markdown"))
            },
            onOpenDetail = { id -> managedBook = null; navController.navigate("shelf/detail/$id") },
            onDelete = { managedBook = null; deletePrompt = managed },
        )
    }
    val deleting = deletePrompt
    if (deleting != null) {
        GlassAlertDialog(
            onDismissRequest = { deletePrompt = null },
            title = { Text("删除书籍？") },
            text = { Text("本地正文、阅读进度、笔记和灵感关联数据会一并移除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { deletePrompt = null; bookOps.deleteBook(deleting.id) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletePrompt = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ArchiveScaffold(
    title: String,
    query: String,
    totalCount: Int,
    sortLabel: String,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val layout = LocalLayoutTokens.current
    AppScreenScaffold(
        title = title,
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
        },
        modifier = modifier,
    ) { viewportPadding ->
        PageLazyColumn(
            scaffoldPadding = viewportPadding,
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索 $totalCount 条记录") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
            }
            item(key = "summary") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("当前显示", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = onToggleSort) { Text("排序：$sortLabel") }
                }
            }
            content()
            item(key = "bottom") { Spacer(Modifier.height(layout.pageVertical)) }
        }
    }
}

@Composable
private fun InspirationArchiveScreen(
    inspirations: List<InspirationEntity>,
    totalCount: Int,
    query: String,
    sortLabel: String,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSort: () -> Unit,
    onOpen: (InspirationEntity) -> Unit,
) {
    ArchiveScaffold("最近灵感", query, totalCount, sortLabel, onBack, onQueryChange, onToggleSort) {
        if (inspirations.isEmpty()) {
            item("empty") {
                FullEmptyState(
                    icon = { Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(48.dp)) },
                    title = "没有找到灵感",
                    body = "阅读时保存的灵感会按最近更新顺序出现在这里。",
                    contentPadding = 28.dp,
                )
            }
        } else inspirations.forEach { inspiration ->
            item(key = inspiration.id) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(inspiration) },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(inspiration.title?.takeIf { it.isNotBlank() } ?: "未命名灵感", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(inspiration.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedArchiveScreen(
    items: List<CompletedArchiveItem>,
    totalCount: Int,
    query: String,
    sortLabel: String,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSort: () -> Unit,
    onOpen: (BookEntity) -> Unit,
    onManage: (BookEntity) -> Unit,
) {
    ArchiveScaffold("已读完成", query, totalCount, sortLabel, onBack, onQueryChange, onToggleSort) {
        if (items.isEmpty()) {
            item("empty") {
                FullEmptyState(icon = { LineArtBook(Modifier.size(52.dp)) }, title = "没有找到已读完的书", body = "完成阅读后会按完成时间整理在这里。", contentPadding = 28.dp)
            }
        } else items.forEach { item ->
            item(key = item.book.id) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(item.book) },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BookCover(
                            book = item.book,
                            modifier = Modifier.size(54.dp, 76.dp),
                            percent = null,
                            fallback = { Text(item.book.title.take(3), modifier = Modifier.align(Alignment.Center).padding(4.dp), style = MaterialTheme.typography.labelSmall) },
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(completedDateLabel(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onManage(item.book) }) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = "管理《${item.book.title}》")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspirationDetailScreen(inspiration: InspirationEntity?, onBack: () -> Unit) {
    AppScreenScaffold(
        title = "灵感详情",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") } },
    ) { viewportPadding ->
        PageLazyColumn(scaffoldPadding = viewportPadding) {
            item {
                if (inspiration == null) {
                    FullEmptyState(
                        icon = { Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(48.dp)) },
                        title = "灵感不存在",
                        body = "它可能已被删除或尚未加载完成。",
                        contentPadding = 28.dp,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(inspiration.title?.takeIf { it.isNotBlank() } ?: "未命名灵感", style = MaterialTheme.typography.headlineSmall)
                        Text(inspiration.body, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private fun completedDateLabel(item: CompletedArchiveItem): String {
    val timestamp = item.progress.completed_at ?: return "已读完"
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${date.year}年${date.monthValue}月${date.dayOfMonth}日读完"
}
