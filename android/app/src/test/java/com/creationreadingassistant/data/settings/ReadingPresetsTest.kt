package com.creationreadingassistant.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ===================== D-2-A：预设不得静默重置分页引擎 =====================
    //
    // 新增两个引擎覆盖键后，「重置全部可覆盖项」会顺带把引擎模式也恢复成结构默认。
    // 但视觉预设（大字号/护眼/默认）与「恢复全局默认」是**三种不同语义**，必须拆开：
    // - 视觉预设：只管排版/显示，绝不碰兼容性开关；
    // - 全部跟随全局：清当前书全部覆盖（含引擎）；
    // - 恢复全局默认：显式重置全局的全部可覆盖项（含引擎）。

    @Test
    fun `the preset owned key set excludes the pager engine keys`() {
        assertFalse(
            "分页引擎是兼容性开关，不得混入视觉预设",
            ReaderOverrideKey.PAGER_ENGINE_MODE in ReadingPresets.PRESET_OWNED_KEYS,
        )
        assertFalse(
            ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE in ReadingPresets.PRESET_OWNED_KEYS,
        )
    }

    @Test
    fun `the preset owned key set covers every non engine overridable key`() {
        // 反向护栏：预设负责键集合必须恰好等于「全部可覆盖项 - 两个引擎键」，
        // 既不能漏（漏了预设就清不干净旧覆盖），也不能多（多了就会碰引擎）。
        val expected = ReaderOverrideKey.entries.toSet() -
            setOf(ReaderOverrideKey.PAGER_ENGINE_MODE, ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE)

        assertEquals(expected, ReadingPresets.PRESET_OWNED_KEYS)
    }

    @Test
    fun `applying any preset leaves the pager engine modes untouched`() {
        val current = ReaderSettings(pagerEngineMode = "off", epubPagerEngineMode = "on")

        ReadingPresets.ALL.forEach { preset ->
            val out = ReadingPresets.applyTo(current, preset)
            assertEquals(
                "${preset.id} 不得改动 TXT/Markdown 引擎模式",
                "off",
                out.pagerEngineMode,
            )
            assertEquals(
                "${preset.id} 不得改动 EPUB 引擎模式",
                "on",
                out.epubPagerEngineMode,
            )
        }
    }

    @Test
    fun `applying a preset still resets the keys it owns`() {
        // 正向护栏：拆开引擎后，预设原本负责的排版项仍必须被重置。
        val current = ReaderSettings(
            fontSize = 44f,
            lineHeight = 2.9f,
            background = "night",
            pagerEngineMode = "off",
        )

        val out = ReadingPresets.applyTo(current, ReadingPresets.DEFAULT)

        assertEquals(ReaderSettings().fontSize, out.fontSize)
        assertEquals(ReaderSettings().lineHeight, out.lineHeight)
        assertEquals(ReaderSettings().background, out.background)
        assertEquals("引擎模式必须保持调用前的值", "off", out.pagerEngineMode)
    }

    @Test
    fun `applying a preset keeps device level settings untouched`() {
        // 既有契约不得回退：设备级偏好（不在预设负责集合里）保持原值。
        val current = ReaderSettings(brightness = 70, tapZoneMode = "five-zone")

        ReadingPresets.ALL.forEach { preset ->
            val out = ReadingPresets.applyTo(current, preset)
            assertEquals("${preset.id} 不得改动亮度", 70, out.brightness)
            assertEquals("${preset.id} 不得改动点击区域", "five-zone", out.tapZoneMode)
        }
    }

    @Test
    fun `reset global defaults covers every overridable key including the engines`() {
        // 「恢复全局默认」是显式的全局恢复操作：必须重置**全部可覆盖项**（含两个引擎键），
        // 而不是复用视觉预设的键集合。这里钉住它覆盖的键集合口径。
        val current = ReaderSettings(pagerEngineMode = "off", epubPagerEngineMode = "on")

        val out = ReadingPresets.resetAllOverridableKeys(current)

        assertEquals("TXT 引擎必须恢复结构默认", ReaderSettings().pagerEngineMode, out.pagerEngineMode)
        assertEquals("EPUB 引擎必须恢复结构默认", ReaderSettings().epubPagerEngineMode, out.epubPagerEngineMode)
        // 两个引擎键必须属于「全部可覆盖项」，对话框项目数因此从 20 变 22
        assertEquals(22, ReaderOverrideKey.entries.size)
    }

    @Test
    fun `reset all overridable keys keeps device level settings untouched`() {
        val current = ReaderSettings(brightness = 70, ttsPitch = 1.3f, tapZoneMode = "five-zone")

        val out = ReadingPresets.resetAllOverridableKeys(current)

        assertEquals("设备级偏好不属于可覆盖项，必须原样保留", 70, out.brightness)
        assertEquals(1.3f, out.ttsPitch)
        assertEquals("five-zone", out.tapZoneMode)
    }
}
