package com.creationreadingassistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 书架视图 / 排序偏好持久层（DataStore Preferences，对齐网页版 localStorage 持久化）。
 * 重开应用后保持网格/列表视图与排序方式。
 */
private val Context.shelfPrefsDataStore by preferencesDataStore(name = "shelf_prefs")

private val KEY_VIEW_MODE = stringPreferencesKey("shelf_view_mode") // "grid" | "list"
private val KEY_SORT_MODE = stringPreferencesKey("shelf_sort_mode") // "recent" | "imported" | "title" | "progress"
private val KEY_RECENT_SEARCHES = stringPreferencesKey("shelf_recent_searches")
private val KEY_PRIVATE_SEARCH = booleanPreferencesKey("shelf_private_search")
private const val SEARCH_SEPARATOR = "\u001F"

@Singleton
class ShelfPrefs @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val ds = context.shelfPrefsDataStore

    val viewMode: StateFlow<String> = ds.data.map { it[KEY_VIEW_MODE] ?: "grid" }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "grid")

    val sortMode: StateFlow<String> = ds.data.map { it[KEY_SORT_MODE] ?: "recent" }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "recent")

    val recentSearches: StateFlow<List<String>> = ds.data.map { prefs ->
        prefs[KEY_RECENT_SEARCHES]
            .orEmpty()
            .split(SEARCH_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase() }
            .take(8)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val privateSearch: StateFlow<Boolean> = ds.data.map { it[KEY_PRIVATE_SEARCH] ?: false }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    suspend fun setViewMode(mode: String) {
        ds.edit { it[KEY_VIEW_MODE] = mode }
    }

    suspend fun setSortMode(mode: String) {
        ds.edit { it[KEY_SORT_MODE] = mode }
    }

    suspend fun recordSearch(query: String) {
        val normalized = query.trim().replace(SEARCH_SEPARATOR, " ")
        if (normalized.isEmpty()) return
        ds.edit { prefs ->
            if (prefs[KEY_PRIVATE_SEARCH] == true) return@edit
            val previous = prefs[KEY_RECENT_SEARCHES]
                .orEmpty()
                .split(SEARCH_SEPARATOR)
                .map(String::trim)
                .filter(String::isNotEmpty)
            prefs[KEY_RECENT_SEARCHES] = (listOf(normalized) + previous)
                .distinctBy { it.lowercase() }
                .take(8)
                .joinToString(SEARCH_SEPARATOR)
        }
    }

    suspend fun clearRecentSearches() {
        ds.edit { it.remove(KEY_RECENT_SEARCHES) }
    }

    suspend fun replaceRecentSearches(values: List<String>) {
        val normalized = values
            .map { it.trim().replace(SEARCH_SEPARATOR, " ") }
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase() }
            .take(8)
        ds.edit { prefs ->
            if (normalized.isEmpty()) prefs.remove(KEY_RECENT_SEARCHES)
            else prefs[KEY_RECENT_SEARCHES] = normalized.joinToString(SEARCH_SEPARATOR)
        }
    }

    suspend fun setPrivateSearch(enabled: Boolean) {
        ds.edit { it[KEY_PRIVATE_SEARCH] = enabled }
    }
}
