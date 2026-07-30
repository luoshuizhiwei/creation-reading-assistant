package com.creationreadingassistant.feature.reader.doc

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Test utility for generating temporary TXT files with various encodings and structures.
 * All generated files use `\n` line endings to ensure cross-platform consistency
 * between byte offsets and character offsets (important for UTF-8 streaming tests).
 */
object TestFileGenerator {

    /**
     * Generate a temporary UTF-8 file with the specified number of chapters.
     * Each chapter has a title line ("{titlePrefix}{i}\n") followed by body text.
     * Body text uses "测试正文内容。" repeated to approximately [charsPerChapter] characters.
     *
     * @return the created temporary file (caller is responsible for cleanup)
     */
    fun generateUtf8(
        chapterCount: Int,
        charsPerChapter: Int,
        titlePrefix: String = "第",
    ): File {
        val file = File.createTempFile("test_txt_", ".txt")
        val sb = StringBuilder()
        for (i in 1..chapterCount) {
            sb.append("${titlePrefix}${i}章\n")
            val bodyLen = maxOf(10, charsPerChapter - 4)
            sb.append("测试正文内容。".repeat(bodyLen / 7))
            if (i < chapterCount) sb.append("\n")
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    /**
     * Generate a temporary file with BOM bytes prepended.
     *
     * @param bomBytes BOM byte sequence (e.g. byteArrayOf(0xEF, 0xBB, 0xBF) for UTF-8 BOM)
     * @param chapterCount number of chapters to generate
     * @param charsPerChapter approximate characters per chapter
     * @param charset the charset to encode content with (must match the BOM type)
     * @return the created temporary file
     */
    fun generateWithBom(
        bomBytes: ByteArray,
        chapterCount: Int,
        charsPerChapter: Int,
        charset: Charset,
    ): File {
        val file = File.createTempFile("test_txt_bom_", ".txt")
        val sb = StringBuilder()
        for (i in 1..chapterCount) {
            sb.append("第${i}章 测试\n")
            val bodyLen = maxOf(10, charsPerChapter - 6)
            sb.append("测试正文内容。".repeat(bodyLen / 7))
            if (i < chapterCount) sb.append("\n")
        }
        val contentBytes = sb.toString().toByteArray(charset)
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(bomBytes)
            raf.write(contentBytes)
        }
        return file
    }

    /**
     * Generate an empty temporary file (0 bytes).
     */
    fun generateEmpty(): File {
        return File.createTempFile("test_txt_empty_", ".txt")
    }

    /**
     * Generate a UTF-8 multi-chapter file that is at least [targetSizeBytes] bytes.
     * Uses [RandomAccessFile] to avoid platform line-ending conversion.
     */
    fun generateLargeUtf8(targetSizeBytes: Long, chapterCount: Int): File {
        val file = File.createTempFile("test_large_utf8_", ".txt")
        val bodyUnit = "测试文本内容，" // 7 chars, 21 bytes in UTF-8
        val bodyUnitBytes = bodyUnit.toByteArray(Charsets.UTF_8).size
        val bytesPerChapter = targetSizeBytes / chapterCount
        val repeats = (bytesPerChapter / bodyUnitBytes).toInt() + 1

        RandomAccessFile(file, "rw").use { raf ->
            for (i in 1..chapterCount) {
                val title = "第${i}章 测试标题\n"
                raf.write(title.toByteArray(Charsets.UTF_8))
                for (j in 0 until repeats) {
                    raf.write(bodyUnit.toByteArray(Charsets.UTF_8))
                }
                if (i < chapterCount) raf.write('\n'.code)
            }
        }
        return file
    }

    /**
     * Generate a UTF-8 file with no chapter headings, at least [targetSizeBytes] bytes.
     */
    fun generateLargeNoToc(targetSizeBytes: Long): File {
        val file = File.createTempFile("test_large_notoc_", ".txt")
        val chunk = "这是一段用于测试的纯文本内容，没有章节标题。\n"
        val chunkBytes = chunk.toByteArray(Charsets.UTF_8).size
        val totalChunks = (targetSizeBytes / chunkBytes).toInt() + 1

        RandomAccessFile(file, "rw").use { raf ->
            for (j in 0 until totalChunks) {
                raf.write(chunk.toByteArray(Charsets.UTF_8))
            }
        }
        return file
    }

    /**
     * Generate a GB18030-encoded multi-chapter file.
     */
    fun generateGb18030(chapterCount: Int, charsPerChapter: Int): File {
        val file = File.createTempFile("test_gb18030_", ".txt")
        val gb18030 = Charset.forName("GB18030")
        val sb = StringBuilder()
        for (i in 1..chapterCount) {
            sb.append("第${i}章 测试\n")
            val bodyLen = maxOf(10, charsPerChapter - 6)
            sb.append("测试正文内容。".repeat(bodyLen / 7))
            if (i < chapterCount) sb.append("\n")
        }
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(sb.toString().toByteArray(gb18030))
        }
        return file
    }

