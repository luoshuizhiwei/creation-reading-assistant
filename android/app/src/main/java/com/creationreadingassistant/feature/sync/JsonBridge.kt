package com.creationreadingassistant.feature.sync

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.LibrarySourceRefDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReaderCorrectionDao
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.entity.BookCategoryEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReaderCorrectionEntity
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.remote.DeviceInfoProvider
import com.creationreadingassistant.data.security.SecurePrefs
import com.creationreadingassistant.domain.model.SyncEnvelope
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject

/**
 * 来源引用导出 DTO（对齐 LibrarySourceRefEntity 字段）。
 */
@Serializable
data class SourceRefExport(
    val book_id: String,
    val root_id: String? = null,
    val provider_authority: String,
    val document_id: String? = null,
    val display_name: String,
    val format: String,
    val size: Long,
    val last_modified: Long? = null,
    val content_hash: String? = null,
    val candidate_fingerprint: String? = null,
    val last_seen_at: Long,
    val availability: String,
) {
    fun toEntity(): LibrarySourceRefEntity = LibrarySourceRefEntity(
        book_id = book_id,
        root_id = root_id,
        provider_authority = provider_authority,
        document_id = document_id,
        display_name = display_name,
        format = format,
        size = size,
        last_modified = last_modified,
        content_hash = content_hash,
        candidate_fingerprint = candidate_fingerprint,
        last_seen_at = last_seen_at,
        availability = availability,
    )

    companion object {
        fun fromEntity(entity: LibrarySourceRefEntity): SourceRefExport = SourceRefExport(
            book_id = entity.book_id,
            root_id = entity.root_id,
            provider_authority = entity.provider_authority,
            document_id = entity.document_id,
            display_name = entity.display_name,
            format = entity.format,
            size = entity.size,
            last_modified = entity.last_modified,
            content_hash = entity.content_hash,
            candidate_fingerprint = entity.candidate_fingerprint,
            last_seen_at = entity.last_seen_at,
            availability = entity.availability,
        )
    }
}

/**
 * 文本替换/净化规则导出 DTO（对齐 ReaderTextRuleEntity 字段）。
 */
@Serializable
data class TextRuleExport(
    val id: String,
    val kind: String,
    val name: String,
    val pattern: String? = null,
    val replacement: String,
    val builtin: Boolean,
    val enabled: Boolean,
    val scope: String,
    val book_id: String? = null,
    val position: Int,
    val created_at: Long,
    val updated_at: Long,
) {
    fun toEntity(): ReaderTextRuleEntity = ReaderTextRuleEntity(
        id = id,
        kind = kind,
        name = name,
        pattern = pattern,
        replacement = replacement,
        builtin = builtin,
        enabled = enabled,
        scope = scope,
        book_id = book_id,
        position = position,
        created_at = created_at,
        updated_at = updated_at,
    )

    companion object {
        fun fromEntity(entity: ReaderTextRuleEntity): TextRuleExport = TextRuleExport(
            id = entity.id,
            kind = entity.kind,
            name = entity.name,
            pattern = entity.pattern,
            replacement = entity.replacement,
            builtin = entity.builtin,
            enabled = entity.enabled,
            scope = entity.scope,
            book_id = entity.book_id,
            position = entity.position,
            created_at = entity.created_at,
            updated_at = entity.updated_at,
        )
    }
}

/**
 * 正文纠错记录导出 DTO（对齐 ReaderCorrectionEntity 字段）。
 */
