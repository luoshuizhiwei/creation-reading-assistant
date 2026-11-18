package com.creationreadingassistant.data.repository

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导入「阅读记录」JSON（2026-11-17，配合去掉持久索引后的重装场景）。
 *
 * 输入是诊断导出的一份 `cra-records-*.json`：每条记录来自旧库的核心业务表，
 * 大文本列在导出时被截断为 300 字并附 `列名#len` 记录原始长度（本导入器忽略 `#len`）。
 *
 * ### 匹配与安全
 * - 书按 `content_hash` 优先匹配本地书；没有哈希或匹配不到时退化为「标题 + 文件大小」。
 * - 匹配不到的书**不新建**（正文不在这里），列入"待关联"，读位/会话原样跳过。
 * - 读位：本地已有且 `updated_at` 不早于记录时**保留本地**，不覆盖更新的进度。
 * - 会话/灵感：按主键 upsert，可重复执行（幂等），不产生重复行。
 */
@Singleton
class ReadingRecordsImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val progressDao: ReadingProgressDao,
    private val sessionDao: ReadingSessionDao,
    private val inspirationDao: InspirationDao,
) {

    data class Report(
        val booksInFile: Int = 0,
        val booksMatched: Int = 0,
        val missingBooks: List<String> = emptyList(),
        val progressRestored: Int = 0,
        val progressKeptLocal: Int = 0,
        val sessionsRestored: Int = 0,
        val inspirationsRestored: Int = 0,
        val inspirationsFailed: Int = 0,
        val error: String? = null,
    ) {
        fun summary(): String = buildString {
            if (error != null) {
                append("导入失败：").append(error)
                return@buildString
            }
            append("记录含 ").append(booksInFile).append(" 本，匹配到 ").append(booksMatched).append(" 本\n")
            append("读位恢复 ").append(progressRestored).append(" 条")
            if (progressKeptLocal > 0) append("（保留本地较新 ").append(progressKeptLocal).append(" 条）")
            append("\n阅读会话 ").append(sessionsRestored).append(" 条，灵感 ").append(inspirationsRestored).append(" 条")
            if (inspirationsFailed > 0) append("（").append(inspirationsFailed).append(" 条写入失败）")
            if (booksMatched == 0) append("\n没有匹配到任何书：请先把书导入书架，再点一次导入。")
            if (missingBooks.isNotEmpty()) {
                append("\n未匹配（需先导入这些书）：").append(missingBooks.take(8).joinToString("、"))
                if (missingBooks.size > 8) append(" 等 ").append(missingBooks.size).append(" 本")
            }
        }
    }

    suspend fun importFromUri(uri: Uri): Report = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: return@withContext Report(error = "无法读取所选文件")
            importFromJson(text)
        }.getOrElse { Report(error = it.javaClass.simpleName + ": " + it.message) }
    }

    suspend fun importFromJson(text: String): Report = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(text)
            val booksJson = root.optJSONArray("books") ?: JSONArray()

            // 本地书索引：content_hash → bookId，以及 title+size → bookId
            val byHash = HashMap<String, String>()
            val byTitleSize = HashMap<String, String>()
            for (bookId in bookDao.activeIds()) {
                val book = bookDao.getById(bookId) ?: continue
                book.content_hash?.takeIf { it.isNotBlank() }?.let { byHash[it] = book.id }
                byTitleSize[keyOf(book.title, book.size.toLong())] = book.id
            }

            val idMap = HashMap<String, String>() // 记录里的 book_id → 本地 book_id
            val missing = ArrayList<String>()
            var matched = 0
            for (i in 0 until booksJson.length()) {
                val b = booksJson.optJSONObject(i) ?: continue
                val recordBookId = b.optString("id")
                val hash = b.optString("content_hash")
                val title = b.optString("title")
                val size = b.optLong("size", -1L)
                val local = byHash[hash] ?: byTitleSize[keyOf(title, size)]
                if (local != null) {
                    idMap[recordBookId] = local
                    matched++
                } else {
                    missing.add(title.ifBlank { recordBookId.take(8) })
                }
            }

            var progressRestored = 0
            var progressKept = 0
            val progressJson = root.optJSONArray("reading_progress") ?: JSONArray()
            for (i in 0 until progressJson.length()) {
                val p = progressJson.optJSONObject(i) ?: continue
                val localBookId = idMap[p.optString("book_id")] ?: continue
                val incomingUpdatedAt = p.optString("updated_at")
                val local = progressDao.getByBook(localBookId)
                if (local != null && local.updated_at >= incomingUpdatedAt) {
                    progressKept++
                    continue
                }
                progressDao.upsert(
                    ReadingProgressEntity(
                        book_id = localBookId,
                        progress_percent = p.optDouble("progress_percent", 0.0).toFloat(),
                        last_read_at = p.optStringOrNull("last_read_at"),
                        total_reading_time_ms = p.optLong("total_reading_time_ms", 0L),
                        completion_state = p.optString("completion_state", "reading").ifBlank { "reading" },
                        current_location_json = p.optStringOrNull("current_location_json"),
                        payload = p.optStringOrNull("payload"),
                        revision = p.optInt("revision", 1),
                        device_id = p.optStringOrNull("device_id"),
                        updated_at = incomingUpdatedAt.ifBlank { "1970-01-01T00:00:00Z" },
                        completed_at = if (p.has("completed_at") && !p.isNull("completed_at")) p.optLong("completed_at") else null,
                        deleted_at = p.optStringOrNull("deleted_at"),
                    ),
                )
                progressRestored++
            }

            var sessionsRestored = 0
            val sessionsJson = root.optJSONArray("reading_sessions") ?: JSONArray()
            for (i in 0 until sessionsJson.length()) {
                val s = sessionsJson.optJSONObject(i) ?: continue
                val localBookId = idMap[s.optString("book_id")] ?: continue
                val id = s.optString("id")
                if (id.isBlank()) continue
                sessionDao.upsert(
                    ReadingSessionEntity(
                        id = id,
                        book_id = localBookId,
                        started_at = s.optStringOrNull("started_at"),
                        ended_at = s.optStringOrNull("ended_at"),
                        duration_ms = s.optLong("duration_ms", 0L),
                        progress_percent = if (s.has("progress_percent") && !s.isNull("progress_percent")) {
                            s.optDouble("progress_percent", 0.0).toFloat()
                        } else null,
                        created_at = s.optStringOrNull("created_at"),
                        device_id = s.optStringOrNull("device_id"),
                        revision = s.optInt("revision", 1),
                        payload = s.optStringOrNull("payload"),
                        updated_at = s.optString("updated_at").ifBlank { "1970-01-01T00:00:00Z" },
                        deleted_at = s.optStringOrNull("deleted_at"),
                    ),
                )
                sessionsRestored++
            }

            var inspirationsRestored = 0
            var inspirationsFailed = 0
            val inspirationsJson = root.optJSONArray("inspirations") ?: JSONArray()
            for (i in 0 until inspirationsJson.length()) {
                val n = inspirationsJson.optJSONObject(i) ?: continue
                val id = n.optString("id")
                if (id.isBlank()) continue
                // 归属书匹配不到时**必须置空**：旧 id 在本地不存在，写进去会触发 FOREIGN KEY
                // 约束并让整批导入回滚（真机 2026-11-17 复现）。
                val sourceBookId = n.optStringOrNull("source_book_id")?.let { idMap[it] }
                val ok = runCatching {
                inspirationDao.upsert(
                    InspirationEntity(
                        id = id,
                        title = n.optString("title"),
                        body = n.optString("body"),
                        type = n.optString("type", "note").ifBlank { "note" },
                        status = n.optString("status", "inbox").ifBlank { "inbox" },
                        source_book_id = sourceBookId,
                        payload = n.optStringOrNull("payload"),
                        created_at = n.optString("created_at").ifBlank { "1970-01-01T00:00:00Z" },
                        device_id = n.optStringOrNull("device_id"),
                        revision = n.optInt("revision", 1),
                        updated_at = n.optString("updated_at").ifBlank { "1970-01-01T00:00:00Z" },
                        deleted_at = n.optStringOrNull("deleted_at"),
                    ),
                )
                }.isSuccess
                if (ok) inspirationsRestored++ else inspirationsFailed++
            }

            Report(
                booksInFile = booksJson.length(),
                booksMatched = matched,
                missingBooks = missing,
                progressRestored = progressRestored,
                progressKeptLocal = progressKept,
                sessionsRestored = sessionsRestored,
                inspirationsRestored = inspirationsRestored,
                inspirationsFailed = inspirationsFailed,
            )
        }.getOrElse { Report(error = it.javaClass.simpleName + ": " + it.message) }
    }

    private fun keyOf(title: String, size: Long): String = title.trim() + "|" + size

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null
}
