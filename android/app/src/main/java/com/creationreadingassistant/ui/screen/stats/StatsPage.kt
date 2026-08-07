package com.creationreadingassistant.ui.screen.stats

import com.creationreadingassistant.data.local.dao.StatsBookRow
import com.creationreadingassistant.data.local.dao.StatsCreatedRow
import com.creationreadingassistant.data.local.dao.StatsProgressRow
import com.creationreadingassistant.data.local.dao.StatsSessionRow
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/* ========== 枚举与模型（保持与原 StatsScreen 一致，仅迁移包） ========== */

/** 与原 StatsPeriod 枚举完全等价：WEEK / MONTH / YEAR / TOTAL。 */
internal enum class StatsPeriod { WEEK, MONTH, YEAR, TOTAL }

internal val PERIOD_LABELS: Map<StatsPeriod, String> = mapOf(
    StatsPeriod.WEEK to "本周",
    StatsPeriod.MONTH to "本月",
    StatsPeriod.YEAR to "本年",
    StatsPeriod.TOTAL to "累计",
)

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
    val shelved: Int = 0,
)

/** ViewModel 输出的纯统计结果（不含 period/anchor 选择态）。 */
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

internal val EMPTY_STATS: StatsUi = StatsUi(
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

/* ========== 用户动作（单向数据流，当前仅 3 个动作 + go-to-shelf） ========== */

internal sealed interface StatsAction {
    data class SelectPeriod(val period: StatsPeriod) : StatsAction
    data class ShiftPeriod(val direction: Int) : StatsAction
    data object GoToShelf : StatsAction
}

/* ========== ViewModel 输出的完整 StatsUiState（含周期选择 + 派生标志） ========== */

/**
 * ViewModel → Screen 的全部状态。
 * Screen 只消费这个 data class 做渲染，不再做任何计算或过滤。
 * - hasAnyData / showGlobalEmpty / showPeriodEmpty / periodTitle / isCurrentPeriod / nextEnabled
 *   全部在 VM 层派生，避免 Screen 里出现 `period == TOTAL`、`buildRange`、`inRange` 等计算。
 */
internal data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val anchor: LocalDate = LocalDate.now(),
    val stats: StatsUi? = null,
    val booksEmpty: Boolean = true,
    val periodTitle: String = "",
    val isCurrentPeriod: Boolean = true,
    val nextEnabled: Boolean = false,
    val hasAnyData: Boolean = false,
    val showGlobalEmpty: Boolean = false,
    val showPeriodEmpty: Boolean = false,
)

/* ========== 格式化函数（保留在 stats 子包，仍属于"日期范围/分桶/连续天数之外"的纯格式） ========== */

internal fun formatThousands(value: Int): String = String.format(Locale.US, "%,d", value)

internal fun formatCompactDuration(ms: Long): String {
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

/* ========== 日期范围/标题/当前周期判断/锚点偏移（放在这里，因为 ViewModel 输出 periodTitle 等需要） ========== */

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

private fun sessionDuration(s: StatsSessionRow): Long {
    val d = s.duration_ms
    if (d <= 0) return 0
    if (d > 24L * 60 * 60 * 1000) return 0
    return d
}

internal fun buildRange(period: StatsPeriod, anchor: LocalDate): Pair<LocalDate, LocalDate> =
    when (period) {
        StatsPeriod.WEEK -> {
            val dow = anchor.dayOfWeek.value
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
        StatsPeriod.TOTAL -> LocalDate.of(1970, 1, 1) to today().plusDays(1)
    }

private fun inRange(range: Pair<LocalDate, LocalDate>, date: LocalDate): Boolean =
    !date.isBefore(range.first) && date.isBefore(range.second)

internal fun periodTitle(period: StatsPeriod, anchor: LocalDate): String = when (period) {
    StatsPeriod.TOTAL -> "全部记录"
    StatsPeriod.WEEK -> {
        val (s, e) = buildRange(period, anchor)
        val end = e.minusDays(1)
        "${s.monthValue}月${s.dayOfMonth}日 - ${end.monthValue}月${end.dayOfMonth}日"
    }
    StatsPeriod.MONTH -> "${anchor.year}年${anchor.monthValue}月"
    StatsPeriod.YEAR -> "${anchor.year}年"
}

internal fun isCurrentPeriod(period: StatsPeriod, anchor: LocalDate): Boolean {
    if (period == StatsPeriod.TOTAL) return true
    val (s, e) = buildRange(period, anchor)
    val t = today()
    return !t.isBefore(s) && t.isBefore(e)
}

/* ========== 核心统计计算函数（复制原 StatsScreen 实现，口径逐行一致） ========== */

@Suppress("LongMethod", "ComplexMethod")
internal fun computeStats(
    period: StatsPeriod,
    anchor: LocalDate,
    sessions: List<StatsSessionRow>,
    progress: List<StatsProgressRow>,
    books: List<StatsBookRow>,
    inspirations: List<StatsCreatedRow>,
    notes: List<StatsCreatedRow>,
): StatsUi {
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

    val displayable = books.filter { it.content_status == "available" }
    val progressMap = progress.associateBy { it.book_id }
    fun hasSession(bid: String) = valid.any { it.book_id == bid }
    fun hasRead(bid: String, p: StatsProgressRow?): Boolean =
        (p?.progress_percent ?: 0f) > 0f || hasSession(bid)

    var reading = 0
    var completed = 0
    var unread = 0
    var shelved = 0
    for (b in displayable) {
        val p = progressMap[b.id]
        val state = ReadingCompletionState.fromStorage(p?.completion_state)
        val comp = state == ReadingCompletionState.FINISHED || (p?.progress_percent ?: 0f) >= 99.5f
        when {
            comp -> completed++
            state == ReadingCompletionState.SHELVED -> shelved++
            hasRead(b.id, p) -> reading++
            else -> unread++
        }
    }
    val unreadable = books.size - displayable.size
    val status = BookStatus(reading, completed, unread, unreadable, books.size, shelved)
    val readBooks = displayable.count { hasRead(it.id, progressMap[it.id]) }

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

/* --- 趋势分桶（属于"日期范围/趋势分桶"，保留在子包；Screen 不调用这些） --- */

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

private fun buildTrend(period: StatsPeriod, anchor: LocalDate, valid: List<StatsSessionRow>): List<TrendItem> =
    when (period) {
        StatsPeriod.WEEK -> fillDaily(valid, buildRange(period, anchor))
        StatsPeriod.MONTH -> fillWeekly(valid, buildRange(period, anchor))
        StatsPeriod.YEAR -> fillMonthly(valid, buildRange(period, anchor))
        StatsPeriod.TOTAL -> fillTotal(valid)
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
