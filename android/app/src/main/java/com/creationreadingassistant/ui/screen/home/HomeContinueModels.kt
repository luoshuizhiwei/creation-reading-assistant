package com.creationreadingassistant.ui.screen.home

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.ui.util.hasBookBeenRead
import com.creationreadingassistant.ui.util.isBookDisplayable
import com.creationreadingassistant.ui.util.isReadingFinished
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
    /** 进度行 completion_state（S05-①）：卡片文案据此避免把取消读完的残余 100% 标成「已读完」。 */
    val completionState: String?,
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
        // S05-① 契约：排除「已完成」以显式状态为准。markFinished 抬高的 100% 残余在
        // 取消读完（state=reading）后不得再触发排除——用户要继续读的书必须回到这里。
        if (isReadingFinished(progress)) return@mapIndexedNotNull null
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
            completionState = progress?.completion_state,
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