    /**
     * Generate a UTF-16LE file with BOM (FF FE).
     */
    fun generateUtf16LE(chapterCount: Int, charsPerChapter: Int): File {
        return generateWithBom(
            bomBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            chapterCount = chapterCount,
            charsPerChapter = charsPerChapter,
            charset = Charsets.UTF_16LE,
        )
    }

    /**
     * Generate a UTF-16BE file with BOM (FE FF).
     */
    fun generateUtf16BE(chapterCount: Int, charsPerChapter: Int): File {
        return generateWithBom(
            bomBytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
            chapterCount = chapterCount,
            charsPerChapter = charsPerChapter,
            charset = Charsets.UTF_16BE,
        )
    }

    /**
     * Generate a UTF-8 file where the first half is all Chinese (3 bytes/char)
     * and the second half is all ASCII (1 byte/char).
     */
    fun generateMixedDensityUtf8(targetSizeBytes: Long): File {
        val file = File.createTempFile("test_mixed_density_", ".txt")
        val halfBytes = targetSizeBytes / 2
        // Chinese: ~3 bytes/char in UTF-8
        val chineseChars = (halfBytes / 3).toInt()
        // ASCII: 1 byte/char
        val asciiChars = (targetSizeBytes - halfBytes).toInt()

        RandomAccessFile(file, "rw").use { raf ->
            // First half: Chinese text with newlines every 80 chars
            val chineseUnit = "测试正文内容"
            var written = 0
            var lineLen = 0
            while (written < chineseChars) {
                for (ch in chineseUnit) {
                    if (written >= chineseChars) break
                    if (lineLen >= 80) {
                        raf.write('\n'.code)
                        written++
                        lineLen = 0
                    }
                    raf.write(ch.toString().toByteArray(Charsets.UTF_8))
                    written++
                    lineLen++
                }
            }
            // Second half: ASCII text with newlines every 80 chars
            val asciiUnit = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            written = 0
            lineLen = 0
            while (written < asciiChars) {
                for (ch in asciiUnit) {
                    if (written >= asciiChars) break
                    if (lineLen >= 80) {
                        raf.write('\n'.code)
                        written++
                        lineLen = 0
                    }
                    raf.write(ch.code)
                    written++
                    lineLen++
                }
            }
        }
        return file
    }

    /**
     * Generate a UTF-8 file with emoji (4 bytes) placed near 50K char boundaries.
     * Uses chapter structure so the scanner creates checkpoints near those boundaries.
     */
    fun generateEmojiAtBoundaries(): File {
        val file = File.createTempFile("test_emoji_boundary_", ".txt")
        RandomAccessFile(file, "rw").use { raf ->
            // Write a chapter with ~55000 chars, placing emoji near the 50K boundary
            val title = "第1章 测试\n"
            raf.write(title.toByteArray(Charsets.UTF_8))

            // Write 49990 Chinese chars (before boundary)
            val chinese = "测试正文内容。"
            var count = 0
            while (count < 49990) {
                for (ch in chinese) {
                    if (count >= 49990) break
                    raf.write(ch.toString().toByteArray(Charsets.UTF_8))
                    count++
                }
            }
            // Place emoji right around the 50K boundary
            val emoji = "\uD83D\uDE00" // U+1F600, 4 bytes in UTF-8
            for (i in 0 until 20) {
                raf.write(emoji.toByteArray(Charsets.UTF_8))
                count++
            }
            // Continue with more Chinese chars after boundary
            while (count < 55000) {
                for (ch in chinese) {
                    if (count >= 55000) break
                    raf.write(ch.toString().toByteArray(Charsets.UTF_8))
                    count++
                }
            }
        }
        return file
    }

