package com.creationreadingassistant.ui.screen.home

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.ui.util.hasBookBeenRead
import com.creationreadingassistant.ui.util.isBookDisplayable
import java.time.Instant

internal enum class ContinueSortKey { RECENT, PROGRESS, CREATED, TITLE }

internal val SORT_LABELS = mapOf(
    ContinueSortKey.RECENT to "最近阅读",
    ContinueSortKey.PROGRESS to "阅读进度",
    ContinueSortKey.CREATED to "加入书库时间",
    ContinueSortKey.TITLE to "书名",
)

internal enum class MenuView { NONE, MORE, SORT }

internal data class ContinueItem(
    val book: BookEntity,
    val progress: Float,
    val lastReadAt: String?,
    val originalIndex: Int,
)

internal fun buildContinueItems(
    books: List<BookEntity>,
    progressById: Map<String, ReadingProgressEntity>,
    sessions: Map<String, List<ReadingSessionEntity>>,
    removedIds: Map<String, String>,
): List<ContinueItem> {
    val now = System.currentTimeMillis()
    val msPerDay = 24 * 60 * 60 * 1000L
    val recencyDecayMs = 7 * msPerDay
    val monthAgo = now - 30 * msPerDay

    return books.mapIndexedNotNull { index, book ->
        val progress = progressById[book.id]
        val pct = progress?.progress_percent ?: 0f
        if (!isBookDisplayable(book)) return@mapIndexedNotNull null
        if (!hasBookBeenRead(book, progress, sessions[book.id])) return@mapIndexedNotNull null
        if (progress?.readingState == ReadingCompletionState.SHELVED) return@mapIndexedNotNull null
        if (pct >= 99.5f) return@mapIndexedNotNull null
        val removedAt = removedIds[book.id]
        if (removedAt != null) {
            val lastReadAt = lastReadAtFor(book, progress, sessions[book.id])
            if (lastReadAt == null || lastReadAt <= removedAt) {
                return@mapIndexedNotNull null
            }
        }

        val lastReadAt = lastReadAtFor(book, progress, sessions[book.id])
        val lastReadTime = lastReadAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        val recencyScore = kotlin.math.exp((lastReadTime - now).toDouble() / recencyDecayMs)
        val recentSessions = sessions[book.id]?.count { session ->
            val t = session.created_at?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
            t > monthAgo
        } ?: 0
        val frequencyScore = (recentSessions.coerceAtMost(10)) / 10.0
        val score = recencyScore * 0.6 + frequencyScore * 0.4

        ContinueItem(
            book = book,
            progress = pct,
            lastReadAt = lastReadAt,
            originalIndex = index,
        )
    }
}

internal fun lastReadAtFor(
    book: BookEntity,
    progress: ReadingProgressEntity?,
    sessions: List<ReadingSessionEntity>?,
): String? {
    progress?.last_read_at?.let { return it }
    return sessions
        ?.filter { it.book_id == book.id }
        ?.mapNotNull { it.ended_at ?: it.started_at }
        ?.maxOrNull()
}
