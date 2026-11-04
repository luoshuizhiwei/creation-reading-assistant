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
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.feature.reader.doc.TxtScanMonitor
import com.creationreadingassistant.feature.reader.doc.TxtTocProfile
import com.creationreadingassistant.feature.reader.rules.BuiltinTocRules
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.feature.reader.session.ReadingActivity
import com.creationreadingassistant.feature.reader.session.ReadingSessionRecorder
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagedReplacementAvailability
import com.creationreadingassistant.feature.reader.pager.PagerHealthStore
import com.creationreadingassistant.feature.reader.pager.ReaderPageIndexManager
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.library.deletion.BookDeletionCoordinator
import com.creationreadingassistant.ui.screen.reader.ReaderScreenState
import com.creationreadingassistant.ui.screen.reader.ReaderChromeEvent
import com.creationreadingassistant.ui.screen.reader.ReaderReplacementCapability
import com.creationreadingassistant.ui.screen.reader.consumesSelection
import com.creationreadingassistant.ui.screen.reader.into
import com.creationreadingassistant.ui.screen.reader.readerChromeReducer
import com.creationreadingassistant.ui.screen.reader.readerReplacementCapability
import com.creationreadingassistant.ui.screen.reader.toReaderChromeState
import com.creationreadingassistant.ui.screen.reader.withSelectionCaptured
import com.creationreadingassistant.ui.screen.reader.withSelectionSnapshotReleased
import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.data.repository.ReaderProgressSaveOrder
import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.data.repository.SearchIndexScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 选区状态的唯一「完整清除」语义（S07-F2）。
 *
 * 五个字段必须**一起**复位，任何一处单独清理都会留下不一致状态：
 * - `selectedText` 是工具条可见性的唯一门控，漏清会让工具条挂在新页面/新章节上；
 * - `selectedRangeStart`（TXT/滚动起点）与 `selectedGlobalOffset`（EPUB 分页起点）
 *   是互斥的两种起点口径，只清其中一个会留下另一条路径的陈旧坐标；
 * - `selectedSourceEnd` 是两端共用的半开终点，`ReaderScaffold` 的
 *   `selectedSourceLength` 正是用它减去上面两个起点之一 —— 半清状态会算出跨字段错值；
 * - `showColorRow` 是工具条的二级视图，漏收会让下一次长按直接落在色板行而不是动作行。
 *
 * 调用方（`ClearSelection` / `PageTurn` / `LoadChapter` / 中央点击收起）只表达
 * 「选区已失效」这一个意图，字段清单只在本函数里维护。
 */
internal fun ReaderScreenState.withSelectionCleared(): ReaderScreenState = copy(
    selectedText = "",
    selectedRangeStart = -1,
    selectedGlobalOffset = -1,
    selectedSourceEnd = -1,
    showColorRow = false,
)

/**
 * S07-F4.1：ViewModel 侧单处纠错纵深防御所消费的**唯一事实源**。
 *
 * [bookId] 是发布时的书身份。带上它，才可能回答「这条裁决还算不算当前书的事实」——
 * 只看 [availability] 会让切书后的迟到发布把上一本书的裁决留在新书会话里。
 */
