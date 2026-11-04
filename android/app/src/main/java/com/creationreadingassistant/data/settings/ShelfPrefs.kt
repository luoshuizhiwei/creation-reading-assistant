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

/**
 * 「新导入未查看」的书籍 id（分隔符拼接）。
 *
 * 持久化而非仅存内存：用户导入后可能直接杀掉应用，重开时若角标全消失，
 * 「哪几本是刚加的」这个信息就丢了，而这正是该功能要解决的问题。
 * 与 [KEY_RECENT_SEARCHES] 同样用 `\u001F` 分隔，避免与书名中的常见字符冲突。
 */
private val KEY_NEW_BOOK_IDS = stringPreferencesKey("shelf_new_book_ids")

private const val SEARCH_SEPARATOR = "\u001F"

/** 「新」角标最多保留的书籍数，防止长时间不打开导致集合无界增长。 */
private const val NEW_BOOK_IDS_MAX = 200

/**
 * 未持久化过排序偏好时的默认排序。
 *
 * 取 "imported"（导入时间倒序）而非 "recent"（最近阅读倒序）：新导入且从未读过的书
 * `last_read_at` 为空字符串，按 recent 倒序会**沉到列表最底部**，用户必须往下翻很久
 * 才能找到刚加的书（M-FEAT-01）。用户之后手动改过排序则以其选择为准。
 */
private const val DEFAULT_SORT_MODE = "imported"

@Singleton
class ShelfPrefs @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val ds = context.shelfPrefsDataStore

    val viewMode: StateFlow<String> = ds.data.map { it[KEY_VIEW_MODE] ?: "grid" }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "grid")

    val sortMode: StateFlow<String> = ds.data.map { it[KEY_SORT_MODE] ?: DEFAULT_SORT_MODE }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), DEFAULT_SORT_MODE)

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

    /** 尚未打开过的新导入书籍 id（用于书卡「新」角标）。 */
    val newBookIds: StateFlow<Set<String>> = ds.data.map { prefs ->
        prefs[KEY_NEW_BOOK_IDS]
            .orEmpty()
            .split(SEARCH_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptySet())

    suspend fun setViewMode(mode: String) {
        ds.edit { it[KEY_VIEW_MODE] = mode }
    }

    suspend fun setSortMode(mode: String) {
        ds.edit { it[KEY_SORT_MODE] = mode }
    }

    /** 记录新导入的书（追加到集合头部，超出上限时淘汰最旧的）。 */
    suspend fun addNewBookIds(ids: Collection<String>) {
        val incoming = ids.map(String::trim).filter(String::isNotEmpty)
        if (incoming.isEmpty()) return
        ds.edit { prefs ->
            val previous = prefs[KEY_NEW_BOOK_IDS]
                .orEmpty()
                .split(SEARCH_SEPARATOR)
                .map(String::trim)
                .filter(String::isNotEmpty)
            val next = (incoming + previous).distinct().take(NEW_BOOK_IDS_MAX)
            prefs[KEY_NEW_BOOK_IDS] = next.joinToString(SEARCH_SEPARATOR)
        }
    }

    /** 用户打开某本书后清除它的「新」角标。 */
    suspend fun clearNewBookId(id: String) {
        if (id.isBlank()) return
        ds.edit { prefs ->
            val next = prefs[KEY_NEW_BOOK_IDS]
                .orEmpty()
                .split(SEARCH_SEPARATOR)
                .map(String::trim)
                .filter { it.isNotEmpty() && it != id }
            if (next.isEmpty()) prefs.remove(KEY_NEW_BOOK_IDS)
            else prefs[KEY_NEW_BOOK_IDS] = next.joinToString(SEARCH_SEPARATOR)
        }
    }

    /** 清除全部「新」角标（书架整理里的「全部标记为已查看」）。 */
    suspend fun clearAllNewBookIds() {
        ds.edit { it.remove(KEY_NEW_BOOK_IDS) }
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
