package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阅读统计聚合（P4）：基于 reading_sessions / reading_progress / books / inspirations。
 */
@Singleton
class StatsRepository @Inject constructor(
    private val sessionDao: ReadingSessionDao,
    private val progressDao: ReadingProgressDao,
    private val bookDao: BookDao,
    private val inspirationDao: InspirationDao,
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
     */
    fun observeStats(): Flow<Stats> = combine(
        sessionDao.observeAllActive(),
        progressDao.observeAllActive(),
        bookDao.observeAllActive(),
        inspirationDao.observeAllActive(),
    ) { sessions, progress, books, inspirations ->
        computeFrom(sessions, progress, books, inspirations)
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    suspend fun compute(): Stats = withContext(ioDispatcher) {
        val sessions = sessionDao.observeAllActive().first()
        val books = bookDao.observeAllActive().first()
        val progress = progressDao.observeAllActive().first()
        val inspirations = inspirationDao.observeAllActive().first()
        computeFrom(sessions, progress, books, inspirations)
    }

    private fun computeFrom(
        sessions: List<ReadingSessionEntity>,
        progress: List<ReadingProgressEntity>,
        books: List<BookEntity>,
        inspirations: List<InspirationEntity>,
    ): Stats {
        val bookTitles = books.associate { it.id to it.title }
        val todayDay = LocalDate.now().toEpochDay()
        val d7 = todayDay - 7
        val d30 = todayDay - 30

        fun epochDayOf(iso: String?): Long {
            if (iso.isNullOrBlank()) return -1
            return runCatching {
                Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            }.getOrElse { -1 }
        }

        var todayMs = 0L
        var last7Ms = 0L
        var last30Ms = 0L
        val activeDays = mutableSetOf<Long>()
        val byBookMap = mutableMapOf<String, Long>()
        for (s in sessions) {
            val day = epochDayOf(s.created_at)
            val d = s.duration_ms
            if (day == todayDay) todayMs += d
            if (day >= d7) last7Ms += d
            if (day >= d30) last30Ms += d
            if (day >= 0) activeDays.add(day)
            byBookMap[s.book_id] = (byBookMap[s.book_id] ?: 0L) + d
        }

        // 连续阅读天数（从今天或昨天往前连续）
        var streak = 0
        var cursor = if (activeDays.contains(todayDay)) todayDay else todayDay - 1
        while (activeDays.contains(cursor)) { streak++; cursor-- }

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
            totalSessions = sessions.size,
            totalDurationMs = sessions.sumOf { it.duration_ms },
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
