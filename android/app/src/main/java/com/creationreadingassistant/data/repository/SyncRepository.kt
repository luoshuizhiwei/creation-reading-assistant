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
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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
 * 拉取时把信封与本地行**按字段合并**落库（R1）：远端 payload 显式给值的合同字段
 * 覆盖本地，缺键/显式 null 保留本地值（新记录落实体默认值）；本机事实列
 * （local_uri / local_content_path / content_status / size / cover_data_url /
 * current_location_json 等）不在 payload 合同内，接受远端时原样保留。
 * 推送时把本地实体 payload 解析回信封发往服务端；payload 列为空的本地记录
 * （本机阅读流不写 payload）按 wire 合同从提升列回填，使对端能学到本机事实。
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
    suspend fun pull(options: SyncOptions = SyncOptions()): PullResult = withContext(ioDispatcher) {
        val api = apiProvider.current() ?: error("尚未配对，无法同步")
        val device = deviceInfoProvider.provide()
        val resp = api.pull(SyncContract.SyncPullPayloadRequest(device))
        val failures = mutableListOf<SyncFailedEntry>()
        val strategy = options.conflictStrategy
        var bookCount = 0
        var inspirationCount = 0
        var progressCount = 0
        var sessionCount = 0

        // 逐条 apply 包在单个事务里：要么整批落库，要么整体回滚，不留半同步状态。
        // 关闭的同步项直接跳过对应类别（不 apply、不计数），因此子页的「书架 / 进度 / 灵感」
        // 开关真正门控落库，而不是摆着能点却不生效。
        bookDao.runInTransaction {
            if (options.syncBooks) {
                resp.books.forEach { env ->
                    runCatching { applyBook(env, strategy) }
                        .onSuccess { bookCount++ }
                        .onFailure { failures.add(SyncFailedEntry("book", env.id, env.payload["title"]?.toString(), it.message ?: "未知错误")) }
                }
            }
            if (options.syncInspirations) {
                resp.inspirations.forEach { env ->
                    runCatching { applyInspiration(env, strategy) }
                        .onSuccess { inspirationCount++ }
                        .onFailure { failures.add(SyncFailedEntry("inspiration", env.id, env.payload["title"]?.toString(), it.message ?: "未知错误")) }
                }
            }
            // 「阅读进度与阅读历史」同属一个开关：progress 与 sessions 一起门控。
            if (options.syncProgress) {
                resp.progress.forEach { env ->
                    runCatching { applyProgress(env, strategy) }
                        .onSuccess { progressCount++ }
                        .onFailure { failures.add(SyncFailedEntry("progress", env.id, null, it.message ?: "未知错误")) }
                }
                resp.sessions.forEach { env ->
                    runCatching { applySession(env, strategy) }
                        .onSuccess { sessionCount++ }
                        .onFailure { failures.add(SyncFailedEntry("session", env.id, null, it.message ?: "未知错误")) }
                }
            }
        }
        configStore.markSyncedAt(Instant.now().toString())
        PullResult(bookCount, inspirationCount, progressCount, sessionCount, failures)
    }

    private fun str(obj: JsonObject, key: String): String? =
        obj[key]?.jsonPrimitive?.content

    private fun jsonStr(payload: JsonObject): String = payload.toString()

    /**
     * R1：显式取值辅助——键存在、**类型正确**且值合法才算「远端显式给值」，否则
     * 一律按「未给出」处理（保留本地值或实体默认值），绝不把非法输入转成字面量
     * （既有 [str] 对显式 JSON null 会产生字面量 "null"、对数字/布尔会把值转成
     * 其文本表示、对对象/数组直接抛错）：
     * - 字符串键要求 `isString`：显式 null、数字、布尔、对象/数组都不构成字符串值；
     * - 数字键要求非字符串且可解析；
     * - 浮点数额外要求有限（NaN/Infinity 等非有限值按未给出，绝不让它们流进
     *   进度/时长列）。
     * 显式 null 与缺键同途：任何对端实现都可能显式发 null，把它解释为「清除本机
     * 数据」没有合同依据，保守保留本地才是唯一不丢数据的选择。
     */
    private fun optString(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun optLong(obj: JsonObject, key: String): Long? =
        (obj[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun optFloat(obj: JsonObject, key: String): Float? =
        (obj[key] as? JsonPrimitive)?.takeIf { !it.isString }
            ?.contentOrNull?.toFloatOrNull()?.takeIf { it.isFinite() }

    /** ISO 时间字符串 → epoch millis；不可解析（合同外输入）返回 null。 */
    private fun epochMillisOrNull(iso: String?): Long? =
        iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    private suspend fun applyBook(env: SyncEnvelope<JsonObject>, strategy: ConflictStrategy?) {
        val local = bookDao.getById(env.id)
        if (local != null && !shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at, strategy)) return
        val p = env.payload
        // R1：新旧记录统一走同一条合并路径与同一套安全解析（optString 对显式 null /
        // 数字 / 布尔 / 对象一律按「未给出」处理，新记录不得落字面量 "null"，已有
        // 记录不得被 123/true 这类非法类型改写）。接受远端新版 = 远端合同字段
        // （title/author/format/contentHash）覆盖 + 本机事实列原样保留：
        // local_uri / local_content_path / content_status / size / cover_data_url /
        // original_file_name / imported_at / description 不在 LAN 同步 payload 合同内
        // （桌面 LibraryBook.filePath/coverPath 是对端设备自己的路径，不得落成
        // "本机路径"），copy 自 base 天然保留。软删除仍以信封为准（墓碑传播）。
        val base = local ?: BookEntity(
            id = env.id,
            title = "(未命名)",
            format = "txt",
            payload = jsonStr(p),
            revision = env.revision,
            device_id = env.deviceId,
            updated_at = env.updatedAt,
            deleted_at = env.deletedAt,
        )
        bookDao.upsert(
            base.copy(
                title = optString(p, "title") ?: base.title,
                author = optString(p, "author") ?: base.author,
                format = optString(p, "format") ?: base.format,
                content_hash = optString(p, "contentHash") ?: base.content_hash,
                payload = jsonStr(p),
                revision = env.revision,
                device_id = env.deviceId,
                updated_at = env.updatedAt,
                deleted_at = env.deletedAt,
            ),
        )
    }

    private suspend fun applyInspiration(env: SyncEnvelope<JsonObject>, strategy: ConflictStrategy?) {
        val local = inspirationDao.getById(env.id)
        if (local != null && !shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at, strategy)) return
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

    private suspend fun applyProgress(env: SyncEnvelope<JsonObject>, strategy: ConflictStrategy?) {
        val local = readingProgressDao.getByBook(env.id)
        if (local != null && !shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at, strategy)) return
        val p = env.payload
        val base = local ?: ReadingProgressEntity(
            book_id = env.id,
            payload = jsonStr(p),
            revision = env.revision,
            device_id = env.deviceId,
            updated_at = env.updatedAt,
            deleted_at = env.deletedAt,
        )
        // R1：提升列逐字段合并。远端 payload 显式给值才消费（缺键/显式 null 保留
        // 本地，新记录落到实体默认值）；显式 0 是合法值。依据：
        // - `progressPercent` 在 wire 上是桌面 0..1 尺度（electron normalizeProgressItem
        //   的 clamp01 + 0.995 完成阈值），落库换算为原生 0..100；
        // - `completionState` 是桌面词汇（unread/reading/completed），经
        //   ReadingCompletionState.fromStorage 映射（completed→finished 别名已被
        //   既有代码承认；unread 落到 reading；shelved 是 Android 词汇，fromStorage
        //   原样识别，容错另一 Android 经中转发来的本机词汇）；
        // - `completedAt` 是 ISO 字符串（桌面 ReadingProgress.completedAt: ISODateString），
        //   原生列是 epoch millis，解析失败按未给出处理；
        // - `currentLocation` 与原生 current_location_json（locator_v2/offset 模式，
        //   LocatorBuilder.progressJson 产出）不是同一 JSON 合同，跨格式换算属于
        //   猜测——本机定位保留，远端对象留在 payload 真相源里。
        // 完成态/完成时间一致：completed_at 以**最终合并后的状态**为准——只有
        // finished 才允许存在完成时间（对齐原生 writer 不变式）。状态缺键不推断
        // 完成（本地 reading/shelved + 远端只带 completedAt 不得形成非完成+完成
        // 时间）；100% 不自动标为 finished（S05-①：显式状态权威，percent 不反推）；
        // 显式完成态采纳可解析完成时间，否则保留本机值，不伪造时间戳。
        val explicitPercent = optFloat(p, "progressPercent")?.let { (it * 100f).coerceIn(0f, 100f) }
        val explicitState = optString(p, "completionState")?.let { ReadingCompletionState.fromStorage(it).storageValue }
        val explicitCompletedAt = epochMillisOrNull(optString(p, "completedAt"))
        val finalState = explicitState ?: base.completion_state
        val finalCompletedAt = when (finalState) {
            ReadingCompletionState.FINISHED.storageValue -> explicitCompletedAt ?: base.completed_at
            else -> null
        }
        readingProgressDao.upsert(
            base.copy(
                progress_percent = explicitPercent ?: base.progress_percent,
                last_read_at = optString(p, "lastReadAt") ?: base.last_read_at,
                total_reading_time_ms = optLong(p, "totalReadingTimeMs")?.coerceAtLeast(0L) ?: base.total_reading_time_ms,
                completion_state = finalState,
                completed_at = finalCompletedAt,
                payload = jsonStr(p),
                revision = env.revision,
                device_id = env.deviceId,
                updated_at = env.updatedAt,
                deleted_at = env.deletedAt,
            ),
        )
    }

    private suspend fun applySession(env: SyncEnvelope<JsonObject>, strategy: ConflictStrategy?) {
        val local = readingSessionDao.getById(env.id)
        if (local != null && !shouldAcceptRemote(env.revision, local.revision, env.updatedAt, local.updated_at, strategy)) return
        val p = env.payload
        val base = local ?: ReadingSessionEntity(
            id = env.id,
            book_id = "",
            payload = jsonStr(p),
            revision = env.revision,
            device_id = env.deviceId,
            created_at = env.updatedAt,
            updated_at = env.updatedAt,
            deleted_at = env.deletedAt,
        )
        // R1：提升列逐字段合并（同 applyProgress 的缺键/显式 null 保留本地规则）。
        // 时间词汇：wire 主用桌面 startAt/endAt；同时容错接受 Android 记录器词汇
        // startedAt/endedAt（ReadingSessionPayload；桌面 normalizeSessionItem 亦接受
        // startedAt 回退）。`durationMs` 两侧同为总时长毫秒（桌面 = active + idle）。
        // session 的 `progressPercent` 唯一生产者是 Android 记录器（0..100 尺度，
        // ReadingSessionRecorder coerceIn(0f,100f)）；桌面 ReadingSession 合同无此键。
        readingSessionDao.upsert(
            base.copy(
                book_id = optString(p, "bookId") ?: base.book_id,
                started_at = optString(p, "startAt") ?: optString(p, "startedAt") ?: base.started_at,
                ended_at = optString(p, "endAt") ?: optString(p, "endedAt") ?: base.ended_at,
                duration_ms = optLong(p, "durationMs")?.coerceAtLeast(0L) ?: base.duration_ms,
                progress_percent = optFloat(p, "progressPercent")?.coerceIn(0f, 100f) ?: base.progress_percent,
                created_at = optString(p, "createdAt") ?: base.created_at,
                payload = jsonStr(p),
                revision = env.revision,
                device_id = env.deviceId,
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
        strategy: ConflictStrategy? = null,
    ): Boolean = when (strategy) {
        null -> SyncMergePolicy.shouldAcceptRemote(remoteRevision, localRevision, remoteUpdatedAt, localUpdatedAt)
        ConflictStrategy.REMOTE_WINS -> remoteRevision >= localRevision
        ConflictStrategy.LOCAL_WINS -> remoteRevision > localRevision
    }

    // ---- 推送：本地库 → 服务端 ----
    suspend fun push(options: SyncOptions = SyncOptions()): PushResult = withContext(ioDispatcher) {
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
        // R1：payload 整理可能无法形成合法记录（filePath 无来源）——此时跳过该条
        // 并记入 failedItems（可检查原因），不发注定被对端丢弃的信封，也不伪造路径。
        val books = mutableListOf<SyncEnvelope<JsonObject>>()
        (if (options.syncBooks) allBooks else emptyList()).forEach { e ->
            val payload = backfillBookPayload(e, e.device_id ?: device.deviceId)
            if (payload == null) {
                failures += SyncFailedEntry("book", e.id, e.title, skipReasonMissingFilePath)
            } else {
                books += SyncEnvelope(
                    id = e.id, type = "book", revision = e.revision,
                    deviceId = e.device_id ?: device.deviceId, updatedAt = e.updated_at,
                    deletedAt = e.deleted_at, payload = payload,
                )
            }
        }
        val activeInspirations = inspirationDao.observeAllActive().first()
        val deletedInspirations = inspirationDao.getDeleted()
        val allInspirations = activeInspirations + deletedInspirations
        allInspirations.forEach { localInspirationMap[it.id] = it }
        val inspirations = (if (options.syncInspirations) allInspirations else emptyList()).map { e ->
            SyncEnvelope(
                id = e.id, type = "inspiration", revision = e.revision,
                deviceId = e.device_id ?: device.deviceId, updatedAt = e.updated_at,
                deletedAt = e.deleted_at, payload = backfillInspirationPayload(e, e.device_id ?: device.deviceId),
            )
        }
        val activeProgress = readingProgressDao.observeAllActive().first()
        val deletedProgress = readingProgressDao.getDeleted()
        val allProgress = activeProgress + deletedProgress
        allProgress.forEach { localProgressMap[it.book_id] = it }
        val progress = mutableListOf<SyncEnvelope<JsonObject>>()
        (if (options.syncProgress) allProgress else emptyList()).forEach { e ->
            val payload = backfillProgressPayload(e, e.device_id ?: device.deviceId, localBookMap[e.book_id])
            if (payload == null) {
                failures += SyncFailedEntry("progress", e.book_id, null, skipReasonMissingFilePath)
            } else {
                progress += SyncEnvelope(
                    id = e.book_id, type = "progress", revision = e.revision,
                    deviceId = e.device_id ?: device.deviceId, updatedAt = e.updated_at,
                    deletedAt = e.deleted_at, payload = payload,
                )
            }
        }
        val activeSessions = readingSessionDao.observeAllActive().first()
        val deletedSessions = readingSessionDao.getDeleted()
        val allSessions = activeSessions + deletedSessions
        allSessions.forEach { localSessionMap[it.id] = it }
        val sessions = mutableListOf<SyncEnvelope<JsonObject>>()
        (if (options.syncProgress) allSessions else emptyList()).forEach { e ->
            val payload = backfillSessionPayload(e, e.device_id ?: device.deviceId, localBookMap[e.book_id])
            if (payload == null) {
                failures += SyncFailedEntry("session", e.id, null, skipReasonMissingFilePath)
            } else {
                sessions += SyncEnvelope(
                    id = e.id, type = "session", revision = e.revision,
                    deviceId = e.device_id ?: device.deviceId, updatedAt = e.updated_at,
                    deletedAt = e.deleted_at, payload = payload,
                )
            }
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
                    "book" -> optString(env.payload, "title")
                    "inspiration" -> optString(env.payload, "title")
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

    /** 书籍合并时受管理键：一律取当前实体/信封值，旧 payload 中的同名键不保留。 */
    private val bookManagedKeys = setOf(
        "id", "filePath", "title", "author", "format", "contentHash", "importedAt",
        "revision", "deviceId", "updatedAt", "deletedAt",
    )

    /** 无法形成合法同步记录（桌面 normalize 硬性键 filePath 缺失）时的跳过原因。 */
    private val skipReasonMissingFilePath =
        "无法形成合法同步记录：payload 与本机书籍事实均未提供 filePath（桌面 normalize 要求非空），已跳过该条"

    /**
     * push 前把书籍行整理成 wire 合同 payload（R1 修订：**合并而非透传**）。
     * - 起点是已存 payload（保留全部未知字段）；受管理键一律取当前实体/信封值
     *   （桌面 normalizeLibraryBook 要求 `id`/`filePath`/`title` 非空，mergeByUpdatedAt
     *   读取 payload 内嵌的 revision/deviceId/updatedAt/deletedAt（withSyncMetadata），
     *   缺失时按桌面当前时间处理会破坏冲突序）；
     * - filePath：payload 已有非空值优先（对端自己的路径，不覆盖也不伪造），否则
     *   本机书籍事实；两者皆无且 payload 非空 → 返回 null，调用方跳过该条并给出
     *   可检查原因。payload 列为空的旧行为（从提升列整体构建、filePath 允许空串、
     *   墓碑照发——软删除传播尽力而为）保持不变。
     */
    private fun backfillBookPayload(e: BookEntity, deviceId: String): JsonObject? {
        val parsed = toJsonObject(e.payload)
        val filePath = optString(parsed, "filePath")?.takeIf { it.isNotBlank() }
            ?: e.local_content_path?.takeIf { it.isNotBlank() }
            ?: e.local_uri?.takeIf { it.isNotBlank() }
        if (parsed.isNotEmpty() && filePath == null) return null
        return buildJsonObject {
            parsed.forEach { (k, v) -> if (k !in bookManagedKeys) put(k, v) }
            put("id", e.id)
            put("filePath", filePath ?: "")
            put("title", e.title)
            e.author?.let { put("author", it) }
            put("format", e.format)
            e.imported_at?.let { put("importedAt", it) }
            e.content_hash?.let { put("contentHash", it) }
            put("revision", e.revision)
            put("deviceId", deviceId)
            put("updatedAt", e.updated_at)
            e.deleted_at?.let { put("deletedAt", it) }
        }
    }

    /** 进度合并时受管理键：一律取当前实体/信封值，旧 payload 中的同名键不保留。 */
    private val progressManagedKeys = setOf(
        "bookId", "filePath", "progressPercent", "completionState", "completedAt",
        "lastReadAt", "totalReadingTimeMs", "revision", "deviceId", "updatedAt", "deletedAt",
    )

    /**
     * push 前把进度行整理成 wire 合同 payload（R1 修订：**合并而非透传**）。
     * pull 会把远端非空 payload 原样存入 payload 列，本机阅读/取消完成只更新提升列
     * 而不改写 payload——透传会把旧进度/状态/时长与旧内嵌 metadata 发回。因此：
     * - 起点是已存 payload（保留全部未知字段，对端专有键不丢），受管理键一律取
     *   当前实体/信封值；
     * - `progressPercent` 用桌面 0..1 尺度（桌面 clamp01 会把 0..100 的 47.42 钳成
     *   1.0=100%），与 pull 侧 ×100 落库互为逆变换；`completionState` finished→completed，
     *   其余原样（shelved 由对端按其合同归一，不伪造 unread）；`completedAt` 输出
     *   ISO 字符串，为 null 时键不出现（受管理键排除已清掉旧值）；
     * - `currentLocation` 不伪造（桌面会按 progressPercent 构造默认定位）；
     * - filePath：payload 已有非空值优先（对端自己的路径，不覆盖也不伪造），否则
     *   本机书籍事实；两者皆无且 payload 非空 → 返回 null，调用方跳过该条并给出
     *   可检查原因（桌面 normalizeProgressItem 要求 filePath 非空，发注定被丢弃的
     *   记录没有意义）。payload 列为空的旧行为（从提升列整体构建、filePath 允许
     *   空串）保持不变。
     */
    private fun backfillProgressPayload(
        e: ReadingProgressEntity,
        deviceId: String,
        book: BookEntity?,
    ): JsonObject? {
        val parsed = toJsonObject(e.payload)
        val filePath = optString(parsed, "filePath")?.takeIf { it.isNotBlank() }
            ?: book?.local_content_path?.takeIf { it.isNotBlank() }
            ?: book?.local_uri?.takeIf { it.isNotBlank() }
        if (parsed.isNotEmpty() && filePath == null) return null
        return buildJsonObject {
            parsed.forEach { (k, v) -> if (k !in progressManagedKeys) put(k, v) }
            put("bookId", e.book_id)
            put("filePath", filePath ?: "")
            put("progressPercent", (e.progress_percent / 100f).coerceIn(0f, 1f))
            put(
                "completionState",
                if (e.completion_state == ReadingCompletionState.FINISHED.storageValue) "completed" else e.completion_state,
            )
            e.completed_at?.let { put("completedAt", Instant.ofEpochMilli(it).toString()) }
            e.last_read_at?.let { put("lastReadAt", it) }
            put("totalReadingTimeMs", e.total_reading_time_ms)
            put("revision", e.revision)
            put("deviceId", deviceId)
            put("updatedAt", e.updated_at)
            e.deleted_at?.let { put("deletedAt", it) }
        }
    }

    /** 会话合并时受管理键：一律取当前实体/信封值，旧 payload 中的同名键不保留。 */
    private val sessionManagedKeys = setOf(
        "id", "bookId", "filePath", "startedAt", "endedAt", "durationMs",
        "progressPercent", "createdAt", "revision", "deviceId", "updatedAt", "deletedAt",
    )

    /**
     * push 前把会话行整理成 wire 合同 payload（R1 修订：**合并而非透传**）。
     * - 起点是已存 payload（保留全部未知字段）；受管理键一律取当前实体/信封值。
     *   实际记录器形状（ReadingSessionPayload：startedAt/endedAt/durationMs/
     *   progressPercent/createdAt，0..100 进度尺度）缺桌面必需的 filePath——在同一
     *   序列化函数中按真实合同从本机书籍事实注入；桌面词汇键（startAt/endAt）若
     *   出现在旧 payload 中，同样刷新为当前事实，避免桌面 normalizeSessionItem 优先
     *   读到旧时间。session 的 `progressPercent` 桌面合同无此键（读取方为原生 pull）；
     * - filePath：payload 已有非空值优先（对端自己的路径，不覆盖也不伪造），否则
     *   本机书籍事实；两者皆无且 payload 非空 → 返回 null，调用方跳过该条并给出
     *   可检查原因。payload 列为空的旧行为（从提升列整体构建、filePath 允许空串）
     *   保持不变。
     */
    private fun backfillSessionPayload(
        e: ReadingSessionEntity,
        deviceId: String,
        book: BookEntity?,
    ): JsonObject? {
        val parsed = toJsonObject(e.payload)
        val filePath = optString(parsed, "filePath")?.takeIf { it.isNotBlank() }
            ?: book?.local_content_path?.takeIf { it.isNotBlank() }
            ?: book?.local_uri?.takeIf { it.isNotBlank() }
        if (parsed.isNotEmpty() && filePath == null) return null
        return buildJsonObject {
            parsed.forEach { (k, v) -> if (k !in sessionManagedKeys) put(k, v) }
            put("id", e.id)
            put("bookId", e.book_id)
            put("filePath", filePath ?: "")
            e.started_at?.let { put("startedAt", it) }
            e.ended_at?.let { put("endedAt", it) }
            put("durationMs", e.duration_ms)
            e.progress_percent?.let { put("progressPercent", it) }
            e.created_at?.let { put("createdAt", it) }
            if ("startAt" in parsed) e.started_at?.let { put("startAt", it) }
            if ("endAt" in parsed) e.ended_at?.let { put("endAt", it) }
            put("revision", e.revision)
            put("deviceId", deviceId)
            put("updatedAt", e.updated_at)
            e.deleted_at?.let { put("deletedAt", it) }
        }
    }

    /** 灵感合并时受管理键：一律取当前实体/信封值，旧 payload 中的同名键不保留。 */
    private val inspirationManagedKeys = setOf(
        "id", "title", "body", "type", "status", "sourceBookId",
        "createdAt", "revision", "deviceId", "updatedAt", "deletedAt",
    )

    /**
     * push 前把灵感行整理成 wire 合同 payload（R5 修订：**合并而非透传**）。
     *
     * 缺陷：真实客户端生产路径（`InspirationViewModel.saveInspiration`）写入的 payload 只含
     * tags/categoryIds/source/adoptions/excerpts/mergedInto（`InspirationPayloadData`，
     * encodeDefaults=true 恒非空）；标题/正文/type/status/sourceBookId 存在**实体提升列**。
     * 旧实现「payload 非空即原样返回」导致这些原生新素材的 wire payload 缺 id/title/body 与
     * 同步元信息；桌面消费者 `normalizeInspirationItem(envelope.payload)` 不从信封回填，
     * 缺 id 会 `makeId` 生成新 id、缺 title 记「未命名灵感」、缺 body 记空，无法按原 id/正文入库。
     *
     * 修复与 book/progress/session 三路径同源：
     * - 起点是已存 payload（**保留全部未知/扩展字段**，不整段替换）；
     * - 受管理键一律取当前实体/信封值 —— 实体编辑后的 title/body 等**胜过** payload 里的
     *   陈旧同名值（pull 落库的旧 payload 不覆盖新编辑）；
     * - 空 payload 的旧行为（从提升列整体构建）保持不变，是上面的退化情形；
     * - 不新增协议版本、不改接收端。
     */
    private fun backfillInspirationPayload(e: InspirationEntity, deviceId: String): JsonObject {
        val parsed = toJsonObject(e.payload)
        return buildJsonObject {
            parsed.forEach { (k, v) -> if (k !in inspirationManagedKeys) put(k, v) }
            put("id", e.id)
            put("title", e.title)
            put("body", e.body)
            put("type", e.type)
            put("status", e.status)
            e.source_book_id?.let { put("sourceBookId", it) }
            put("createdAt", e.created_at)
            put("revision", e.revision)
            put("deviceId", deviceId)
            put("updatedAt", e.updated_at)
            e.deleted_at?.let { put("deletedAt", it) }
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
