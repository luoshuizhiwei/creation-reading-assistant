package com.creationreadingassistant.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchIndexScheduler @Inject constructor(
    @ApplicationContext val appContext: Context,
) {

    /** 冷启动接线：入队一次增量扫描 + 周期扫描（见 App.onCreate）。 */
    fun start() {
        enqueueOnce()
        enqueuePeriodic()
    }

    /**
     * 入队一次全库增量扫描（`UNIQUE_ONCE_WORK`，10s 初始延迟）。
     *
     * 除冷启动外，**规则变更置脏后**也用它立即触发重建（P1）：替换规则变了，
     * 显示文通道的旧索引行即失真，需要 worker 从重置后的游标重扫。
     * [ExistingWorkPolicy.KEEP] 保证已排队/在跑的任务不会被重复插入。
     */
    fun enqueueOnce() {
        val oneTimeRequest = OneTimeWorkRequestBuilder<SearchIndexWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS)
            // 全库构建跨多个作业窗口完成（见 IndexSweepResult.PROGRESSED），续建重试必须是
            // **有界短退避**：默认指数退避会随次数膨胀（上限 5 小时），几十本书的场景会把
            // 总耗时拖到不可接受。线性 10s 足够让出调度器，又不会明显拖慢续建。
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .setConstraints(constraints())
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_ONCE_WORK,
            ExistingWorkPolicy.KEEP,
            oneTimeRequest,
        )
    }

    /**
     * 立即唤起一次全库扫描任务（零延迟）。
     *
     * @param force 为 true 时使用 [ExistingWorkPolicy.REPLACE] 替换可能在跑或排队的任务，
     *              用于用户显式点击「全量重建索引」；为 false 时使用 [ExistingWorkPolicy.KEEP]。
     */
    fun triggerNow(force: Boolean = false) {
        val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        val oneTimeRequest = OneTimeWorkRequestBuilder<SearchIndexWorker>()
            .setInitialDelay(0, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .setConstraints(constraints())
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_ONCE_WORK,
            policy,
            oneTimeRequest,
        )
    }

    private fun enqueuePeriodic() {
        val periodicRequest = PeriodicWorkRequestBuilder<SearchIndexWorker>(
            24, TimeUnit.HOURS,
        )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .setConstraints(constraints())
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )
    }

    private fun constraints(): Constraints =
        Constraints.Builder().setRequiresBatteryNotLow(false).build()

    companion object {
        const val UNIQUE_ONCE_WORK = "search_index_once_v1"
        const val UNIQUE_PERIODIC_WORK = "search_index_daily_v1"
    }
}
