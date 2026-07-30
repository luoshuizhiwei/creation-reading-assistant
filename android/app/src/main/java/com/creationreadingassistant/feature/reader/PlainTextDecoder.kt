package com.creationreadingassistant.feature.reader

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset

data class DecodedPlainText(
    val text: String,
    val encoding: String,
    /** Number of BOM bytes skipped at the start of the file (0 if no BOM). */
    val bomLength: Int = 0,
)

/**
 * Result of encoding detection from a byte header.
 *
 * @param encoding the detected charset name (e.g. "UTF-8", "GB18030")
 * @param bomLength number of BOM bytes at the start of the file (0 if no BOM)
 */
data class EncodingResult(
    val encoding: String,
    val bomLength: Int,
)

/**
 * 本地小说常见文本编码解码器。
 *
 * UTF-8 必须通过严格校验后才采用；无 BOM 且不是合法 UTF-8 的中文文本按
 * GB18030 解码。GB18030 向下兼容 GBK/GB2312，可覆盖大多数中文 TXT 小说。
 */
object PlainTextDecoder {
    private val gb18030: Charset = Charset.forName("GB18030")

    fun decode(bytes: ByteArray): DecodedPlainText {
        if (bytes.isEmpty()) return DecodedPlainText("", "UTF-8", 0)

        return when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) ->
                DecodedPlainText(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8", 3)

            bytes.startsWith(0xFF, 0xFE) ->
                DecodedPlainText(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), "UTF-16LE", 2)

            bytes.startsWith(0xFE, 0xFF) ->
                DecodedPlainText(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), "UTF-16BE", 2)

            isStrictUtf8(bytes) ->
                DecodedPlainText(String(bytes, Charsets.UTF_8), "UTF-8", 0)

            else ->
                DecodedPlainText(String(bytes, gb18030), "GB18030", 0)
        }
    }

    /**
     * Detect encoding from the first few bytes of a stream.
     * Does NOT consume the entire stream — only inspects up to [headerLength] bytes.
     *
     * Detection order:
     * 1. BOM (UTF-8 BOM, UTF-16LE BOM, UTF-16BE BOM)
     * 2. Strict UTF-8 validation on the header
     * 3. Fallback to GB18030 (covers GBK / GB2312)
     *
     * @param header raw bytes from the start of the file
     * @param headerLength number of valid bytes in [header] (may be less than header.size)
     */
    fun detectEncoding(header: ByteArray, headerLength: Int = header.size): EncodingResult {
        if (headerLength == 0) return EncodingResult("UTF-8", 0)

        // BOM detection
        if (headerLength >= 3
            && header[0] == 0xEF.toByte()
            && header[1] == 0xBB.toByte()
            && header[2] == 0xBF.toByte()
        ) {
            return EncodingResult("UTF-8", 3)
        }
        if (headerLength >= 2
            && header[0] == 0xFF.toByte()
            && header[1] == 0xFE.toByte()
        ) {
            return EncodingResult("UTF-16LE", 2)
        }
        if (headerLength >= 2
            && header[0] == 0xFE.toByte()
            && header[1] == 0xFF.toByte()
        ) {
            return EncodingResult("UTF-16BE", 2)
        }

        // Strict UTF-8 validation on the available header bytes
        val slice = if (headerLength == header.size) header else header.copyOf(headerLength)
        // 截去末尾不完整的 UTF-8 序列再校验（采样边界可能截断多字节字符）
        val effectiveEnd = lastCompleteUtf8Boundary(slice)
        val effectiveSlice = if (effectiveEnd == slice.size) slice else slice.copyOf(effectiveEnd)
        if (effectiveSlice.isNotEmpty() && isStrictUtf8(effectiveSlice)) {
            return EncodingResult("UTF-8", 0)
        }

        return EncodingResult("GB18030", 0)
    }

    /**
     * 返回 bytes 中最后一个完整 UTF-8 序列的结束位置。
     * 如果末尾是不完整的多字节序列（如 3 字节中文只读到前 1-2 字节），
     * 返回截断点，以便跳过不完整序列再做严格校验。
     */
    private fun lastCompleteUtf8Boundary(bytes: ByteArray): Int {
        var i = bytes.size
        // 向前跳过 continuation bytes (10xxxxxx)
        while (i > 0 && (bytes[i - 1].toInt() and 0xC0) == 0x80) i--
        if (i == 0) return bytes.size  // 全是 continuation bytes，异常数据
        val lead = bytes[i - 1].toInt() and 0xFF
        val expectedLen = when {
            lead < 0x80 -> 1      // ASCII
            lead < 0xC0 -> return bytes.size  // continuation byte at lead position, malformed
            lead < 0xE0 -> 2      // 2-byte sequence
            lead < 0xF0 -> 3      // 3-byte sequence
            else -> 4              // 4-byte sequence
        }
        val actualLen = bytes.size - (i - 1)
        return if (actualLen >= expectedLen) bytes.size else i - 1
    }

    private fun isStrictUtf8(bytes: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { index ->
            this[index].toInt() and 0xFF == expected[index]
        }
}
