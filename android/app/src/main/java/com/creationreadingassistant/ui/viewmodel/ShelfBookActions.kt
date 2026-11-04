package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.repository.BookCacheClearOutcome
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.MidwayAbortCause
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

    /**
     * 标记为已读完。
     *
     * 不清 `continueReadingStore`：读完的书仍可出现在「继续阅读」里回看，
     * 这与「搁置」不同——搁置是明确不再继续，读完是已完成的阅读行为。
     */
    suspend fun markFinished(id: String): String = runCatching {
        repository.markFinished(id)
    }.fold(
        onSuccess = { "已标记为读完" },
        onFailure = { "标记失败：${it.message}" },
    )

    /** 取消已读完，回到在读。清空 `completed_at` 由 `setReadingState` 负责。 */
    suspend fun markReading(id: String): String = runCatching {
        repository.setReadingState(id, ReadingCompletionState.READING)
    }.fold(
        onSuccess = { "已取消读完" },
        onFailure = { "取消失败：${it.message}" },
    )

    /** 批量标记读完。 */
    suspend fun markFinishedBatch(ids: Collection<String>): String = runCatching {
        ids.distinct().forEach { repository.markFinished(it) }
    }.fold(
        onSuccess = { "已将 ${ids.distinct().size} 本标记为读完" },
        onFailure = { "批量标记失败：${it.message}" },
    )

    /** 批量取消读完。 */
    suspend fun markReadingBatch(ids: Collection<String>): String = runCatching {
        ids.distinct().forEach { repository.setReadingState(it, ReadingCompletionState.READING) }
    }.fold(
        onSuccess = { "已取消 ${ids.distinct().size} 本的读完状态" },
        onFailure = { "批量取消失败：${it.message}" },
    )

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

    /**
     * 批量清理本地正文缓存：无残留的书照常清理；检测到替换/导入残留（可能是该书
     * 仅存正文）的书整本保留——文件与书架信息都不动，绝不把「保留备份」报成成功；
     * 删除中途被中止的书单独按「部分执行」说明；正文未删净的书（个别文件删除失败）
     * 单独说明，不与完整清理混同。返回给既有结果通道（书架 Snackbar）的消息逐项
     * 说明清理与保留情况。
     */
    suspend fun clearCacheForBooks(ids: List<String>): String {
        var cleared = 0
        var clearedWithRemains = 0
        var residueRefused = 0
        var abortedMidwayResidue = 0
        var abortedMidwayUnreadable = 0
        var unsafeRefused = 0
        ids.distinct().forEach { id ->
            when (val outcome = repository.clearBookCache(id)) {
                is BookCacheClearOutcome.Cleared -> cleared++
                BookCacheClearOutcome.ClearedWithRemains -> clearedWithRemains++
                is BookCacheClearOutcome.RefusedResidue -> residueRefused++
                BookCacheClearOutcome.RefusedUnsafeScan -> unsafeRefused++
                is BookCacheClearOutcome.AbortedMidway -> when (outcome.cause) {
                    MidwayAbortCause.RESIDUE_APPEARED -> abortedMidwayResidue++
                    MidwayAbortCause.LOCATION_UNREADABLE -> abortedMidwayUnreadable++
                }
            }
        }
        return buildCacheClearMessage(
            cleared,
            clearedWithRemains,
            residueRefused,
            abortedMidwayResidue,
            abortedMidwayUnreadable,
            unsafeRefused,
        )
    }

    private fun buildCacheClearMessage(
        cleared: Int,
        clearedWithRemains: Int,
        residueRefused: Int,
        abortedMidwayResidue: Int,
        abortedMidwayUnreadable: Int,
        unsafeRefused: Int,
    ): String {
        val parts = mutableListOf<String>()
        if (cleared > 0) parts += "已清理 ${cleared} 本书的本地正文缓存"
        if (clearedWithRemains > 0) {
            parts += "${clearedWithRemains} 本正文未删净：书架与数据库状态已更新，" +
                "但仍有正文文件留在本地（可能被系统占用），空间未完全释放"
        }
        if (residueRefused > 0) {
            parts += "${residueRefused} 本检测到未完成替换的残留备份，已整本保留：" +
                "该备份可能是该书仅存的正文，未删除也未自动恢复，书架信息未改动"
        }
        if (abortedMidwayResidue > 0) {
            parts += "${abortedMidwayResidue} 本清理中止：删除过程中发现新的替换残留，" +
                "备份与书架信息已保留（部分本地文件可能已被清理）"
        }
        if (abortedMidwayUnreadable > 0) {
            parts += "${abortedMidwayUnreadable} 本清理中止：过程中书籍目录变为不可读取，" +
                "已按保守策略停止并保留现场（部分本地文件可能已被清理，书架信息未改动）"
        }
        if (unsafeRefused > 0) {
            parts += "${unsafeRefused} 本无法确认本地文件状态，已保守跳过"
        }
        // 全部被拒/中止/未删净时不得使用无保留的成功字样；空列表保持与既有行为一致的计数文案。
        return if (parts.isEmpty()) "已清理 0 本书的本地正文缓存" else parts.joinToString("；")
    }
}
