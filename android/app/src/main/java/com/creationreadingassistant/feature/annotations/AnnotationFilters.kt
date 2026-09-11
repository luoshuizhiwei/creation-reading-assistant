package com.creationreadingassistant.feature.annotations

/**
 * 「我的 → 阅读笔记」筛选状态：类型（多选）、书籍（单选或全部）、关键词。
 * 空态必须对应真实筛选状态 —— 全库为空与「筛选无结果」在 UI 上是两种空态。
 */
data class AnnotationFilterState(
    val types: Set<AnnotationType> = AnnotationType.entries.toSet(),
    val bookId: String? = null,
    val keyword: String = "",
) {
    val isDefault: Boolean
        get() = types.size == AnnotationType.entries.size && bookId == null && keyword.isBlank()
}

/** 关键词匹配范围：标题、原文摘录、个人批注、章节名（大小写不敏感、忽略首尾空白）。 */
fun AnnotationEntry.matchesKeyword(keyword: String): Boolean {
    val q = keyword.trim()
    if (q.isEmpty()) return true
    return title?.contains(q, ignoreCase = true) == true ||
        excerpt?.contains(q, ignoreCase = true) == true ||
        annotation?.contains(q, ignoreCase = true) == true ||
        chapterTitle?.contains(q, ignoreCase = true) == true
}

/**
 * 应用筛选：类型集合 + 书籍 + 关键词三者取交集。
 * bookId 为 null 表示全部书籍；类型集合为空集时结果为空（显式用户选择，不是 bug）。
 */
fun filterAnnotations(
    entries: List<AnnotationEntry>,
    filter: AnnotationFilterState,
): List<AnnotationEntry> = entries.filter { entry ->
    entry.type in filter.types &&
        (filter.bookId == null || entry.bookId == filter.bookId) &&
        entry.matchesKeyword(filter.keyword)
}

/**
 * 统一条目排序。
 *
 * - 未选书籍（跨书浏览）：按创建时间倒序（最新在前），与既有列表行为一致。
 * - 选定单本书：按**实际文档顺序**排 —— locator 的 chapterIndex 升序，
 *   章内按 positionOffset 升序；无 locator / 无章索引的历史记录排在同书末尾
 *   （按创建时间倒序），**不按章节标题中文字典序**（"第十章" vs "第二章"
 *   字典序与文档序无关，"Prologue/Epilogue" 等非数字标题更无法比较）。
 */
fun sortAnnotationEntries(
    entries: List<AnnotationEntry>,
    filter: AnnotationFilterState,
): List<AnnotationEntry> {
    if (filter.bookId == null) {
        return entries.sortedByDescending { it.createdAt }
    }
    return entries.sortedWith(
        compareBy<AnnotationEntry>(
            { it.chapterIndex ?: Int.MAX_VALUE },
            { it.positionOffset ?: Int.MAX_VALUE },
        ).thenByDescending { it.createdAt },
    )
}

/**
 * 章节分组的通用文档序排序：组按组内最小 chapterIndex 升序，
 * 无章索引的组排最后（按组内最新创建时间倒序）。
 * 替代旧 toSortedMap() 的中文标题字典序（"第十章" < "第二章" 与文档序无关）。
 * ReaderNotesSheet 与导出共用，保持「我的」与书内面板同一排序口径。
 */
fun <T> groupByChapterInDocumentOrder(
    items: List<T>,
    chapterTitleOf: (T) -> String?,
    chapterIndexOf: (T) -> Int?,
    createdAtOf: (T) -> String,
): List<Pair<String?, List<T>>> {
    val groups = items.groupBy(chapterTitleOf)
    return groups.entries
        .map { (title, group) -> title to group }
        .sortedWith(
            compareBy<Pair<String?, List<T>>> { (_, group) ->
                group.minOfOrNull { chapterIndexOf(it) ?: Int.MAX_VALUE } ?: Int.MAX_VALUE
            }.thenByDescending { (_, group) -> group.maxOfOrNull(createdAtOf) ?: "" },
        )
}

/** 统一条目的章节分组（文档序）：见 [groupByChapterInDocumentOrder]。 */
fun chapterGroupsInDocumentOrder(
    entries: List<AnnotationEntry>,
): List<Pair<String?, List<AnnotationEntry>>> = groupByChapterInDocumentOrder(
    items = entries,
    chapterTitleOf = { it.chapterTitle },
    chapterIndexOf = { it.chapterIndex },
    createdAtOf = { it.createdAt },
)