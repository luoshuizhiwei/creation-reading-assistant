package com.creationreadingassistant.ui.screen.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creationreadingassistant.ui.viewmodel.StatsDashboardViewModel

/**
 * Route：管理 ViewModel、分发 action、处理 go-to-shelf 导航回调。
 * StatsScreen 是纯渲染，不持有任何计算。
 */
@Composable
internal fun StatsRoute(
    onGoToShelf: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StatsDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onAction: (StatsAction) -> Unit = { action ->
        when (action) {
            is StatsAction.SelectPeriod -> viewModel.selectPeriod(action.period)
            is StatsAction.ShiftPeriod -> viewModel.shiftPeriod(action.direction)
            StatsAction.GoToShelf -> onGoToShelf()
        }
    }
    StatsScreen(
        state = state,
        onAction = onAction,
        onGoToShelf = onGoToShelf,
        modifier = modifier,
    )
}
