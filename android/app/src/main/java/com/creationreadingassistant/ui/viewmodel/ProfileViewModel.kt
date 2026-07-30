package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.feature.sync.PairingManager
import com.creationreadingassistant.data.repository.SyncRepository
import com.creationreadingassistant.feature.sync.JsonBridge
import com.creationreadingassistant.feature.sync.WebDavBackup
import com.creationreadingassistant.feature.sync.WebDavConfigStore
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.feature.log.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * 单条同步失败项。
 */
data class SyncFailedItem(
    val type: String,
    val bookId: String? = null,
    val title: String? = null,
    val reason: String,
)

/**
 * 同步冲突项。
 */
data class SyncConflictItem(
    val type: String,
    val id: String,
    val title: String? = null,
    val remoteUpdatedAt: String,
    val localUpdatedAt: String,
    val resolution: String,
)

/**
 * 同步结果计数分组。
 */
data class SyncCountGroup(
    val inspirations: Int = 0,
    val books: Int = 0,
    val progress: Int = 0,
    val bookFiles: Int = 0,
    val sessions: Int = 0,
)

/**
 * 结构化同步结果详情（对照 web 版 useMobileSync.SyncResultDetail）。
 */
data class SyncResultDetail(
    val timestamp: String,
    val success: Boolean,
    val uploaded: SyncCountGroup,
    val downloaded: SyncCountGroup,
    val pendingDownloadCount: Int,
    val failedItems: List<SyncFailedItem> = emptyList(),
    val conflicts: List<SyncConflictItem> = emptyList(),
    val durationMs: Long,
    val snapshotBackupKey: String? = null,
)

data class ProfileLibraryState(
    val books: List<BookEntity> = emptyList(),
    val progressByBook: Map<String, ReadingProgressEntity> = emptyMap(),
    val readingDurationByBook: Map<String, Long> = emptyMap(),
    val notes: List<NoteEntity> = emptyList(),
    val cachedCount: Int = 0,
    val cacheBytes: Long = 0L,
)

private data class ProfileReadingArchive(
    val books: List<BookEntity>,
    val progressByBook: Map<String, ReadingProgressEntity>,
    val readingDurationByBook: Map<String, Long>,
    val notes: List<NoteEntity>,
)

private data class ProfileCacheSummary(
    val count: Int,
    val bytes: Long,
)

