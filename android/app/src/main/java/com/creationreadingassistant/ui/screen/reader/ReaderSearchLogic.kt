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
)

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
    val lower = text.lowercase()
    val lowerKw = keyword.lowercase()
    val results = mutableListOf<BookSearchResult>()
    var from = 0
    var occurrence = 0
    while (results.size < 80) {
        val hit = lower.indexOf(lowerKw, from)
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
        results.add(BookSearchResult(occurrence, snippet, progress, chIdx, chTitle, hit))
        occurrence += 1
        from = hit + lowerKw.length
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
): List<BookSearchResult> {
    val keyword = query.trim()
    if (keyword.isBlank()) return emptyList()
    val lowerKw = keyword.lowercase()
    val results = mutableListOf<BookSearchResult>()
    val denom = totalChars.coerceAtLeast(1)
    for (ci in document.chapters.indices) {
        if (results.size >= 80) break
        yield()
        val ct = document.text(ci)
        if (ct.isBlank()) continue
        val text = ct
        val lower = text.lowercase()
        val base = chapterStartOffsets.getOrElse(ci) { 0 }
        var from = 0
        while (results.size < 80) {
            val hit = lower.indexOf(lowerKw, from)
            if (hit < 0) break
            val start = (hit - 28).coerceAtLeast(0)
            val end = (hit + keyword.length + 42).coerceAtMost(text.length)
            val excerpt = text.substring(start, end).replace(Regex("\\s+"), " ")
            val snippet = "${if (start > 0) "…" else ""}$excerpt${if (end < text.length) "…" else ""}"
            val globalHit = base + hit
            val progress = (globalHit.toFloat() / denom) * 100f
            val chTitle = chapterTitles.getOrNull(ci) ?: "正文"
            results.add(BookSearchResult(results.size, snippet, progress, ci, chTitle, hit))
            from = hit + lowerKw.length
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
): List<BookSearchResult> {
    val keyword = query.trim()
    if (keyword.isBlank()) return emptyList()
    val lowerKw = keyword.lowercase()
    val results = mutableListOf<BookSearchResult>()
    val denom = totalChars.coerceAtLeast(1)
    var previousTail = ""
    for (unit in readingUnits) {
        if (results.size >= 80) break
        yield()
        currentCoroutineContext().ensureActive()
        val unitText = document.readUnit(unit)
        val searchInput = previousTail + unitText
        val lower = searchInput.lowercase()
        val offsetAdjust = unit.charStart - previousTail.length
        var from = 0
        while (results.size < 80) {
            val hit = lower.indexOf(lowerKw, from)
            if (hit < 0) break
            val globalHit = hit + offsetAdjust
            val start = (hit - 28).coerceAtLeast(0)
            val end = (hit + keyword.length + 42).coerceAtMost(searchInput.length)
            val excerpt = searchInput.substring(start, end).replace(Regex("\\s+"), " ")
            val snippet = "${if (start > 0) "…" else ""}$excerpt${if (end < searchInput.length) "…" else ""}"
            val progress = (globalHit.toFloat() / denom) * 100f
            results.add(BookSearchResult(results.size, snippet, progress, -1, "全文", globalHit))
            from = hit + lowerKw.length
        }
        previousTail = if (unitText.length >= 200) {
            unitText.takeLast(200)
        } else {
            (previousTail + unitText).takeLast(200)
        }
    }
    return results
}