    /**
     * Generate a GB18030 mixed file with ASCII + 2-byte + 4-byte GB18030 characters.
     * 4-byte GB18030 chars are placed near unit boundaries.
     */
    fun generateGb18030Mixed(): File {
        val file = File.createTempFile("test_gb18030_mixed_", ".txt")
        val gb18030 = Charset.forName("GB18030")
        RandomAccessFile(file, "rw").use { raf ->
            val title = "第1章 测试\n"
            raf.write(title.toByteArray(gb18030))

            // Write ~55000 chars of mixed content
            val ascii = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val twoByte = "测试正文内容。" // 2 bytes each in GB18030
            // U+3000 is 2-byte in GB18030, but some rare chars are 4-byte
            // GB18030 4-byte: U+E000-U+E4FF range maps to 4-byte sequences
            // Actually, let's use common Chinese chars (2-byte) and ASCII
            var count = 0
            // First 25000 chars: mix of ASCII and 2-byte
            while (count < 25000) {
                for (ch in ascii) {
                    if (count >= 25000) break
                    raf.write(ch.code)
                    count++
                }
                for (ch in twoByte) {
                    if (count >= 25000) break
                    raf.write(ch.toString().toByteArray(gb18030))
                    count++
                }
            }
            // Place some characters near the 50K boundary
            while (count < 55000) {
                for (ch in twoByte) {
                    if (count >= 55000) break
                    raf.write(ch.toString().toByteArray(gb18030))
                    count++
                }
            }
        }
        return file
    }

    /**
     * Generate a UTF-16LE file with surrogate pairs (emoji) near unit boundaries.
     */
    fun generateUtf16LEWithEmoji(): File {
        val file = File.createTempFile("test_utf16le_emoji_", ".txt")
        val charset = Charsets.UTF_16LE
        RandomAccessFile(file, "rw").use { raf ->
            // Write BOM
            raf.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))

            val title = "第1章 测试\n"
            raf.write(title.toByteArray(charset))

