package com.creationreadingassistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.hilt.navigation.compose.hiltViewModel
import com.creationreadingassistant.data.local.dao.StatsBookRow
import com.creationreadingassistant.data.local.dao.StatsCreatedRow
import com.creationreadingassistant.data.local.dao.StatsProgressRow
import com.creationreadingassistant.data.local.dao.StatsSessionRow
import com.creationreadingassistant.ui.viewmodel.StatsDashboardViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * 统计页（1:1 复刻 mobile/src/features/stats/StatsPage.tsx）。
 *
 * 约束：只改本文件。为不新增 ViewModel 文件，通过同文件内的 Hilt @EntryPoint
 * 直接访问已存在的 DAO（reading_sessions / reading_progress / books / inspirations / notes），
 * 用 helper 函数复刻 statistics-helpers.ts 的口径计算各项指标；原生数据层缺失的字段优雅降级为 0/空。
 */

// ---- 时间范围（与 statsPeriodLabels 对齐，无 day） ----
internal enum class StatsPeriod { WEEK, MONTH, YEAR, TOTAL }
private val PERIOD_LABELS = mapOf(
    StatsPeriod.WEEK to "本周",
    StatsPeriod.MONTH to "本月",
    StatsPeriod.YEAR to "本年",
    StatsPeriod.TOTAL to "累计",
)

// ---- 计算结果模型 ----
internal data class TrendItem(
    val dateKey: String,
    val label: String,
    val durationMs: Long,
    val sessionCount: Int,
)

internal data class BookStatus(
    val reading: Int,
    val completed: Int,
    val unread: Int,
    val unreadable: Int,
    val total: Int,
)

internal data class StatsUi(
    val totalReadingMs: Long,
    val readingDays: Int,
    val readBooks: Int,
    val completed: Int,
    val sessionCount: Int,
    val streakCurrent: Int,
    val streakLongest: Int,
    val status: BookStatus,
    val words: Int,
    val speed: Int,
    val noteCount: Int,
    val inspirationCount: Int,
    val trend: List<TrendItem>,
)

