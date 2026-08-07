package com.creationreadingassistant.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.creationreadingassistant.ui.screen.stats.StatsRoute
import com.creationreadingassistant.ui.viewmodel.StatsDashboardViewModel

/* =============================================================
 * 兼容入口：StatsScreen() 转发到新的 StatsRoute()。
 * ============================================================= */

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
