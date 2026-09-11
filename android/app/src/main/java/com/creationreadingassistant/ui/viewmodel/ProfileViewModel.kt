package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.dao.StatsCreatedRow
import com.creationreadingassistant.data.local.dao.StatsProgressRow
import com.creationreadingassistant.data.local.dao.StatsSessionRow
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.feature.annotations.AnnotationEntry
import com.creationreadingassistant.feature.annotations.AnnotationType
import com.creationreadingassistant.feature.sync.PairingManager
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.data.repository.StatsRepository
import com.creationreadingassistant.data.repository.SyncRepository
import com.creationreadingassistant.feature.sync.JsonBridge
import com.creationreadingassistant.feature.sync.LocalZipBackup
import com.creationreadingassistant.feature.sync.WebDavBackup
import com.creationreadingassistant.feature.sync.WebDavConfigStore
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject/**
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

@Stable
data class ProfileLibraryState(
    val books: List<BookEntity> = emptyList(),
    val progressByBook: Map<String, ReadingProgressEntity> = emptyMap(),
    val readingDurationByBook: Map<String, Long> = emptyMap(),
    val notes: List<NoteEntity> = emptyList(),
    val highlights: List<HighlightEntity> = emptyList(),
    val cachedCount: Int = 0,
    val cacheBytes: Long = 0L,
)

/**
 * 「我的」首页摘要：仅承载首页需要的少量聚合数字。
 * 全部来自窄投影（StatsSessionRow / StatsProgressRow / StatsCreatedRow），
 * 不物化完整 Book/Progress/Session/Note 实体，也不触发同步 / WebDAV / AI /
 * 诊断 / 存储等子页查询。子页数据由 [libraryState] 在子页打开后单独加载。
 */
@Stable
data class ProfileHomeSummary(
    val totalDurationMs: Long = 0L,
    val completedBookCount: Int = 0,
    val inspirationCount: Int = 0,
)

