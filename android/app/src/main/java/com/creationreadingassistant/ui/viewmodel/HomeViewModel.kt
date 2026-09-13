package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import com.creationreadingassistant.data.repository.StatsRepository
import com.creationreadingassistant.data.repository.coarseLowerIso
import com.creationreadingassistant.data.repository.epochDayOf
import com.creationreadingassistant.data.repository.startEpochSecondOf
import com.creationreadingassistant.data.settings.ContinueReadingStore
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.ui.util.hasBookBeenRead
import com.creationreadingassistant.ui.util.isBookDisplayable
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.exp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val books: ImmutableList<BookEntity> = persistentListOf(),
    val progressById: Map<String, ReadingProgressEntity> = emptyMap(),
    val sessionsByBook: Map<String, List<ReadingSessionEntity>> = emptyMap(),
    val removedContinueIds: Map<String, String> = emptyMap(),
    val continueBooks: ImmutableList<BookEntity> = persistentListOf(),
    val completedBooks: ImmutableList<BookEntity> = persistentListOf(),
    val recentInspirations: ImmutableList<InspirationEntity> = persistentListOf(),
    val totalReadBooksCount: Int = 0,
    val thisWeekNew: Int = 0,
    val readingCount: Int = 0,
    val totalReadingMs: Long = 0L,
    val todayReadingMs: Long = 0L,
    val dailyGoalMinutes: Int = 0,
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
    inspirationRepository: InspirationRepository,
    goalStore: com.creationreadingassistant.data.settings.GoalStore,
    private val statsRepository: StatsRepository,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = combine(
        source,
        inspirationRepository.observeAllActive(),
        goalStore.prefs,
    ) { input, inspirations, goalPrefs ->
        HomeAggregationInput(input, inspirations, goalPrefs.dailyMinutes)
    }
        .mapLatest { (input, inspirations, dailyGoalMinutes) ->
            // 总时长/今日时长下推为 SQL SUM，不再逐行 Instant.parse；
            // 边界每次发射现算，与旧实现的逐次重算语义一致。
            val todayStart = startEpochSecondOf(LocalDate.now())
            val tomorrowStart = startEpochSecondOf(LocalDate.now().plusDays(1))
            val totalReadingMs = statsRepository.sumAllActiveDuration()
            val todayReadingMs = statsRepository.sumOccurredDurationBetween(
                todayStart, tomorrowStart, coarseLowerIso(todayStart),
            )
            buildHomeUiState(input, inspirations, totalReadingMs, todayReadingMs, dailyGoalMinutes)
        }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )
}

private data class HomeAggregationInput(
    val source: HomeSource,
    val inspirations: List<InspirationEntity>,
    val dailyGoalMinutes: Int,
)

private fun buildHomeUiState(
    input: HomeSource,
    inspirations: List<InspirationEntity>,
    totalReadingMs: Long,
    todayReadingMs: Long,
    dailyGoalMinutes: Int,
): HomeUiState {
    val progressById = input.progress.associateBy { it.book_id }
    val sessionsByBook = input.sessions.groupBy { it.book_id }
    val weekStart = LocalDate.now().minusDays(LocalDate.now().dayOfWeek.value.toLong() - 1).toEpochDay()
    val nowDay = LocalDate.now().toEpochDay()

    val completed = input.books
        .asSequence()
        .filter { book ->
            val progress = progressById[book.id]
            isBookDisplayable(book) &&
                (progress?.completion_state == "finished" || (progress?.progress_percent ?: 0f) >= 99.5f)
        }
        .sortedWith { first, second ->
            completionTime(second, progressById).compareTo(completionTime(first, progressById))
        }
        .take(8)
        .toList()

    return HomeUiState(
        books = input.books.toImmutableList(),
        progressById = progressById,
        sessionsByBook = sessionsByBook,
        removedContinueIds = input.removedContinueIds,
        continueBooks = buildContinueBooks(
            input.books,
            progressById,
            sessionsByBook,
            input.removedContinueIds,
        ).toImmutableList(),
        completedBooks = completed.toImmutableList(),
        recentInspirations = inspirations.sortedByDescending { it.updated_at }.take(5).toImmutableList(),
        totalReadBooksCount = input.books.count {
            isBookDisplayable(it) && hasBookBeenRead(it, progressById[it.id], sessionsByBook[it.id])
        },
        thisWeekNew = input.books.count { epochDayOf(it.imported_at) in weekStart..nowDay },
        readingCount = input.books.count {
            isBookDisplayable(it) && progressById[it.id]?.completion_state == "reading"
        },
        totalReadingMs = totalReadingMs,
        todayReadingMs = todayReadingMs,
        dailyGoalMinutes = dailyGoalMinutes,
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
        if (!isBookDisplayable(book) || !hasBookBeenRead(book, progress, sessions)) return@mapNotNull null
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
