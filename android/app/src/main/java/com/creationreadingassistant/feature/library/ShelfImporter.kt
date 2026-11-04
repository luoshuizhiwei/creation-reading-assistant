package com.creationreadingassistant.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.LibrarySourceAvailability
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinator
import com.creationreadingassistant.data.settings.ImportHistoryEntry
import com.creationreadingassistant.data.settings.ImportHistoryStore
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.PlainTextDecoder
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.ui.viewmodel.ImportBatchUiState
import com.creationreadingassistant.ui.viewmodel.ImportFailureUi
import com.creationreadingassistant.ui.viewmodel.ImportTaskUi
import com.creationreadingassistant.ui.viewmodel.markStopRequested
import com.creationreadingassistant.ui.viewmodel.remainingImportCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * 「按已记录来源刷新正文」的结果（M-BUG-03 第二阶段）。
 *
 * 与 [ShelfImporter.repairFile] 返回的裸 String 不同，这里必须给出**可靠的成败信号**：
 * 调用方要靠它决定是否撤掉书架上的「可更新」标签，而按消息文本前缀嗅探成败过于脆弱。
 */
sealed interface SourceRefreshResult {
    /** 刷新成功，[message] 可直接展示给用户。 */
    data class Success(val message: String) : SourceRefreshResult

    /**
     * 没有可用于刷新的来源引用：从未记录来源、引用缺 `document_id`、或无法构造文档 URI。
     * 此时只能退回「重新定位文件」由用户重选一次。
     */
    data object NoSourceRef : SourceRefreshResult

    /** 有来源引用但执行失败：持久授权失效、文件已删除、格式不符、写入异常等。 */
    data class Failed(val message: String) : SourceRefreshResult
}

/**
 * 某一批导入的成功结果，[batchId] 与 [bookIds] **绑在一起原子发布**（S05-④ 复核补强）。
 *
 * 为什么不只暴露一个 `List<String>`：UI 在「导入完成提示」上点「查看」时，必须拿到
 * **与那条提示同一个批次**的成功 id。若批次 id 与 id 列表分两次写、或 UI 侧读的是
 * Compose 异步收集到的最近一帧，就可能出现「提示还是旧批次、id 已是新批次」的错配，
 * 于是用户点旧提示却被带去新批次的书。两者放进同一个不可变对象、一次赋值发布，
 * 调用方再同步读 `.value`，这个错配就不可能发生。
 */
data class ImportBatchSuccesses(
    /** 批次 id；空串表示还没有跑过任何批次。 */
    val batchId: String = "",
    /** 该批成功新增的 bookId，按导入顺序、已去重。 */
    val bookIds: List<String> = emptyList(),
)

/**
 * 书架导入管线 —— 真实 SAF 导入（EPUB/TXT/MD）、文件夹扫描、修复缺失正文，
 * 以及导入任务队列 / 批量进度 / 导入历史的状态管理。
 *
 * 纯逻辑类：不持有协程作用域，所有入口均为 suspend 函数，由调用方（ViewModel）
 * 负责 launch 调度；因此可以在 JVM 测试中用 runTest 直接驱动任务队列状态机，
 * 而不需要任何 Dispatcher / scope 样板。
 */
class ShelfImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BookRepository,
    private val bookDao: BookDao,
    private val bookContentDao: BookContentDao,
    private val bookFileDao: BookFileDao,
    private val epubRepository: EpubRepository,
    private val importHistoryStore: ImportHistoryStore,
    private val sourceIndex: LibrarySourceIndex,
    private val rootStore: LibraryRootStore,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val indexRefreshCoordinator: SearchIndexBookRefreshCoordinator,
) {


    /**
     * 当前书架上的书籍列表（用于重复导入检测），由 ViewModel 在使用前注入最新值来源。
     * 不放构造参数是为了让本类可被 Hilt 直接注入（未加 scope，每个 ViewModel 仍是新实例）。
     */
    var booksProvider: () -> List<BookEntity> = { emptyList() }

    /**
     * 每次**成功导入一本新书**后回调其 bookId，由 ViewModel 记录为「新导入未查看」。
     *
     * 只在 [ImportOutcome.Success] 时触发：重复、跳过、失败都不算新书，
     * 否则「已存在，未重复导入」的书也会挂上「新」角标。
     * 与 [booksProvider] 同样用 var 而非构造参数，保持本类可被 Hilt 直接注入。
     */
    var onBookImported: (String) -> Unit = {}

    private val _importTasks = MutableStateFlow<List<ImportTaskUi>>(emptyList())
    val importTasks: StateFlow<List<ImportTaskUi>> = _importTasks.asStateFlow()

    private val _importBatch = MutableStateFlow(ImportBatchUiState())
    val importBatch: StateFlow<ImportBatchUiState> = _importBatch.asStateFlow()

    private val _batchSuccesses = MutableStateFlow(ImportBatchSuccesses())

    /**
     * 最近一批导入的成功 id，**与批次 id 一起原子发布**；新批次开始时整体重置。
     *
     * 为什么不能复用 `ShelfPrefs.newBookIds`（S05-④）：后者是**跨批次的持久「未查看」集合**，
     * 只有用户真正打开过那本书才会移除。导入完成点「查看」时若取它的 `firstOrNull()`，
     * 拿到的可能是**上一批**留下且从未打开的书 —— 于是「查看」滚到了旧书上。
     *
     * 调用方（`ShelfRoute`）应在**点按那一刻同步读** `batchSuccesses.value` 并核对
     * `batchId`，不要依赖 Compose 异步收集到的最近一帧：那不能保证与「提示对应的批次
     * 结果」原子同步。见 [ImportBatchSuccesses] 的说明。
     *
     * 口径与 [ImportBatchUiState.succeeded] 一致：重复 / 跳过 / 失败都不计入，
     * 且与同一成功分支里自增 `succeeded`，两者不会分叉。
     */
    val batchSuccesses: StateFlow<ImportBatchSuccesses> = _batchSuccesses.asStateFlow()

    val importHistory: StateFlow<List<ImportHistoryEntry>> = importHistoryStore.entries

    private var stopImportAfterCurrent = false

    /** 导入文件选择器返回的多本书（统一顺序批处理，避免同时解析多本大书抢占内存）。 */
    suspend fun importFiles(uris: List<Uri>, sourceLabel: String = "所选文件") {
        val uniqueUris = uris.distinctBy(Uri::toString)
        if (uniqueUris.isEmpty() || _importBatch.value.isRunning) return
        stopImportAfterCurrent = false
        runImportBatch(
            uris = uniqueUris,
            sourceLabel = sourceLabel,
            initialSkipped = uris.size - uniqueUris.size,
        )
    }

    /** 扫描用户明确授权的 SAF 文件夹，再顺序导入支持的书籍文件。 */
    suspend fun importFolder(treeUri: Uri) {
        if (_importBatch.value.isRunning) return
        stopImportAfterCurrent = false
        val batchId = "batch-${UUID.randomUUID()}"
        _importBatch.value = ImportBatchUiState(
            id = batchId,
            sourceLabel = "所选文件夹",
            isScanning = true,
            isRunning = true,
        )
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val scanResult = runCatching {
            SafBookSourceScanner(context.contentResolver).scan(treeUri)
        }.getOrElse { error ->
            _importBatch.value = _importBatch.value.copy(
                isScanning = false,
                isRunning = false,
                failed = 1,
                completed = 1,
                total = 1,
                failures = listOf(
                    ImportFailureUi(
                        uri = treeUri.toString(),
                        fileName = "所选文件夹",
                        reason = error.message ?: "无法读取文件夹",
                    )
                ),
            )
            return
        }
        runImportBatch(
            uris = scanResult.bookUris,
            sourceLabel = "所选文件夹",
            initialSkipped = scanResult.skippedFiles,
            unreadableFolders = scanResult.unreadableFolders,
            truncated = scanResult.truncated,
            batchId = batchId,
        )
    }

    /** 重试上一批的失败项（共享同一批进度展示）。 */
    suspend fun retryFailedImports() {
        val failures = _importBatch.value.failures
        if (failures.isEmpty()) return
        importFiles(failures.map { Uri.parse(it.uri) }, sourceLabel = "重试失败项")
    }

    fun dismissImportBatchSummary() {
        if (!_importBatch.value.isRunning) _importBatch.value = ImportBatchUiState()
    }

    /** 标记书架的一次性完成提示已消费，同时保留导入页需要展示的批次结果。 */
    fun markImportBatchSummaryNotified(batchId: String) {
        val current = _importBatch.value
        if (
            current.id == batchId &&
            !current.isRunning &&
            current.hasResult &&
            !current.summaryNotified
        ) {
            _importBatch.value = current.copy(summaryNotified = true)
        }
    }

    /** 安全停止：不取消当前解析/写库，只在当前文件完成后停止剩余队列。 */
    fun requestStopImport() {
        if (!_importBatch.value.isRunning) return
        stopImportAfterCurrent = true
        _importBatch.value = _importBatch.value.markStopRequested()
    }

    suspend fun clearImportHistory() {
        importHistoryStore.clear()
    }

    /**
     * 重新选择文件（修复缺失正文），返回给用户的结果消息。
     *
     * 保留裸 String 契约：既有调用点（书架长按菜单等）只把消息直接展示给用户，
     * 不需要成败信号。需要类型化结果的场景请用 [refreshFromSource]。
     */
    suspend fun repairFile(bookId: String, uri: Uri): String = repairFileTyped(bookId, uri).message

    /**
     * 按 `library_source_refs` 已记录的来源引用直接刷新正文，**不经系统文件选择器**
     * （M-BUG-03 第二阶段）。
     *
     * 解决的痛点：源文件在系统目录被改过之后，此前必须「长按书籍 → 重新定位文件」
     * 手动把同一个文件再选一遍；本方法用已存的 `authority + document_id` 重建文档 URI
     * （授权口径见 [sourceDocumentUriOf]），让「刷新」变成一次点击。
     *
     * 两条必须同时满足的约束：
     * 1. **成败判定要可靠** —— 复用 [repairFileTyped] 的类型化结果，不嗅探消息文本；
     * 2. **成功后必须重写来源基线** —— `library_source_refs` 的 `size` / `last_modified`
     *    是「内容有更新」的比较基线，而修复路径本身**不写**它。不重写则下次进书架
     *    [ShelfSourceUpdateChecker] 仍按旧基线报「可更新」，标签永远清不掉。
     *
     * 注意：本方法会**替换正文事实源**（内部稳定副本），因此章节索引与阅读坐标可能
     * 随新内容变化而错位 —— 这是「刷新」的固有代价，由 UI 侧在调用前向用户说明。
     */
    suspend fun refreshFromSource(bookId: String): SourceRefreshResult {
        val ref = try {
            sourceIndex.snapshot().firstOrNull { it.bookId == bookId }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return SourceRefreshResult.NoSourceRef
        val root = try {
            rootStore.loadRoot()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val rootDocumentId = root?.treeUri?.let {
            runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull()
        }
        val uri = sourceDocumentUriOf(ref, root?.treeUri, rootDocumentId)
            ?: return SourceRefreshResult.NoSourceRef
        val outcome = repairFileTyped(bookId, uri)
        if (!outcome.ok) return SourceRefreshResult.Failed(outcome.message)
        refreshSourceBaseline(ref, uri)
        // 正文替换成功后，异步重建该书的搜索索引（S04-2）。
        // 不等待重建完成，不把索引失败改变本次刷新结果：
        // 失败由 SearchIndexBookRefreshCoordinator 写入 AppLog 并静默吞掉。
        indexRefreshCoordinator.request(bookId)
        return SourceRefreshResult.Success(outcome.message)
    }

    /**
     * [repairFile] 与 [refreshFromSource] 的共同实现 —— 修复路径的唯一入口。
     *
     * 内部用 [RepairOutcome] 传递类型化成败，对外的裸 String 契约由 [repairFile] 负责。
     */
    private suspend fun repairFileTyped(bookId: String, uri: Uri): RepairOutcome {
        val existing = withContext(ioDispatcher) { bookDao.getById(bookId) }
        if (existing == null) return RepairOutcome(false, "书籍记录不存在")
        val taskId = "repair-${UUID.randomUUID()}"
        _importTasks.value = (_importTasks.value + ImportTaskUi(taskId, "修复中", "开始修复", "processing"))
            .sortedByDescending { it.createdAt }
            .take(50)
        return try {
            // uriFileName / uriSize / 解析 / 读文件 全部属于 I/O，必须切到 IO 线程
            val resolved = withContext(ioDispatcher) {
                val fileName = uriFileName(uri)
                val format = normalizedFormat(fileName)
                val expectedFormat = normalizedFormat(existing.original_file_name ?: "book.${existing.format}")
                if (format != expectedFormat) {
                    throw IllegalArgumentException(
                        "格式不一致：原书为 ${expectedFormat.uppercase()}，所选为 ${format.uppercase()}"
                    )
                }
                val fileSize = uriSize(uri)
                when (format) {
                    "epub" -> reselectEpub(existing, uri, taskId)
                    "txt", "md" -> reselectPlainText(existing, uri, taskId, format)
                    else -> throw IllegalArgumentException("不支持的格式：$format")
                }
                ResolvedImport(existing, format, fileSize, fileName)
            }
            _importTasks.value = _importTasks.value.map {
                if (it.id == taskId) it.copy(phase = "修复完成", status = "done", fileName = resolved.fileName) else it
            }
            recordImport(resolved.fileName, resolved.format, resolved.fileSize, "success", bookTitle = resolved.book.title)
            RepairOutcome(true, "《${existing.title}》正文已修复")
        } catch (e: CancellationException) {
            // 取消不是失败：写入「修复失败」历史会制造假记录，且吞掉取消会破坏结构化并发
            // （调用方的 scope 已经结束，这里再落库属于越权写入）。原样抛出，任务状态交给取消方清理。
            throw e
        } catch (e: Throwable) {
            val msg = e.message ?: "修复失败"
            _importTasks.value = _importTasks.value.map {
                if (it.id == taskId) it.copy(phase = "修复失败：$msg", status = "error") else it
            }
            recordImport("unknown", "unknown", 0, "failed", bookTitle = existing.title, error = msg)
            RepairOutcome(false, "修复失败：$msg")
        }
    }

    /**
     * 刷新成功后就地推进来源基线（M-BUG-03 第二阶段，S04-3 / S04-4 修正）。
     *
     * 一行基线必须描述**同一次观测**，因此三个字段的回退口径各自明确：
     * 1. `size` / `last_modified`（S04-4）：**逐字段**防护 —— provider 该字段观测不到就保留
     *    旧观测值，绝不编造；provider 整体不可读（`querySourceMetadata` 返回 null）时整行不写。
     * 2. `content_hash`（S04-3）：只允许三种取值 —— 大文件策略性 null、本次测得的新哈希、
     *    小文件测不到时的「未知 null」。**不沿用旧哈希**：旧值对应的是刷新前的内容，
     *    与新 size 同行即是自相矛盾的配对（详见下方分支注释与 `ShelfImporterTest` 两条用例）。
     * 3. `candidate_fingerprint`：仅 >32 MiB 才计算，口径与 `LibraryBrowserPolicy` 的
     *    SAME_CONTENT 判定一致。
     */
    private suspend fun refreshSourceBaseline(ref: LibrarySourceRef, uri: Uri) {
        val observed = withContext(ioDispatcher) { querySourceMetadata(context.contentResolver, uri) }
            ?: return
        val newSize = observed.first ?: ref.sizeBytes
        val newLastModified = observed.second ?: ref.lastModifiedMillis

        val internalFile = findInternalFile(ref.bookId)
        val isLarge = SourceFingerprint.isLargeFile(newSize)
        val newFingerprint = if (isLarge) {
            internalFile?.let { computeCandidateFingerprint(it) }
                ?: SourceFingerprint.compute(context.contentResolver, uri, newSize)
        } else {
            null
        }
        val newContentHash = if (isLarge) {
            // ① 大文件（>32 MiB）：**既定策略**不落全量哈希。null 表示「不适用」，不是失败。
            null
        } else {
            // ② 小文件且本次**确实测得**：内部副本优先，失败再按来源 uri 重算。
            // ③ 小文件但两处都测不到：**写 null 表示未知**。
            //    绝不沿用 `ref.contentHash` —— 那是**上一个版本**的指纹，而同一行的 `size`
            //    已被推进到本次观测值，「新 size + 旧哈希」是自相矛盾的配对，等于用旧内容的
            //    哈希冒充新内容。未知就如实记为未知。
            //    代价可接受：`library_source_refs.content_hash` 目前无任何读取方（去重读的是
            //    `books.content_hash`，SAME_CONTENT 读的是 `candidate_fingerprint`），
            //    而 books 侧的新哈希由 reselectPlainText 独立落库，不受此处影响。
            internalFile?.let { runCatching { computeFileHash(it) }.getOrNull() }
                ?: contentHashOrNull(uri, newSize.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
        }

        try {
            sourceIndex.record(
                LibrarySourceRefEntity(
                    book_id = ref.bookId,
                    root_id = ref.rootId,
                    provider_authority = ref.providerAuthority,
                    document_id = ref.documentId,
                    display_name = ref.displayName,
                    format = ref.format,
                    size = newSize,
                    last_modified = newLastModified,
                    content_hash = newContentHash,
                    candidate_fingerprint = newFingerprint,
                    last_seen_at = System.currentTimeMillis(),
                    availability = LibrarySourceAvailability.AVAILABLE.storageValue,
                )
            )
        } catch (e: CancellationException) {
            // record 是挂起调用：不能用 runCatching 包（会吞掉取消，使取消失效）。
            throw e
        } catch (_: Exception) {
            // best-effort：基线写入失败只意味着「可更新」标签会继续显示（用户可再点一次），
            // 不影响本次正文刷新的结果。
        }
    }

    /**
     * 查找已成功落盘的内部正文副本文件（S04-3）。
     */
    private suspend fun findInternalFile(bookId: String): java.io.File? = withContext(ioDispatcher) {
        runCatching {
            val book = bookDao.getById(bookId)
            val path = book?.local_content_path
            if (path != null) {
                val file = java.io.File(path)
                if (file.exists() && file.isFile) return@withContext file
            }
            val dir = java.io.File(context.filesDir, "books/$bookId")
            if (dir.exists() && dir.isDirectory) {
                val candidate = dir.listFiles()?.firstOrNull { it.isFile && !it.name.endsWith(".repairing") && !it.name.endsWith(".backup") }
                if (candidate != null) return@withContext candidate
            }
            null
        }.getOrNull()
    }

    /**
     * 计算内部副本文件的全量 MD5（S04-3）。
     *
     * 语义与 [EpubRepository.computeFileHash] 对齐（两处阈值必须一致）：
     * - 大文件（`len > MAX_HASH_BYTES`）：**既定策略**，返回 `null` 表示「不计算全量哈希」；
     * - 小文件（`len <= MAX_HASH_BYTES`）：必须给出真实哈希。空文件或读取失败**抛出**，
     *   绝不以 `null` 冒充「大文件策略值」—— 那会把一次 I/O 故障静默写成
     *   「这本书没有内容哈希」，并让旧哈希被悄悄清除、调用方仍报成功。
     */
    internal fun computeFileHash(file: java.io.File): String? {
        val len = file.length()
        if (len > MAX_HASH_BYTES) return null
        require(len > 0L) { "内部正文副本为空，无法计算内容哈希" }
        val md = java.security.MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算超大内部副本文件的「大小 + 首尾分块」候选指纹（S04-3）。
     */
    private fun computeCandidateFingerprint(file: java.io.File): String? {
        val size = file.length()
        if (!SourceFingerprint.isLargeFile(size)) return null
        return runCatching {
            val headMd5 = file.inputStream().use { input ->
                val buf = ByteArray(SourceFingerprint.CHUNK_BYTES)
                var read = 0
                while (read < SourceFingerprint.CHUNK_BYTES) {
                    val n = input.read(buf, read, SourceFingerprint.CHUNK_BYTES - read)
                    if (n < 0) return@use null
                    read += n
                }
                SourceFingerprint.md5Hex(buf)
            } ?: return null
            val tailMd5 = file.inputStream().use { input ->
                var remaining = size - SourceFingerprint.CHUNK_BYTES
                while (remaining > 0) {
                    val skipped = input.skip(remaining)
                    if (skipped > 0) {
                        remaining -= skipped
                    } else {
                        if (input.read() < 0) return@use null
                        remaining -= 1
                    }
                }
                val buf = ByteArray(SourceFingerprint.CHUNK_BYTES)
                var read = 0
                while (read < SourceFingerprint.CHUNK_BYTES) {
                    val n = input.read(buf, read, SourceFingerprint.CHUNK_BYTES - read)
                    if (n < 0) return@use null
                    read += n
                }
                SourceFingerprint.md5Hex(buf)
            } ?: return null
            SourceFingerprint.fromChunks(size, headMd5, tailMd5)
        }.getOrNull()
    }

    private suspend fun runImportBatch(
        uris: List<Uri>,
        sourceLabel: String,
        initialSkipped: Int = 0,
        unreadableFolders: Int = 0,
        truncated: Boolean = false,
        batchId: String = "batch-${UUID.randomUUID()}",
    ) {
        var state = ImportBatchUiState(
            id = batchId,
            sourceLabel = sourceLabel,
            total = uris.size + initialSkipped,
            completed = initialSkipped,
            skipped = initialSkipped,
            unreadableFolders = unreadableFolders,
            truncated = truncated,
            isRunning = true,
        )
        _importBatch.value = state
        // 本批候选整体重置，并把批次 id 一起写进去：上一批的成功 id 不得泄漏进这一批的
        // 「查看」定位，也不能出现「批次 id 已是新的、id 列表还是旧的」这种半截状态（S05-④）。
        val succeededBookIds = mutableListOf<String>()
        _batchSuccesses.value = ImportBatchSuccesses(batchId = batchId)
        var lastBatchEmitMs = System.currentTimeMillis()

        fun emitProgress(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (force || now - lastBatchEmitMs >= BATCH_EMIT_THROTTLE_MS) {
                lastBatchEmitMs = now
                _importBatch.value = state
            }
        }

        val batchFingerprints = mutableSetOf<String>()
        // 每批只读一次根目录配置，用于判定导入来源是否落在授权树内（判定不了就留空，不伪造）。
        val configuredRoot = withContext(ioDispatcher) {
            runCatching { rootStore.loadRoot() }.getOrNull()
        }
        for ((index, uri) in uris.withIndex()) {
            if (stopImportAfterCurrent) {
                state = state.copy(
                    stopped = state.stopped + remainingImportCount(uris.size, index, currentCompleted = false),
                    stopRequested = true,
                )
                emitProgress(force = true)
                break
            }
            val outcome = importOne(uri, batchFingerprints, configuredRoot)
            val isFailure = outcome is ImportOutcome.Failed
            state = when (outcome) {
                is ImportOutcome.Success -> {
                    // 与 succeeded 计数同一分支，保证「本批成功 id」计数与列表不分叉。
                    succeededBookIds += outcome.bookId
                    _batchSuccesses.value =
                        ImportBatchSuccesses(batchId = batchId, bookIds = succeededBookIds.toList())
                    state.copy(
                        completed = state.completed + 1,
                        succeeded = state.succeeded + 1,
                    )
                }
                is ImportOutcome.Duplicate -> state.copy(
                    completed = state.completed + 1,
                    duplicates = state.duplicates + 1,
                )
                is ImportOutcome.Skipped -> state.copy(
                    completed = state.completed + 1,
                    skipped = state.skipped + 1,
                )
                is ImportOutcome.Failed -> state.copy(
                    completed = state.completed + 1,
                    failed = state.failed + 1,
                    failures = state.failures + ImportFailureUi(
                        uri = uri.toString(),
                        fileName = outcome.fileName,
                        reason = outcome.reason,
                    ),
                )
            }
            val isLast = index == uris.lastIndex
            emitProgress(force = isFailure || isLast)

            if (stopImportAfterCurrent) {
                state = state.copy(
                    stopped = state.stopped + remainingImportCount(uris.size, index, currentCompleted = true),
                    stopRequested = true,
                )
                emitProgress(force = true)
                break
            }
        }
        _importBatch.value = state.copy(isRunning = false, isScanning = false)
        stopImportAfterCurrent = false
    }

    private suspend fun importOne(
        uri: Uri,
        batchFingerprints: MutableSet<String>,
        configuredRoot: LibraryRoot?,
    ): ImportOutcome {
        val quickName = uri.lastPathSegment?.substringAfterLast('/') ?: "unknown"
        val taskId = "imp-${UUID.randomUUID()}"
        val newTask = ImportTaskUi(taskId, quickName, IMPORT_PHASES[0], "processing")
        val currentTasks = _importTasks.value
        _importTasks.value = ArrayList<ImportTaskUi>(50).apply {
            add(newTask)
            addAll(currentTasks.take(49))
        }
        return try {
            val metadata = withContext(ioDispatcher) {
                val fileName = uriFileName(uri)
                val size = uriSize(uri)
                val verdict = FormatClassifier.classify(
                    claimedFormat = null,
                    fileName = fileName,
                    firstBytes = readMagic(uri),
                )
                // 超大文件（>32 MiB）不算全量哈希，改用「大小 + 首尾分块」候选指纹参与去重。
                val fingerprint = if (SourceFingerprint.isLargeFile(size.toLong())) {
                    SourceFingerprint.compute(context.contentResolver, uri, size.toLong())
                } else {
                    null
                }
                ImportCandidate(
                    uri = uri,
                    fileName = fileName,
                    fileSize = size,
                    contentHash = contentHashOrNull(uri, size),
                    fingerprint = fingerprint,
                    lastModified = uriLastModified(uri),
                    verdict = verdict,
                )
            }
            val format = when (val verdict = metadata.verdict) {
                is FormatClassifier.Verdict.Accepted -> verdict.format
                is FormatClassifier.Verdict.Rejected -> return rejectImport(metadata, verdict, taskId)
            }

            val fingerprint = metadata.fingerprint(format)
            // 候选指纹去重：同指纹的来源引用仍挂在书架在册书籍上时视为重复。
            // 指纹只用于去重提示，不参与任何内容覆盖；删除书架项会级联清掉引用，
            // 因此不会阻止用户重新导入已被移除的书。
            val fingerprintDuplicate = metadata.fingerprint?.let { candidateFingerprint ->
                sourceIndex.getByFingerprint(candidateFingerprint).any { ref ->
                    booksProvider().any { book -> book.id == ref.bookId }
                }
            } == true
            val duplicate = fingerprintDuplicate || fingerprint in batchFingerprints || booksProvider().any { book ->
                book.local_uri == uri.toString() ||
                    (
                        metadata.fileSize > 0 &&
                            book.format.lowercase() == format &&
                            book.original_file_name.equals(metadata.fileName, ignoreCase = true) &&
                            book.size == metadata.fileSize
                        ) ||
                    // 内容哈希：改名 / 移动后的同内容文件也能识别（仅对已落哈希的书生效）
                    (
                        metadata.contentHash != null &&
                            book.content_hash != null &&
                            book.content_hash == metadata.contentHash
                        )
            }
            if (duplicate) {
                batchFingerprints += fingerprint
                metadata.contentHash?.let { batchFingerprints += "hash|$it" }
                updateTaskResult(taskId, metadata.fileName, "已存在，未重复导入", "duplicate")
                recordImport(
                    metadata.fileName,
                    format,
                    metadata.fileSize,
                    status = "duplicate",
                    isDuplicate = true,
                )
                return ImportOutcome.Duplicate
            }

            val resolved = withContext(ioDispatcher) {
                val book = when (format) {
                    "epub" -> importEpub(uri, taskId)
                    "txt", "md" -> importPlainText(uri, taskId, format)
                    else -> error("不支持的格式：$format")
                }
                ResolvedImport(book, format, metadata.fileSize, metadata.fileName)
            }
            batchFingerprints += fingerprint
            metadata.contentHash?.let { batchFingerprints += "hash|$it" }
            // 首次导入落内容哈希（不动 updated_at，避免扰动同步修订号）；best-effort，失败不阻断导入
            metadata.contentHash?.let { hash ->
                runCatching {
                    bookDao.getById(resolved.book.id)?.let { row ->
                        if (row.content_hash.isNullOrBlank()) {
                            bookDao.update(row.copy(content_hash = hash))
                        }
                    }
                }
            }
            updateTaskResult(taskId, resolved.fileName, "导入完成", "done")
            recordImport(
                resolved.fileName,
                resolved.format,
                resolved.fileSize,
                "success",
                bookTitle = resolved.book.title,
            )
            recordSourceRef(uri, configuredRoot, metadata, format, resolved)
            onBookImported(resolved.book.id)
            ImportOutcome.Success(resolved.book.id)
        } catch (e: Throwable) {
            val msg = e.message ?: "导入失败"
            updateTaskResult(taskId, quickName, "导入失败：$msg", "error")
            recordImport(quickName, "unknown", 0, "failed", error = msg)
            ImportOutcome.Failed(quickName, msg)
        }
    }

    private fun updateTaskResult(taskId: String, fileName: String, phase: String, status: String) {
        _importTasks.value = _importTasks.value.map {
            if (it.id == taskId) it.copy(fileName = fileName, phase = phase, status = status) else it
        }
    }

    private fun updateTask(taskId: String, phase: String) {
        _importTasks.value = _importTasks.value.map {
            if (it.id == taskId) it.copy(phase = phase) else it
        }
    }

    /** 写入一条导入历史（持久化到 ImportHistoryStore）。 */
    private suspend fun recordImport(
        fileName: String,
        format: String,
        fileSize: Int,
        status: String,
        bookTitle: String? = null,
        error: String? = null,
        isDuplicate: Boolean = false,
    ) {
        importHistoryStore.addEntry(
            ImportHistoryEntry(
                id = "imp-h-${UUID.randomUUID()}",
                fileName = fileName,
                fileSize = fileSize,
                format = format,
                encoding = if (format in listOf("txt", "md", "markdown")) "UTF-8" else null,
                isDuplicate = isDuplicate,
                bookTitle = bookTitle,
                error = error,
                status = status,
            )
        )
    }

    /**
     * 导入成功后记录来源引用（路线 §4.5）。best-effort：来源索引不是阅读事实源，
     * 写入失败只损失一次分层判定基线，绝不让刚完成的导入报错。
     *
     * rootId 只在能证明来源位于当前授权树内时写入（[sourceRootIdFor]）；系统选择器
     * 选出的任意位置文件 root_id 为 null，绝不伪造归属。
     */
    private suspend fun recordSourceRef(
        uri: Uri,
        configuredRoot: LibraryRoot?,
        metadata: ImportCandidate,
        format: String,
        resolved: ResolvedImport,
    ) {
        runCatching {
            val key = sourceKeyOf(uri)
            sourceIndex.record(
                LibrarySourceRefEntity(
                    book_id = resolved.book.id,
                    root_id = sourceRootIdFor(uri, configuredRoot),
                    provider_authority = key.authority.orEmpty(),
                    document_id = key.documentId,
                    display_name = metadata.fileName,
                    format = format,
                    size = metadata.fileSize.toLong(),
                    last_modified = metadata.lastModified,
                    content_hash = metadata.contentHash,
                    candidate_fingerprint = metadata.fingerprint,
                    last_seen_at = System.currentTimeMillis(),
                    availability = LibrarySourceAvailability.AVAILABLE.storageValue,
                )
            )
        }
    }

    /**
     * 分类器拒绝：无法识别归为「跳过」（沿用旧的不支持格式语义），
     * 声称 EPUB 但正文非 ZIP 归为「失败」（格式错配，绝不进入 EPUB 解析）。
     */
    private suspend fun rejectImport(
        metadata: ImportCandidate,
        verdict: FormatClassifier.Verdict.Rejected,
        taskId: String,
    ): ImportOutcome {
        return if (verdict.claimedFormat == null) {
            val reason = "不支持的格式"
            updateTaskResult(taskId, metadata.fileName, "已跳过：$reason", "skipped")
            recordImport(metadata.fileName, "unknown", metadata.fileSize, "skipped", error = reason)
            ImportOutcome.Skipped
        } else {
            val reason = verdict.reason
            updateTaskResult(taskId, metadata.fileName, "导入失败：$reason", "error")
            recordImport(metadata.fileName, verdict.claimedFormat, metadata.fileSize, "failed", error = reason)
            ImportOutcome.Failed(metadata.fileName, reason)
        }
    }

    private suspend fun importEpub(uri: Uri, taskId: String): BookEntity {
        updateTask(taskId, "正在解析 EPUB 结构")
        val book = epubRepository.openEpub(uri)
        val fileName = uriFileName(uri)
        val size = uriSize(uri)
        val now = Instant.now().toString()
        updateTask(taskId, "正在保存书架记录")
        bookDao.getById(book.id)?.let { existing ->
            bookDao.update(
                existing.copy(
                    original_file_name = fileName,
                    size = size,
                    imported_at = now,
                    updated_at = now,
                )
            )
        }
        bookFileDao.upsert(
            BookFileEntity(
                book_id = book.id,
                file_name = fileName,
                format = "epub",
                size = size,
                local_uri = uri.toString(),
                updated_at = now,
            )
        )
        // 重新导入同一本书时，新的 SAF Uri 需要重新获取持久化读取权限。
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        // EPUB 内嵌封面：仅当书架记录还没有封面时提取（用户手动设置的封面优先，不覆盖）。
        val stored = bookDao.getById(book.id)
        if (stored != null && stored.cover_data_url.isNullOrBlank() && !book.coverEntryPath.isNullOrBlank()) {
            runCatching { extractEpubCoverDataUrl(book) }.getOrNull()?.let { dataUrl ->
                bookDao.update(stored.copy(cover_data_url = dataUrl, updated_at = now))
            }
        }
        return bookDao.getById(book.id) ?: BookEntity(
            id = book.id,
            title = book.title,
            author = book.author,
            format = "epub",
            original_file_name = fileName,
            size = size,
            local_uri = uri.toString(),
            content_status = "available",
            imported_at = now,
            updated_at = now,
        )
    }

    private suspend fun importPlainText(uri: Uri, taskId: String, format: String): BookEntity {
        updateTask(taskId, "正在读取文件内容")
        val fileName = uriFileName(uri)
        // 无论文件大小都保存应用自有副本。只持有 SAF Uri 会在权限撤销、文件移动或
        // 文件提供器状态变化后让书架里的书无法再打开。
        val maxInlineBytes = 10 * 1024 * 1024
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        updateTask(taskId, "正在保存本地副本")
        val internalDir = java.io.File(context.filesDir, "books/$id").also { it.mkdirs() }
        val internalFile = java.io.File(internalDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            internalFile.outputStream().use { out -> input.copyTo(out) }
        } ?: throw IllegalStateException("无法打开文件")
        val size = internalFile.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (size <= 0) {
            internalFile.delete()
            throw IllegalStateException("文件内容为空")
        }
        val storedUri = Uri.fromFile(internalFile).toString()
        val text = if (size <= maxInlineBytes) {
            PlainTextDecoder.decode(internalFile.readBytes()).text
        } else {
            // 大文件只生成有界预览，正文由阅读器按索引流式读取。
            val index = TxtFileScanner.scan(internalFile)
            PlainTextDocument.fromFileIndex(internalFile, index).readWindow(0, 20_000)
        }
        updateTask(taskId, "正在保存书架记录")
        val book = BookEntity(
            id = id,
            title = fileName.substringBeforeLast('.').ifBlank { "未命名书籍" },
            author = null,
            format = format,
            original_file_name = fileName,
            size = size,
            local_uri = storedUri,
            local_content_path = internalFile.absolutePath,
            content_status = "available",
            imported_at = now,
            updated_at = now,
        )
        bookDao.upsert(book)
        bookContentDao.upsert(BookContentEntity(book_id = id, reader_preview = text.take(20000)))
        bookFileDao.upsert(
            BookFileEntity(
                book_id = id,
                file_name = fileName,
                format = format,
                size = size,
                local_uri = storedUri,
                updated_at = now,
            )
        )
        // 持久化 SAF Uri 读取权限（大文件时已复制到内部存储，此处仅对原始 Uri 授权）。
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        return book
    }

    private suspend fun reselectEpub(existing: BookEntity, uri: Uri, taskId: String) {
        updateTask(taskId, "正在解析 EPUB 结构")
        val fileName = uriFileName(uri)
        updateTask(taskId, "正在安全替换本地副本")
        val parsed = epubRepository.replaceStoredEpub(existing, uri, fileName)
        val size = java.io.File(parsed.cachedEpubPath).length()
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
        val now = Instant.now().toString()
        bookFileDao.upsert(
            BookFileEntity(
                book_id = existing.id,
                file_name = fileName,
                format = "epub",
                size = size,
                local_uri = Uri.fromFile(java.io.File(parsed.cachedEpubPath)).toString(),
                updated_at = now,
            )
        )
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private suspend fun reselectPlainText(existing: BookEntity, uri: Uri, taskId: String, format: String) {
        updateTask(taskId, "正在读取文件内容")
        val fileName = uriFileName(uri)
        val maxInlineBytes = 10 * 1024 * 1024
        val now = Instant.now().toString()
        val internalDir = java.io.File(context.filesDir, "books/${existing.id}").also { it.mkdirs() }
        val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val internalFile = java.io.File(internalDir, safeName)
        val staging = java.io.File(internalDir, "$safeName.repairing")
        val backup = java.io.File(internalDir, "$safeName.backup")
        staging.delete()
        // 崩溃残留守卫（第一道，最小范围）：同名 backup 已存在时**不得**删除、覆盖或自动恢复。
        //
        // 它的来源无法在本轮判定：
        //   · 可能是上次进程被杀后**唯一完好的**正文（旧文件已挪进 backup、新文件尚未落位）；
        //   · 也可能是上次已提交成功的**过期**副本（只差最后一步清理）。
        // 仅凭 target 是否存在或长度**无法区分**这两种情况，因此一律停止本次替换并保留现场：
        // 不删 backup、不动 target、不写 DB，让用户/后续工具去判断。
        // 自动恢复状态机属独立设计（见 2026-09-24 设计稿），**不在本轮实现**。
        if (backup.exists()) {
            // 提示语必须对**两种现场都成立**：target 存在（旧正文仍在）与 target 缺失
            // （旧正文已挪进 backup）。故不能断言「原有文件已保留」—— target 缺失时并没有
            // 这样一份文件，那样写会让用户误以为旧正文还在原处。
            // 也不能暗示存在自助恢复入口，或引导用户删书/清备份（那正是销毁唯一副本的做法）。
            throw IllegalStateException(
                "检测到未完成的替换残留（${backup.name}），本次替换已停止；" +
                    "旧正文若仍存在，则未被本次操作修改，残留备份已保留。" +
                    "重复重试不会自动恢复该备份",
            )
        }
        // 阶段标记：catch 必须区分「旧副本根本没被动过」与「旧副本已挪走 / 新副本已落位」。
        // 前者若执行 internalFile.delete()，会把用户**仅存**的正文删掉，而那时并没有 backup
        // 可还原 —— 一次复制失败就等于永久丢正文（旧实现无条件 delete 的真实缺陷）。
        var oldCopyMovedToBackup = false
        var newCopyPromoted = false
        try {
            updateTask(taskId, "正在复制到应用存储")
            context.contentResolver.openInputStream(uri)?.use { input ->
                staging.outputStream().use { out -> input.copyTo(out) }
            } ?: throw IllegalStateException("无法打开文件")
            if (staging.length() <= 0L) throw IllegalStateException("文件内容为空")
            if (internalFile.exists()) {
                if (!internalFile.renameTo(backup)) {
                    internalFile.copyTo(backup, overwrite = true)
                    internalFile.delete()
                }
                oldCopyMovedToBackup = true
            }
            if (!staging.renameTo(internalFile)) {
                staging.copyTo(internalFile, overwrite = true)
                staging.delete()
            }
            newCopyPromoted = true
            val size = internalFile.length()
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
            val text = if (size <= maxInlineBytes) {
                PlainTextDecoder.decode(internalFile.readBytes()).text
            } else {
                val index = TxtFileScanner.scan(internalFile)
                PlainTextDocument.fromFileIndex(internalFile, index).readWindow(0, 20_000)
            }
            updateTask(taskId, "正在保存书架记录")
            val storedUri = Uri.fromFile(internalFile).toString()
            // S04 修复：刷新后同步计算新文件的 content_hash，写入 books 表。
            // 与 refreshSourceBaseline 采用相同的 MAX_HASH_BYTES 阈值：
            //   - 小文件（≤32MB）：计算全量 MD5，与 library_source_refs.content_hash 同口径。
            //   - 大文件（>32MB）：置为 null，旧哈希必须清除（旧哈希对应旧内容，不能保留）。
            val newBookHash = computeFileHash(internalFile)
            bookDao.runInTransaction {
                bookDao.upsert(
                    existing.copy(
                        original_file_name = fileName,
                        size = size,
                        local_uri = storedUri,
                        local_content_path = internalFile.absolutePath,
                        content_status = "available",
                        content_hash = newBookHash,
                        updated_at = now,
                    )
                )
                bookContentDao.upsert(
                    BookContentEntity(
                        book_id = existing.id,
                        reader_preview = text.take(20_000),
                    )
                )
                bookFileDao.upsert(
                    BookFileEntity(
                        book_id = existing.id,
                        file_name = fileName,
                        format = format,
                        size = size,
                        local_uri = storedUri,
                        updated_at = now,
                    )
                )
            }
            backup.delete()
        } catch (error: Throwable) {
            // 回滚必须按「走到哪一步」分流，而不是无条件删 internalFile：
            //  - 旧副本未被挪动（复制 staging 阶段就失败）：internalFile 就是用户仅存的正文，
            //    原样保留即可；此时不存在 backup，删了它没有任何东西能还原。
            //  - 旧副本已挪进 backup：把 backup 还原回 internalFile；新副本若已落位，先删掉它。
            //  - 原本没有旧副本（首次替换）：只清理本次落位的新副本，不留半截状态。
            if (oldCopyMovedToBackup) {
                if (newCopyPromoted) internalFile.delete()
                if (backup.exists()) {
                    if (!backup.renameTo(internalFile)) {
                        backup.copyTo(internalFile, overwrite = true)
                        backup.delete()
                    }
                }
            } else if (newCopyPromoted) {
                internalFile.delete()
            }
            throw error
        } finally {
            staging.delete()
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun normalizedFormat(fileName: String): String {
        return when (val format = fileName.substringAfterLast('.', "").lowercase()) {
            "markdown" -> "md"
            else -> format
        }
    }

    private fun uriFileName(uri: Uri): String {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
        }
        return name ?: uri.lastPathSegment ?: "unknown"
    }

    /**
     * 全量内容 MD5：识别改名/移动后的同内容文件。书籍普遍 ≤32MB，顺序读一遍的
     * 成本相对随后的解析/复制可忽略；超限（如超大 TXT）返回 null 回退弱指纹。
     * 必须在 IO 线程调用。
     */
    private fun contentHashOrNull(uri: Uri, size: Int): String? {
        if (size <= 0 || size > MAX_HASH_BYTES) return null
        return runCatching {
            val md = java.security.MessageDigest.getInstance("MD5")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            } ?: return null
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private fun uriSize(uri: Uri): Int {
        var size = 0
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst()) size = cursor.getLong(idx).toInt()
        }
        return size
    }

    /**
     * 来源文件的最后修改时间（epoch millis），用于「内容有更新」的比较基线。
     * provider 不提供该列（如部分网盘）或值为 0 时返回 null。
     */
    private fun uriLastModified(uri: Uri): Long? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex("last_modified")
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) {
                    cursor.getLong(idx).takeIf { it > 0 }
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    /**
     * 读取文件前 4 字节用于格式判定，绝不读完整文件。必须在 IO 线程调用。
     * 无法打开（例如权限失效或测试桩未提供流）时返回空数组 —— 空数组不会被
     * [FormatClassifier.isEpubZip] 判定为 EPUB，对 epub 声明即视为格式错配拒绝。
     */
    private fun readMagic(uri: Uri): ByteArray {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(FormatClassifier.EPUB_MAGIC.size)
                var off = 0
                while (off < buf.size) {
                    val n = input.read(buf, off, buf.size - off)
                    if (n < 0) break
                    off += n
                }
                buf.copyOf(off)
            } ?: ByteArray(0)
        }.getOrDefault(ByteArray(0))
    }

    /**
     * 提取 EPUB 内嵌封面为 JPEG data URL（压缩实现内聚在 [com.creationreadingassistant.feature.reader.EpubParser.loadCoverDataUrl]，
     * 与封面补扫共用同一口径）。必须在 IO 线程调用。
     */
    private fun extractEpubCoverDataUrl(book: com.creationreadingassistant.domain.model.EpubBook): String? {
        val entryPath = book.coverEntryPath ?: return null
        return com.creationreadingassistant.feature.reader.EpubParser
            .loadCoverDataUrl(book.cachedEpubPath, entryPath, COVER_TARGET_EDGE_PX)
    }

    private data class ResolvedImport(
        val book: BookEntity,
        val format: String,
        val fileSize: Int,
        val fileName: String,
    )

    /** 修复路径的内部类型化结果；[message] 与 [repairFile] 的裸 String 契约逐字一致。 */
    private data class RepairOutcome(val ok: Boolean, val message: String)

    private data class ImportCandidate(
        val uri: Uri,
        val fileName: String,
        val fileSize: Int,
        /** 全量内容 MD5（≤32MB 时计算；超限为 null，查重回退文件名+大小/候选指纹）。 */
        val contentHash: String? = null,
        /** 超大文件的「大小 + 首尾分块哈希」候选指纹；只用于去重，不是安全签名。 */
        val fingerprint: String? = null,
        /** 来源文件导入时的最后修改时间，作为「内容有更新」的比较基线。 */
        val lastModified: Long? = null,
        val verdict: FormatClassifier.Verdict,
    ) {
        fun fingerprint(format: String): String =
            "${format.lowercase()}|${fileName.lowercase()}|$fileSize"
    }

    private sealed interface ImportOutcome {
        /** [bookId] 是本次成功新增的书，供「本批成功 id」接缝定位用（S05-④）。 */
        data class Success(val bookId: String) : ImportOutcome
        data object Duplicate : ImportOutcome
        data object Skipped : ImportOutcome
        data class Failed(val fileName: String, val reason: String) : ImportOutcome
    }

    private companion object {
        /** 批量导入向 StateFlow 发射进度的最小间隔（毫秒），防连续重组风暴。 */
        const val BATCH_EMIT_THROTTLE_MS = 80L

        /** 封面降采样目标最长边（px）：书架封面卡片足够清晰的最小尺寸。 */
        const val COVER_TARGET_EDGE_PX = 600

        /** 内容哈希的文件大小上限（字节）：超限不计算，避免超大文件双倍 IO。 */
        const val MAX_HASH_BYTES = 32 * 1024 * 1024

        val IMPORT_PHASES = listOf(
            "正在校验文件",
            "正在复制到应用",
            "正在验证 EPUB 结构",
            "正在保存书架记录",
            "导入完成",
        )
    }
}
