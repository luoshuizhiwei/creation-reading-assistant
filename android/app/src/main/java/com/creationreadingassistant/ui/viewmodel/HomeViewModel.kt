package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.ContinueReadingStore
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.exp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val books: List<BookEntity> = emptyList(),
    val progressById: Map<String, ReadingProgressEntity> = emptyMap(),
    val sessionsByBook: Map<String, List<ReadingSessionEntity>> = emptyMap(),
    val removedContinueIds: Map<String, String> = emptyMap(),
    val continueBooks: List<BookEntity> = emptyList(),
    val completedBooks: List<BookEntity> = emptyList(),
    val recentInspirations: List<InspirationEntity> = emptyList(),
    val totalReadBooksCount: Int = 0,
    val thisWeekNew: Int = 0,
    val readingCount: Int = 0,
    val totalReadingMs: Long = 0L,
    val todayReadingMs: Long = 0L,
    val isReady: Boolean = false,
)

private data class HomeSource(
    val books: List<BookEntity>,
    val progress: List<ReadingProgressEntity>,
    val sessions: List<ReadingSessionEntity>,
    val removedContinueIds: Map<String, String>,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: BookRepository,
    continueReadingStore: ContinueReadingStore,
    inspirationDao: InspirationDao,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val source = combine(
        repository.observeBooks(),
        repository.observeProgress(),
        repository.observeSessions(),
        continueReadingStore.removedIds,
    ) { books, progress, sessions, removedIds ->
        HomeSource(books, progress, sessions, removedIds)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        source,
        inspirationDao.observeAllActive(),
    ) { input, inspirations ->
        buildHomeUiState(input, inspirations)
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )
}

private fun buildHomeUiState(
    input: HomeSource,
    inspirations: List<InspirationEntity>,
): HomeUiState {
    val progressById = input.progress.associateBy { it.book_id }
    val sessionsByBook = input.sessions.groupBy { it.book_id }
    val today = LocalDate.now()
    val weekStart = LocalDate.now().minusDays(LocalDate.now().dayOfWeek.value.toLong() - 1).toEpochDay()
    val nowDay = LocalDate.now().toEpochDay()

    val completed = input.books
        .asSequence()
        .filter { book ->
            val progress = progressById[book.id]
            book.isDisplayable() &&
                (progress?.completion_state == "finished" || (progress?.progress_percent ?: 0f) >= 99.5f)
        }
        .sortedWith { first, second ->
            completionTime(second, progressById).compareTo(completionTime(first, progressById))
        }
        .take(8)
        .toList()

    return HomeUiState(
        books = input.books,
        progressById = progressById,
        sessionsByBook = sessionsByBook,
        removedContinueIds = input.removedContinueIds,
        continueBooks = buildContinueBooks(
            input.books,
            progressById,
            sessionsByBook,
            input.removedContinueIds,
        ),
        completedBooks = completed,
        recentInspirations = inspirations.sortedByDescending { it.updated_at }.take(5),
        totalReadBooksCount = input.books.count {
            it.isDisplayable() && it.hasBeenRead(progressById[it.id], sessionsByBook[it.id])
        },
        thisWeekNew = input.books.count { epochDayOf(it.imported_at) in weekStart..nowDay },
        readingCount = input.books.count {
            it.isDisplayable() && progressById[it.id]?.completion_state == "reading"
        },
        totalReadingMs = input.sessions.sumOf { it.duration_ms },
        todayReadingMs = input.sessions
            .filter { epochDayOf(it.started_at ?: it.created_at) == today.toEpochDay() }
            .sumOf { it.duration_ms },
        isReady = true,
    )
}

private fun completionTime(
    book: BookEntity,
    progressById: Map<String, ReadingProgressEntity>,
): Long {
    val progress = progressById[book.id]
    return progress?.completed_at
        ?: runCatching { Instant.parse(progress?.updated_at ?: book.updated_at).toEpochMilli() }.getOrDefault(Long.MIN_VALUE)
}

private fun BookEntity.isDisplayable(): Boolean {
    if (deleted_at != null || content_status in setOf("failed", "missing", "downloading")) return false
    return size > 0 && (local_content_path != null || local_uri != null || content_hash != null)
}

private fun BookEntity.hasBeenRead(
    progress: ReadingProgressEntity?,
    sessions: List<ReadingSessionEntity>?,
): Boolean = (progress?.progress_percent ?: 0f) > 0f || sessions?.isNotEmpty() == true

private fun epochDayOf(value: String?): Long {
    if (value.isNullOrBlank()) return -1L
    return runCatching {
        Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }.getOrDefault(-1L)
}

private fun buildContinueBooks(
    books: List<BookEntity>,
    progressById: Map<String, ReadingProgressEntity>,
    sessionsByBook: Map<String, List<ReadingSessionEntity>>,
    removedIds: Map<String, String>,
): List<BookEntity> {
    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L
    val monthAgo = now - 30 * dayMs

    return books.mapNotNull { book ->
        val progress = progressById[book.id]
        val sessions = sessionsByBook[book.id]
        if (!book.isDisplayable() || !book.hasBeenRead(progress, sessions)) return@mapNotNull null
        if (progress?.readingState == ReadingCompletionState.SHELVED) return@mapNotNull null
        if ((progress?.progress_percent ?: 0f) >= 99.5f) return@mapNotNull null

        val lastReadAt = progress?.last_read_at
            ?: sessions?.mapNotNull { it.ended_at ?: it.started_at }?.maxOrNull()
        val removedAt = removedIds[book.id]
        if (removedAt != null && (lastReadAt == null || lastReadAt <= removedAt)) return@mapNotNull null

        val lastReadTime = lastReadAt
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: 0L
        val recencyScore = exp((lastReadTime - now).toDouble() / (7 * dayMs))
        val recentSessions = sessions?.count { session ->
            val created = session.created_at
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: 0L
            created > monthAgo
        } ?: 0
        book to (recencyScore * 0.6 + recentSessions.coerceAtMost(10) / 10.0 * 0.4)
    }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(8)
}
