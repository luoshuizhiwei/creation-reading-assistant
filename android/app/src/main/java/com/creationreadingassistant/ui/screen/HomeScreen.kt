package com.creationreadingassistant.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.R
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.viewmodel.BookViewModel
import com.creationreadingassistant.ui.viewmodel.HomeViewModel
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.LinearProgressIndicator
import com.creationreadingassistant.ui.components.EmptyStateHint
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionHeader
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.ListSkeleton
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt
import com.creationreadingassistant.ui.util.bookNotReadyLabel
import com.creationreadingassistant.ui.util.hasBookBeenRead
import com.creationreadingassistant.ui.util.isBookDisplayable
import com.creationreadingassistant.ui.util.formatBookProgressForCard

/**
 * 首页（对齐 mobile/ HomePage）：阅读概览双卡 + 继续阅读横滑 + 统计网格
 * + 最近灵感 + 已阅读完成横滑。数据取自现有 ViewModel，技术栈保持原生。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    bookViewModel: BookViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val layout = LocalLayoutTokens.current
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(uiState.isReady) {
        if (uiState.isReady) {
            (activity as? MainActivity)?.onHomeContentReady()
        }
    }
    val books = uiState.books
    val progressById = uiState.progressById
    val sessions = uiState.sessionsByBook
    val removedContinueIds = uiState.removedContinueIds
    val thisWeekNew = uiState.thisWeekNew
    val readingCount = uiState.readingCount
    val completedBooks = uiState.completedBooks
    val continueBooks = uiState.continueBooks
    val recentInspirations = uiState.recentInspirations
    val todayMs = uiState.todayReadingMs

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var continueSheetOpen by remember { mutableStateOf(false) }

    // A 档打磨：系统「减少动态效果」时所有入场动效退化为瞬时
    val reducedMotion = rememberReducedMotion()
    // 首屏加载占位：数据未就绪时显示微光骨架，避免空白一闪；数据到达或超时后回到真实内容 / 空态
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(350); firstLoad = false }
    LaunchedEffect(books) { if (books.isNotEmpty()) firstLoad = false }
    val showSkeleton = firstLoad && books.isEmpty()

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = layout.pageHorizontal,
                vertical = layout.pageVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
        ) {
            // 首屏加载占位：数据未就绪时显示微光骨架，避免空白一闪（reduced-motion 时退化为静态）
            if (showSkeleton) {
                item(key = "skeleton") {
                    ListSkeleton(modifier = Modifier.fillMaxWidth(), count = 5, reducedMotion = reducedMotion)
                }
            } else {
            // 首要任务：继续阅读。概览数据并入后面的单一指标组，避免首屏重复。
            item(key = "continue-header") {
            SectionHeader(
                title = stringResource(R.string.home_continue),
                modifier = Modifier.animateEnter(60, reducedMotion),
                action = {
                    IconButton(onClick = { continueSheetOpen = true }) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "管理继续阅读")
                    }
                },
            )
            }
            item(key = "continue-content") {
            if (continueBooks.isEmpty()) {
                EmptyHint(
                    text = "书架还空着，先导入一本 TXT、Markdown 或 EPUB。",
                    onClick = { navController.navigate("shelf") },
                    modifier = Modifier.animateEnter(60, reducedMotion),
                )
            } else {
                LazyRow(
                    modifier = Modifier.animateEnter(60, reducedMotion),
                    horizontalArrangement = Arrangement.spacedBy(layout.contentGap),
                ) {
                    items(continueBooks, key = { it.id }) { book ->
                        ContinueCard(book = book, progress = progressById[book.id]) {
                            openBook(book)
                        }
                    }
                }
            }
            }

            // 本周概览（单一指标组）
            item(key = "stats-title") {
            Text(
                "本周概览",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.animateEnter(120, reducedMotion),
            )
            }
            item(key = "stats-values") {
            Row(
                modifier = Modifier.fillMaxWidth().animateEnter(120, reducedMotion),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GridStat(Modifier.weight(1f), stringResource(R.string.home_this_week), rememberCountUp(thisWeekNew, reducedMotion).toString())
                VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                GridStat(Modifier.weight(1f), stringResource(R.string.home_reading), rememberCountUp(readingCount, reducedMotion).toString())
                VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                GridStat(Modifier.weight(1f), stringResource(R.string.home_finished), rememberCountUp(completedBooks.size, reducedMotion).toString())
                VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                GridStat(Modifier.weight(1f), stringResource(R.string.home_today), formatCompactDuration(rememberCountUp((todayMs / 60000).toInt(), reducedMotion).toLong() * 60000L))
            }
            }

            // 最近灵感：编辑式文本列表，不套卡片或额外装饰线。
            item(key = "inspiration-header") {
            SectionHeader(
                title = stringResource(R.string.home_recent_inspiration),
                modifier = Modifier.animateEnter(180, reducedMotion),
                action = {
                    TextButton(onClick = { navController.navigate("inspiration") }) {
                        Text(stringResource(R.string.home_see_all) + " ›")
                    }
                },
            )
            }
            if (recentInspirations.isEmpty()) {
                item(key = "inspiration-empty") {
                Column(Modifier.fillMaxWidth().animateEnter(180, reducedMotion)) {
                    Text(
                        "还没有灵感，阅读时选中文字即可保存为灵感。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                }
            } else {
                items(recentInspirations, key = { "inspiration-${it.id}" }) { insp ->
                        // 点击标题打开该条灵感详情。
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .animateEnter(180, reducedMotion)
                                .padding(bottom = 12.dp),
                        ) {
                            Text(
                                insp.title.ifBlank { "无标题灵感" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { navController.navigate("inspiration?inspId=${insp.id}") },
                            )
                            if (insp.body.isNotBlank()) {
                                Text(
                                    insp.body.take(60),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                }
            }

            // 已阅读完成
            item(key = "completed-header") {
            SectionHeader(
                title = stringResource(R.string.home_completed),
                modifier = Modifier.animateEnter(240, reducedMotion),
                action = {
                    TextButton(onClick = { navController.navigate("shelf") }) {
                        Text(stringResource(R.string.home_see_all) + " ›")
                    }
                },
            )
            }
            item(key = "completed-content") {
            if (completedBooks.isEmpty()) {
                EmptyHint(
                    text = "还没有读完的书，继续阅读吧。",
                    onClick = {},
                    modifier = Modifier.animateEnter(240, reducedMotion),
                )
            } else {
                LazyRow(
                    modifier = Modifier.animateEnter(240, reducedMotion),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(completedBooks, key = { it.id }) { book ->
                        CompletedCard(book = book, progress = progressById[book.id]) { openBook(book) }
                    }
                }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun GridStat(modifier: Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .padding(10.dp, 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
    val layout = LocalLayoutTokens.current
    SectionCard(
        onClick = onClick,
        modifier = Modifier.width(276.dp),
        contentPadding = layout.compactCardPadding,
    ) {
        Column {
            Row(
                Modifier.height(88.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.contentGap),
            ) {
                BookCover(
                    book = book,
                    modifier = Modifier.size(56.dp, 80.dp),
                    percent = null,
                    fallback = {
                        Text(
                            book.title.take(2),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!book.author.isNullOrBlank()) {
                        Text(
                            book.author!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        formatBookProgressForCard(pct),
                        style = MaterialTheme.typography.bodySmall,
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
            Spacer(Modifier.height(10.dp))
            // 进度细条：消费既有 primary / surfaceVariant Token，2dp 极细，贴合清屏美学（§冻结设计）
            LinearProgressIndicator(
                progress = { pct.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
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
        BookCover(
            book = book,
            modifier = Modifier.size(84.dp, 112.dp),
            percent = null,
            fallback = {
                Text(
                    book.title.take(2),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                book.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!book.author.isNullOrBlank()) {
                Text(
                    book.author!!,
                    style = MaterialTheme.typography.labelSmall,
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
private fun EmptyHint(text: String, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    EmptyStateHint(
        text = text,
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    )
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

// epochDayOf / formatBookProgressForCard / isBookDisplayable / bookNotReadyLabel / hasBookBeenRead
// 已抽到 ui/util/BookReadiness.kt（含 epochDayOf 死代码删除——HomeViewModel 有自己的 L152 私有版）。

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
