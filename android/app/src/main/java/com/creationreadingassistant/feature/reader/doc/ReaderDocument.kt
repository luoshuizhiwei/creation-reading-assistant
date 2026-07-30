package com.creationreadingassistant.feature.reader.doc

/**
 * 格式中立的内容块。
 *
 * 与 `domain.model.EpubBlock` 的区别：图片带 intrinsic 宽高。
 * 排版层需要知道图片尺寸才能决定占几行、要不要整块下移到次页；
 * 若排版时才去读文件头拿尺寸，就等于在排版热路径上做 IO。
 */
sealed interface DocBlock {
    data class Text(val text: String, val isHeading: Boolean = false) : DocBlock

    /**
     * Markdown 语义块容器。
     *
     * [chapter] 包含规范阅读文本、语义块树、偏移映射与目录项。
     * 搜索、TTS、Locator、选区、高亮、书签全部基于 [chapter.canonicalText] 的字符偏移。
     */
    data class Markdown(val chapter: MarkdownParser.MarkdownChapter) : DocBlock

    /** [width]/[height] 为 0 表示尺寸未知（读取失败），排版层需按占位处理。 */
    data class Image(val path: String, val width: Int, val height: Int) : DocBlock
}

/**
 * 一章的轻量描述。
 *
 * [startOffset] 与 [charCount] 的单位是**字符**，不是字节。
 * TXT 侧这两个值是精确的；EPUB 侧目前仍是基于 ZIP 解压字节数的估算
 * （中文书会偏大约 3 倍），标记在 [charCountIsEstimated] 上。
 * 按 SIDECAR-ZH 的 P5，真实字符数会随阅读逐章回填，届时该标记转为 false。
 */
data class DocChapter(
    val index: Int,
    val title: String,
    val startOffset: Int,
    val charCount: Int,
    val charCountIsEstimated: Boolean = false,
)

/**
 * 阅读文档的统一入口。TXT / EPUB / Markdown 都实现它，
 * 排版层、搜索、目录、进度计算只认这个接口，不再各自去碰解析器。
 *
 * **核心不变式**（实现必须保证，否则章内定位会漂）：
 *
 *     text(i) == blocks(i).filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }
 *
 * 这条不是洁癖。此前渲染走一套抽取、搜索走另一套，两者产出的文本长度不同，
 * 而全局偏移是按「每块长度 + 1」累加的 —— 搜索命中的位置和渲染用的位置对不上。
 * 当前搜索只跳到章所以没暴露，一旦做章内精确跳转必然跳偏。
 */
interface ReaderDocument {

    val chapters: List<DocChapter>

    /** 全书字符总数。EPUB 侧在 P5 之前是估算值。 */
    val totalChars: Int

    /** 按需取一章的内容块。**必须在 IO 线程调用。** */
    fun blocks(chapterIndex: Int): List<DocBlock>

    /** 一章的纯文本。见接口注释里的不变式。 */
    fun text(chapterIndex: Int): String =
        blocks(chapterIndex).filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }

    /** 用完释放底层资源（ZIP 句柄、缓存）。 */
    fun close() {}

    /** 全书偏移 → (章号, 章内偏移)。越界会被钳到合法范围。 */
    fun locate(globalOffset: Int): Pair<Int, Int> {
        if (chapters.isEmpty()) return 0 to 0
        val g = globalOffset.coerceAtLeast(0)
        var lo = 0
        var hi = chapters.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (chapters[mid].startOffset <= g) lo = mid else hi = mid - 1
        }
        return lo to (g - chapters[lo].startOffset).coerceAtLeast(0)
    }
}
