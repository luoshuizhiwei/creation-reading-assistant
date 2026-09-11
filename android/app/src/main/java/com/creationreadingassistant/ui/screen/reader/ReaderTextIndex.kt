package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.LegacyOffsetCodec
import com.creationreadingassistant.feature.reader.doc.ReadingUnit

/**
 * 阅读器纯偏移/索引换算：全书章节索引、TXT 分块、单元定位、搜索区间换算。
 * 全部为无副作用纯函数（JVM 可测），渲染路径与搜索/TTS 高亮共用同一坐标口径。
 */

/**
 * 计算每章各渲染块（含图片，图片记为 -1）在全书文本中的全局字符偏移。
 * 与 [splitSentencesWithOffsets] 对 contentText 的切分一致：文本块按 "\n" 拼接。
 */
internal fun computeBlockGlobalOffsets(blocks: List<DocBlock>, chapterBase: Int): List<Int> =
    LegacyOffsetCodec.blockOffsets(
        blocks.map { (it as? DocBlock.Text)?.text?.length },
        chapterBase,
    )

/** 根据章节内偏移，返回包含该偏移的「渲染块」索引（用于导航精确滚动）。 */
internal fun blockIndexForChapterOffset(blocks: List<DocBlock>, inChapter: Int): Int? {
    var acc = 0
    var idx = -1
    blocks.forEachIndexed { i, block ->
        if (block is DocBlock.Text) {
            if (acc <= inChapter) idx = i
            acc += block.text.length + 1
        }
    }
    return idx.takeIf { it >= 0 }
}

/** EPUB 全书轻量索引：只记录每章起始偏移、标题与总字符数。 */
internal data class BookIndex(
    val chapterStartOffsets: List<Int>,
    val chapterTitles: List<String>,
    val totalChars: Int,
)

