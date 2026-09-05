package com.creationreadingassistant.ui.screen.homearchive

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.shelf.BookActionSheet
import com.creationreadingassistant.ui.screen.shelf.bookNotReadyLabel
import com.creationreadingassistant.ui.viewmodel.BookOperationsViewModel
import com.creationreadingassistant.ui.viewmodel.CompletedArchiveItem
import com.creationreadingassistant.ui.viewmodel.HomeArchiveViewModel
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json

private enum class InspirationArchiveSort { UPDATED, CREATED, TITLE }
private enum class CompletedArchiveSort { COMPLETED, TITLE }

/**
 * 完读书籍封面微书脊立体阴影画笔（顶级常量，零 GC 分配）。
 */
private val CompletedCoverSpineShadowBrush = Brush.horizontalGradient(
    listOf(
        Color.Black.copy(alpha = 0.25f),
        Color.Transparent,
    )
)

/**
 * 完读统计微岛背景渐变画笔（顶级常量，零 GC 分配）。
 */
private val CompletedStatsBackgroundBrush = Brush.horizontalGradient(
    listOf(
        Color(0xFF10B981).copy(alpha = 0.08f),
        Color(0xFF10B981).copy(alpha = 0.02f),
        Color.Transparent,
    )
)

/**
 * 完读统计微岛图标底座渐变画笔（顶级常量，零 GC 分配）。
 */
private val CompletedStatsIconHaloBrush = Brush.linearGradient(
    listOf(
        Color(0xFF10B981).copy(alpha = 0.22f),
        Color(0xFF059669).copy(alpha = 0.12f),
    )
)

/**
 * 摘录原句卡片左侧墨线竖标渐变画笔（顶级常量，零 GC 分配）。
 */
private val ExcerptAccentVerticalBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF059669),
        Color(0xFF10B981).copy(alpha = 0.35f),
    )
)

private val payloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun parseInspirationPayload(entity: InspirationEntity?): InspirationPayloadData {
    if (entity?.payload.isNullOrBlank()) return InspirationPayloadData()
    return runCatching { payloadJson.decodeFromString<InspirationPayloadData>(entity!!.payload!!) }
        .getOrDefault(InspirationPayloadData())
}

private fun formatInspirationTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "刚刚"
    val instant = runCatching { Instant.parse(isoString) }.getOrNull() ?: return "刚刚"
    val zone = ZoneId.systemDefault()
    val localDateTime = instant.atZone(zone).toLocalDateTime()
    val now = java.time.ZonedDateTime.now(zone).toLocalDateTime()
    val days = ChronoUnit.DAYS.between(localDateTime.toLocalDate(), now.toLocalDate())
    return when {
        days == 0L -> {
            val hours = ChronoUnit.HOURS.between(localDateTime, now)
            if (hours <= 0) "刚刚" else "${hours}小时前"
        }
        days in 1..30 -> "${days}天前"
        localDateTime.year == now.year -> "%d月%d日".format(localDateTime.monthValue, localDateTime.dayOfMonth)
        else -> "%d年%d月%d日".format(localDateTime.year, localDateTime.monthValue, localDateTime.dayOfMonth)
    }
}

private fun formatDetailTime(value: String?): String {
    if (value.isNullOrBlank()) return "刚刚"
    val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return "刚刚"
    val zdt = instant.atZone(ZoneId.systemDefault())
    return zdt.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"))
}

private fun formatReadingTime(value: Long): String {
    val minutes = (value.coerceAtLeast(0L) / 60_000.0).roundToInt()
    if (minutes <= 0) return "不足 1 分钟"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours}小时${rest}分钟" else "${minutes}分钟"
}

private fun completedDateLabel(item: CompletedArchiveItem): String {
    val timestamp = item.progress.completed_at ?: return "已读完"
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${date.year}年${date.monthValue}月${date.dayOfMonth}日读完"
}

private fun getTypeLabel(type: String?): String = when (type) {
    "note" -> "想法"
    "excerpt" -> "金句摘录"
    "setting" -> "世界设定"
    "plot" -> "情节桥段"
    else -> "灵感"
}

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
            query.isBlank() || "${it.title} ${it.body}".contains(query.trim(), ignoreCase = true)
        }.let { list ->
            when (sort) {
                InspirationArchiveSort.UPDATED -> list.sortedByDescending { it.updated_at }
                InspirationArchiveSort.CREATED -> list.sortedByDescending { it.created_at }
                InspirationArchiveSort.TITLE -> list.sortedBy { it.title }
            }
        }
    }
    InspirationArchiveScreen(
        inspirations = visible,
        totalCount = state.inspirations.size,
        query = query,
        sort = sort,
        onBack = { navController.popBackStack() },
        onQueryChange = { query = it },
        onSelectSort = { sort = it },
        onOpen = { navController.navigate("home/inspiration/${it.id}") },
        onOpenBook = { bookId -> navController.navigate("reader/$bookId") },
    )
}

