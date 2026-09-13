package com.creationreadingassistant.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 选区工具条与查询目标的持久化（R3-X1）。
 *
 * 与 [PerBookSettingsStore] 同一套路：复用 [SettingsStore] 的 DataStore 实例（`app_settings`），
 * 不新增表、不新的迁移，避免把纯 UI 偏好塞进 Room 版本线。
 *
 * **读路径一律 [SelectionActions.sanitize]**：手改 / 旧版本 / 未来版本写下的脏值
 * （未知动作 id、不含 `{q}` 的模板、非法模式）在出口就被收敛，UI 永远拿到可用的配置。
 */
@Singleton
class SelectionActionStore @Inject constructor(
    private val settingsStore: SettingsStore,
) {
    private val ds: DataStore<Preferences> get() = settingsStore.preferencesDataStore

    val settings: Flow<SelectionActionSettings> = ds.data.map { prefs ->
        SelectionActions.sanitize(
            SelectionActionSettings(
                enabledPrimary = prefs[KEY_PRIMARY] ?: SelectionActions.DEFAULT_PRIMARY,
                enabledMore = prefs[KEY_MORE] ?: SelectionActions.DEFAULT_MORE,
                browserUrlTemplate = prefs[KEY_BROWSER_TEMPLATE] ?: SelectionActions.DEFAULT_BROWSER_TEMPLATE,
                dictionaryUrlTemplate = prefs[KEY_DICTIONARY_TEMPLATE]
                    ?: SelectionActions.DEFAULT_DICTIONARY_TEMPLATE,
                dictionaryMode = prefs[KEY_DICTIONARY_MODE] ?: SelectionActions.MODE_OFFLINE,
            ),
        )
    }

    /**
     * 把一个动作放进 / 移出高频槽或「更多」菜单。
     *
     * [target] 与动作的出厂分组解耦：用户可以把任意非固定动作放到高频槽
     * （「搜索提供方」就是在高频槽里放「书内搜索」而不是「浏览器」）。
     *
     * 拒绝写入的情况（保持原值不变，绝不写坏配置）：
     * - 未知 id、固定组（「取消选择」不可隐藏）；
     * - 移出后高频槽会空 —— 选中文字后没有任何入口；
     * - 放进后超过 [SelectionActions.MAX_PRIMARY_SLOTS] 个 —— 会把胶囊挤到不可点。
     */
    suspend fun setActionEnabled(
        settings: SelectionActionSettings,
        id: String,
        enabled: Boolean,
        target: SelectionActionGroup = SelectionActions.byId(id)?.group ?: SelectionActionGroup.MORE,
    ) {
        val def = SelectionActions.byId(id) ?: return
        if (def.group == SelectionActionGroup.FIXED) return
        if (target == SelectionActionGroup.FIXED) return

        val key = if (target == SelectionActionGroup.PRIMARY) KEY_PRIMARY else KEY_MORE
        val current = if (target == SelectionActionGroup.PRIMARY) settings.enabledPrimary else settings.enabledMore
        val next = if (enabled) current + id else current - id
        if (target == SelectionActionGroup.PRIMARY) {
            if (next.size > SelectionActions.MAX_PRIMARY_SLOTS) return
            if (next.isEmpty()) return
        }
        if (next == current) return
        ds.edit { prefs -> prefs[key] = next }
    }

    /** 设置浏览器查询模板；不含 `{q}` 时**不落盘**（宁可保留旧值也不写坏值）。 */
    suspend fun setBrowserUrlTemplate(template: String) {
        val trimmed = template.trim()
        if (!SelectionActions.isValidTemplate(trimmed)) return
        ds.edit { it[KEY_BROWSER_TEMPLATE] = trimmed }
    }

    suspend fun setDictionaryUrlTemplate(template: String) {
        val trimmed = template.trim()
        if (!SelectionActions.isValidTemplate(trimmed)) return
        ds.edit { it[KEY_DICTIONARY_TEMPLATE] = trimmed }
    }

    /** 设置首选词典；非法模式不落盘。 */
    suspend fun setDictionaryMode(mode: String) {
        if (mode !in SelectionActions.MODES) return
        ds.edit { it[KEY_DICTIONARY_MODE] = mode }
    }

    /** 恢复默认（动作清单 + 模板 + 模式）。 */
    suspend fun resetToDefaults() {
        ds.edit { prefs ->
            prefs.remove(KEY_PRIMARY)
            prefs.remove(KEY_MORE)
            prefs.remove(KEY_BROWSER_TEMPLATE)
            prefs.remove(KEY_DICTIONARY_TEMPLATE)
            prefs.remove(KEY_DICTIONARY_MODE)
        }
    }

    private companion object {
        val KEY_PRIMARY = stringSetPreferencesKey("selection_actions_primary")
        val KEY_MORE = stringSetPreferencesKey("selection_actions_more")
        val KEY_BROWSER_TEMPLATE = stringPreferencesKey("selection_browser_url_template")
        val KEY_DICTIONARY_TEMPLATE = stringPreferencesKey("selection_dictionary_url_template")
        val KEY_DICTIONARY_MODE = stringPreferencesKey("selection_dictionary_mode")
    }
}
