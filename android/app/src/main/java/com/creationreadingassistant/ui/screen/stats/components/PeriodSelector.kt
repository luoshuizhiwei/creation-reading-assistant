package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.screen.stats.PERIOD_LABELS
import com.creationreadingassistant.ui.screen.stats.StatsAction
import com.creationreadingassistant.ui.screen.stats.StatsPeriod
import com.creationreadingassistant.ui.screen.stats.StatsUiState
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 紧凑现代的周期胶囊切换器：
 * - 纸墨微导轨底座，微边框与细腻圆角；
 * - 选中状态带高亮纸面底色、精微边框与颜色过渡动效反馈；
 * - 左右切换按钮采用微卡片容器包装。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodSelector(
    state: StatsUiState,
    onAction: (StatsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 紧凑现代的胶囊切换导轨
        StatsPeriodTabs(
            period = state.period,
            onSelect = { onAction(StatsAction.SelectPeriod(it)) },
            reducedMotion = reducedMotion,
        )

        // 周期导航栏与标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("stats-period-title"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                onClick = { onAction(StatsAction.ShiftPeriod(-1)) },
                enabled = state.period != StatsPeriod.TOTAL,
                shape = RoundedCornerShape(8.dp),
                color = if (state.period != StatsPeriod.TOTAL) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
                border = if (state.period != StatsPeriod.TOTAL) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.ChevronLeft,
                        contentDescription = "上一周期",
                        modifier = Modifier.size(18.dp),
                        tint = if (state.period != StatsPeriod.TOTAL) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    )
                }
            }

            Text(
                text = state.periodTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Surface(
                onClick = { onAction(StatsAction.ShiftPeriod(1)) },
                enabled = state.nextEnabled,
                shape = RoundedCornerShape(8.dp),
                color = if (state.nextEnabled) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
                border = if (state.nextEnabled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "下一周期",
                        modifier = Modifier.size(18.dp),
                        tint = if (state.nextEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    )
                }
            }
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.testTag("stats-period-tabs"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StatsPeriod.values().forEach { p ->
                val active = period == p
                val label = remember(p) { PERIOD_LABELS[p] ?: "" }

                val animatedBg by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                    animationSpec = tween(if (reducedMotion) 0 else 200),
                    label = "tabBg",
                )
                val animatedContentColor by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(if (reducedMotion) 0 else 200),
                    label = "tabColor",
                )

                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onSelect(p)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = animatedBg,
                    contentColor = animatedContentColor,
                    border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                }
            }
        }
    }
}
