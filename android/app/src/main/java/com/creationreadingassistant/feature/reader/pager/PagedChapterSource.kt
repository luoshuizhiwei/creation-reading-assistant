package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter

/**
 * 翻页引擎的章节内容来源。TXT / EPUB 的差别全部收在这个接口后面，
 * 控制器与宿主只认它。
 *
 * 偏移语义（必须与各格式**既有的 locator 语义一致**，否则历史高亮会错位）：
 * - TXT：全书就是一个大字符串，[chapterStartAbs] 是精确偏移。
 * - EPUB：全书偏移是 LegacyOffsetCodec 按「解压字节数估算 + 每章 +1」算出的
 *   **估算基准**。它不精确（中文书偏大约 3 倍），但选区/高亮/进度从一开始
 *   就按这套基准落库 —— 翻页引擎必须沿用同一基准，绝不能自算一套。
 */
interface PagedChapterSource {

    val chapterCount: Int

    fun chapterTitle(index: Int): String

    /** 该章起点的全书偏移（TXT 精确 / EPUB 估算基准）。 */
    fun chapterStartAbs(index: Int): Int

    /** 全书总字符数（进度百分比的分母；EPUB 为估算值）。 */
    val totalChars: Int

    /**
     * 取整章纯文本。**可能做 IO（EPUB 解压），必须在后台线程调。**
     *
     * EPUB 侧必须满足冻结不变式：
     * `text == blocks.filterIsInstance<Text>().joinToString("\n") { it.text }`
     * —— 搜索、TTS、选区偏移全都建立在这条之上。
     */
    fun loadChapterText(index: Int): String

    /** 全书偏移 → 章号（二分，取最后一个起点 ≤ offset 的章）。 */
    fun chapterIndexFor(absOffset: Int): Int {
        if (chapterCount == 0) return 0
        var lo = 0
        var hi = chapterCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (chapterStartAbs(mid) <= absOffset) lo = mid else hi = mid - 1
        }
        return lo
    }
}

/** TXT：全书一个大字符串 + 已识别的章节表。 */
class TxtChapterSource(
    private val fullText: String,
    private val chapters: List<DocChapter>,
) : PagedChapterSource {

    override val chapterCount: Int get() = chapters.size
    override fun chapterTitle(index: Int): String = chapters.getOrNull(index)?.title ?: ""
    override fun chapterStartAbs(index: Int): Int = chapters.getOrNull(index)?.startOffset ?: 0
    override val totalChars: Int get() = fullText.length

    override fun loadChapterText(index: Int): String {
        val c = chapters.getOrNull(index) ?: return ""
        return TxtPageSource.chapterTextOf(fullText, c)
    }
}

/**
 * EPUB：按需解压取章文本。
 *
 * @param chapterStartOffsets 来自 LegacyOffsetCodec 的既有估算基准（勿自算）
 * @param loadText 按章取纯文本，调用方保证与渲染/搜索同源
 *        （`blocks.filterIsInstance<Text>().joinToString("\n")`）
 */
class EpubChapterSource(
    private val titles: List<String>,
    private val chapterStartOffsets: List<Int>,
    override val totalChars: Int,
    private val loadText: (Int) -> String,
) : PagedChapterSource {

    override val chapterCount: Int get() = titles.size
    override fun chapterTitle(index: Int): String = titles.getOrNull(index) ?: ""
    override fun chapterStartAbs(index: Int): Int = chapterStartOffsets.getOrNull(index) ?: 0
    override fun loadChapterText(index: Int): String = loadText(index)
}
