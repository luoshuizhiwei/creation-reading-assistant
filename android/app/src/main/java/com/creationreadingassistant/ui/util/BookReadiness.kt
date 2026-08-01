package com.creationreadingassistant.ui.util

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity

/**
 * 书籍可读性 / 进度格式化工具。
 *
 * 抽自 HomeScreen.kt 与 HomeContinueSheet.kt 两处重复定义的工具函数，统一命名与实现。
 * 这些函数只依赖 data.local.entity 模型，不依赖 View 层，可安全跨页面复用。
 */

/**
 * 判断书籍是否可在本机直接打开（未删除 + 内容已就绪 + 有本地路径或内容哈希）。
 *
 * 原 HomeContinueSheet 内同名 `isBookReadableOnDevice` 与本函数逻辑完全一致，
 * 已统一为 `isBookDisplayable`，调用方请改用本函数名。
 */
fun isBookDisplayable(book: BookEntity): Boolean {
    if (book.deleted_at != null) return false
    return when (book.content_status) {
        "failed", "missing", "downloading" -> false
        else -> book.size > 0 && (book.local_content_path != null || book.local_uri != null || book.content_hash != null)
    }
}

/**
 * H2：对齐网页 getBookReadiness。返回未就绪时的中文提示文案；若已就绪（可离线打开）则返回 null。
 */
fun bookNotReadyLabel(book: BookEntity): String? {
    if (book.deleted_at != null) return "正文未在本机"
    return when (book.content_status) {
        "failed" -> "正文保存失败"
        "missing" -> "正文未在本机"
        "downloading" -> "正文下载中"
        else -> {
            val readable = book.size > 0 &&
                (book.local_content_path != null || book.local_uri != null || book.content_hash != null)
            if (!readable) "需下载正文" else null
        }
    }
}

/**
 * 判断书籍是否曾被阅读过（有进度记录或有阅读会话）。
 */
fun hasBookBeenRead(
    book: BookEntity,
    progress: ReadingProgressEntity?,
    sessions: List<ReadingSessionEntity>?,
): Boolean {
    if ((progress?.progress_percent ?: 0f) > 0f) return true
    return sessions?.any { it.book_id == book.id } == true
}

/**
 * 格式化阅读进度百分比为卡片显示文案。
 *
 * 原 HomeContinueSheet 内同名 `formatBookProgress` 与本函数逻辑完全一致，
 * 已统一为 `formatBookProgressForCard`，调用方请改用本函数名。
 */
fun formatBookProgressForCard(progress: Float): String {
    val normalized = progress.coerceIn(0f, 100f)
    return when {
        normalized <= 0.05f -> "未读"
        normalized >= 99.5f -> "已读完"
        normalized >= 10f -> "${normalized.toInt()}%"
        else -> "${"%.1f".format(normalized)}%"
    }
}
