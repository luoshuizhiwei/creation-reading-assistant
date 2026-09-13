package com.creationreadingassistant.ui.screen.homearchive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.library.deletion.DeletionScope
import com.creationreadingassistant.feature.library.deletion.DeletionUndoViewModel
import com.creationreadingassistant.feature.library.deletion.deletionConfirmAction
import com.creationreadingassistant.feature.library.deletion.deletionConfirmBody
import com.creationreadingassistant.feature.library.deletion.deletionConfirmTitle
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.shelf.BookActionSheet
import com.creationreadingassistant.ui.screen.shelf.bookNotReadyLabel
import com.creationreadingassistant.ui.viewmodel.BookOperationsViewModel
import com.creationreadingassistant.ui.viewmodel.CompletedArchiveItem
import com.creationreadingassistant.ui.viewmodel.HomeArchiveViewModel

@Composable
fun HomeCompletedRoute(
    navController: NavHostController,
    archiveViewModel: HomeArchiveViewModel = hiltViewModel(),
    bookOps: BookOperationsViewModel = hiltViewModel(),
    deletions: DeletionUndoViewModel = hiltViewModel(),
) {
    val state by archiveViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
        totalReadingTimeMs = remember(state.completedBooks) {
            state.completedBooks.sumOf { it.progress.total_reading_time_ms }
        },
        query = query,
        sort = sort,
        onBack = { navController.popBackStack() },
        onQueryChange = { query = it },
        onSelectSort = { sort = it },
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
                TextButton(onClick = { deletePrompt = null; bookOps.deleteBook(deleting.id) }) {
                    Text(
                        deletionConfirmAction(context, DeletionScope.DELETE_BOOK),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { deletePrompt = null }) { Text("取消") } },
        )
    }
}

@Composable
internal fun CompletedStatsIsland(
    totalCompletedBooks: Int,
    totalReadingTimeMs: Long,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 16.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CompletedStatsBackgroundBrush),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 36dp 独立微彩圆角底座与渐变光环
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CompletedStatsIconHaloBrush)
                            .border(0.6.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF047857),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "完读里程碑",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "已完读 $totalCompletedBooks 本藏书",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 累计阅读用时微胶囊
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                    border = BorderStroke(0.6.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = Color(0xFFD97706),
                        )
                        Text(
                            text = formatReadingTime(totalReadingTimeMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD97706),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompletedArchiveScreen(
    items: List<CompletedArchiveItem>,
    totalCount: Int,
    totalReadingTimeMs: Long,
    query: String,
    sort: CompletedArchiveSort,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectSort: (CompletedArchiveSort) -> Unit,
    onOpen: (BookEntity) -> Unit,
    onManage: (BookEntity) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    AppScreenScaffold(
        title = "已读完成",
        navigationIcon = {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        modifier = Modifier.testTag("home-completed-screen"),
    ) { viewportPadding ->
        PageLazyColumn(
            scaffoldPadding = viewportPadding,
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
        ) {
            // 1. 顶部完读统计微岛（未搜索时常驻）
            if (query.isBlank() && totalCount > 0) {
                item(key = "stats-island", contentType = "stats-island") {
                    CompletedStatsIsland(
                        totalCompletedBooks = totalCount,
                        totalReadingTimeMs = totalReadingTimeMs,
                    )
                }
            }

            // 2. 搜索框微岛
            item(key = "search", contentType = "search") {
                ArchiveSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholderText = "搜索 $totalCount 本已完读书籍...",
                )
            }

            // 3. 排序选择微胶囊导轨
            item(key = "sort-rail", contentType = "sort-rail") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectablePill(
                        text = "完读时间",
                        selected = sort == CompletedArchiveSort.COMPLETED,
                        onClick = { onSelectSort(CompletedArchiveSort.COMPLETED) },
                    )
                    SelectablePill(
                        text = "书名",
                        selected = sort == CompletedArchiveSort.TITLE,
                        onClick = { onSelectSort(CompletedArchiveSort.TITLE) },
                    )
                }
            }

            // 4. 数量统计行
            item(key = "summary", contentType = "summary") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (query.isBlank()) "共 $totalCount 本完读书籍" else "筛选出 ${items.size} 本书籍",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 5. 列表项 / 空状态
            if (items.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    FullEmptyState(
                        icon = { LineArtBook(Modifier.size(52.dp)) },
                        title = if (query.isBlank()) "还没有已读完的书" else "没有找到匹配的书籍",
                        body = if (query.isBlank()) "读完书籍后会自动归档在此，见证你的阅读旅程。" else "换个书名或作者试试吧。",
                        contentPadding = 28.dp,
                    )
                }
            } else {
                items(
                    items = items,
                    key = { it.book.id },
                    contentType = { "completed-book-card" },
                ) { item ->
                    CompletedBookCard(
                        item = item,
                        onOpen = { onOpen(item.book) },
                        onManage = { onManage(item.book) },
                    )
                }
            }

            item(key = "bottom-space", contentType = "spacer") { Spacer(Modifier.height(layout.pageVertical)) }
        }
    }
}

@Composable
internal fun CompletedBookCard(
    item: CompletedArchiveItem,
    onOpen: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("completed-book-${item.book.id}"),
        onClick = onOpen,
        contentPadding = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 左侧书脊 3D 微阴影封面
            Box(
                modifier = Modifier
                    .shadow(elevation = 1.dp, shape = RoundedCornerShape(8.dp), clip = false)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                BookCover(
                    book = item.book,
                    modifier = Modifier.size(54.dp, 76.dp),
                    percent = null,
                    fallback = {
                        Text(
                            item.book.title.take(3),
                            modifier = Modifier.align(Alignment.Center).padding(4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(76.dp)
                        .background(CompletedCoverSpineShadowBrush),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = item.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.book.author?.ifBlank { null } ?: "未知作者",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = item.book.format.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 「已完读」翡翠绿微徽章
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = Color(0xFF047857),
                            )
                            Text(
                                text = "已完读",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF047857),
                            )
                        }
                    }
                    // 累计阅读用时暖琥珀微胶囊
                    if (item.progress.total_reading_time_ms > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                            border = BorderStroke(0.6.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = Color(0xFFD97706),
                                )
                                Text(
                                    text = formatReadingTime(item.progress.total_reading_time_ms),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFD97706),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = completedDateLabel(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            // 更多操作按钮微胶囊化
            Surface(
                onClick = onManage,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "管理《${item.book.title}》",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
