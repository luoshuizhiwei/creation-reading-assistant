package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.creationreadingassistant.feature.reader.EpubParser
import com.creationreadingassistant.data.repository.SyncRepository
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.feature.reader.EpubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
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
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val sessionsByBook: StateFlow<Map<String, List<ReadingSessionEntity>>> =
        repository.observeSessions()
            .map { list -> list.groupBy { it.book_id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 笔记（书签/笔记）按 bookId 分组，供详情区块展示。 */
    val notesByBook: StateFlow<Map<String, List<NoteEntity>>> =
        repository.observeNotes()
            .map { list -> list.groupBy { it.book_id ?: "" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 高亮/标注按 bookId 分组，供详情区块展示。 */
    val highlightsByBook: StateFlow<Map<String, List<HighlightEntity>>> =
        repository.observeHighlights()
            .map { list -> list.groupBy { it.book_id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 灵感按 source_book_id 分组，供详情区块展示。 */
    val inspirationsByBook: StateFlow<Map<String, List<InspirationEntity>>> =
        repository.observeInspirations()
            .map { list -> list.groupBy { it.source_book_id ?: "" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ===== 书架视图 / 排序偏好（持久化，对齐网页 localStorage）=====
    val shelfViewMode: StateFlow<String> = shelfPrefs.viewMode
    val shelfSortMode: StateFlow<String> = shelfPrefs.sortMode

    fun setShelfViewMode(mode: String) = viewModelScope.launch { shelfPrefs.setViewMode(mode) }
    fun setShelfSortMode(mode: String) = viewModelScope.launch { shelfPrefs.setSortMode(mode) }

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
    }

    // ===== 导入队列（真实 SAF 导入 + 示例书兜底）=====
    private val _importTasks = MutableStateFlow<List<ImportTaskUi>>(emptyList())
    val importTasks: StateFlow<List<ImportTaskUi>> = _importTasks.asStateFlow()

    /** 空书架时导入一本示例书（保留原兜底行为）。 */
    fun importSampleBook() {
        if (_importTasks.value.any { it.status == "processing" }) return
        viewModelScope.launch {
            val book = repository.addSampleBook("导入·新卷")
            recordImport(fileName = "${book.title}.txt", format = "txt", fileSize = 0, status = "success", bookTitle = book.title)
        }
    }

    /** 通过 SAF Uri 真实导入 EPUB / TXT / MD。 */
    fun importFile(uri: Uri) {
        // 仅用 path segment 做快速去重判断，避免主线程 contentResolver 查询
        val quickName = uri.lastPathSegment?.substringAfterLast('/') ?: "unknown"
        if (_importTasks.value.any { it.status == "processing" && it.fileName == quickName }) return
        val taskId = "imp-${UUID.randomUUID()}"
        viewModelScope.launch {
            _importTasks.value = (_importTasks.value + ImportTaskUi(taskId, quickName, IMPORT_PHASES[0], "processing"))
                .sortedByDescending { it.createdAt }
                .take(50)
            try {
                // uriFileName / uriSize / 解析 / 读文件 全部属于 I/O，必须切到 IO 线程，
                // 否则 contentResolver.query 等 Binder 调用阻塞主线程触发 ANR
                val resolved = withContext(Dispatchers.IO) {
                    val fileName = uriFileName(uri)
                    val format = fileName.substringAfterLast('.', "txt").lowercase()
                    val fileSize = uriSize(uri)
                    val book = when (format) {
                        "epub" -> importEpub(uri, taskId)
                        "txt", "md", "markdown" -> importPlainText(uri, taskId, if (format == "markdown") "md" else format)
                        else -> throw IllegalArgumentException("不支持的格式：$format")
                    }
                    ResolvedImport(book, format, fileSize, fileName)
                }
                _importTasks.value = _importTasks.value.map {
                    if (it.id == taskId) it.copy(phase = "导入完成", status = "done") else it
                }
                recordImport(resolved.fileName, resolved.format, resolved.fileSize, "success", bookTitle = resolved.book.title)
            } catch (e: Throwable) {
                val msg = e.message ?: "导入失败"
                _importTasks.value = _importTasks.value.map {
                    if (it.id == taskId) it.copy(phase = "导入失败：$msg", status = "error") else it
                }
                recordImport(quickName, "unknown", 0, "failed", error = msg)
            }
        }
    }

    private data class ResolvedImport(
        val book: BookEntity,
        val format: String,
        val fileSize: Int,
        val fileName: String,
    )

    /** 写入一条导入历史（持久化到 ImportHistoryStore）。 */
    private fun recordImport(
        fileName: String,
        format: String,
        fileSize: Int,
        status: String,
        bookTitle: String? = null,
        error: String? = null,
    ) {
        viewModelScope.launch {
            importHistoryStore.addEntry(
                ImportHistoryEntry(
                    id = "imp-h-${UUID.randomUUID()}",
                    fileName = fileName,
                    fileSize = fileSize,
                    format = format,
                    encoding = if (format in listOf("txt", "md", "markdown")) "UTF-8" else null,
                    bookTitle = bookTitle,
                    error = error,
                    status = status,
                )
            )
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
            bookDao.upsert(
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
        val fileSize = uriSize(uri)
        // 大文件保护：超过 10 MB 时拒绝全量读入内存，改为复制到内部存储后引用内部路径。
        val maxInlineBytes = 10 * 1024 * 1024
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val text: String
        val size: Int
        val storedUri: String
        if (fileSize > maxInlineBytes) {
            // 大文件：复制到内部存储，仅读取前 20000 字符作为预览。
            updateTask(taskId, "文件较大，正在复制到内部存储")
            val internalDir = java.io.File(context.filesDir, "books/$id").also { it.mkdirs() }
            val internalFile = java.io.File(internalDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                internalFile.outputStream().use { out -> input.copyTo(out) }
            } ?: throw IllegalStateException("无法打开文件")
            size = internalFile.length().toInt()
            storedUri = Uri.fromFile(internalFile).toString()
            // 预览仅取前 20000 字符，避免大文件 OOM。
            text = internalFile.inputStream().use { stream ->
                val reader = java.io.BufferedReader(stream.reader(Charsets.UTF_8))
                val sb = StringBuilder()
                var charsRead = 0
                val buf = CharArray(4096)
                while (charsRead < 20000) {
                    val n = reader.read(buf)
                    if (n <= 0) break
                    sb.append(buf, 0, minOf(n, 20000 - charsRead))
                    charsRead += n
                }
                sb.toString()
            }
        } else {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("无法打开文件")
            text = bytes.toString(Charsets.UTF_8)
            size = bytes.size
            storedUri = uri.toString()
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
        val existing = bookDao.getById(bookId)
        if (existing == null) {
            onResult("书籍记录不存在")
            return@launch
        }
        // 格式一致性检查用主线程安全的 path segment 推导，避免 contentResolver 查询
        val quickFormat = (uri.lastPathSegment ?: "").substringAfterLast('.', "txt").lowercase()
        if (quickFormat != existing.format) {
            onResult("格式不一致：原书为 ${existing.format.uppercase()}，所选为 ${quickFormat.uppercase()}")
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
                val format = fileName.substringAfterLast('.', "txt").lowercase()
                val fileSize = uriSize(uri)
                when (format) {
                    "epub" -> reselectEpub(existing, uri, taskId)
                    "txt", "md", "markdown" -> reselectPlainText(existing, uri, taskId, if (format == "markdown") "md" else format)
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
        val parsed = EpubParser.parse(context, uri)
        val fileName = uriFileName(uri)
        val existingBase = existing.original_file_name?.substringBeforeLast('.', "")?.lowercase() ?: ""
        val newBase = fileName.substringBeforeLast('.', "").lowercase()
        if (parsed.id != existing.id && existingBase != newBase) {
            throw IllegalStateException("所选文件与原书不匹配，请作为新书导入")
        }
        val size = uriSize(uri)
        val now = Instant.now().toString()
        bookDao.upsert(
            existing.copy(
                title = parsed.title.takeIf { it.isNotBlank() } ?: existing.title,
                author = parsed.author ?: existing.author,
                original_file_name = fileName,
                size = size,
                local_uri = uri.toString(),
                content_status = "available",
                imported_at = now,
                updated_at = now,
            )
        )
        bookFileDao.upsert(
            BookFileEntity(
                book_id = existing.id,
                file_name = fileName,
                format = "epub",
                size = size,
                local_uri = uri.toString(),
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
        val fileSize = uriSize(uri)
        val maxInlineBytes = 10 * 1024 * 1024
        val now = Instant.now().toString()
        val text: String
        val size: Int
        val storedUri: String
        if (fileSize > maxInlineBytes) {
            updateTask(taskId, "文件较大，正在复制到内部存储")
            val internalDir = java.io.File(context.filesDir, "books/${existing.id}").also { it.mkdirs() }
            val internalFile = java.io.File(internalDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                internalFile.outputStream().use { out -> input.copyTo(out) }
            } ?: throw IllegalStateException("无法打开文件")
            size = internalFile.length().toInt()
            storedUri = Uri.fromFile(internalFile).toString()
            text = internalFile.inputStream().use { stream ->
                val reader = java.io.BufferedReader(stream.reader(Charsets.UTF_8))
                val sb = StringBuilder()
                var charsRead = 0
                val buf = CharArray(4096)
                while (charsRead < 20000) {
                    val n = reader.read(buf)
                    if (n <= 0) break
                    sb.append(buf, 0, minOf(n, 20000 - charsRead))
                    charsRead += n
                }
                sb.toString()
            }
        } else {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("无法打开文件")
            text = bytes.toString(Charsets.UTF_8)
            size = bytes.size
            storedUri = uri.toString()
        }
        updateTask(taskId, "正在保存书架记录")
        bookDao.upsert(
            existing.copy(
                title = fileName.substringBeforeLast('.').ifBlank { existing.title },
                original_file_name = fileName,
                size = size,
                local_uri = storedUri,
                content_status = "available",
                imported_at = now,
                updated_at = now,
            )
        )
        bookContentDao.upsert(BookContentEntity(book_id = existing.id, reader_preview = text.take(20000)))
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
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
