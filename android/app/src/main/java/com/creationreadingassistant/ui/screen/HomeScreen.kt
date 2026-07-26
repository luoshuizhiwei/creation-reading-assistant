package com.creationreadingassistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.creationreadingassistant.R
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.viewmodel.BookViewModel
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel
import com.creationreadingassistant.ui.viewmodel.StatsViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * 首页（对齐 mobile/ HomePage）：阅读概览双卡 + 继续阅读横滑 + 统计网格
 * + 最近灵感 + 已阅读完成横滑。数据取自现有 ViewModel，技术栈保持原生。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    bookViewModel: BookViewModel = hiltViewModel(),
    statsViewModel: StatsViewModel = hiltViewModel(),
    inspirationViewModel: InspirationViewModel = hiltViewModel(),
) {
    val books by bookViewModel.books.collectAsStateWithLifecycle()
    val progressById by bookViewModel.progressById.collectAsStateWithLifecycle()
    val sessions by bookViewModel.sessions.collectAsStateWithLifecycle()
    val removedContinueIds by bookViewModel.removedContinueIds.collectAsStateWithLifecycle()
    val stats by statsViewModel.stats.collectAsStateWithLifecycle()
    val inspirations by inspirationViewModel.items.collectAsStateWithLifecycle()

    val weekStart = weekStartEpochDay()
    val nowDay = LocalDate.now().toEpochDay()

    val totalReadBooksCount = books.count { isBookDisplayable(it) && hasBookBeenRead(it, progressById[it.id], sessions[it.id]) }
    val thisWeekNew = books.count { epochDayOf(it.imported_at) in weekStart..nowDay }
    val readingCount = books.count {
        val p = progressById[it.id]
        isBookDisplayable(it) && p?.completion_state == "reading"
    }
    val completedBooks = books
        .filter {
            val p = progressById[it.id]
            isBookDisplayable(it) && (p?.completion_state == "finished" || (p?.progress_percent ?: 0f) >= 99.5f)
        }
        // 对齐网页：按完成时间（completed_at）倒序，未写的排末尾；回退 updated_at / 书籍 updated_at
        .sortedWith { a, b ->
            val ka = runCatching {
                val pa = progressById[a.id]
                pa?.completed_at ?: Instant.parse(pa?.updated_at ?: a.updated_at).toEpochMilli()
            }.getOrNull()
            val kb = runCatching {
                val pb = progressById[b.id]
                pb?.completed_at ?: Instant.parse(pb?.updated_at ?: b.updated_at).toEpochMilli()
            }.getOrNull()
            when {
                ka == null && kb == null -> 0
                ka == null -> 1 // 无完成时间排末尾
                kb == null -> -1
                else -> kb.compareTo(ka) // 倒序
            }
        }
        .take(8)

    val continueBooks = buildContinueBooks(books, progressById, sessions, removedContinueIds)

    val recentInspirations = inspirations
        .sortedByDescending { it.updated_at }
        .take(5)
    val totalMs = stats?.totalDurationMs ?: 0L
    val todayMs = stats?.todayMs ?: 0L

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var continueSheetOpen by remember { mutableStateOf(false) }

    var homeEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { homeEntered = true }
    val homeAlpha by animateFloatAsState(if (homeEntered) 1f else 0f, label = "homeEnterAlpha")

    // H2：打开阅读器前做 readiness 校验，未就绪用 Snackbar 提示（对齐网页 getBookReadiness）
    fun openBook(book: BookEntity) {
        val label = bookNotReadyLabel(book)
        if (label != null) {
            scope.launch {
                snackbarHostState.showSnackbar("《${book.title}》${label}，暂时无法打开。请检查文件状态或重新导入/下载正文。")
            }
            return
        }
        navController.navigate("reader/${book.id}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = { navController.navigate("search") }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.home_open_search))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(homeAlpha)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 阅读概览双卡
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Book,
                    value = totalReadBooksCount.toString(),
                    label = stringResource(R.string.home_books),
                    unit = " 本",
                    onClick = { navController.navigate("shelf") },
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Schedule,
                    value = formatDuration(totalMs),
                    label = stringResource(R.string.home_duration),
                    onClick = { navController.navigate("stats") },
                )
            }

            // 继续阅读
            SectionHeader(
                title = stringResource(R.string.home_continue),
                actionIcon = { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "管理继续阅读") },
                onAction = { continueSheetOpen = true },
            )
            if (continueBooks.isEmpty()) {
                EmptyHint(text = "书架还空着，先导入一本 TXT、Markdown 或 EPUB。") { navController.navigate("shelf") }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(continueBooks, key = { it.id }) { book ->
                        ContinueCard(book = book, progress = progressById[book.id]) {
                            openBook(book)
                        }
                    }
                }
            }

            // 阅读统计网格（本周新增 / 在读 / 已读完 / 今日阅读）
            Text("阅读统计", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GridStat(Modifier.weight(1f), stringResource(R.string.home_this_week), thisWeekNew.toString())
                GridStat(Modifier.weight(1f), stringResource(R.string.home_reading), readingCount.toString())
                GridStat(Modifier.weight(1f), stringResource(R.string.home_finished), completedBooks.size.toString())
                GridStat(Modifier.weight(1f), stringResource(R.string.home_today), formatCompactDuration(todayMs))
            }

            // 最近灵感
            SectionHeader(
                title = stringResource(R.string.home_recent_inspiration),
                onSeeAll = { navController.navigate("inspiration") },
            )
            if (recentInspirations.isEmpty()) {
                EmptyHint(text = "还没有灵感，阅读时选中文字即可保存为灵感。") {}
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentInspirations.forEach { insp ->
                        // H3：对齐网页，点击打开该条灵感自己的详情面板（按 id），而非灵感列表页
                        InspirationMiniCard(insp = insp) { navController.navigate("inspiration?inspId=${insp.id}") }
                    }
                }
            }

            // 已阅读完成
            SectionHeader(
                title = stringResource(R.string.home_completed),
                onSeeAll = { navController.navigate("shelf") },
            )
            if (completedBooks.isEmpty()) {
                EmptyHint(text = "还没有读完的书，继续阅读吧。") {}
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(completedBooks, key = { it.id }) { book ->
                        CompletedCard(book = book, progress = progressById[book.id]) { openBook(book) }
                    }
                }
            }
        }
    }

    if (continueSheetOpen) {
        HomeContinueSheet(
            navController = navController,
            books = books,
            progressById = progressById,
            sessions = sessions,
            removedIds = removedContinueIds,
            viewModel = bookViewModel,
            onDismiss = { continueSheetOpen = false },
        )
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    unit: String = "",
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (unit.isNotBlank()) {
                        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    actionIcon: @Composable (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 区块标题走宋体 headline：此前用 titleMedium(16sp) 只比正文大 2sp，
        // 层级几乎不存在，整屏文字看起来一样大。
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.weight(1f))
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text(stringResource(R.string.home_see_all) + " ›")
            }
        }
        if (actionIcon != null && onAction != null) {
            IconButton(onClick = onAction) {
                actionIcon()
            }
        }
    }
}

