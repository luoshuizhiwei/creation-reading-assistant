package com.creationreadingassistant.feature.goal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.settings.GoalStore
import com.creationreadingassistant.data.settings.ReadingGoalPrefs
import com.creationreadingassistant.data.repository.StatsRepository
import com.creationreadingassistant.feature.log.AppLog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 提醒通知渠道（与 TTS 的 tts_playback 分开，IMPORTANCE_DEFAULT 有声提示）。 */
const val READING_GOAL_CHANNEL_ID = "reading_goal"

private const val GOAL_WORK_NAME = "reading_goal_reminder"
private const val GOAL_NOTIFICATION_ID = 2001

/**
 * 阅读目标提醒调度器（P3.2 片 3）。
 *
 * 职责严格收窄为「调度对账 + 到点检查发通知」：判定逻辑在 [decideGoalReminder]（纯函数），
 * 数据读取走 [GoalStore]/[StatsRepository]，Worker 只是薄壳入口。
 */
@Singleton
class ReadingGoalScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val goalStore: GoalStore,
    private val statsRepository: StatsRepository,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    /**
     * App 启动对账（App.onCreate 调用）：注册通知渠道 + 按当前偏好恢复/取消每日调度，
     * 防止系统清除或升级后 WorkManager 失联。
     */
    fun start() {
        ensureChannelRegistered(context)
        appScope.launch {
            runCatching { syncScheduling(goalStore.prefs.first()) }
                .onFailure { AppLog.e("GoalReminder", "启动对账失败: ${it.message}") }
        }
    }

    /** 按当前偏好对账调度：开启→每日周期任务（对齐提醒时刻）；关闭→取消。 */
    suspend fun syncScheduling(prefs: ReadingGoalPrefs) {
        val workManager = WorkManager.getInstance(context)
        if (!prefs.reminderActive) {
            workManager.cancelUniqueWork(GOAL_WORK_NAME)
            return
        }
        val initialDelay = delayUntilNextReminder(prefs.reminderMinuteOfDay)
        val request = PeriodicWorkRequestBuilder<ReadingGoalWorker>(1, java.util.concurrent.TimeUnit.DAYS)
            // Duration 重载要求 API 26（minSdk 24），改用毫秒重载
            .setInitialDelay(initialDelay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(GOAL_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Worker 到点执行：采集输入 → 纯函数判定 → 发通知或静默。返回判定结果供测试/日志。 */
    suspend fun sendReminderIfDue(): GoalReminderDecision = withContext(ioDispatcher) {
        val prefs = goalStore.prefs.first()
        val todayStart = statsReminderTodayStartEpochSecond()
        val tomorrowStart = statsReminderTodayStartEpochSecond(plusDays = 1)
        val todayMs = statsRepository.sumOccurredDurationBetween(
            todayStart, tomorrowStart, com.creationreadingassistant.data.repository.coarseLowerIso(todayStart),
        )
        val snapshot = ReadingGoalSnapshot(
            prefs = prefs,
            todayReadingMs = todayMs,
            notificationsGranted = areNotificationsGranted(),
            appInForeground = isAppInForeground(),
        )
        val decision = decideGoalReminder(snapshot)
        if (decision == GoalReminderDecision.NOTIFY) {
            runCatching { showReminderNotification(snapshot) }
                .onFailure { AppLog.e("GoalReminder", "通知发送失败: ${it.message}") }
        }
        AppLog.i("GoalReminder", "到点判定: $decision")
        decision
    }

    private fun statsReminderTodayStartEpochSecond(plusDays: Long = 0): Long =
        // 与 Home/Stats 聚合完全同源（同模块 internal，直接复用防公式漂移）
        com.creationreadingassistant.data.repository.startEpochSecondOf(LocalDate.now().plusDays(plusDays))

    private fun isAppInForeground(): Boolean {
        // 拿不到进程信息时按前台处理（宁可少打扰）；TTS 前台服务等同前台。
        val importance = processForegroundImportance() ?: return true
        return importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
    }

    private fun processForegroundImportance(): Int? = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        am?.runningAppProcesses?.firstOrNull { it.processName == context.packageName }?.importance
    }.getOrNull()

    private fun areNotificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showReminderNotification(snapshot: ReadingGoalSnapshot) {
        // 双保险：判定后到发送前权限可能被系统收回，发送前再核一次（也让 lint 权限检查可见）
        if (!areNotificationsGranted()) return
        ensureChannelRegistered(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            GOAL_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, READING_GOAL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(goalReminderTitle(snapshot))
            .setContentText(goalReminderBody(snapshot))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(false)
            .build()
        val manager = NotificationManagerCompat.from(context)
        // 发送前三检（同方法内联以显式满足权限契约）：运行时权限 + 渠道总开关
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(GOAL_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            AppLog.e("GoalReminder", "通知提交失败: ${e.message}")
        }
    }

    companion object {
        /** App 启动时注册渠道（IMPORTANCE_DEFAULT；调用幂等）。 */
        fun ensureChannelRegistered(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(READING_GOAL_CHANNEL_ID)
            if (existing != null) return
            val channel = NotificationChannel(
                READING_GOAL_CHANNEL_ID,
                "阅读目标提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "每日固定时刻的阅读目标完成提醒" }
            manager.createNotificationChannel(channel)
        }

        /** 距下一个提醒时刻的时长（本地时区；今天未到→今天，已过→明天）。 */
        fun delayUntilNextReminder(minuteOfDay: Int, now: LocalDateTime = LocalDateTime.now()): Duration {
            val target = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            val todayTarget = now.toLocalDate().atTime(target)
            return if (now < todayTarget) Duration.between(now, todayTarget) else Duration.between(now, todayTarget.plusDays(1))
        }
    }
}

/** Worker 薄壳：不做业务，转交 [ReadingGoalScheduler]（无 hilt-work 依赖，EntryPoint 取实例）。 */
class ReadingGoalWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters,
) : androidx.work.CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val scheduler = EntryPointAccessors.fromApplication(applicationContext, GoalSchedulerEntryPoint::class.java)
            .scheduler()
        return try {
            scheduler.sendReminderIfDue()
            Result.success()
        } catch (t: Throwable) {
            AppLog.e("GoalReminder", "Worker 执行失败: ${t.message}")
            Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GoalSchedulerEntryPoint {
    fun scheduler(): ReadingGoalScheduler
}
