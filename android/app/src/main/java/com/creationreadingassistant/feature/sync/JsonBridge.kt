package com.creationreadingassistant.feature.sync

import android.content.Context
import android.net.Uri
import android.provider.Settings
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookCategoryEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.domain.model.SyncEnvelope
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
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
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: String,
    val deviceId: String,
    val books: List<SyncEnvelope<BookEntity>>,
    val inspirations: List<SyncEnvelope<InspirationEntity>>,
    val progress: List<SyncEnvelope<ReadingProgressEntity>>,
    val sessions: List<SyncEnvelope<ReadingSessionEntity>>,
    // —— 2026-08-22 补齐：此前导出只含四类实体，笔记/高亮/标签/分类/书单/已读标记
    //    在备份恢复时全部丢失（与 StorageSubPage 承诺不符）。以下字段带默认空列表，
    //    旧备份（无这些字段）仍可读；新备份对旧 App 同样兼容（ignoreUnknownKeys）。
    val notes: List<SyncEnvelope<NoteEntity>> = emptyList(),
    val highlights: List<SyncEnvelope<HighlightEntity>> = emptyList(),
    val tags: List<SyncEnvelope<TagEntity>> = emptyList(),
    val categories: List<SyncEnvelope<CategoryEntity>> = emptyList(),
    val shelves: List<SyncEnvelope<ShelfEntity>> = emptyList(),
    val bookTags: List<BookTagEntity> = emptyList(),
    val bookCategories: List<BookCategoryEntity> = emptyList(),
    val shelfBooks: List<ShelfBookEntity> = emptyList(),
    val chapterReads: List<ChapterReadEntity> = emptyList(),
) {
    companion object {
        /** 当前导出结构版本；导入侧拒绝不认识的版本，防止未来 schema 被按旧结构静默导入。 */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

class JsonBridge @Inject constructor(
    private val bookDao: BookDao,
    private val inspirationDao: InspirationDao,
    private val progressDao: ReadingProgressDao,
    private val sessionDao: ReadingSessionDao,
    private val noteDao: NoteDao,
    private val highlightDao: HighlightDao,
    private val tagDao: TagDao,
    private val categoryDao: CategoryDao,
    private val shelfDao: ShelfDao,
    private val bookTagDao: BookTagDao,
    private val bookCategoryDao: BookCategoryDao,
    private val shelfBookDao: ShelfBookDao,
    private val chapterReadDao: ChapterReadDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
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
        val notes = noteDao.observeAllActive().first().map {
            envelope("note", it, it.id, it.updated_at, it.revision, it.deleted_at, dev)
        }
        val highlights = highlightDao.observeAllActive().first().map {
            envelope("highlight", it, it.id, it.updated_at, it.revision, it.deleted_at, dev)
        }
        val tags = tagDao.getAllActive().map {
            envelope("tag", it, it.id, it.updated_at, it.revision, it.deleted_at, dev)
        }
        val categories = categoryDao.getAllActive().map {
            envelope("category", it, it.id, it.updated_at, it.revision, it.deleted_at, dev)
        }
        val shelves = shelfDao.getAllActive().map {
            envelope("shelf", it, it.id, it.updated_at, it.revision, it.deleted_at, dev)
        }
        return LocalExport(
            exportedAt = Instant.now().toString(),
            deviceId = dev,
            books = books,
            inspirations = inspirations,
            progress = progress,
            sessions = sessions,
            notes = notes,
            highlights = highlights,
            tags = tags,
            categories = categories,
            shelves = shelves,
            bookTags = bookTagDao.getAllActive(),
            bookCategories = bookCategoryDao.getAllActive(),
            shelfBooks = shelfBookDao.getAllActive(),
            chapterReads = chapterReadDao.getAll(),
        )
    }

    /** 导出整库到用户用 SAF 选定的 JSON 文件。 */
    suspend fun exportTo(context: Context, uri: Uri) = withContext(ioDispatcher) {
        val export = buildExport(context)
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(json.encodeToString(LocalExport.serializer(), export).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("无法写入目标文件：$uri")
    }

    /** 导出整库为 JSON 字符串（供 WebDAV 远程备份复用）。 */
    suspend fun exportToString(context: Context): String = withContext(ioDispatcher) {
        json.encodeToString(LocalExport.serializer(), buildExport(context))
    }

    /** 从用户用 SAF 选定的 JSON 文件导入整库（按 id 覆盖合并）。 */
    suspend fun importFrom(context: Context, uri: Uri) = withContext(ioDispatcher) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: throw IllegalStateException("无法读取源文件：$uri")
        importFromString(context, text)
    }

    /**
     * 从 JSON 字符串导入整库（供 WebDAV 下载恢复复用）。逐条 revision 校验，避免旧数据覆盖新数据。
     *
     * 全程包在单个数据库事务中：导入中途失败时整体回滚，不会留下半导入的脏状态。
     * 合并策略与局域网同步拉取共用 [SyncMergePolicy]，消除两条路径的策略分叉。
     * 每类数据先批量查询建 Map，循环内查 Map，消除逐条 getById 的 N+1。
     */
    suspend fun importFromString(context: Context, text: String) = withContext(ioDispatcher) {
        val export = json.decodeFromString<LocalExport>(text)
        // 未来 schema 演进时在这里按版本分派解析；未知版本宁可拒绝也不能静默按旧结构导入。
        if (export.schemaVersion != LocalExport.CURRENT_SCHEMA_VERSION) {
            error("备份格式版本不受支持（schemaVersion=${export.schemaVersion}，当前支持 ${LocalExport.CURRENT_SCHEMA_VERSION}），已拒绝导入")
        }

        bookDao.runInTransaction {
            // 书籍：revision 高者胜；revision 相等时比较 updated_at
            val localBooks = batchLoad(export.books.map { it.payload.id }) { bookDao.getByIds(it) }
                .associateBy { it.id }
            export.books.forEach { env ->
                val local = localBooks[env.payload.id]
                if (local == null || SyncMergePolicy.shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at)) {
                    bookDao.upsert(env.payload)
                }
            }

            // 灵感
            val localInspirations = batchLoad(export.inspirations.map { it.payload.id }) { inspirationDao.getByIds(it) }
                .associateBy { it.id }
            export.inspirations.forEach { env ->
                val local = localInspirations[env.payload.id]
                if (local == null || SyncMergePolicy.shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at)) {
                    inspirationDao.upsert(env.payload)
                }
            }

            // 进度
            val localProgress = batchLoad(export.progress.map { it.payload.book_id }) { progressDao.getByBooks(it) }
                .associateBy { it.book_id }
            export.progress.forEach { env ->
                val local = localProgress[env.payload.book_id]
                if (local == null || SyncMergePolicy.shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at)) {
                    progressDao.upsert(env.payload)
                }
            }

            // 会话
            val localSessions = batchLoad(export.sessions.map { it.payload.id }) { sessionDao.getByIds(it) }
                .associateBy { it.id }
            export.sessions.forEach { env ->
                val local = localSessions[env.payload.id]
                if (local == null || SyncMergePolicy.shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at)) {
                    sessionDao.upsert(env.payload)
                }
            }

            // —— 2026-08-22 补齐：笔记/高亮/标签/分类/书单按 revision 合并（与四类一致）——
            mergeEntities(export.notes, localId = { it.payload.id }, query = { noteDao.getById(it) }, upsert = { noteDao.upsert(it) })
            mergeEntities(export.highlights, localId = { it.payload.id }, query = { highlightDao.getById(it) }, upsert = { highlightDao.upsert(it) })
            mergeEntities(export.tags, localId = { it.payload.id }, query = { tagDao.getById(it) }, upsert = { tagDao.upsert(it) })
            mergeEntities(export.categories, localId = { it.payload.id }, query = { categoryDao.getById(it) }, upsert = { categoryDao.upsert(it) })
            mergeEntities(export.shelves, localId = { it.payload.id }, query = { shelfDao.getById(it) }, upsert = { shelfDao.upsert(it) })

            // 关联表与已读标记：整表重建（派生关系无独立 revision，备份为准）
            if (export.bookTags.isNotEmpty() || export.bookCategories.isNotEmpty() ||
                export.shelfBooks.isNotEmpty() || export.chapterReads.isNotEmpty()
            ) {
                bookTagDao.clearAll()
                bookCategoryDao.clearAll()
                shelfBookDao.clearAll()
                chapterReadDao.clearAll()
                bookTagDao.upsertAll(export.bookTags)
                bookCategoryDao.upsertAll(export.bookCategories)
                shelfBookDao.upsertAll(export.shelfBooks)
                chapterReadDao.upsertAll(export.chapterReads)
            }
        }
    }

    /**
     * 按 revision 合并单类实体：本地缺失或远端更新时覆盖（与局域网同步同一策略）。
     */
    private suspend fun <T> mergeEntities(
        envelopes: List<SyncEnvelope<T>>,
        localId: (SyncEnvelope<T>) -> String,
        query: suspend (String) -> T?,
        upsert: suspend (T) -> Unit,
    ) {
        envelopes.forEach { env ->
            val id = localId(env)
            val local = id?.let { query(it) }
            if (local == null || SyncMergePolicy.shouldAcceptRemote(env.revision, revisionOf(local), env.updatedAt, updatedAtOf(local))) {
                upsert(env.payload)
            }
        }
    }

    private fun <T> revisionOf(entity: T): Int = when (entity) {
        is NoteEntity -> entity.revision
        is HighlightEntity -> entity.revision
        is TagEntity -> entity.revision
        is CategoryEntity -> entity.revision
        is ShelfEntity -> entity.revision
        else -> 1
    }

    private fun <T> updatedAtOf(entity: T): String = when (entity) {
        is NoteEntity -> entity.updated_at
        is HighlightEntity -> entity.updated_at
        is TagEntity -> entity.updated_at
        is CategoryEntity -> entity.updated_at
        is ShelfEntity -> entity.updated_at
        else -> ""
    }

    /** 空列表不走查询（Room 的 IN () 语法对空列表无意义），直接返回空。 */
    private suspend fun <T> batchLoad(ids: List<String>, query: suspend (List<String>) -> List<T>): List<T> =
        if (ids.isEmpty()) emptyList() else query(ids)
}
