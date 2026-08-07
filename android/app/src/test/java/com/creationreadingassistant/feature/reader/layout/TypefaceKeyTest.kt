package com.creationreadingassistant.feature.reader.layout

import com.creationreadingassistant.feature.reader.layout.android.PaintTextRuler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 排版字体指纹（typefaceKey）纯逻辑测试：
 * 自定义字体路径必须进指纹，换字体后页索引才会整体失效重排。
 */
class TypefaceKeyTest {

    @Test
    fun `blank path uses typeface hash`() {
        val key = PaintTextRuler.typefaceKeyOf(null, textSizePx = 50f, letterSpacing = 0f, customFontPath = "")
        assertEquals("tf=0|size=50.0|ls=0.0", key)
    }

    @Test
    fun `custom font path is included in key`() {
        val key = PaintTextRuler.typefaceKeyOf(null, textSizePx = 50f, letterSpacing = 0f, customFontPath = "/data/files/fonts/a.otf")
        assertEquals("tf=/data/files/fonts/a.otf|size=50.0|ls=0.0", key)
    }

    @Test
    fun `different font paths produce different keys`() {
        val a = PaintTextRuler.typefaceKeyOf(null, 50f, 0f, "/data/files/fonts/a.otf")
        val b = PaintTextRuler.typefaceKeyOf(null, 50f, 0f, "/data/files/fonts/b.otf")
        assertNotEquals(a, b)
    }

    @Test
    fun `switching from system to custom font invalidates key`() {
        val system = PaintTextRuler.typefaceKeyOf(null, 50f, 0f, "")
        val custom = PaintTextRuler.typefaceKeyOf(null, 50f, 0f, "/data/files/fonts/a.otf")
        assertNotEquals(system, custom)
    }

    @Test
    fun `text size still part of key with custom font`() {
        val small = PaintTextRuler.typefaceKeyOf(null, 50f, 0f, "/data/files/fonts/a.otf")
        val large = PaintTextRuler.typefaceKeyOf(null, 70f, 0f, "/data/files/fonts/a.otf")
        assertNotEquals(small, large)
    }
}
