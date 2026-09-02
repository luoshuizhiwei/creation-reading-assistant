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
            repository.ensureIndexedIncremental()
            Result.success()
        } catch (cancelled: CancellationException) {
            // WorkManager 停止任务时不得伪造失败或重新入队。
            throw cancelled
        } catch (error: Throwable) {
            // 索引有断点状态，重试会从最近已完成的 chunk 继续。
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
