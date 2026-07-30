package com.creationreadingassistant.feature.reader.doc

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.logging.Logger

/**
 * Markdown 的 [ReaderDocument] 实现。
 *
 * 支持两种模式：
 * 1. **小文件模式**：全文已在内存里，按 H1/H2 切章，解析产物精确。
 * 2. **流式模式**：通过 [fromFileIndex] 工厂方法创建，不加载全文到内存。
 *    先扫描文件建立 Markdown 标题索引，再逐单元解析以计算规范文本长度，
 *    最后得到基于规范文本字符偏移的章节表。
 *
 * 坐标约定：
 * - [DocChapter.startOffset] / [DocChapter.charCount] 基于**规范阅读文本**字符偏移。
 * - [blocks] 返回的 [DocBlock.Markdown] 内含本章局部规范文本与局部偏移映射；
 *   配合 [DocChapter.startOffset] 即可换算为全书偏移。
 */
class MarkdownDocument private constructor(
    private val fullText: String?,
    private val fileSource: File?,
    private val fileIndex: TxtFileIndex?,
    private val chapterSourceRanges: List<IntRange>,
    override val chapters: List<DocChapter>,
) : ReaderDocument {

    /**
     * 小文件路径：全文 + 按 H1/H2 切章。
     */
    constructor(fullText: String) : this(
        fullText = fullText,
        fileSource = null,
        fileIndex = null,
        chapterSourceRanges = emptyList(),
        chapters = buildChaptersFromText(fullText),
    )

    companion object {
        private val logger = Logger.getLogger("MarkdownDocument")

        /** 流式模式下单个源章节的最大源字符数，必须小于 [PlainTextDocument.MAX_WINDOW_CHARS]。 */
        private const val MAX_SOURCE_CHAPTER_CHARS = 60_000

        /** 按 H1/H2 切章时的平均长度下限；低于此值则合并为单章，防止标题过多。 */
        private const val MIN_AVERAGE_CHAPTER_CHARS = 300L

        /**
         * 从流式扫描结果构建 [MarkdownDocument]，不加载全文到内存。
         *
         * 实现步骤：
         * 1. 扫描文件，识别 Markdown 标题行，建立源字符偏移章节表；
         *    超长章节会被进一步切分，确保单次读取不超过 [MAX_SOURCE_CHAPTER_CHARS]。
         * 2. 对每个章节有界读取源文本，用 [MarkdownParser] 解析，得到规范文本长度。
         * 3. 累加规范长度，重建基于规范文本偏移的 [DocChapter] 列表。
         *
         * 该方法会解析每个章节一次以量取规范长度；解析在调用线程执行，调用方应置于 IO 线程。
         */
        fun fromFileIndex(file: File, index: TxtFileIndex): MarkdownDocument {
            val sourceChapters = scanSourceChapters(file, index)
            val canonicalChapters = computeCanonicalChapters(file, index, sourceChapters)
            return MarkdownDocument(
                fullText = null,
                fileSource = file,
                fileIndex = index,
                chapterSourceRanges = sourceChapters.map { it.sourceRange },
                chapters = canonicalChapters,
            )
        }

        /**
         * 小文件：解析全文后按 H1 切章。
         *
         * 章节边界落在标题块的 canonicalRange.start 处；每个章节从该标题开始，
         * 到下一个 H1 之前（或文本末尾）结束。H2-H6 仍作为语义块渲染并进入目录，
         * 但不单独拆分为阅读器章节。
         */
        private fun buildChaptersFromText(fullText: String): List<DocChapter> {
            if (fullText.isEmpty()) {
                return listOf(DocChapter(0, "全文", 0, 0))
            }
            val chapter = MarkdownParser.parse(fullText)
            val headingBlocks = chapter.blocks.filterIsInstance<MarkdownBlock.Heading>()
                .filter { it.level == 1 }
            if (headingBlocks.isEmpty()) {
                return listOf(
                    DocChapter(
                        index = 0,
                        title = "全文",
                        startOffset = 0,
                        charCount = chapter.canonicalText.length,
                    ),
                )
            }

            val result = ArrayList<DocChapter>()
            // 标题前的前言部分
            val firstHeadingStart = headingBlocks.first().canonicalRange.first
            if (firstHeadingStart > 0) {
                result.add(
                    DocChapter(
                        index = result.size,
                        title = "开篇",
                        startOffset = 0,
                        charCount = firstHeadingStart,
                    ),
                )
            }

            for ((i, heading) in headingBlocks.withIndex()) {
                val nextStart = if (i + 1 < headingBlocks.size) {
                    headingBlocks[i + 1].canonicalRange.first
                } else {
                    chapter.canonicalText.length
                }
                val title = heading.inlines.joinCanonicalText()
                result.add(
                    DocChapter(
                        index = result.size,
                        title = title,
                        startOffset = heading.canonicalRange.first,
                        charCount = nextStart - heading.canonicalRange.first,
                    ),
                )
            }

            // 密度保护：章节过碎时合并为单章。小文件保留 H1/H2 结构，只有章节数量
            // 足够多（> 6）且平均长度过短时才合并，避免把正常短篇文档压成单章。
            if (result.size > 6) {
                val avg = chapter.canonicalText.length.toDouble() / result.size
                if (avg < MIN_AVERAGE_CHAPTER_CHARS) {
                    return listOf(
                        DocChapter(
                            index = 0,
                            title = "全文",
                            startOffset = 0,
                            charCount = chapter.canonicalText.length,
                        ),
                    )
                }
            }
            return result
        }

        /**
         * 扫描文件，识别 Markdown ATX 标题（H1），返回源字符偏移区间列表。
         *
         * 仅按 H1 分章；H2-H6 作为章内标题保留，仍可通过解析后的 headings 进入目录。
         * 超长章节会被切分为多个源单元，确保后续有界读取不溢出。
         */
        private fun scanSourceChapters(file: File, index: TxtFileIndex): List<SourceChapter> {
            val encoding = index.encoding
            val charset = resolveCharset(encoding)
            val bomSkip = detectBomLength(file, encoding)

            val marks = ArrayList<HeadingMark>()
            var charOffset = 0L

            FileInputStream(file).use { fis ->
                if (bomSkip > 0) fis.skip(bomSkip.toLong())
                BufferedReader(InputStreamReader(fis, charset), 64 * 1024).use { reader ->
                    var eof = false
                    val lineBuf = StringBuilder()
                    while (!eof) {
                        lineBuf.setLength(0)
                        var terminator = ""
                        while (true) {
                            val ch = reader.read()
                            if (ch == -1) { eof = true; break }
                            if (ch == '\n'.code) { terminator = "\n"; break }
                            if (ch == '\r'.code) {
                                reader.mark(1)
                                val next = reader.read()
                                terminator = if (next == '\n'.code) "\r\n" else { reader.reset(); "\r" }
                                break
                            }
                            lineBuf.append(ch.toChar())
                        }
                        if (eof && lineBuf.isEmpty() && terminator.isEmpty()) break
                        val line = lineBuf.toString()

                        val headingMatch = matchAtxHeading(line)
                        if (headingMatch != null && headingMatch.level == 1) {
                            marks.add(
                                HeadingMark(
                                    level = headingMatch.level,
                                    title = headingMatch.title,
                                    charStart = charOffset,
                                ),
                            )
                        }
                        charOffset += line.length + terminator.length
                    }
                }
            }

            val totalChars = charOffset
            val rawChapters = if (marks.isEmpty()) {
                listOf(RawChapter(0, "全文", 0L, totalChars))
            } else {
                val list = ArrayList<RawChapter>()
                if (marks[0].charStart > 0) {
                    list.add(RawChapter(list.size, "开篇", 0L, marks[0].charStart))
                }
                for ((i, mark) in marks.withIndex()) {
                    val nextStart = if (i + 1 < marks.size) marks[i + 1].charStart else totalChars
                    list.add(RawChapter(list.size, mark.title, mark.charStart, nextStart))
                }
                list
            }

            // 密度保护：章节过碎时合并为单章。短篇流式文件保留 H1/H2 结构。
            if (rawChapters.size > 6) {
                val avg = totalChars.toDouble() / rawChapters.size
                if (avg < MIN_AVERAGE_CHAPTER_CHARS) {
                    return listOf(
                        SourceChapter(
                            index = 0,
                            title = "全文",
                            sourceRange = 0 until totalChars.toInt(),
                        ),
                    )
                }
            }

            // 超长章节切分，确保每个源单元不超过 MAX_SOURCE_CHAPTER_CHARS
            val result = ArrayList<SourceChapter>()
            for (raw in rawChapters) {
                val length = raw.endExcl - raw.start
                if (length <= MAX_SOURCE_CHAPTER_CHARS) {
                    result.add(
                        SourceChapter(
                            index = result.size,
                            title = raw.title,
                            sourceRange = raw.start.toInt() until raw.endExcl.toInt(),
                        ),
                    )
                } else {
                    var cursor = raw.start
                    while (cursor < raw.endExcl) {
                        val end = minOf(cursor + MAX_SOURCE_CHAPTER_CHARS, raw.endExcl)
                        result.add(
                            SourceChapter(
                                index = result.size,
                                title = if (cursor == raw.start) raw.title else "${raw.title}（续）",
                                sourceRange = cursor.toInt() until end.toInt(),
                            ),
                        )
                        cursor = end
                    }
                }
            }
            return result
        }

        /**
         * 对每个源章节读取源文本并解析，得到规范文本长度，最终重建基于规范偏移的 [DocChapter]。
         */
        private fun computeCanonicalChapters(
            file: File,
            index: TxtFileIndex,
            sourceChapters: List<SourceChapter>,
        ): List<DocChapter> {
            val document = PlainTextDocument.fromFileIndex(file, index)
            var canonicalOffset = 0
            return sourceChapters.map { sourceChapter ->
                val sourceText = readSourceText(document, sourceChapter.sourceRange)
                val chapter = MarkdownParser.parse(
                    source = sourceText,
                    sourceOffsetShift = sourceChapter.sourceRange.first,
                )
                val charCount = chapter.canonicalText.length
                val docChapter = DocChapter(
                    index = sourceChapter.index,
                    title = sourceChapter.title,
                    startOffset = canonicalOffset,
                    charCount = charCount,
                )
                canonicalOffset += charCount
                docChapter
            }
        }

        /**
         * 使用 [PlainTextDocument.readWindow] 有界读取源章节文本。
         * readWindow 保证不会一次性加载全文，且最大窗口受 [PlainTextDocument.MAX_WINDOW_CHARS] 限制。
         */
        private fun readSourceText(document: PlainTextDocument, range: IntRange): String {
            val start = range.first.coerceAtLeast(0)
            val count = (range.last + 1 - start).coerceAtLeast(0)
            if (count == 0) return ""
            val text = document.readWindow(start, count)
            // readWindow 可能因编码边界返回略多于 count 的字符，精确截取到 count
            return if (text.length > count) text.substring(0, count) else text
        }

        private fun detectBomLength(file: File, encoding: String): Int {
            if (file.length() < 2) return 0
            return when (encoding) {
                "UTF-8" -> if (file.length() >= 3 && readBytes(file, 3).contentEquals(UTF8_BOM)) 3 else 0
                "UTF-16LE" -> if (readBytes(file, 2).contentEquals(UTF16LE_BOM)) 2 else 0
                "UTF-16BE" -> if (readBytes(file, 2).contentEquals(UTF16BE_BOM)) 2 else 0
                else -> 0
            }
        }

        private fun readBytes(file: File, count: Int): ByteArray {
            return FileInputStream(file).use { fis ->
                val buf = ByteArray(count)
                var read = 0
                while (read < count) {
                    val n = fis.read(buf, read, count - read)
                    if (n < 0) break
                    read += n
                }
                if (read == count) buf else buf.copyOf(read)
            }
        }

        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        private val UTF16LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        private val UTF16BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

        private fun resolveCharset(encoding: String): Charset = when (encoding) {
            "UTF-8" -> Charsets.UTF_8
            "UTF-16LE" -> Charsets.UTF_16LE
            "UTF-16BE" -> Charsets.UTF_16BE
            else -> Charset.forName("GB18030")
        }

        private fun matchAtxHeading(line: String): HeadingMatch? {
            val trimmedStart = line.takeWhile { it == '#' }
            val level = trimmedStart.length
            if (level == 0 || level > 6) return null
            val afterHashes = line.drop(level)
            if (afterHashes.isEmpty() || !afterHashes[0].isWhitespace()) return null
            var title = afterHashes.trimStart()
            // 去除行尾 optional 的 #
            title = title.trimEnd { it == '#' || it.isWhitespace() }
            if (title.isBlank()) return null
            return HeadingMatch(level, title)
        }

        private fun List<MdInline>.joinCanonicalText(): String = buildString {
            for (inline in this@joinCanonicalText) append(inline.canonicalText())
        }

        private fun MdInline.canonicalText(): String = when (this) {
            is MdInline.Text -> text
            is MdInline.Code -> text
            is MdInline.Strong -> children.joinCanonicalText()
            is MdInline.Emphasis -> children.joinCanonicalText()
            is MdInline.Strikethrough -> children.joinCanonicalText()
            is MdInline.Link -> children.joinCanonicalText()
            is MdInline.Image -> alt
            is MdInline.HardLineBreak -> "\n"
            is MdInline.SoftLineBreak -> " "
        }
    }

    override val totalChars: Int
        get() = chapters.sumOf { it.charCount.toLong() }.toInt()

    override fun blocks(chapterIndex: Int): List<DocBlock> {
        if (fullText != null) {
            val chapter = MarkdownParser.parse(fullText)
            return listOf(DocBlock.Markdown(chapter))
        }
        val range = chapterSourceRanges.getOrNull(chapterIndex) ?: return emptyList()
        val document = fileIndex?.let { PlainTextDocument.fromFileIndex(fileSource!!, it) }
            ?: return emptyList()
        val sourceText = readSourceText(document, range)
        val chapter = MarkdownParser.parse(sourceText, sourceOffsetShift = range.first)
        return listOf(DocBlock.Markdown(chapter))
    }

    override fun text(chapterIndex: Int): String {
        val blocks = blocks(chapterIndex)
        return blocks.filterIsInstance<DocBlock.Markdown>()
            .joinToString("\n") { it.chapter.canonicalText }
    }

    override fun close() {
        // 无持久资源；RandomAccessFile 按调用打开/关闭
    }

    /**
     * 将旧版「纯文本 Markdown」存储的源文本偏移迁移到新的规范文本偏移。
     *
     * 旧版没有 Markdown 语义解析，进度按原始 Markdown 源字符计；新版按移除标记后的
     * [canonicalText] 计。该方法把源偏移解析到对应章节后，用 [MarkdownOffsetMap.toCanonical]
     * 换算为规范偏移。若 [sourceOffset] 已经是规范偏移且落在新坐标系内，结果会近似自身
     * （略有偏差），因此调用方应通过 [ReaderDocumentLoader] 的 `space` 字段区分新旧数据。
     */
    fun migrateLegacyOffset(sourceOffset: Int): Int {
        val clamped = sourceOffset.coerceAtLeast(0)
        if (fullText != null) {
            val chapter = MarkdownParser.parse(fullText)
            return chapter.offsetMap.toCanonical(clamped).coerceIn(0, totalChars)
        }
        val file = fileSource ?: return clamped.coerceAtMost(totalChars)
        val index = fileIndex ?: return clamped.coerceAtMost(totalChars)
        // 定位源偏移所在的源章节
        val sourceChapterIdx = chapterSourceRanges.indexOfFirst { clamped in it }
            .takeIf { it >= 0 }
            ?: chapterSourceRanges.indexOfLast { it.first <= clamped }
                .takeIf { it >= 0 }
                ?: 0
        val range = chapterSourceRanges.getOrNull(sourceChapterIdx) ?: return clamped.coerceAtMost(totalChars)
        val document = PlainTextDocument.fromFileIndex(file, index)
        val text = readSourceText(document, range)
        val chapter = MarkdownParser.parse(text, sourceOffsetShift = range.first)
        return chapter.offsetMap.toCanonical(clamped).coerceIn(0, totalChars)
    }

    private data class RawChapter(
        val index: Int,
        val title: String,
        val start: Long,
        val endExcl: Long,
    )

    private data class SourceChapter(
        val index: Int,
        val title: String,
        val sourceRange: IntRange,
    )

    private data class HeadingMark(
        val level: Int,
        val title: String,
        val charStart: Long,
    )

    private data class HeadingMatch(
        val level: Int,
        val title: String,
    )
}
