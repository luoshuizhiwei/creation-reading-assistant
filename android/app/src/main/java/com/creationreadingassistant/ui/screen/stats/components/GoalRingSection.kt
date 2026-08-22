package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlin.math.min

/**
 * 今日阅读目标进度环（P3.2 片 2）。
 *
 * - 输入全部来自 [com.creationreadingassistant.ui.screen.stats.GoalUi]（VM 派生，Screen 零计算）；
 * - 达成态：满环 + 「已完成」文案与对勾图标，颜色走 accent；
 * - 目标关闭（GoalUi == null）时该区块整体不组合。
 */
@Composable
internal fun GoalRingSection(
    todayMs: Long,
    goalMinutes: Int,
    streakDays: Int,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val goalMs = goalMinutes * 60_000L
    val rawFraction = if (goalMs > 0) todayMs.toFloat() / goalMs else 0f
    val achieved = goalMs > 0 && todayMs >= goalMs
    val fraction by animateFloatAsState(
        targetValue = rawFraction.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) tween(0) else tween(600),
        label = "goal-ring",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val ringColor = MaterialTheme.colorScheme.primary
    val todayMinutes = (todayMs / 60000).toInt()

    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-goal"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                GoalRing(
                    fraction = fraction,
                    trackColor = trackColor,
                    ringColor = ringColor,
                    size = 92.dp,
                    stroke = 10.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (achieved) "达成" else "$todayMinutes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (!achieved) {
                        Text(
                            text = "/ $goalMinutes 分",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (achieved) "今日目标已完成" else "今日阅读目标",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                GoalBadgeRow(
                    icon = if (achieved) Icons.Outlined.TaskAlt else Icons.Outlined.LocalFireDepartment,
                    text = if (achieved) "目标 $goalMinutes 分钟 · 继续保持" else "目标 $goalMinutes 分钟",
                )
                if (streakDays > 0) {
                    GoalBadgeRow(
                        icon = Icons.Outlined.LocalFireDepartment,
                        text = "已连续阅读 $streakDays 天",
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalBadgeRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun GoalRing(
    fraction: Float,
    trackColor: androidx.compose.ui.graphics.Color,
    ringColor: androidx.compose.ui.graphics.Color,
    size: Dp,
    stroke: Dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val strokePx = stroke.toPx()
        val inset = strokePx / 2
        val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
        if (fraction > 0f) {
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = min(fraction, 1f) * 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}
