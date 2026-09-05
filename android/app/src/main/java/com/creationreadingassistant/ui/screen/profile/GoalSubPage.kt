package com.creationreadingassistant.ui.screen.profile

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.GoalStore
import com.creationreadingassistant.ui.components.NotificationPermission
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/** 预设档位：15, 30, 45, 60, 90 分钟 */
private val GOAL_PRESETS = listOf(15, 30, 45, 60, 90)

// ============================== 阅读目标大屏页 ==============================

@Composable
internal fun GoalSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val goal = state.goal
    val context = LocalContext.current
    val haptic = rememberHaptic(reducedMotion)

    val targetMinutes = goal.dailyMinutes
    val todayMinutes = goal.todayMinutes
    val isEnabled = goal.goalEnabled
    val isReached = isEnabled && todayMinutes >= targetMinutes
    val progressFraction = if (isEnabled) {
        (todayMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f)
    } else 0f
    val progressPercent = if (isEnabled) {
        ((todayMinutes.toFloat() / targetMinutes) * 100).toInt()
    } else 0

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier
            .fillMaxSize()
            .testTag("goal-subpage"),
    ) {
        // 1. 目标进度大屏微岛：年度/月度/每日目标卡片配备 36dp 独立微彩底座与渐变微光环
        item(key = "goal-hero-card") {
            val ringBorderColor = if (isReached) {
                Color(0xFF10B981) // 墨绿
            } else if (isEnabled) {
                Color(0xFFF59E0B) // 暖金
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateEnter(reducedMotion = reducedMotion)
                    // 外层渐变柔光微光环
                    .then(
                        if (isEnabled) {
                            Modifier.border(
                                width = 1.5.dp,
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        ringBorderColor.copy(alpha = 0.45f),
                                        ringBorderColor.copy(alpha = 0.08f),
                                    ),
                                ),
                                shape = RoundedCornerShape(20.dp),
                            )
                        } else Modifier,
                    )
                    .padding(if (isEnabled) 1.5.dp else 0.dp),
            ) {
                SectionCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // 标题栏：36dp 独立微彩底座 + 标题 + 达成率微徽章
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isReached) Color(0xFF10B981).copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (isReached) Icons.Outlined.CheckCircle else Icons.Outlined.Flag,
                                        contentDescription = null,
                                        tint = if (isReached) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column {
                                    Text(
                                        "每日阅读目标",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("goal-section-title"),
                                    )
                                    Text(
                                        if (!isEnabled) "未设定目标 · 点击下方档位开启"
                                        else if (isReached) "今日目标已达成，阅读习惯非常棒！"
                                        else "距离目标还差 ${(targetMinutes - todayMinutes).coerceAtLeast(0)} 分钟",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // 达成率微徽章（墨绿/暖金胶囊）
                            if (isEnabled) {
                                Surface(
                                    shape = PillShape,
                                    color = if (isReached) Color(0xFF10B981).copy(alpha = 0.15f)
                                    else Color(0xFFF59E0B).copy(alpha = 0.15f),
                                    border = BorderStroke(
                                        0.8.dp,
                                        if (isReached) Color(0xFF10B981).copy(alpha = 0.4f)
                                        else Color(0xFFF59E0B).copy(alpha = 0.4f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isReached) Color(0xFF10B981) else Color(0xFFF59E0B)),
                                        )
                                        Text(
                                            if (isReached) "已达标" else "达成 $progressPercent%",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isReached) Color(0xFF047857) else Color(0xFFB45309),
                                        )
                                    }
                                }
                            }
                        }

                        // 3 列独立微岛指标矩阵（每日目标、今日已读、连续打卡）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GoalStatMicroCard(
                                icon = Icons.Outlined.Flag,
                                iconTint = MaterialTheme.colorScheme.primary,
                                value = if (isEnabled) "$targetMinutes" else "--",
                                unit = "分钟",
                                label = "每日目标",
                                modifier = Modifier.weight(1f),
                            )
                            GoalStatMicroCard(
                                icon = Icons.Outlined.Timer,
                                iconTint = if (isReached) Color(0xFF10B981) else Color(0xFF0284C7),
                                value = "$todayMinutes",
                                unit = "分钟",
                                label = "今日已读",
                                badgeText = if (isEnabled) "$progressPercent%" else null,
                                badgeColor = if (isReached) Color(0xFF10B981) else Color(0xFF0284C7),
                                modifier = Modifier.weight(1f),
                            )
                            GoalStatMicroCard(
                                icon = Icons.Outlined.LocalFireDepartment,
                                iconTint = Color(0xFFE65100),
                                value = "${goal.streakDays}",
                                unit = "天",
                                label = "连续打卡",
                                badgeText = if (goal.streakDays > 0) "坚持中" else null,
                                badgeColor = Color(0xFFE65100),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // 平滑微导轨进度条
                        if (isEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(PillShape),
                                    color = if (isReached) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "今日进度：$todayMinutes / $targetMinutes 分钟",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        if (isReached) "已完成今日阅读额度 ✓" else "还差 ${targetMinutes - todayMinutes} 分钟",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = if (isReached) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        SectionDivider()

                        // 预设档位微胶囊导轨
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "预设目标档位",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SelectablePill(
                                    text = "关闭",
                                    selected = targetMinutes == 0,
                                    onClick = {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        onAction(ProfileAction.UpdateGoalMinutes(0))
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                GOAL_PRESETS.forEach { minutes ->
                                    SelectablePill(
                                        text = "$minutes 分",
                                        selected = targetMinutes == minutes,
                                        onClick = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            onAction(ProfileAction.UpdateGoalMinutes(minutes))
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        // 目标微调滑块/步进按钮升级为圆润微胶囊导轨
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "精细调节与滑块导轨",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // 步进微胶囊导轨
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            val next = (targetMinutes - 5).coerceAtLeast(0)
                                            onAction(ProfileAction.UpdateGoalMinutes(next))
                                        },
                                        enabled = targetMinutes > 0,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Remove,
                                            contentDescription = "减少 5 分钟",
                                            tint = if (targetMinutes > 0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        )
                                    }

                                    // 中央当前数值微胶囊
                                    Surface(
                                        shape = PillShape,
                                        color = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                        border = BorderStroke(
                                            0.8.dp,
                                            if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        ),
                                    ) {
                                        Text(
                                            text = if (targetMinutes == 0) "已关闭" else "$targetMinutes 分钟",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isEnabled) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(horizontal = 18.dp, vertical = 6.dp)
                                                .testTag("goal-custom-minutes"),
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            val next = (if (targetMinutes == 0) 5 else targetMinutes + 5).coerceAtMost(600)
                                            onAction(ProfileAction.UpdateGoalMinutes(next))
                                        },
                                        enabled = targetMinutes < 600,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Add,
                                            contentDescription = "增加 5 分钟",
                                            tint = if (targetMinutes < 600) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        )
                                    }
                                }
                            }

                            // 连续滑块微导轨
                            Slider(
                                value = targetMinutes.toFloat(),
                                onValueChange = {
                                    val stepped = (it / 5).toInt() * 5
                                    onAction(ProfileAction.UpdateGoalMinutes(stepped))
                                },
                                valueRange = 0f..180f,
                                steps = 35,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("0 分钟 (关闭)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("90 分钟", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("180 分钟", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // 2. 每日提醒微岛
        item(key = "goal-reminder-card") {
            SectionCard(
                modifier = Modifier
                    .animateEnter(reducedMotion = reducedMotion)
                    .testTag("goal-reminder-card"),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF6366F1).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint = Color(0xFF6366F1),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    "每日定时提醒",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "到点若目标未达成且未在阅读，发送一条温和通知",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Switch(
                            checked = goal.reminderEnabled,
                            onCheckedChange = { enabled ->
                                haptic(HapticFeedbackType.TextHandleMove)
                                if (enabled) {
                                    (context as? Activity)?.let {
                                        NotificationPermission.requestIfNeeded(it, NotificationPermission.REQUEST_GOAL)
                                    }
                                }
                                onAction(ProfileAction.UpdateGoalReminderEnabled(enabled))
                            },
                            enabled = isEnabled,
                        )
                    }

                    // 权限未授予警告微岛
                    val hasPerm = NotificationPermission.isGranted(context)
                    if (!hasPerm && goal.reminderEnabled) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    "系统未授予通知权限，提醒可能无法弹出",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                (context as? Activity)?.let { act ->
                                    TextButton(
                                        onClick = {
                                            NotificationPermission.requestIfNeeded(act, NotificationPermission.REQUEST_GOAL)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text("去授权", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    if (goal.reminderEnabled) {
                        SectionDivider()

                        // 提醒时刻调节胶囊导轨
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    "提醒时刻",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "每日固定时刻检查一次目标达成情况",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            // 步进微胶囊
                            Surface(
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            onAction(ProfileAction.UpdateGoalReminderMinuteOfDay(goal.reminderMinuteOfDay - 30))
                                        },
                                        enabled = goal.reminderMinuteOfDay > 0,
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(Icons.Outlined.Remove, contentDescription = "提前 30 分钟", modifier = Modifier.size(16.dp))
                                    }

                                    Text(
                                        text = GoalStore.formatMinuteOfDay(goal.reminderMinuteOfDay),
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    )

                                    IconButton(
                                        onClick = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            onAction(ProfileAction.UpdateGoalReminderMinuteOfDay(goal.reminderMinuteOfDay + 30))
                                        },
                                        enabled = goal.reminderMinuteOfDay < 24 * 60 - 1,
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(Icons.Outlined.Add, contentDescription = "推迟 30 分钟", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. 打卡与成就激励微岛
        item(key = "goal-streak-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE65100).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "连续阅读徽章",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Surface(
                                shape = PillShape,
                                color = if (goal.streakDays > 0) Color(0xFF10B981).copy(alpha = 0.12f)
                                else Color(0xFFF59E0B).copy(alpha = 0.12f),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (goal.streakDays > 0) Color(0xFF10B981).copy(alpha = 0.35f)
                                    else Color(0xFFF59E0B).copy(alpha = 0.35f),
                                ),
                            ) {
                                Text(
                                    if (goal.streakDays > 0) "已连续 ${goal.streakDays} 天" else "今日待打卡",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (goal.streakDays > 0) Color(0xFF047857) else Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (goal.streakDays > 0) "坚持每天读一点，书籍将在时光中积淀为深刻见解。"
                            else "今天开始打卡，达成每日目标即可点亮连续阅读勋章。",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 目标指标微岛卡片：包含 36dp 独立微彩底座、分色大数字与标签
 */
@Composable
private fun GoalStatMicroCard(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    unit: String,
    label: String,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 36dp 独立微彩底座
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }

            // 大数字 + 单位
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }

            // 标签
            Text(
                label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 徽章
            if (badgeText != null) {
                Surface(
                    shape = PillShape,
                    color = badgeColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, badgeColor.copy(alpha = 0.3f)),
                ) {
                    Text(
                        badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}
