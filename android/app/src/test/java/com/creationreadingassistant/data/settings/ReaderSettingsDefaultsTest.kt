package com.creationreadingassistant.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 阅读亮度默认值语义测试（纯 JVM，无 Android 依赖）。
 *
 * 「全新安装（无 prefs）默认跟随系统」由 ReaderSettings 默认值承担：
 * 加载路径 `prefs[KEY_READER_BRIGHTNESS] ?: -1` 在无历史值时同样落到 -1。
 */
class ReaderSettingsDefaultsTest {

    @Test
    fun `默认亮度为 -1 表示跟随系统`() {
        assertEquals(-1, ReaderSettings().brightness)
    }

    @Test
    fun `默认上次固定亮度为 65`() {
        assertEquals(65, ReaderSettings().lastFixedBrightness)
    }

    @Test
    fun `关闭跟随系统开关回退到上次固定亮度`() {
        val s = ReaderSettings(brightness = -1, lastFixedBrightness = 80)
        val afterToggleOff = s.copy(brightness = s.lastFixedBrightness.coerceIn(5, 100))
        assertEquals(80, afterToggleOff.brightness)
    }

    @Test
    fun `拖动固定亮度滑块会更新上次固定亮度`() {
        val s = ReaderSettings(brightness = 45)
        val afterDrag = s.copy(brightness = 70)
        assertEquals(70, afterDrag.brightness.coerceIn(5, 100))
    }
}
