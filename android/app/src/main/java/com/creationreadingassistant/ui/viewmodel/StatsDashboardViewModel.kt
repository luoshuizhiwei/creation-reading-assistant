package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.data.local.dao.StatsBookRow
import com.creationreadingassistant.data.local.dao.StatsCreatedRow
import com.creationreadingassistant.data.local.dao.StatsProgressRow
import com.creationreadingassistant.data.local.dao.StatsSessionRow
import com.creationreadingassistant.data.repository.StatsRepository
import com.creationreadingassistant.data.repository.computeReadingStreak
import com.creationreadingassistant.ui.screen.stats.EMPTY_STATS
import com.creationreadingassistant.ui.screen.stats.GoalUi
import com.creationreadingassistant.ui.screen.stats.StatsPeriod
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.StatsUiState
import com.creationreadingassistant.ui.screen.stats.computeStats
import com.creationreadingassistant.ui.screen.stats.computeTodayReadingMs
import com.creationreadingassistant.ui.screen.stats.isCurrentPeriod
import com.creationreadingassistant.ui.screen.stats.HeatmapCell
import com.creationreadingassistant.ui.screen.stats.buildHeatmap
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
    goalStore: com.creationreadingassistant.data.settings.GoalStore,
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

    /** 目标投影（P3.2 片 2）：目标关闭时恒为 null（UI 全隐藏），开启时今日已读分钟随 sessions 流动。 */
    private val goalProjection = combine(
        goalStore.prefs,
        statsRepository.observeStatsSessions(),
    ) { prefs, sessions ->
        if (!prefs.goalEnabled) null
        else GoalUi(
            todayReadingMs = computeTodayReadingMs(sessions),
            dailyGoalMinutes = prefs.dailyMinutes,
            streakDays = streakDaysOf(sessions),
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    /** streak 只依赖 sessions，独立缓存避免 sessions 未变时重复计算。 */
    private var cachedStreakTables: List<StatsSessionRow>? = null
    private var cachedStreakDays = -1

    private fun streakDaysOf(sessions: List<StatsSessionRow>): Int {
        if (cachedStreakTables !== sessions) {
            cachedStreakDays = computeReadingStreak(sessions).current
            cachedStreakTables = sessions
        }
        return cachedStreakDays
    }

    /**
     * 图表模型缓存：key = (period, anchor)。
     * tables 未变化时，周期筛选来回切换直接复用已生成的 [StatsUi]，
     * 避免对同一 DB 结果重复 map/group/sort；tables 变化则整体失效。
     */
    private var cachedTables: StatsTables? = null
    private val statsCache = HashMap<Pair<StatsPeriod, LocalDate>, StatsUi>()
    /** 热力图与周期无关，随 tables 变化整体失效复用（与 statsCache 同生命周期）。 */
    private var cachedHeatmap: List<HeatmapCell>? = null

    /**
     * ViewModel 输出完整 [StatsUiState]。
     * Screen 消费这个状态即可渲染，不会再自己做 buildRange / inRange / 过滤 等任何计算。
     */
    internal val uiState: StateFlow<StatsUiState> = combine(
        tables,
        selection,
        goalProjection,
    ) { data, selected, goal ->
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
            goal = goal,
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
