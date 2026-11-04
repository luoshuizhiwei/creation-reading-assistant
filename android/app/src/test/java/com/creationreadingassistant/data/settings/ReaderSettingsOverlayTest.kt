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

    @Test
    fun `screen orientation override applies and rejects invalid`() {
        val out = ReaderSettingsOverlay.apply(global, mapOf(ReaderOverrideKey.SCREEN_ORIENTATION to "landscape"))
        assertEquals("landscape", out.screenOrientation)
        // 其余项不受影响
        assertEquals(global.fontSize, out.fontSize)
        // 非法方向被拒绝（write 返回 null，叠加时跳过）
        assertNull(ReaderOverrideKey.SCREEN_ORIENTATION.write(ReaderSettings(), "upside-down"))
        val skipped = ReaderSettingsOverlay.apply(global, mapOf(ReaderOverrideKey.SCREEN_ORIENTATION to "upside-down"))
        assertEquals(global.screenOrientation, skipped.screenOrientation)
    }

    @Test
    fun `every paper option is accepted as a per-book background override`() {
        // 锁定 BACKGROUND_VALUES 与 9 档纸色一致：任意纸色都能作为本书覆盖持久化生效
        val paperKeys = listOf(
            "follow", "white", "warm", "parchment", "celadon",
            "green", "sepia_dark", "ink_night", "night",
        )
        paperKeys.forEach { key ->
            val written = ReaderOverrideKey.BACKGROUND.write(ReaderSettings(), key)
            assertNotNull("纸色 $key 应被接受为合法本书覆盖", written)
            assertEquals(key, written?.background)
        }
    }

    // ===================== D-2-A：分页引擎兼容模式的本书覆盖 =====================
    //
    // 产品契约：高级设置文案明确写「特定书籍分页异常时，可临时切回」，
    // 因此 TXT/Markdown 与 EPUB 的引擎模式必须是**真正的** per-book 设置，
    // 而不是「在本书作用域下编辑、实际写全局」。

    @Test
    fun `pager engine keys exist with ids matching the ReaderSettings field names`() {
        // id 必须与 ReaderSettings 字段口径一致，不得创建第二套名称
        // （PerBookSettingsStore 的 DataStore 键由 `book_reader_override_<bookId>_<id>` 生成）。
        assertEquals("pagerEngineMode", ReaderOverrideKey.PAGER_ENGINE_MODE.id)
        assertEquals("epubPagerEngineMode", ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE.id)
    }

    @Test
    fun `pager engine keys accept exactly off auto and on`() {
        listOf(
            ReaderOverrideKey.PAGER_ENGINE_MODE,
            ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE,
        ).forEach { key ->
            listOf("off", "auto", "on").forEach { value ->
                val written = key.write(ReaderSettings(), value)
                assertNotNull("$key 必须接受合法值 $value", written)
                assertEquals("$key 写入 $value 后读回必须一致", value, key.read(written!!))
            }
        }
    }

    @Test
    fun `pager engine keys reject illegal values instead of approximating`() {
        listOf(
            ReaderOverrideKey.PAGER_ENGINE_MODE,
            ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE,
        ).forEach { key ->
            listOf("maybe", "ON", "true", "1", "", "off ", " garbage").forEach { bad ->
                assertNull("$key 必须拒绝非法值 `$bad`（不得近似修正）", key.write(ReaderSettings(), bad))
            }
        }
    }

    @Test
    fun `the two pager engine keys write to their own ReaderSettings fields`() {
        // 交叉验证：两个键不能写错字段（写错会让 TXT 与 EPUB 互相串台）。
        val txt = ReaderOverrideKey.PAGER_ENGINE_MODE.write(ReaderSettings(), "off")
        assertEquals("off", txt?.pagerEngineMode)
        assertEquals(ReaderSettings().epubPagerEngineMode, txt?.epubPagerEngineMode)

        val epub = ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE.write(ReaderSettings(), "on")
        assertEquals("on", epub?.epubPagerEngineMode)
        assertEquals(ReaderSettings().pagerEngineMode, epub?.pagerEngineMode)
    }

    @Test
    fun `a book override of the pager engine wins over global and leaves siblings alone`() {
        val globalSettings = ReaderSettings(pagerEngineMode = "on", epubPagerEngineMode = "auto")

        val out = ReaderSettingsOverlay.apply(
            globalSettings,
            mapOf(ReaderOverrideKey.PAGER_ENGINE_MODE to "off"),
        )

        assertEquals("本书覆盖必须生效", "off", out.pagerEngineMode)
        assertEquals("另一格式的引擎模式不得被连带改动", "auto", out.epubPagerEngineMode)
    }

    @Test
    fun `an invalid pager engine override falls back to global`() {
        val globalSettings = ReaderSettings(pagerEngineMode = "on")

        val out = ReaderSettingsOverlay.apply(
            globalSettings,
            mapOf(ReaderOverrideKey.PAGER_ENGINE_MODE to "bogus"),
        )

        assertEquals("非法覆盖必须回退全局，不得近似修正", "on", out.pagerEngineMode)
    }

    @Test
    fun `sanitize keeps a valid pager engine override and drops an invalid one`() {
        val kept = ReaderSettingsOverlay.sanitize(
            mapOf("pagerEngineMode" to "off", "epubPagerEngineMode" to "on"),
        )
        assertEquals("off", kept[ReaderOverrideKey.PAGER_ENGINE_MODE])
        assertEquals("on", kept[ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE])

        val dropped = ReaderSettingsOverlay.sanitize(mapOf("pagerEngineMode" to "sometimes"))
        assertTrue("非法引擎值必须被 sanitize 丢弃", dropped.isEmpty())
    }

    private companion object {
        /** 自由格式字符串项：无法离线判定「非法」，故不参与严格拒绝断言。 */
        val FREE_FORM_KEYS = setOf(ReaderOverrideKey.CUSTOM_FONT_PATH)
    }
}
