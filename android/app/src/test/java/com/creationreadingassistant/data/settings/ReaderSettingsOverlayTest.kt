package com.creationreadingassistant.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「全局 + 本书覆盖」叠加规则的纯函数测试（R3-P1）。
 *
 * 核心不变量：**未覆盖项一律保持全局值**；坏值只跳过该项，绝不被近似替换成别的值。
 */
class ReaderSettingsOverlayTest {

    private val global = ReaderSettings(
        fontSize = 25f,
        lineHeight = 1.85f,
        background = "follow",
        readerMode = "paged",
        fontWeightBold = false,
        headerLeft = HeaderFooterItem.BOOK_NAME,
        autoHideSeconds = 4,
    )

    @Test
    fun `empty overrides leave global untouched`() {
        assertEquals(global, ReaderSettingsOverlay.apply(global, emptyMap()))
    }

    @Test
    fun `single override wins over global and leaves siblings alone`() {
        val out = ReaderSettingsOverlay.apply(global, mapOf(ReaderOverrideKey.FONT_SIZE to "32.0"))

        assertEquals(32f, out.fontSize)
        assertEquals(global.lineHeight, out.lineHeight)
        assertEquals(global.background, out.background)
        assertEquals(global.readerMode, out.readerMode)
    }

    @Test
    fun `multiple overrides apply independently`() {
        val out = ReaderSettingsOverlay.apply(
            global,
            mapOf(
                ReaderOverrideKey.FONT_SIZE to "30.0",
                ReaderOverrideKey.BACKGROUND to "warm",
                ReaderOverrideKey.AUTO_HIDE_SECONDS to "9",
            ),
        )

        assertEquals(30f, out.fontSize)
        assertEquals("warm", out.background)
        assertEquals(9, out.autoHideSeconds)
        assertEquals(global.lineHeight, out.lineHeight)
    }

    @Test
    fun `invalid numeric override falls back to global instead of guessing`() {
        val out = ReaderSettingsOverlay.apply(global, mapOf(ReaderOverrideKey.FONT_SIZE to "not-a-number"))

        assertEquals(global.fontSize, out.fontSize)
    }

    @Test
    fun `NaN override is rejected`() {
        val out = ReaderSettingsOverlay.apply(global, mapOf(ReaderOverrideKey.LINE_HEIGHT to "NaN"))

        assertEquals(global.lineHeight, out.lineHeight)
    }

    @Test
    fun `unknown enum value for string key falls back to global`() {
        val out = ReaderSettingsOverlay.apply(
            global,
            mapOf(
                ReaderOverrideKey.BACKGROUND to "rainbow",
                ReaderOverrideKey.READER_MODE to "diagonal",
                ReaderOverrideKey.HEADER_LEFT to "NOT_A_HEADER_ITEM",
            ),
        )

        assertEquals(global.background, out.background)
        assertEquals(global.readerMode, out.readerMode)
        assertEquals(global.headerLeft, out.headerLeft)
    }

    @Test
    fun `one bad entry does not block the others`() {
        val out = ReaderSettingsOverlay.apply(
            global,
            mapOf(
                ReaderOverrideKey.FONT_SIZE to "garbage",
                ReaderOverrideKey.FONT_BOLD to "true",
            ),
        )

        assertEquals(global.fontSize, out.fontSize)
        assertTrue(out.fontWeightBold)
    }

    @Test
    fun `boolean keys only accept strict literals`() {
        assertEquals(true, ReaderSettingsOverlay.apply(global, mapOf(ReaderOverrideKey.FONT_BOLD to "true")).fontWeightBold)
        assertEquals(false, ReaderSettingsOverlay.apply(global, mapOf(ReaderOverrideKey.FONT_BOLD to "false")).fontWeightBold)
        // "1" / "yes" 不合法 → 保留全局
        assertEquals(global.fontWeightBold, ReaderSettingsOverlay.apply(global, mapOf(ReaderOverrideKey.FONT_BOLD to "1")).fontWeightBold)
    }

    @Test
    fun `isOverridden reads presence not value`() {
        val overrides: PerBookOverrides = mapOf(ReaderOverrideKey.FONT_SIZE to "25.0")

        assertTrue(ReaderSettingsOverlay.isOverridden(overrides, ReaderOverrideKey.FONT_SIZE))
        assertFalse(ReaderSettingsOverlay.isOverridden(overrides, ReaderOverrideKey.LINE_HEIGHT))
    }

    @Test
    fun `sanitize drops unknown ids and invalid values`() {
        val cleaned = ReaderSettingsOverlay.sanitize(
            mapOf(
                "fontSize" to "31.0",
                "noSuchKey" to "x",
                "background" to "rainbow",
                "readerMode" to "scroll",
            ),
        )

        assertEquals(setOf(ReaderOverrideKey.FONT_SIZE, ReaderOverrideKey.READER_MODE), cleaned.keys)
    }

    @Test
    fun `every override key round trips through its persisted encoding`() {
        // 防止 read() 与 write() 的格式漂移（新增 key 时最容易漏）
        ReaderOverrideKey.entries.forEach { key ->
            val encoded = key.read(ReaderSettings())
            val decoded = key.write(ReaderSettings(), encoded)

            assertNotNull("$key 的 read() 输出无法被 write() 解析：$encoded", decoded)
            assertEquals("$key 往返后值发生变化", encoded, key.read(decoded!!))
        }
    }

    @Test
    fun `write returns null for garbage so callers can keep the old value`() {
        // CUSTOM_FONT_PATH 是自由格式字符串（路径无法离线校验，是否可加载由字体层兜底），
        // 不参与「非法值必拒绝」的约束，单独由下一个用例覆盖。
        ReaderOverrideKey.entries
            .filterNot { it in FREE_FORM_KEYS }
            .forEach { key ->
                assertNull("$key 对非法输入应返回 null", key.write(ReaderSettings(), "\u0000garbage"))
            }
    }

    @Test
    fun `custom font path is free form and empty clears it`() {
        val key = ReaderOverrideKey.CUSTOM_FONT_PATH

        assertEquals("D:/fonts/x.ttf", key.write(ReaderSettings(), "D:/fonts/x.ttf")?.customFontPath)
        // 空串 = 取消自定义字体，属于合法覆盖值（与全局默认口径一致）
        assertEquals("", key.write(ReaderSettings(fontSize = 30f), "")?.customFontPath)
    }

    @Test
    fun `apply is order independent for disjoint keys`() {
        val a = ReaderSettingsOverlay.apply(global, mapOf(ReaderOverrideKey.FONT_SIZE to "30.0"))
        val b = ReaderSettingsOverlay.apply(a, mapOf(ReaderOverrideKey.LINE_HEIGHT to "2.0"))
        val c = ReaderSettingsOverlay.apply(global, mapOf(ReaderOverrideKey.LINE_HEIGHT to "2.0"))
        val d = ReaderSettingsOverlay.apply(c, mapOf(ReaderOverrideKey.FONT_SIZE to "30.0"))

        assertEquals(b, d)
    }

    private companion object {
        /** 自由格式字符串项：无法离线判定「非法」，故不参与严格拒绝断言。 */
        val FREE_FORM_KEYS = setOf(ReaderOverrideKey.CUSTOM_FONT_PATH)
    }
}