internal data class PublishedReplacementAvailability(
    val bookId: String,
    val availability: PagedReplacementAvailability,
)

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

    // ── 阅读进度保存的完成边界（CRA-READER-EXIT-SAVE-DRAIN-v1）──────────────
    // 只登记阅读进度保存（SaveProgress / SaveEpubProgress）：笔记/标签/规则等其它 io 写
    // 不在此记账，退出边界也不等它们。登记发生在 launch **之前**，因此"刚派发"不会被判为空。
    private val progressSaveStateLock = Any()
    private var progressSavesInFlight = 0
    private var progressSaveSeq = 0L
    /** 每本书最近一次**未解决**的失败：保留其派发序号，用来界定"哪次成功才能覆盖它"。 */
    private val progressSaveFailures = HashMap<String, ProgressSaveFailure>()
    /** 每本书最近一次正常完成的保存序号：更旧的失败晚到时据此判定"已被更晚的成功覆盖"。 */
    private val progressSaveSuccessSeq = HashMap<String, Long>()
    private val progressSavesIdle = MutableStateFlow(true)

    private class ProgressSaveFailure(val seq: Long, val throwable: Throwable)

    // ── 协同模块委托 ──────────────────────────────────────────────
    private val chapterLoaderCoordinator = ReaderChapterLoaderCoordinator()
    private val txtScanCoordinator = ReaderTxtScanCoordinator()

    val chapterLoadState: StateFlow<ChapterLoadResult?> get() = chapterLoaderCoordinator.chapterLoadState
    val txtRuleScanResult: StateFlow<TxtRuleScanResult?> get() = txtScanCoordinator.txtRuleScanResult
    val txtRuleScanStatus: StateFlow<TxtRuleScanStatus> get() = txtScanCoordinator.txtRuleScanStatus

    /**
     * 测试 seam：默认走真实扫描器；测试可注入受控实现来验证取消 / 进度时序。
     */
    internal var txtFileScanner: (File, String, TxtScanMonitor?) -> TxtFileIndex
        get() = txtScanCoordinator.txtFileScanner
        set(value) { txtScanCoordinator.txtFileScanner = value }

    /**
     * 测试 seam：按显式 [TxtTocProfile] 扫描（多规则目录重扫入口）。
     */
    internal var txtFileScannerProfile: (File, TxtTocProfile, TxtScanMonitor?) -> TxtFileIndex
        get() = txtScanCoordinator.txtFileScannerProfile
        set(value) { txtScanCoordinator.txtFileScannerProfile = value }

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

    /**
     * S07-F4.1：**生产层发布的替换能力裁决**，单处纠错纵深防御的唯一事实源。
     *
     * 由 UI 侧的 `LaunchedEffect(pagerEngine.replacementAvailability)` 经
     * [ReaderAction.SetReplacementAvailability] 同步进来 —— 与工具栏摘除
     * 「替换」槽位、规则页门控读的是**同一个** `PagedReplacementAvailability`
     * 实例来源（`effectiveReplacementAvailability`）。
     *
     * 为什么不再按 [ReaderLoadedContent] 类型猜：EPUB 分页路径的引擎裁决可以是
     * `APPLIED / NO_EFFECTIVE_RULES / PARTIALLY_APPLIED / ALL_SCOPES_OVERSIZED /
     * UNVERIFIED_CHAPTER_LENGTHS`（全部 `Available`），只有 legacy/非分页路径才被
     * 遮蔽成 `ESTIMATED_COORDINATES`。「是不是 EPUB」推不出「坐标能不能当锚点」。
     *
     * 初值与切书时一律为 null ⇒ 按 `SOURCE_UNAVAILABLE` 拒绝，绝不沿用上一本书的裁决。
     */
    private val _replacementAvailability = MutableStateFlow<PublishedReplacementAvailability?>(null)

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
        // 首发射兜底：combine 需所有源至少发射一次才发布；新书首开时 Room 查询可能被
        // 导入重活饿住数十秒，期间 routeUiState 停在初始值 → isLoading 传不到 UI（白屏、
        // 转圈迟到）。onStart 先补发空快照解锁 combine，真实数据到达后自然覆盖。
        .onStart { emit(RuleUiState()) }
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
        // 首发射兜底（同 ruleData）：先补发空数据解锁外层 combine，让 document 的
        // isLoading/错误状态立即可达 UI；高亮/笔记等真实数据晚到后自动覆盖。
        .onStart { emit(ReaderBookData()) }
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
            is ReaderAction.LoadChapter -> {
                // S07-F2：章节身份变化 → 旧选区的章内/全书坐标全部失效。
                // 目录选择、上一章/下一章、搜索跳转、高亮回源都经 goToChapter 落到这里，
                // 因此清除只在这一处收敛，调用方不需要各自记字段清单。
                updateScreen { it.withSelectionCleared() }
                loadChapter(action.bookId, action.chapterIndex, action.persistProgress)
            }
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
                    val next = readerChromeReducer(it.toReaderChromeState(), event).into(it)
                    // S07-F2：中央点击收起菜单时，若正有选区则一并完整清除。
                    // 旧实现只清 text 与 rangeStart，留下 globalOffset / sourceEnd /
                    // showColorRow，形成跨字段的半清状态。
                    if (event == ReaderChromeEvent.CenterTap && it.selectedText.isNotBlank()) {
                        next.withSelectionCleared()
                    } else {
                        next
                    }
                }
            }
            is ReaderAction.PageTurn -> updateScreen {
                // S07-F2：页面身份变化 → 选区失效。手势翻页（PageTurner 的
                // onPageTurned）、硬件键翻页（ReaderSessionEffects 在消费成功后派发）、
                // 自动翻页与外部翻页请求都在提交页码后走到这里；宿主侧的本地 selRange
                // 由 selectionCleared 回流撤掉，但 VM 里的旧文本与旧坐标必须在这里清，
                // 否则工具条会继续引用上一页的文字，保存高亮/笔记即落错位置。
                readerChromeReducer(it.toReaderChromeState(), ReaderChromeEvent.PageTurn)
                    .into(it)
                    .withSelectionCleared()
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
                // S07-F2.4：打开长生命周期选区消费者时，**原子**捕获当前四字段为不可变快照。
                // 之后的 ClearSelection / PageTurn / ReadingUnit dispose / 正文新选区都不会
                // 改动它（快照是独立字段）。打开非消费者 sheet 时释放快照，避免复用旧选区。
                val opened = readerChromeReducer(it.toReaderChromeState(), ReaderChromeEvent.SheetOpen)
                    .into(it)
                    .copy(sheet = action.sheet)
                if (action.sheet.consumesSelection()) opened.withSelectionCaptured()
                else opened.withSelectionSnapshotReleased()
            }
            is ReaderAction.CloseSheet -> updateScreen {
                // 关闭弹层即释放快照（要求 8）：下一次打开必须重新捕获，不复用旧选区。
                readerChromeReducer(it.toReaderChromeState(), ReaderChromeEvent.SheetClose).into(it)
                    .copy(sheet = null)
                    .withSelectionSnapshotReleased()
            }
            is ReaderAction.SetSelectedText -> updateScreen {
                // 注意：这里**不动** selectionSnapshot —— 正文建立新选区不得改写消费者快照。
                it.copy(
                    selectedText = action.text,
                    selectedRangeStart = action.rangeStart,
                    selectedGlobalOffset = action.globalOffset,
                    selectedSourceEnd = action.sourceEnd,
                )
            }
            is ReaderAction.ClearSelection -> updateScreen {
                // 同理：正文活动选区清理不得改写消费者快照（这正是 F2.4 要修的核心）。
                it.withSelectionCleared()
            }
            is ReaderAction.SetShowTts -> updateScreen { it.copy(showTts = action.show) }
            is ReaderAction.SetSearchQuery -> updateScreen { it.copy(searchQuery = action.query) }
            is ReaderAction.SetShowOverflow -> updateScreen { it.copy(showReaderOverflow = action.show) }
            is ReaderAction.SetNoteOpen -> updateScreen {
                // 笔记弹层同样是长生命周期消费者：打开时捕获，关闭时释放。
                val next = if (action.open) it.withSelectionCaptured() else it.withSelectionSnapshotReleased()
                next.copy(noteOpen = action.open)
            }
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
                // 69-v2-B：使用事件携带的 id，与 onCreateCategory 回调返回给弹层的 id 同源，
                // 避免 payload.categoryIds 指向另一枚从未落库的 id。
                taxonomyRepository.upsertCategory(
                    CategoryEntity(id = action.id, name = action.name, created_at = nowIso(), updated_at = nowIso())
                )
            }
            is ReaderAction.CreateTag -> io {
                val id = "mobile-tag-${java.util.UUID.randomUUID()}"
                taxonomyRepository.upsertTag(TagEntity(id = id, name = action.name, type = "inspiration", created_at = nowIso(), updated_at = nowIso()))
            }
            is ReaderAction.SaveProgress -> {
                // CRA-READER-DISPATCH-ORDER-MINIMAL-v6：事件序号在**派发边界**（本分支同步执行时）
                // 取号，随后才交给 io 异步落库；绝不在 io 块内取号（那反映的是落库顺序）。
                val saveOrder = ReaderProgressSaveOrder.next()
                ioProgressSave(action.progress.book_id) {
                    com.creationreadingassistant.ui.screen.reader.ReaderClampDiagnostics.log(
                        "vm_save_progress_received",
                        "bid" to action.progress.book_id,
                        "new_offset" to action.progress.current_location_json,
                        "new_percent" to action.progress.progress_percent,
                    )
                    bookRepository.saveProgress(action.progress, saveOrder)
                }
            }
            is ReaderAction.UpdateReadingActivity -> readingSessionRecorder.update(
                ReadingActivity(
                    bookId = action.bookId,
                    active = action.active,
                    progressPercent = action.progressPercent,
                ),
            )
            is ReaderAction.SaveEpubProgress -> {
                // 同 SaveProgress：序号在派发边界同步取号。
                val saveOrder = ReaderProgressSaveOrder.next()
                ioProgressSave(action.bookId) {
                    epubRepository.saveProgress(
                        bookId = action.bookId,
                        chapterIndex = action.chapterIndex,
                        percent = action.percent,
                        offsetInChapter = action.offsetInChapter,
                        absoluteOffset = action.absoluteOffset,
                        order = saveOrder,
                    )
                }
            }
            is ReaderAction.DeleteBook -> io { deletions.deleteBook(action.bookId) }
            is ReaderAction.ClearChapterReads -> io {
                chapterReadRepository.clearForBook(action.bookId)
            }

            // 规则写入（RulesRepository；校验/迁移失败不落库，结果原样发布）
            is ReaderAction.ExecuteRuleCommand -> io {
                // 每次写入先清空上一次的反馈：_ruleMutationResult 是 MutableStateFlow，
                // 按 equals 去重发射。编辑同一条规则时两次保存都返回相同的 Saved(id)，
                // 不清空则第二次根本不发射 → UI 收不到「保存完成」→ 编辑器不关闭、
                // 点击无反馈（M-BUG-01，退出重进才发现名字已改）。先置 null 保证每次
                // 保存都有 null → 结果的可见跃变；随后 rulesRepository.execute 必须挂起
                // （Room 写入），主线程收集器必然能观察到中间态。
                _ruleMutationResult.value = null
                // 单处纠错（E2）：命令不带坐标，锚点在执行前从当前选区状态回查填充，
                // 与 ReaderScaffold/ReaderProgressActions 的字段判别式保持一致
                // （TXT 用 selectedRangeStart，EPUB 用 selectedGlobalOffset，终点统一 selectedSourceEnd）。
                val command: RuleCommand? = when {
                    action.command !is RuleCommand.SaveSingleCorrection -> action.command
                    else -> {
                        // F4 纵深防御第 1 道门：能力级。判据来自生产层发布的
                        // pagerEngine.replacementAvailability（见 currentReplacementCapability），
                        // 不是「文档是不是 EPUB」的类型推断，也不是 UI 开关反推。
                        //
                        // 为什么必须有这一道：`RulesRepository.saveSingleCorrection` 只校验
                        // 区间有效性与 findText 非空，**不校验 `[sourceStart, sourceEnd)` 处的
                        // 文本是否等于 findText**。因此把估算量纲（legacy EPUB）或渲染量纲
                        // （Markdown）的坐标当锚点写库，是一条不报错、不崩溃、只有重开书
                        // 才发现正文被改错位置的路径。任何新增入口（设置页跳转、深链、
                        // 规则管理重构、未来的 AI 建议纠错）都能绕过 UI 直接构造本命令。
                        val capability = currentReplacementCapability()
                        if (capability !is ReaderReplacementCapability.Available) {
                            _ruleMutationResult.value = RuleMutationResult.NotAnchorable(
                                (capability as? ReaderReplacementCapability.Unavailable)
                                    ?.message
                                    ?.let { "当前书籍格式暂不支持单处纠错：$it" }
                                    ?: "当前书籍格式暂不支持单处纠错",
                            )
                            null
                        } else {
                            resolveSingleCorrectionAnchor(draft = action.command)
                        }
                    }
                }
                if (command == null) return@io
                // 受影响范围必须在 execute **之前**判定：删除类命令执行后规则行已不在。
                val indexPlan = ReaderSearchIndexCoordinator.planForCommand(command, rulesRepository)
                val result = rulesRepository.execute(action.bookId, command)
                if (result is RuleMutationResult.Migrated) {
                    // 快速单选 / 迁移：同步旧单选显示 id，并发布结果供统一重扫策略消费
                    _txtTocRuleId.value = result.effectiveRuleId
                }
                _ruleMutationResult.value = result
                if (result is RuleMutationResult.Success || result is RuleMutationResult.Saved) {
                    // 选区使命已尽（单处纠错的锚点已在 execute 前回查消费）。
                    // 规则变更会使正文重分页，残留的 display 坐标选区会错位；
                    // 选区工具条与规则表单的「选中文本」预填也须一并失效。
                    // 失败路径（Rejected/NotAnchorable/NotFound）不清——选区仍有效，用户可重试。
                    updateScreen { it.withSelectionCleared() }
                }
                if (indexPlan != null &&
                    (result is RuleMutationResult.Success || result is RuleMutationResult.Saved)
                ) {
                    ReaderSearchIndexCoordinator.applySearchIndexRefresh(
                        bookId = action.bookId,
                        plan = indexPlan,
                        appScope = appScope,
                        searchIndexRepository = searchIndexRepository,
                        searchIndexScheduler = searchIndexScheduler,
                    )
                }
            }
            is ReaderAction.ClearRuleMutationResult -> _ruleMutationResult.value = null

            // S07-F4.1：生产层同步替换能力裁决（单处纠错纵深防御的唯一事实源）
            is ReaderAction.SetReplacementAvailability -> {
                // 只采信「当前书」的发布：切书后旧组合的迟到发布不得覆盖新书的裁决。
                // 身份不符即忽略（而不是写成 null）—— 新书自己的发布很快就会到达，
                // 中途把它清空只会让一次合法的保存被误拒。
                if (action.bookId == _uiState.value.requestedBookId) {
                    _replacementAvailability.value = PublishedReplacementAvailability(
                        bookId = action.bookId,
                        availability = action.availability,
                    )
                }
            }

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

    /** 加载 TTS 续读信息并返回。 */
    suspend fun loadTtsResume(): TtsResume? =
        runCatching { settingsStore.loadTtsResume() }.getOrNull()

    // ── 内部辅助 ─────────────────────────────────────────────────
    private fun updateScreen(transform: (ReaderScreenState) -> ReaderScreenState) {
        _screenState.update(transform)
    }

    /**
     * S07-F4.1：当前书的替换能力 —— 直接读生产层发布的事实源，不再按文档类型猜。
     *
     * 纵深防御的**判据来源**必须是 UI 用的那一个 `PagedReplacementAvailability`：
     * - 未发布（初值 / 刚切书 / 引擎尚未裁决）⇒ `SOURCE_UNAVAILABLE` ⇒ 拒绝；
     * - 发布值与当前书身份不符 ⇒ 视为未发布 ⇒ 拒绝。
     *
     * 这就是「默认拒绝」：宁可让一次保存被要求重试，也不把上一本书或估算量纲的
     * 坐标当锚点写进库。
     */
    private fun currentReplacementCapability(): ReaderReplacementCapability {
        val published = _replacementAvailability.value
        val availability = if (published != null &&
            published.bookId == _uiState.value.requestedBookId
        ) {
            published.availability
        } else {
            PagedReplacementAvailability.SOURCE_UNAVAILABLE
        }
        return readerReplacementCapability(availability)
    }

    /**
     * F4：单处纠错命令的坐标填充与拒绝（第 2、3 道门）。
     *
     * 前置：调用方已确认当前书具备可安全改写的 source 坐标（第 1 道能力门）。
     * 这里只回答「有没有锚点」与「草稿文字是否就是当前选区文字」，返回 null 表示拒绝。
     */
    private fun resolveSingleCorrectionAnchor(
        draft: RuleCommand.SaveSingleCorrection,
    ): RuleCommand? {
        val anchor = ReaderSearchIndexCoordinator.currentSelectionSourceRange(_screenState.value)
            ?: run {
                _ruleMutationResult.value = RuleMutationResult.NotAnchorable(
                    "当前没有可用的选区位置，请先在正文中长按选中要纠错的文字",
                )
                return null
            }
        if (draft.findText != _screenState.value.selectedText) {
            _ruleMutationResult.value = RuleMutationResult.NotAnchorable(
                "单处纠错只作用于当前选区文字；如需修改查找内容，请改用本书替换",
            )
            return null
        }
        return RuleCommand.SaveSingleCorrection(
            sourceStart = anchor.first,
            sourceEnd = anchor.second,
            findText = draft.findText,
            replaceText = draft.replaceText,
        )
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(ioDispatcher) { block() }
    }

    /**
     * 阅读进度保存的唯一派发入口（只给 SaveProgress / SaveEpubProgress 用）。
     *
     * 记账要点：
     * - 登记在 launch **之前**（派发即计入，不会"刚派发就被判为空"）；
     * - 结算挂在 `invokeOnCompletion` 上：**即使协程在真正开始执行前就被取消也会结算**，
     *   不会留下永远减不掉的假 pending；
     * - 失败（含保存自身被取消）保存**原异常实例**，类型与消息都不变；成功则结算该次派发。
     */
    private fun ioProgressSave(bookId: String, block: suspend () -> Unit) {
        val seq: Long
        synchronized(progressSaveStateLock) {
            progressSaveSeq += 1
            seq = progressSaveSeq
            progressSavesInFlight += 1
            progressSavesIdle.value = false
        }
        var failure: Throwable? = null
        val job = viewModelScope.launch(ioDispatcher) {
            // 不把异常抛进 viewModelScope（SupervisorJob 下仍会崩进程）：由完成回调统一结算。
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            }
        }
        job.invokeOnCompletion { cause ->
            val recorded = failure ?: cause
            synchronized(progressSaveStateLock) {
                // 覆盖边界只看**同一本书**的序号：
                // - 失败：比本书最近一次成功更旧的失败已被覆盖，不记（避免旧失败晚到永久卡死）；
                //   否则记为本书最新的失败（更新的一次失败必须盖过更旧的失败）。
                // - 成功：只有比本书最新失败更新的成功才解除它；更旧的成功不得清掉更新的失败。
                val lastSuccess = progressSaveSuccessSeq[bookId] ?: 0L
                if (recorded != null) {
                    if (seq > lastSuccess) {
                        val previous = progressSaveFailures[bookId]
                        if (previous == null || seq > previous.seq) {
                            progressSaveFailures[bookId] = ProgressSaveFailure(seq, recorded)
                        }
                    }
                } else {
                    if (seq > lastSuccess) progressSaveSuccessSeq[bookId] = seq
                    val previous = progressSaveFailures[bookId]
                    if (previous != null && seq > previous.seq) {
                        progressSaveFailures.remove(bookId)
                    }
                }
                progressSavesInFlight -= 1
                if (progressSavesInFlight == 0) progressSavesIdle.value = true
            }
        }
    }

    /**
     * 等待本 reader **已派发**的阅读进度保存全部正常完成。
     *
     * - 正常返回 = 在途计数归零且没有未解除的失败；
     * - 失败/取消抛出**原异常实例**（不包装、不吞、**不消费**）：异常保持 sticky，
     *   直到**更晚派发的一次保存正常完成**（新位置覆盖旧失败）才解除；
     *   因此"空保存再退一次"不会假成功；
     * - 只覆盖调用前已派发的事件；退出之后新派发的事件不在本保证范围。
     */
    suspend fun awaitProgressSaves() {
        while (true) {
            progressSavesIdle.first { it }
            val (inFlight, failure) = synchronized(progressSaveStateLock) {
                progressSavesInFlight to progressSaveFailures.values.maxByOrNull { it.seq }?.throwable
            }
            if (inFlight != 0) continue // 归零瞬间又派发了新事件：继续等
            failure?.let { throw it }
            return
        }
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
        val bookChanged = current.requestedBookId != bookId
        // 内容身份是否重建：切书必然重建；force 重载（错误页重试）也会重新走
        // documentLoader.load 并把旧 loadedBook 交给 release()。两者都会让旧选区的
        // 坐标指向一个已被丢弃的文档实例，因此**都**必须作废选区。
        //
        // 这里显式把判据写出来，而不是依赖「切书分支碰巧包含 force」或反之。
        val documentIdentityRebuilt = bookChanged || force

        if (bookChanged) {
            // 切书重置 chrome：菜单回到可见（用户反馈 4）；同时清空旧书搜索词。
            updateScreen {
                readerChromeReducer(it.toReaderChromeState(), ReaderChromeEvent.BookSwitched).into(it)
                    .copy(searchQuery = "")
            }
            // 切书清空旧书规则写入反馈，避免新书会话展示上一本书的结果
            _ruleMutationResult.value = null
            // S07-F4.1：旧书的能力裁决一并作废 —— 新书自己的发布到达前一律按
            // SOURCE_UNAVAILABLE 拒绝，绝不沿用上一本书的 availability。
            // 这里**只**在切书时清：书身份变化会重发发布（LaunchedEffect 的 key 含
            // requestedBookId），而 force 重载的书身份不变、不会重发，清掉就再也不会
            // 被填回来（等于把该书永久判为不可用）。
            _replacementAvailability.value = null
        }

        if (documentIdentityRebuilt) {
            // S07-F4 / F4.1：**同一个 ViewModel 实例跨书（或跨文档实例）复用**时，
            // 旧书的选区数据必须全部作废，不能只释放快照。理由按顺序叠加三层：
            //
            // 1. 活动选区四字段 + showColorRow 属于**上一份文档**的坐标空间。F4 只释放了
            //    快照，于是旧书的 `selectedText` / 两个起点槽 / `sourceEnd` 仍留在新书
            //    会话里 —— 工具条会带着上一本的文字显示，保存高亮/笔记/灵感/纠错会
            //    消费它，而且新书打开消费者时 `withSelectionCaptured()` 会把它**重新
            //    捕获**成一份看似合法的快照（旧坐标配上新会话，比复用旧快照更糟）。
            // 2. 消费者快照本身同样必须释放：切书不一定经过 CloseSheet（从书架直接打开
            //    另一本），旧书的摘录与坐标不能跨书。
            // 3. `noteOpen` / `noteBody` / `sheet` 是仍持有旧选区语义的消费者 UI 状态：
            //    笔记弹层带着旧书摘录、规则页带着旧书草稿。
            updateScreen {
                it.withSelectionCleared()
                    .withSelectionSnapshotReleased()
                    .copy(
                        sheet = null,
                        noteOpen = false,
                        noteBody = "",
                    )
            }
        }
        activeBookId.value = bookId
        // P1-A：切书后，上一本书在途的规则扫描一律失效，迟到结果不得发布，并重置状态
        txtScanCoordinator.onBookSwitched()

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
        chapterLoaderCoordinator.loadChapter(
            scope = viewModelScope,
            dispatcher = ioDispatcher,
            loadedBookProvider = { _uiState.value.loadedBook },
            bookId = bookId,
            chapterIndex = chapterIndex,
            persistProgress = persistProgress,
            chapterReadRepository = chapterReadRepository,
            epubRepository = epubRepository,
            // CRA-READER-DISPATCH-ORDER-MINIMAL-v6：章切换落库（EPUB 只知章号）的事件序号在本
            // 次导航的派发边界取号——不能在加载协程完成时取号，否则「慢的旧章加载」会拿到
            // 比它之后的精确位置保存更大的序号，把进度倒回去。
            saveOrder = ReaderProgressSaveOrder.next(),
        )
    }

    /** 一次性加载章节块（供搜索/高亮定位等 UI 跳转场景使用，不改变当前章节状态）。 */
    suspend fun loadChapterBlocks(bookId: String, chapterIndex: Int): List<DocBlock> =
        chapterLoaderCoordinator.loadChapterBlocks({ _uiState.value.loadedBook }, bookId, chapterIndex)

    /** 提取章节纯文本（供锚点解析等场景使用）。 */
    suspend fun extractChapterText(bookId: String, chapterIndex: Int): String =
        chapterLoaderCoordinator.extractChapterText({ _uiState.value.loadedBook }, bookId, chapterIndex)

    // ── TXT 规则扫描 ──────────────────────────────────────────
    @Suppress("DEPRECATION")
    private fun scanTxtTocRule(bookId: String, filePath: String, ruleId: String) {
        txtScanCoordinator.scanTxtTocRule(
            scope = viewModelScope,
            dispatcher = ioDispatcher,
            bookId = bookId,
            filePath = filePath,
            ruleId = ruleId,
        )
    }

    /**
     * 多规则目录重扫（P1-A）：规则写入成功后重新识别当前流式 TXT。
     */
    private fun rescanTxtToc(bookId: String, filePath: String, profileKeyHint: String) {
        txtScanCoordinator.rescanTxtToc(
            scope = viewModelScope,
            dispatcher = ioDispatcher,
            activeBookIdProvider = { activeBookId.value },
            bookId = bookId,
            filePath = filePath,
            profileKeyHint = profileKeyHint,
            rulesRepository = rulesRepository,
        )
    }

    /** 显式取消当前 TXT 规则扫描。 */
    private fun cancelTxtTocScan() {
        txtScanCoordinator.cancelTxtTocScan()
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
        chapterLoaderCoordinator.clear()
        txtScanCoordinator.clear()
        loadGeneration.incrementAndGet()
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

/**
 * 阅读器退出边界（CRA-READER-EXIT-SAVE-DRAIN-v1）：系统 Back / 顶栏 Back / 临时返回的**唯一共用入口**。
 *
 * 语义（严格顺序，不可倒置）：
 * 1. 先**同步派发**本次退出保存（[dispatchExitSave]，内部仍是生产 `persistOnLeave`，
 *    临时查阅 / Legado marker / 会话位置守卫一律照旧生效）；
 * 2. 再等待本 reader 已派发的进度保存**正常完成**（[awaitProgressSaves]）；
 * 3. 只有全部正常完成后才导航**一次**（[navigate]）。
 *
 * 失败/取消一律不导航：原异常交给 [onFailure] 走既有提示路径，reader 留在原页。
 * 等待期间重复 Back 不会触发第二次导航；等待结束（成功或失败）后允许再次发起退出。
 *
 * 边界之外：不新建持久队列 / WorkManager，不做超时强退，不在 `onCleared()` 等待，
 * 也不把应用级 scope 的后台完成当作"退出前已 drain"。
 */
internal class ReaderExitBoundary(
    private val scope: CoroutineScope,
    private val dispatchExitSave: () -> Unit,
    private val awaitProgressSaves: suspend () -> Unit,
    private val navigate: () -> Unit = {},
    /**
     * 成功 drain 之后、导航之前调用一次：生产用它把 lifecycle 防重复门标记为
     * "本次会话已在权威时机落过库"，避免导航后的 onDispose 再派发一次可能被取消的重复保存。
     * 失败路径**不会**调用它（失败不得提前标成功去抑制应当发生的保存）。
     */
    private val onDrained: () -> Unit = {},
) {
    private var exitJob: Job? = null
    private var navigated = false

    fun requestExit(onFailure: (Throwable) -> Unit = {}) {
        requestExitWithAction(navigate, onFailure = onFailure)
    }

    /**
     * CRA-TASK195-r1: 支持携带特定跳转/退出动作的权威退出保存排空边界。
     * - 保存/排空未完成：不执行 action，不标记 onDrained；
     * - 保存失败/取消：不执行 action，保留原异常并回调 onFailure（主线程反馈）；
     * - 协程自身取消直接重抛；保存过程内部取消作为失败回传；
     * - 成功后在主线程标记 onDrained() 并执行 action() 一次；
     * - 防重复点击：排空中或已导航时直接 return，防重复压栈。
     */
    fun requestExitWithAction(
        action: () -> Unit,
        skipExitSave: Boolean = false,
        onFailure: (Throwable) -> Unit = {},
    ) {
        if (navigated || exitJob?.isActive == true) return
        // 197-r3：skipExitSave=true = 临时入口转换（S0→T1），S0 无新进度可写 →
        // 跳过 dispose 落库（S0 12 列冻结）；仍 await 排空在途保存后导航。
        if (!skipExitSave) dispatchExitSave()
        exitJob = scope.launch {
            val failure: Throwable? = try {
                awaitProgressSaves()
                null
            } catch (c: CancellationException) {
                // 取消语义：
                // - 外部退出协程取消（页面被销毁 / 作用域取消）：直接向外重抛，交由协程结构化并发处理；
                // - 保存协程自身被取消：视同排空失败，停留在原页并向外可观察反馈。
                if (!currentCoroutineContext().isActive) throw c
                c
            } catch (t: Throwable) {
                t
            }
            if (failure != null) {
                withContext(Dispatchers.Main.immediate) {
                    onFailure(failure)
                }
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                onDrained()
                navigated = true
                action()
            }
        }
    }

    /**
     * CRA-TASK195-r1: 当从临时查阅返回前台重新激活时复位导航就绪标志，
     * 允许后续阅读与后续合法的退出保存。
     */
    fun onResume() {
        navigated = false
    }
}