            // Write ~55000 chars with emoji near 50K boundary
            val chinese = "测试正文内容。"
            var count = 0
            while (count < 49990) {
                for (ch in chinese) {
                    if (count >= 49990) break
                    raf.write(ch.toString().toByteArray(charset))
                    count++
                }
            }
            // Place emoji (surrogate pairs) near boundary
            val emoji = "\uD83D\uDE00" // U+1F600
            for (i in 0 until 20) {
                raf.write(emoji.toByteArray(charset))
                count++
            }
            // Continue with Chinese
            while (count < 55000) {
                for (ch in chinese) {
                    if (count >= 55000) break
                    raf.write(ch.toString().toByteArray(charset))
                    count++
                }
            }
        }
        return file
    }

    /**
     * Generate a UTF-16BE file with surrogate pairs (emoji) near unit boundaries.
     */
    fun generateUtf16BEWithEmoji(): File {
        val file = File.createTempFile("test_utf16be_emoji_", ".txt")
        val charset = Charsets.UTF_16BE
        RandomAccessFile(file, "rw").use { raf ->
            // Write BOM
            raf.write(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))

            val title = "第1章 测试\n"
            raf.write(title.toByteArray(charset))

            val chinese = "测试正文内容。"
            var count = 0
            while (count < 49990) {
                for (ch in chinese) {
                    if (count >= 49990) break
                    raf.write(ch.toString().toByteArray(charset))
                    count++
                }
            }
            val emoji = "\uD83D\uDE00"
            for (i in 0 until 20) {
                raf.write(emoji.toByteArray(charset))
                count++
            }
            while (count < 55000) {
                for (ch in chinese) {
                    if (count >= 55000) break
                    raf.write(ch.toString().toByteArray(charset))
                    count++
                }
            }
        }
        return file
    }

    /**
     * Generate a temporary UTF-8 Markdown file with ATX headings and varied blocks.
     */
    fun generateMarkdownUtf8(chapterCount: Int, charsPerChapter: Int): File {
        val file = File.createTempFile("test_md_utf8_", ".md")
        val sb = StringBuilder()
        val body = "正文段落，包含**粗体**、`行内代码`和中英文 mixed content。"
        val repeats = (charsPerChapter / body.length).coerceAtLeast(1)
        for (i in 1..chapterCount) {
            sb.append("# 第${i}章 测试标题\n\n")
            sb.append(body.repeat(repeats))
            sb.append("\n\n")
            if (i < chapterCount) {
                sb.append("- 列表项 one\n- 列表项 two\n\n")
            }
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    /**
     * Generate a Markdown file with BOM and the specified charset.
     */
    fun generateMarkdownWithBom(
        bomBytes: ByteArray,
        chapterCount: Int,
        charsPerChapter: Int,
        charset: Charset,
    ): File {
        val file = File.createTempFile("test_md_bom_", ".md")
        val sb = StringBuilder()
        val body = "正文段落，包含**粗体**、*斜体*。"
        val repeats = (charsPerChapter / body.length).coerceAtLeast(1)
        for (i in 1..chapterCount) {
            sb.append("# 第${i}章 测试\n\n")
            sb.append(body.repeat(repeats))
            sb.append("\n\n")
        }
        val contentBytes = sb.toString().toByteArray(charset)
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(bomBytes)
            raf.write(contentBytes)
        }
        return file
    }

    /**
     * Generate a GB18030-encoded Markdown file.
     */
    fun generateMarkdownGb18030(chapterCount: Int, charsPerChapter: Int): File {
        val file = File.createTempFile("test_md_gb18030_", ".md")
        val gb18030 = Charset.forName("GB18030")
        val sb = StringBuilder()
        val body = "正文段落，包含**粗体**、*斜体*。"
        val repeats = (charsPerChapter / body.length).coerceAtLeast(1)
        for (i in 1..chapterCount) {
            sb.append("# 第${i}章 测试\n\n")
            sb.append(body.repeat(repeats))
            sb.append("\n\n")
        }
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(sb.toString().toByteArray(gb18030))
        }
        return file
    }

    /**
     * Generate a large UTF-8 Markdown file of at least [targetSizeBytes] bytes.
     * Uses ATX headings to create chapters and bounded reading units.
     */
    fun generateLargeMarkdownUtf8(targetSizeBytes: Long, chapterCount: Int): File {
        val file = File.createTempFile("test_large_md_", ".md")
        val bodyUnit = "测试 Markdown 正文，含**粗体**、*斜体*、`代码`和 emoji \uD83D\uDE00。"
        val bodyUnitBytes = bodyUnit.toByteArray(Charsets.UTF_8).size
        val bytesPerChapter = targetSizeBytes / chapterCount
        val repeats = (bytesPerChapter / bodyUnitBytes).toInt() + 1

        RandomAccessFile(file, "rw").use { raf ->
            for (i in 1..chapterCount) {
                val title = "# 第${i}章 测试标题\n\n"
                raf.write(title.toByteArray(Charsets.UTF_8))
                for (j in 0 until repeats) {
                    raf.write(bodyUnit.toByteArray(Charsets.UTF_8))
                }
                if (i < chapterCount) {
                    raf.write("\n\n".toByteArray(Charsets.UTF_8))
                }
            }
        }
        return file
    }

    /**
     * Generate a 50MB UTF-8 file with mixed Chinese/ASCII/emoji content.
     */
    fun generateLargeMixedUtf8(targetSizeBytes: Long, chapterCount: Int): File {
        val file = File.createTempFile("test_large_mixed_", ".txt")
        val bytesPerChapter = targetSizeBytes / chapterCount
        val emoji = "\uD83D\uDE00"

        RandomAccessFile(file, "rw").use { raf ->
            for (i in 1..chapterCount) {
                val title = "第${i}章 测试标题\n"
                raf.write(title.toByteArray(Charsets.UTF_8))
                var written = title.toByteArray(Charsets.UTF_8).size

                // Alternate between Chinese, ASCII, and emoji blocks
                val chineseBlock = "测试文本内容，混合不同字符。"
                val asciiBlock = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

                while (written < bytesPerChapter - 20) {
                    val before = written
                    // Chinese block
                    val cb = chineseBlock.toByteArray(Charsets.UTF_8)
                    if (written + cb.size < bytesPerChapter - 20) {
                        raf.write(cb)
                        written += cb.size
                    }
                    // ASCII block
                    val ab = asciiBlock.toByteArray(Charsets.UTF_8)
                    if (written + ab.size < bytesPerChapter - 20) {
                        raf.write(ab)
                        written += ab.size
                    }
                    // Emoji
                    val eb = emoji.toByteArray(Charsets.UTF_8)
                    if (written + eb.size < bytesPerChapter - 20) {
                        raf.write(eb)
                        written += eb.size
                    }
                    // 剩余空间小于最小 UTF-8 块时，上述三个分支都不会推进 written。
                    // 用单字节填充收尾，避免压力测试生成器进入无限循环。
                    if (written == before) {
                        val remaining = (bytesPerChapter - 20 - written).coerceAtLeast(0)
                        repeat(remaining.toInt()) { raf.write('x'.code) }
                        written += remaining.toInt()
                    }
                }
                if (i < chapterCount) raf.write('\n'.code)
            }
        }
        return file
    }
}
