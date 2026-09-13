package com.creationreadingassistant.ui.viewmodel

import android.os.Build
import android.os.Trace
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.ChapterReadRepository
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.data.repository.TaxonomyRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.data.settings.TtsResume
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.locator.AnchorCacheStore
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.feature.reader.doc.TxtScanMonitor
import com.creationreadingassistant.feature.reader.doc.TxtTocProfile
import com.creationreadingassistant.feature.reader.rules.BuiltinTocRules
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleEngine
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.feature.reader.session.ReadingActivity
import com.creationreadingassistant.feature.reader.session.ReadingSessionRecorder
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagerHealthStore
import com.creationreadingassistant.feature.reader.pager.ReaderPageIndexManager
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.library.deletion.BookDeletionCoordinator
import com.creationreadingassistant.ui.screen.reader.ReaderScreenState
import com.creationreadingassistant.ui.screen.reader.ReaderChromeEvent
import com.creationreadingassistant.ui.screen.reader.into
import com.creationreadingassistant.ui.screen.reader.readerChromeReducer
import com.creationreadingassistant.ui.screen.reader.toReaderChromeState
import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.data.repository.SearchIndexScheduler
import com.creationreadingassistant.feature.reader.rules.RuleDescriptor
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleScope
import kotlinx.coroutines.CoroutineScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
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
    private val noteRepository: NoteRepository,
    private val taxonomyRepository: TaxonomyRepository,
    private val bookRepository: BookRepository,
    private val deletions: BookDeletionCoordinator,
    private val chapterReadRepository: ChapterReadRepository,
    private val readingSessionRecorder: ReadingSessionRecorder,
    private val epubRepository: EpubRepository,
    val settingsStore: SettingsStore,
    val anchorCacheStore: AnchorCacheStore,
    val pageIndexStore: PageIndexStore,
    val pagerHealthStore: PagerHealthStore,
    val pageIndexManager: ReaderPageIndexManager,
    val aiClient: AiClient,
    private val rulesRepository: RulesRepository,
    private val searchIndexRepository: SearchIndexRepository,
    private val searchIndexScheduler: SearchIndexScheduler,
    @ApplicationScope private val appScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
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
    private val txtRuleScanCoordinator = TxtRuleScanCoordinator()

    // ── TXT 规则扫描状态（进度 / 取消 / 完成章数）──────────────
    private val _txtRuleScanStatus = MutableStateFlow<TxtRuleScanStatus>(TxtRuleScanStatus.Idle)
    val txtRuleScanStatus: StateFlow<TxtRuleScanStatus> = _txtRuleScanStatus.asStateFlow()
    private var txtScanJob: Job? = null

    /**
     * 测试 seam：默认走真实扫描器；测试可注入受控实现来验证取消 / 进度时序。
     */
    internal var txtFileScanner: (File, String, TxtScanMonitor?) -> TxtFileIndex =
        { file, ruleId, monitor -> TxtFileScanner.scan(file, ruleId, monitor) }

    /**
     * 测试 seam：按显式 [TxtTocProfile] 扫描（多规则目录重扫入口）。
     */
    internal var txtFileScannerProfile: (File, TxtTocProfile, TxtScanMonitor?) -> TxtFileIndex =
        { file, profile, monitor -> TxtFileScanner.scan(file, profile, monitor) }

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

    // ── 规则：当前书快照 + 最近一次写入反馈 ──────────────────────
    private val _ruleMutationResult = MutableStateFlow<RuleMutationResult?>(null)
    val ruleMutationResult: StateFlow<RuleMutationResult?> = _ruleMutationResult.asStateFlow()

    private val ruleData = activeBookId
        .flatMapLatest { bookId ->
            if (bookId.isBlank()) {
                // 无书身份：安全空快照，不触达 Room
                flowOf(RuleUiState())
            } else {
                combine(
                    rulesRepository.observe(bookId),
                    _ruleMutationResult,
                ) { snapshot, mutationResult ->
                    RuleUiState(snapshot = snapshot, mutationResult = mutationResult)
                }
            }
        }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    private val bookData = activeBookId
        .flatMapLatest { bookId ->
            if (bookId.isBlank()) {
                flowOf(ReaderBookData())
            } else {
                combine(
                    noteRepository.observeHighlightsByBook(bookId),
                    noteRepository.observeNotesByBook(bookId),
                    noteRepository.observeInspirationsByBook(bookId),
                    bookRepository.observeSessionsByBook(bookId),
                    chapterReadRepository.observeReadChapters(bookId),
                ) { highlights, notes, inspirations, sessions, readChapters ->
                    ReaderBookData(
                        highlights = highlights,
                        notes = notes,
                        inspirations = inspirations,
                        sessions = sessions,
                        readChapters = readChapters,
                    )
                }
            }
        }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    private val taxonomyData = screenState
        .map { state ->
            state.sheet == com.creationreadingassistant.ui.screen.reader.ReaderSheet.AI_EXPLAIN ||
                state.sheet == com.creationreadingassistant.ui.screen.reader.ReaderSheet.INSPIRATION
        }
        .distinctUntilChanged()
        .flatMapLatest { needed ->
            if (!needed) {
                flowOf(ReaderTaxonomyData())
            } else {
                combine(
                    taxonomyRepository.observeCategories(),
                    taxonomyRepository.observeTags(),
                ) { categories, tags -> ReaderTaxonomyData(categories, tags) }
            }
        }
        .distinctUntilChanged()

    private val auxiliaryState = combine(
        txtTocRuleId,
        chapterLoadState,
        txtRuleScanResult,
        txtRuleScanStatus,
    ) { tocRuleId, chapterLoad, ruleScan, ruleScanStatus ->
        ReaderAuxiliaryState(tocRuleId, chapterLoad, ruleScan, ruleScanStatus)
    }.distinctUntilChanged()

    /** 路由渲染的前 5 路状态源（typed combine 上限为 5，故先聚合再与规则状态合并）。 */
    private val routeSources = combine(
        uiState,
        screenState,
        bookData,
        taxonomyData,
        auxiliaryState,
    ) { document, screen, book, taxonomy, auxiliary ->
        ReaderRouteSources(document, screen, book, taxonomy, auxiliary)
    }

    val routeUiState: StateFlow<ReaderRouteUiState> = combine(
        routeSources,
        ruleData,
    ) { sources, rule ->
        ReaderRouteUiState(
            document = sources.document,
            screen = sources.screen,
            highlights = sources.book.highlights,
            notes = sources.book.notes,
            inspirations = sources.book.inspirations,
            sessions = sources.book.sessions,
            readChapters = sources.book.readChapters,
            categories = sources.taxonomy.categories,
            tags = sources.taxonomy.tags,
            txtTocRuleId = sources.auxiliary.txtTocRuleId,
            chapterLoadResult = sources.auxiliary.chapterLoadResult,
            txtRuleScanResult = sources.auxiliary.txtRuleScanResult,
            txtRuleScanStatus = sources.auxiliary.txtRuleScanStatus,
            ruleSnapshot = rule.snapshot,
            ruleMutationResult = rule.mutationResult,
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderRouteUiState(),
        )

    // ── Action 路由 ──────────────────────────────────────────────
    @Suppress("DEPRECATION") // ScanTxtTocRule 分支：死路径保留期，仅测试驱动
    fun onAction(action: ReaderAction) {
        when (action) {
            // 文档协调
            is ReaderAction.OpenBook -> openBook(action.bookId, force = false)
            is ReaderAction.Retry -> openBook(_uiState.value.requestedBookId, force = true)
            is ReaderAction.LoadChapter -> loadChapter(action.bookId, action.chapterIndex, action.persistProgress)
            is ReaderAction.ScanTxtTocRule -> scanTxtTocRule(action.bookId, action.filePath, action.ruleId)
            is ReaderAction.RescanTxtToc -> rescanTxtToc(action.bookId, action.filePath, action.profileKeyHint)
            is ReaderAction.CancelTxtTocScan -> cancelTxtTocScan()

            // UI 状态：chrome 显隐统一走 ReaderChromeReducer 状态机
            is ReaderAction.ToggleControls -> {
                val event = when {
                    action.visible == null -> ReaderChromeEvent.CenterTap
                    action.visible == true -> ReaderChromeEvent.Enter
                    else -> ReaderChromeEvent.PageTurn
                }
                updateScreen {
                    readerChromeReducer(it.toReaderChromeState(), event).into(it)
                        .copy(
                            selectedText = if (event == ReaderChromeEvent.CenterTap && it.selectedText.isNotBlank()) "" else it.selectedText,
                            selectedRangeStart = if (event == ReaderChromeEvent.CenterTap && it.selectedText.isNotBlank()) -1 else it.selectedRangeStart,
                        )
                }
            }
            is ReaderAction.PageTurn -> updateScreen {
                readerChromeReducer(it.toReaderChromeState(), ReaderChromeEvent.PageTurn).into(it)
            }
            is ReaderAction.ProgressScrubberInteractionStarted -> updateScreen {
                readerChromeReducer(
                    it.toReaderChromeState(),
                    ReaderChromeEvent.ProgressScrubberInteractionStarted,
                ).into(it)
            }
            is ReaderAction.AutoHideElapsed -> updateScreen {
                readerChromeReducer(
                    it.toReaderChromeState(),
                    ReaderChromeEvent.AutoHideElapsed(action.autoHideSeconds),
                ).into(it)
            }
            is ReaderAction.OpenSheet -> updateScreen {
                readerChromeReducer(it.toReaderChromeState(), ReaderChromeEvent.SheetOpen).into(it)
                    .copy(sheet = action.sheet)
            }
            is ReaderAction.CloseSheet -> updateScreen {
                readerChromeReducer(it.toReaderChromeState(), ReaderChromeEvent.SheetClose).into(it)
                    .copy(sheet = null)
            }
            is ReaderAction.SetSelectedText -> updateScreen {
                it.copy(
                    selectedText = action.text,
                    selectedRangeStart = action.rangeStart,
                    selectedGlobalOffset = action.globalOffset,
                    selectedSourceEnd = action.sourceEnd,
                )
            }
            is ReaderAction.ClearSelection -> updateScreen {
                it.copy(
                    selectedText = "",
                    selectedRangeStart = -1,
                    selectedGlobalOffset = -1,
                    selectedSourceEnd = -1,
                    showColorRow = false,
                )
            }
            is ReaderAction.SetShowTts -> updateScreen { it.copy(showTts = action.show) }
            is ReaderAction.SetSearchQuery -> updateScreen { it.copy(searchQuery = action.query) }
            is ReaderAction.SetShowOverflow -> updateScreen { it.copy(showReaderOverflow = action.show) }
            is ReaderAction.SetNoteOpen -> updateScreen { it.copy(noteOpen = action.open) }
            is ReaderAction.SetNoteBody -> updateScreen { it.copy(noteBody = action.body) }
            is ReaderAction.ToggleColorRow -> updateScreen { it.copy(showColorRow = !it.showColorRow) }

            // 数据写入（全部 IO 协程；高亮/笔记/灵感落库细节在 NoteRepository）
            is ReaderAction.SaveHighlight -> io { noteRepository.saveHighlight(action.highlight) }
            is ReaderAction.DeleteHighlight -> io { noteRepository.deleteHighlight(action.highlightId) }
            is ReaderAction.UpdateHighlightColor -> io {
                noteRepository.updateHighlightColor(action.highlightId, action.color)
            }
            is ReaderAction.UpdateHighlightNote -> io {
                noteRepository.updateHighlightNote(action.highlightId, action.note)
            }
            is ReaderAction.SaveNote -> io { noteRepository.saveNote(action.note) }
            is ReaderAction.DeleteNote -> io { noteRepository.deleteNote(action.noteId) }
            is ReaderAction.AddBookmark -> io {
                noteRepository.addBookmark(
                    bookId = action.bookId,
                    title = action.title,
                    offset = action.offset,
                    absOffset = action.absOffset,
                    chapterIndex = action.chapterIndex,
                    charOffset = action.charOffset,
                    locatorJson = action.locatorJson,
                )
            }
            is ReaderAction.ConvertHighlightToNote -> io {
                noteRepository.convertHighlightToNote(action.highlightId)
            }
            is ReaderAction.ConvertHighlightToInspiration -> io {
                noteRepository.convertHighlightToInspiration(action.highlightId)
            }
            is ReaderAction.SaveInspiration -> io { noteRepository.saveInspiration(action.inspiration) }
            is ReaderAction.CreateCategory -> io {
                val id = "mobile-category-${java.util.UUID.randomUUID()}"
                taxonomyRepository.upsertCategory(CategoryEntity(id = id, name = action.name, created_at = nowIso(), updated_at = nowIso()))
            }
            is ReaderAction.CreateTag -> io {
                val id = "mobile-tag-${java.util.UUID.randomUUID()}"
                taxonomyRepository.upsertTag(TagEntity(id = id, name = action.name, type = "inspiration", created_at = nowIso(), updated_at = nowIso()))
            }
            is ReaderAction.SaveProgress -> io {
                bookRepository.saveProgress(action.progress)
            }
            is ReaderAction.UpdateReadingActivity -> readingSessionRecorder.update(
                ReadingActivity(
                    bookId = action.bookId,
                    active = action.active,
                    progressPercent = action.progressPercent,
                ),
            )
            is ReaderAction.SaveEpubProgress -> io {
                epubRepository.saveProgress(
                    bookId = action.bookId,
                    chapterIndex = action.chapterIndex,
                    percent = action.percent,
                    offsetInChapter = action.offsetInChapter,
                    absoluteOffset = action.absoluteOffset,
                )
            }
            is ReaderAction.DeleteBook -> io { deletions.deleteBook(action.bookId) }
            is ReaderAction.ClearChapterReads -> io {
                chapterReadRepository.clearForBook(action.bookId)
            }

            // 规则写入（RulesRepository；校验/迁移失败不落库，结果原样发布）
            is ReaderAction.ExecuteRuleCommand -> io {
                // 单处纠错（E2）：命令不带坐标，锚点在执行前从当前选区状态回查填充，
                // 与 ReaderScaffold/ReaderProgressActions 的字段判别式保持一致
                // （TXT 用 selectedRangeStart，EPUB 用 selectedGlobalOffset，终点统一 selectedSourceEnd）。
                val command: RuleCommand? = when {
                    action.command !is RuleCommand.SaveSingleCorrection -> action.command
                    else -> {
                        val anchor = currentSelectionSourceRange()
                        val draft = action.command
                        when {
                            anchor == null -> {
                                _ruleMutationResult.value = RuleMutationResult.NotAnchorable(
                                    "当前没有可用的选区位置，请先在正文中长按选中要纠错的文字",
                                )
                                null
                            }
                            draft.findText != _screenState.value.selectedText -> {
                                _ruleMutationResult.value = RuleMutationResult.NotAnchorable(
                                    "单处纠错只作用于当前选区文字；如需修改查找内容，请改用本书替换",
                                )
                                null
                            }
                            else -> RuleCommand.SaveSingleCorrection(
                                sourceStart = anchor.first,
                                sourceEnd = anchor.second,
                                findText = draft.findText,
                                replaceText = draft.replaceText,
                            )
                        }
                    }
                }
                if (command == null) return@io
                // 受影响范围必须在 execute **之前**判定：删除类命令执行后规则行已不在。
                val indexPlan = searchIndexRefreshPlan(command)
                val result = rulesRepository.execute(action.bookId, command)
                if (result is RuleMutationResult.Migrated) {
                    // 快速单选 / 迁移：同步旧单选显示 id，并发布结果供统一重扫策略消费
                    _txtTocRuleId.value = result.effectiveRuleId
                }
                _ruleMutationResult.value = result
                if (indexPlan != null &&
                    (result is RuleMutationResult.Success || result is RuleMutationResult.Saved)
                ) {
                    applySearchIndexRefresh(action.bookId, indexPlan)
                }
            }
            is ReaderAction.ClearRuleMutationResult -> _ruleMutationResult.value = null

            // 设置
            is ReaderAction.LoadTxtTocRule -> io {
                val legacy = settingsStore.loadTxtTocRule(action.bookId)
                if (settingsStore.isTxtTocRuleMigrated(action.bookId)) {
                    // 已迁移：Room 是唯一状态源，旧值只作显示参考，不再写回
                    val key = rulesRepository.observe(action.bookId).first().effectiveTocProfile.key
                    _txtTocRuleId.value = if (
                        key == BuiltinTocRules.STANDARD_ID || key in BuiltinTocRules.byId
                    ) {
                        key
                    } else {
                        BuiltinTocRules.STANDARD_ID
                    }
                } else {
                    val migrated = rulesRepository.execute(
                        action.bookId,
                        RuleCommand.MigrateLegacyTocRule(legacy),
                    )
                    settingsStore.markTxtTocRuleMigrated(action.bookId)
                    _txtTocRuleId.value = (migrated as? RuleMutationResult.Migrated)?.effectiveRuleId
                        ?: BuiltinTocRules.STANDARD_ID
                }
            }
            is ReaderAction.SaveTxtTocRule -> io {
                settingsStore.saveTxtTocRule(action.bookId, action.ruleId)
            }
            is ReaderAction.SaveTtsResume -> io {
                settingsStore.saveTtsResume(action.bookId, action.chapterIndex, action.offset)
            }
        }
    }

    // ── 规则变更 → 搜索索引刷新（P1）────────────────────────────────────

    /** 搜索结果刷新方案；[global] 表示该变更影响全库（GLOBAL 作用域）。 */
    private data class SearchIndexRefreshPlan(val global: Boolean)

    /**
     * 判定一条规则命令是否需要刷新搜索索引、是否影响全库。
     *
     * 只有**替换（REPLACE）规则**会改变索引内容：搜索索引按内置 TOC 分章、按生效替换规则
     * 投影显示文通道，故替换规则一变旧 display 行即失真；TOC 规则不参与索引分章，不影响索引。
     *
     * 必须在 [RulesRepository.execute] **之前**调用 —— 删除类命令执行后规则行已消失，
     * 无从再查其类型 / 作用域。
     */
    private suspend fun searchIndexRefreshPlan(command: RuleCommand): SearchIndexRefreshPlan? =
        when (command) {
            is RuleCommand.SaveCustomReplace ->
                SearchIndexRefreshPlan(global = command.scope == RuleScope.GLOBAL)
            is RuleCommand.ToggleCustom -> replaceRulePlan(command.ruleId)
            is RuleCommand.DeleteCustom -> replaceRulePlan(command.ruleId)
            // 单处纠错只影响本书 display 文本：保存/撤销/恢复都重建本书索引。
            is RuleCommand.SaveSingleCorrection,
            is RuleCommand.UndoCorrection,
            is RuleCommand.RestoreCorrection,
            -> SearchIndexRefreshPlan(global = false)
            else -> null
        }

    /**
     * 当前选区的全书 source 半开区间；无可锚定选区返回 null。
     *
     * 分页投影路径两种来源：TXT 选区起点在 [ReaderScreenState.selectedRangeStart]，
     * EPUB 选区起点在 [ReaderScreenState.selectedGlobalOffset]；终点统一在
     * selectedSourceEnd（旧路径 -1 表示不可锚定）。判别式与
     * ReaderScaffold / ReaderProgressActions 既有消费点一致。
     */
    private fun currentSelectionSourceRange(): Pair<Int, Int>? {
        val state = _screenState.value
        val end = state.selectedSourceEnd
        if (end < 0) return null
        val start = when {
            state.selectedRangeStart >= 0 -> state.selectedRangeStart
            state.selectedGlobalOffset >= 0 -> state.selectedGlobalOffset
            else -> return null
        }
        return if (start in 0 until end) start to end else null
    }

    private suspend fun replaceRulePlan(ruleId: String): SearchIndexRefreshPlan? {
        val descriptor: RuleDescriptor = rulesRepository.describeRule(ruleId) ?: return null
        return if (descriptor.kind == RuleKind.REPLACE) {
            SearchIndexRefreshPlan(global = descriptor.scope == RuleScope.GLOBAL)
        } else {
            null
        }
    }

    /**
     * 执行索引刷新：PER_BOOK 立即重建当前书；GLOBAL 置脏 + 后台 worker 惰性重建全库。
     *
     * 一律放到应用级作用域，绝不在阅读器交互路径上同步重建
     * （全库重建可达数十分钟 / 数 GB，见索引构建真机量化）。
     */
    private fun applySearchIndexRefresh(bookId: String, plan: SearchIndexRefreshPlan) {
        appScope.launch {
            runCatching {
                if (plan.global) {
                    searchIndexRepository.invalidateSweepForFullRebuild()
                    searchIndexScheduler.enqueueOnce()
                } else {
                    searchIndexRepository.reindexBookById(bookId)
                }
            }.onFailure {
                AppLog.e(
                    "SearchIndex",
                    "规则变更后刷新索引失败（book=${bookId.take(8)}，global=${plan.global}）：" +
                        (it.message ?: it.javaClass.simpleName),
                )
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
        viewModelScope.launch(ioDispatcher) { block() }
    }

    private fun nowIso(): String = com.creationreadingassistant.ui.util.nowIso()

    private fun openBook(bookId: String, force: Boolean) {
        val current = _uiState.value
        val sameBookNoop = !force && current.requestedBookId == bookId && (current.isLoading || current.isReady)
        if (sameBookNoop) {
            // 同书冗余打开：不切换书身份，不得作废该书合法的在途 TXT 规则扫描
            return
        }
        // 书内搜索查询按书持有：书身份变化（切书）清空旧 query，避免旧词自动搜索新书；
        // 同书重开/force 重载不清空——只有用户清空查询或切书才清空。
        if (current.requestedBookId != bookId) {
            // 切书重置 chrome：菜单回到可见（用户反馈 4）；同时清空旧书搜索词。
            updateScreen {
                readerChromeReducer(it.toReaderChromeState(), ReaderChromeEvent.BookSwitched).into(it)
                    .copy(searchQuery = "")
            }
            // 切书清空旧书规则写入反馈，避免新书会话展示上一本书的结果
            _ruleMutationResult.value = null
        }
        activeBookId.value = bookId
        // P1-A：切书后，上一本书在途的规则扫描一律失效，迟到结果不得发布
        txtRuleScanCoordinator.invalidate()
        // 切书同时取消旧扫描 Job，状态不残留到新书会话
        txtScanJob?.cancel()
        txtScanJob = null
        _txtRuleScanStatus.value = TxtRuleScanStatus.Idle

        loadJob?.cancel()
        val generation = loadGeneration.incrementAndGet()
        beginReaderOpenTrace(generation)
        release(current.loadedBook)
        _uiState.value = ReaderUiState(
            requestedBookId = bookId,
            isLoading = true,
        )

        loadJob = viewModelScope.launch(ioDispatcher) {
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
                val initialReadChapter = when (val content = loaded.content) {
                    is ReaderLoadedContent.Epub -> content.initialChapterIndex
                    is ReaderLoadedContent.Markdown -> content.document.chapters
                        .indexOfLast { it.startOffset <= content.initialAbsoluteOffset }
                        .coerceAtLeast(0)
                    is ReaderLoadedContent.Text -> null
                }
                loaded = null
                endReaderOpenTrace(generation)
                if (initialReadChapter != null) {
                    runCatching { chapterReadRepository.markRead(bookId, initialReadChapter) }
                        .onFailure { AppLog.w("ReaderVM", "mark initial chapter read failed: ${it.message}") }
                }
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
    private fun loadChapter(bookId: String, chapterIndex: Int, persistProgress: Boolean = true) {
        chapterLoadJob?.cancel()
        val gen = chapterLoadGeneration.incrementAndGet()
        _chapterLoadState.value = ChapterLoadResult.Loading(bookId, chapterIndex)
        chapterLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                val book = _uiState.value.loadedBook ?: return@launch
                if (book.id != bookId) return@launch
                val blocks = when (val content = book.content) {
                    is ReaderLoadedContent.Epub -> content.document.blocks(chapterIndex)
                    is ReaderLoadedContent.Markdown -> content.document.blocks(chapterIndex)
                    is ReaderLoadedContent.Text -> return@launch
                }
                if (gen != chapterLoadGeneration.get()) return@launch
                _chapterLoadState.value = ChapterLoadResult.Loaded(bookId, chapterIndex, blocks)
                runCatching { chapterReadRepository.markRead(bookId, chapterIndex) }
                    .onFailure { AppLog.w("ReaderVM", "mark chapter read failed: ${it.message}") }
                // 保存 epub 进度（章号即位置的 legacy 语义）。
                // persistProgress=false：分页引擎的被动跨章同步——真实落点可能是上一章
                // 末页而非章首，这里若写 offsetInChapter=0 的章首 locator（merge 无条件
                // 采信 incoming）会在退出重进时把用户拽回前一章开头；精确进度由分页
                // 侧的防抖保存落库。
                if (persistProgress && book.content is ReaderLoadedContent.Epub) {
                    val epub = book.content
                    val percent = if (epub.book.chapters.isEmpty()) {
                        0f
                    } else {
                        ((chapterIndex + 1).toFloat() / epub.book.chapters.size) * 100f
                    }
                    epubRepository.saveProgress(bookId, chapterIndex, percent)
                }
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
        if (book.id != bookId) return emptyList()
        val document = when (val content = book.content) {
            is ReaderLoadedContent.Epub -> content.document
            is ReaderLoadedContent.Markdown -> content.document
            is ReaderLoadedContent.Text -> return emptyList()
        }
        return try {
            document.blocks(chapterIndex)
        } catch (e: Exception) {
            AppLog.w("ReaderVM", "loadChapterBlocks failed: ${e.message}")
            emptyList()
        }
    }

    /** 提取章节纯文本（供锚点解析等场景使用）。 */
    suspend fun extractChapterText(bookId: String, chapterIndex: Int): String {
        val book = _uiState.value.loadedBook ?: return ""
        if (book.id != bookId) return ""
        val document = when (val content = book.content) {
            is ReaderLoadedContent.Epub -> content.document
            is ReaderLoadedContent.Markdown -> content.document
            is ReaderLoadedContent.Text -> return ""
        }
        return try {
            document.text(chapterIndex)
        } catch (e: Exception) {
            AppLog.w("ReaderVM", "extractChapterText failed: ${e.message}")
            ""
        }
    }

    // ── TXT 规则扫描 ──────────────────────────────────────────
    /**
     * 死路径（任务 #15 小清理）：生产零派发，TOC 规则切换已改走
     * ExecuteRuleCommand + [rescanTxtToc]。保留实现仅为扫描状态机
     * （取消/竞争/进度）的测试驱动入口，与 [rescanTxtToc] 共用同一套
     * txtScanJob / txtRuleScanCoordinator / 状态发布链路。
     */
    private fun scanTxtTocRule(bookId: String, filePath: String, ruleId: String) {
        // 新请求立即取消旧 Job；阻塞中的旧扫描靠 monitor 探针协作退出
        txtScanJob?.cancel()
        val requestId = txtRuleScanCoordinator.begin(bookId, ruleId)
        _txtRuleScanStatus.value = TxtRuleScanStatus.Running(bookId, ruleId, progress = null)
        txtScanJob = viewModelScope.launch(ioDispatcher) {
            try {
                val file = File(filePath)
                val monitor = object : TxtScanMonitor {
                    override fun isCancelled(): Boolean =
                        !txtRuleScanCoordinator.isCurrent(bookId, ruleId, requestId)

                    override fun onProgress(fraction: Float?) {
                        // 迟到进度（取消 / 切书 / 换规则后）一律不发布
                        if (txtRuleScanCoordinator.isCurrent(bookId, ruleId, requestId)) {
                            _txtRuleScanStatus.value = TxtRuleScanStatus.Running(bookId, ruleId, fraction)
                        }
                    }
                }
                val newIndex = txtFileScanner(file, ruleId, monitor)
                if (!txtRuleScanCoordinator.isCurrent(bookId, ruleId, requestId)) return@launch
                val newDocument = PlainTextDocument.fromFileIndex(file, newIndex)
                if (!txtRuleScanCoordinator.isCurrent(bookId, ruleId, requestId)) return@launch
                _txtRuleScanResult.value = TxtRuleScanResult(
                    bookId = bookId,
                    ruleId = ruleId,
                    requestId = requestId,
                    fileIndex = newIndex,
                    document = newDocument,
                )
                _txtRuleScanStatus.value = TxtRuleScanStatus.Completed(bookId, ruleId, newIndex.chapters.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (!txtRuleScanCoordinator.isCurrent(bookId, ruleId, requestId)) return@launch
                _txtRuleScanResult.value = TxtRuleScanResult(
                    bookId = bookId,
                    ruleId = ruleId,
                    requestId = requestId,
                    error = e.message ?: "切换目录规则失败",
                )
                _txtRuleScanStatus.value = TxtRuleScanStatus.Failed(
                    bookId = bookId,
                    ruleId = ruleId,
                    message = e.message ?: "切换目录规则失败",
                )
            }
        }
    }

    /**
     * 多规则目录重扫（P1-A）：规则写入成功后重新识别当前流式 TXT。
     *
     * - 权威身份来自 Room 生效规则（effectiveToc → TxtTocProfile.key）；
     * - [profileKeyHint] 仅作竞态校验：与权威 key 不一致说明规则又变了 / UI
     *   快照过期，直接丢弃本次请求；
     * - 协程启动时再核对当前书身份：切书（openBook 已 invalidate）后晚到的
     *   请求不得重新激活旧书，防止跨书发布。
     */
    private fun rescanTxtToc(bookId: String, filePath: String, profileKeyHint: String) {
        txtScanJob?.cancel()
        txtScanJob = viewModelScope.launch(ioDispatcher) {
            var requestId = 0L
            var activeKey: String? = null
            try {
                if (activeBookId.value != bookId) return@launch
                val snapshot = rulesRepository.observe(bookId).first()
                val profile = RuleEngine.tocProfile(snapshot.effectiveToc)
                if (profile.key != profileKeyHint) return@launch

                activeKey = profile.key
                requestId = txtRuleScanCoordinator.begin(bookId, profile.key)
                _txtRuleScanStatus.value = TxtRuleScanStatus.Running(bookId, profile.key, progress = null)
                val file = File(filePath)
                val monitor = object : TxtScanMonitor {
                    override fun isCancelled(): Boolean =
                        !txtRuleScanCoordinator.isCurrent(bookId, profile.key, requestId)

                    override fun onProgress(fraction: Float?) {
                        if (txtRuleScanCoordinator.isCurrent(bookId, profile.key, requestId)) {
                            _txtRuleScanStatus.value = TxtRuleScanStatus.Running(bookId, profile.key, fraction)
                        }
                    }
                }
                val newIndex = txtFileScannerProfile(file, profile, monitor)
                if (!txtRuleScanCoordinator.isCurrent(bookId, profile.key, requestId)) return@launch
                val newDocument = PlainTextDocument.fromFileIndex(file, newIndex)
                if (!txtRuleScanCoordinator.isCurrent(bookId, profile.key, requestId)) return@launch
                _txtRuleScanResult.value = TxtRuleScanResult(
                    bookId = bookId,
                    ruleId = profile.key,
                    requestId = requestId,
                    fileIndex = newIndex,
                    document = newDocument,
                )
                _txtRuleScanStatus.value = TxtRuleScanStatus.Completed(bookId, profile.key, newIndex.chapters.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                val key = activeKey ?: return@launch
                if (!txtRuleScanCoordinator.isCurrent(bookId, key, requestId)) return@launch
                _txtRuleScanResult.value = TxtRuleScanResult(
                    bookId = bookId,
                    ruleId = key,
                    requestId = requestId,
                    error = e.message ?: "切换目录规则失败",
                )
                _txtRuleScanStatus.value = TxtRuleScanStatus.Failed(
                    bookId = bookId,
                    ruleId = key,
                    message = e.message ?: "切换目录规则失败",
                )
            }
        }
    }

    /** 显式取消当前 TXT 规则扫描。 */
    private fun cancelTxtTocScan() {
        val inFlight = txtScanJob?.isActive == true
        txtScanJob?.cancel()
        txtScanJob = null
        val active = txtRuleScanCoordinator.invalidate()
        _txtRuleScanStatus.value = if (inFlight && active != null) {
            TxtRuleScanStatus.Cancelled(active.first, active.second)
        } else {
            TxtRuleScanStatus.Idle
        }
        // 不发布 result：原 document/fileIndex 保持不变，pending anchor 不被消费
    }

    override fun onCleared() {
        readingSessionRecorder.update(
            ReadingActivity(
                bookId = _uiState.value.requestedBookId,
                active = false,
                progressPercent = null,
            ),
        )
        loadJob?.cancel()
        chapterLoadJob?.cancel()
        txtScanJob?.cancel()
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
    val readChapters: List<Int> = emptyList(),
)

private data class ReaderRouteSources(
    val document: ReaderUiState,
    val screen: ReaderScreenState,
    val book: ReaderBookData,
    val taxonomy: ReaderTaxonomyData,
    val auxiliary: ReaderAuxiliaryState,
)

private data class ReaderTaxonomyData(
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
)

private data class ReaderAuxiliaryState(
    val txtTocRuleId: String = "builtin",
    val chapterLoadResult: ChapterLoadResult? = null,
    val txtRuleScanResult: TxtRuleScanResult? = null,
    val txtRuleScanStatus: TxtRuleScanStatus = TxtRuleScanStatus.Idle,
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
    val readChapters: List<Int> = emptyList(),
    val txtTocRuleId: String = "builtin",
    val chapterLoadResult: ChapterLoadResult? = null,
    val txtRuleScanResult: TxtRuleScanResult? = null,
    val txtRuleScanStatus: TxtRuleScanStatus = TxtRuleScanStatus.Idle,
    val ruleSnapshot: RuleSnapshot = RuleSnapshot.empty(),
    val ruleMutationResult: RuleMutationResult? = null,
)
