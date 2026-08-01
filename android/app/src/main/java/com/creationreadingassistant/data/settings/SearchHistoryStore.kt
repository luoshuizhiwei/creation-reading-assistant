package com.creationreadingassistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索历史持久层（DataStore Preferences，存一个 JSON 字符串列表，轻量、无需 Room migration）。
 * 对齐网页版 GlobalSearchOverlay：最近 12 条、去重、新词置顶、可清空。
 */
private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

private val KEY_HISTORY = stringPreferencesKey("search_history_list")

private val json = Json { ignoreUnknownKeys = true }

private const val SEARCH_HISTORY_MAX = 12

@Serializable
private data class SearchHistoryPayload(val items: List<String>)

@Singleton
class SearchHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val ds = context.searchHistoryDataStore
    private val mutex = Mutex()
    private val _history = MutableStateFlow<List<String>>(emptyList())

    val history: StateFlow<List<String>> = ds.data.map { prefs ->
        runCatching { json.decodeFromString(SearchHistoryPayload.serializer(), prefs[KEY_HISTORY] ?: "{\"items\":[]}") }
            .getOrDefault(SearchHistoryPayload(emptyList())).items
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 写入一条搜索词：去重、新词置顶、上限 12。空白词忽略。 */
    suspend fun add(term: String) {
        val trimmed = term.trim()
        if (trimmed.isBlank()) return
        mutex.withLock {
            val next = (listOf(trimmed) + _history.value.filter { it != trimmed }).take(SEARCH_HISTORY_MAX)
            _history.value = next
            ds.edit { prefs -> prefs[KEY_HISTORY] = json.encodeToString(SearchHistoryPayload.serializer(), SearchHistoryPayload(next)) }
        }
    }

    /** 清空全部历史。 */
    suspend fun clear() {
        mutex.withLock {
            _history.value = emptyList()
            ds.edit { prefs -> prefs[KEY_HISTORY] = json.encodeToString(SearchHistoryPayload.serializer(), SearchHistoryPayload(emptyList())) }
        }
    }
}
