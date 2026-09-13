package com.creationreadingassistant.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本书级阅读设置覆盖（R3-P1）。
 *
 * 存储：复用 `SettingsStore` 的同一个 DataStore 实例（`app_settings`），
 * 每本书每项一个独立键 `book_reader_override_<bookId>_<keyId>`。
 * **键存在 = 该项被本书显式覆盖**；因此"只保存覆盖项"是存储结构本身保证的，
 * 不需要额外的 marker 或全量快照。
 *
 * 读取优先级：[ReaderSettingsOverlay.apply] 叠加后即 `本书覆盖 > 全局 > 结构默认`。
 *
 * 注意：本类**只做持久化与叠加**，不做任何 UI 决策；UI 通过 [overrides] 判断「哪一项来自本书」，
 * 通过 [clearOverride] / [clearAllOverrides] 回落全局。
 */
@Singleton
class PerBookSettingsStore @Inject constructor(
    private val settingsStore: SettingsStore,
) {
    private val ds: DataStore<Preferences> get() = settingsStore.preferencesDataStore

    /** 本书显式覆盖项（只含用户改过的 key）。 */
    fun overrides(bookId: String): Flow<PerBookOverrides> =
        ds.data.map { prefs -> readOverrides(prefs, bookId) }

    /**
     * 阅读器应消费的**有效设置**：全局叠加本书覆盖。
     * 阅读器不要直接读 `settingsStore.reader`，否则本书覆盖不生效。
     */
    fun effectiveReader(bookId: String): Flow<ReaderSettings> =
        combine(settingsStore.reader, overrides(bookId)) { global, book ->
            ReaderSettingsOverlay.apply(global, book)
        }

    /** 写入一项本书覆盖。非法值（[ReaderOverrideKey.write] 返回 null）**不落盘**。 */
    suspend fun setOverride(bookId: String, key: ReaderOverrideKey, value: String) {
        if (bookId.isBlank()) return
        if (key.write(ReaderSettings(), value) == null) return
        ds.edit { it[overrideKey(bookId, key)] = value }
    }

    /** 清除本项覆盖 → 该项回落全局。 */
    suspend fun clearOverride(bookId: String, key: ReaderOverrideKey) {
        if (bookId.isBlank()) return
        ds.edit { it.remove(overrideKey(bookId, key)) }
    }

    /** 清除本书全部覆盖 → 整本回落全局。 */
    suspend fun clearAllOverrides(bookId: String) {
        if (bookId.isBlank()) return
        ds.edit { prefs -> ReaderOverrideKey.entries.forEach { prefs.remove(overrideKey(bookId, it)) } }
    }

    /** 一次性快照（测试 / 诊断用）。 */
    suspend fun snapshotOverrides(bookId: String): PerBookOverrides =
        readOverrides(ds.data.first(), bookId)

    // ── 预设与恢复默认 ────────────────────────────────────────────────

    /** 把预设套用到**全局**：先清掉可覆盖项的改动，再叠加预设值。 */
    suspend fun applyPresetToGlobal(preset: ReadingPreset) {
        settingsStore.updateReader { ReadingPresets.applyTo(this, preset) }
    }

    /**
     * 把预设套用到**本书**：先清本书全部覆盖，再逐项写入预设值。
     * 「默认」预设值为空 → 等价于 [clearAllOverrides]，回到全局。
     */
    suspend fun applyPresetToBook(bookId: String, preset: ReadingPreset) {
        if (bookId.isBlank()) return
        clearAllOverrides(bookId)
        preset.values.forEach { (key, value) -> setOverride(bookId, key, value) }
    }

    /**
     * 恢复默认（全局）：把**可覆盖项**（排版 / 显示，见 [ReaderOverrideKey]）恢复为结构默认。
     *
     * 作用范围**不含**亮度、TTS、护眼、音量键翻页等设备相关项 —— 那些是跨书共享的设备偏好，
     * 一并清掉会丢用户设置。本书覆盖也不受影响（清本书覆盖用 [clearAllOverrides]）。
     */
    suspend fun resetGlobalReaderToDefaults() {
        settingsStore.updateReader { ReadingPresets.applyTo(this, ReadingPresets.DEFAULT) }
    }

    private fun readOverrides(prefs: Preferences, bookId: String): PerBookOverrides {
        if (bookId.isBlank()) return emptyMap()
        val out = LinkedHashMap<ReaderOverrideKey, String>()
        ReaderOverrideKey.entries.forEach { key ->
            prefs[overrideKey(bookId, key)]?.let { out[key] = it }
        }
        return out
    }

    private fun overrideKey(bookId: String, key: ReaderOverrideKey) =
        stringPreferencesKey("book_reader_override_${bookId}_${key.id}")
}
