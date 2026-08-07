package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.data.local.dao.StatsBookRow
import com.creationreadingassistant.data.local.dao.StatsCreatedRow
import com.creationreadingassistant.data.local.dao.StatsProgressRow
import com.creationreadingassistant.data.local.dao.StatsSessionRow
import com.creationreadingassistant.data.repository.StatsRepository
import com.creationreadingassistant.ui.screen.stats.EMPTY_STATS
import com.creationreadingassistant.ui.screen.stats.StatsPeriod
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.StatsUiState
import com.creationreadingassistant.ui.screen.stats.computeStats
import com.creationreadingassistant.ui.screen.stats.isCurrentPeriod
import com.creationreadingassistant.ui.screen.stats.periodTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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

/** 兼容旧测试 import `StatsDashboardUiState`。 */
internal typealias StatsDashboardUiState = com.creationreadingassistant.ui.screen.stats.StatsUiState

@HiltViewModel
class StatsDashboardViewModel @Inject constructor(
    statsRepository: StatsRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val selection = MutableStateFlow(StatsSelection())

    private val tables = combine(
        statsRepository.observeStatsSessions(),
        statsRepository.observeStatsProgress(),
        statsRepository.observeStatsBooks(),
        statsRepository.observeStatsInspirations(),
        statsRepository.observeStatsNotes(),
    ) { sessions, progress, books, inspirations, notes ->
        StatsTables(sessions, progress, books, inspirations, notes)
    }
        .distinctUntilChanged()

    /**
     * 图表模型缓存：key = (period, anchor)。
     * tables 未变化时，周期筛选来回切换直接复用已生成的 [StatsUi]，
     * 避免对同一 DB 结果重复 map/group/sort；tables 变化则整体失效。
     */
    private var cachedTables: StatsTables? = null
    private val statsCache = HashMap<Pair<StatsPeriod, LocalDate>, StatsUi>()

    /**
     * ViewModel 输出完整 [StatsUiState]。
     * Screen 消费这个状态即可渲染，不会再自己做 buildRange / inRange / 过滤 等任何计算。
     */
    internal val uiState: StateFlow<StatsUiState> = combine(
        tables,
        selection,
    ) { data, selected ->
        if (cachedTables !== data) {
            statsCache.clear()
            cachedTables = data
        }
        val stats = statsCache.getOrPut(selected.period to selected.anchor) {
            computeStats(
                selected.period,
                selected.anchor,
                data.sessions,
                data.progress,
                data.books,
                data.inspirations,
                data.notes,
            )
        }
        // --- 派生标志（Screen 零计算策略） ---
        val title = periodTitle(selected.period, selected.anchor)
        val currentPeriod = isCurrentPeriod(selected.period, selected.anchor)
        val hasAny = stats.totalReadingMs > 0 || stats.sessionCount > 0
        val globalEmpty = data.books.isEmpty() && !hasAny
        val periodEmpty = !globalEmpty && !hasAny
        StatsUiState(
            period = selected.period,
            anchor = selected.anchor,
            stats = stats,
            booksEmpty = data.books.isEmpty(),
            periodTitle = title,
            isCurrentPeriod = currentPeriod,
            nextEnabled = selected.period != StatsPeriod.TOTAL && !currentPeriod,
            hasAnyData = hasAny,
            showGlobalEmpty = globalEmpty,
            showPeriodEmpty = periodEmpty,
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(stats = null, periodTitle = ""),
        )

    internal fun selectPeriod(period: StatsPeriod) {
        selection.value = selection.value.copy(period = period)
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
