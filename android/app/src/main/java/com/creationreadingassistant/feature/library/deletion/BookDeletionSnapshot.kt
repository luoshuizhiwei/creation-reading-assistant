package com.creationreadingassistant.feature.library.deletion

import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity

/**
 * 一次删除操作的作用范围快照。
 *
 * 快照只记录「本次操作前处于活跃状态」的资料与关系：本次操作之前就已经被用户删除的
 * 笔记/高亮/会话不在其中，因此撤销不会把它们复活。分类、标签、书单、已读章节在删除时
 * 是硬删除（连接表没有 deleted_at 列），只有快照能把它们带回来。
 *
 * [deletedAt] 是本次操作打在全部软删除行上的同一个时间戳；撤销时用它区分
 * 「本次操作删掉的」与「此后又被别的操作删掉的」，避免覆盖后来的合法改动。
 */
data class BookDeletionSnapshot(
    val bookId: String,
    val deletedAt: String,
    val book: BookEntity?,
    val progress: ReadingProgressEntity?,
    val sessions: List<ReadingSessionEntity>,
    val notes: List<NoteEntity>,
    val highlights: List<HighlightEntity>,
    val file: BookFileEntity?,
    /** 删除前的正文缓存行；载荷可能因内存预算被裁剪，此时 [contentPayloadRetained] 为 false。 */
    val content: BookContentEntity?,
    val contentPayloadRetained: Boolean,
    val tagIds: List<String>,
    val categoryIds: List<String>,
    val shelfLinks: List<ShelfBookEntity>,
    val chapterReads: List<ChapterReadEntity>,
) {
    /** 快照里到底记了多少东西，用于「本次删除前无正文/无记录」的边界判断与提示文案。 */
    val isEmpty: Boolean
        get() = book == null &&
            progress == null &&
            sessions.isEmpty() &&
            notes.isEmpty() &&
            highlights.isEmpty() &&
            file == null &&
            content == null &&
            tagIds.isEmpty() &&
            categoryIds.isEmpty() &&
            shelfLinks.isEmpty() &&
            chapterReads.isEmpty()

    /** 驻留在内存里的正文缓存字符数；资料与阅读数据不计入预算，它们不可重建。 */
    val contentPayloadChars: Long
        get() = (content?.reader_preview?.length ?: 0).toLong() +
            (content?.epub_json?.length ?: 0).toLong()
}

/**
 * 按内存预算裁剪一组快照里的正文缓存载荷。
 *
 * 超预算时整本丢弃载荷，而不是截断字符串：半截 epub_json / 预览正文既读不出来也无法校验，
 * 留着只会让撤销看起来恢复了正文、实际上没有。被丢弃的快照 [BookDeletionSnapshot.contentPayloadRetained]
 * 为 false，撤销时会如实报告 [BookRestoreReport.contentPayloadDropped]，正文在重新打开书籍时重建。
 *
 * 按传入顺序保留，预算耗尽后其余全部丢弃；资料、进度、会话、笔记、高亮、关系一律不裁剪。
 */
internal fun applyContentBudget(
    snapshots: List<BookDeletionSnapshot>,
    maxChars: Long,
): List<BookDeletionSnapshot> {
    var remaining = maxChars
    return snapshots.map { snapshot ->
        val cost = snapshot.contentPayloadChars
        when {
            snapshot.content == null -> snapshot
            cost <= remaining -> {
                remaining -= cost
                snapshot
            }
            else -> snapshot.copy(
                content = snapshot.content.copy(reader_preview = null, epub_json = null),
                contentPayloadRetained = false,
            )
        }
    }
}

/**
 * 撤销一次删除的实际结果。
 *
 * 「恢复成功」不等于「快照里的每一项都写回去了」：操作之后的合法新改动、已被删除的
 * 关系目标都必须让路，所以这里把跳过原因显式分开，供上层如实告知用户。
 */
data class BookRestoreReport(
    val bookId: String,
    val restoredBook: Boolean = false,
    val restoredProgress: Boolean = false,
    val restoredSessionCount: Int = 0,
    val restoredNoteCount: Int = 0,
    val restoredHighlightCount: Int = 0,
    val restoredTagCount: Int = 0,
    val restoredCategoryCount: Int = 0,
    val restoredShelfLinkCount: Int = 0,
    val restoredChapterReadCount: Int = 0,
    val restoredFile: Boolean = false,
    val restoredContent: Boolean = false,
    /** 本次操作之后又被写入/再次删除，因此不被覆盖的行数。 */
    val skippedNewerChangeCount: Int = 0,
    /** 关系目标（标签/分类/书单）已不存在或已删除，跳过以免产生孤儿关系。 */
    val skippedRelationTargetIds: List<String> = emptyList(),
    /** 正文缓存载荷未保留在快照内，撤销后需要重新打开书籍重建。 */
    val contentPayloadDropped: Boolean = false,
    /** 目标书籍当前已经是活跃状态：重复撤销或已被其它路径恢复。 */
    val bookAlreadyActive: Boolean = false,
) {
    val restoredAnything: Boolean
        get() = restoredBook || restoredProgress || restoredFile || restoredContent ||
            restoredSessionCount > 0 || restoredNoteCount > 0 || restoredHighlightCount > 0 ||
            restoredTagCount > 0 || restoredCategoryCount > 0 || restoredShelfLinkCount > 0 ||
            restoredChapterReadCount > 0

    val restoredRelationCount: Int get() = restoredTagCount + restoredCategoryCount + restoredShelfLinkCount
}

/**
 * 撤销时仍然处于活跃状态的关系目标。
 *
 * 分类/标签/书单可能在「删除之后、撤销之前」被用户删掉；此时重新写回连接行只会产生
 * 指向已删除目标的孤儿关系。由上层（协调器）从分类仓储解析后传入，BookRepository
 * 因此不必再依赖 TagDao/CategoryDao/ShelfDao。
 */
data class LiveRelationTargets(
    val tagIds: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val shelfIds: Set<String> = emptySet(),
) {
    companion object {
        val EMPTY = LiveRelationTargets()
    }
}
