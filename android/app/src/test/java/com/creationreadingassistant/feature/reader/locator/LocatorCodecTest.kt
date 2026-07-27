package com.creationreadingassistant.feature.reader.locator

import com.creationreadingassistant.feature.reader.doc.LegacyOffsetCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocatorCodecTest {

    @Test
    fun `v2 keeps the legacy offset readable`() {
        val json = LocatorCodec.encode(legacyOffset = 345, chapterIndex = 2, charOffset = 44, excerpt = "选中文字")
        assertEquals(345, LegacyOffsetCodec.decodeLocator(json))
        val decoded = LocatorCodec.decode(json)
        assertNotNull(decoded)
        assertEquals(2, decoded!!.chapterIndex)
        assertEquals(44, decoded.charOffset)
        assertEquals(LocatorCodec.fingerprint("选中文字"), decoded.excerptFingerprint)
    }

    @Test
    fun `legacy locator is split algebraically without searching`() {
        val locator = LocatorCodec.decode("""{"offset":135}""")!!
        val resolved = AnchorResolver.resolve(locator, listOf(0, 101, 302), "0123456789".repeat(10), null)
        assertEquals(1, resolved.chapterIndex)
        assertEquals(34, resolved.charOffset)
        assertEquals(AnchorConfidence.EXACT, resolved.confidence)
    }

    @Test
    fun `excerpt validation recovers a nearby moved anchor`() {
        val locator = LocatorCodec.decode(
            LocatorCodec.encode(legacyOffset = 110, chapterIndex = 1, charOffset = 9, excerpt = "目标句"),
        )!!
        val resolved = AnchorResolver.resolve(locator, listOf(0, 101), "前缀发生变化……目标句，后文。", "目标句")
        assertEquals(1, resolved.chapterIndex)
        assertEquals("前缀发生变化……".length, resolved.charOffset)
        assertEquals(AnchorConfidence.RECOVERED, resolved.confidence)
    }

    @Test
    fun `missing excerpt falls back to a bounded approximate anchor`() {
        val locator = ReaderLocator(legacyOffset = 999, chapterIndex = 0, charOffset = 999, excerptFingerprint = "bad")
        val resolved = AnchorResolver.resolve(locator, listOf(0), "短章", "已经删除的句子")
        assertEquals(2, resolved.charOffset)
        assertEquals(AnchorConfidence.APPROXIMATE, resolved.confidence)
    }
}