private data class ProfileReadingArchive(
    val books: List<BookEntity>,
    val progressByBook: Map<String, ReadingProgressEntity>,
    val readingDurationByBook: Map<String, Long>,
    val notes: List<NoteEntity>,
    val highlights: List<HighlightEntity>,
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
    private val localZipBackup: LocalZipBackup,
    private val pairingManager: PairingManager,
    private val syncRepository: SyncRepository,
    private val configStore: SyncConfigStore,
    private val webDavConfigStore: WebDavConfigStore,
    private val webDavBackup: WebDavBackup,
    private val aiClient: AiClient,
    private val bookRepository: BookRepository,
    private val noteRepository: NoteRepository,
    private val statsRepository: StatsRepository,
    private val goalStore: com.creationreadingassistant.data.settings.GoalStore,
    private val goalScheduler: com.creationreadingassistant.feature.goal.ReadingGoalScheduler,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val syncEngine = ProfileSyncEngine(
        syncRepository = syncRepository,
        configStore = configStore,
        bookRepository = bookRepository,
    )

    private val webDavManager = ProfileWebDavManager(
        webDavConfigStore = webDavConfigStore,
        webDavBackup = webDavBackup,
        jsonBridge = jsonBridge,
    )

    private val readingArchive = combine(
        bookRepository.observeBooks(),
        bookRepository.observeProgress(),
        bookRepository.observeSessions(),
        bookRepository.observeNotes(),
        bookRepository.observeHighlights(),
    ) { books, progress, sessions, notes, highlights ->
        ProfileReadingArchive(
            books = books,
            progressByBook = progress.associateBy { it.book_id },
            readingDurationByBook = sessions
                .groupBy { it.book_id }
                .mapValues { (_, values) -> values.sumOf { it.duration_ms } },
            notes = notes.sortedByDescending { it.created_at },
            highlights = highlights,
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    private val cacheSummary = combine(
        bookRepository.observeCachedCount(),
        bookRepository.observeCachedBytes(),
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
            highlights = archive.highlights,
            cachedCount = cache.count,
            cacheBytes = cache.bytes,
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileLibraryState(),
    )

    /**
     * 首页摘要：窄投影聚合，仅供首页展示「阅读时长 / 累计读完 / 灵感数量」。
     * 与 [libraryState] 完全解耦 —— 首页不订阅 [libraryState]，故不会物化
     * 全部书籍 / 进度 / 会话 / 笔记实体，也不会触发同步 / WebDAV / AI / 诊断 /
     * 存储等子页查询（子页数据由 [libraryState] 在对应子页打开后单独加载）。
     */
    val homeSummary: StateFlow<ProfileHomeSummary> = combine(
        statsRepository.observeStatsSessions(),
        statsRepository.observeStatsProgress(),
        statsRepository.observeStatsInspirations(),
    ) { sessions, progress, inspirations ->
        ProfileHomeSummary(
            totalDurationMs = sessions.sumOf { it.duration_ms },
            completedBookCount = progress.count { it.completion_state == "finished" || it.progress_percent >= 99.5f },
            inspirationCount = inspirations.size,
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileHomeSummary(),
        )

    // ---- 阅读笔记（统一高亮 / 批注 / 书签）----

    /** 一次性提示消息（id 单调递增，保证同文本连续两次也能触发 LaunchedEffect）。 */
    data class AnnotationMessage(val id: Long, val text: String, val undoable: Boolean = false)

    private val _annotationMsg = MutableStateFlow<AnnotationMessage?>(null)
    val annotationMsg: StateFlow<AnnotationMessage?> = _annotationMsg.asStateFlow()
    private var annotationMsgSeq = 0L

    /** 最近一次删除的条目快照：撤销只恢复这一批（本次记录操作），不复活更早的已删项。 */
    private val pendingUndoEntries = MutableStateFlow<List<AnnotationEntry>?>(null)

    private fun emitAnnotationMsg(text: String, undoable: Boolean = false) {
        _annotationMsg.value = AnnotationMessage(++annotationMsgSeq, text, undoable)
    }

    /** 批量软删除统一笔记条目（高亮 / 批注 / 书签），删除后可通过 [undoDeleteAnnotationEntries] 撤销。 */
    fun deleteAnnotationEntries(entries: List<AnnotationEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            entries.forEach { entry ->
                when (entry.type) {
                    AnnotationType.HIGHLIGHT -> noteRepository.deleteHighlight(entry.rawId)
                    AnnotationType.NOTE, AnnotationType.BOOKMARK -> noteRepository.deleteNote(entry.rawId)
                }
            }
            pendingUndoEntries.value = entries
            emitAnnotationMsg("已删除 ${entries.size} 条记录", undoable = true)
        }
    }

    /** 撤销最近一次删除：只恢复该批条目（当前未删除的同 id 记录不会被改动）。 */
    fun undoDeleteAnnotationEntries() {
        val entries = pendingUndoEntries.value ?: return
        viewModelScope.launch(ioDispatcher) {
            entries.forEach { entry ->
                when (entry.type) {
                    AnnotationType.HIGHLIGHT -> noteRepository.restoreHighlight(entry.rawId)
                    AnnotationType.NOTE, AnnotationType.BOOKMARK -> noteRepository.restoreNote(entry.rawId)
                }
            }
            pendingUndoEntries.value = null
            emitAnnotationMsg("已恢复 ${entries.size} 条记录")
        }
    }

    /** 保存个人批注（高亮 note / 笔记 body）；仓储层 copy 保留既有定位信息。 */
    fun editAnnotationEntry(entry: AnnotationEntry, newAnnotation: String) {
        viewModelScope.launch(ioDispatcher) {
            when (entry.type) {
                AnnotationType.HIGHLIGHT -> noteRepository.updateHighlightNote(entry.rawId, newAnnotation)
                AnnotationType.NOTE -> noteRepository.updateNoteBody(entry.rawId, newAnnotation)
                AnnotationType.BOOKMARK -> Unit
            }
            emitAnnotationMsg("批注已保存")
        }
    }

    /** 修改高亮颜色（仅高亮条目有效，改色不动定位信息）。 */
    fun changeAnnotationEntryColor(entry: AnnotationEntry, color: String) {
        viewModelScope.launch(ioDispatcher) {
            noteRepository.updateHighlightColor(entry.rawId, color)
            emitAnnotationMsg("高亮颜色已更新")
        }
    }

    // ---- JSON 桥接 ----
    private val _bridgeStatus = MutableStateFlow<String?>(null)
    val bridgeStatus: StateFlow<String?> = _bridgeStatus

    // ---- ZIP 整包备份 ----
    private val _zipStatus = MutableStateFlow<String?>(null)
    val zipStatus: StateFlow<String?> = _zipStatus

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
        viewModelScope.launch(ioDispatcher) {
            _bridgeStatus.value = null
            runCatching { jsonBridge.exportTo(context, uri) }
                .onSuccess { _bridgeStatus.value = "导出成功"; AppLog.event("Export", "导出成功") }
                .onFailure { _bridgeStatus.value = "导出失败：${it.message}"; AppLog.e("Export", it.message ?: "未知错误") }
        }
    }

    fun import(context: Context, uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            _bridgeStatus.value = null
            runCatching { jsonBridge.importFrom(context, uri) }
                .onSuccess { _bridgeStatus.value = "导入成功"; AppLog.event("Import", "导入成功") }
                .onFailure { _bridgeStatus.value = "导入失败：${it.message}"; AppLog.e("Import", it.message ?: "未知错误") }
        }
    }

    // ---- ZIP 整包备份（backup.json + books/ 源文件）----
    fun zipExport(context: Context, uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            _zipStatus.value = null
            runCatching { localZipBackup.exportZip(context, uri) }
                .onSuccess { _zipStatus.value = "整包导出成功"; AppLog.event("ZipBackup", "导出成功") }
                .onFailure { _zipStatus.value = "整包导出失败：${it.message}"; AppLog.e("ZipBackup", it.message ?: "未知错误") }
        }
    }

    fun zipImport(context: Context, uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            _zipStatus.value = null
            runCatching { localZipBackup.importZip(context, uri) }
                .onSuccess { _zipStatus.value = "整包导入成功"; AppLog.event("ZipBackup", "导入成功") }
                .onFailure { _zipStatus.value = "整包导入失败：${it.message}"; AppLog.e("ZipBackup", it.message ?: "未知错误") }
        }
    }

    // ---- 配对 ----
    // ---- 阅读目标（P3.2 片 2/3） ----

    /** 目标子页一站式投影：偏好 + streak + 今日已读（occurred 口径）。 */
    internal val goalState: kotlinx.coroutines.flow.StateFlow<com.creationreadingassistant.ui.screen.profile.GoalPageState> =
        combine(
            goalStore.prefs,
            statsRepository.observeStatsSessions(),
        ) { prefs, sessions ->
            com.creationreadingassistant.ui.screen.profile.GoalPageState(
                dailyMinutes = prefs.dailyMinutes,
                reminderEnabled = prefs.reminderEnabled,
                reminderMinuteOfDay = prefs.reminderMinuteOfDay,
                streakDays = com.creationreadingassistant.data.repository.computeReadingStreak(sessions).current,
                todayReadingMs = com.creationreadingassistant.ui.screen.stats.computeTodayReadingMs(sessions),
            )
        }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = com.creationreadingassistant.ui.screen.profile.GoalPageState(),
            )

    fun setGoalMinutes(minutes: Int) {
        viewModelScope.launch(ioDispatcher) {
            goalStore.setDailyMinutes(minutes)
            goalScheduler.syncScheduling(goalStore.prefs.first())
        }
    }

    fun setGoalReminderEnabled(enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            goalStore.setReminderEnabled(enabled)
            goalScheduler.syncScheduling(goalStore.prefs.first())
        }
    }

    fun setGoalReminderMinuteOfDay(minute: Int) {
        viewModelScope.launch(ioDispatcher) {
            goalStore.setReminderMinuteOfDay(minute)
            goalScheduler.syncScheduling(goalStore.prefs.first())
        }
    }

    fun startPairing(rawQr: String) {
        viewModelScope.launch(ioDispatcher) {
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
        viewModelScope.launch(ioDispatcher) {
            _syncing.value = true
            _syncMsg.value = null
            val outcome = syncEngine.runSync(autoDownloadBooks)
            outcome.logs.forEach { appendSyncLog(it) }
            _lastSyncResult.value = outcome.result
            _syncMsg.value = outcome.message
            if (outcome.result.success) {
                _config.value = configStore.config
                AppLog.event("Sync", "同步完成：拉取 ${outcome.result.downloaded.books} 本 / ${outcome.result.downloaded.inspirations} 条；冲突 ${outcome.result.conflicts.size}；下载 ${outcome.result.downloaded.bookFiles} 本")
            } else {
                AppLog.e("Sync", outcome.message.removePrefix("同步失败："))
            }
            _syncing.value = false
        }
    }

    // ---- 同步失败项重试（对齐网页版「重试上传失败项」/ 逐项重试） ----

    /** 重试单个失败项：book_file 类下载失败可单项重下；其余（拉取/推送应用失败）回退为整体重同步。 */
    fun retryItem(item: SyncFailedItem) {
        viewModelScope.launch(ioDispatcher) {
            if (item.type == "book_file" && item.bookId != null) {
                if (syncEngine.retryBookFile(item.bookId)) dropFailedItem(item)
            } else {
                // 拉取/推送类失败为整体操作，无单条接口，回退为整体重同步
                syncNow(autoDownloadBooks = false)
            }
        }
    }

    /** 重试全部失败项（对齐网页版「重试上传失败项」整体入口）。 */
    fun retryFailed() {
        viewModelScope.launch(ioDispatcher) {
            val items = _lastSyncResult.value?.failedItems ?: return@launch
            val bookFileIds = items.filter { it.type == "book_file" }.mapNotNull { it.bookId }.distinct()
            bookFileIds.forEach { id -> syncEngine.retryBookFile(id) }
            // 刷新失败列表：移除已成功下载的 book_file 项
            val prev = _lastSyncResult.value
            if (prev != null) {
                val remaining = prev.failedItems.filterNot { it.type == "book_file" && it.bookId in bookFileIds && syncEngine.isBookDownloadedById(it.bookId) }
                _lastSyncResult.value = prev.copy(
                    failedItems = remaining,
                    pendingDownloadCount = syncEngine.countPendingDownloads(),
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
        if (!syncEngine.isBookDownloadedById(item.bookId)) return
        val remaining = prev.failedItems.filterNot { it === item }
        _lastSyncResult.value = prev.copy(
            failedItems = remaining,
            pendingDownloadCount = syncEngine.countPendingDownloads(),
            success = remaining.isEmpty() && prev.conflicts.isEmpty(),
        )
    }

    private fun appendSyncLog(entry: String) {
        val line = "${Instant.now().toString().take(19).replace("T", " ")} · $entry"
        _syncLogs.value = (listOf(line) + _syncLogs.value).take(8)
    }

    // ---- WebDAV ----
    private val _webDavBackups = MutableStateFlow<List<com.creationreadingassistant.feature.sync.WebDavBackup.BackupFile>>(emptyList())
    val webDavBackups: StateFlow<List<com.creationreadingassistant.feature.sync.WebDavBackup.BackupFile>> = _webDavBackups.asStateFlow()

    fun saveWebDav(url: String, user: String, pass: String) {
        // 密码留空 = 继续使用已保存的密码（占位文案已承诺此语义，不得清空凭证）
        val keepPass = pass.isBlank() && (webDavConfigStore.config?.pass?.isNotBlank() == true)
        val effectivePass = if (keepPass) webDavConfigStore.config!!.pass else pass
        webDavConfigStore.config = WebDavConfigStore.Config(url.trim(), user.trim(), effectivePass)
        _webDavConfig.value = webDavConfigStore.config
        _webDavMsg.value = "WebDAV 配置已保存"
        loadWebDavBackups()
    }

    fun loadWebDavBackups() {
        viewModelScope.launch(ioDispatcher) {
            when (val result = webDavManager.listBackups()) {
                null -> Unit
                else -> result
                    .onSuccess { _webDavBackups.value = it }
                    .onFailure { _webDavBackups.value = emptyList() }
            }
        }
    }

    fun backupNow(context: Context) {
        viewModelScope.launch(ioDispatcher) {
            _webDavMsg.value = null
            webDavManager.backup(context)
                .onSuccess {
                    _webDavMsg.value = it
                    AppLog.event("WebDAV", "备份成功")
                    loadWebDavBackups()
                }
                .onFailure { _webDavMsg.value = "备份失败：${it.message}"; AppLog.e("WebDAV", "备份失败：${it.message}") }
        }
    }

    fun testWebDav() {
        viewModelScope.launch(ioDispatcher) {
            _webDavMsg.value = null
            webDavManager.test()
                .onSuccess { _webDavMsg.value = it; AppLog.event("WebDAV", "连接测试：${it.removePrefix("WebDAV ")}") }
                .onFailure { _webDavMsg.value = "连接测试失败：${it.message}"; AppLog.e("WebDAV", "连接测试失败：${it.message}") }
        }
    }

    fun downloadRestore(context: Context, filename: String = "cra-backup-latest.json") {
        viewModelScope.launch(ioDispatcher) {
            _webDavMsg.value = null
            webDavManager.downloadRestore(context, filename)
                .onSuccess { _webDavMsg.value = it; AppLog.event("WebDAV", "下载恢复成功") }
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

    /** 非加密 http AI 接口的一次性警示（由 AiClient 暴露，AI 设置页展示，不拦截请求）。 */
    val aiHttpWarning: StateFlow<String?> = aiClient.insecureHttpWarning

    fun testAi() {
        viewModelScope.launch(ioDispatcher) {
            _aiMsg.value = null
            aiClient.testConnection()
                .onSuccess { _aiMsg.value = "AI 连接成功"; AppLog.event("AI", "连接测试成功") }
                .onFailure { _aiMsg.value = "AI 连接失败：${it.message}"; AppLog.e("AI", "连接测试失败：${it.message}") }
        }
    }
}