@Serializable
data class CorrectionExport(
    val id: String,
    val book_id: String,
    val source_start: Int,
    val source_end: Int,
    val find_text: String,
    val replace_text: String,
    val status: String,
    val created_at: Long,
    val updated_at: Long,
) {
    fun toEntity(): ReaderCorrectionEntity = ReaderCorrectionEntity(
        id = id,
        book_id = book_id,
        source_start = source_start,
        source_end = source_end,
        find_text = find_text,
        replace_text = replace_text,
        status = status,
        created_at = created_at,
        updated_at = updated_at,
    )

    companion object {
        fun fromEntity(entity: ReaderCorrectionEntity): CorrectionExport = CorrectionExport(
            id = entity.id,
            book_id = entity.book_id,
            source_start = entity.source_start,
            source_end = entity.source_end,
            find_text = entity.find_text,
            replace_text = entity.replace_text,
            status = entity.status,
            created_at = entity.created_at,
            updated_at = entity.updated_at,
        )
    }
}

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
internal val localExportJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

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
    // —— S04-5 / S14 补齐：来源引用与阅读器规则/纠错数据 ——
    @SerialName("library_source_refs")
    val librarySourceRefs: List<SourceRefExport> = emptyList(),
    @SerialName("reader_text_rules")
    val textRules: List<TextRuleExport> = emptyList(),
    @SerialName("reader_text_corrections")
    val textCorrections: List<CorrectionExport> = emptyList(),
) {
    companion object {
        /** 当前导出结构版本；导入侧拒绝不认识的版本，防止未来 schema 被按旧结构静默导入。 */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * 本地恢复（JSON / ZIP）导入的合并结果快照（S14，2026-09-28）。
 *
 * @param acceptedBookIds 本次事务中**实际被写入**的书籍 id（本地缺失或远端较新）。
 * @param skippedBookContentPaths 因本地较新而被跳过的书籍行的 `books.local_content_path`
 *   （绝对路径；该字段为 null 的行不在此列）。调用方（`LocalZipBackup`）据此把
 *   「本地较新书籍的源文件」排除出文件覆盖集合——版本感知文件复制**只依据实际
 *   DB 合并结果**，不以 ZIP 是否含该文件、也不以路径模式猜测书 id。
 *
 * 为什么用回调而不是改返回值：`importFromString` 的既有调用点（JSON 导入、WebDAV
 * 恢复）与既有 mockk 桩都以 Unit 返回值为前提，带默认值的回调参数使全部既有调用
 * 与测试桩零改动；ZIP 恢复是唯一需要结果的路径。回调在 DB 事务提交后调用一次，
 * 且不得抛出异常（当前唯一调用方的实现是纯赋值）。
 */
data class ImportMergeResult(
    val acceptedBookIds: Set<String> = emptySet(),
    val skippedBookContentPaths: List<String> = emptyList(),
    val strictlyNewerBookContentPaths: List<String> = emptyList(),
    val equalBookContentPaths: List<String> = emptyList(),
    val strictlyNewerBookIds: Set<String> = emptySet(),
)

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
    private val deviceInfoProvider: DeviceInfoProvider? = null,
    private val sourceRefDao: LibrarySourceRefDao? = null,
    private val textRuleDao: ReaderTextRuleDao? = null,
    private val correctionDao: ReaderCorrectionDao? = null,
) {
    private val json = localExportJson

    private fun deviceId(context: Context): String =
        deviceInfoProvider?.provide()?.deviceId ?: SecurePrefs.getOrCreateAppInstanceId(context)

    private fun <T> envelope(type: String, entity: T, id: String, updatedAt: String, revision: Int, deletedAt: String?, deviceId: String): SyncEnvelope<T> =
        SyncEnvelope(id = id, type = type, revision = revision, deviceId = deviceId, updatedAt = updatedAt, deletedAt = deletedAt, payload = entity)

    /** 组装整库导出对象（导出文件与字符串共用）。 */
    internal suspend fun buildExport(context: Context): LocalExport {
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
        val activeBookIds = books.map { it.payload.id }.toSet()
        val sourceRefs = sourceRefDao?.getAll()
            ?.filter { it.book_id in activeBookIds }
            ?.map { SourceRefExport.fromEntity(it) } ?: emptyList()
        val textRules = textRuleDao?.observeAll()?.first()?.map { TextRuleExport.fromEntity(it) } ?: emptyList()
        val textCorrections = correctionDao?.let { dao ->
            books.flatMap { bookEnv ->
                dao.listForBook(bookEnv.payload.id).map { CorrectionExport.fromEntity(it) }
            }
        } ?: emptyList()
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
            librarySourceRefs = sourceRefs,
            textRules = textRules,
            textCorrections = textCorrections,
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
     *
     * N1-v4 窄接缝：在 DB 可能提交之前，通过 [onMergeResult] 回调可靠持久化备份身份与可执行计划；
     * 计划落盘失败时抛出异常，DB 事务不会提交，保证 fail-closed。
     */
    suspend fun importFromString(
        context: Context,
        text: String,
        onMergeResult: (ImportMergeResult) -> Unit = {},
    ) = withContext(ioDispatcher) {
        importSnapshot(context, json.decodeFromString<LocalExport>(text), onMergeResult)
    }

    internal suspend fun importSnapshot(
        context: Context,
        export: LocalExport,
        onMergeResult: (ImportMergeResult) -> Unit,
    ) {
        // 未来 schema 演进时在这里按版本分派解析；未知版本宁可拒绝也不能静默按旧结构导入。
        if (export.schemaVersion != LocalExport.CURRENT_SCHEMA_VERSION) {
            error("备份格式版本不受支持（schemaVersion=${export.schemaVersion}，当前支持 ${LocalExport.CURRENT_SCHEMA_VERSION}），已拒绝导入")
        }

        val acceptedBookIds = mutableSetOf<String>()
        val skippedBookContentPaths = mutableListOf<String>()
        val strictlyNewerBookContentPaths = mutableListOf<String>()
        val strictlyNewerBookIds = mutableSetOf<String>()
        val equalBookContentPaths = mutableListOf<String>()

        bookDao.runInTransaction {
            // 书籍：区分接受、等版本、严格较新
            val localBooks = batchLoad(export.books.map { it.payload.id }) { bookDao.getByIds(it) }
                .associateBy { it.id }
            val booksToUpsert = mutableListOf<BookEntity>()

            export.books.forEach { env ->
                val local = localBooks[env.payload.id]
                if (local == null) {
                    booksToUpsert += env.payload
                    acceptedBookIds += env.payload.id
                } else if (SyncMergePolicy.shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at)) {
                    booksToUpsert += env.payload
                    acceptedBookIds += env.payload.id
                } else {
                    val path = local.local_content_path
                    if (env.revision == local.revision && env.updatedAt == local.updated_at) {
                        path?.let {
                            equalBookContentPaths += it
                            skippedBookContentPaths += it
                        }
                    } else {
                        strictlyNewerBookIds += local.id
                        path?.let {
                            strictlyNewerBookContentPaths += it
                            skippedBookContentPaths += it
                        }
                    }
                }
            }

            val preCommitResult = ImportMergeResult(
                acceptedBookIds = acceptedBookIds,
                skippedBookContentPaths = skippedBookContentPaths,
                strictlyNewerBookContentPaths = strictlyNewerBookContentPaths,
                strictlyNewerBookIds = strictlyNewerBookIds,
                equalBookContentPaths = equalBookContentPaths,
            )

            // N1-v4 窄接缝：在 DB 事务实际写入和提交之前执行回调（例如可执行计划落盘）
            // 若执行失败，异常上抛使整个 DB 事务回滚，DB 零污染（fail-closed）
            onMergeResult(preCommitResult)

            booksToUpsert.forEach { bookDao.upsert(it) }

            // 灵感（孤立书籍引用按 SET_NULL 语义降级为空，保全独立灵感正文与想法）
            val localInspirations = batchLoad(export.inspirations.map { it.payload.id }) { inspirationDao.getByIds(it) }
                .associateBy { it.id }
            val existingBookIdsForInsp = (localBooks.keys + booksToUpsert.map { it.id }).toMutableSet()
            export.inspirations.forEach { env ->
                val local = localInspirations[env.payload.id]
                if (local == null || SyncMergePolicy.shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at)) {
                    val safePayload = if (env.payload.source_book_id != null && env.payload.source_book_id !in existingBookIdsForInsp) {
                        val bookExists = bookDao.getById(env.payload.source_book_id) != null
                        if (bookExists) {
                            existingBookIdsForInsp.add(env.payload.source_book_id)
                            env.payload
                        } else {
                            env.payload.copy(source_book_id = null)
                        }
                    } else {
                        env.payload
                    }
                    inspirationDao.upsert(safePayload)
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

            // 关联表与已读标记：R3 修复（2026-10-13）。关系行没有独立 revision，旧实现
            // 「任一备份列表非空就 clearAll 四表再重放备份」会把备份之后本地新增的
            // 标签/分类/书单/已读关联全部清掉（本地较新的书、被 revision 门拒收的旧书
            // 同样受害），且备份只含一类关系时其余三类也一并被清——这是确定性数据丢失，
            // 违背 ProfileRoute「按版本合并、旧不覆新」的恢复承诺。改为保守合并：
            // 保留本地全部现存关系，仅把备份中本地缺失的行补入；chapter_reads 同键
            // 保留较新的 read_at，避免旧备份倒退已读时间。R3-v2 补充：shelf_book 的
            // ShelfBookEntity 除复合主键外还有 position 列，REPLACE 会整行覆盖——
            // 同键直接 upsertAll 会用旧备份 position 覆盖本地重排后的顺序，因此与
            // chapter_reads 一样先查本地、同键整行保留、只写本地缺失的行。
            bookTagDao.upsertAll(export.bookTags)
            bookCategoryDao.upsertAll(export.bookCategories)
            mergeShelfBooks(export.shelfBooks)
            mergeChapterReads(export.chapterReads)

            // —— S04-5 / S14 补齐：来源引用与阅读器规则/纠错数据（旧不覆新，孤立引用跳过）——
            sourceRefDao?.let { dao ->
                if (export.librarySourceRefs.isNotEmpty()) {
                    val candidateBookIds = export.librarySourceRefs.map { it.book_id }.distinct()
                    val mergedBookIds = (localBooks.keys + booksToUpsert.map { it.id }).toMutableSet()
                    val remainingIds = candidateBookIds.filter { it !in mergedBookIds }
                    if (remainingIds.isNotEmpty()) {
                        val existingLocal = batchLoad(remainingIds) { bookDao.getByIds(it) }
                        mergedBookIds += existingLocal.map { it.id }
                    }

                    val localRefs = dao.getAll().associateBy { it.book_id }
                    export.librarySourceRefs.forEach { item ->
                        if (item.book_id in mergedBookIds) {
                            val local = localRefs[item.book_id]
                            if (local == null || item.last_seen_at >= local.last_seen_at) {
                                dao.upsert(item.toEntity())
                            }
                        }
                    }
                }
            }

            textRuleDao?.let { dao ->
                if (export.textRules.isNotEmpty()) {
                    export.textRules.forEach { item ->
                        val local = dao.getById(item.id)
                        if (local == null || item.updated_at >= local.updated_at) {
                            dao.upsert(item.toEntity())
                        }
                    }
                }
            }

            correctionDao?.let { dao ->
                if (export.textCorrections.isNotEmpty()) {
                    export.textCorrections.forEach { item ->
                        val local = dao.getById(item.id)
                        if (local == null || item.updated_at >= local.updated_at) {
                            dao.upsert(item.toEntity())
                        }
                    }
                }
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

    /**
     * 章节已读标记保守合并（R3）：复合主键 (book_id, chapter_index) 相同的行保留较新的
     * read_at，本地缺失的行按备份补入。read_at 是 markRead 的 `Instant.now().toString()`
     * ISO-8601 时间戳，字典序与时间序一致；相等时保留本地（含格式差异），重复恢复幂等。
     * 解析失败的时间戳按本地有效、备份无效处理，不吞错重新抛出。
     */
    private suspend fun mergeChapterReads(backup: List<ChapterReadEntity>) {
        if (backup.isEmpty()) return
        val local = chapterReadDao.getByBookIds(backup.map { it.book_id })
            .associateBy { it.book_id to it.chapter_index }
        val toUpsert = backup.filter { remote ->
            val existing = local[remote.book_id to remote.chapter_index]
            existing == null || isNewerReadAt(remote.read_at, existing.read_at)
        }
        if (toUpsert.isNotEmpty()) chapterReadDao.upsertAll(toUpsert)
    }

    /**
     * 书单关联保守合并（R3-v2）：复合主键 (shelf_id, book_id) 相同的行**整行保留本地**，
     * 只补入本地缺失的键。ShelfBookEntity 的 position 是非主键列，REPLACE 会整行覆盖，
     * 同键直接 upsertAll 会用旧备份的 position 倒退本地重排后的书单顺序——关系行没有
     * 版本字段，无法判定本地 position 与备份 position 谁新，保守保留本地。
     */
    private suspend fun mergeShelfBooks(backup: List<ShelfBookEntity>) {
        if (backup.isEmpty()) return
        val localKeys = shelfBookDao.getByBookIds(backup.map { it.book_id })
            .map { it.shelf_id to it.book_id }
            .toHashSet()
        val toUpsert = backup.filter { (it.shelf_id to it.book_id) !in localKeys }
        if (toUpsert.isNotEmpty()) shelfBookDao.upsertAll(toUpsert)
    }

    /** 备份 read_at 严格晚于本地时才算「较新」；相等或无法解析（且本地可解析）都保留本地。 */
    private fun isNewerReadAt(remoteReadAt: String, localReadAt: String): Boolean =
        runCatching {
            Instant.parse(remoteReadAt).isAfter(Instant.parse(localReadAt))
        }.getOrThrow()

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
