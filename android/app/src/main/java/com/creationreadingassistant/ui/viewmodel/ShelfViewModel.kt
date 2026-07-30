package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Trace
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.ImportHistoryEntry
import com.creationreadingassistant.data.settings.ImportHistoryStore
import com.creationreadingassistant.data.settings.ShelfPrefs
import com.creationreadingassistant.feature.reader.PlainTextDecoder
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.data.repository.SyncRepository
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.library.SafBookSourceScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * 书架页 ViewModel —— 仅复用已有仓库 / DAO，不新增 DB 列或查询方法。
 * - 书籍列表、阅读进度：来自 BookRepository。
 * - 书单 / 分类 / 标签：来自各自 DAO 的 observeAllActive（用于筛选 chip 与批量弹层展示）。
 * - 导入队列：支持真实 SAF 文件导入（EPUB/TXT/MD），保留示例书导入作空书架兜底。
 */
@HiltViewModel
class ShelfViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BookRepository,
    private val bookDao: BookDao,
    private val bookContentDao: BookContentDao,
    private val bookFileDao: BookFileDao,
    private val tagDao: TagDao,
    private val categoryDao: CategoryDao,
    private val shelfDao: ShelfDao,
    private val syncRepository: SyncRepository,
    private val epubRepository: EpubRepository,
    private val importHistoryStore: ImportHistoryStore,
    private val shelfPrefs: ShelfPrefs,
) : ViewModel() {

    val books: StateFlow<List<BookEntity>> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<TagEntity>> = tagDao.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shelves: StateFlow<List<ShelfEntity>> = shelfDao.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progressById: StateFlow<Map<String, ReadingProgressEntity>> =
        repository.observeProgress()
            .map { list -> list.associateBy { it.book_id } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val sessionsByBook: StateFlow<Map<String, List<ReadingSessionEntity>>> =
        repository.observeSessions()
            .map { list -> list.groupBy { it.book_id } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 笔记（书签/笔记）按 bookId 分组，供详情区块展示。 */
    val notesByBook: StateFlow<Map<String, List<NoteEntity>>> =
        repository.observeNotes()
            .map { list -> list.groupBy { it.book_id ?: "" } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 高亮/标注按 bookId 分组，供详情区块展示。 */
    val highlightsByBook: StateFlow<Map<String, List<HighlightEntity>>> =
        repository.observeHighlights()
            .map { list -> list.groupBy { it.book_id } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 灵感按 source_book_id 分组，供详情区块展示。 */
    val inspirationsByBook: StateFlow<Map<String, List<InspirationEntity>>> =
        repository.observeInspirations()
            .map { list -> list.groupBy { it.source_book_id ?: "" } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ===== 书架视图 / 排序偏好（持久化，对齐网页 localStorage）=====
    val shelfViewMode: StateFlow<String> = shelfPrefs.viewMode
    val shelfSortMode: StateFlow<String> = shelfPrefs.sortMode

    fun setShelfViewMode(mode: String) = viewModelScope.launch { shelfPrefs.setViewMode(mode) }
    fun setShelfSortMode(mode: String) = viewModelScope.launch { shelfPrefs.setSortMode(mode) }

    /**
     * 书架渲染用稳定列表：后台线程完成模型投影与排序，新请求自动取消旧请求。
     *
     * 只依赖书籍、进度、排序方式；搜索词、弹层、选择状态、导入任务变化不会触发此 Flow。
     */
    val shelfBooks: StateFlow<List<ShelfBookItem>> = combine(
        books,
        progressById,
        shelfSortMode,
    ) { bookList, progress, sortMode ->
        Trace.beginSection("ShelfListPublish")
        try {
            withContext(Dispatchers.Default) {
                Trace.beginSection("ShelfItemProjection")
                val projected = ShelfBookSorter.projectAll(bookList, progress)
                Trace.endSection()
                Trace.beginSection("ShelfSort")
                val sorted = ShelfBookSorter.sort(projected, sortMode)
                Trace.endSection()
                sorted
            }
        } finally {
            Trace.endSection()
        }
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // ===== 导入历史（持久化记录，对齐网页 ImportHistoryPanel）=====
    val importHistory: StateFlow<List<ImportHistoryEntry>> = importHistoryStore.entries

    fun clearImportHistory() = viewModelScope.launch { importHistoryStore.clear() }

    init {
        viewModelScope.launch {
            try {
                repository.seedSampleIfEmpty()
            } catch (e: Throwable) {
                android.util.Log.e("ShelfVM", "Init seed failed", e)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                epubRepository.repairMissingLocalFileSizes()
            } catch (e: Throwable) {
                android.util.Log.w("ShelfVM", "EPUB metadata repair failed", e)
            }
        }
    }

    // ===== 导入队列（真实 SAF 导入 + 示例书兜底）=====
    private val _importTasks = MutableStateFlow<List<ImportTaskUi>>(emptyList())
    val importTasks: StateFlow<List<ImportTaskUi>> = _importTasks.asStateFlow()
    private val _importBatch = MutableStateFlow(ImportBatchUiState())
    val importBatch: StateFlow<ImportBatchUiState> = _importBatch.asStateFlow()
    private var activeImportJob: Job? = null

    /** 空书架时导入一本示例书（保留原兜底行为）。 */
    fun importSampleBook() {
        if (_importTasks.value.any { it.status == "processing" }) return
        viewModelScope.launch {
            val book = repository.addSampleBook("导入·新卷")
            recordImport(fileName = "${book.title}.txt", format = "txt", fileSize = 0, status = "success", bookTitle = book.title)
        }
    }

    /** 兼容单文件入口；实际统一走顺序批处理，避免同时解析多本大书抢占内存。 */
    fun importFile(uri: Uri) = importFiles(listOf(uri))

    /** 导入文件选择器返回的多本书。 */
    fun importFiles(uris: List<Uri>, sourceLabel: String = "所选文件") {
        val uniqueUris = uris.distinctBy(Uri::toString)
        if (uniqueUris.isEmpty() || activeImportJob?.isActive == true) return
        activeImportJob = viewModelScope.launch {
            runImportBatch(
                uris = uniqueUris,
                sourceLabel = sourceLabel,
                initialSkipped = uris.size - uniqueUris.size,
            )
        }
    }

    /** 扫描用户明确授权的 SAF 文件夹，再顺序导入支持的书籍文件。 */
    fun importFolder(treeUri: Uri) {
        if (activeImportJob?.isActive == true) return
        activeImportJob = viewModelScope.launch {
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
                return@launch
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
    }

    fun retryFailedImports() {
        val failures = _importBatch.value.failures
        if (failures.isEmpty()) return
        importFiles(failures.map { Uri.parse(it.uri) }, sourceLabel = "重试失败项")
    }

    fun dismissImportBatchSummary() {
        if (!_importBatch.value.isRunning) _importBatch.value = ImportBatchUiState()
    }

    private suspend fun runImportBatch(
        uris: List<Uri>,
        sourceLabel: String,
        initialSkipped: Int = 0,
        unreadableFolders: Int = 0,
        truncated: Boolean = false,
        batchId: String = "batch-${UUID.randomUUID()}",
    ) {
        val fingerprints = mutableSetOf<String>()
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
        for (uri in uris) {
            val outcome = importOne(uri, fingerprints)
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
        }
        _importBatch.value = state.copy(isRunning = false, isScanning = false)
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
            val metadata = withContext(Dispatchers.IO) {
                val fileName = uriFileName(uri)
                val rawFormat = fileName.substringAfterLast('.', "").lowercase()
                val format = if (rawFormat == "markdown") "md" else rawFormat
                ImportCandidate(uri, fileName, format, uriSize(uri))
            }
            if (metadata.format !in SUPPORTED_FORMATS) {
                val reason = "不支持的格式"
                updateTaskResult(taskId, metadata.fileName, "已跳过：$reason", "skipped")
                recordImport(metadata.fileName, metadata.format, metadata.fileSize, "skipped", error = reason)
                return ImportOutcome.Skipped
            }

            val fingerprint = metadata.fingerprint
            val duplicate = fingerprint in batchFingerprints || books.value.any { book ->
                book.local_uri == uri.toString() ||
                    (
                        metadata.fileSize > 0 &&
                            book.format.lowercase() == metadata.format &&
                            book.original_file_name.equals(metadata.fileName, ignoreCase = true) &&
                            book.size == metadata.fileSize
                    )
            }
            if (duplicate) {
                batchFingerprints += fingerprint
                updateTaskResult(taskId, metadata.fileName, "已存在，未重复导入", "duplicate")
                recordImport(
                    metadata.fileName,
                    metadata.format,
                    metadata.fileSize,
                    status = "duplicate",
                    isDuplicate = true,
                )
                return ImportOutcome.Duplicate
            }

            val resolved = withContext(Dispatchers.IO) {
                val book = when (metadata.format) {
                    "epub" -> importEpub(uri, taskId)
                    "txt", "md" -> importPlainText(uri, taskId, metadata.format)
                    else -> error("不支持的格式：${metadata.format}")
                }
                ResolvedImport(book, metadata.format, metadata.fileSize, metadata.fileName)
            }
            batchFingerprints += fingerprint
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

    private data class ResolvedImport(
        val book: BookEntity,
        val format: String,
        val fileSize: Int,
        val fileName: String,
    )

    private data class ImportCandidate(
        val uri: Uri,
        val fileName: String,
        val format: String,
        val fileSize: Int,
    ) {
        val fingerprint: String
            get() = "${format.lowercase()}|${fileName.lowercase()}|$fileSize"
    }

    private sealed interface ImportOutcome {
        data object Success : ImportOutcome
        data object Duplicate : ImportOutcome
        data object Skipped : ImportOutcome
        data class Failed(val fileName: String, val reason: String) : ImportOutcome
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

    private fun updateTask(taskId: String, phase: String) {
        _importTasks.value = _importTasks.value.map {
            if (it.id == taskId) it.copy(phase = phase) else it
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

    private fun uriSize(uri: Uri): Int {
        var size = 0
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst()) size = cursor.getLong(idx).toInt()
        }
        return size
    }

    // ===== 删除（软删，级联清理进度/会话/笔记/高亮/正文/文件/关联）=====
    fun deleteBook(id: String) = viewModelScope.launch {
        repository.deleteBook(id)
    }

    /** 撤销删除：恢复书籍及其关联数据。 */
    fun restoreBook(id: String, onResult: ((String) -> Unit)? = null) = viewModelScope.launch {
        try {
            repository.restoreBook(id)
            onResult?.invoke("已恢复书籍")
        } catch (e: Throwable) {
            onResult?.invoke("恢复失败：${e.message}")
        }
    }

    /** 更新书名/作者/简介。 */
    fun updateBookInfo(
        bookId: String,
        title: String,
        author: String?,
        description: String? = null,
        onResult: (String) -> Unit,
    ) = viewModelScope.launch {
        if (title.isBlank()) {
            onResult("书名不能为空")
            return@launch
        }
        repository.updateBookInfo(
            bookId,
            title.trim(),
            author?.trim()?.takeIf { it.isNotBlank() },
            description?.trim()?.takeIf { it.isNotBlank() },
        )
        onResult("已更新书籍信息")
    }

    /** 更新封面（SAF Uri 字符串）。 */
    fun updateBookCover(bookId: String, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        repository.updateBookCover(bookId, uri.toString())
        onResult("已更新封面")
    }

    /** 设置文字封面（由前端生成的 SVG DataURL，对照网页「文字封面」）。 */
    fun setBookTextCover(bookId: String, dataUrl: String, onResult: (String) -> Unit) = viewModelScope.launch {
        repository.updateBookCover(bookId, dataUrl)
        onResult("已生成文字封面")
    }

    /** 重置封面（清空封面图，回退为文字封面）。 */
    fun resetBookCover(bookId: String, onResult: (String) -> Unit) = viewModelScope.launch {
        repository.updateBookCover(bookId, null)
        onResult("已重置封面")
    }

    // ===== 重新选择文件（修复缺失正文）=====
    fun reselectFile(bookId: String, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val existing = withContext(Dispatchers.IO) { bookDao.getById(bookId) }
        if (existing == null) {
            onResult("书籍记录不存在")
            return@launch
        }
        val taskId = "repair-${UUID.randomUUID()}"
        _importTasks.value = (_importTasks.value + ImportTaskUi(taskId, "修复中", "开始修复", "processing"))
            .sortedByDescending { it.createdAt }
            .take(50)
        try {
            // uriFileName / uriSize / 解析 / 读文件 全部属于 I/O，必须切到 IO 线程
            val resolved = withContext(Dispatchers.IO) {
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
            onResult("《${existing.title}》正文已修复")
        } catch (e: Throwable) {
            val msg = e.message ?: "修复失败"
            _importTasks.value = _importTasks.value.map {
                if (it.id == taskId) it.copy(phase = "修复失败：$msg", status = "error") else it
            }
            recordImport("unknown", "unknown", 0, "failed", bookTitle = existing.title, error = msg)
            onResult("修复失败：$msg")
        }
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

    // ===== 清理本地正文缓存（保留书架元数据）=====
    fun clearCacheForBooks(ids: List<String>, onResult: (String) -> Unit) = viewModelScope.launch {
        ids.forEach { repository.clearBookCache(it) }
        onResult("已清理 ${ids.size} 本书的本地正文缓存")
    }

    // ===== 同步下载 ----
    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    /**
     * 书架渲染所需的单一只读状态。
     *
     * 搜索词、当前弹层和选择模式仍属于页面会话状态，不在这里持久化；这样既减少
     * Composable 对十余条 Flow 的分散订阅，也不会让 ViewModel 承担平台选择器状态。
     */
    val uiState: StateFlow<ShelfUiState> = combine(
        combine(books, tags, categories, shelves, progressById) { bookList, tagList, categoryList, shelfList, progress ->
            ShelfLibraryState(
                books = bookList,
                tags = tagList,
                categories = categoryList,
                shelves = shelfList,
                progressById = progress,
            )
        },
        combine(sessionsByBook, notesByBook, highlightsByBook, inspirationsByBook) { sessions, notes, highlights, inspirations ->
            ShelfActivityState(
                sessionsByBook = sessions,
                notesByBook = notes,
                highlightsByBook = highlights,
                inspirationsByBook = inspirations,
            )
        },
        combine(
            combine(importTasks, importBatch) { tasks, batch -> tasks to batch },
            downloadingIds,
            importHistory,
            shelfViewMode,
            shelfSortMode,
        ) { (tasks, batch), downloading, history, viewMode, sortMode ->
            ShelfAuxiliaryState(
                importTasks = tasks,
                importBatch = batch,
                downloadingIds = downloading,
                importHistory = history,
                savedViewMode = viewMode,
                savedSortMode = sortMode,
            )
        },
    ) { library, activity, auxiliary ->
        ShelfUiState(
            library = library,
            activity = activity,
            auxiliary = auxiliary,
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShelfUiState(),
    )

    // ===== 下拉刷新（主书架页）=====
    // 书架数据来自 Room 热流，本身已实时；此刷新用于手动重采底层查询，
    // 让外部写入 / 同步落地后能通过下拉手势立即反映，并给出可见的刷新反馈。
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.observeBooks().first()
                    repository.observeProgress().first()
                }
            }
            delay(350) // 保证刷新反馈有最短可见时长
            _isRefreshing.value = false
        }
    }

    fun downloadBookContent(bookId: String, onResult: (String) -> Unit) = viewModelScope.launch {
        // 防重复下载
        if (_downloadingIds.value.contains(bookId)) {
            onResult("正在下载中…")
            return@launch
        }
        _downloadingIds.value = _downloadingIds.value + bookId
        syncRepository.downloadBookContent(bookId)
            .onSuccess { onResult("下载成功") }
            .onFailure { onResult("下载失败：${it.message}") }
        _downloadingIds.value = _downloadingIds.value - bookId
    }

    suspend fun listDesktopBooks(): List<SyncContract.BookFileManifest> = syncRepository.listDesktopBooks()

    private fun nowIso(): String = java.time.Instant.now().toString()

    companion object {
        private val SUPPORTED_FORMATS = setOf("epub", "txt", "md")
        private val IMPORT_PHASES = listOf(
            "正在校验文件",
            "正在复制到应用",
            "正在验证 EPUB 结构",
            "正在保存书架记录",
            "导入完成",
        )
    }
}

data class ImportTaskUi(
    val id: String,
    val fileName: String,
    val phase: String,
    val status: String, // "processing" | "done" | "error"
    val createdAt: Long = System.currentTimeMillis(),
)

data class ImportFailureUi(
    val uri: String,
    val fileName: String,
    val reason: String,
)

data class ImportBatchUiState(
    val id: String = "",
    val sourceLabel: String = "",
    val total: Int = 0,
    val completed: Int = 0,
    val succeeded: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val unreadableFolders: Int = 0,
    val truncated: Boolean = false,
    val isScanning: Boolean = false,
    val isRunning: Boolean = false,
    val failures: List<ImportFailureUi> = emptyList(),
) {
    val hasResult: Boolean
        get() = total > 0 || failed > 0 || unreadableFolders > 0 || truncated
}

data class ShelfUiState(
    val library: ShelfLibraryState = ShelfLibraryState(),
    val activity: ShelfActivityState = ShelfActivityState(),
    val auxiliary: ShelfAuxiliaryState = ShelfAuxiliaryState(),
)

data class ShelfLibraryState(
    val books: List<BookEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val shelves: List<ShelfEntity> = emptyList(),
    val progressById: Map<String, ReadingProgressEntity> = emptyMap(),
)

data class ShelfActivityState(
    val sessionsByBook: Map<String, List<ReadingSessionEntity>> = emptyMap(),
    val notesByBook: Map<String, List<NoteEntity>> = emptyMap(),
    val highlightsByBook: Map<String, List<HighlightEntity>> = emptyMap(),
    val inspirationsByBook: Map<String, List<InspirationEntity>> = emptyMap(),
)

data class ShelfAuxiliaryState(
    val importTasks: List<ImportTaskUi> = emptyList(),
    val importBatch: ImportBatchUiState = ImportBatchUiState(),
    val downloadingIds: Set<String> = emptySet(),
    val importHistory: List<ImportHistoryEntry> = emptyList(),
    val savedViewMode: String = "grid",
    val savedSortMode: String = "recent",
)
