package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.TrendItem
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlin.math.max

@Composable
internal fun TrendSection(
    stats: StatsUi,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-trend"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("阅读趋势", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (stats.sessionCount > 0) "${stats.sessionCount} 次阅读" else "暂无数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TrendChart(stats.trend)
        }
    }
}

@Composable
private fun TrendChart(items: List<TrendItem>) {
    if (items.isEmpty() || items.none { it.durationMs > 0L }) {
        Text(
            "开始阅读后，这里会显示每天的时长变化。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = LocalLayoutTokens.current.microGap),
        )
        return
    }
    val maxMs = items.maxOf { it.durationMs }.coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEach { item ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                val h = max(4f, (item.durationMs.toFloat() / maxMs) * 92f).dp
                Box(
                    modifier = Modifier
                        .height(h)
                        .fillMaxWidth()
                        .clip(LocalComponentSpec.current.listItemShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
