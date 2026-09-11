package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.ContinueReadingStore
import com.creationreadingassistant.feature.library.deletion.BookDeletionCoordinator
import com.creationreadingassistant.feature.library.deletion.DeletionUndoOutcome

/**
 * 书架书籍级写操作 —— 删除/恢复/搁置/信息编辑/封面/本地缓存清理。
 *
 * 纯逻辑类：不持有协程作用域，方法全部为 suspend 函数，由 ViewModel 负责 launch
 * 调度并包装回调；成功/失败以返回值（String 或 Result）表达，便于 JVM 单测。
 *
 * [deletions] 是可选的删除协调器：有它时删除会登记会话内撤销凭证，撤销走快照恢复
 * （资料、阅读数据、分类/标签/书单关联、已读章节、正文缓存一并回来）；没有它时退回
 * 仓储的按时间戳恢复，语义仍然正确，只是恢复不了硬删除的关联行。
 */
class ShelfBookActions(
    private val context: Context,
    private val repository: BookRepository,
    private val continueReadingStore: ContinueReadingStore,
    private val deletions: BookDeletionCoordinator? = null,
) {

    /** 删除单本资料；成功即已登记会话内撤销凭证（撤销提示由 offers 流驱动）。 */
    suspend fun deleteBook(id: String): Boolean = deleteBooks(listOf(id))

    /**
     * 批量删除：一次事务、一张凭证，撤销范围与入口提示的数量一致。
     *
     * 不走 `ids.forEach { deleteBook(it) }`——那会产生多张凭证，撤销只能恢复其中一批。
     * 返回值只表示删除本身成功与否，不表示凭证还在：凭证可能被新删除挤出上限，
     * 但那种情况下数据确实已经删掉了，报告失败会说谎。
     */
    suspend fun deleteBooks(ids: Collection<String>): Boolean {
        val coordinator = deletions
        if (coordinator == null) {
            // 无协调器的调用方（既有测试与未接入的入口）保持原委托语义。
            ids.distinct().forEach { repository.deleteBook(it) }
            return true
        }
        return coordinator.deleteBooks(ids) != null
    }

    /** 撤销一次删除；结果区分「已恢复」「让路给后续改动」「凭证已失效」。 */
    suspend fun undoDeletion(credentialId: String): DeletionUndoOutcome? =
        deletions?.undo(credentialId)

    fun dismissDeletionUndo(credentialId: String) {
        deletions?.dismiss(credentialId)
    }

    /** 撤销删除：恢复书籍及其关联数据。 */
    suspend fun restoreBook(id: String): String = try {
        repository.restoreBook(id)
        "已恢复书籍"
    } catch (e: Throwable) {
        "恢复失败：${e.message}"
    }

    suspend fun shelveBook(id: String): String = runCatching {
        repository.setReadingState(id, ReadingCompletionState.SHELVED)
        continueReadingStore.clear(id)
    }.fold(
        onSuccess = { "已搁置，阅读记录仍会保留" },
        onFailure = { "搁置失败：${it.message}" },
    )

    suspend fun restoreReading(id: String): Result<String> = runCatching {
        repository.setReadingState(id, ReadingCompletionState.READING)
        continueReadingStore.clear(id)
    }.map { "已恢复为在读" }

    /** 更新书名/作者/简介，返回结果消息。 */
    suspend fun updateBookInfo(
        bookId: String,
        title: String,
        author: String?,
        description: String? = null,
    ): String {
        if (title.isBlank()) return "书名不能为空"
        repository.updateBookInfo(
            bookId,
            title.trim(),
            author?.trim()?.takeIf { it.isNotBlank() },
            description?.trim()?.takeIf { it.isNotBlank() },
        )
        return "已更新书籍信息"
    }

    /** 更新封面（SAF Uri 字符串）。 */
    suspend fun updateBookCover(bookId: String, uri: Uri): String {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        repository.updateBookCover(bookId, uri.toString())
        return "已更新封面"
    }

    /** 设置文字封面（由前端生成的 SVG DataURL，对照网页「文字封面」）。 */
    suspend fun setBookTextCover(bookId: String, dataUrl: String): String {
        repository.updateBookCover(bookId, dataUrl)
        return "已生成文字封面"
    }

    /** 重置封面（清空封面图，回退为文字封面）。 */
    suspend fun resetBookCover(bookId: String): String {
        repository.updateBookCover(bookId, null)
        return "已重置封面"
    }

    /** 清理本地正文缓存（保留书架元数据），返回结果消息。 */
    suspend fun clearCacheForBooks(ids: List<String>): String {
        ids.forEach { repository.clearBookCache(it) }
        return "已清理 ${ids.size} 本书的本地正文缓存"
    }
}
