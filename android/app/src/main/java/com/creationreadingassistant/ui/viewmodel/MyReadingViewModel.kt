package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class MyReadingFilter(val label: String) {
    ALL("全部"),
    READING("在读"),
    SHELVED("搁置"),
    FINISHED("读完"),
}

data class MyReadingItem(
    val book: BookEntity,
    val progress: ReadingProgressEntity?,
    val state: ReadingCompletionState,
    val readingTimeMs: Long,
    val startedAtMs: Long,
    val lastActivityAtMs: Long,
)

data class MyReadingMonth(
    val year: Int,
    val month: Int,
    val items: List<MyReadingItem>,
)

data class MyReadingUiState(
    val query: String = "",
    val filter: MyReadingFilter = MyReadingFilter.ALL,
    val counts: Map<MyReadingFilter, Int> = MyReadingFilter.entries.associateWith { 0 },
    val months: List<MyReadingMonth> = emptyList(),
    val isReady: Boolean = false,
)

private data class MyReadingSource(
    val books: List<BookEntity>,
    val progress: List<ReadingProgressEntity>,
    val sessions: List<ReadingSessionEntity>,
)

@HiltViewModel
class MyReadingViewModel @Inject constructor(
    repository: BookRepository,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(MyReadingFilter.ALL)

    private val source = combine(
        repository.observeBooks(),
        repository.observeProgress(),
        repository.observeSessions(),
    ) { books, progress, sessions -> MyReadingSource(books, progress, sessions) }

    val uiState: StateFlow<MyReadingUiState> = combine(source, query, filter) { data, q, selected ->
        buildMyReadingUiState(
            books = data.books,
            progress = data.progress,
            sessions = data.sessions,
            query = q,
            filter = selected,
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MyReadingUiState(),
        )

    fun setQuery(value: String) = query.update { value }

    fun setFilter(value: MyReadingFilter) = filter.update { value }
}

internal fun buildMyReadingUiState(
    books: List<BookEntity>,
    progress: List<ReadingProgressEntity>,
    sessions: List<ReadingSessionEntity>,
    query: String,
    filter: MyReadingFilter,
    zoneId: ZoneId = ZoneId.systemDefault(),
): MyReadingUiState {
    val progressByBook = progress.associateBy { it.book_id }
    val sessionsByBook = sessions.groupBy { it.book_id }
    val allItems = books.mapNotNull { book ->
        if (book.deleted_at != null) return@mapNotNull null
        val bookProgress = progressByBook[book.id]
        val bookSessions = sessionsByBook[book.id].orEmpty()
        val rawState = bookProgress?.readingState ?: ReadingCompletionState.READING
        val state = if ((bookProgress?.progress_percent ?: 0f) >= 99.5f) {
            ReadingCompletionState.FINISHED
        } else rawState
        val hasRecord = bookSessions.isNotEmpty() ||
            bookProgress?.last_read_at != null ||
            (bookProgress?.progress_percent ?: 0f) > 0f ||
            (bookProgress?.total_reading_time_ms ?: 0L) > 0L ||
            state != ReadingCompletionState.READING
        if (!hasRecord) return@mapNotNull null

        val sessionTimes = bookSessions.mapNotNull { parseInstant(it.started_at ?: it.created_at) }
        val progressTime = parseInstant(bookProgress?.last_read_at)
        val fallbackTime = parseInstant(book.imported_at) ?: parseInstant(book.updated_at) ?: 0L
        val startedAt = sessionTimes.minOrNull() ?: progressTime ?: fallbackTime
        val activityAt = listOfNotNull(
            bookProgress?.completed_at,
            progressTime,
            sessionTimes.maxOrNull(),
            startedAt,
        ).maxOrNull() ?: startedAt
        val sessionTotal = bookSessions.sumOf { it.duration_ms.coerceAtLeast(0L) }
        MyReadingItem(
            book = book,
            progress = bookProgress,
            state = state,
            readingTimeMs = maxOf(bookProgress?.total_reading_time_ms ?: 0L, sessionTotal),
            startedAtMs = startedAt,
            lastActivityAtMs = activityAt,
        )
    }

    val counts = mapOf(
        MyReadingFilter.ALL to allItems.size,
        MyReadingFilter.READING to allItems.count { it.state == ReadingCompletionState.READING },
        MyReadingFilter.SHELVED to allItems.count { it.state == ReadingCompletionState.SHELVED },
        MyReadingFilter.FINISHED to allItems.count { it.state == ReadingCompletionState.FINISHED },
    )
    val normalizedQuery = query.trim().lowercase()
    val visible = allItems.asSequence()
        .filter { item ->
            when (filter) {
                MyReadingFilter.ALL -> true
                MyReadingFilter.READING -> item.state == ReadingCompletionState.READING
                MyReadingFilter.SHELVED -> item.state == ReadingCompletionState.SHELVED
                MyReadingFilter.FINISHED -> item.state == ReadingCompletionState.FINISHED
            }
        }
        .filter { item ->
            normalizedQuery.isEmpty() ||
                "${item.book.title} ${item.book.author.orEmpty()}".lowercase().contains(normalizedQuery)
        }
        .sortedByDescending(MyReadingItem::lastActivityAtMs)
        .toList()

    val months = visible.groupBy { item ->
        Instant.ofEpochMilli(item.startedAtMs.coerceAtLeast(0L)).atZone(zoneId).let { it.year to it.monthValue }
    }.map { (key, items) ->
        MyReadingMonth(
            year = key.first,
            month = key.second,
            items = items.sortedByDescending(MyReadingItem::lastActivityAtMs),
        )
    }.sortedWith(compareByDescending<MyReadingMonth> { it.year }.thenByDescending { it.month })

    return MyReadingUiState(
        query = query,
        filter = filter,
        counts = counts,
        months = months,
        isReady = true,
    )
}

private fun parseInstant(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
