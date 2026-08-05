package com.creationreadingassistant.ui.screen.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.stats.components.CreationSection
import com.creationreadingassistant.ui.screen.stats.components.PeriodSelector
import com.creationreadingassistant.ui.screen.stats.components.StatusSection
import com.creationreadingassistant.ui.screen.stats.components.SummaryGroup
import com.creationreadingassistant.ui.screen.stats.components.TrendSection

/**
 * 纯 StatsScreen：单一 AppScreenScaffold + 唯一 PageLazyColumn，
 * Screen 内绝不做 sessions 分桶、日期范围或整表过滤计算 — 所有派生逻辑已由 ViewModel 在 [StatsUiState] 中产出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsScreen(
    state: StatsUiState,
    onAction: (StatsAction) -> Unit,
    onGoToShelf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 统一兜底：未计算完的 stats 视为 EMPTY_STATS，避免所有下游 nullable 处理。
    val stats = state.stats ?: EMPTY_STATS
    AppScreenScaffold(
        title = "统计",
        modifier = modifier.testTag("stats-screen"),
    ) { scaffoldPadding ->
        PageLazyColumn(
            scaffoldPadding = scaffoldPadding,
        ) {
            item(key = "period-tabs") {
                PeriodSelector(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier.testTag("stats-period"),
                )
            }

            if (state.showGlobalEmpty) {
                item(key = "global-empty") {
                    FullEmptyState(
                        icon = { LineArtBook(sizeDp = 72.dp) },
                        title = "还没有阅读记录",
                        body = "开始阅读后，这里会展示你的阅读时长、书籍和天数统计。",
                        contentPadding = 24.dp,
                        primaryAction = "前往书架" to onGoToShelf,
                        modifier = Modifier.testTag("stats-global-empty"),
                    )
                }
            } else {
                // 只在非空态组合 4 个区块：空时不组合空图表和大卡片占位。
                item(key = "summary") {
                    SummaryGroup(stats = stats)
                }
                item(key = "trend") {
                    TrendSection(stats = stats)
                }
                item(key = "book-status") {
                    StatusSection(status = stats.status)
                }
                item(key = "creation") {
                    CreationSection(stats = stats)
                }
                if (state.showPeriodEmpty) {
                    item(key = "period-empty") {
                        PeriodEmptyHint(Modifier.fillMaxWidth().testTag("stats-period-empty"))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodEmptyHint(modifier: Modifier = Modifier) {
    val layout = LocalLayoutTokens.current
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineArtBook(modifier = Modifier.size(48.dp))
        Text("本期还没有阅读记录", style = MaterialTheme.typography.titleSmall)
        Text(
            "切换其他时间范围，或开始阅读以生成统计。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
