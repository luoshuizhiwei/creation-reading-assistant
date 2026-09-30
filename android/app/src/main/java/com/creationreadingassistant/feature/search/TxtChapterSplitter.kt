package com.creationreadingassistant.feature.search

import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector

/**
 * 章节切片：切分后的一章正文标题+文本，供逐章全文索引用。
 *
 * @property logicalIndex 逻辑章序，0 基。
 * @property title 章节标题。
 * @property body 章节正文（截断至安全上限内）。
 * @property startOffset 该章在全书文本中的起始字符偏移，真实字符量纲，用于换算全局精确跳转位置。
 * @property truncated 该章正文是否因超过最大字符上限而被截断。
 */
data class TxtChapterSlice(
    val logicalIndex: Int,
    val title: String,
    val body: String,
    val startOffset: Int = 0,
    val truncated: Boolean = false,
)

/**
 * 纯文本章节物理切分纯算法组件。
 *
 * 脱离 Android Context 与数据库 DAO，纯基于字符串与章节识别规则执行安全切片与防超长截断保护。
 */
object TxtChapterSplitter {

    /** 单章正文超长截断（索引不求无损，超长章只索前 4 万字，避免单章分词撑爆内存）。 */
    const val DEFAULT_MAX_CHARS_PER_CHAPTER = 40_000

    /**
     * 用 [TxtChapterDetector] 把纯文本正文按逻辑章切分。
     *
     * 即使识别失败、densityGuard 作废、整段退化，也保证至少返回一片「全文」，
     * 下游永远不会拿到空列表。
     *
     * @param fullText 全书纯文本内容。
     * @param tocRuleId 目录规则 ID（null 时回退 "builtin"）。
     * @param maxCharsPerChapter 单章最大安全索引字数上限。
     */
    fun split(
        fullText: String,
        tocRuleId: String? = null,
        maxCharsPerChapter: Int = DEFAULT_MAX_CHARS_PER_CHAPTER,
    ): List<TxtChapterSlice> {
        val detected = TxtChapterDetector.detect(fullText, tocRuleId ?: "builtin")
        if (detected.isEmpty()) {
            return listOf(
                TxtChapterSlice(
                    logicalIndex = 0,
                    title = "全文",
                    body = fullText.take(maxCharsPerChapter),
                    startOffset = 0,
                    truncated = fullText.length > maxCharsPerChapter,
                ),
            )
        }
        return detected.mapIndexed { i, c ->
            val endExclusive = (c.startOffset + c.charCount).coerceAtMost(fullText.length)
            val safeStart = c.startOffset.coerceIn(0, fullText.length)
            val bodyRaw = if (safeStart < endExclusive) fullText.substring(safeStart, endExclusive) else ""
            TxtChapterSlice(
                logicalIndex = i,
                title = c.title,
                body = bodyRaw.take(maxCharsPerChapter),
                startOffset = safeStart,
                truncated = bodyRaw.length > maxCharsPerChapter,
            )
        }
    }
}
