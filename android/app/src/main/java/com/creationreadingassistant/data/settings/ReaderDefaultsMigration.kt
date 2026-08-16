package com.creationreadingassistant.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * 一次性旧默认迁移模块（问题 1/4）。
 *
 * 新默认（brightness=-1 / fontSize=25 / immersive=true）只对全新安装生效；
 * 升级设备残留旧默认特征值（100 / 18 / false），在此按 marker 一次性迁移：
 * - 仅 marker 未设置时迁移特征值；其他自定义亮度/字号原样保留；
 * - immersive 仅在亮度/字号/immersive 三者构成完整旧默认指纹（100/18/false）时才迁移为 true；
 * - 迁移与写 marker 在同一 DataStore 事务内完成（见 [SettingsStore.migrateLegacyReaderDefaultsOnce]），
 *   幂等且并发原子。
 */
internal val KEY_LEGACY_DEFAULTS_MIGRATED = booleanPreferencesKey("reader_legacy_defaults_migrated_v1")

/** 迁移特征值：仅当亮度仍是旧默认 100 时才收敛为 -1（跟随系统）。 */
private const val LEGACY_DEFAULT_BRIGHTNESS = 100
private const val NEW_DEFAULT_BRIGHTNESS = -1

/** 迁移特征值：仅当字号仍是旧默认 18 时才收敛为 25。 */
private const val LEGACY_DEFAULT_FONT_SIZE = 18f
private const val NEW_DEFAULT_FONT_SIZE = 25f

/**
 * 纯函数迁移：mutate 传入的 [MutablePreferences]。
 * 已写 marker 时直接返回（幂等）；未写时先于任何写入计算完整旧默认指纹，
 * 亮度/字号仍各自独立迁移，仅指纹命中（100/18/false）才把 immersive 改为 true，最后写 marker。
 */
internal fun migrateLegacyReaderDefaults(prefs: MutablePreferences) {
    if (prefs[KEY_LEGACY_DEFAULTS_MIGRATED] == true) return
    val completeLegacyFingerprint =
        prefs[KEY_READER_BRIGHTNESS] == LEGACY_DEFAULT_BRIGHTNESS &&
            prefs[KEY_FONT_SIZE] == LEGACY_DEFAULT_FONT_SIZE &&
            prefs[KEY_IMMERSIVE] == false
    if (prefs[KEY_READER_BRIGHTNESS] == LEGACY_DEFAULT_BRIGHTNESS) {
        prefs[KEY_READER_BRIGHTNESS] = NEW_DEFAULT_BRIGHTNESS
    }
    if (prefs[KEY_FONT_SIZE] == LEGACY_DEFAULT_FONT_SIZE) {
        prefs[KEY_FONT_SIZE] = NEW_DEFAULT_FONT_SIZE
    }
    if (completeLegacyFingerprint) {
        prefs[KEY_IMMERSIVE] = true
    }
    prefs[KEY_LEGACY_DEFAULTS_MIGRATED] = true
}
