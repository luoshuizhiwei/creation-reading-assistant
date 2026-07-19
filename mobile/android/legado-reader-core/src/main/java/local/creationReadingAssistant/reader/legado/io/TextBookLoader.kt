/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package local.creationReadingAssistant.reader.legado.io

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object TextBookLoader {
    private const val MAX_TEXT_FILE_BYTES = 128L * 1024L * 1024L

    @JvmStatic
    fun load(context: Context, uriValue: String): String {
        val uri = Uri.parse(uriValue)
        val bytes = when (uri.scheme?.lowercase()) {
            "content" -> context.contentResolver.openInputStream(uri)?.use { readLimited(it) }
            "file" -> FileInputStream(File(requireNotNull(uri.path))).use { readLimited(it) }
            null, "" -> FileInputStream(File(uriValue)).use { readLimited(it) }
            else -> throw IllegalArgumentException("不支持的本地文件地址：${uri.scheme}")
        } ?: throw IllegalStateException("无法读取本地正文文件")
        if (bytes.isEmpty()) throw IllegalStateException("正文文件为空")
        return decode(bytes).replace("\u0000", "")
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_TEXT_FILE_BYTES) {
                throw IllegalArgumentException("TXT 文件超过 128MB，当前版本请先分卷后导入")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }
        try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            // Most Chinese web novels outside UTF-8 use GBK/GB18030.
        }
        for (charsetName in listOf("GB18030", "GBK", "Big5")) {
            try {
                return String(bytes, charset(charsetName))
            } catch (_: Exception) {
                // Try the next installed charset.
            }
        }
        return String(bytes, StandardCharsets.UTF_8)
    }
}
