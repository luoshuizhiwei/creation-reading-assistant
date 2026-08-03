package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.hasLocalBookSource
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.util.bookNotReadyLabel as utilBookNotReadyLabel
import com.creationreadingassistant.ui.viewmodel.ShelfBookItem

// ========== enums & data class：供 BookGrid / 其他页面引用 ==========

internal enum class ShelfSortMode { RECENT, IMPORTED, TITLE, PROGRESS }
internal enum class ShelfViewMode { GRID, LIST }
internal enum class BatchSheetKind { SHELF, CATEGORY, TAG }

/** 对齐 web shelfStatusOptions（全部/在读/已完成/未开始/本机可读）。 */
internal enum class ShelfStatusFilter { ALL, READING, COMPLETED, UNREAD, READABLE }

/** 对齐 book-status.ts 的就绪状态判定（原生仅用已存在字段）。 */
internal enum class ReadinessTone { READY, CLOUD, ERROR }

internal data class BookReadiness(val label: String, val tone: ReadinessTone)

// ========== 工具函数 ==========

internal fun BookEntity.isDownloaded(): Boolean {
    return hasLocalBookSource()
}

internal fun BookEntity.readiness(): BookReadiness {
    if (content_status == "failed") return BookReadiness("正文保存失败", ReadinessTone.ERROR)
    if (content_status == "missing") return BookReadiness("正文未在本机", ReadinessTone.CLOUD)
    if (content_status == "downloading") return BookReadiness("正文下载中", ReadinessTone.CLOUD)
    if (isDownloaded()) return BookReadiness("可离线阅读", ReadinessTone.READY)
    if (size <= 0) return BookReadiness("正文为空", ReadinessTone.ERROR)
    return BookReadiness("需下载正文", ReadinessTone.CLOUD)
}

/** 向后兼容：返回 null 表示可读，非 null 为不可读原因提示（对齐 ui.util.bookNotReadyLabel 语义）。 */
internal fun bookNotReadyLabel(book: BookEntity): String? = utilBookNotReadyLabel(book)

internal fun progressFor(map: Map<String, ReadingProgressEntity>, id: String): Float =
    (map[id]?.progress_percent ?: 0f).coerceIn(0f, 100f)

/** 对齐 book-status.ts 的「阅读状态」判定，用于状态筛选条。 */
internal fun bookStatus(book: BookEntity, percent: Float): ShelfStatusFilter {
    val p = percent.coerceIn(0f, 100f)
    return when {
        book.isDownloaded() -> ShelfStatusFilter.READABLE
        p >= 99.5f -> ShelfStatusFilter.COMPLETED
        p > 0f -> ShelfStatusFilter.READING
        else -> ShelfStatusFilter.UNREAD
    }
}

/** 仅做筛选，不做排序；排序已在 ViewModel 后台完成。 */
internal fun filterItems(
    items: List<ShelfBookItem>,
    query: String,
    statusFilter: ShelfStatusFilter,
    progressById: Map<String, ReadingProgressEntity>,
    allowedBookIds: Set<String>?,
): List<ShelfBookItem> {
    val trimmed = query.trim()
    // 常见路径快速返回：避免遍历整库，排序切换时直接复用 ViewModel 后台已排序的列表。
    if (trimmed.isEmpty() && statusFilter == ShelfStatusFilter.ALL && allowedBookIds == null) {
        return items
    }
    val lower = trimmed.lowercase()
    return items.filter { item ->
        val book = item.book
        val matchesQuery = lower.isEmpty() ||
            "${book.title} ${book.author ?: ""} ${book.original_file_name ?: ""}".lowercase().contains(lower)
        val matchesFilter = allowedBookIds?.contains(book.id) ?: true
        val matchesStatus = statusFilter == ShelfStatusFilter.ALL ||
            bookStatus(book, progressFor(progressById, book.id)) == statusFilter
        matchesQuery && matchesFilter && matchesStatus
    }
}

@Composable
internal fun toneColor(tone: ReadinessTone): Color = when (tone) {
    ReadinessTone.READY -> AppSuccess
    ReadinessTone.CLOUD -> MaterialTheme.colorScheme.primary
    ReadinessTone.ERROR -> AppError
}
