package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.StatsBookRow
import com.creationreadingassistant.data.local.dao.StatsCreatedRow
import com.creationreadingassistant.data.local.dao.StatsProgressRow
import com.creationreadingassistant.data.local.dao.StatsSessionRow
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 将 ISO-8601 时间戳解析为系统时区的 epochDay；空值 / 解析失败返回 -1。
 * 供统计聚合与 Home 页（最近完成排序）共用的唯一实现。
 */
internal fun epochDayOf(iso: String?): Long {
    if (iso.isNullOrBlank()) return -1
    return runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }.getOrElse { -1 }
}

/**
 * 本地日历日零点（系统时区）的 epoch 秒边界，供聚合下推 SQL 使用。
 * 与 [epochDayOf] 同一时区语义：`epochDayOf(iso) >= date.toEpochDay()`
 * ⇔ `epochSecond(iso) >= startEpochSecondOf(date)`（日边界恰在整数秒上，
 * 秒级向下取整不改变日历日归属，详见 ReadingSessionDao 聚合查询注释）。
 */
internal fun startEpochSecondOf(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

/**
 * 索引加速用粗下界（ISO 串）：比精确边界早 2 天，供时间字段预过滤。
 * ISO 'Z' 串的字典序与时间序只在「整数秒边界后带小数秒」的串上不一致，
 * 回退 2 天足以保证不会误杀任何应命中的行；最终结果仍由精确的 epoch 秒谓词决定。
 */
internal fun coarseLowerIso(startEpochSecond: Long): String =
    Instant.ofEpochSecond(startEpochSecond - 2 * 86_400L).toString()

/**
 * 阅读统计聚合（P4）：基于 reading_sessions / reading_progress / books / inspirations。
 */
@Singleton
class StatsRepository @Inject constructor(
    private val sessionDao: ReadingSessionDao,
    private val progressDao: ReadingProgressDao,
    private val bookDao: BookDao,
    private val inspirationDao: InspirationDao,
    private val noteDao: NoteDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    data class ByBook(val bookId: String, val title: String, val totalMs: Long)
    data class RecentBook(val bookId: String, val title: String, val progressPercent: Float)
    data class Stats(
        val totalSessions: Int,
        val totalDurationMs: Long,
        val todayMs: Long,
        val last7Ms: Long,
        val last30Ms: Long,
        val streakDays: Int,
        val bookCount: Int,
        val completedBookCount: Int,
        val inspirationCount: Int,
        val recentBooks: List<RecentBook>,
        val byBook: List<ByBook>,
    )

    /**
     * 实时观察统计：任意底层数据变化后自动重算。
     *
     * 今日/7日/30日时长不再拉全表逐行 Instant.parse，而是下推为 DAO 层
     * SUM 聚合（见 [ReadingSessionDao.sumOccurredDurationBetween] 等）；
     * 边界在每次发射时用 LocalDate.now() 现算，与旧实现的逐次重算语义一致。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeStats(): Flow<Stats> = combine(
        sessionDao.observeAllActive(),
        progressDao.observeAllActive(),
        bookDao.observeAllActive(),
        inspirationDao.observeAllActive(),
    ) { sessions, progress, books, inspirations ->
        StatsSource(sessions, progress, books, inspirations)
    }
        .mapLatest { src -> computeWithWindowAggregates(src) }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    suspend fun compute(): Stats = withContext(ioDispatcher) {
        val sessions = sessionDao.observeAllActive().first()
        val books = bookDao.observeAllActive().first()
        val progress = progressDao.observeAllActive().first()
        val inspirations = inspirationDao.observeAllActive().first()
        computeWithWindowAggregates(StatsSource(sessions, progress, books, inspirations))
    }

    /** 有效活跃会话总时长（Home 页顶栏统计卡，排除异常时长）。 */
    suspend fun sumAllActiveDuration(): Long = sessionDao.sumAllActiveDuration()

    /** 发生时间落在 [startEpochSecond, endEpochSecond) 内的活跃会话时长之和（Home 今日窗口）。 */
    suspend fun sumOccurredDurationBetween(
        startEpochSecond: Long,
        endEpochSecond: Long,
        coarseIso: String,
    ): Long = sessionDao.sumOccurredDurationBetween(startEpochSecond, endEpochSecond, coarseIso)

    /** 统计看板数据源：会话明细（StatsDashboardViewModel 窄投影）。 */
    fun observeStatsSessions(): Flow<List<StatsSessionRow>> = sessionDao.observeStatsRows()

    /** 统计看板数据源：进度明细。 */
    fun observeStatsProgress(): Flow<List<StatsProgressRow>> = progressDao.observeStatsRows()

    /** 统计看板数据源：书籍明细。 */
    fun observeStatsBooks(): Flow<List<StatsBookRow>> = bookDao.observeStatsRows()

    /** 统计看板数据源：灵感创建记录。 */
    fun observeStatsInspirations(): Flow<List<StatsCreatedRow>> = inspirationDao.observeStatsCreatedRows()

    /** 统计看板数据源：笔记创建记录。 */
    fun observeStatsNotes(): Flow<List<StatsCreatedRow>> = noteDao.observeStatsCreatedRows()

    /** 时间窗口聚合下推：今日/7日/30日由 SQL SUM 计算，其余指标仍由 [computeFrom] 内存汇总。 */
    private suspend fun computeWithWindowAggregates(src: StatsSource): Stats {
        val today = LocalDate.now()
        val todayStart = startEpochSecondOf(today)
        val tomorrowStart = startEpochSecondOf(today.plusDays(1))
        val last7Start = startEpochSecondOf(today.minusDays(7))
        val last30Start = startEpochSecondOf(today.minusDays(30))

        val todayMs = sessionDao.sumOccurredDurationBetween(
            todayStart, tomorrowStart, coarseLowerIso(todayStart),
        )
        val last7Ms = sessionDao.sumOccurredDurationSince(last7Start, coarseLowerIso(last7Start))
        val last30Ms = sessionDao.sumOccurredDurationSince(last30Start, coarseLowerIso(last30Start))

        return computeFrom(
            src.sessions, src.progress, src.books, src.inspirations,
            todayMs = todayMs, last7Ms = last7Ms, last30Ms = last30Ms,
        )
    }

    private data class StatsSource(
        val sessions: List<ReadingSessionEntity>,
        val progress: List<ReadingProgressEntity>,
        val books: List<BookEntity>,
        val inspirations: List<InspirationEntity>,
    )

    private fun computeFrom(
        sessions: List<ReadingSessionEntity>,
        progress: List<ReadingProgressEntity>,
        books: List<BookEntity>,
        inspirations: List<InspirationEntity>,
        todayMs: Long,
        last7Ms: Long,
        last30Ms: Long,
    ): Stats {
        val bookTitles = books.associate { it.id to it.title }
        val validSessions = sessions.filter { isValidReadingSessionDuration(it.duration_ms) }
        val byBookMap = mutableMapOf<String, Long>()
        for (s in validSessions) {
            byBookMap[s.book_id] = (byBookMap[s.book_id] ?: 0L) + s.duration_ms
        }

        val streak = computeReadingStreak(
            validSessions.map { session ->
                StatsSessionRow(
                    book_id = session.book_id,
                    occurred_at = session.started_at ?: session.created_at,
                    duration_ms = session.duration_ms,
                    progress_percent = session.progress_percent,
                )
            },
        ).current

        val byBook = byBookMap.entries.sortedByDescending { it.value }.map {
            ByBook(it.key, bookTitles[it.key] ?: "(未知)", it.value)
        }
        val recentBooks = progress.sortedByDescending { it.updated_at }.take(5).map {
            RecentBook(it.book_id, bookTitles[it.book_id] ?: "(未知)", it.progress_percent)
        }
        val completedBookCount = progress.count {
            it.completion_state == "finished" || it.progress_percent >= 99.5f
        }

        return Stats(
            totalSessions = validSessions.size,
            totalDurationMs = validSessions.sumOf { it.duration_ms },
            todayMs = todayMs,
            last7Ms = last7Ms,
            last30Ms = last30Ms,
            streakDays = streak,
            bookCount = books.size,
            completedBookCount = completedBookCount,
            inspirationCount = inspirations.size,
            recentBooks = recentBooks,
            byBook = byBook,
        )
    }
}
