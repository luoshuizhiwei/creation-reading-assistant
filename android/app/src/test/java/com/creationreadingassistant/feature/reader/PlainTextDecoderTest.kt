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
}