/**
 * 「我的」页 ViewModel。
 * - JSON 数据桥接（导出/导入）—— 复用同步信封契约
 * - 局域网配对与同步（P3）
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val jsonBridge: JsonBridge,
    private val pairingManager: PairingManager,
    private val syncRepository: SyncRepository,
    private val configStore: SyncConfigStore,
    private val webDavConfigStore: WebDavConfigStore,
    private val webDavBackup: WebDavBackup,
    private val aiClient: AiClient,
    private val bookDao: BookDao,
    readingProgressDao: ReadingProgressDao,
    readingSessionDao: ReadingSessionDao,
    noteDao: NoteDao,
    bookContentDao: BookContentDao,
) : ViewModel() {

    private val readingArchive = combine(
        bookDao.observeAllActive(),
        readingProgressDao.observeAllActive(),
        readingSessionDao.observeAllActive(),
        noteDao.observeAllActive(),
    ) { books, progress, sessions, notes ->
        ProfileReadingArchive(
            books = books,
            progressByBook = progress.associateBy { it.book_id },
            readingDurationByBook = sessions
                .groupBy { it.book_id }
                .mapValues { (_, values) -> values.sumOf { it.duration_ms } },
            notes = notes.sortedByDescending { it.created_at },
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private val cacheSummary = combine(
        bookContentDao.observeCachedCount(),
        bookContentDao.observeCachedBytes(),
    ) { count, bytes ->
        ProfileCacheSummary(count = count, bytes = bytes)
    }.distinctUntilChanged()

    val libraryState: StateFlow<ProfileLibraryState> = combine(
        readingArchive,
        cacheSummary,
    ) { archive, cache ->
        ProfileLibraryState(
            books = archive.books,
            progressByBook = archive.progressByBook,
            readingDurationByBook = archive.readingDurationByBook,
            notes = archive.notes,
            cachedCount = cache.count,
            cacheBytes = cache.bytes,
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileLibraryState(),
    )

    // ---- JSON 桥接 ----
    private val _bridgeStatus = MutableStateFlow<String?>(null)
    val bridgeStatus: StateFlow<String?> = _bridgeStatus

    // ---- 配对 / 同步 ----
    private val _config = MutableStateFlow<SyncConfigStore.Config?>(configStore.config)
    val config: StateFlow<SyncConfigStore.Config?> = _config.asStateFlow()

    private val _pairing = MutableStateFlow(false)
    val pairing: StateFlow<Boolean> = _pairing.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _pairMsg = MutableStateFlow<String?>(null)
    val pairMsg: StateFlow<String?> = _pairMsg.asStateFlow()

    private val _syncMsg = MutableStateFlow<String?>(null)
    val syncMsg: StateFlow<String?> = _syncMsg.asStateFlow()

    // ---- 同步结果 / 日志 ----
    private val _lastSyncResult = MutableStateFlow<SyncResultDetail?>(null)
    val lastSyncResult: StateFlow<SyncResultDetail?> = _lastSyncResult.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<String>>(emptyList())
    val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

    // ---- WebDAV 远程备份 ----
    private val _webDavConfig = MutableStateFlow<WebDavConfigStore.Config?>(webDavConfigStore.config)
    val webDavConfig: StateFlow<WebDavConfigStore.Config?> = _webDavConfig.asStateFlow()

    private val _webDavMsg = MutableStateFlow<String?>(null)
    val webDavMsg: StateFlow<String?> = _webDavMsg.asStateFlow()

    // ---- JSON 桥接操作 ----
    fun export(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _bridgeStatus.value = null
            runCatching { jsonBridge.exportTo(context, uri) }
                .onSuccess { _bridgeStatus.value = "导出成功"; AppLog.event("Export", "导出成功") }
                .onFailure { _bridgeStatus.value = "导出失败：${it.message}"; AppLog.e("Export", it.message ?: "未知错误") }
        }
    }

    fun import(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _bridgeStatus.value = null
            runCatching { jsonBridge.importFrom(context, uri) }
                .onSuccess { _bridgeStatus.value = "导入成功"; AppLog.event("Import", "导入成功") }
                .onFailure { _bridgeStatus.value = "导入失败：${it.message}"; AppLog.e("Import", it.message ?: "未知错误") }
        }
    }

    // ---- 配对 ----
    fun startPairing(rawQr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _pairing.value = true
            _pairMsg.value = null
            runCatching {
                val info = pairingManager.parseQr(rawQr)
                pairingManager.pair(info)
            }.onSuccess { cfg ->
                _config.value = cfg
                _pairMsg.value = "已配对：${cfg.baseUrl}"
                appendSyncLog("已配对：${cfg.baseUrl}")
            }.onFailure {
                _pairMsg.value = "配对失败：${it.message}"
                appendSyncLog("配对失败：${it.message}")
            }
            _pairing.value = false
        }
    }

    fun unpair() {
        pairingManager.clearPairing()
        _config.value = null
        _pairMsg.value = "已解除配对"
        _syncMsg.value = null
        appendSyncLog("已解除配对")
    }

    // ---- 同步 ----
    fun syncNow(autoDownloadBooks: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            _syncing.value = true
            _syncMsg.value = null
            val startedAt = System.currentTimeMillis()
            val timestamp = Instant.now().toString()
            val allFailures = mutableListOf<SyncFailedItem>()
            var allConflicts = emptyList<SyncConflictItem>()
            var downloadedBookFiles = 0
            try {
                val pulled = syncRepository.pull()
                allFailures += pulled.failedItems.map { toUiFailedItem(it) }
                appendSyncLog("拉取完成：${pulled.books} 本 / ${pulled.inspirations} 条 / ${pulled.progress} 进度 / ${pulled.sessions} 会话")
                if (pulled.failedItems.isNotEmpty()) {
                    appendSyncLog("  拉取失败 ${pulled.failedItems.size} 项")
                }

                val pushed = syncRepository.push()
                allFailures += pushed.failedItems.map { toUiFailedItem(it) }
                allConflicts = pushed.conflicts.map {
                    SyncConflictItem(
                        type = it.type,
                        id = it.id,
                        title = it.title,
                        remoteUpdatedAt = it.remoteUpdatedAt,
                        localUpdatedAt = it.localUpdatedAt,
                        resolution = it.resolution,
                    )
                }
                appendSyncLog("推送完成：${pushed.applied.books + pushed.applied.inspirations + pushed.applied.progress + pushed.applied.sessions} 条")
                if (allConflicts.isNotEmpty()) appendSyncLog("  冲突 ${allConflicts.size} 条（服务端保留）")

                if (autoDownloadBooks) {
                    appendSyncLog("开始下载书籍正文…")
                    val dl = syncRepository.downloadPendingBooks(maxCount = 20)
                    downloadedBookFiles = dl.success
                    allFailures += dl.failedItems.map { toUiFailedItem(it) }
                    appendSyncLog("下载完成：${dl.success} 本成功，${dl.failed} 本失败")
                }

                _config.value = configStore.config

                val pending = countPendingDownloads()
                val duration = System.currentTimeMillis() - startedAt
                val result = SyncResultDetail(
                    timestamp = timestamp,
                    success = true,
                    uploaded = SyncCountGroup(
                        inspirations = pushed.applied.inspirations,
                        books = pushed.applied.books,
                        progress = pushed.applied.progress,
                        sessions = pushed.applied.sessions,
                    ),
                    downloaded = SyncCountGroup(
                        inspirations = pulled.inspirations,
                        books = pulled.books,
                        progress = pulled.progress,
                        sessions = pulled.sessions,
                        bookFiles = downloadedBookFiles,
                    ),
                    pendingDownloadCount = pending,
                    failedItems = allFailures,
                    conflicts = allConflicts,
                    durationMs = duration,
                )
                _lastSyncResult.value = result
                _syncMsg.value = "同步完成：拉取 ${pulled.books} 书 / ${pulled.inspirations} 灵感；" +
                    "推送 ${pushed.applied.books + pushed.applied.inspirations} 条；" +
                    "冲突 ${allConflicts.size}；下载 $downloadedBookFiles 本"
                AppLog.event("Sync", "同步完成：拉取 ${pulled.books} 本 / ${pulled.inspirations} 条；冲突 ${allConflicts.size}；下载 $downloadedBookFiles 本")
            } catch (e: Throwable) {
                val msg = e.message ?: "未知错误"
                allFailures.add(SyncFailedItem(type = "sync", reason = msg))
                val result = SyncResultDetail(
                    timestamp = timestamp,
                    success = false,
                    uploaded = SyncCountGroup(),
                    downloaded = SyncCountGroup(),
                    pendingDownloadCount = countPendingDownloads(),
                    failedItems = allFailures,
                    conflicts = allConflicts,
                    durationMs = System.currentTimeMillis() - startedAt,
                )
                _lastSyncResult.value = result
                _syncMsg.value = "同步失败：$msg"
                AppLog.e("Sync", msg)
                appendSyncLog("同步失败：$msg")
            }
            _syncing.value = false
        }
    }

    private fun toUiFailedItem(entry: com.creationreadingassistant.data.repository.SyncRepository.SyncFailedEntry): SyncFailedItem =
        SyncFailedItem(
            type = entry.type,
            bookId = if (entry.type == "book" || entry.type == "book_file") entry.id else null,
            title = entry.title,
            reason = entry.reason,
        )

    private suspend fun countPendingDownloads(): Int {
        return runCatching {
            bookDao.observeAllActive().first().count { !isBookDownloaded(it) }
        }.getOrDefault(0)
    }

    private fun isBookDownloaded(book: com.creationreadingassistant.data.local.entity.BookEntity): Boolean {
        if (book.content_status == "missing" || book.content_status == "failed" || book.content_status == "downloading") return false
        return !book.local_content_path.isNullOrBlank() || !book.local_uri.isNullOrBlank()
    }

    // ---- 同步失败项重试（对齐网页版「重试上传失败项」/ 逐项重试） ----

    /** 重试单个失败项：book_file 类下载失败可单项重下；其余（拉取/推送应用失败）回退为整体重同步。 */
    fun retryItem(item: SyncFailedItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (item.type == "book_file" && item.bookId != null) {
                val ok = runCatching { syncRepository.downloadBookContent(item.bookId) }.isSuccess
                if (ok) dropFailedItem(item)
            } else {
                // 拉取/推送类失败为整体操作，无单条接口，回退为整体重同步
                syncNow(autoDownloadBooks = false)
            }
        }
    }

    /** 重试全部失败项（对齐网页版「重试上传失败项」整体入口）。 */
    fun retryFailed() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = _lastSyncResult.value?.failedItems ?: return@launch
            val bookFileIds = items.filter { it.type == "book_file" }.mapNotNull { it.bookId }.distinct()
            bookFileIds.forEach { id -> runCatching { syncRepository.downloadBookContent(id) } }
            // 刷新失败列表：移除已成功下载的 book_file 项
            val prev = _lastSyncResult.value
            if (prev != null) {
                val remaining = prev.failedItems.filterNot { it.type == "book_file" && it.bookId in bookFileIds && isBookDownloadedById(it.bookId) }
                _lastSyncResult.value = prev.copy(
                    failedItems = remaining,
                    pendingDownloadCount = countPendingDownloads(),
                    success = remaining.isEmpty() && prev.conflicts.isEmpty(),
                )
            }
            // 非整本正文类失败（拉取/推送应用失败）整体重同步
            if (items.any { it.type != "book_file" }) {
                syncNow(autoDownloadBooks = false)
            }
        }
    }

    private suspend fun dropFailedItem(item: SyncFailedItem) {
        val prev = _lastSyncResult.value ?: return
        if (!isBookDownloadedById(item.bookId)) return
        val remaining = prev.failedItems.filterNot { it === item }
        _lastSyncResult.value = prev.copy(
            failedItems = remaining,
            pendingDownloadCount = countPendingDownloads(),
            success = remaining.isEmpty() && prev.conflicts.isEmpty(),
        )
    }

    private suspend fun isBookDownloadedById(id: String?): Boolean {
        if (id == null) return false
        val b = runCatching { bookDao.getById(id) }.getOrNull() ?: return false
        return b.content_status == "available" && (!b.local_content_path.isNullOrBlank() || !b.local_uri.isNullOrBlank())
    }

    private fun appendSyncLog(entry: String) {
        val line = "${Instant.now().toString().take(19).replace("T", " ")} · $entry"
        _syncLogs.value = (listOf(line) + _syncLogs.value).take(8)
    }

    // ---- WebDAV ----
    private val _webDavBackups = MutableStateFlow<List<com.creationreadingassistant.feature.sync.WebDavBackup.BackupFile>>(emptyList())
    val webDavBackups: StateFlow<List<com.creationreadingassistant.feature.sync.WebDavBackup.BackupFile>> = _webDavBackups.asStateFlow()

    fun saveWebDav(url: String, user: String, pass: String) {
        webDavConfigStore.config = WebDavConfigStore.Config(url.trim(), user.trim(), pass)
        _webDavConfig.value = webDavConfigStore.config
        _webDavMsg.value = "WebDAV 配置已保存"
        loadWebDavBackups()
    }

    fun loadWebDavBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val cfg = webDavConfigStore.config ?: return@launch
                webDavBackup.listBackups(cfg.url, cfg.user, cfg.pass).getOrThrow()
            }.onSuccess { _webDavBackups.value = it }
                .onFailure { _webDavBackups.value = emptyList() }
        }
    }

    fun backupNow(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _webDavMsg.value = null
            runCatching {
                val cfg = webDavConfigStore.config ?: throw IllegalStateException("请先填写 WebDAV 配置")
                val json = jsonBridge.exportToString(context)
                // 同时写「最新」固定名（供下载恢复确定性拉取）与带时间戳的归档
                webDavBackup.put(cfg.url, cfg.user, cfg.pass, "cra-backup-latest.json", json).getOrThrow()
                webDavBackup.put(cfg.url, cfg.user, cfg.pass, "cra-backup-${System.currentTimeMillis()}.json", json).getOrThrow()
            }.onSuccess {
                _webDavMsg.value = "备份成功"
                AppLog.event("WebDAV", "备份成功")
                loadWebDavBackups()
            }
                .onFailure { _webDavMsg.value = "备份失败：${it.message}"; AppLog.e("WebDAV", "备份失败：${it.message}") }
        }
    }

    fun testWebDav() {
        viewModelScope.launch(Dispatchers.IO) {
            _webDavMsg.value = null
            runCatching {
                val cfg = webDavConfigStore.config ?: throw IllegalStateException("请先填写 WebDAV 配置")
                webDavBackup.test(cfg.url, cfg.user, cfg.pass).getOrThrow()
            }.onSuccess { _webDavMsg.value = "WebDAV $it"; AppLog.event("WebDAV", "连接测试：$it") }
                .onFailure { _webDavMsg.value = "连接测试失败：${it.message}"; AppLog.e("WebDAV", "连接测试失败：${it.message}") }
        }
    }

    fun downloadRestore(context: Context, filename: String = "cra-backup-latest.json") {
        viewModelScope.launch(Dispatchers.IO) {
            _webDavMsg.value = null
            runCatching {
                val cfg = webDavConfigStore.config ?: throw IllegalStateException("请先填写 WebDAV 配置")
                val json = webDavBackup.get(cfg.url, cfg.user, cfg.pass, filename).getOrThrow()
                jsonBridge.importFromString(context, json)
            }.onSuccess { _webDavMsg.value = "已从 WebDAV 恢复备份"; AppLog.event("WebDAV", "下载恢复成功") }
                .onFailure { _webDavMsg.value = "下载恢复失败：${it.message}"; AppLog.e("WebDAV", "下载恢复失败：${it.message}") }
        }
    }

    fun clearWebDav() {
        webDavConfigStore.config = null
        _webDavConfig.value = null
        _webDavBackups.value = emptyList()
        _webDavMsg.value = "WebDAV 配置已清除"
    }

    // ---- AI 连接测试 ----
    private val _aiMsg = MutableStateFlow<String?>(null)
    val aiMsg: StateFlow<String?> = _aiMsg.asStateFlow()

    fun testAi() {
        viewModelScope.launch(Dispatchers.IO) {
            _aiMsg.value = null
            aiClient.testConnection()
                .onSuccess { _aiMsg.value = "AI 连接成功"; AppLog.event("AI", "连接测试成功") }
                .onFailure { _aiMsg.value = "AI 连接失败：${it.message}"; AppLog.e("AI", "连接测试失败：${it.message}") }
        }
    }
}
