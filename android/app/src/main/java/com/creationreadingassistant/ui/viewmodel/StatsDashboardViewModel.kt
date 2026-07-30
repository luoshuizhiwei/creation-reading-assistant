package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.StatsBookRow
import com.creationreadingassistant.data.local.dao.StatsCreatedRow
import com.creationreadingassistant.data.local.dao.StatsProgressRow
import com.creationreadingassistant.data.local.dao.StatsSessionRow
import com.creationreadingassistant.ui.screen.StatsPeriod
import com.creationreadingassistant.ui.screen.StatsUi
import com.creationreadingassistant.ui.screen.computeStats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal data class StatsDashboardUiState(
    internal val period: StatsPeriod = StatsPeriod.WEEK,
    internal val anchor: LocalDate = LocalDate.now(),
    internal val stats: StatsUi? = null,
    val booksEmpty: Boolean = true,
)

private data class StatsTables(
    val sessions: List<StatsSessionRow>,
    val progress: List<StatsProgressRow>,
    val books: List<StatsBookRow>,
    val inspirations: List<StatsCreatedRow>,
    val notes: List<StatsCreatedRow>,
)

private data class StatsSelection(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val anchor: LocalDate = LocalDate.now(),
)

@HiltViewModel
class StatsDashboardViewModel @Inject constructor(
    sessionDao: ReadingSessionDao,
    progressDao: ReadingProgressDao,
    bookDao: BookDao,
    inspirationDao: InspirationDao,
    noteDao: NoteDao,
) : ViewModel() {
    private val selection = MutableStateFlow(StatsSelection())

    private val tables = combine(
        sessionDao.observeStatsRows(),
        progressDao.observeStatsRows(),
        bookDao.observeStatsRows(),
        inspirationDao.observeStatsCreatedRows(),
        noteDao.observeStatsCreatedRows(),
    ) { sessions, progress, books, inspirations, notes ->
        StatsTables(sessions, progress, books, inspirations, notes)
    }

    internal val uiState: StateFlow<StatsDashboardUiState> = combine(
        tables,
        selection,
    ) { data, selected ->
        StatsDashboardUiState(
            period = selected.period,
            anchor = selected.anchor,
            stats = computeStats(
                selected.period,
                selected.anchor,
                data.sessions,
                data.progress,
                data.books,
                data.inspirations,
                data.notes,
            ),
            booksEmpty = data.books.isEmpty(),
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsDashboardUiState(),
        )

    internal fun selectPeriod(period: StatsPeriod) {
        selection.value = StatsSelection(period = period)
    }

    internal fun shiftPeriod(direction: Int) {
        selection.update { current ->
            val anchor = when (current.period) {
                StatsPeriod.WEEK -> current.anchor.plusWeeks(direction.toLong())
                StatsPeriod.MONTH -> current.anchor.withDayOfMonth(1).plusMonths(direction.toLong())
                StatsPeriod.YEAR -> current.anchor.withDayOfYear(1).plusYears(direction.toLong())
                StatsPeriod.TOTAL -> current.anchor
            }
            current.copy(anchor = anchor)
        }
    }
}
