package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.BreakOracle
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.layout.LayoutParagraph
import com.creationreadingassistant.feature.reader.layout.TextRuler

/**
 * TXT 全文 → 某一章的分页结果。纯 JVM，零 Android 依赖。
 *
 * 关键纪律：**段落文本必须是章文本的原样切片，偏移必须可逆。**
 * 也就是对任何段落 p 与段内偏移 k，恒有
 *
 *     chapterText[p.charOffset + k] == p.text[k]
 *
 * 进度锚点、高亮、选区全靠这条换算回全书偏移。所以：
 * - 去掉行首缩进空白（TXT 常见「　　」全角缩进，排版层自己会加 2em 缩进，
 *   不去掉会双重缩进）的方式是**切片时前移起点**，而不是 trim 后拼接 ——
 *   前者保持偏移可逆，后者会让段内偏移与原文对不上。
 * - 空行直接跳过，不产生段落，偏移记账继续。
 */
object TxtPageSource {

    /** 该章在全文中的文本切片。 */
    fun chapterTextOf(fullText: String, chapter: DocChapter): String {
        val start = chapter.startOffset.coerceIn(0, fullText.length)
        val end = (chapter.startOffset + chapter.charCount).coerceIn(start, fullText.length)
        return fullText.substring(start, end)
    }

    /**
     * 章文本 → 段落列表。charOffset 为段首在**章内**的字符偏移。
     *
     * @param chapterTitle 章标题。首个非空段若与之相同则标为 HEADING
     *        （不缩进、keep-with-next、加粗绘制）。
     */
    fun paragraphsOf(chapterText: String, chapterTitle: String?): List<LayoutParagraph> {
        val out = ArrayList<LayoutParagraph>()
        val title = chapterTitle?.trim()
        var lineStart = 0
        var isFirst = true
        while (lineStart <= chapterText.length) {
            val nl = chapterText.indexOf('\n', lineStart)
            val lineEnd = if (nl >= 0) nl else chapterText.length

            // 收缩到 [s, e)：去掉首尾空白但保持偏移可逆
            var s = lineStart
            while (s < lineEnd && chapterText[s].isWhitespace()) s++
            var e = lineEnd
            while (e > s && chapterText[e - 1].isWhitespace()) e--

            if (e > s) {
                val text = chapterText.substring(s, e)
                val role = if (isFirst && title != null && text == title) BlockRole.HEADING else BlockRole.BODY
                out.add(LayoutParagraph(text = text, role = role, charOffset = s))
                isFirst = false
            }

            if (nl < 0) break
            lineStart = nl + 1
        }
        return out
    }

    /** 整章分页。调用方负责放到后台线程。 */
    fun layoutChapter(
        fullText: String,
        chapter: DocChapter,
        cfg: LayoutConfig,
        ruler: TextRuler,
        oracle: BreakOracle,
    ): ChapterPaginator.ChapterLayout {
        val chapterText = chapterTextOf(fullText, chapter)
        val paragraphs = paragraphsOf(chapterText, chapter.title)
        return ChapterPaginator.paginate(paragraphs, cfg, ruler, oracle)
    }
}
