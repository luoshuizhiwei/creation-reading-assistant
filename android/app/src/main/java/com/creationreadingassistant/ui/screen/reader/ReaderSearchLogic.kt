package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/** 书内全文搜索结果（对照 web ReaderSearchResult）。 */
internal data class BookSearchResult(
    val occurrenceIndex: Int,
    val snippet: String,
    val progressPercent: Float,
    val chapterIndex: Int, // -1 表示纯文本全书
    val chapterTitle: String,
    /** TXT 为全书偏移；EPUB 为章内真实字符偏移。 */
    val charOffset: Int,
    /** 命中在全书文本中的字符区间（含首不含尾）；TXT=原始字符空间，EPUB/Markdown=估算/规范字符空间。 */
    val absoluteRange: IntRange,
)

/**
 * 搜索运行绑定的文档上下文键（身份维度，不含文本内容）：
 * 同一批文档实例重开面板时结果可直接复用；文档重建（TXT 目录规则切换、
 * 书籍重载）即使查询未变也必须重新搜索。
 */
internal fun searchContextKeyOf(
    document: com.creationreadingassistant.feature.reader.doc.ReaderDocument?,
    txtDocument: PlainTextDocument?,
    plainContent: String,
): String = "${System.identityHashCode(document)}|${System.identityHashCode(txtDocument)}|${System.identityHashCode(plainContent)}"

/**
 * Case-insensitive lookup that reports indices in the original UTF-16 string.
 * Building a lowercased copy is unsafe because Unicode case conversion can change its length.
 */
private fun String.indexOfIgnoreCaseStable(needle: String, startIndex: Int): Int {
    if (needle.isEmpty()) return startIndex.coerceIn(0, length)
    val first = startIndex.coerceAtLeast(0)
    val last = length - needle.length
    if (first > last) return -1
    for (index in first..last) {
        if (
            regionMatches(
                thisOffset = index,
                other = needle,
                otherOffset = 0,
                length = needle.length,
                ignoreCase = true,
            )
        ) {
            return index
        }
    }
    return -1
}

/** 对照 web createReaderSearchResults：全本拼接文本上做不区分大小写检索，最多 80 处，片断取 28 前 +42 后。 */
internal fun computeBookSearch(
    fullText: String,
    query: String,
    chapterStartOffsets: List<Int>,
    chapterTitles: List<String>,
    isTxt: Boolean,
): List<BookSearchResult> {
    val keyword = query.trim()
    if (keyword.isBlank()) return emptyList()
    val text = fullText
    val results = mutableListOf<BookSearchResult>()
    var from = 0
    var occurrence = 0
    while (results.size < 80) {
        val hit = text.indexOfIgnoreCaseStable(keyword, from)
        if (hit < 0) break
        val start = (hit - 28).coerceAtLeast(0)
        val end = (hit + keyword.length + 42).coerceAtMost(text.length)
        val excerpt = text.substring(start, end).replace(Regex("\\s+"), " ")
        val snippet = "${if (start > 0) "…" else ""}$excerpt${if (end < text.length) "…" else ""}"
        val progress = if (text.isEmpty()) 0f else (hit.toFloat() / text.length) * 100f
        val chIdx = if (isTxt || chapterStartOffsets.isEmpty()) {
            -1
        } else {
            chapterStartOffsets.indexOfLast { it <= hit }.coerceIn(0, chapterTitles.lastIndex)
        }
        val chTitle = if (isTxt || chIdx < 0) "全文" else chapterTitles.getOrNull(chIdx) ?: "正文"
        results.add(
            BookSearchResult(
                occurrence, snippet, progress, chIdx, chTitle, hit,
                absoluteRange = hit until hit + keyword.length,
            ),
        )
        occurrence += 1
        from = hit + keyword.length
    }
    return results
}

/**
 * EPUB 搜索：逐章经 [ReaderDocument.text] 读取与渲染完全相同的文本空间并检索，
 * 内存只保留当前章文本（不拼接全本），命中后用 [chapterStartOffsets] 映射回全局偏移与所属章。
 * 必须在 IO 线程调用（[SearchSheet] 内已 withContext(Dispatchers.IO)）。
 */