/** Locate a book-level character offset without assuming chapters have equal length. */
internal fun chapterIndexForBookOffset(
    chapterStartOffsets: List<Int>,
    targetOffset: Int,
    totalChars: Int,
): Int? {
    if (chapterStartOffsets.isEmpty()) return null
    val target = targetOffset.coerceIn(0, totalChars.coerceAtLeast(0))
    var low = 0
    var high = chapterStartOffsets.lastIndex
    var result = 0
    while (low <= high) {
        val middle = (low + high) ushr 1
        if (chapterStartOffsets[middle] <= target) {
            result = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return result
}

internal const val PLAIN_TEXT_CHUNK_CHARS = 3_000

internal data class PlainTextChunk(
    val startOffset: Int,
    val text: String,
)

/**
 * 按接近自然段的位置切分长文本。每个块单独排版，LazyColumn 只保留视口附近的
 * TextLayout，避免数 MB 小说在打开时生成数十万行的单一布局而触发 ANR/OOM。
 */
internal fun chunkPlainText(
    content: String,
    targetChars: Int = PLAIN_TEXT_CHUNK_CHARS,
): List<PlainTextChunk> {
    if (content.isEmpty()) return emptyList()
    val chunks = ArrayList<PlainTextChunk>((content.length / targetChars) + 1)
    var start = 0
    while (start < content.length) {
        var end = (start + targetChars).coerceAtMost(content.length)
        if (end < content.length) {
            val searchEnd = (end + 512).coerceAtMost(content.length)
            val newline = content.indexOf('\n', startIndex = end)
            if (newline in end until searchEnd) end = newline + 1
        }
        if (end <= start) end = (start + targetChars).coerceAtMost(content.length)
        chunks += PlainTextChunk(startOffset = start, text = content.substring(start, end))
        start = end
    }
    return chunks
}

/**
 * 小文件滚动 TXT 的章对齐读取单元（R1-S1）。
 *
 * 旧实现由 [chunkPlainText] 直接派生 unit 且 `chapterIndex` 恒为 0，使整本书坍缩为
 * 单一投影作用域：书长超过投影上限（256K 字符）时所有 unit 的 scope 都被判
 * UnsupportedTooLarge，正文永久降级原文，而替换能力裁决仍报 APPLIED ——
 * 形成「菜单可用、规则可保存、预览命中、正文永不替换」的跨层缺陷。
 *
 * 章节识别成功且章节偏移从 0 起完整衔接覆盖全书时，按真实逻辑章切块
 * （章内沿用 [chunkPlainText] 的有界切块，偏移平移到全书坐标系，unitIndex 重编）；
 * 识别失败或章节边界不衔接时不重排，回退旧的全文切块行为——保持诚实降级，
 * 不伪造完整逻辑章。
 */
internal fun buildChapterAlignedPlainUnits(
    content: String,
    chapters: List<DocChapter>,
    targetChars: Int = PLAIN_TEXT_CHUNK_CHARS,
): List<ReadingUnit> {
    if (content.isEmpty()) return emptyList()
    val usable = chapters
        .filter { it.charCount > 0 }
        .sortedBy { it.startOffset }
        .takeIf { list ->
            list.first().startOffset == 0 &&
                list.zipWithNext().all { (a, b) -> a.startOffset + a.charCount == b.startOffset } &&
                list.last().let { it.startOffset + it.charCount } == content.length
        }
    if (usable.isNullOrEmpty()) {
        return chunkPlainText(content, targetChars).mapIndexed { i, chunk ->
            ReadingUnit(
                unitIndex = i,
                chapterIndex = 0,
                title = "全文",
                charStart = chunk.startOffset,
                charCount = chunk.text.length,
            )
        }
    }
    val units = ArrayList<ReadingUnit>()
    var unitIndex = 0
    for (chapter in usable) {
        val chapterStart = chapter.startOffset
        val chapterText = content.substring(chapterStart, chapterStart + chapter.charCount)
        for (chunk in chunkPlainText(chapterText, targetChars)) {
            units += ReadingUnit(
                unitIndex = unitIndex++,
                chapterIndex = chapter.index,
                title = chapter.title,
                charStart = chapterStart + chunk.startOffset,
                charCount = chunk.text.length,
            )
        }
    }
    return units
}

internal fun chunkIndexForOffset(chunks: List<PlainTextChunk>, offset: Int): Int {
    if (chunks.isEmpty()) return 0
    var low = 0
    var high = chunks.lastIndex
    var result = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (chunks[mid].startOffset <= offset) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

internal fun unitIndexForOffset(units: List<ReadingUnit>, offset: Int): Int {
    if (units.isEmpty()) return 0
    var lo = 0
    var hi = units.lastIndex
    var result = 0
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (units[mid].charStart <= offset) {
            result = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return result
}

/**
 * 全书字符区间 → 单个文本段（ReadingUnit / DocBlock.Text）内的局部高亮区间。
 *
 * [textStart] 为该段在全书文本中的起始偏移，[textLength] 为段长；
 * [rangeAbs] 为命中区间（含首不含尾，与 [BookSearchResult.absoluteRange] 同一基准）。
 * 返回段内局部区间（含首不含尾），段与命中无重叠（或段为空）时返回 null。
 * TXT 滚动 / EPUB 滚动 / legacy 翻页的搜索高亮共用此换算，越界一律安全跳过。
 */
internal fun intersectTextRange(textStart: Int, textLength: Int, rangeAbs: IntRange): IntRange? {
    if (textLength <= 0) return null
    val s = maxOf(rangeAbs.first - textStart, 0)
    val e = minOf(rangeAbs.last + 1 - textStart, textLength)
    return if (e > s) s until e else null
}

/**
 * 只使用 EPUB 目录阶段已取得的 ZIP 条目大小构建轻量索引。
 *
 * 旧实现会在打开书籍时逐章解压并抽取全书文本。大书会长时间占用 IO，
 * 多次进出阅读器还可能叠加多个不可及时取消的扫描任务，最终触发 ANR/OOM。
 * ZIP 解压后字节数通常大于可见字符数，适合作为单调递增的定位偏移估算。
 */
internal fun buildBookIndex(book: EpubBook): BookIndex {
    // 公式已抽到 LegacyOffsetCodec 并由单测逐值锁死。这里只保留一处调用：
    // 所有历史高亮/笔记的 locator 都按这套公式算出，线上必须只有一份实现，
    // 否则将来换偏移基准时无从比对。
    val lengths = book.chapters.map { it.estimatedTextLength }
    return BookIndex(
        chapterStartOffsets = LegacyOffsetCodec.chapterStartOffsets(lengths),
        chapterTitles = book.chapters.map { it.title },
        totalChars = LegacyOffsetCodec.totalChars(lengths),
    )
}
