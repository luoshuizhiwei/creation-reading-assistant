package com.creationreadingassistant.feature.sync

import android.content.Context
import android.net.Uri
import android.provider.Settings
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.domain.model.SyncEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject

/**
 * 本地 JSON 导出/导入桥接（P1 的「手动同步」手段）。
 *
 * 复用 src/types/sync.ts 的 SyncEnvelope 信封结构：每条记录包成
 * SyncEnvelope<实体>，整库导出为 LocalExport。P3 局域网同步将复用同一信封，
 * 仅把 payload 换成跨端对象即可，无需改契约。
 *
 * 该桥接用于「原生 App 实例间」的数据迁移（如旧机 → 新机），
 * 与现有 Capacitor 旧 App 的数据库不互通（新版为独立 Room 库）。
 * P4 起 `exportToString()` 供 WebDAV 远程备份复用同一份导出内容。
 */
@Serializable
data class LocalExport(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val deviceId: String,
    val books: List<SyncEnvelope<BookEntity>>,
    val inspirations: List<SyncEnvelope<InspirationEntity>>,
    val progress: List<SyncEnvelope<ReadingProgressEntity>>,
    val sessions: List<SyncEnvelope<ReadingSessionEntity>>,
)

class JsonBridge @Inject constructor(
    private val bookDao: BookDao,
    private val inspirationDao: InspirationDao,
    private val progressDao: ReadingProgressDao,
    private val sessionDao: ReadingSessionDao,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"

    private fun <T> envelope(type: String, entity: T, id: String, updatedAt: String, revision: Int, deletedAt: String?, deviceId: String): SyncEnvelope<T> =
        SyncEnvelope(id = id, type = type, revision = revision, deviceId = deviceId, updatedAt = updatedAt, deletedAt = deletedAt, payload = entity)

    /** 组装整库导出对象（导出文件与字符串共用）。 */
    private suspend fun buildExport(context: Context): LocalExport {
        val dev = deviceId(context)
        val books = bookDao.observeAllActive().first().map {
            envelope("book", it, it.id, it.updated_at, it.revision, it.deleted_at, dev)
        }
        val inspirations = inspirationDao.observeAllActive().first().map {
            envelope("inspiration", it, it.id, it.updated_at, it.revision, it.deleted_at, dev)
        }
        val progress = progressDao.observeAllActive().first().map {
            envelope("progress", it, it.book_id, it.updated_at, it.revision, it.deleted_at, dev)
        }
        val sessions = sessionDao.observeAllActive().first().map {
            envelope("session", it, it.id, it.updated_at, it.revision, it.deleted_at, dev)
        }
        return LocalExport(
            exportedAt = Instant.now().toString(),
            deviceId = dev,
            books = books,
            inspirations = inspirations,
            progress = progress,
            sessions = sessions,
        )
    }

    /** 导出整库到用户用 SAF 选定的 JSON 文件。 */
    suspend fun exportTo(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val export = buildExport(context)
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(json.encodeToString(LocalExport.serializer(), export).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("无法写入目标文件：$uri")
    }

    /** 导出整库为 JSON 字符串（供 WebDAV 远程备份复用）。 */
    suspend fun exportToString(context: Context): String = withContext(Dispatchers.IO) {
        json.encodeToString(LocalExport.serializer(), buildExport(context))
    }

    /** 从用户用 SAF 选定的 JSON 文件导入整库（按 id 覆盖合并）。 */
    suspend fun importFrom(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: throw IllegalStateException("无法读取源文件：$uri")
        importFromString(context, text)
    }

    /** 从 JSON 字符串导入整库（供 WebDAV 下载恢复复用）。逐条 revision 校验，避免旧数据覆盖新数据。 */
    suspend fun importFromString(context: Context, text: String) = withContext(Dispatchers.IO) {
        val export = json.decodeFromString<LocalExport>(text)

        // 书籍：逐条比较 revision，仅当导入记录 revision >= 本地时才覆盖
        export.books.forEach { env ->
            val local = bookDao.getById(env.payload.id)
            if (local == null || env.revision >= local.revision) {
                bookDao.upsert(env.payload)
            }
        }

        // 灵感
        export.inspirations.forEach { env ->
            val local = inspirationDao.getById(env.payload.id)
            if (local == null || env.revision >= local.revision) {
                inspirationDao.upsert(env.payload)
            }
        }

        // 进度
        export.progress.forEach { env ->
            val local = progressDao.getByBook(env.payload.book_id)
            if (local == null || env.revision >= local.revision) {
                progressDao.upsert(env.payload)
            }
        }

        // 会话
        export.sessions.forEach { env ->
            val local = sessionDao.getById(env.payload.id)
            if (local == null || env.revision >= local.revision) {
                sessionDao.upsert(env.payload)
            }
        }
    }
}
