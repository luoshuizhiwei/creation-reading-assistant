package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.ContinueReadingStore

/**
 * 书架书籍级写操作 —— 删除/恢复/搁置/信息编辑/封面/本地缓存清理。
 *
 * 纯逻辑类：不持有协程作用域，方法全部为 suspend 函数，由 ViewModel 负责 launch
 * 调度并包装回调；成功/失败以返回值（String 或 Result）表达，便于 JVM 单测。
 */
class ShelfBookActions(
    private val context: Context,
    private val repository: BookRepository,
    private val continueReadingStore: ContinueReadingStore,
) {

    suspend fun deleteBook(id: String) {
        repository.deleteBook(id)
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
