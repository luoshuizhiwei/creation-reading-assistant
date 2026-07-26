package com.creationreadingassistant.feature.reader

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset

data class DecodedPlainText(
    val text: String,
    val encoding: String,
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
        if (bytes.isEmpty()) return DecodedPlainText("", "UTF-8")

        return when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) ->
                DecodedPlainText(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8")

            bytes.startsWith(0xFF, 0xFE) ->
                DecodedPlainText(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), "UTF-16LE")

            bytes.startsWith(0xFE, 0xFF) ->
                DecodedPlainText(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), "UTF-16BE")

            isStrictUtf8(bytes) ->
                DecodedPlainText(String(bytes, Charsets.UTF_8), "UTF-8")

            else ->
                DecodedPlainText(String(bytes, gb18030), "GB18030")
        }
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