internal suspend fun computeEpubSearch(
    document: com.creationreadingassistant.feature.reader.doc.ReaderDocument,
    query: String,
    chapterStartOffsets: List<Int>,
    chapterTitles: List<String>,
    totalChars: Int,
    onProgress: (searched: Int, total: Int) -> Unit = { _, _ -> },
): List<BookSearchResult> {
    val keyword = query.trim()
    if (keyword.isBlank()) return emptyList()
    val results = mutableListOf<BookSearchResult>()
    val denom = totalChars.coerceAtLeast(1)
    val total = document.chapters.size
    for (ci in document.chapters.indices) {
        if (results.size >= 80) break
        yield()
        onProgress(ci + 1, total)
        val ct = document.text(ci)
        if (ct.isBlank()) continue
        val text = ct
        val base = chapterStartOffsets.getOrElse(ci) { 0 }
        var from = 0
        while (results.size < 80) {
            val hit = text.indexOfIgnoreCaseStable(keyword, from)
            if (hit < 0) break
            val start = (hit - 28).coerceAtLeast(0)
            val end = (hit + keyword.length + 42).coerceAtMost(text.length)
            val excerpt = text.substring(start, end).replace(Regex("\\s+"), " ")
            val snippet = "${if (start > 0) "…" else ""}$excerpt${if (end < text.length) "…" else ""}"
            val globalHit = base + hit
            val progress = (globalHit.toFloat() / denom) * 100f
            val chTitle = chapterTitles.getOrNull(ci) ?: "正文"
            results.add(
                BookSearchResult(
                    results.size, snippet, progress, ci, chTitle, hit,
                    absoluteRange = globalHit until globalHit + keyword.length,
                ),
            )
            from = hit + keyword.length
        }
    }
    return results
}

/**
 * 流式 TXT 搜索：逐 ReadingUnit 读取文本并检索，避免加载整个章节。
 * 使用 200 字符重叠处理跨单元边界的匹配。
 * 必须在 IO 线程调用。
 */
internal suspend fun computeStreamingTxtSearch(
    document: PlainTextDocument,
    readingUnits: List<ReadingUnit>,
    query: String,
    totalChars: Int,
    onProgress: (searched: Int, total: Int) -> Unit = { _, _ -> },
): List<BookSearchResult> {
    val keyword = query.trim()
    if (keyword.isBlank()) return emptyList()
    val results = mutableListOf<BookSearchResult>()
    val denom = totalChars.coerceAtLeast(1)
    // 跨 ReadingUnit 的命中最多需要保留 keyword.length - 1 个前缀字符；
    // 200 只是短关键词的默认窗口，不能让超长查询在单元边界被截断。
    val overlapLength = (keyword.length - 1).coerceAtLeast(200)
    var previousTail = ""
    val total = readingUnits.size
    for ((i, unit) in readingUnits.withIndex()) {
        if (results.size >= 80) break
        yield()
        currentCoroutineContext().ensureActive()
        onProgress(i + 1, total)
        val unitText = document.readUnit(unit)
        val searchInput = previousTail + unitText
        val offsetAdjust = unit.charStart - previousTail.length
        var from = 0
        while (results.size < 80) {
            val hit = searchInput.indexOfIgnoreCaseStable(keyword, from)
            if (hit < 0) break
            val globalHit = hit + offsetAdjust
            // previousTail 仅用于发现跨 ReadingUnit 边界的匹配。完全落在重叠尾部的
            // 命中已经在上一单元报告过，必须跳过；若命中延伸到当前单元，则保留它。
            if (globalHit + keyword.length <= unit.charStart) {
                from = hit + keyword.length
                continue
            }
            val start = (hit - 28).coerceAtLeast(0)
            val end = (hit + keyword.length + 42).coerceAtMost(searchInput.length)
            val excerpt = searchInput.substring(start, end).replace(Regex("\\s+"), " ")
            val snippet = "${if (start > 0) "…" else ""}$excerpt${if (end < searchInput.length) "…" else ""}"
            val progress = (globalHit.toFloat() / denom) * 100f
            results.add(
                BookSearchResult(
                    results.size, snippet, progress, -1, "全文", globalHit,
                    absoluteRange = globalHit until globalHit + keyword.length,
                ),
            )
            from = hit + keyword.length
        }
        previousTail = if (unitText.length >= overlapLength) {
            unitText.takeLast(overlapLength)
        } else {
            (previousTail + unitText).takeLast(overlapLength)
        }
    }
    return results
}
