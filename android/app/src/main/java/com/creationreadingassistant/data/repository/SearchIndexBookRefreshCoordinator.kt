package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import com.creationreadingassistant.feature.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 异步逐书搜索索引刷新协调器（S04-2 接缝）。
 *
 * 职责单一：接收一个 bookId，在 [ApplicationScope] 中异步启动单本书的索引重建任务。
 * 调用方提交请求后立即返回，不同步等待重建完成。
 *
 * 设计要点：
 * - 不依赖任何 UI 层类；位于 data.repository 层，可被 feature 层（如 ShelfImporter）注入。
 * - 不复制 [SearchIndexRepository.reindexBookById] 的实现，仅委托调用。
 * - 索引失败只写入 [AppLog]，不向调用方抛出，也不影响已完成的正文刷新。
 * - 使用已注入的 [ApplicationScope]（生命周期 = 进程生命周期，含 SupervisorJob），
 *   协程取消（[CancellationException]）原样传播，不伪装成普通失败。
 * - 不使用 GlobalScope，不创建无生命周期的私有 CoroutineScope。
 *
 * 后续可复用路径（本轮不实现）：
 * - S03-3：BookRepository 撤销删除后，通过本协调器触发被恢复书的索引重建。
 */
@Singleton
class SearchIndexBookRefreshCoordinator @Inject constructor(
    private val searchIndexRepository: SearchIndexRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    /**
     * 向 [applicationScope] 提交一次异步索引重建请求，立即返回。
     *
     * - 若 [searchIndexRepository] 抛出普通异常，将写入 [AppLog] 并静默吞掉，
     *   不向调用方抛出，不取消整个 [applicationScope]（SupervisorJob 已隔离子协程失败）。
     * - 若协程被结构化取消（[CancellationException]），按 Kotlin 协程规范原样重新抛出，
     *   不伪装成普通失败。
     * - 同一 bookId 每次调用都会启动一个新的重建协程；调用方自行保证单次成功只提交一次。
     *
     * @param bookId 需要重建搜索索引的书籍 ID。
     */
    fun request(bookId: String) {
        applicationScope.launch {
            try {
                searchIndexRepository.reindexBookById(bookId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(
                    "SearchIndexBookRefresh",
                    "异步索引重建失败（bookId=$bookId）：${e.message}",
                )
            }
        }
    }
}
