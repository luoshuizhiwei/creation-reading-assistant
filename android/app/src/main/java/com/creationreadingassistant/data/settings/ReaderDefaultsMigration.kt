package com.creationreadingassistant.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

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

/** EPUB 分页引擎重启用迁移 marker：off→auto 只做一次，用户此后手动改回 off 不再打扰。 */
internal val KEY_EPUB_ENGINE_REENABLED = booleanPreferencesKey("epub_engine_reenabled_v1")

internal val KEY_EPUB_PAGER_ENGINE = stringPreferencesKey("epub_pager_engine_mode")

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

/**
 * EPUB 分页引擎重启用（一次性）：历史版本的 off 残留会让 EPUB 永远落在
 * legacy 整章翻页分支——点击翻页直接跳章、章内又可滚动，真机观感即「EPUB 阅读有问题」。
 * 分页引擎此后已经过多轮加固（引擎收口/翻页修复/章界导航修复），迁移为 auto；
 * auto 模式自带健康自愈（连续 2 次崩溃自动停用引擎），风险可控。
 * 用户迁移后手动改回 off 是明确意愿，marker 保证不再迁移。
 */
internal fun migrateEpubEngineReenable(prefs: MutablePreferences) {
    if (prefs[KEY_EPUB_ENGINE_REENABLED] == true) return
    if (prefs[KEY_EPUB_PAGER_ENGINE] == "off") {
        prefs[KEY_EPUB_PAGER_ENGINE] = "auto"
    }
    prefs[KEY_EPUB_ENGINE_REENABLED] = true
}
