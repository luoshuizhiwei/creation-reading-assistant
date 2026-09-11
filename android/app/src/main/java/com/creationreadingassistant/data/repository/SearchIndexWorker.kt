package com.creationreadingassistant.data.repository

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.creationreadingassistant.SearchIndexEntryPoint
import com.creationreadingassistant.feature.log.AppLog
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException

class SearchIndexWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository: SearchIndexRepository = EntryPointAccessors.fromApplication<SearchIndexEntryPoint>(
                applicationContext,
                SearchIndexEntryPoint::class.java,
            ).repository()
            when (repository.ensureIndexedIncremental()) {
                IndexSweepResult.COMPLETED -> Result.success()

                // 预算耗尽但确有推进 → 重新入队续建（锚点已落盘，下次从下一本开始）。
                // 这一步是「全库能真正建完」的关键：一次作业窗口装不下整个书架。
                IndexSweepResult.PROGRESSED -> Result.retry()

                // 一本都没推进：单本超出一次作业窗口，重试只会空转。
                // 停止并留下可查告警；下次冷启动/周期任务仍会重新入队（KEEP 对已结束的 work 会插入新任务）。
                IndexSweepResult.STALLED -> {
                    AppLog.e(
                        "SearchIndex",
                        "单本书超出单次构建预算（${SearchIndexRepository.INDEX_BUILD_BUDGET_MS / 1000}s），" +
                            "构建停在上一本之后；需章节级续建才能继续。",
                    )
                    Result.failure()
                }
            }
        } catch (cancelled: CancellationException) {
            // WorkManager 停止任务时不得伪造失败或重新入队。
            throw cancelled
        } catch (error: Throwable) {
            // 索引有断点状态，重试会从最近已完成的锚点继续。
            // 旧实现无论何种异常都回 success，导致书架全文搜索悄悄停在陈旧索引。
            val action = searchIndexWorkerFailureAction(runAttemptCount)
            AppLog.e(
                "SearchIndex",
                "后台索引失败（第 ${runAttemptCount + 1} 次，${if (action == SearchIndexWorkerFailureAction.RETRY) "将重试" else "停止自动重试"}）：${error.message ?: error.javaClass.simpleName}",
            )
            when (action) {
                SearchIndexWorkerFailureAction.RETRY -> Result.retry()
                SearchIndexWorkerFailureAction.FAIL -> Result.failure()
            }
        }
    }
}
