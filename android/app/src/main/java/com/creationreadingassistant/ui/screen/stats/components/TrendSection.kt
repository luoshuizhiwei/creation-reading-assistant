package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.TrendItem
import com.creationreadingassistant.ui.theme.MotionTokens
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
    val reducedMotion = rememberReducedMotion()
    val maxMs = items.maxOf { it.durationMs }.coerceAtLeast(1L)
    Column {
        // 柱区固定 110dp：柱顶数值(约16dp，大字号缩放约24dp) + 柱子(上限84dp) 必须放得下。
        // 日期标签拆到下方独立行——旧实现四者同列堆叠（最高约128dp > 110dp），
        // 长时长柱子会向下溢出压住日期（真机反馈）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEachIndexed { index, item ->
                // 柱子生长动效：错落延迟依次升起；reducedMotion 直接到位
                val targetH = max(4f, (item.durationMs.toFloat() / maxMs) * 84f)
                val grow = remember { Animatable(if (reducedMotion) 1f else 0f) }
                LaunchedEffect(item.durationMs, reducedMotion) {
                    if (reducedMotion) {
                        grow.snapTo(1f)
                    } else {
                        grow.snapTo(0f)
                        grow.animateTo(
                            1f,
                            tween(MotionTokens.Base, delayMillis = index * 28, easing = MotionTokens.StandardEasing),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // 柱顶数值：有阅读日显示分钟数，无阅读日留空（避免 0 噪音）
                    if (item.durationMs > 0L) {
                        Text(
                            "${item.durationMs / 60_000L}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    val h = max(4f, targetH * grow.value).dp
                    Box(
                        modifier = Modifier
                            .height(h)
                            .fillMaxWidth()
                            .clip(LocalComponentSpec.current.listItemShape)
                            // 无阅读日画成轨道色细条，与有数据的 primary 柱明确区分（不再是小蓝桩）
                            .background(
                                if (item.durationMs > 0L) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // 日期行独立于柱区：柱子再高也不会突破/覆盖日期。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
