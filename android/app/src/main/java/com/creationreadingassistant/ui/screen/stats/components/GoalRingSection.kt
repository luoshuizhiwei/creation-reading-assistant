package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlin.math.min

/**
 * 今日阅读目标微光达成环：
 * - 环形进度条微仪表盘，达成态微光绿环与对勾高光；
 * - 核心分钟数采用 Bold 展示字体，配进度完成百分比微胶囊；
 * - 状态信息卡片化排版，火苗连续打卡徽标。
 */
@Composable
internal fun GoalRingSection(
    todayMs: Long,
    goalMinutes: Int,
    streakDays: Int,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val scheme = MaterialTheme.colorScheme
    val goalMs = goalMinutes * 60_000L
    val rawFraction = if (goalMs > 0) todayMs.toFloat() / goalMs else 0f
    val achieved = goalMs > 0 && todayMs >= goalMs
    val fraction by animateFloatAsState(
        targetValue = rawFraction.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) tween(0) else tween(600),
        label = "goal-ring",
    )
    val trackColor = scheme.surfaceContainerHighest.copy(alpha = 0.45f)
    val ringColor = if (achieved) Color(0xFF059669) else scheme.primary
    val todayMinutes = (todayMs / 60000).toInt()
    val percentage = (fraction * 100).toInt()

    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-goal"),
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 环形进度微仪表盘
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(96.dp),
            ) {
                GoalRing(
                    fraction = fraction,
                    trackColor = trackColor,
                    ringColor = ringColor,
                    size = 96.dp,
                    stroke = 10.dp,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (achieved) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "达成",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF059669),
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            text = "$todayMinutes",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = scheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = "/ $goalMinutes 分",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 右侧目标与连续打卡信息微岛
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (achieved) "今日目标已达成" else "今日阅读目标",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (achieved) Color(0xFF059669).copy(alpha = 0.12f) else scheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, if (achieved) Color(0xFF059669).copy(alpha = 0.25f) else scheme.primary.copy(alpha = 0.25f)),
                    ) {
                        Text(
                            text = if (achieved) "已完成" else "$percentage%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (achieved) Color(0xFF059669) else scheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }

                GoalBadgePill(
                    icon = if (achieved) Icons.Outlined.TaskAlt else Icons.Outlined.Timer,
                    iconTint = if (achieved) Color(0xFF059669) else Color(0xFF2563EB),
                    text = if (achieved) {
                        "目标 $goalMinutes 分钟 · 翻页不止"
                    } else {
                        "目标 $goalMinutes 分钟 · 还需 ${(goalMinutes - todayMinutes).coerceAtLeast(1)} 分"
                    },
                )

                if (streakDays > 0) {
                    GoalBadgePill(
                        icon = Icons.Outlined.LocalFireDepartment,
                        iconTint = Color(0xFFEA580C),
                        text = "已连续阅读 $streakDays 天",
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalBadgePill(
    icon: ImageVector,
    iconTint: Color,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = iconTint,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalRing(
    fraction: Float,
    trackColor: Color,
    ringColor: Color,
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
