package com.creationreadingassistant.feature.reader

import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import com.creationreadingassistant.feature.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EPUB size 修复的进程级一次性调度器。
 *
 * 此前每个 ShelfViewModel 实例 init 都会触发一次 [EpubRepository.repairMissingLocalFileSizes]，
 * 多页面各自创建实例时会重复执行。现在收敛为应用级懒启动任务：
 * - 生命周期挂在 [ApplicationScope]（= 进程生命周期），不随任何页面销毁取消；
 * - [AtomicBoolean] 保证进程内只执行一次，任何后续调用直接跳过。
 */
@Singleton
class EpubSizeRepairTask @Inject constructor(
    private val epubRepository: EpubRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)

    /** 进程内只会真正执行一次；重复调用安全跳过。 */
    fun startOnce() {
        if (!started.compareAndSet(false, true)) return
        applicationScope.launch {
            runCatching { epubRepository.repairMissingLocalFileSizes() }
                .onFailure { AppLog.w("EpubSizeRepair", "EPUB size repair failed: ${it.message}") }
            runCatching { epubRepository.backfillMissingEpubCovers() }
                .onFailure { AppLog.w("EpubSizeRepair", "EPUB cover backfill failed: ${it.message}") }
        }
    }
}
