package com.creationreadingassistant.feature.reader.doc

import java.io.File
import java.io.RandomAccessFile
import java.util.logging.Logger

/**
 * TXT / Markdown 的 [ReaderDocument] 实现。
 *
 * 支持两种模式：
 * 1. **小文件模式**：全文已在内存里（TXT 有 8 MB 上限），章节偏移与字符数都是精确值。
 * 2. **流式模式**：通过 [fromFileIndex] 工厂方法创建，不加载全文到内存，
 *    而是按需从文件读取章节内容。适用于超过 8 MB 的大文件。
 *
 * 段落切分规则：按空行分段；单个换行视为段内换行，转成一个空格。
 * 这样诗歌、书信不会被拆成一堆碎块，而普通小说的自然段又能正确分开。
 */
class PlainTextDocument private constructor(
    private val fullText: String,
    private val tocRuleId: String,
    // Streaming mode properties (all null for small-file path)
    private val fileSource: File?,
    private val fileIndex: TxtFileIndex?,
    private val fileEncoding: String?,
    /** Pre-computed chapter list for streaming mode (null for small-file mode). */
    precomputedChapters: List<DocChapter>?,
) : ReaderDocument {

    /**
     * 小文件路径：全文 + TOC 规则。
     */
    constructor(
        fullText: String,
        tocRuleId: String = "builtin",
    ) : this(
        fullText = fullText,
        tocRuleId = tocRuleId,
        fileSource = null,
        fileIndex = null,
        fileEncoding = null,
        precomputedChapters = null,
    )

    companion object {
        private val logger = Logger.getLogger("PlainTextDocument")

        /** 有界窗口读取的最大字符数上限。 */
        const val MAX_WINDOW_CHARS = 100_000

        /**
         * 从流式扫描结果构建 [PlainTextDocument]，不加载全文到内存。
         * 章节内容按需通过 [File] 的 RandomAccess 读取。
         */
        fun fromFileIndex(
            file: File,
            index: TxtFileIndex,
        ): PlainTextDocument {
            val docChapters = index.chapters.map { entry ->
                DocChapter(
                    index = entry.index,
                    title = entry.title,
                    startOffset = entry.charStart.toInt(),
                    charCount = entry.charCount.toInt(),
                    charCountIsEstimated = false,
                )
            }
            return PlainTextDocument(
                fullText = "",
                tocRuleId = index.detectedRuleId ?: "builtin",
                fileSource = file,
                fileIndex = index,
                fileEncoding = index.encoding,
                precomputedChapters = docChapters,
            )
        }
    }

    // ── Internal state ─────────────────────────────────────────────────

    /** Non-null only in small-file mode. */
    private val detected: List<TxtChapterDetector.Chapter>? =
        if (fileIndex == null) TxtChapterDetector.detect(fullText, tocRuleId) else null

    private val _isStreaming: Boolean get() = fileSource != null

    /**
     * 读取单元列表，由外部（如 ReaderViewModel）在构建后设置。
     * 用于 [unitIndexForOffset] 等需要全局读取单元元数据的方法。
     */
    var readingUnits: List<ReadingUnit> = emptyList()

    // ── ReaderDocument implementation ──────────────────────────────────

    override val chapters: List<DocChapter> = detected?.mapIndexed { i, c ->
        DocChapter(
            index = i,
            title = c.title,
            startOffset = c.startOffset,
            charCount = c.charCount,
            charCountIsEstimated = false,
        )
    } ?: precomputedChapters.orEmpty()

    override val totalChars: Int =
        if (_isStreaming) fileIndex!!.totalCharCount.toInt()
        else fullText.length

    override fun blocks(chapterIndex: Int): List<DocBlock> {
        if (_isStreaming) {
            val raw = readChapterString(chapterIndex)
            return splitParagraphs(raw)
        }
        val c = detected!!.getOrNull(chapterIndex) ?: return emptyList()
        val raw = fullText.substring(c.startOffset, c.endOffset.coerceAtMost(fullText.length))
        return splitParagraphs(raw)
    }

    /** 章内纯文本。这里直接复用与 [blocks] 相同的切分，保证接口的不变式成立。 */
    override fun text(chapterIndex: Int): String =
        blocks(chapterIndex).filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }

    override fun close() {
        // No persistent resources to release; RandomAccessFile is opened/closed per call
    }

    // ── Bounded read APIs ─────────────────────────────────────────────

    /**
     * 有界窗口读取：从 [charStart] 开始读取最多 [maxChars] 个字符。
     * [maxChars] 被硬限制为 [MAX_WINDOW_CHARS]。
     *
     * 流式模式通过 checkpoints 将字符偏移映射为字节偏移，按需从文件读取。
     * 小文件模式直接从 fullText 切片。
     */
    fun readWindow(charStart: Int, maxChars: Int): String {
        val clamped = maxChars.coerceAtMost(MAX_WINDOW_CHARS)
        val start = charStart.coerceIn(0, totalChars)
        if (clamped <= 0 || start >= totalChars) return ""

        // 小文件模式
        if (!_isStreaming) {
            val end = (start + clamped).coerceAtMost(fullText.length)
            return fullText.substring(start, end)
        }

        // 流式模式：通过 checkpoints 定位字节偏移
        val checkpoints = fileIndex!!.checkpoints
        val charset = charset(fileEncoding!!)
        val endChar = (start + clamped).coerceAtMost(totalChars)

        // 找到 start 的 floor checkpoint，从该字节偏移开始读取
        val startCpInfo = findFloorCheckpoint(checkpoints, start.toLong(), fileIndex)
        // 找到 endChar 的 floor checkpoint，确定需要读取到的字节位置
        val endCpInfo = findFloorCheckpoint(checkpoints, endChar.toLong(), fileIndex)

        // 从 floor checkpoint 的字节偏移开始读取
        val readStartByte = startCpInfo.byteOffset
        // 读取到 ceil checkpoint 的字节偏移（或文件末尾）
        val readEndByte = (endCpInfo.ceilByteOffset + 16).coerceAtMost(fileSource!!.length())
        val readLength = (readEndByte - readStartByte).toInt().coerceAtLeast(0)
        if (readLength <= 0) return ""

        val bytes = RandomAccessFile(fileSource, "r").use { raf ->
            raf.seek(readStartByte)
            val buf = ByteArray(readLength)
            raf.readFully(buf)
            buf
        }

        var decoded = String(bytes, charset)

        // 处理编码边界：确保不在多字节字符中间截断
        decoded = trimToEncodingBoundary(decoded, fileEncoding!!)

        // 裁剪到请求的字符范围：跳过 floor checkpoint 到 start 之间的字符
        val skipChars = (start - startCpInfo.charOffset).toInt().coerceAtLeast(0)
        if (skipChars > 0 && decoded.length > skipChars) {
            decoded = decoded.substring(skipChars)
        } else if (skipChars > 0) {
            return ""
        }

        // 最终裁剪到请求的字符数
        if (decoded.length > clamped) {
            decoded = decoded.substring(0, clamped)
        }
        return decoded
    }

    /**
     * 围绕 [charOffset] 的有界窗口读取。
     * 从 max(0, charOffset - before) 开始，读取 min(before + after, MAX_WINDOW_CHARS) 个字符。
     */
    fun readWindowAround(charOffset: Int, before: Int, after: Int): String {
        val windowStart = (charOffset - before).coerceAtLeast(0)
        val windowSize = (before + after).coerceAtMost(MAX_WINDOW_CHARS)
        return readWindow(windowStart, windowSize)
    }

    /**
     * 二分查找包含 [charOffset] 的 ReadingUnit。
     * 返回 unit index，如果未找到返回 -1。
     */
    fun unitIndexForOffset(charOffset: Int): Int {
        if (readingUnits.isEmpty()) return -1
        var lo = 0
        var hi = readingUnits.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (readingUnits[mid].charStart <= charOffset) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        // 验证 charOffset 确实在该 unit 范围内
        if (result >= 0) {
            val unit = readingUnits[result]
            if (charOffset >= unit.charStart + unit.charCount) return -1
        }
        return result
    }

    // ── Bounded read helpers ──────────────────────────────────────────

    /**
     * Checkpoint 查找结果：包含 floor checkpoint 的字符偏移、字节偏移，
     * 以及 ceil checkpoint 的字节偏移（用于确定读取范围）。
     */
    private data class CheckpointInfo(
        val charOffset: Long,
        val byteOffset: Long,
        val ceilByteOffset: Long,
    )

    /**
     * 查找 charOffset 的 floor checkpoint。
     * 返回 CheckpointInfo，包含 floor checkpoint 的信息和 ceil checkpoint 的字节偏移。
     */
    private fun findFloorCheckpoint(
        checkpoints: List<CharByteCheckpoint>,
        charOffset: Long,
        index: TxtFileIndex,
    ): CheckpointInfo {
        if (checkpoints.isEmpty()) {
            // 无 checkpoint：使用文件起始和全局估算
            val lastChapter = index.chapters.last()
            val fileEndByte = lastChapter.byteStart + lastChapter.byteLength
            return CheckpointInfo(0L, 0L, fileEndByte)
        }
        // 二分查找 floor checkpoint
        var lo = 0
        var hi = checkpoints.size - 1
        var floorIdx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (checkpoints[mid].charOffset <= charOffset) {
                floorIdx = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        // charOffset 在第一个 checkpoint 之前
        if (floorIdx < 0) {
            val first = checkpoints.first()
            return CheckpointInfo(0L, 0L, first.byteOffset)
        }
        val floor = checkpoints[floorIdx]
        // ceil checkpoint 的字节偏移（如果存在）
        val ceilByte = if (floorIdx + 1 < checkpoints.size) {
            checkpoints[floorIdx + 1].byteOffset
        } else {
            // 最后一个 checkpoint：使用文件末尾
            val lastChapter = index.chapters.last()
            lastChapter.byteStart + lastChapter.byteLength
        }
        return CheckpointInfo(floor.charOffset, floor.byteOffset, ceilByte)
    }

    /**
     * 使用 checkpoints 二分查找 charOffset 对应的字节偏移。
     * 通过线性插值在两个相邻 checkpoint 之间估算精确字节偏移。
     * 如果 charOffset 在所有 checkpoint 之前，使用第一个 checkpoint 向前估算。
     * 如果 charOffset 在所有 checkpoint 之后，使用最后一个 checkpoint 向后估算。
     */
    private fun checkpointByteOffset(
        checkpoints: List<CharByteCheckpoint>,
        charOffset: Long,
        index: TxtFileIndex,
    ): Long {
        if (checkpoints.isEmpty()) {
            // 无 checkpoint：按平均字节密度估算
            if (index.totalCharCount == 0L) return 0L
            val lastChapter = index.chapters.last()
            val fileEndByte = lastChapter.byteStart + lastChapter.byteLength
            return (charOffset * fileEndByte / index.totalCharCount).coerceAtLeast(0L)
        }
        // 找到 floor checkpoint（charOffset <= 目标的最大 checkpoint）
        var lo = 0
        var hi = checkpoints.size - 1
        var floorIdx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (checkpoints[mid].charOffset <= charOffset) {
                floorIdx = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        // charOffset 在第一个 checkpoint 之前：向前线性估算
        if (floorIdx < 0) {
            val first = checkpoints.first()
            if (first.charOffset == 0L) return first.byteOffset
            // 使用第一和第二个 checkpoint 估算密度
            if (checkpoints.size >= 2) {
                val second = checkpoints[1]
                val charDensity = (second.byteOffset - first.byteOffset).toDouble() /
                    (second.charOffset - first.charOffset).coerceAtLeast(1L)
                return (first.byteOffset + (charOffset - first.charOffset) * charDensity).toLong()
                    .coerceAtLeast(0L)
            }
            // 只有一个 checkpoint，使用全局密度
            val lastChapter = index.chapters.last()
            val fileEndByte = lastChapter.byteStart + lastChapter.byteLength
            val globalDensity = fileEndByte.toDouble() / index.totalCharCount.coerceAtLeast(1L)
            return (charOffset * globalDensity).toLong().coerceAtLeast(0L)
        }
        val floor = checkpoints[floorIdx]
        // 精确命中
        if (floor.charOffset == charOffset) return floor.byteOffset
        // 如果有下一个 checkpoint，在两个 checkpoint 之间线性插值
        if (floorIdx + 1 < checkpoints.size) {
            val ceil = checkpoints[floorIdx + 1]
            val charSpan = (ceil.charOffset - floor.charOffset).coerceAtLeast(1L)
            val byteSpan = ceil.byteOffset - floor.byteOffset
            val charDelta = charOffset - floor.charOffset
            return floor.byteOffset + (byteSpan * charDelta / charSpan)
        }
        // 最后一个 checkpoint 之后：使用全局平均密度向后估算
        val lastChapter = index.chapters.last()
        val fileEndByte = lastChapter.byteStart + lastChapter.byteLength
        val remainingChars = index.totalCharCount - floor.charOffset
        if (remainingChars <= 0L) return floor.byteOffset
        val density = (fileEndByte - floor.byteOffset).toDouble() / remainingChars.coerceAtLeast(1L)
        return (floor.byteOffset + (charOffset - floor.charOffset) * density).toLong()
            .coerceAtMost(fileEndByte)
    }

    /**
     * 确保解码后的字符串在编码边界上完整截断。
     * 处理 UTF-8 不完整多字节序列和 UTF-16 不完整代理对。
     */
    private fun trimToEncodingBoundary(text: String, encoding: String): String {
        if (text.isEmpty()) return text
        return when (encoding) {
            "UTF-8" -> {
                // 检查末尾是否有不完整的 UTF-8 序列
                // 在 Java String (UTF-16) 中表现为末尾的 U+FFFD
                val trimmed = text.trimEnd('\uFFFD')
                trimmed
            }
            "UTF-16LE", "UTF-16BE", "UTF-16" -> {
                // 检查末尾是否有孤立的高代理
                if (text.isNotEmpty() && text.last().isHighSurrogate()) {
                    text.dropLast(1)
                } else {
                    text
                }
            }
            else -> text // GB18030: Java 解码器通常能正确处理
        }
    }

    // ── Streaming helpers ──────────────────────────────────────────────

    /**
     * 按读取单元加载文本。流式模式通过 RandomAccessFile 按字节区间读取，
     * 小文件模式从 fullText 切片。
     *
     * After decoding, a safety check logs a warning if U+FFFD replacement
     * characters are detected (indicating byte boundaries may not align
     * with encoding boundaries).
     */
    fun readUnit(unit: ReadingUnit): String {
        if (fileSource != null && unit.byteStart != null && unit.byteLength != null) {
            return RandomAccessFile(fileSource, "r").use { raf ->
                raf.seek(unit.byteStart)
                val bytes = ByteArray(unit.byteLength)
                raf.readFully(bytes)
                val decoded = String(bytes, charset(fileEncoding!!))
                // Safety check: warn if decoding introduced replacement characters
                val replacementCount = decoded.count { it == '\uFFFD' }
                if (replacementCount > 0) {
                    logger.warning("readUnit(${unit.unitIndex}): decoded text contains " +
                        "$replacementCount U+FFFD replacement character(s) — " +
                        "byte boundaries may not align with encoding boundaries")
                }
                decoded
            }
        }
        if (fileSource != null) {
            return readWindow(unit.charStart, unit.charCount)
        }
        // 小文件模式：从 fullText 切片
        val start = unit.charStart
        val end = (start + unit.charCount).coerceAtMost(fullText.length)
        return fullText.substring(start, end)
    }

    /**
     * Read a chapter's text from the file as a String.
     * Uses [RandomAccessFile] to seek to the chapter's byte offset and read exactly
     * [ChapterEntry.byteLength] bytes, then decodes with the file's encoding.
     */
    private fun readChapterString(chapterIndex: Int): String {
        val entry = fileIndex!!.chapters[chapterIndex]
        val bytes = readChapterBytes(chapterIndex)
        return String(bytes, charset(fileEncoding!!))
    }

    private fun readChapterBytes(chapterIndex: Int): ByteArray {
        val entry = fileIndex!!.chapters[chapterIndex]
        return RandomAccessFile(fileSource!!, "r").use { raf ->
            raf.seek(entry.byteStart)
            val bytes = ByteArray(entry.byteLength)
            raf.readFully(bytes)
            bytes
        }
    }

    private fun splitParagraphs(raw: String): List<DocBlock> {
        val out = ArrayList<DocBlock>()
        val sb = StringBuilder()
        var pendingBlank = false
        var first = true

        fun flush(isHeading: Boolean) {
            val t = sb.toString().trim()
            sb.setLength(0)
            if (t.isNotEmpty()) out.add(DocBlock.Text(t, isHeading))
        }

        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                pendingBlank = true
                continue
            }
            // 章节标题独占块，并标记为 heading —— 排版层据此加大字号、做 keep-with-next
            if (first && TxtChapterDetector.isChapterTitle(trimmed, tocRuleId)) {
                flush(false)
                out.add(DocBlock.Text(trimmed, isHeading = true))
                first = false
                pendingBlank = false
                continue
            }
            first = false
            if (pendingBlank) {
                flush(false)
                pendingBlank = false
            } else if (sb.isNotEmpty()) {
                // 段内换行：中文正文不需要空格，直接接上；西文之间留一个空格
                val last = sb.last()
                if (last.isLetterOrDigit() && trimmed.first().isLetterOrDigit() &&
                    (last.code < 0x2E80 || trimmed.first().code < 0x2E80)
                ) {
                    sb.append(' ')
                }
            }
            sb.append(trimmed)
        }
        flush(false)
        return out
    }
}
