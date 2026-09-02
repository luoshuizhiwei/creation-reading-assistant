package com.creationreadingassistant.data.repository

/** 索引失败的 WorkManager 处置，独立于 Android Worker 以便 JVM 覆盖。 */
internal enum class SearchIndexWorkerFailureAction {
    RETRY,
    FAIL,
}

private const val SEARCH_INDEX_MAX_ATTEMPTS = 3

/** [runAttemptCount] 从 0 开始，单次调度最多执行三次。 */
internal fun searchIndexWorkerFailureAction(runAttemptCount: Int): SearchIndexWorkerFailureAction =
    if (runAttemptCount + 1 < SEARCH_INDEX_MAX_ATTEMPTS) {
        SearchIndexWorkerFailureAction.RETRY
    } else {
        SearchIndexWorkerFailureAction.FAIL
    }
