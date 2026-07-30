package com.creationreadingassistant.feature.reader.doc

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.feature.reader.PlainTextDecoder
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Streaming scanner for large TXT / Markdown files.
 *
 * Performs a single sequential pass over the file to build a [TxtFileIndex]:
 * - Detects encoding from BOM / UTF-8 validation (delegates to [PlainTextDecoder])
 * - Tracks both byte offsets and character offsets simultaneously
 * - Detects chapter headings via [TxtChapterDetector.isChapterTitle]
 * - Applies density protection: if average chapter length < 300 chars, falls back
 *   to a single "全文" chapter (same policy as [PlainTextDocument])
 * - Collects [CharByteCheckpoint]s at chapter boundaries and every
 *   [MAX_UNIT_CHARS][ReadingUnitBuilder.MAX_UNIT_CHARS] within long chapters
 *
 * Memory footprint: only one line of text is held at a time, plus the resulting
 * index (~300 KB for a 50 MB / 5000-chapter file).
 */
object TxtFileScanner {

    /** Average chapter length threshold for density protection. */
    private const val MIN_AVERAGE_CHAPTER_CHARS = 300L

    /** Maximum bytes to read for encoding detection header. */
    private const val HEADER_SIZE = 8192

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Scan a file identified by [Uri] via [ContentResolver][Context.contentResolver].
     * The input stream is automatically closed after scanning.
     */
    fun scan(context: Context, uri: Uri, ruleId: String = "builtin"): TxtFileIndex {
        val rawStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open input stream for uri: $uri")
        return rawStream.use { scan(it, ruleId) }
    }

    /**
     * Scan a [File] on the local filesystem.
     * The file input stream is automatically closed after scanning.
     */
    fun scan(file: File, ruleId: String = "builtin"): TxtFileIndex {
        return FileInputStream(file).use { scan(it, ruleId) }
    }

    /**
     * Core scan entry point. Reads [inputStream] sequentially, detects encoding,
     * identifies chapters, and returns a [TxtFileIndex].
     *
     * **Note:** The caller is responsible for closing [inputStream] after this
     * method returns. This method fully consumes the stream but does NOT close it.
     */
    fun scan(inputStream: InputStream, ruleId: String = "builtin"): TxtFileIndex {
        // 1. Buffer the stream so we can mark/reset for header inspection
        val buffered = if (inputStream is BufferedInputStream) inputStream
                       else BufferedInputStream(inputStream, HEADER_SIZE)

        // 2. Read header for encoding detection
        buffered.mark(HEADER_SIZE)
        val header = ByteArray(HEADER_SIZE)
        val headerBytesRead = readFully(buffered, header, HEADER_SIZE)
        buffered.reset()

        val encodingResult = PlainTextDecoder.detectEncoding(header, headerBytesRead)
        val charset = resolveCharset(encodingResult.encoding)
        val bomSkip = encodingResult.bomLength

        // 3. Skip BOM bytes if present
        if (bomSkip > 0) {
            val skipped = buffered.skip(bomSkip.toLong())
            if (skipped < bomSkip) {
                // File is shorter than BOM — treat as empty
                return emptyIndex(encodingResult.encoding, ruleId)
            }
        }

        // 4. Line-by-line scan with explicit terminator tracking.
        //
        // ⚠️ 不能用 BufferedReader.forEachLine + CountingInputStream 组合取每行字节偏移：
        // BufferedReader 会一次性预读 64KB，底层流计数在读第一行时就已经跑到缓冲区末尾，
        // 得到的 byteStart 全部错位（曾导致所有章节 byteLength=0、正文读出空串）。
        // 正确做法：字节偏移由「每行文本 + 实际行终止符」按检测出的编码重新编码累加，
        // 与 RandomAccessFile.seek 使用的绝对文件位置一致（起点含 BOM 长度）。
        val reader = BufferedReader(InputStreamReader(buffered, charset), 64 * 1024)

        // Accumulators
        val chapterMarks = ArrayList<ChapterMark>()
        var charOffset = 0L
        var byteOffset = bomSkip.toLong() // absolute file position (BOM included)
        val lineBuf = StringBuilder()
        var eof = false

        // Checkpoint collection — recorded at actual line boundary positions
        // so that byte ranges between consecutive checkpoints decode to exactly
        // the character count between those checkpoints.
        val checkpoints = ArrayList<CharByteCheckpoint>()
        var nextCheckpointCharOffset = ReadingUnitBuilder.MAX_UNIT_CHARS.toLong()

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
            // 文件读尽且本轮没有任何内容（例如文件以换行结尾）：不产生虚假的空行。
            if (eof && lineBuf.isEmpty() && terminator.isEmpty()) break

            val line = lineBuf.toString()

            // Check if this line is a chapter title
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()
                && trimmed.length <= MAX_TITLE_LENGTH
                && TxtChapterDetector.isChapterTitle(trimmed, ruleId)
            ) {
                chapterMarks.add(
                    ChapterMark(
                        title = trimmed,
                        charStart = charOffset,
                        byteStart = byteOffset,
                    )
                )
                // Checkpoint at chapter boundary (start of new chapter).
                // Only add when charOffset > 0 to avoid duplicating the
                // initial checkpoint that will be added at position 0.
                if (charOffset > 0) {
                    checkpoints.add(CharByteCheckpoint(charOffset, byteOffset))
                }
                // Reset checkpoint grid for new chapter
                nextCheckpointCharOffset = charOffset + ReadingUnitBuilder.MAX_UNIT_CHARS
            }

            // Advance offsets by what was actually consumed（末行无换行不再虚增 +1）。
            charOffset += line.length + terminator.length
            byteOffset += (line + terminator).toByteArray(charset).size

