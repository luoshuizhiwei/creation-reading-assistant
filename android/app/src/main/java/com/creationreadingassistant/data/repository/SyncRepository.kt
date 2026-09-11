package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.remote.DeviceInfoProvider
import com.creationreadingassistant.data.remote.SyncApiProvider
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.domain.model.SyncEnvelope
import com.creationreadingassistant.feature.library.FormatClassifier
import com.creationreadingassistant.feature.sync.SyncMergePolicy
import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 局域网同步仓储：拉取（pull）与推送（push）。
 *
 * 核心约定（与 P1 的 V2 schema 同构）：每张业务表都有 `payload TEXT` 真相源列，
 * 因此信封的 payload（业务对象 JSON）原样写入该列，提升列仅用于查询。
 * 拉取时直接把信封映射成实体落库；推送时把本地实体 payload 解析回信封发往服务端。
 */
@Singleton
class SyncRepository @Inject constructor(
    private val apiProvider: SyncApiProvider,
    private val configStore: SyncConfigStore,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val bookDao: BookDao,
    private val bookFileDao: BookFileDao,
    private val inspirationDao: InspirationDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readingSessionDao: ReadingSessionDao,
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /**
     * 测试 seam：JVM 单测里 `android.net.Uri.fromFile` 恒返回 null
     * （`unitTests.isReturnDefaultValues = true`），注入受控映射以便验证闭环回写；
     * 生产默认即 [android.net.Uri.fromFile] 的 toString。
     */
    internal var fileUri: (File) -> String = { file -> Uri.fromFile(file).toString() }

    data class PullResult(
        val books: Int,
        val inspirations: Int,
        val progress: Int,
        val sessions: Int,
        val failedItems: List<SyncFailedEntry> = emptyList(),
    )

    data class PushResult(
        val applied: SyncContract.SyncPushResult.AppliedCount,
        val conflicts: List<SyncConflictEntry> = emptyList(),
        val failedItems: List<SyncFailedEntry> = emptyList(),
    )

    data class SyncFailedEntry(
        val type: String,
        val id: String,
        val title: String? = null,
        val reason: String,
    )

    data class SyncConflictEntry(
        val type: String,
        val id: String,
        val title: String? = null,
        val remoteUpdatedAt: String,
        val localUpdatedAt: String,
        val resolution: String,
    )

    // ---- 拉取：服务端全量 → 本地库 ----
    suspend fun pull(): PullResult = withContext(ioDispatcher) {
        val api = apiProvider.current() ?: error("尚未配对，无法同步")
        val device = deviceInfoProvider.provide()
        val resp = api.pull(SyncContract.SyncPullPayloadRequest(device))
        val failures = mutableListOf<SyncFailedEntry>()
        var bookCount = 0
        var inspirationCount = 0
        var progressCount = 0
        var sessionCount = 0

        // 逐条 apply 包在单个事务里：要么整批落库，要么整体回滚，不留半同步状态。
        bookDao.runInTransaction {
            resp.books.forEach { env ->
                runCatching { applyBook(env) }
                    .onSuccess { bookCount++ }
                    .onFailure { failures.add(SyncFailedEntry("book", env.id, env.payload["title"]?.toString(), it.message ?: "未知错误")) }
            }
            resp.inspirations.forEach { env ->
                runCatching { applyInspiration(env) }
                    .onSuccess { inspirationCount++ }
                    .onFailure { failures.add(SyncFailedEntry("inspiration", env.id, env.payload["title"]?.toString(), it.message ?: "未知错误")) }
            }
            resp.progress.forEach { env ->
                runCatching { applyProgress(env) }
                    .onSuccess { progressCount++ }
                    .onFailure { failures.add(SyncFailedEntry("progress", env.id, null, it.message ?: "未知错误")) }
            }
            resp.sessions.forEach { env ->
                runCatching { applySession(env) }
                    .onSuccess { sessionCount++ }
                    .onFailure { failures.add(SyncFailedEntry("session", env.id, null, it.message ?: "未知错误")) }
            }
        }
        configStore.markSyncedAt(Instant.now().toString())
        PullResult(bookCount, inspirationCount, progressCount, sessionCount, failures)
    }

    private fun str(obj: JsonObject, key: String): String? =
        obj[key]?.jsonPrimitive?.content

    private fun jsonStr(payload: JsonObject): String = payload.toString()

    private suspend fun applyBook(env: SyncEnvelope<JsonObject>) {
        val local = bookDao.getById(env.id)
        if (local != null && !shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at)) return
        val p = env.payload
        bookDao.upsert(
            BookEntity(
                id = env.id,
                title = str(p, "title") ?: "(未命名)",
                author = str(p, "author"),
                format = str(p, "format") ?: "txt",
                content_hash = str(p, "contentHash"),
                payload = jsonStr(p),
                revision = env.revision,
                device_id = env.deviceId,
                updated_at = env.updatedAt,
                deleted_at = env.deletedAt,
            ),
        )
    }

    private suspend fun applyInspiration(env: SyncEnvelope<JsonObject>) {
        val local = inspirationDao.getById(env.id)
        if (local != null && !shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at)) return
        val p = env.payload
        inspirationDao.upsert(
            InspirationEntity(
                id = env.id,
                title = str(p, "title") ?: "(未命名)",
                body = str(p, "body") ?: "",
                type = str(p, "type") ?: "note",
                status = str(p, "status") ?: "inbox",
                source_book_id = str(p, "sourceBookId"),
                payload = jsonStr(p),
                revision = env.revision,
                device_id = env.deviceId,
                created_at = str(p, "createdAt") ?: env.updatedAt,
                updated_at = env.updatedAt,
                deleted_at = env.deletedAt,
            ),
        )
    }

    private suspend fun applyProgress(env: SyncEnvelope<JsonObject>) {
        val local = readingProgressDao.getByBook(env.id)
        if (local != null && !shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at)) return
        val p = env.payload
        readingProgressDao.upsert(
            ReadingProgressEntity(
                book_id = env.id,
                payload = jsonStr(p),
                revision = env.revision,
                device_id = env.deviceId,
                updated_at = env.updatedAt,
                deleted_at = env.deletedAt,
            ),
        )
    }

    private suspend fun applySession(env: SyncEnvelope<JsonObject>) {
        val local = readingSessionDao.getById(env.id)
        if (local != null && !shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at)) return
        val p = env.payload
        readingSessionDao.upsert(
            ReadingSessionEntity(
                id = env.id,
                book_id = str(p, "bookId") ?: "",
                payload = jsonStr(p),
                revision = env.revision,
                device_id = env.deviceId,
                created_at = str(p, "createdAt") ?: env.updatedAt,
                updated_at = env.updatedAt,
                deleted_at = env.deletedAt,
            ),
        )
    }

    /**
     * 冲突合并策略：委托给 [SyncMergePolicy]，与本地 JSON / WebDAV 恢复路径
     * （[com.creationreadingassistant.feature.sync.JsonBridge]）共用同一纯函数，避免策略分叉。
     */
    private fun shouldAcceptRemote(
        remoteRevision: Int, localRevision: Int,
        remoteUpdatedAt: String, localUpdatedAt: String,
    ): Boolean = SyncMergePolicy.shouldAcceptRemote(remoteRevision, localRevision, remoteUpdatedAt, localUpdatedAt)

    // ---- 推送：本地库 → 服务端 ----
    suspend fun push(): PushResult = withContext(ioDispatcher) {
        val api = apiProvider.current() ?: error("尚未配对，无法同步")
        val device = deviceInfoProvider.provide()
        val failures = mutableListOf<SyncFailedEntry>()
        val localBookMap = mutableMapOf<String, BookEntity>()
        val localInspirationMap = mutableMapOf<String, InspirationEntity>()
        val localProgressMap = mutableMapOf<String, ReadingProgressEntity>()
        val localSessionMap = mutableMapOf<String, ReadingSessionEntity>()

        // 活跃记录 + 软删除记录均推送，确保删除操作传播到对端
        val activeBooks = bookDao.observeAllActive().first()
        val deletedBooks = bookDao.getDeleted()
        val allBooks = activeBooks + deletedBooks
        allBooks.forEach { localBookMap[it.id] = it }
        val books = allBooks.map { e ->
            SyncEnvelope(
                id = e.id, type = "book", revision = e.revision,
                deviceId = e.device_id ?: device.deviceId, updatedAt = e.updated_at,
                deletedAt = e.deleted_at, payload = backfillBookPayload(e),
            )
        }
        val activeInspirations = inspirationDao.observeAllActive().first()
        val deletedInspirations = inspirationDao.getDeleted()
        val allInspirations = activeInspirations + deletedInspirations
        allInspirations.forEach { localInspirationMap[it.id] = it }
        val inspirations = allInspirations.map { e ->
            SyncEnvelope(
                id = e.id, type = "inspiration", revision = e.revision,
                deviceId = e.device_id ?: device.deviceId, updatedAt = e.updated_at,
                deletedAt = e.deleted_at, payload = backfillInspirationPayload(e),
            )
        }
        val activeProgress = readingProgressDao.observeAllActive().first()
        val deletedProgress = readingProgressDao.getDeleted()
        val allProgress = activeProgress + deletedProgress
        allProgress.forEach { localProgressMap[it.book_id] = it }
        val progress = allProgress.map { e ->
            SyncEnvelope(
                id = e.book_id, type = "progress", revision = e.revision,
                deviceId = e.device_id ?: device.deviceId, updatedAt = e.updated_at,
                deletedAt = e.deleted_at, payload = toJsonObject(e.payload),
            )
        }
        val activeSessions = readingSessionDao.observeAllActive().first()
        val deletedSessions = readingSessionDao.getDeleted()
        val allSessions = activeSessions + deletedSessions
        allSessions.forEach { localSessionMap[it.id] = it }
        val sessions = allSessions.map { e ->
            SyncEnvelope(
                id = e.id, type = "session", revision = e.revision,
                deviceId = e.device_id ?: device.deviceId, updatedAt = e.updated_at,
                deletedAt = e.deleted_at, payload = toJsonObject(e.payload),
            )
        }

        val payload = SyncContract.SyncPushPayload(
            device = device,
            inspirations = inspirations,
            books = books,
            progress = progress,
            sessions = sessions,
        )
        val result = api.push(payload)
        configStore.markSyncedAt(Instant.now().toString())

        val conflicts = result.conflicts.map { env ->
            val localUpdatedAt = when (env.type) {
                "book" -> localBookMap[env.id]?.updated_at ?: ""
                "inspiration" -> localInspirationMap[env.id]?.updated_at ?: ""
                "progress" -> localProgressMap[env.id]?.updated_at ?: ""
                "session" -> localSessionMap[env.id]?.updated_at ?: ""
                else -> ""
            }
            SyncConflictEntry(
                type = env.type,
                id = env.id,
                title = when (env.type) {
                    "book" -> str(env.payload, "title")
                    "inspiration" -> str(env.payload, "title")
                    else -> null
                },
                remoteUpdatedAt = env.updatedAt,
                localUpdatedAt = localUpdatedAt,
                resolution = "服务端保留",
            )
        }

        PushResult(
            applied = result.applied,
            conflicts = conflicts,
            failedItems = failures,
        )
    }

    /** 解析本地 payload 列；为空或非法 JSON 时回退为空对象。 */
    private fun toJsonObject(s: String?): JsonObject {
        if (s.isNullOrBlank()) return buildJsonObject { }
        return runCatching { json.parseToJsonElement(s).jsonObject }
            .getOrElse { buildJsonObject { } }
    }

    /** 检查 payload 是否为空或空对象，若是则从实体提升列回填。 */
    private fun backfillBookPayload(e: BookEntity): JsonObject {
        val parsed = toJsonObject(e.payload)
        if (parsed.isNotEmpty()) return parsed
        return buildJsonObject {
            put("title", e.title)
            e.author?.let { put("author", it) }
            put("format", e.format)
            e.content_hash?.let { put("contentHash", it) }
        }
    }

    /** 检查 payload 是否为空或空对象，若是则从实体提升列回填。 */
    private fun backfillInspirationPayload(e: InspirationEntity): JsonObject {
        val parsed = toJsonObject(e.payload)
        if (parsed.isNotEmpty()) return parsed
        return buildJsonObject {
            put("title", e.title)
            put("body", e.body)
            put("type", e.type)
            put("status", e.status)
            e.source_book_id?.let { put("sourceBookId", it) }
        }
    }

    // ---- 书籍正文下载 ----
    suspend fun downloadBookContent(bookId: String): Result<Unit> = withContext(ioDispatcher) {
        // 先标记 downloading，让 UI 能即时反映
        runCatching {
            val b = bookDao.getById(bookId) ?: return@runCatching
            if (b.content_status == "downloading") return@runCatching
            bookDao.upsert(b.copy(content_status = "downloading", updated_at = Instant.now().toString()))
        }
        runCatching {
            val api = apiProvider.current() ?: error("尚未配对，无法下载")
            val resp = api.getBookFile(bookId)
            if (!resp.isSuccessful) error("下载失败：HTTP ${resp.code()}")
            val body = resp.body() ?: error("响应体为空")
            val book = bookDao.getById(bookId) ?: error("书籍不存在：$bookId")
            val written = writeValidatedBookFile(book.id, book.format, fileName = null, body)
            persistDownloadedContent(book, written, Instant.now().toString())
        }.onFailure { e ->
            // 下载失败标记 failed
            runCatching {
                val b = bookDao.getById(bookId) ?: return@runCatching
                bookDao.upsert(b.copy(content_status = "failed", updated_at = Instant.now().toString()))
            }
            throw e
        }
    }

    data class DownloadBooksResult(
        val success: Int,
        val failed: Int,
        val failedItems: List<SyncFailedEntry> = emptyList(),
    )

    /** 同步后自动下载未下载的书籍正文（最多 maxCount 本，单线程顺序下载）。 */
    suspend fun downloadPendingBooks(maxCount: Int = 20): DownloadBooksResult = withContext(ioDispatcher) {
        val api = apiProvider.current() ?: return@withContext DownloadBooksResult(0, 0)
        val manifest = runCatching { api.manifest().bookFiles.associateBy { it.bookId } }.getOrDefault(emptyMap())
        val pendingBooks = bookDao.observeAllActive().first()
            .filter { book ->
                book.content_status != "available" &&
                    book.local_content_path.isNullOrBlank() &&
                    book.local_uri.isNullOrBlank() &&
                    manifest.containsKey(book.id)
            }
            .take(maxCount)

        var success = 0
        val failures = mutableListOf<SyncFailedEntry>()

        for (book in pendingBooks) {
            // 标记 downloading
            bookDao.upsert(book.copy(content_status = "downloading", updated_at = Instant.now().toString()))
            val r = runCatching {
                val resp = api.getBookFile(book.id)
                if (!resp.isSuccessful) error("HTTP ${resp.code()}")
                val body = resp.body() ?: error("响应体为空")
                val written = writeValidatedBookFile(book.id, book.format, manifest[book.id]?.fileName, body)
                persistDownloadedContent(book, written, Instant.now().toString())
            }
            if (r.isSuccess) {
                success++
            } else {
                // 标记 failed
                runCatching { bookDao.upsert(book.copy(content_status = "failed", updated_at = Instant.now().toString())) }
                failures.add(SyncFailedEntry("book_file", book.id, book.title, r.exceptionOrNull()?.message ?: "未知错误"))
            }
        }
        DownloadBooksResult(success, failures.size, failures)
    }

    /** 获取桌面端清单（轻量探查，用于"从电脑下载"列表）。 */
    suspend fun listDesktopBooks(): List<SyncContract.BookFileManifest> = withContext(ioDispatcher) {
        runCatching {
            val api = apiProvider.current() ?: return@withContext emptyList()
            api.manifest().bookFiles
        }.getOrDefault(emptyList())
    }

    private data class DownloadedContent(val file: File, val format: String)

    /**
     * 把下载正文按「格式真源」落到 content.{真实格式}，导入与同步共用的 [FormatClassifier]
     * 是同一套规则，禁止在下载路径上分叉。
     *
     * 只读前 4 字节做判定，绝不读完整文件；声称 epub 但正文非 ZIP，或无法识别格式时，
     * 在创建任何临时文件之前就抛错，因此不会遗留 content.epub 之类错误扩展名的最终文件。
     */
    private fun writeValidatedBookFile(
        bookId: String,
        claimedFormat: String,
        fileName: String?,
        body: ResponseBody,
    ): DownloadedContent {
        val dir = File(context.cacheDir, "books/$bookId").apply { mkdirs() }
        var result: DownloadedContent? = null
        body.use { b ->
            b.byteStream().use { input ->
                val magic = ByteArray(FormatClassifier.EPUB_MAGIC.size)
                var off = 0
                while (off < magic.size) {
                    val n = input.read(magic, off, magic.size - off)
                    if (n < 0) break
                    off += n
                }
                val firstBytes = if (off == magic.size) magic else magic.copyOf(off)
                val format = when (val verdict = FormatClassifier.classify(claimedFormat, fileName, firstBytes)) {
                    is FormatClassifier.Verdict.Accepted -> verdict.format
                    is FormatClassifier.Verdict.Rejected -> error(verdict.reason)
                }
                val file = File(dir, "content.$format")
                // 先写 .tmp 再 rename 原子落盘：中途中断不会留下半截坏文件。
                val tmp = File(dir, file.name + ".tmp")
                try {
                    FileOutputStream(tmp).use { output ->
                        output.write(firstBytes)
                        input.copyTo(output)
                    }
                } catch (e: Throwable) {
                    tmp.delete()
                    throw e
                }
                if (!tmp.renameTo(file)) {
                    tmp.delete()
                    error("写入磁盘失败：重命名目标文件失败")
                }
                result = DownloadedContent(file, format)
            }
        }
        return result ?: error("下载内容为空")
    }

    /**
     * 校验并原子落盘成功后，闭环回写书籍与文件元数据（单本与批量共用，禁止分叉）。
     *
     * - BookEntity.format / local_uri / local_content_path 与实际写入文件一致，保证
     *   [com.creationreadingassistant.ui.viewmodel.ReaderDocumentLoader] 按真实格式打开；
     * - BookEntity.payload 的 format 更新为真实格式，保留其余未知字段；
     * - BookFileEntity 的 format / 文件名 / local_uri 与实际写入文件一致。
     */
    private suspend fun persistDownloadedContent(
        book: BookEntity,
        written: DownloadedContent,
        now: String,
    ) {
        val localUri = fileUri(written.file)
        val size = written.file.length().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        bookFileDao.upsert(
            BookFileEntity(
                book_id = book.id,
                file_name = written.file.name,
                format = written.format,
                size = size,
                local_uri = localUri,
                updated_at = now,
            ),
        )
        bookDao.upsert(
            book.copy(
                format = written.format,
                size = size,
                local_uri = localUri,
                local_content_path = written.file.absolutePath,
                content_status = "available",
                updated_at = now,
                payload = withPayloadFormat(book.payload, written.format),
            ),
        )
    }

    /** 将 payload 的 format 字段更新为真实格式，保留 payload 中其他未知字段。 */
    private fun withPayloadFormat(payload: String?, format: String): String =
        buildJsonObject {
            toJsonObject(payload).forEach { (k, v) -> put(k, v) }
            put("format", format)
        }.toString()
}
