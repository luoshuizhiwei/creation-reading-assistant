package com.creationreadingassistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.creationreadingassistant.data.local.entity.BookEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导入历史记录持久层（DataStore Preferences，存一个 JSON 列表，轻量、无需 Room migration）。
 * 对齐网页版 ImportHistoryPanel 的展示口径：来源文件名、大小、格式、编码、重复标记、书名、错误、时间戳、结果状态。
 */
private val Context.importHistoryDataStore by preferencesDataStore(name = "import_history")

private val KEY_HISTORY = stringPreferencesKey("import_history_list")

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class ImportHistoryEntry(
    val id: String,
    val fileName: String,
    val fileSize: Int = 0,
    val format: String = "",
    val encoding: String? = null,
    val isDuplicate: Boolean = false,
    val bookTitle: String? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "success" | "failed"
)

@Singleton
class ImportHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ds = context.importHistoryDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<ImportHistoryEntry>>(emptyList())

    val entries: StateFlow<List<ImportHistoryEntry>> = ds.data.map { prefs ->
        val raw = prefs[KEY_HISTORY] ?: "[]"
        runCatching { json.decodeFromString<List<ImportHistoryEntry>>(raw) }.getOrDefault(emptyList())
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun addEntry(entry: ImportHistoryEntry) {
        mutex.withLock {
            val current = _entries.value
            val next = (listOf(entry) + current).take(100)
            ds.edit { prefs -> prefs[KEY_HISTORY] = json.encodeToString(ListSerializer(ImportHistoryEntry.serializer()), next) }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            ds.edit { prefs -> prefs[KEY_HISTORY] = "[]" }
        }
    }
}
