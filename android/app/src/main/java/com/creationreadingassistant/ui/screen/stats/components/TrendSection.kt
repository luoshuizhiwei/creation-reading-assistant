package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.components.IslandSectionHeader
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.TrendItem
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.MotionTokens
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max

/**
 * 阅读趋势微图表：
 * - 顶部 32dp 微彩底座图标与次数摘要胶囊；
 * - 柔和圆角柱体、生长动效、今日焦点高亮与峰值色彩层次；
 * - 底部清晰的日期标签与今日强调标识。
 */
@Composable
internal fun TrendSection(
    stats: StatsUi,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val spec = LocalComponentSpec.current
    val scheme = MaterialTheme.colorScheme

    IslandCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-trend"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 标题行：收敛到共享 IslandSectionHeader（32dp IconPedestal 底座）+ 令牌化摘要胶囊
            IslandSectionHeader(
                title = "阅读趋势",
                icon = Icons.Outlined.BarChart,
                tint = scheme.primary,
                trailing = {
                    Surface(
                        shape = PillShape,
                        color = scheme.surfaceContainerHigh.copy(alpha = 0.45f),
                        border = BorderStroke(
                            spec.hairlineBorderWidth,
                            scheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                        ),
                    ) {
                        Text(
                            text = if (stats.sessionCount > 0) "${stats.sessionCount} 次阅读" else "暂无数据",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                },
            )

            TrendChart(stats.trend)
        }
    }
}

@Composable
private fun TrendChart(items: List<TrendItem>) {
    val scheme = MaterialTheme.colorScheme
    if (items.isEmpty() || items.none { it.durationMs > 0L }) {
        Text(
            "开始阅读后，这里会显示每天的时长变化。",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = LocalLayoutTokens.current.microGap),
        )
        return
    }

    val reducedMotion = rememberReducedMotion()
    val maxMs = items.maxOf { it.durationMs }.coerceAtLeast(1L)
    val todayKey = remember {
        val now = LocalDate.now()
        String.format(Locale.ROOT, "%04d-%02d-%02d", now.year, now.monthValue, now.dayOfMonth)
    }

    Column {
        // 柱区固定 110dp：柱顶数值 + 圆角柱体
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEachIndexed { index, item ->
                val targetH = max(6f, (item.durationMs.toFloat() / maxMs) * 84f)
                val grow = remember { Animatable(if (reducedMotion) 1f else 0f) }
                LaunchedEffect(item.durationMs, reducedMotion) {
                    if (reducedMotion) {
                        grow.snapTo(1f)
                    } else {
                        grow.snapTo(0f)
                        grow.animateTo(
                            1f,
                            tween(MotionTokens.Base, delayMillis = index * 24, easing = MotionTokens.StandardEasing),
                        )
                    }
                }

                val hasData = item.durationMs > 0L
                val isToday = item.dateKey == todayKey
                val isPeak = hasData && item.durationMs == maxMs && items.count { it.durationMs > 0L } > 1

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // 柱顶数值：有阅读日显示分钟数，今日/峰值日高亮
                    if (hasData) {
                        Text(
                            text = "${item.durationMs / 60_000L}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isToday || isPeak) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp,
                            ),
                            color = when {
                                isToday -> scheme.primary
                                isPeak -> Color(0xFFD97706)
                                else -> scheme.onSurface
                            },
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    val h = if (hasData) max(8f, targetH * grow.value).dp else 3.dp
                    Box(
                        modifier = Modifier
                            .height(h)
                            .then(
                                if (hasData) Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                                else Modifier.width(8.dp)
                            )
                            .clip(
                                if (hasData) {
                                    RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
                                } else {
                                    RoundedCornerShape(1.5.dp)
                                },
                            )
                            .background(
                                if (hasData) {
                                    Brush.verticalGradient(
                                        when {
                                            isToday -> listOf(
                                                scheme.primary,
                                                scheme.primary.copy(alpha = 0.82f),
                                            )
                                            isPeak -> listOf(
                                                Color(0xFFD97706),
                                                Color(0xFFD97706).copy(alpha = 0.70f),
                                            )
                                            else -> listOf(
                                                scheme.primary.copy(alpha = 0.75f),
                                                scheme.primary.copy(alpha = 0.45f),
                                            )
                                        },
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            scheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                            scheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                        ),
                                    )
                                },
                            ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 日期行独立于柱区：今日焦点高亮突出
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                val isToday = item.dateKey == todayKey
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (isToday) "今天" else item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        ),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        color = if (isToday) scheme.primary else scheme.onSurfaceVariant,
                    )
                    if (isToday) {
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(scheme.primary),
                        )
                    }
                }
            }
        }
    }
}
