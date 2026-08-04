package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.data.repository.SyncRepository
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * 单次同步运行的结果：结构化详情 + 展示消息 + 日志行（无时间戳，由 ViewModel 包装）。
 */
data class SyncRunOutcome(
    val result: SyncResultDetail,
    val message: String,
    val logs: List<String>,
)

/**
 * 「我的」页同步引擎 —— 局域网拉取 / 推送 / 正文下载、失败项重试、待下载统计。
 *
 * 纯逻辑类：不持有协程作用域，所有入口均为 suspend 函数，由调用方（ViewModel）
 * 负责 launch 调度与状态更新；因此可以在 JVM 测试中用 runTest 直接驱动。
 */
class ProfileSyncEngine(
    private val syncRepository: SyncRepository,
    private val configStore: SyncConfigStore,
    private val bookDao: BookDao,
) {

    /** 单次全量同步：pull → push →（可选）下载待同步正文，返回结果 + 展示消息 + 日志行。 */
    suspend fun runSync(autoDownloadBooks: Boolean): SyncRunOutcome {
        val startedAt = System.currentTimeMillis()
        val timestamp = Instant.now().toString()
        val allFailures = mutableListOf<SyncFailedItem>()
        var allConflicts = emptyList<SyncConflictItem>()
        var downloadedBookFiles = 0
        val logs = mutableListOf<String>()
        try {
            val pulled = syncRepository.pull()
            allFailures += pulled.failedItems.map { toUiFailedItem(it) }
            logs += "拉取完成：${pulled.books} 本 / ${pulled.inspirations} 条 / ${pulled.progress} 进度 / ${pulled.sessions} 会话"
            if (pulled.failedItems.isNotEmpty()) {
                logs += "  拉取失败 ${pulled.failedItems.size} 项"
            }

            val pushed = syncRepository.push()
            allFailures += pushed.failedItems.map { toUiFailedItem(it) }
            allConflicts = pushed.conflicts.map {
                SyncConflictItem(
                    type = it.type,
                    id = it.id,
                    title = it.title,
                    remoteUpdatedAt = it.remoteUpdatedAt,
                    localUpdatedAt = it.localUpdatedAt,
                    resolution = it.resolution,
                )
            }
            logs += "推送完成：${pushed.applied.books + pushed.applied.inspirations + pushed.applied.progress + pushed.applied.sessions} 条"
            if (allConflicts.isNotEmpty()) logs += "  冲突 ${allConflicts.size} 条（服务端保留）"

            if (autoDownloadBooks) {
                logs += "开始下载书籍正文…"
                val dl = syncRepository.downloadPendingBooks(maxCount = 20)
                downloadedBookFiles = dl.success
                allFailures += dl.failedItems.map { toUiFailedItem(it) }
                logs += "下载完成：${dl.success} 本成功，${dl.failed} 本失败"
            }

            val pending = countPendingDownloads()
            val duration = System.currentTimeMillis() - startedAt
            val result = SyncResultDetail(
                timestamp = timestamp,
                success = true,
                uploaded = SyncCountGroup(
                    inspirations = pushed.applied.inspirations,
                    books = pushed.applied.books,
                    progress = pushed.applied.progress,
                    sessions = pushed.applied.sessions,
                ),
                downloaded = SyncCountGroup(
                    inspirations = pulled.inspirations,
                    books = pulled.books,
                    progress = pulled.progress,
                    sessions = pulled.sessions,
                    bookFiles = downloadedBookFiles,
                ),
                pendingDownloadCount = pending,
                failedItems = allFailures,
                conflicts = allConflicts,
                durationMs = duration,
            )
            val message = "同步完成：拉取 ${pulled.books} 书 / ${pulled.inspirations} 灵感；" +
                "推送 ${pushed.applied.books + pushed.applied.inspirations} 条；" +
                "冲突 ${allConflicts.size}；下载 $downloadedBookFiles 本"
            return SyncRunOutcome(result = result, message = message, logs = logs)
        } catch (e: Throwable) {
            val msg = e.message ?: "未知错误"
            allFailures.add(SyncFailedItem(type = "sync", reason = msg))
            val result = SyncResultDetail(
                timestamp = timestamp,
                success = false,
                uploaded = SyncCountGroup(),
                downloaded = SyncCountGroup(),
                pendingDownloadCount = countPendingDownloads(),
                failedItems = allFailures,
                conflicts = allConflicts,
                durationMs = System.currentTimeMillis() - startedAt,
            )
            return SyncRunOutcome(
                result = result,
                message = "同步失败：$msg",
                logs = listOf("同步失败：$msg"),
            )
        }
    }

    /** 重试单本正文下载（book_file 类失败项）。 */
    suspend fun retryBookFile(bookId: String): Boolean =
        runCatching { syncRepository.downloadBookContent(bookId) }.isSuccess

    /** 待下载书籍数：活跃书籍中未下载（或内容缺失/失败/下载中）的数量。 */
    suspend fun countPendingDownloads(): Int {
        return runCatching {
            bookDao.observeAllActive().first().count { !isBookDownloaded(it) }
        }.getOrDefault(0)
    }

    /** 单本是否已下载（content_status=available 且有本地路径）。 */
    suspend fun isBookDownloadedById(id: String?): Boolean {
        if (id == null) return false
        val b = runCatching { bookDao.getById(id) }.getOrNull() ?: return false
        return b.content_status == "available" && (!b.local_content_path.isNullOrBlank() || !b.local_uri.isNullOrBlank())
    }

    fun toUiFailedItem(entry: SyncRepository.SyncFailedEntry): SyncFailedItem =
        SyncFailedItem(
            type = entry.type,
            bookId = if (entry.type == "book" || entry.type == "book_file") entry.id else null,
            title = entry.title,
            reason = entry.reason,
        )

    private fun isBookDownloaded(book: BookEntity): Boolean {
        if (book.content_status == "missing" || book.content_status == "failed" || book.content_status == "downloading") return false
        return !book.local_content_path.isNullOrBlank() || !book.local_uri.isNullOrBlank()
    }
}