@Composable
private fun GridStat(modifier: Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp, 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContinueCard(
    book: BookEntity,
    progress: ReadingProgressEntity?,
    onClick: () -> Unit,
) {
    val pct = progress?.progress_percent ?: 0f
    Card(
        onClick = onClick,
        modifier = Modifier.width(180.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Row(
            Modifier.height(100.dp).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp, 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (book.cover_data_url != null) {
                    AsyncImage(
                        model = book.cover_data_url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(book.title.take(2), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                ) {
                    Text(
                        book.format.uppercase(),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
                // 封面微高光：左上→右下极淡白色斜向光泽，强化实体书质感
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.White.copy(alpha = 0.16f),
                                    0.55f to Color.White.copy(alpha = 0.03f),
                                    1.0f to Color.White.copy(alpha = 0.0f),
                                ),
                            ),
                        ),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    book.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!book.author.isNullOrBlank()) {
                    Text(
                        book.author!!,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    formatBookProgressForCard(pct),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun CompletedCard(
    book: BookEntity,
    progress: ReadingProgressEntity?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(116.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(84.dp, 112.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (book.cover_data_url != null) {
                AsyncImage(
                    model = book.cover_data_url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(book.title.take(2), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            ) {
                Text(
                    book.format.uppercase(),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = 0.16f),
                                0.55f to Color.White.copy(alpha = 0.03f),
                                1.0f to Color.White.copy(alpha = 0.0f),
                            ),
                        ),
                    ),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                book.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!book.author.isNullOrBlank()) {
                Text(
                    book.author!!,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("已读完", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InspirationMiniCard(insp: InspirationEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                insp.title.ifBlank { "无标题灵感" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                insp.body.takeIf { it.isNotBlank() } ?: insp.payload?.takeIf { it.length > 30 }?.take(60) ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String, onClick: () -> Unit = {}) {
    // 空状态用虚线框而不是实心块：虚线本身就表示「这个位置在等内容」，
    // 实心填充反而像一张有内容的卡片，读者要看完文字才知道是空的。
    val outline = MaterialTheme.colorScheme.outlineVariant
    val radius = 10.dp
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    ),
                )
            },
        shape = RoundedCornerShape(radius),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60000).toInt()
    if (totalMin <= 0) return "0 分钟"
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h} 小时 ${m} 分" else "${m} 分"
}

private fun formatCompactDuration(ms: Long): String {
    if (ms <= 0) return "0 分钟"
    if (ms < 60_000) return "${Math.round(ms / 1000.0)} 秒"
    val totalMinutes = Math.round(ms / 60_000.0).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m} 分钟"
}

private fun epochDayOf(iso: String?): Long {
    if (iso.isNullOrBlank()) return -1
    return runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }.getOrElse { -1 }
}

private fun formatBookProgressForCard(progress: Float): String {
    val normalized = progress.coerceIn(0f, 100f)
    return when {
        normalized <= 0.05f -> "未读"
        normalized >= 99.5f -> "已读完"
        normalized >= 10f -> "${normalized.toInt()}%"
        else -> "${"%.1f".format(normalized)}%"
    }
}

private fun isBookDisplayable(book: BookEntity): Boolean {
    if (book.deleted_at != null) return false
    return when (book.content_status) {
        "failed", "missing", "downloading" -> false
        else -> book.size > 0 && (book.local_content_path != null || book.local_uri != null || book.content_hash != null)
    }
}

/**
 * H2：对齐网页 getBookReadiness。返回未就绪时的中文提示文案；若已就绪（可离线打开）则返回 null。
 */
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

private fun buildContinueBooks(
    books: List<BookEntity>,
    progressById: Map<String, ReadingProgressEntity>,
    sessions: Map<String, List<com.creationreadingassistant.data.local.entity.ReadingSessionEntity>>,
    removedIds: Map<String, String>,
): List<BookEntity> {
    val now = System.currentTimeMillis()
    val msPerDay = 24 * 60 * 60 * 1000L
    val recencyDecayMs = 7 * msPerDay
    val monthAgo = now - 30 * msPerDay

    return books.mapNotNull { book ->
        val progress = progressById[book.id]
        val pct = progress?.progress_percent ?: 0f
        if (!isBookDisplayable(book)) return@mapNotNull null
        if (!hasBookBeenRead(book, progress, sessions[book.id])) return@mapNotNull null
        if (pct >= 99.5f) return@mapNotNull null

        val removedAt = removedIds[book.id]
        if (removedAt != null) {
            val lastReadAt = progress?.last_read_at
                ?: sessions[book.id]?.mapNotNull { it.ended_at ?: it.started_at }?.maxOrNull()
            if (lastReadAt == null || lastReadAt <= removedAt) return@mapNotNull null
        }

        val lastReadAt = progress?.last_read_at
            ?: sessions[book.id]?.mapNotNull { it.ended_at ?: it.started_at }?.maxOrNull()
        val lastReadTime = lastReadAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        val recencyScore = kotlin.math.exp((lastReadTime - now).toDouble() / recencyDecayMs)
        val recentSessions = sessions[book.id]?.count { session ->
            val t = session.created_at?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
            t > monthAgo
        } ?: 0
        val frequencyScore = (recentSessions.coerceAtMost(10)) / 10.0
        val score = recencyScore * 0.6 + frequencyScore * 0.4
        book to score
    }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(8)
}
