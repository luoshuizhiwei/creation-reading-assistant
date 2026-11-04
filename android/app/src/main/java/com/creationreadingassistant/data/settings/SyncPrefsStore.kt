package com.creationreadingassistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.creationreadingassistant.data.repository.ConflictStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 同步偏好（DataStore `"sync_prefs"`，按 [GoalStore] 惯例）。
 *
 * 与配对凭证（[com.creationreadingassistant.data.remote.SyncConfigStore]，加密、仅配对后存在）
 * 刻意分离：这 4 项都是**非机密**的同步行为开关，且必须在**未配对时也能配置**，
 * 因此独立落盘、默认全开 + 冲突「以远端为准」，与「我的 · 同步」子页的初始展示一致。
 */
private val Context.syncPrefsDataStore by preferencesDataStore(name = "sync_prefs")

private val KEY_SYNC_BOOKS = booleanPreferencesKey("sync_books")
private val KEY_SYNC_PROGRESS = booleanPreferencesKey("sync_progress")
private val KEY_SYNC_INSPIRATIONS = booleanPreferencesKey("sync_inspirations")
private val KEY_CONFLICT_STRATEGY = stringPreferencesKey("conflict_strategy")

/**
 * 同步偏好的不可变快照。
 *
 * 三个开关分别门控 pull/push 的类别；[syncProgress] 同时门控「阅读进度」与「阅读会话」
 * （子页文案即「阅读进度与阅读历史」，二者同属一项，不拆成两个开关）。
 */
data class SyncPrefs(
    val syncBooks: Boolean = true,
    val syncProgress: Boolean = true,
    val syncInspirations: Boolean = true,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.DEFAULT,
)

@Singleton
class SyncPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val prefs: Flow<SyncPrefs> = context.syncPrefsDataStore.data.map { raw ->
        SyncPrefs(
            syncBooks = raw[KEY_SYNC_BOOKS] ?: true,
            syncProgress = raw[KEY_SYNC_PROGRESS] ?: true,
            syncInspirations = raw[KEY_SYNC_INSPIRATIONS] ?: true,
            // 坏值/缺失一律回退默认策略，绝不让非法字符串变成有效配置。
            conflictStrategy = ConflictStrategy.fromWire(raw[KEY_CONFLICT_STRATEGY]),
        )
    }

    suspend fun setSyncBooks(enabled: Boolean) {
        context.syncPrefsDataStore.edit { it[KEY_SYNC_BOOKS] = enabled }
    }

    suspend fun setSyncProgress(enabled: Boolean) {
        context.syncPrefsDataStore.edit { it[KEY_SYNC_PROGRESS] = enabled }
    }

    suspend fun setSyncInspirations(enabled: Boolean) {
        context.syncPrefsDataStore.edit { it[KEY_SYNC_INSPIRATIONS] = enabled }
    }

    suspend fun setConflictStrategy(strategy: ConflictStrategy) {
        context.syncPrefsDataStore.edit { it[KEY_CONFLICT_STRATEGY] = strategy.wire }
    }
}
