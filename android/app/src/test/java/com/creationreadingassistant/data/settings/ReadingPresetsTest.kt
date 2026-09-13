package com.creationreadingassistant.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预设（R3-P1）的纯函数测试。
 *
 * 两个最容易出错的点：
 * 1. 预设值的落盘格式必须与 [ReaderOverrideKey.read] 一致，否则整条预设静默失效；
 * 2. 套用「默认」必须回到**结构默认**，而不是保留上一个预设的残留。
 */
class ReadingPresetsTest {

    @Test
    fun `every preset value is accepted by its key encoding`() {
        ReadingPresets.ALL.forEach { preset ->
            preset.values.forEach { (key, value) ->
                assertNotNull(
                    "预设 ${preset.id} 的 $key = \"$value\" 无法被 write() 解析（落盘格式漂移）",
                    key.write(ReaderSettings(), value),
                )
            }
        }
    }

    @Test
    fun `preset ids are unique and resolvable`() {
        val ids = ReadingPresets.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertNotNull(ReadingPresets.fromId(it)) }
        assertNull(ReadingPresets.fromId("no_such_preset"))
    }

    @Test
    fun `default preset carries no values`() {
        assertTrue(ReadingPresets.DEFAULT.values.isEmpty())
        assertEquals("default", ReadingPresets.DEFAULT.id)
    }

    @Test
    fun `every preset explains what it changes`() {
        ReadingPresets.ALL.forEach { assertTrue("${it.id} 缺少作用说明", it.description.isNotBlank()) }
    }

    @Test
    fun `applying default returns overridable keys to structural defaults`() {
        val current = ReaderSettings(
            fontSize = 44f,
            lineHeight = 2.9f,
            background = "night",
            readerMode = "scroll",
            fontWeightBold = true,
            headerLeft = HeaderFooterItem.TIME,
        )

        val out = ReadingPresets.applyTo(current, ReadingPresets.DEFAULT)
        val defaults = ReaderSettings()

        assertEquals(defaults.fontSize, out.fontSize)
        assertEquals(defaults.lineHeight, out.lineHeight)
        assertEquals(defaults.background, out.background)
        assertEquals(defaults.readerMode, out.readerMode)
        assertEquals(defaults.fontWeightBold, out.fontWeightBold)
        assertEquals(defaults.headerLeft, out.headerLeft)
    }

    @Test
    fun `applying default keeps device-level settings untouched`() {
        val current = ReaderSettings(
            brightness = 70,
            lastFixedBrightness = 70,
            ttsPitch = 1.3f,
            ttsTimedStopMinutes = 15,
            eyeCareIntensity = 80,
            volumeKeyPaging = false,
            autoPageSpeed = 8,
            readingRhythmReminderMinutes = 45,
        )

        val out = ReadingPresets.applyTo(current, ReadingPresets.DEFAULT)

        assertEquals(70, out.brightness)
        assertEquals(70, out.lastFixedBrightness)
        assertEquals(1.3f, out.ttsPitch)
        assertEquals(15, out.ttsTimedStopMinutes)
        assertEquals(80, out.eyeCareIntensity)
        assertEquals(false, out.volumeKeyPaging)
        assertEquals(8, out.autoPageSpeed)
        assertEquals(45, out.readingRhythmReminderMinutes)
    }

    @Test
    fun `applying default wipes residues of a previously applied preset`() {
        val afterLargeFont = ReadingPresets.applyTo(ReaderSettings(), ReadingPresets.LARGE_FONT)
        assertEquals(32f, afterLargeFont.fontSize)

        val backToDefault = ReadingPresets.applyTo(afterLargeFont, ReadingPresets.DEFAULT)

        assertEquals(ReaderSettings().fontSize, backToDefault.fontSize)
        assertEquals(ReaderSettings().lineHeight, backToDefault.lineHeight)
    }

    @Test
    fun `large font preset only touches its declared keys`() {
        val out = ReadingPresets.applyTo(ReaderSettings(), ReadingPresets.LARGE_FONT)

        assertEquals(32f, out.fontSize)
        assertEquals(2f, out.lineHeight)
        assertEquals(ReaderSettings().background, out.background)
    }

    @Test
    fun `eye care preset switches to warm paper`() {
        val out = ReadingPresets.applyTo(ReaderSettings(), ReadingPresets.EYE_CARE)

        assertEquals("warm", out.background)
        assertEquals(28f, out.fontSize)
        assertEquals(1.95f, out.lineHeight)
    }

    @Test
    fun `applying a preset is idempotent`() {
        val once = ReadingPresets.applyTo(ReaderSettings(), ReadingPresets.EYE_CARE)
        val twice = ReadingPresets.applyTo(once, ReadingPresets.EYE_CARE)

        assertEquals(once, twice)
    }
}
