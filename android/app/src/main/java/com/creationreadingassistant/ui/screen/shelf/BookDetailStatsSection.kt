package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.LocalDate

@Composable
internal fun BookDetailStatsSection(
    progress: ReadingProgressEntity?,
    sessions: List<ReadingSessionEntity>,
    percent: Float,
) {
    DetailIslandCard {
        SectionTitle("阅读统计")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BookDetailStatCell(
                    icon = Icons.Outlined.AccessTime,
                    label = "总阅读时长",
                    value = formatDuration(bookDetailReadingTimeMs(progress?.total_reading_time_ms ?: 0L, sessions)),
                    tint = Color(0xFFB45309),
                    modifier = Modifier.weight(1f),
                )
                BookDetailStatCell(
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    label = "阅读进度",
                    value = "${"%.1f".format(percent)}%",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BookDetailStatCell(
                    icon = Icons.Outlined.History,
                    label = "阅读次数",
                    value = "${sessions.size} 次",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f),
                )
                BookDetailStatCell(
                    icon = Icons.Outlined.CalendarToday,
                    label = "上次阅读",
                    value = if (progress?.last_read_at != null) progress.last_read_at.take(10) else "从未阅读",
                    tint = Color(0xFF059669),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        ReadingHistoryChart(sessions = sessions)
    }
}

@Composable
internal fun BookDetailSessionsSection(sessions: List<ReadingSessionEntity>) {
    val reducedMotion = rememberReducedMotion()
    val sortedSessions = remember(sessions) {
        sessions.sortedByDescending { it.started_at ?: it.created_at ?: "" }
    }
    var expandedAll by remember { mutableStateOf(false) }

    DetailIslandCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            SectionTitle("阅读记录", modifier = Modifier.weight(1f))
            if (sortedSessions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                ) {
                    Text(
                        "共 ${sortedSessions.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (sortedSessions.isEmpty()) {
            Text(
                "还没有阅读记录。开始阅读后，这里会显示每次阅读。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            val displaySessions = if (expandedAll) sortedSessions else sortedSessions.take(3)
            val todayStr = remember { LocalDate.now().toString() }
            val yesterdayStr = remember { LocalDate.now().minusDays(1).toString() }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = if (reducedMotion) snap() else tween(
                            durationMillis = 280,
                            easing = FastOutSlowInEasing,
                        ),
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                displaySessions.forEach { s ->
                    SessionMicroIsland(
                        session = s,
                        todayStr = todayStr,
                        yesterdayStr = yesterdayStr,
                    )
                }
            }

            if (sortedSessions.size > 3) {
                Spacer(modifier = Modifier.height(10.dp))
                val haptic = rememberHaptic(reducedMotion)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            expandedAll = !expandedAll
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (expandedAll) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (!expandedAll) "展开更多记录 (共 ${sortedSessions.size} 条)" else "收起阅读记录",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionMicroIsland(
    session: ReadingSessionEntity,
    todayStr: String,
    yesterdayStr: String,
) {
    val reducedMotion = rememberReducedMotion()
    var itemExpanded by remember { mutableStateOf(false) }
    val rawTime = (session.started_at ?: session.created_at ?: "未知时间").take(16).replace("T", " ")
    val datePart = rawTime.take(10)
    val timePart = if (rawTime.length >= 16) rawTime.substring(11, 16) else ""
    val dateLabel = when (datePart) {
        todayStr -> "今天"
        yesterdayStr -> "昨天"
        else -> if (datePart.length >= 10) datePart.substring(5) else datePart
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { itemExpanded = !itemExpanded },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .animateContentSize(animationSpec = if (reducedMotion) snap() else tween()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "$dateLabel $timePart".trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFB45309).copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, Color(0xFFB45309).copy(alpha = 0.20f)),
                ) {
                    Text(
                        text = formatDuration(session.duration_ms),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                ) {
                    Text(
                        text = "进度 ${(session.progress_percent ?: 0f).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    if (itemExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.size(16.dp),
                )
            }

            if (itemExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        InfoRow("开始时间", (session.started_at ?: "-").take(19).replace("T", " "))
                        InfoRow("结束时间", (session.ended_at ?: "-").take(19).replace("T", " "))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReadingHistoryChart(sessions: List<ReadingSessionEntity>) {
    if (sessions.isEmpty()) {
        Text("暂无阅读记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val dayLabel = java.time.format.DateTimeFormatter.ofPattern("M/d")
    val now = LocalDate.now()
    val days = (6 downTo 0).map { now.minusDays(it.toLong()) }
    val durationsByDay = sessions.groupBy { session ->
        val instant = runCatching { java.time.Instant.parse(session.created_at ?: session.started_at) }.getOrNull()
        instant?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()?.toString() ?: ""
    }.mapValues { entry -> entry.value.sumOf { it.duration_ms } }
    val maxDuration = durationsByDay.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val key = day.toString()
            val duration = durationsByDay[key] ?: 0L
            val fraction = if (maxDuration > 0) (duration.toFloat() / maxDuration).coerceIn(0f, 1f) else 0f
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(0.08f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        ),
                                    ),
                                ),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 8.dp, height = 3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(dayLabel.format(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BookDetailStatCell(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.07f),
        border = BorderStroke(0.8.dp, tint.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
