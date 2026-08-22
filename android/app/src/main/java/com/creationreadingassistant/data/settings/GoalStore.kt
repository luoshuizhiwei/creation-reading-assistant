package com.creationreadingassistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 阅读目标偏好（P3.2 片 2，DataStore `"goal_prefs"`，按 [ImportHistoryStore] 惯例）。
 *
 * 语义：
 * - [dailyMinutes] 为 0 表示目标关闭（UI 全部隐藏，提醒不发）；
 * - [reminderMinuteOfDay] 为「当天第几分钟」（0-1439），默认 21:00。
 */
private val Context.goalDataStore by preferencesDataStore(name = "goal_prefs")

private val KEY_DAILY_MINUTES = intPreferencesKey("daily_minutes")
private val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
private val KEY_REMINDER_MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")

data class ReadingGoalPrefs(
    val dailyMinutes: Int = 0,
    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = GoalStore.DEFAULT_REMINDER_MINUTE_OF_DAY,
) {
    /** 目标是否处于开启状态（0 分钟 = 关闭）。 */
    val goalEnabled: Boolean get() = dailyMinutes > 0

    /** 提醒是否实际生效：需要目标开启且提醒开关打开。 */
    val reminderActive: Boolean get() = goalEnabled && reminderEnabled
}

@Singleton
class GoalStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val prefs: Flow<ReadingGoalPrefs> = context.goalDataStore.data.map { raw ->
        ReadingGoalPrefs(
            dailyMinutes = clampDailyMinutes(raw[KEY_DAILY_MINUTES] ?: 0),
            reminderEnabled = raw[KEY_REMINDER_ENABLED] ?: false,
            reminderMinuteOfDay = clampReminderMinute(raw[KEY_REMINDER_MINUTE_OF_DAY] ?: DEFAULT_REMINDER_MINUTE_OF_DAY),
        )
    }

    suspend fun setDailyMinutes(minutes: Int) {
        context.goalDataStore.edit { it[KEY_DAILY_MINUTES] = clampDailyMinutes(minutes) }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.goalDataStore.edit { it[KEY_REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderMinuteOfDay(minuteOfDay: Int) {
        context.goalDataStore.edit { it[KEY_REMINDER_MINUTE_OF_DAY] = clampReminderMinute(minuteOfDay) }
    }

    companion object {
        const val DEFAULT_REMINDER_MINUTE_OF_DAY: Int = 21 * 60

        /** 自定义目标范围 5–600 分钟；0 = 关闭；负数或超界收敛到关闭/上限。 */
        fun clampDailyMinutes(value: Int): Int = when {
            value <= 0 -> 0
            value > 600 -> 600
            else -> value
        }

        fun clampReminderMinute(value: Int): Int = value.coerceIn(0, 24 * 60 - 1)

        fun formatMinuteOfDay(minuteOfDay: Int): String {
            val m = clampReminderMinute(minuteOfDay)
            return "%02d:%02d".format(m / 60, m % 60)
        }
    }
}
