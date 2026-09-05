package com.creationreadingassistant.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一次性旧默认迁移模块（问题 1/4）验收：
 * - 仅 marker 未设置时迁移特征值：brightness=100→-1、fontSize=18→25 各自独立迁移；
 * - immersive 仅在 brightness/font/immersive 三者同时呈完整旧默认指纹（100/18/false）时才迁移为 true；
 * - 其他自定义亮度/字号原样保留；
 * - 写 marker 后幂等（已迁移状态不再改动）；
 * - 全新安装（无特征值）只写 marker，不散落条件。
 */
class ReaderDefaultsMigrationTest {

    @Test
    fun `完整旧默认指纹 100-18-false 迁移全部三个特征值`() {
        val prefs = preferencesOf(
            KEY_READER_BRIGHTNESS to 100,
            KEY_FONT_SIZE to 18f,
            KEY_IMMERSIVE to false,
        ).toMutablePreferences()

        migrateLegacyReaderDefaults(prefs)

        assertEquals(-1, prefs[KEY_READER_BRIGHTNESS])
        assertEquals(25f, prefs[KEY_FONT_SIZE])
        assertEquals(true, prefs[KEY_IMMERSIVE])
        assertEquals(true, prefs[KEY_LEGACY_DEFAULTS_MIGRATED])
    }

    @Test
    fun `仅 immersive=false 且其他为自定义时 immersive 原样保留`() {
        val prefs = preferencesOf(
            KEY_READER_BRIGHTNESS to 45,
            KEY_FONT_SIZE to 20f,
            KEY_IMMERSIVE to false,
        ).toMutablePreferences()

        migrateLegacyReaderDefaults(prefs)

        assertEquals(45, prefs[KEY_READER_BRIGHTNESS])
        assertEquals(20f, prefs[KEY_FONT_SIZE])
        assertFalse(prefs[KEY_IMMERSIVE]!!)
        assertEquals(true, prefs[KEY_LEGACY_DEFAULTS_MIGRATED])
    }

    @Test
    fun `部分旧指纹时亮度或字号仍独立迁移而 immersive 不动`() {
        // 仅亮度命中旧默认：亮度迁移，字号与 immersive 保留
        val brightnessOnly = preferencesOf(
            KEY_READER_BRIGHTNESS to 100,
            KEY_FONT_SIZE to 21f,
            KEY_IMMERSIVE to false,
        ).toMutablePreferences()

        migrateLegacyReaderDefaults(brightnessOnly)

        assertEquals(-1, brightnessOnly[KEY_READER_BRIGHTNESS])
        assertEquals(21f, brightnessOnly[KEY_FONT_SIZE])
        assertFalse(brightnessOnly[KEY_IMMERSIVE]!!)

        // 仅字号命中旧默认：字号迁移，亮度与 immersive 保留
        val fontSizeOnly = preferencesOf(
            KEY_READER_BRIGHTNESS to 80,
            KEY_FONT_SIZE to 18f,
            KEY_IMMERSIVE to false,
        ).toMutablePreferences()

        migrateLegacyReaderDefaults(fontSizeOnly)

        assertEquals(80, fontSizeOnly[KEY_READER_BRIGHTNESS])
        assertEquals(25f, fontSizeOnly[KEY_FONT_SIZE])
        assertFalse(fontSizeOnly[KEY_IMMERSIVE]!!)
    }

    @Test
    fun `特征值逐个独立迁移`() {
        val prefs = preferencesOf(
            KEY_READER_BRIGHTNESS to 100,
            KEY_FONT_SIZE to 21f,
            KEY_IMMERSIVE to true,
        ).toMutablePreferences()

        migrateLegacyReaderDefaults(prefs)

        assertEquals(-1, prefs[KEY_READER_BRIGHTNESS])
        assertEquals(21f, prefs[KEY_FONT_SIZE])
        assertEquals(true, prefs[KEY_IMMERSIVE])
    }

    @Test
    fun `marker 已设置时幂等不再迁移`() {
        val prefs = preferencesOf(
            KEY_LEGACY_DEFAULTS_MIGRATED to true,
            KEY_READER_BRIGHTNESS to 100,
            KEY_FONT_SIZE to 18f,
            KEY_IMMERSIVE to false,
        ).toMutablePreferences()

        migrateLegacyReaderDefaults(prefs)

        // 已迁移过：即使残留旧特征值也不动（幂等），避免覆盖用户后来设置的值
        assertEquals(100, prefs[KEY_READER_BRIGHTNESS])
        assertEquals(18f, prefs[KEY_FONT_SIZE])
        assertFalse(prefs[KEY_IMMERSIVE]!!)
    }

    @Test
    fun `全新安装无特征值只写 marker 不引入值`() {
        val prefs = preferencesOf().toMutablePreferences()

        migrateLegacyReaderDefaults(prefs)

        assertEquals(1, prefs.asMap().size)
        assertTrue(prefs[KEY_LEGACY_DEFAULTS_MIGRATED]!!)
        assertFalse(KEY_READER_BRIGHTNESS in prefs.asMap().keys)
    }

    // ── EPUB 分页引擎重启用迁移（off → auto，一次性） ────────────────────

    @Test
    fun `epub 引擎 off 迁移为 auto 并写 marker`() {
        val prefs = preferencesOf(KEY_EPUB_PAGER_ENGINE to "off").toMutablePreferences()

        migrateEpubEngineReenable(prefs)

        assertEquals("auto", prefs[KEY_EPUB_PAGER_ENGINE])
        assertTrue(prefs[KEY_EPUB_ENGINE_REENABLED]!!)
    }

    @Test
    fun `epub 引擎 auto 或 on 原样保留只写 marker`() {
        val auto = preferencesOf(KEY_EPUB_PAGER_ENGINE to "auto").toMutablePreferences()
        migrateEpubEngineReenable(auto)
        assertEquals("auto", auto[KEY_EPUB_PAGER_ENGINE])
        assertTrue(auto[KEY_EPUB_ENGINE_REENABLED]!!)

        val on = preferencesOf(KEY_EPUB_PAGER_ENGINE to "on").toMutablePreferences()
        migrateEpubEngineReenable(on)
        assertEquals("on", on[KEY_EPUB_PAGER_ENGINE])
    }

    @Test
    fun `epub 迁移 marker 已设置时幂等 用户手动改回 off 不被覆盖`() {
        val prefs = preferencesOf(
            KEY_EPUB_ENGINE_REENABLED to true,
            KEY_EPUB_PAGER_ENGINE to "off",
        ).toMutablePreferences()

        migrateEpubEngineReenable(prefs)

        assertEquals("off", prefs[KEY_EPUB_PAGER_ENGINE])
    }

    @Test
    fun `epub 全新安装无该键只写 marker`() {
        val prefs = preferencesOf().toMutablePreferences()

        migrateEpubEngineReenable(prefs)

        assertEquals(1, prefs.asMap().size)
        assertTrue(prefs[KEY_EPUB_ENGINE_REENABLED]!!)
    }
}
