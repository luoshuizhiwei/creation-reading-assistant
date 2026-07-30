package com.creationreadingassistant.ui.viewmodel

import android.os.Build
import android.os.Trace
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.data.settings.TtsResume
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.locator.AnchorCacheStore
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.ui.screen.ReaderScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 阅读器文档协调层 + 数据层中枢。
 *
 * 文档加载 / 资源所有权 + 所有 DAO·Repository·Store 写入均经过此 ViewModel；
 * ReaderScreen 变为纯组合函数，只接收 State 和 onAction 回调。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val documentLoader: ReaderDocumentLoader,
    private val highlightDao: HighlightDao,
    private val noteDao: NoteDao,
    private val inspirationDao: InspirationDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readingSessionDao: ReadingSessionDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val bookRepository: BookRepository,
    private val epubRepository: EpubRepository,
    val settingsStore: SettingsStore,
    val anchorCacheStore: AnchorCacheStore,
    val pageIndexStore: PageIndexStore,
    val aiClient: AiClient,
) : ViewModel() {

    // ── 文档加载代次 ──────────────────────────────────────────────
    private val loadGeneration = AtomicInteger(0)
    private var loadJob: Job? = null
    private val readerOpenTraceLock = Any()
    private var activeReaderOpenTraceCookie: Int? = null

    // ── 章节加载代次 ──────────────────────────────────────────
    private val chapterLoadGeneration = AtomicInteger(0)
    private var chapterLoadJob: Job? = null
    private val _chapterLoadState = MutableStateFlow<ChapterLoadResult?>(null)
    val chapterLoadState: StateFlow<ChapterLoadResult?> = _chapterLoadState.asStateFlow()

    // ── TXT 规则扫描结果 ──────────────────────────────────────
    private val _txtRuleScanResult = MutableStateFlow<TxtRuleScanResult?>(null)
    val txtRuleScanResult: StateFlow<TxtRuleScanResult?> = _txtRuleScanResult.asStateFlow()

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // ── 阅读页 UI 短状态 ─────────────────────────────────────────
    private val _screenState = MutableStateFlow(ReaderScreenState())
    val screenState: StateFlow<ReaderScreenState> = _screenState.asStateFlow()

    // ── 路由渲染状态：只观察当前书籍，避免打开一本书时订阅并过滤整库数据 ──
    private val activeBookId = MutableStateFlow("")

    // ── 设置：TXT 目录规则 ───────────────────────────────────────
    private val _txtTocRuleId = MutableStateFlow("builtin")
    val txtTocRuleId: StateFlow<String> = _txtTocRuleId.asStateFlow()

    private val bookData = activeBookId
        .flatMapLatest { bookId ->
            if (bookId.isBlank()) {
                flowOf(ReaderBookData())
            } else {
                combine(
                    highlightDao.observeByBook(bookId),
                    noteDao.observeByBook(bookId),
                    inspirationDao.observeByBook(bookId),
                    readingSessionDao.observeByBook(bookId),
                ) { highlights, notes, inspirations, sessions ->
                    ReaderBookData(
                        highlights = highlights,
                        notes = notes,
                        inspirations = inspirations,
                        sessions = sessions,
                    )
                }
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private val taxonomyData = screenState
        .map { state ->
            state.sheet == com.creationreadingassistant.ui.screen.ReaderSheet.AI_EXPLAIN ||
                state.sheet == com.creationreadingassistant.ui.screen.ReaderSheet.INSPIRATION
        }
        .distinctUntilChanged()
        .flatMapLatest { needed ->
            if (!needed) {
                flowOf(ReaderTaxonomyData())
            } else {
                combine(
                    categoryDao.observeAllActive(),
                    tagDao.observeAllActive(),
                ) { categories, tags -> ReaderTaxonomyData(categories, tags) }
            }
        }
        .distinctUntilChanged()

    private val auxiliaryState = combine(
        txtTocRuleId,
        chapterLoadState,
        txtRuleScanResult,
    ) { tocRuleId, chapterLoad, ruleScan ->
        ReaderAuxiliaryState(tocRuleId, chapterLoad, ruleScan)
    }.distinctUntilChanged()

    val routeUiState: StateFlow<ReaderRouteUiState> = combine(
        uiState,
        screenState,
        bookData,
        taxonomyData,
        auxiliaryState,
    ) { document, screen, book, taxonomy, auxiliary ->
        ReaderRouteUiState(
            document = document,
            screen = screen,
            highlights = book.highlights,
            notes = book.notes,
            inspirations = book.inspirations,
            sessions = book.sessions,
            categories = taxonomy.categories,
            tags = taxonomy.tags,
            txtTocRuleId = auxiliary.txtTocRuleId,
            chapterLoadResult = auxiliary.chapterLoadResult,
            txtRuleScanResult = auxiliary.txtRuleScanResult,
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderRouteUiState(),
        )

    // ── Action 路由 ──────────────────────────────────────────────
    fun onAction(action: ReaderAction) {
        when (action) {
            // 文档协调
            is ReaderAction.OpenBook -> openBook(action.bookId, force = false)
            is ReaderAction.Retry -> openBook(_uiState.value.requestedBookId, force = true)
            is ReaderAction.LoadChapter -> loadChapter(action.bookId, action.chapterIndex)
            is ReaderAction.ScanTxtTocRule -> scanTxtTocRule(action.filePath, action.ruleId)

            // UI 状态
            is ReaderAction.ToggleControls -> updateScreen {
                it.copy(controlsVisible = action.visible ?: !it.controlsVisible)
            }
            is ReaderAction.OpenSheet -> updateScreen { it.copy(sheet = action.sheet) }
            is ReaderAction.CloseSheet -> updateScreen { it.copy(sheet = null) }
            is ReaderAction.SetSelectedText -> updateScreen {
                it.copy(selectedText = action.text, selectedRangeStart = action.rangeStart, selectedGlobalOffset = action.globalOffset)
            }
            is ReaderAction.ClearSelection -> updateScreen {
                it.copy(selectedText = "", selectedRangeStart = -1, selectedGlobalOffset = -1, showColorRow = false)
            }
            is ReaderAction.SetShowTts -> updateScreen { it.copy(showTts = action.show) }
            is ReaderAction.SetSearchQuery -> updateScreen { it.copy(searchQuery = action.query) }
            is ReaderAction.SetShowOverflow -> updateScreen { it.copy(showReaderOverflow = action.show) }
            is ReaderAction.SetNoteOpen -> updateScreen { it.copy(noteOpen = action.open) }
            is ReaderAction.SetNoteBody -> updateScreen { it.copy(noteBody = action.body) }
            is ReaderAction.ToggleColorRow -> updateScreen { it.copy(showColorRow = !it.showColorRow) }
            is ReaderAction.SetSheetOpenGuard -> updateScreen { it.copy(sheetOpenGuard = action.guard) }

            // 数据写入（全部 IO 协程）
            is ReaderAction.SaveHighlight -> io { highlightDao.upsert(action.highlight) }
            is ReaderAction.DeleteHighlight -> io {
                highlightDao.upsert(highlightDao.getById(action.highlightId)?.copy(deleted_at = nowIso()) ?: return@io)
            }
            is ReaderAction.UpdateHighlightColor -> io {
                val h = highlightDao.getById(action.highlightId) ?: return@io
                highlightDao.upsert(h.copy(color = action.color, updated_at = nowIso(), revision = h.revision + 1))
            }
            is ReaderAction.UpdateHighlightNote -> io {
                val h = highlightDao.getById(action.highlightId) ?: return@io
                highlightDao.upsert(h.copy(note = action.note.ifBlank { null }, updated_at = nowIso(), revision = h.revision + 1))
            }
            is ReaderAction.SaveNote -> io { noteDao.upsert(action.note) }
            is ReaderAction.DeleteNote -> io {
                noteDao.upsert(noteDao.getById(action.noteId)?.copy(deleted_at = nowIso()) ?: return@io)
            }
            is ReaderAction.AddBookmark -> io {
                noteDao.upsert(
                    NoteEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        book_id = action.bookId.ifBlank { null },
                        title = "书签 · ${action.title}",
                        body = "",
                        excerpt = null,
                        chapter_title = action.title.ifBlank { null },
                        progress_percent = action.offset.toFloat(),
                        kind = "bookmark",
                        locator_json = null,
                        payload = "{}",
                        created_at = nowIso(),
                        device_id = null,
                        revision = 1,
                        updated_at = nowIso(),
                        deleted_at = null,
                    ),
                )
            }
            is ReaderAction.ConvertHighlightToNote -> io {
                val h = highlightDao.getById(action.highlightId) ?: return@io
                noteDao.upsert(
                    NoteEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        book_id = h.book_id,
                        title = "笔记：${h.chapter_title ?: ""}",
                        body = if (h.note.isNullOrBlank()) h.text else "${h.text}\n\n${h.note}",
                        excerpt = h.text,
                        chapter_title = h.chapter_title,
                        progress_percent = h.progress_percent ?: 0f,
                        kind = "note",
                        locator_json = h.locator_json,
                        payload = "{}",
                        created_at = nowIso(),
                        device_id = null,
                        revision = 1,
                        updated_at = nowIso(),
                        deleted_at = null,
                    ),
                )
            }
            is ReaderAction.ConvertHighlightToInspiration -> io {
                val h = highlightDao.getById(action.highlightId) ?: return@io
                inspirationDao.upsert(
                    InspirationEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        title = "高亮灵感：${h.text.take(24)}",
                        body = if (h.note.isNullOrBlank()) h.text else "${h.text}\n\n${h.note}",
                        type = "note",
                        status = "inbox",
                        source_book_id = h.book_id,
                        payload = "{}",
                        created_at = nowIso(),
                        device_id = null,
                        revision = 1,
                        updated_at = nowIso(),
                        deleted_at = null,
                    ),
                )
            }
            is ReaderAction.SaveInspiration -> io { inspirationDao.upsert(action.inspiration) }
            is ReaderAction.CreateCategory -> io {
                val id = "mobile-category-${java.util.UUID.randomUUID()}"
                categoryDao.upsert(CategoryEntity(id = id, name = action.name, created_at = nowIso(), updated_at = nowIso()))
            }
            is ReaderAction.CreateTag -> io {
                val id = "mobile-tag-${java.util.UUID.randomUUID()}"
                tagDao.upsert(TagEntity(id = id, name = action.name, type = "inspiration", created_at = nowIso(), updated_at = nowIso()))
            }
            is ReaderAction.SaveProgress -> io { readingProgressDao.upsert(action.progress) }
            is ReaderAction.SaveEpubProgress -> io {
                epubRepository.saveProgress(action.bookId, action.chapterIndex, action.percent, action.offsetInChapter)
            }
            is ReaderAction.DeleteBook -> io { bookRepository.deleteBook(action.bookId) }

            // 设置
            is ReaderAction.LoadTxtTocRule -> io {
                _txtTocRuleId.value = settingsStore.loadTxtTocRule(action.bookId)
            }
            is ReaderAction.SaveTxtTocRule -> io {
                settingsStore.saveTxtTocRule(action.bookId, action.ruleId)
            }
            is ReaderAction.SaveTtsResume -> io {
                settingsStore.saveTtsResume(action.bookId, action.chapterIndex, action.offset)
            }
        }
    }

    /** 加载 TTS 续读信息并返回。 */
    suspend fun loadTtsResume(): TtsResume? =
        runCatching { settingsStore.loadTtsResume() }.getOrNull()

    // ── 内部辅助 ─────────────────────────────────────────────────
    private fun updateScreen(transform: (ReaderScreenState) -> ReaderScreenState) {
        _screenState.update(transform)
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    private fun nowIso(): String = Instant.now().toString()

    private fun openBook(bookId: String, force: Boolean) {
        activeBookId.value = bookId
        val current = _uiState.value
        if (!force && current.requestedBookId == bookId && (current.isLoading || current.isReady)) {
            return
        }

        loadJob?.cancel()
        val generation = loadGeneration.incrementAndGet()
        beginReaderOpenTrace(generation)
        release(current.loadedBook)
        _uiState.value = ReaderUiState(
            requestedBookId = bookId,
            isLoading = true,
        )

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            var loaded: ReaderLoadedBook? = null
            try {
                loaded = documentLoader.load(bookId)
                if (generation != loadGeneration.get()) {
                    release(loaded)
                    endReaderOpenTrace(generation)
                    return@launch
                }
                _uiState.value = ReaderUiState(
                    requestedBookId = bookId,
                    loadedBook = loaded,
                )
                loaded = null
                endReaderOpenTrace(generation)
            } catch (cancelled: CancellationException) {
                release(loaded)
                endReaderOpenTrace(generation)
                throw cancelled
            } catch (error: Throwable) {
                release(loaded)
                endReaderOpenTrace(generation)
                if (generation == loadGeneration.get()) {
                    _uiState.value = ReaderUiState(
                        requestedBookId = bookId,
                        errorMessage = error.message ?: "打开失败",
                    )
                }
            }
        }
    }

    private fun beginReaderOpenTrace(cookie: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        synchronized(readerOpenTraceLock) {
            activeReaderOpenTraceCookie?.let { Trace.endAsyncSection(READER_OPEN_TRACE, it) }
            Trace.beginAsyncSection(READER_OPEN_TRACE, cookie)
            activeReaderOpenTraceCookie = cookie
        }
    }

    private fun endReaderOpenTrace(cookie: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        synchronized(readerOpenTraceLock) {
            if (activeReaderOpenTraceCookie == cookie) {
                Trace.endAsyncSection(READER_OPEN_TRACE, cookie)
                activeReaderOpenTraceCookie = null
            }
        }
    }

    // ── 章节加载 ─────────────────────────────────────────────
    private fun loadChapter(bookId: String, chapterIndex: Int) {
        chapterLoadJob?.cancel()
        val gen = chapterLoadGeneration.incrementAndGet()
        _chapterLoadState.value = ChapterLoadResult.Loading(bookId, chapterIndex)
        chapterLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val book = _uiState.value.loadedBook ?: return@launch
                val epub = book.content as? ReaderLoadedContent.Epub ?: return@launch
                if (book.id != bookId) return@launch
                val blocks = epub.document.blocks(chapterIndex)
                if (gen != chapterLoadGeneration.get()) return@launch
                _chapterLoadState.value = ChapterLoadResult.Loaded(bookId, chapterIndex, blocks)
                // 保存 epub 进度
                val percent = if (epub.book.chapters.isEmpty()) {
                    0f
                } else {
                    ((chapterIndex + 1).toFloat() / epub.book.chapters.size) * 100f
                }
                epubRepository.saveProgress(bookId, chapterIndex, percent)
            } catch (_: CancellationException) {
                // stale load, ignore
            } catch (e: Exception) {
                if (gen == chapterLoadGeneration.get()) {
                    _chapterLoadState.value = ChapterLoadResult.Error(bookId, chapterIndex, e.message ?: "章节加载失败")
                }
            }
        }
    }

    /** 一次性加载章节块（供搜索/高亮定位等 UI 跳转场景使用，不改变当前章节状态）。 */
    suspend fun loadChapterBlocks(bookId: String, chapterIndex: Int): List<DocBlock> {
        val book = _uiState.value.loadedBook ?: return emptyList()
        val epub = book.content as? ReaderLoadedContent.Epub ?: return emptyList()
        if (book.id != bookId) return emptyList()
        return try {
            epub.document.blocks(chapterIndex)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 提取章节纯文本（供锚点解析等场景使用）。 */
    suspend fun extractChapterText(bookId: String, chapterIndex: Int): String {
        val book = _uiState.value.loadedBook ?: return ""
        val epub = book.content as? ReaderLoadedContent.Epub ?: return ""
        if (book.id != bookId) return ""
        return try {
            epub.document.text(chapterIndex)
        } catch (_: Exception) {
            ""
        }
    }

    // ── TXT 规则扫描 ──────────────────────────────────────────
    private fun scanTxtTocRule(filePath: String, ruleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                val newIndex = TxtFileScanner.scan(file, ruleId)
                val newDocument = PlainTextDocument.fromFileIndex(file, newIndex)
                _txtRuleScanResult.value = TxtRuleScanResult(
                    ruleId = ruleId,
                    fileIndex = newIndex,
                    document = newDocument,
                )
            } catch (e: Exception) {
                _txtRuleScanResult.value = TxtRuleScanResult(
                    ruleId = ruleId,
                    error = e.message ?: "切换目录规则失败",
                )
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        chapterLoadJob?.cancel()
        loadGeneration.incrementAndGet()
        chapterLoadGeneration.incrementAndGet()
        release(_uiState.value.loadedBook)
        _uiState.value = ReaderUiState()
        synchronized(readerOpenTraceLock) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activeReaderOpenTraceCookie?.let { Trace.endAsyncSection(READER_OPEN_TRACE, it) }
            }
            activeReaderOpenTraceCookie = null
        }
        super.onCleared()
    }

    private fun release(book: ReaderLoadedBook?) {
        book?.content?.release()
    }

    private companion object {
        const val READER_OPEN_TRACE = "ReaderOpen"
    }
}

private data class ReaderBookData(
    val highlights: List<HighlightEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val inspirations: List<InspirationEntity> = emptyList(),
    val sessions: List<ReadingSessionEntity> = emptyList(),
)

private data class ReaderTaxonomyData(
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
)

private data class ReaderAuxiliaryState(
    val txtTocRuleId: String = "builtin",
    val chapterLoadResult: ChapterLoadResult? = null,
    val txtRuleScanResult: TxtRuleScanResult? = null,
)

data class ReaderRouteUiState(
    val document: ReaderUiState = ReaderUiState(),
    val screen: ReaderScreenState = ReaderScreenState(),
    val highlights: List<HighlightEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val inspirations: List<InspirationEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val sessions: List<ReadingSessionEntity> = emptyList(),
    val txtTocRuleId: String = "builtin",
    val chapterLoadResult: ChapterLoadResult? = null,
    val txtRuleScanResult: TxtRuleScanResult? = null,
)