@Composable
fun HomeInspirationDetailRoute(
    navController: NavHostController,
    inspirationId: String,
    viewModel: HomeArchiveViewModel = hiltViewModel(),
    inspirationViewModel: InspirationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val inspiration = state.inspirations.firstOrNull { it.id == inspirationId }
    val books by inspirationViewModel.books.collectAsStateWithLifecycle()

    InspirationDetailScreen(
        inspiration = inspiration,
        allBooks = books,
        onBack = { navController.popBackStack() },
        onOpenBook = { bookId -> navController.navigate("reader/$bookId") },
        onDelete = { id ->
            inspirationViewModel.deleteInspiration(id)
            navController.popBackStack()
        },
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
            title = { Text("删除书籍？") },
            text = { Text("本地正文、阅读进度、笔记和灵感关联数据会一并移除，删除后可随时从书架恢复。") },
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
private fun ArchiveSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                placeholderText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "清空",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    )
}

@Composable
private fun InspirationArchiveScreen(
    inspirations: List<InspirationEntity>,
    totalCount: Int,
    query: String,
    sort: InspirationArchiveSort,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectSort: (InspirationArchiveSort) -> Unit,
    onOpen: (InspirationEntity) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    AppScreenScaffold(
        title = "全量灵感",
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
        modifier = Modifier.testTag("home-inspirations-screen"),
    ) { viewportPadding ->
        PageLazyColumn(
            scaffoldPadding = viewportPadding,
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
        ) {
            // 1. 顶部搜索微岛
            item(key = "search", contentType = "search") {
                ArchiveSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholderText = "搜索 $totalCount 条灵感记录...",
                )
            }

            // 2. 排序微胶囊导轨
            item(key = "sort-rail", contentType = "sort-rail") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectablePill(
                        text = "更新时间",
                        selected = sort == InspirationArchiveSort.UPDATED,
                        onClick = { onSelectSort(InspirationArchiveSort.UPDATED) },
                    )
                    SelectablePill(
                        text = "创建时间",
                        selected = sort == InspirationArchiveSort.CREATED,
                        onClick = { onSelectSort(InspirationArchiveSort.CREATED) },
                    )
                    SelectablePill(
                        text = "标题",
                        selected = sort == InspirationArchiveSort.TITLE,
                        onClick = { onSelectSort(InspirationArchiveSort.TITLE) },
                    )
                }
            }

            // 3. 统计状态行
            item(key = "summary", contentType = "summary") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (query.isBlank()) "共 $totalCount 条灵感" else "筛选出 ${inspirations.size} 条灵感",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 4. 列表项 / 空状态
            if (inspirations.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    FullEmptyState(
                        icon = { LineArtBook(Modifier.size(52.dp)) },
                        title = if (query.isBlank()) "还没有灵感" else "没有找到灵感",
                        body = if (query.isBlank()) "阅读时保存的灵感会按时间顺序整理在这里。" else "换个搜索词试试吧。",
                        contentPadding = 28.dp,
                    )
                }
            } else {
                items(
                    items = inspirations,
                    key = { it.id },
                    contentType = { "inspiration-card" },
                ) { inspiration ->
                    InspirationArchiveCard(
                        inspiration = inspiration,
                        onOpenDetail = { onOpen(inspiration) },
                        onOpenBook = onOpenBook,
                    )
                }
            }

            item(key = "bottom-space", contentType = "spacer") { Spacer(Modifier.height(layout.pageVertical)) }
        }
    }
}

