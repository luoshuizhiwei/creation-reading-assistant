package com.creationreadingassistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.continueReadingDataStore by preferencesDataStore(name = "continue_reading")

private const val KEY_PREFIX = "continue_removed_"

private fun removedKey(bookId: String) = stringPreferencesKey("$KEY_PREFIX$bookId")

/**
 * 「继续阅读」本地隐藏状态存储。
 *
 * 网页版用 localStorage 保存用户「从继续阅读移除」的书籍 ID；原生版用 DataStore 对齐。
 * 该状态纯本地，不参与同步；若移除后用户重新阅读（last_read_at 更新），书籍会重新出现。
 */
@Singleton
class ContinueReadingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.continueReadingDataStore

    /** 当前被移除的书籍 ID → 移除时间 ISO。 */
    val removedIds: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs.asMap().keys
            .filterIsInstance<androidx.datastore.preferences.core.Preferences.Key<String>>()
            .mapNotNull { key ->
                val bookId = key.name.removePrefix(KEY_PREFIX).takeIf { it != key.name } ?: return@mapNotNull null
                bookId to (prefs[key] ?: return@mapNotNull null)
            }
            .toMap()
    }

    suspend fun remove(bookId: String) {
        dataStore.edit { prefs ->
            prefs[removedKey(bookId)] = Instant.now().toString()
        }
    }

    suspend fun clear(bookId: String) {
        dataStore.edit { prefs ->
            prefs.remove(removedKey(bookId))
        }
    }
}
