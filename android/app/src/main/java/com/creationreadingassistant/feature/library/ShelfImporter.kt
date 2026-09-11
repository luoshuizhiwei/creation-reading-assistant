package com.creationreadingassistant.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.repository.BookRepository
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
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * 当前书架上的书籍列表（用于重复导入检测），由 ViewModel 在使用前注入最新值来源。
     * 不放构造参数是为了让本类可被 Hilt 直接注入（未加 scope，每个 ViewModel 仍是新实例）。
     */
    var booksProvider: () -> List<BookEntity> = { emptyList() }

    private val _importTasks = MutableStateFlow<List<ImportTaskUi>>(emptyList())
    val importTasks: StateFlow<List<ImportTaskUi>> = _importTasks.asStateFlow()

    private val _importBatch = MutableStateFlow(ImportBatchUiState())
    val importBatch: StateFlow<ImportBatchUiState> = _importBatch.asStateFlow()

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

    /** 重新选择文件（修复缺失正文），返回给用户的结果消息。 */
    suspend fun repairFile(bookId: String, uri: Uri): String {
        val existing = withContext(ioDispatcher) { bookDao.getById(bookId) }
        if (existing == null) return "书籍记录不存在"
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
            "《${existing.title}》正文已修复"
        } catch (e: Throwable) {
            val msg = e.message ?: "修复失败"
            _importTasks.value = _importTasks.value.map {
                if (it.id == taskId) it.copy(phase = "修复失败：$msg", status = "error") else it
            }
            recordImport("unknown", "unknown", 0, "failed", bookTitle = existing.title, error = msg)
            "修复失败：$msg"
        }
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
        val batchFingerprints = mutableSetOf<String>()
        for ((index, uri) in uris.withIndex()) {
            if (stopImportAfterCurrent) {
                state = state.copy(
                    stopped = state.stopped + remainingImportCount(uris.size, index, currentCompleted = false),
                    stopRequested = true,
                )
                break
            }
            val outcome = importOne(uri, batchFingerprints)
            state = when (outcome) {
                is ImportOutcome.Success -> state.copy(
                    completed = state.completed + 1,
                    succeeded = state.succeeded + 1,
                )
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
            _importBatch.value = state
            if (stopImportAfterCurrent) {
                state = state.copy(
                    stopped = state.stopped + remainingImportCount(uris.size, index, currentCompleted = true),
                    stopRequested = true,
                )
                break
            }
        }
        _importBatch.value = state.copy(isRunning = false, isScanning = false)
        stopImportAfterCurrent = false
    }

    private suspend fun importOne(
        uri: Uri,
        batchFingerprints: MutableSet<String>,
    ): ImportOutcome {
        val quickName = uri.lastPathSegment?.substringAfterLast('/') ?: "unknown"
        val taskId = "imp-${UUID.randomUUID()}"
        _importTasks.value = (_importTasks.value + ImportTaskUi(taskId, quickName, IMPORT_PHASES[0], "processing"))
            .sortedByDescending { it.createdAt }
            .take(50)
        return try {
            val metadata = withContext(ioDispatcher) {
                val fileName = uriFileName(uri)
                val size = uriSize(uri)
                val verdict = FormatClassifier.classify(
                    claimedFormat = null,
                    fileName = fileName,
                    firstBytes = readMagic(uri),
                )
                ImportCandidate(uri, fileName, size, contentHashOrNull(uri, size), verdict)
            }
            val format = when (val verdict = metadata.verdict) {
                is FormatClassifier.Verdict.Accepted -> verdict.format
                is FormatClassifier.Verdict.Rejected -> return rejectImport(metadata, verdict, taskId)
            }

            val fingerprint = metadata.fingerprint(format)
            val duplicate = fingerprint in batchFingerprints || booksProvider().any { book ->
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
            ImportOutcome.Success
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
        backup.delete()
        try {
            updateTask(taskId, "正在复制到应用存储")
            context.contentResolver.openInputStream(uri)?.use { input ->
                staging.outputStream().use { out -> input.copyTo(out) }
            } ?: throw IllegalStateException("无法打开文件")
            if (staging.length() <= 0L) throw IllegalStateException("文件内容为空")
            if (internalFile.exists() && !internalFile.renameTo(backup)) {
                internalFile.copyTo(backup, overwrite = true)
                internalFile.delete()
            }
            if (!staging.renameTo(internalFile)) {
                staging.copyTo(internalFile, overwrite = true)
                staging.delete()
            }
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
            bookDao.runInTransaction {
                bookDao.upsert(
                    existing.copy(
                        original_file_name = fileName,
                        size = size,
                        local_uri = storedUri,
                        local_content_path = internalFile.absolutePath,
                        content_status = "available",
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
            internalFile.delete()
            if (backup.exists()) {
                if (!backup.renameTo(internalFile)) {
                    backup.copyTo(internalFile, overwrite = true)
                    backup.delete()
                }
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

    private data class ImportCandidate(
        val uri: Uri,
        val fileName: String,
        val fileSize: Int,
        /** 全量内容 MD5（≤32MB 时计算；超限为 null，查重回退文件名+大小）。 */
        val contentHash: String? = null,
        val verdict: FormatClassifier.Verdict,
    ) {
        fun fingerprint(format: String): String =
            "${format.lowercase()}|${fileName.lowercase()}|$fileSize"
    }

    private sealed interface ImportOutcome {
        data object Success : ImportOutcome
        data object Duplicate : ImportOutcome
        data object Skipped : ImportOutcome
        data class Failed(val fileName: String, val reason: String) : ImportOutcome
    }

    private companion object {
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