internal val EMPTY_STATS = StatsUi(
    totalReadingMs = 0L,
    readingDays = 0,
    readBooks = 0,
    completed = 0,
    sessionCount = 0,
    streakCurrent = 0,
    streakLongest = 0,
    status = BookStatus(0, 0, 0, 0, 0),
    words = 0,
    speed = 0,
    noteCount = 0,
    inspirationCount = 0,
    trend = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onGoToShelf: () -> Unit = {},
    viewModel: StatsDashboardViewModel = hiltViewModel(),
) {
    val layout = LocalLayoutTokens.current
    val reducedMotion = rememberReducedMotion()
    val dashboard by viewModel.uiState.collectAsStateWithLifecycle()
    val period = dashboard.period
    val anchor = dashboard.anchor
    val ui = dashboard.stats ?: EMPTY_STATS

    val hasAnyData = ui.totalReadingMs > 0 || ui.sessionCount > 0
    val showGlobalEmpty = dashboard.booksEmpty && !hasAnyData
    val currentPeriod = isCurrentPeriod(period, anchor)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "统计",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
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
            // 2. 时间范围 tabs（对齐 web .stats-period-tabs 分段控件）
            item(key = "period-tabs") {
                StatsPeriodTabs(period = period, onSelect = viewModel::selectPeriod, reducedMotion = reducedMotion)
            }

            // 3. 周期标题行（上一周期 / 标题 / 下一周期）
            item(key = "period-title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = { viewModel.shiftPeriod(-1) },
                    enabled = period != StatsPeriod.TOTAL,
                ) { Icon(Icons.Filled.ChevronLeft, contentDescription = "上一周期") }
                Text(periodTitle(period, anchor), style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = { viewModel.shiftPeriod(1) },
                    enabled = period != StatsPeriod.TOTAL && !currentPeriod,
                ) { Icon(Icons.Filled.ChevronRight, contentDescription = "下一周期") }
            }
            }

            if (showGlobalEmpty) {
                // 9. 全局空状态
                item(key = "global-empty") {
                    FullEmptyState(
                        icon = {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.LibraryBooks,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp),
                                )
                                LineArtBook(modifier = Modifier.size(72.dp))
                            }
                        },
                        title = "还没有阅读记录",
                        body = "开始阅读后，这里会展示你的阅读时长、书籍和天数统计。",
                        contentPadding = 24.dp,
                        primaryAction = "前往书架" to onGoToShelf,
                    )
                }
            } else {
                item(key = "summary") {
                SummaryMetricGroup(
                    reducedMotion = reducedMotion,
                    items = listOf(
                        SummaryMetric(Icons.Filled.AccessTime, (ui.totalReadingMs / 60000).toInt(), { formatCompactDuration(it.toLong() * 60000) }, "阅读时长"),
                        SummaryMetric(Icons.Filled.CalendarMonth, ui.readingDays, { "$it 天" }, "阅读天数"),
                        SummaryMetric(Icons.Filled.Book, ui.readBooks, { "$it 本" }, "读过书籍"),
                        SummaryMetric(Icons.Filled.CheckCircle, ui.completed, { "$it 本" }, "已读完"),
                        SummaryMetric(Icons.Filled.BarChart, ui.streakCurrent, { "$it 天" }, "当前连续"),
                        SummaryMetric(Icons.Filled.CalendarMonth, ui.streakLongest, { "$it 天" }, "最长连续"),
                    ),
                )
                }

                // 6. 阅读趋势卡片
                item(key = "trend") {
                SectionCard(modifier = Modifier.fillMaxWidth().animateEnter(reducedMotion = reducedMotion)) {
                    Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("阅读趋势", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (ui.sessionCount > 0) "${ui.sessionCount} 次阅读" else "暂无数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TrendChart(ui.trend)
                    }
                }
                }

                // 7. 书籍状态卡片
                item(key = "book-status") {
                SectionCard(modifier = Modifier.fillMaxWidth().animateEnter(reducedMotion = reducedMotion)) {
                    Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("书籍状态", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "共 ${ui.status.total} 本",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusBar("在读", ui.status.reading, ui.status.total, MaterialTheme.colorScheme.primary)
                        StatusBar("已读完", ui.status.completed, ui.status.total, MaterialTheme.colorScheme.tertiary)
                        StatusBar("未开始", ui.status.unread, ui.status.total, MaterialTheme.colorScheme.secondary)
                        if (ui.status.unreadable > 0) {
                            StatusBar("不可读", ui.status.unreadable, ui.status.total, MaterialTheme.colorScheme.error)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    "不可读包括同步占位、导入失败或文件缺失的书籍。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                }

                // 8. 阅读与创作卡片
                item(key = "creation") {
                SectionCard(modifier = Modifier.fillMaxWidth().animateEnter(reducedMotion = reducedMotion)) {
                    Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("阅读与创作", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CreationItem(Icons.Filled.TextFields, formatThousands(ui.words), "阅读字数", Modifier.weight(1f), reducedMotion = reducedMotion)
                            CreationItem(Icons.Filled.Speed, "${ui.speed}", "字/分钟", Modifier.weight(1f), reducedMotion = reducedMotion)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CreationItem(Icons.Filled.ChatBubble, "${ui.noteCount}", "笔记", Modifier.weight(1f), reducedMotion = reducedMotion)
                            CreationItem(Icons.Filled.AutoAwesome, "${ui.inspirationCount}", "灵感", Modifier.weight(1f), reducedMotion = reducedMotion)
                        }
                    }
                }
                }

                // 9. 本期无数据
                if (!hasAnyData) {
                    item(key = "period-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LineArtBook(modifier = Modifier.size(48.dp))
                        Text("本期还没有阅读记录", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "切换其他时间范围，或开始阅读以生成统计。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    }
                }
            }
        }
    }
}

// ===================== UI 小组件 =====================

@Composable
private fun StatsPeriodTabs(period: StatsPeriod, onSelect: (StatsPeriod) -> Unit, reducedMotion: Boolean = false) {
    val haptic = rememberHaptic(reducedMotion)
    Surface(
        shape = LocalComponentSpec.current.listItemShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StatsPeriod.values().forEach { p ->
                val active = period == p
                Surface(
                    onClick = { haptic(HapticFeedbackType.TextHandleMove); onSelect(p) },
                    modifier = Modifier.weight(1f),
                    shape = LocalComponentSpec.current.listItemShape,
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        PERIOD_LABELS[p] ?: "",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}



private data class SummaryMetric(
    val icon: ImageVector,
    val value: Int,
    val format: (Int) -> String,
    val label: String,
)

@Composable
private fun SummaryMetricGroup(items: List<SummaryMetric>, reducedMotion: Boolean = false) {
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 0.dp,
    ) {
        items.chunked(2).forEachIndexed { rowIndex, rowItems ->
            if (rowIndex > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEachIndexed { columnIndex, item ->
                    if (columnIndex > 0) {
                        VerticalDivider(
                            modifier = Modifier.height(52.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    SummaryMetricCell(
                        item = item,
                        modifier = Modifier.weight(1f),
                        reducedMotion = reducedMotion,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCell(
    item: SummaryMetric,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val layout = LocalLayoutTokens.current
    Row(
        modifier = modifier
            .heightIn(min = 76.dp)
            .padding(layout.compactCardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(layout.relatedGap),
    ) {
        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            val display = rememberCountUp(item.value, reducedMotion)
            Text(
                item.format(display),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                item.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StreakItem(icon: ImageVector, value: Int, label: String, active: Boolean, modifier: Modifier = Modifier, reducedMotion: Boolean = false) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            Text("${rememberCountUp(value, reducedMotion)}", style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CreationItem(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier, reducedMotion: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = LocalComponentSpec.current.listItemShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            val numeric = value.toIntOrNull()
            Text(
                if (numeric != null) rememberCountUp(numeric, reducedMotion).toString() else value,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrendChart(items: List<TrendItem>) {
    if (items.isEmpty() || items.none { it.durationMs > 0L }) {
        Text(
            "开始阅读后，这里会显示每天的时长变化。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = LocalLayoutTokens.current.microGap),
        )
        return
    }
    val maxMs = items.maxOf { it.durationMs }.coerceAtLeast(1L)
    Row(
        modifier = Modifier.fillMaxWidth().height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEach { item ->
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                val h = max(4f, (item.durationMs.toFloat() / maxMs) * 92f).dp
                Box(
                    modifier = Modifier
                        .height(h)
                        .fillMaxWidth()
                        .clip(LocalComponentSpec.current.listItemShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusBar(label: String, count: Int, total: Int, color: Color) {
    val fraction = if (total > 0) (count.toFloat() / total) else 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color = color))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = fraction)
                    .height(8.dp)
                    .clip(LocalComponentSpec.current.listItemShape)
                    .background(color = color),
            )
        }
        Text("$count", style = MaterialTheme.typography.bodySmall)
    }
}

// ===================== 计算逻辑（复刻 statistics-helpers.ts） =====================

private fun today(): LocalDate = LocalDate.now()

private fun toDateKey(d: LocalDate): String =
    String.format(Locale.ROOT, "%04d-%02d-%02d", d.year, d.monthValue, d.dayOfMonth)

private fun parseDate(iso: String?): LocalDate? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
}

private fun sessionDateKey(s: StatsSessionRow): String {
    val d = parseDate(s.occurred_at) ?: today()
    return toDateKey(d)
}

/** 有效会话时长（过滤 <=0 与 >24h 的异常值），对齐 MAX_SESSION_MS。 */
private fun sessionDuration(s: StatsSessionRow): Long {
    val d = s.duration_ms
    if (d <= 0) return 0
    if (d > 24L * 60 * 60 * 1000) return 0
    return d
}

/** 左闭右开的日期区间 [start, end)。 */
private fun buildRange(period: StatsPeriod, anchor: LocalDate): Pair<LocalDate, LocalDate> {
    return when (period) {
        StatsPeriod.WEEK -> {
            val dow = anchor.dayOfWeek.value // 1=周一
            val start = anchor.minusDays((dow - 1).toLong())
            start to start.plusDays(7)
        }
        StatsPeriod.MONTH -> {
            val start = anchor.withDayOfMonth(1)
            start to start.plusMonths(1)
        }
        StatsPeriod.YEAR -> {
            val start = anchor.withDayOfYear(1)
            start to start.plusYears(1)
        }
        StatsPeriod.TOTAL -> {
            LocalDate.of(1970, 1, 1) to today().plusDays(1)
        }
    }
}

private fun inRange(range: Pair<LocalDate, LocalDate>, date: LocalDate): Boolean =
    !date.isBefore(range.first) && date.isBefore(range.second)

private fun periodTitle(period: StatsPeriod, anchor: LocalDate): String {
    return when (period) {
        StatsPeriod.TOTAL -> "全部记录"
        StatsPeriod.WEEK -> {
            val (s, e) = buildRange(period, anchor)
            val end = e.minusDays(1)
            "${s.monthValue}月${s.dayOfMonth}日 - ${end.monthValue}月${end.dayOfMonth}日"
        }
        StatsPeriod.MONTH -> "${anchor.year}年${anchor.monthValue}月"
        StatsPeriod.YEAR -> "${anchor.year}年"
    }
}

private fun isCurrentPeriod(period: StatsPeriod, anchor: LocalDate): Boolean {
    if (period == StatsPeriod.TOTAL) return true
    val (s, e) = buildRange(period, anchor)
    val t = today()
    return !t.isBefore(s) && t.isBefore(e)
}

private fun shiftAnchor(anchor: LocalDate, period: StatsPeriod, dir: Int): LocalDate {
    return when (period) {
        StatsPeriod.WEEK -> anchor.plusDays(7L * dir)
        StatsPeriod.MONTH -> anchor.withDayOfMonth(1).plusMonths(dir.toLong())
        StatsPeriod.YEAR -> anchor.withDayOfMonth(1).withMonth(1).plusYears(dir.toLong())
        StatsPeriod.TOTAL -> anchor
    }
}

internal fun computeStats(
    period: StatsPeriod,
    anchor: LocalDate,
    sessions: List<StatsSessionRow>,
    progress: List<StatsProgressRow>,
    books: List<StatsBookRow>,
    inspirations: List<StatsCreatedRow>,
    notes: List<StatsCreatedRow>,
): StatsUi {
    // 空数据早退：全部表为空时直接返回共享的 EMPTY_STATS 单例，
    // 不构建趋势桶 / 进度映射等图表结构（此时 UI 展示全局空态，不渲染趋势图）。
    if (sessions.isEmpty() && progress.isEmpty() && books.isEmpty() &&
        inspirations.isEmpty() && notes.isEmpty()
    ) {
        return EMPTY_STATS
    }
    val valid = sessions.filter { sessionDuration(it) > 0 }
    val (rs, re) = buildRange(period, anchor)
    val periodSessions = valid.filter { s ->
        val d = parseDate(s.occurred_at)
        d != null && inRange(rs to re, d)
    }

    val totalReadingMs = periodSessions.sumOf { it.duration_ms }
    val readingDays = periodSessions.map { sessionDateKey(it) }.toSet().size
    val sessionCount = periodSessions.size

    // 书籍状态（content_status != "available" 视为不可读）
    val displayable = books.filter { it.content_status == "available" }
    val progressMap = progress.associateBy { it.book_id }
    fun hasSession(bid: String) = valid.any { it.book_id == bid }
    fun hasRead(bid: String, p: StatsProgressRow?): Boolean =
        (p?.progress_percent ?: 0f) > 0f || hasSession(bid)

    var reading = 0
    var completed = 0
    var unread = 0
    for (b in displayable) {
        val p = progressMap[b.id]
        val comp = p?.completion_state == "completed" || (p?.progress_percent ?: 0f) >= 99.5f
        when {
            comp -> completed++
            hasRead(b.id, p) -> reading++
            else -> unread++
        }
    }
    val unreadable = books.size - displayable.size
    val status = BookStatus(reading, completed, unread, unreadable, books.size)
    val readBooks = displayable.count { hasRead(it.id, progressMap[it.id]) }

    // 阅读字数 / 速度（按周期内每本书最大进度估算，size 缺失则降级为 0）
    val sizeMap = books.associate { it.id to it.size }
    val maxProg = mutableMapOf<String, Float>()
    for (s in periodSessions) {
        val p = s.progress_percent ?: 0f
        maxProg[s.book_id] = max(maxProg[s.book_id] ?: 0f, p)
    }
    var words = 0
    for ((bid, frac) in maxProg) {
        val size = sizeMap[bid] ?: 0
        if (size <= 0) continue
        val bookWords = max(1, round(size / 3.0).toInt())
        words += round(bookWords * min(100f, max(0f, frac)) / 100f).toInt()
    }
    val speed = if (totalReadingMs > 0) {
        round(words / max(1.0, totalReadingMs / 60000.0)).toInt()
    } else {
        0
    }

    // 笔记 / 灵感（按 created_at 落在区间内）
    val noteCount = notes.count { parseDate(it.created_at)?.let { d -> inRange(rs to re, d) } == true }
    val inspirationCount = inspirations.count { parseDate(it.created_at)?.let { d -> inRange(rs to re, d) } == true }

    val (streakCurrent, streakLongest) = computeStreak(valid)
    val trend = buildTrend(period, anchor, valid)

    return StatsUi(
        totalReadingMs = totalReadingMs,
        readingDays = readingDays,
        readBooks = readBooks,
        completed = completed,
        sessionCount = sessionCount,
        streakCurrent = streakCurrent,
        streakLongest = streakLongest,
        status = status,
        words = words,
        speed = speed,
        noteCount = noteCount,
        inspirationCount = inspirationCount,
        trend = trend,
    )
}

private fun computeStreak(valid: List<StatsSessionRow>): Pair<Int, Int> {
    val keys = valid.map { sessionDateKey(it) }.toSet().toList().sorted()
    if (keys.isEmpty()) return 0 to 0
    var longest = 1
    var temp = 1
    for (i in 1..keys.lastIndex) {
        val prev = runCatching { LocalDate.parse(keys[i - 1]) }.getOrNull()
        val curr = runCatching { LocalDate.parse(keys[i]) }.getOrNull()
        if (prev != null && curr != null) {
            val diff = ChronoUnit.DAYS.between(prev, curr)
            if (diff == 1L) {
                temp++
                longest = max(longest, temp)
            } else {
                temp = 1
            }
        }
    }
    val t = today()
    var current = 0
    var cursor = if (keys.contains(toDateKey(t))) t else t.minusDays(1)
    while (keys.contains(toDateKey(cursor))) {
        current++
        cursor = cursor.minusDays(1)
    }
    return current to longest
}

// ---- 阅读趋势 ----

private fun bucketByDate(valid: List<StatsSessionRow>): Map<String, Pair<Long, Int>> {
    val m = mutableMapOf<String, Pair<Long, Int>>()
    for (s in valid) {
        val d = parseDate(s.occurred_at) ?: continue
        val key = toDateKey(d)
        val dur = sessionDuration(s)
        val e = m[key]
        m[key] = if (e == null) dur to 1 else (e.first + dur) to (e.second + 1)
    }
    return m
}

private fun buildTrend(period: StatsPeriod, anchor: LocalDate, valid: List<StatsSessionRow>): List<TrendItem> {
    return when (period) {
        StatsPeriod.WEEK -> fillDaily(valid, buildRange(period, anchor))
        StatsPeriod.MONTH -> fillWeekly(valid, buildRange(period, anchor))
        StatsPeriod.YEAR -> fillMonthly(valid, buildRange(period, anchor))
        StatsPeriod.TOTAL -> fillTotal(valid)
    }
}

private fun fillDaily(valid: List<StatsSessionRow>, range: Pair<LocalDate, LocalDate>): List<TrendItem> {
    val groups = bucketByDate(valid)
    val items = mutableListOf<TrendItem>()
    var d = range.first
    while (d.isBefore(range.second)) {
        val key = toDateKey(d)
        val g = groups[key]
        items.add(TrendItem(key, "${d.monthValue}/${d.dayOfMonth}", g?.first ?: 0, g?.second ?: 0))
        d = d.plusDays(1)
    }
    return items
}

private fun fillWeekly(valid: List<StatsSessionRow>, range: Pair<LocalDate, LocalDate>): List<TrendItem> {
    val buckets = mutableMapOf<String, Pair<Long, Int>>()
    for (s in valid) {
        val d = parseDate(s.occurred_at) ?: continue
        if (d.isBefore(range.first) || !d.isBefore(range.second)) continue
        val dow = d.dayOfWeek.value
        val off = if (dow == 7) -6 else (1 - dow)
        var ws = d.plusDays(off.toLong())
        if (ws.isBefore(range.first)) ws = range.first
        val key = toDateKey(ws)
        val dur = sessionDuration(s)
        val b = buckets[key]
        buckets[key] = if (b == null) dur to 1 else (b.first + dur) to (b.second + 1)
    }
    val items = mutableListOf<TrendItem>()
    var idx = 1
    var d = range.first
    while (d.isBefore(range.second)) {
        val dow = d.dayOfWeek.value
        val off = if (dow == 7) -6 else (1 - dow)
        var ws = d.plusDays(off.toLong())
        if (ws.isBefore(range.first)) ws = range.first
        val key = toDateKey(ws)
        val b = buckets[key]
        items.add(TrendItem(key, "第${idx}周", b?.first ?: 0, b?.second ?: 0))
        d = d.plusDays(7)
        idx++
    }
    return items
}

private fun fillMonthly(valid: List<StatsSessionRow>, range: Pair<LocalDate, LocalDate>): List<TrendItem> {
    val buckets = mutableMapOf<String, Pair<Long, Int>>()
    for (s in valid) {
        val d = parseDate(s.occurred_at) ?: continue
        if (d.isBefore(range.first) || !d.isBefore(range.second)) continue
        val key = "${d.year}-${String.format(Locale.ROOT, "%02d", d.monthValue)}"
        val dur = sessionDuration(s)
        val b = buckets[key]
        buckets[key] = if (b == null) dur to 1 else (b.first + dur) to (b.second + 1)
    }
    val items = mutableListOf<TrendItem>()
    var d = range.first
    while (d.isBefore(range.second)) {
        val key = "${d.year}-${String.format(Locale.ROOT, "%02d", d.monthValue)}"
        val b = buckets[key]
        items.add(TrendItem(key, "${d.monthValue}月", b?.first ?: 0, b?.second ?: 0))
        d = d.plusMonths(1)
    }
    return items
}

private fun fillTotal(valid: List<StatsSessionRow>): List<TrendItem> {
    val dates = valid.mapNotNull { parseDate(it.occurred_at) }
    if (dates.isEmpty()) return emptyList()
    val start = dates.minOrNull()!!.withDayOfMonth(1)
    val end = dates.maxOrNull()!!.withDayOfMonth(1).plusMonths(1)
    return fillMonthly(valid, start to end)
}

// ---- 时长格式化（对齐 formatCompactDuration） ----
private fun formatThousands(value: Int): String = String.format(Locale.US, "%,d", value)

private fun formatCompactDuration(ms: Long): String {
    if (ms <= 0) return "0 分钟"
    val minutes = (ms / 60000).toInt()
    if (minutes < 60) return "$minutes 分钟"
    val hours = minutes / 60
    val rem = minutes % 60
    if (rem == 0) return "$hours 小时"
    val tenths = round(rem / 60.0 * 10).toInt()
    if (tenths >= 10) return "${hours + 1} 小时"
    return "$hours.${tenths} 小时"
}
