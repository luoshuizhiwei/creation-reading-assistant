package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.settings.AISettings
import com.creationreadingassistant.data.settings.AppearanceSettings
import com.creationreadingassistant.data.settings.PerBookOverrides
import com.creationreadingassistant.data.settings.PerBookSettingsStore
import com.creationreadingassistant.data.settings.ReaderOverrideKey
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.data.settings.SelectionActionGroup
import com.creationreadingassistant.data.settings.SelectionActionPreferences
import com.creationreadingassistant.data.settings.SelectionActionSettings
import com.creationreadingassistant.data.settings.SelectionActionStore
import com.creationreadingassistant.data.settings.ReadingPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置 ViewModel：把 SettingsStore 的持久化状态暴露给 Composable，
 * 并提供挂起的更新方法（写回 DataStore）。本身无状态，所有数据来自单例 SettingsStore。
 *
 * R3-P1 起同时承担**本书覆盖**的读写入口：
 * - [reader] 仍是**全局**设置（「我的 → 阅读设置」页面用它）；
 * - 阅读器必须用 [effectiveReader]（全局叠加本书覆盖），否则本书覆盖不生效；
 * - 书内编辑经 `ReaderSettingsRouter` 定好落盘层级后，通过下面的 write 方法发写请求。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val perBook: PerBookSettingsStore,
    private val selectionActionStore: SelectionActionStore,
) : ViewModel() {

    val appearance: StateFlow<AppearanceSettings> = settings.appearance

    /** **全局**阅读设置。阅读器请改用 [effectiveReader]。 */
    val reader: StateFlow<ReaderSettings> = settings.reader
    val ai: StateFlow<AISettings> = settings.ai

    /**
     * R3-X1：选区工具条动作与查询目标配置。
     *
     * 用 `Eagerly` 预热：工具条在选中文字时立刻要渲染，晚一帧拿到默认值会出现
     * 「先铺满默认动作再跳成用户配置」的闪烁。
     */
    val selectionActions: StateFlow<SelectionActionSettings> = selectionActionStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SelectionActionSettings())

    /**
     * 交给 Reader 之外的消费侧（Profile「选区与查词」）的**唯一**入口。
     *
     * 只暴露 [SelectionActionPreferences] 这一窄接口：消费方既拿不到 [selectionActionStore]，
     * 也拿不到本 ViewModel 上与选区无关的外观 / AI / 阅读设置面，因此无从复制默认值、
     * sanitize、URL 校验或动作排序与过滤——那些裁决唯一地留在仓储里。
     */
    val selectionActionPreferences: SelectionActionPreferences = object : SelectionActionPreferences {
        override val settings: StateFlow<SelectionActionSettings> = selectionActions

        override fun setActionEnabled(id: String, enabled: Boolean, target: SelectionActionGroup) {
            val current = selectionActions.value
            viewModelScope.launch {
                selectionActionStore.setActionEnabled(current, id, enabled, target)
            }
        }

        override fun setBrowserUrlTemplate(template: String) {
            viewModelScope.launch { selectionActionStore.setBrowserUrlTemplate(template) }
        }

        override fun setDictionaryUrlTemplate(template: String) {
            viewModelScope.launch { selectionActionStore.setDictionaryUrlTemplate(template) }
        }

        override fun setDictionaryMode(mode: String) {
            viewModelScope.launch { selectionActionStore.setDictionaryMode(mode) }
        }

        override fun resetToDefaults() {
            viewModelScope.launch { selectionActionStore.resetToDefaults() }
        }
    }

    // ── 本书覆盖（R3-P1）────────────────────────────────────────────

    /** 本书显式覆盖项；用于 UI 标注「哪一项来自本书」。 */
    fun overrides(bookId: String?): Flow<PerBookOverrides> =
        if (bookId.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(emptyMap()) else perBook.overrides(bookId)

    /** 阅读器应消费的有效设置（全局 + 本书覆盖）。 */
    fun effectiveReader(bookId: String?): Flow<ReaderSettings> =
        if (bookId.isNullOrBlank()) reader else perBook.effectiveReader(bookId)

    fun setOverride(bookId: String?, key: ReaderOverrideKey, value: String) {
        val id = bookId.validBookId() ?: return
        viewModelScope.launch { perBook.setOverride(id, key, value) }
    }

    fun clearOverride(bookId: String?, key: ReaderOverrideKey) {
        val id = bookId.validBookId() ?: return
        viewModelScope.launch { perBook.clearOverride(id, key) }
    }

    fun clearAllOverrides(bookId: String?) {
        val id = bookId.validBookId() ?: return
        viewModelScope.launch { perBook.clearAllOverrides(id) }
    }

    fun applyPresetToBook(bookId: String?, preset: ReadingPreset) {
        val id = bookId.validBookId() ?: return
        viewModelScope.launch { perBook.applyPresetToBook(id, preset) }
    }

    /** null / 空串都不算有效书号：避免把无意义写入透传给仓储（仓储侧另有兜底）。 */
    private fun String?.validBookId(): String? = this?.takeIf { it.isNotBlank() }

    fun applyPresetToGlobal(preset: ReadingPreset) {
        viewModelScope.launch { perBook.applyPresetToGlobal(preset) }
    }

    /** 恢复全局可覆盖项为结构默认（不动本书覆盖，也不动设备相关项）。 */
    fun resetGlobalReaderDefaults() {
        viewModelScope.launch { perBook.resetGlobalReaderToDefaults() }
    }

    // ── 全局写入口 ──────────────────────────────────────────────────

    fun updateAppearance(block: AppearanceSettings.() -> AppearanceSettings) {
        viewModelScope.launch { settings.updateAppearance(block) }
    }

    fun updateReader(block: ReaderSettings.() -> ReaderSettings) {
        viewModelScope.launch { settings.updateReader(block) }
    }

    fun updateAi(block: AISettings.() -> AISettings) {
        viewModelScope.launch { settings.updateAi(block) }
    }
}