            // Checkpoint at MAX_UNIT_CHARS boundary within long chapters.
            // Record at the ACTUAL line boundary position (charOffset, byteOffset)
            // so the checkpoint's char and byte offsets are perfectly aligned.
            while (charOffset >= nextCheckpointCharOffset) {
                checkpoints.add(CharByteCheckpoint(charOffset, byteOffset))
                nextCheckpointCharOffset += ReadingUnitBuilder.MAX_UNIT_CHARS
            }
        }

        val totalBytes = byteOffset // absolute end position (BOM included)
        val totalChars = charOffset

        // Add initial checkpoint at text start (before any content).
        // Done after the loop so we can skip it when the first line is a
        // chapter title (which already added a checkpoint at charOffset=0).
        if (charOffset > 0 && (checkpoints.isEmpty() || checkpoints[0].charOffset > 0)) {
            checkpoints.add(0, CharByteCheckpoint(0L, bomSkip.toLong()))
        }
        // Add final checkpoint at EOF to close the last unit exactly.
        if (charOffset > 0 && (checkpoints.isEmpty() || checkpoints.last().charOffset < charOffset)) {
            checkpoints.add(CharByteCheckpoint(charOffset, byteOffset))
        }

        // 5. Build chapter list
        val chapters = buildChapterList(
            marks = chapterMarks,
            totalChars = totalChars,
            totalBytes = totalBytes,
            bomSkip = bomSkip,
            ruleId = ruleId,
        )

        return TxtFileIndex(
            chapters = chapters,
            totalCharCount = totalChars,
            encoding = encodingResult.encoding,
            detectedRuleId = ruleId,
            checkpoints = checkpoints,
        )
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private const val MAX_TITLE_LENGTH = 40

    private data class ChapterMark(
        val title: String,
        val charStart: Long,
        val byteStart: Long,
    )

    /**
     * Build the final chapter list from detected marks.
     * Applies density protection: if average chapter length is too short,
     * falls back to a single "全文" chapter.
     */
    private fun buildChapterList(
        marks: List<ChapterMark>,
        totalChars: Long,
        totalBytes: Long,
        bomSkip: Int,
        ruleId: String,
    ): List<ChapterEntry> {
        // byteStart/byteLength 均为绝对文件位置（正文起点在 BOM 之后），
        // 与 PlainTextDocument.readChapterBytes 的 RandomAccessFile.seek 语义一致。
        if (marks.isEmpty()) {
            return listOf(
                ChapterEntry(
                    index = 0,
                    title = "全文",
                    charStart = 0,
                    byteStart = bomSkip.toLong(),
                    byteLength = (totalBytes - bomSkip).toInt(),
                    charCount = totalChars,
                )
            )
        }

        val entries = ArrayList<ChapterEntry>(marks.size + 1)
        var entryIndex = 0

        // Content before the first chapter heading → "开篇"
        if (marks[0].charStart > 0) {
            entries.add(
                ChapterEntry(
                    index = entryIndex++,
                    title = "开篇",
                    charStart = 0,
                    byteStart = bomSkip.toLong(),
                    byteLength = (marks[0].byteStart - bomSkip).toInt(),
                    charCount = marks[0].charStart,
                )
            )
        }

        // Each detected chapter
        for ((i, mark) in marks.withIndex()) {
            val nextCharStart = if (i + 1 < marks.size) marks[i + 1].charStart else totalChars
            val nextByteStart = if (i + 1 < marks.size) marks[i + 1].byteStart else totalBytes
            entries.add(
                ChapterEntry(
                    index = entryIndex++,
                    title = mark.title,
                    charStart = mark.charStart,
                    byteStart = mark.byteStart,
                    byteLength = (nextByteStart - mark.byteStart).toInt(),
                    charCount = nextCharStart - mark.charStart,
                )
            )
        }

        // Density protection: too many chapters means false positives
        if (ruleId == "builtin" && entries.isNotEmpty()) {
            val avgChars = totalChars.toDouble() / entries.size
            if (avgChars < MIN_AVERAGE_CHAPTER_CHARS) {
                return listOf(
                    ChapterEntry(
                        index = 0,
                        title = "全文",
                        charStart = 0,
                        byteStart = bomSkip.toLong(),
                        byteLength = (totalBytes - bomSkip).toInt(),
                        charCount = totalChars,
                    )
                )
            }
        }

        return entries
    }

    private fun emptyIndex(encoding: String, ruleId: String): TxtFileIndex {
        return TxtFileIndex(
            chapters = listOf(
                ChapterEntry(
                    index = 0,
                    title = "全文",
                    charStart = 0,
                    byteStart = 0,
                    byteLength = 0,
                    charCount = 0,
                )
            ),
            totalCharCount = 0,
            encoding = encoding,
            detectedRuleId = ruleId,
        )
    }

    /**
     * Read exactly [length] bytes (or until EOF), returning the number of bytes actually read.
     */
    private fun readFully(stream: InputStream, buffer: ByteArray, length: Int): Int {
        var offset = 0
        while (offset < length) {
            val read = stream.read(buffer, offset, length - offset)
            if (read == -1) break
            offset += read
        }
        return offset
    }

    /**
     * Resolve an encoding name to a [Charset].
     */
    private fun resolveCharset(encoding: String): Charset = when (encoding) {
        "UTF-8" -> Charsets.UTF_8
        "UTF-16LE" -> Charsets.UTF_16LE
        "UTF-16BE" -> Charsets.UTF_16BE
        else -> Charset.forName("GB18030")
    }

}