@Composable
private fun InspirationArchiveCard(
    inspiration: InspirationEntity,
    onOpenDetail: () -> Unit,
    onOpenBook: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val payloadData = remember(inspiration) { parseInspirationPayload(inspiration) }
    val source = payloadData.source
    val bookId = inspiration.source_book_id ?: source?.bookId
    val bookTitle = source?.bookTitle
    val chapter = source?.chapterTitle ?: source?.locationLabel

    val accentColor = when (inspiration.type) {
        "setting" -> Color(0xFF7C3AED)
        "plot" -> Color(0xFFD97706)
        "excerpt" -> Color(0xFF059669)
        else -> MaterialTheme.colorScheme.primary
    }

    val accentGradientBrush = remember(accentColor) {
        Brush.verticalGradient(
            listOf(
                accentColor,
                accentColor.copy(alpha = 0.35f),
            )
        )
    }

    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("inspiration-archive-${inspiration.id}"),
        onClick = onOpenDetail,
        contentPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // 左侧 3dp 典雅墨线竖标（Primary 渐变或柔和主色）
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .background(accentGradientBrush),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 顶部小行：类型微胶囊 + 格式化时间戳
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(accentColor),
                            )
                            Text(
                                text = getTypeLabel(inspiration.type),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                            )
                        }
                    }
                    Text(
                        text = formatInspirationTime(inspiration.updated_at),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }

                // 灵感标题
                Text(
                    text = inspiration.title.ifBlank { "未命名灵感" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // 正文摘录排版典雅
                val excerpt = source?.excerpt
                val displayText = inspiration.body.ifBlank { excerpt ?: "" }
                if (displayText.isNotBlank()) {
                    Text(
                        text = if (inspiration.type == "excerpt" || !excerpt.isNullOrBlank()) "“$displayText”" else displayText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (inspiration.type == "excerpt" || !excerpt.isNullOrBlank()) FontFamily.Serif else FontFamily.Default,
                            fontStyle = if (inspiration.type == "excerpt" || !excerpt.isNullOrBlank()) FontStyle.Italic else FontStyle.Normal,
                            lineHeight = 22.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 关联书籍微胶囊（带书卷小图标）与章节位置微徽章
                if (!bookTitle.isNullOrBlank() || !chapter.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!bookTitle.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.MenuBook,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "《$bookTitle》",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        if (!chapter.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = chapter,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // 操作行（打开书籍、查看详情）微胶囊化
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (!bookId.isNullOrBlank() && onOpenBook != null) {
                        Surface(
                            onClick = { onOpenBook(bookId) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "打开书籍",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Surface(
                        onClick = onOpenDetail,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "查看详情",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedStatsIsland(
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
private fun CompletedArchiveScreen(
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
private fun CompletedBookCard(
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

@Composable
private fun InspirationDetailScreen(
    inspiration: InspirationEntity?,
    allBooks: List<BookEntity>,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    val payloadData = remember(inspiration) { parseInspirationPayload(inspiration) }
    val source = payloadData.source
    val bookId = inspiration?.source_book_id ?: source?.bookId
    val excerptText = source?.excerpt?.takeIf { it.isNotBlank() }
        ?: (if (inspiration?.type == "excerpt") inspiration.body.takeIf { it.isNotBlank() } else null)
    val thoughtText = if (inspiration?.type == "excerpt" && source?.excerpt.isNullOrBlank()) "" else (inspiration?.body ?: "")

    BackHandler { onBack() }

    AppScreenScaffold(
        title = "灵感详情",
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
        modifier = Modifier.testTag("home-inspiration-detail-screen"),
    ) { viewportPadding ->
        PageLazyColumn(
            scaffoldPadding = viewportPadding,
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
        ) {
            if (inspiration == null) {
                item(key = "empty", contentType = "empty") {
                    FullEmptyState(
                        icon = { LineArtBook(Modifier.size(52.dp)) },
                        title = "灵感不存在",
                        body = "它可能已被删除或尚未加载完成。",
                        contentPadding = 28.dp,
                    )
                }
            } else {
                // 1. 摘录原句卡片（Excerpt Card）
                if (!excerptText.isNullOrBlank()) {
                    item(key = "excerpt-card", contentType = "excerpt-card") {
                        ExcerptCard(
                            excerptText = excerptText,
                            chapterTitle = source?.chapterTitle,
                        )
                    }
                }

                // 2. 笔记想法卡片（Thought Card）
                item(key = "thought-card", contentType = "thought-card") {
                    ThoughtCard(
                        inspiration = inspiration,
                        thoughtText = thoughtText,
                        tags = payloadData.tags,
                    )
                }

                // 3. 关联书籍微岛卡片（Book Source Island）
                if (source != null || !bookId.isNullOrBlank()) {
                    item(key = "book-source-island", contentType = "book-source-island") {
                        BookSourceIsland(
                            bookId = bookId,
                            source = source,
                            allBooks = allBooks,
                            onOpenBook = onOpenBook,
                        )
                    }
                }

                // 4. 底部操作栏（微胶囊化复制、分享与删除）
                item(key = "actions", contentType = "actions") {
                    val textToCopy = listOfNotNull(
                        inspiration.title.takeIf { it.isNotBlank() },
                        excerptText?.let { "“$it”" },
                        thoughtText.takeIf { it.isNotBlank() },
                    ).joinToString("\n\n")

                    val textToShare = listOfNotNull(
                        inspiration.title.takeIf { it.isNotBlank() },
                        excerptText?.let { "“$it”" },
                        thoughtText.takeIf { it.isNotBlank() },
                        source?.bookTitle?.let { "—— 摘自《$it》" },
                    ).joinToString("\n\n")

                    InspirationDetailActionRow(
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(textToCopy))
                            Toast.makeText(context, "已复制灵感内容到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, textToShare)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(intent, "分享灵感"))
                        },
                        onDeleteClick = { showDeleteDialog = true },
                    )
                }

                item(key = "bottom-space", contentType = "spacer") {
                    Spacer(Modifier.height(layout.pageVertical))
                }
            }
        }
    }

    if (showDeleteDialog && inspiration != null) {
        GlassAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除此灵感？") },
            text = { Text("删除后将从灵感库中彻底移除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(inspiration.id)
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ExcerptCard(
    excerptText: String,
    chapterTitle: String?,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // 左侧 3dp 墨线竖标
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .background(ExcerptAccentVerticalBrush),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF059669).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.FormatQuote,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = "原句摘录",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                    )
                    if (!chapterTitle.isNullOrBlank()) {
                        Text(
                            text = "· $chapterTitle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = "“$excerptText”",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 26.sp,
                        letterSpacing = 0.3.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThoughtCard(
    inspiration: InspirationEntity,
    thoughtText: String,
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    val accentColor = when (inspiration.type) {
        "setting" -> Color(0xFF7C3AED)
        "plot" -> Color(0xFFD97706)
        "excerpt" -> Color(0xFF059669)
        else -> MaterialTheme.colorScheme.primary
    }

    SectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 16.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部信息：类型、状态与更新时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(accentColor),
                            )
                            Text(
                                text = getTypeLabel(inspiration.type),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                            )
                        }
                    }
                    if (inspiration.status.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = when (inspiration.status) {
                                    "inbox" -> "收件箱"
                                    "archived" -> "已归档"
                                    "draft" -> "草稿"
                                    else -> inspiration.status
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "更新于 ${formatDetailTime(inspiration.updated_at)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }

            // 标题
            if (inspiration.title.isNotBlank()) {
                Text(
                    text = inspiration.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 想法内容（长文优雅换行与字间距）
            if (thoughtText.isNotBlank()) {
                Text(
                    text = thoughtText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 26.sp,
                        letterSpacing = 0.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 标签微胶囊
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tags.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookSourceIsland(
    bookId: String?,
    source: InspirationSourceInfo?,
    allBooks: List<BookEntity>,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val matchedBook = remember(allBooks, bookId, source) {
        val targetId = bookId ?: source?.bookId
        allBooks.firstOrNull { it.id == targetId }
    }
    val title = matchedBook?.title ?: source?.bookTitle ?: "未知书籍"
    val author = matchedBook?.author?.takeIf { it.isNotBlank() } ?: source?.bookAuthor ?: "未知作者"
    val format = matchedBook?.format ?: "EPUB"
    val chapter = source?.chapterTitle ?: source?.locationLabel
    val effectiveBookId = bookId ?: source?.bookId

    SectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 封面或底座
            if (matchedBook != null) {
                BookCover(
                    book = matchedBook,
                    modifier = Modifier
                        .size(46.dp, 64.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    percent = null,
                    fallback = {
                        Text(
                            matchedBook.title.take(3),
                            modifier = Modifier.align(Alignment.Center).padding(2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp, 64.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "来源书籍",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = format.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                if (!chapter.isNullOrBlank()) {
                    Text(
                        text = chapter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!effectiveBookId.isNullOrBlank()) {
                Surface(
                    onClick = { onOpenBook(effectiveBookId) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "打开书籍",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspirationDetailActionRow(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onCopy,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "复制正文",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Surface(
            onClick = onShare,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "分享灵感",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Surface(
            onClick = onDeleteClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除灵感",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
