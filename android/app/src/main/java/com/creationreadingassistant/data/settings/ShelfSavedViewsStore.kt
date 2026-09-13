package com.creationreadingassistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * L1 书架动态视图：把一次筛选组合（状态 + 格式 + 书单/分类/标签 + 关键词）
 * 保存为具名视图，点击即整体套用。
 *
 * 与「书单（shelves）」的语义边界：书单是**静态**的书籍集合（书加减才变），
 * 动态视图是**筛选条件**的快照——书架内容变化时视图结果自动跟着变。
 * 复用既有书单/标签/分类机制做筛选本身，这里只持久化「条件组合」。
 *
 * 存储走独立 DataStore 文件（不挤占 shelf_prefs、不碰 Room schema）。
 */

/** 一个具名动态视图 = 一组筛选条件的快照。 */
@Serializable
data class SavedShelfFilter(
    val name: String,
    /** ShelfStatusFilter 枚举名（ALL/READING/COMPLETED/UNREAD/READABLE/SHELVED）。 */
    val statusFilter: String = "ALL",
    /** "" 表示不限格式（epub/txt/md）。 */
    val formatFilter: String = "",
    val selectedShelfId: String = "",
    val selectedCategoryId: String = "",
    val selectedTagIds: List<String> = emptyList(),
    val searchQuery: String = "",
    val createdAt: String,
)

private val Context.shelfSavedViewsDataStore by preferencesDataStore(name = "shelf_saved_views")

private val KEY_SAVED_FILTERS = stringPreferencesKey("shelf_saved_filters")

/** 视图名称的唯一性口径与列表上限（防止无节制堆积）。 */
internal const val SAVED_VIEWS_MAX = 12

@Singleton
class ShelfSavedViewsStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val ds = context.shelfSavedViewsDataStore

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val savedFilters: StateFlow<List<SavedShelfFilter>> = ds.data.map { prefs ->
        decode(prefs[KEY_SAVED_FILTERS])
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun save(filter: SavedShelfFilter): Boolean {
        val name = filter.name.trim()
        if (name.isEmpty()) return false
        ds.edit { prefs ->
            val current = decode(prefs[KEY_SAVED_FILTERS])
            val replaced = current.filterNot { it.name == name }
            val next = (listOf(filter.copy(name = name)) + replaced).take(SAVED_VIEWS_MAX)
            prefs[KEY_SAVED_FILTERS] = json.encodeToString(ListSerializer(SavedShelfFilter.serializer()), next)
        }
        return true
    }

    suspend fun delete(name: String) {
        ds.edit { prefs ->
            val next = decode(prefs[KEY_SAVED_FILTERS]).filterNot { it.name == name }
            prefs[KEY_SAVED_FILTERS] =
                json.encodeToString(ListSerializer(SavedShelfFilter.serializer()), next)
        }
    }

    private fun decode(raw: String?): List<SavedShelfFilter> =
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching {
                json.decodeFromString(ListSerializer(SavedShelfFilter.serializer()), raw)
            }.getOrDefault(emptyList())
        }
}
