package com.creationreadingassistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
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

    suspend fun setViewMode(mode: String) {
        ds.edit { it[KEY_VIEW_MODE] = mode }
    }

    suspend fun setSortMode(mode: String) {
        ds.edit { it[KEY_SORT_MODE] = mode }
    }
}
