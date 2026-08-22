package com.creationreadingassistant.ui.screen.profile

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.settings.GoalStore
import com.creationreadingassistant.ui.components.NotificationPermission
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Timer

/** 预设档位；自定义走 5–600 分钟步进（P3.2 设计 §片 2）。 */
private val GOAL_PRESETS = listOf(15, 30, 60, 90)

// ============================== 阅读目标页 ==============================

@Composable
internal fun GoalSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val goal = state.goal
    val context = LocalContext.current

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize().testTag("goal-subpage"),
    ) {
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Text(
                    "每日阅读目标",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).testTag("goal-section-title"),
                )
                Text(
                    "达成当天，统计页进度环满格；目标关闭时进度环与提醒全部隐藏。当前已连续阅读 ${goal.streakDays} 天，今天已读 ${goal.todayMinutes} 分钟。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp),
                )
                GoalPresetRow(
                    selected = goal.dailyMinutes,
                    onSelect = { onAction(ProfileAction.UpdateGoalMinutes(it)) },
                )
                // 自定义档（非预设值或显式调整）
                SettingRow(
                    title = "自定义时长",
                    subtitle = "5–600 分钟，步进 5",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onAction(ProfileAction.UpdateGoalMinutes((goal.dailyMinutes - 5).coerceAtLeast(if (goal.dailyMinutes > 5) 5 else 0))) },
                                enabled = goal.dailyMinutes > 0,
                            ) { Icon(Icons.Outlined.Remove, contentDescription = "减少 5 分钟") }
                            Text(
                                if (goal.dailyMinutes == 0) "关闭" else "${goal.dailyMinutes} 分钟",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.testTag("goal-custom-minutes"),
                            )
                            IconButton(
                                onClick = { onAction(ProfileAction.UpdateGoalMinutes((if (goal.dailyMinutes == 0) 5 else goal.dailyMinutes + 5).coerceAtMost(600))) },
                                enabled = goal.dailyMinutes < 600,
                            ) { Icon(Icons.Outlined.Add, contentDescription = "增加 5 分钟") }
                        }
                    },
                )
            }
        }
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion).testTag("goal-reminder-card")) {
                Text(
                    "每日提醒",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    "到点若目标未达成且未在阅读，发一条通知提醒。需要目标开启且授予通知权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp),
                )
                SettingRow(
                    title = "开启提醒",
                    subtitle = if (!NotificationPermission.isGranted(context) && goal.reminderEnabled) {
                        "未授予通知权限——点击开关旁的申请或到系统设置开启"
                    } else {
                        "每日固定时刻检查一次"
                    },
                    trailing = {
                        Switch(
                            checked = goal.reminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    (context as? Activity)?.let {
                                        NotificationPermission.requestIfNeeded(it, NotificationPermission.REQUEST_GOAL)
                                    }
                                }
                                onAction(ProfileAction.UpdateGoalReminderEnabled(enabled))
                            },
                            enabled = goal.goalEnabled,
                        )
                    },
                )
                SettingRow(
                    title = "提醒时刻",
                    subtitle = GoalStore.formatMinuteOfDay(goal.reminderMinuteOfDay),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onAction(ProfileAction.UpdateGoalReminderMinuteOfDay(goal.reminderMinuteOfDay - 30)) },
                                enabled = goal.reminderMinuteOfDay > 0,
                            ) { Icon(Icons.Outlined.Remove, contentDescription = "提前 30 分钟") }
                            IconButton(
                                onClick = { onAction(ProfileAction.UpdateGoalReminderMinuteOfDay(goal.reminderMinuteOfDay + 30)) },
                                enabled = goal.reminderMinuteOfDay < 24 * 60 - 1,
                            ) { Icon(Icons.Outlined.Add, contentDescription = "推迟 30 分钟") }
                        }
                    },
                )
            }
        }
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                SettingRow(
                    title = "连续阅读",
                    subtitle = if (goal.streakDays > 0) "已连续 ${goal.streakDays} 天" else "今天开始连续打卡",
                    leading = {
                        Icon(
                            Icons.Outlined.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun GoalPresetRow(selected: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        GOAL_PRESETS.forEach { minutes ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == minutes,
                    onClick = { onSelect(minutes) },
                )
                Text(
                    "$minutes 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
