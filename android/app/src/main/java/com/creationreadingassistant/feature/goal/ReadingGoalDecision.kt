package com.creationreadingassistant.feature.goal

import com.creationreadingassistant.data.settings.ReadingGoalPrefs

/**
 * 阅读提醒判定（P3.2 片 3）——纯函数，Worker 只做「采集输入 → 判定 → 发通知/静默」。
 *
 * 判定输入全部显式传入，保证三分支（发/不发原因）可在 JVM 直接测试。
 */
data class ReadingGoalSnapshot(
    val prefs: ReadingGoalPrefs,
    val todayReadingMs: Long,
    val notificationsGranted: Boolean,
    val appInForeground: Boolean,
)

enum class GoalReminderDecision {
    /** 发送提醒。 */
    NOTIFY,
    /** 目标或提醒开关关闭。 */
    SKIP_DISABLED,
    /** 今日目标已达成。 */
    SKIP_ACHIEVED,
    /** 没有通知权限（Android 13+ 未授权）。 */
    SKIP_NO_PERMISSION,
    /** 应用正在前台（正在用，不打扰）。 */
    SKIP_FOREGROUND,
}

fun decideGoalReminder(snapshot: ReadingGoalSnapshot): GoalReminderDecision {
    if (!snapshot.prefs.reminderActive) return GoalReminderDecision.SKIP_DISABLED
    if (!snapshot.notificationsGranted) return GoalReminderDecision.SKIP_NO_PERMISSION
    if (snapshot.appInForeground) return GoalReminderDecision.SKIP_FOREGROUND
    val goalMs = snapshot.prefs.dailyMinutes * 60_000L
    if (goalMs > 0 && snapshot.todayReadingMs >= goalMs) return GoalReminderDecision.SKIP_ACHIEVED
    return GoalReminderDecision.NOTIFY
}

/** 通知标题/正文的文案计算（纯函数，便于测试）。 */
fun goalReminderTitle(snapshot: ReadingGoalSnapshot): String {
    val remainingMinutes = remainingMinutesOf(snapshot)
    return if (remainingMinutes > 0) "今天的阅读目标还差 $remainingMinutes 分钟" else "今天的阅读目标还没开始"
}

fun goalReminderBody(snapshot: ReadingGoalSnapshot): String =
    "目标 ${snapshot.prefs.dailyMinutes} 分钟 · 打开书架继续阅读"

fun remainingMinutesOf(snapshot: ReadingGoalSnapshot): Int {
    val goalMs = snapshot.prefs.dailyMinutes * 60_000L
    if (goalMs <= 0) return 0
    val remaining = goalMs - snapshot.todayReadingMs
    // 不足一分钟按一分钟计（0 < 差额 < 60s 时提示 1 分钟，避免「还差 0 分钟」）
    return ((remaining + 59_999L) / 60_000L).coerceAtLeast(0L).toInt()
}
