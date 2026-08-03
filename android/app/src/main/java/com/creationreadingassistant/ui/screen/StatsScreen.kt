package com.creationreadingassistant.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.creationreadingassistant.ui.screen.stats.StatsRoute
import com.creationreadingassistant.ui.viewmodel.StatsDashboardViewModel

/* =============================================================
 * 兼容入口：StatsScreen() 转发到新的 StatsRoute()。
 * 本文件行数 <= 80 行。
 *
 * 旧包符号 typealias 转发（StatsDashboardViewModel 与 StatsDashboardViewModelTest
 * 仍 import 旧包名的 StatsPeriod/StatsUi/EMPTY_STATS/computeStats）。
 * ============================================================= */

@Deprecated(
    "Use com.creationreadingassistant.ui.screen.stats.StatsPeriod",
    level = DeprecationLevel.WARNING,
)
internal typealias StatsPeriod = com.creationreadingassistant.ui.screen.stats.StatsPeriod

@Deprecated(
    "Use com.creationreadingassistant.ui.screen.stats.StatsUi",
    level = DeprecationLevel.WARNING,
)
internal typealias StatsUi = com.creationreadingassistant.ui.screen.stats.StatsUi

@Deprecated(
    "Use com.creationreadingassistant.ui.screen.stats.TrendItem",
    level = DeprecationLevel.WARNING,
)
internal typealias TrendItem = com.creationreadingassistant.ui.screen.stats.TrendItem

@Deprecated(
    "Use com.creationreadingassistant.ui.screen.stats.BookStatus",
    level = DeprecationLevel.WARNING,
)
internal typealias BookStatus = com.creationreadingassistant.ui.screen.stats.BookStatus

@Deprecated(
    "Use com.creationreadingassistant.ui.screen.stats.EMPTY_STATS",
    level = DeprecationLevel.WARNING,
)
internal val EMPTY_STATS: StatsUi = com.creationreadingassistant.ui.screen.stats.EMPTY_STATS

@Deprecated(
    "Use com.creationreadingassistant.ui.screen.stats.computeStats",
    level = DeprecationLevel.WARNING,
)
internal fun computeStats(
    period: StatsPeriod,
    anchor: java.time.LocalDate,
    sessions: List<com.creationreadingassistant.data.local.dao.StatsSessionRow>,
    progress: List<com.creationreadingassistant.data.local.dao.StatsProgressRow>,
    books: List<com.creationreadingassistant.data.local.dao.StatsBookRow>,
    inspirations: List<com.creationreadingassistant.data.local.dao.StatsCreatedRow>,
    notes: List<com.creationreadingassistant.data.local.dao.StatsCreatedRow>,
): StatsUi = com.creationreadingassistant.ui.screen.stats.computeStats(
    period, anchor, sessions, progress, books, inspirations, notes,
)

@Deprecated(
    "Use com.creationreadingassistant.ui.screen.stats.buildRange",
    level = DeprecationLevel.WARNING,
)
internal fun buildRange(
    period: StatsPeriod,
    anchor: java.time.LocalDate,
): Pair<java.time.LocalDate, java.time.LocalDate> =
    com.creationreadingassistant.ui.screen.stats.buildRange(period, anchor)

@Composable
public fun StatsScreen(
    onGoToShelf: () -> Unit = {},
    viewModel: StatsDashboardViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    StatsRoute(
        onGoToShelf = onGoToShelf,
        modifier = modifier,
        viewModel = viewModel,
    )
}
