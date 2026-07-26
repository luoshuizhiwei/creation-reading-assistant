package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.settings.AISettings
import com.creationreadingassistant.data.settings.AppearanceSettings
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置 ViewModel：把 SettingsStore 的持久化状态暴露给 Composable，
 * 并提供挂起的更新方法（写回 DataStore）。本身无状态，所有数据来自单例 SettingsStore。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
) : ViewModel() {

    val appearance: StateFlow<AppearanceSettings> = settings.appearance
    val reader: StateFlow<ReaderSettings> = settings.reader
    val ai: StateFlow<AISettings> = settings.ai

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
