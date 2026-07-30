package com.creationreadingassistant.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class PlainTextDecoderTest {
    @Test
    fun decodesUtf8Text() {
        val source = "第一章\n这是 UTF-8 小说正文。"

        val decoded = PlainTextDecoder.decode(source.toByteArray(Charsets.UTF_8))

        assertEquals("UTF-8", decoded.encoding)
        assertEquals(source, decoded.text)
    }

    @Test
    fun decodesGbkCompatibleTextAsGb18030() {
        val source = "第一章\n中文小说正文，剑与魔法。"

        val decoded = PlainTextDecoder.decode(source.toByteArray(Charset.forName("GBK")))

        assertEquals("GB18030", decoded.encoding)
        assertEquals(source, decoded.text)
    }

    @Test
    fun stripsUtf8Bom() {
        val source = "带 BOM 的正文"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            source.toByteArray(Charsets.UTF_8)

        val decoded = PlainTextDecoder.decode(bytes)

        assertEquals("UTF-8", decoded.encoding)
        assertEquals(source, decoded.text)
    }

    // ── E6: UTF-8 边界截断测试 ─────────────────────────────────────────

    @Test
    fun `detectEncoding handles UTF-8 header truncated at 2-byte sequence boundary`() {
        // 构造 8192 字节 header，末尾是不完整的 2 字节 UTF-8 序列首字节
        val header = ByteArray(8192)
        for (i in 0 until 8191) header[i] = 'A'.code.toByte()
        header[8191] = 0xC0.toByte()  // 11000000 — 2 字节序列首字节，缺少后续字节

        val result = PlainTextDecoder.detectEncoding(header, 8192)
        assertEquals("UTF-8", result.encoding)
    }

    @Test
    fun `detectEncoding handles UTF-8 header truncated at 3-byte sequence`() {
        // 末尾只有 3 字节序列的前 2 字节
        val header = ByteArray(8192)
        for (i in 0 until 8190) header[i] = 'A'.code.toByte()
        header[8190] = 0xE4.toByte()  // 11100100 (3-byte lead)
        header[8191] = 0xB8.toByte()  // 10111000 (continuation)

        val result = PlainTextDecoder.detectEncoding(header, 8192)
        assertEquals("UTF-8", result.encoding)
    }

    @Test
    fun `detectEncoding handles UTF-8 header truncated at 4-byte sequence`() {
        // 末尾只有 4 字节序列的前 3 字节
        val header = ByteArray(8192)
        for (i in 0 until 8189) header[i] = 'A'.code.toByte()
        header[8189] = 0xF0.toByte()  // 11110000 (4-byte lead)
        header[8190] = 0x9F.toByte()  // 10011111 (continuation)
        header[8191] = 0x98.toByte()  // 10011000 (continuation)

        val result = PlainTextDecoder.detectEncoding(header, 8192)
        assertEquals("UTF-8", result.encoding)
    }
}
