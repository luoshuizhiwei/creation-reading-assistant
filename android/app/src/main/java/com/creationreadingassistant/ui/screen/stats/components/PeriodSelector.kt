package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.screen.stats.PERIOD_LABELS
import com.creationreadingassistant.ui.screen.stats.StatsAction
import com.creationreadingassistant.ui.screen.stats.StatsPeriod
import com.creationreadingassistant.ui.screen.stats.StatsUiState
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodSelector(
    state: StatsUiState,
    onAction: (StatsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    Column(modifier.fillMaxWidth()) {
        StatsPeriodTabs(
            period = state.period,
            onSelect = { onAction(StatsAction.SelectPeriod(it)) },
            reducedMotion = reducedMotion,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .testTag("stats-period-title"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = { onAction(StatsAction.ShiftPeriod(-1)) },
                enabled = state.period != StatsPeriod.TOTAL,
            ) { Icon(Icons.Filled.ChevronLeft, contentDescription = "上一周期") }
            Text(state.periodTitle, style = MaterialTheme.typography.titleMedium)
            IconButton(
                onClick = { onAction(StatsAction.ShiftPeriod(1)) },
                enabled = state.nextEnabled,
            ) { Icon(Icons.Filled.ChevronRight, contentDescription = "下一周期") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsPeriodTabs(
    period: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
    reducedMotion: Boolean = false,
) {
    val haptic = rememberHaptic(reducedMotion)
    Surface(
        shape = LocalComponentSpec.current.listItemShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag("stats-period-tabs"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StatsPeriod.values().forEach { p ->
                val active = period == p
                val label = remember(p) { PERIOD_LABELS[p] ?: "" }
                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onSelect(p)
                    },
                    modifier = Modifier.weight(1f),
                    shape = LocalComponentSpec.current.listItemShape,
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (active) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    Text(
                        label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
