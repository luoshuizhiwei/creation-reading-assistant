package com.creationreadingassistant.feature.annotations

import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.reader.locator.LocatorCodec

/**
 * 统一阅读笔记条目的类型：高亮（含批注）、普通批注笔记、书签。
 *
 * 对应存储：高亮 = highlights 表；批注与书签 = notes 表（kind = "note" / "bookmark"）。
 * 不合并数据库表，仅在做统一展示 / 筛选 / 导出时由 [AnnotationEntry] 投影。
 */
enum class AnnotationType {
    HIGHLIGHT,
    NOTE,
    BOOKMARK,
}

/**
 * 「我的 → 阅读笔记」与书内笔记面板共用的统一条目模型。
 *
 * - [stableId] 形如 `"highlight:{id}"` / `"note:{id}"` / `"bookmark:{id}"`，
 *   由类型前缀 + 原表主键构成；notes 与 highlights 两表的主键都是 UUID，
 *   统一条目仍以类型前缀显式区分，避免任何同 ID 场景下的 key 冲突
 *   （Compose lazy list key、批量选择集合等）。
 * - [excerpt] 为原文摘录（高亮正文 / 笔记摘录），[annotation] 为个人批注
 *   （高亮的 note 字段 / 笔记的 body 字段），二者在 UI 上分区展示。
 * - [chapterIndex] / [charOffset] / [legacyOffset] 来自 locator_json（v2）解码；
 *   [hasLocator] = false 的历史记录无法精确定位，只能降级打开书籍，禁止伪造偏移。
 */
data class AnnotationEntry(
    val stableId: String,
    val type: AnnotationType,
    val rawId: String,
    val bookId: String?,
    val title: String?,
    val excerpt: String?,
    val annotation: String?,
    val chapterTitle: String?,
    val chapterIndex: Int?,
    val charOffset: Int?,
    val legacyOffset: Int?,
    val hasLocator: Boolean,
    val color: String?,
    val progressPercent: Float?,
    val createdAt: String,
    val updatedAt: String,
) {
    /** 定位排序键：章内偏移（EPUB co）优先，其次全书偏移（TXT charOffset / legacyOffset）。 */
    val positionOffset: Int? get() = charOffset ?: legacyOffset
}

private fun stableIdOf(type: AnnotationType, rawId: String): String = "${type.name.lowercase()}:$rawId"

/** 高亮 → 统一条目。excerpt = 高亮正文，annotation = 高亮批注。 */
fun HighlightEntity.toAnnotationEntry(): AnnotationEntry {
    val locator = LocatorCodec.decode(locator_json)
    return AnnotationEntry(
        stableId = stableIdOf(AnnotationType.HIGHLIGHT, id),
        type = AnnotationType.HIGHLIGHT,
        rawId = id,
        bookId = book_id,
        title = null,
        excerpt = text.takeIf { it.isNotBlank() },
        annotation = note?.takeIf { it.isNotBlank() },
        chapterTitle = chapter_title?.takeIf { it.isNotBlank() },
        chapterIndex = locator?.chapterIndex,
        charOffset = locator?.charOffset,
        legacyOffset = locator?.legacyOffset,
        hasLocator = locator != null,
        color = color,
        progressPercent = progress_percent,
        createdAt = created_at,
        updatedAt = updated_at,
    )
}

/**
 * 笔记 → 统一条目。kind = "bookmark" 投影为 [AnnotationType.BOOKMARK]，
 * 其余投影为 [AnnotationType.NOTE]（excerpt = 摘录，annotation = body 批注）。
 */
fun NoteEntity.toAnnotationEntry(): AnnotationEntry {
    val isBookmark = kind == "bookmark"
    val type = if (isBookmark) AnnotationType.BOOKMARK else AnnotationType.NOTE
    val locator = LocatorCodec.decode(locator_json)
    return AnnotationEntry(
        stableId = stableIdOf(type, id),
        type = type,
        rawId = id,
        bookId = book_id,
        title = title.takeIf { it.isNotBlank() },
        excerpt = excerpt?.takeIf { it.isNotBlank() },
        annotation = if (isBookmark) null else body.takeIf { it.isNotBlank() },
        chapterTitle = chapter_title?.takeIf { it.isNotBlank() },
        chapterIndex = locator?.chapterIndex,
        charOffset = locator?.charOffset,
        legacyOffset = locator?.legacyOffset,
        hasLocator = locator != null,
        color = null,
        progressPercent = progress_percent,
        createdAt = created_at,
        updatedAt = updated_at,
    )
}

/** 高亮 + 笔记（含书签）两表数据合并为统一条目列表（不去重、不改排序，排序交给调用方）。 */
fun buildAnnotationEntries(
    highlights: List<HighlightEntity>,
    notes: List<NoteEntity>,
): List<AnnotationEntry> = highlights.map { it.toAnnotationEntry() } + notes.map { it.toAnnotationEntry() }