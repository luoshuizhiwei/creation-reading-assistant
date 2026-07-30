package com.creationreadingassistant.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity

/**
 * 书架列表的稳定渲染模型。
 *
 * - 所有字段不可变，可被 Compose 编译器识别为稳定，Lazy 列表可用 [bookId] 做稳定 key。
 * - 排序相关的规范化 key 在后台线程一次性计算，避免 UI 线程重复做字符串处理与比较。
 * - 保留原始 [BookEntity] 引用供书卡使用，不展开字段，减少模型转换带来的信息丢失。
 */
@Immutable
data class ShelfBookItem(
    val bookId: String,
    val book: BookEntity,
    /** 书名排序 key（与现有顺序一致，保留原始大小写与空白）。 */
    val titleKey: String,
    /** 作者排序 key。 */
    val authorKey: String,
    /** 导入时间排序 key（无导入时间时回退到更新时间）。 */
    val importedAtKey: String,
    /** 进度排序 key。 */
    val progressKey: Float,
    /** 最近阅读排序 key。 */
    val lastReadAtKey: String,
    /** 更新时间排序 key。 */
    val updatedAtKey: String,
) {
    companion object {
        fun from(book: BookEntity, progress: ReadingProgressEntity?): ShelfBookItem = ShelfBookItem(
            bookId = book.id,
            book = book,
            titleKey = book.title,
            authorKey = book.author ?: "",
            importedAtKey = book.imported_at ?: book.updated_at,
            progressKey = progress?.progress_percent ?: 0f,
            lastReadAtKey = progress?.last_read_at ?: "",
            updatedAtKey = book.updated_at,
        )
    }
}

/**
 * 书架列表投影与排序。纯函数，不持有可变状态，可在 [Dispatchers.Default] 上安全执行。
 */
object ShelfBookSorter {

    fun projectAll(
        books: List<BookEntity>,
        progressById: Map<String, ReadingProgressEntity>,
    ): List<ShelfBookItem> {
        return books.map { ShelfBookItem.from(it, progressById[it.id]) }
    }

    fun sort(items: List<ShelfBookItem>, sortMode: String): List<ShelfBookItem> {
        return when (sortMode.lowercase()) {
            "title" -> items.sortedWith(
                compareBy(ShelfBookItem::titleKey)
                    .thenBy(ShelfBookItem::bookId)
            )
            "progress" -> items.sortedWith(
                compareByDescending(ShelfBookItem::progressKey)
                    .thenBy(ShelfBookItem::bookId)
            )
            "imported" -> items.sortedWith(
                compareByDescending(ShelfBookItem::importedAtKey)
                    .thenBy(ShelfBookItem::bookId)
            )
            "recent" -> items.sortedWith(
                compareByDescending(ShelfBookItem::lastReadAtKey)
                    .thenByDescending(ShelfBookItem::updatedAtKey)
                    .thenBy(ShelfBookItem::bookId)
            )
            else -> items
        }
    }
}
