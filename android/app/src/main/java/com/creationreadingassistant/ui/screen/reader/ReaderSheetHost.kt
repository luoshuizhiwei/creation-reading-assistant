package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.creationreadingassistant.data.settings.PerBookOverrides
import com.creationreadingassistant.data.settings.ReaderOverrideKey
import com.creationreadingassistant.data.settings.ReaderDayNightToggle
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.ReaderSettingsScope
import com.creationreadingassistant.data.settings.ReadingPreset
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.pager.PagedReplacementAvailability
import com.creationreadingassistant.feature.reader.pager.ReaderPagePosition
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.screen.reader.sheets.ReaderExportReceipts
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import com.creationreadingassistant.ui.screen.reader.sheets.AiAssistSheet
import com.creationreadingassistant.ui.screen.reader.sheets.AiExplainSheet
import com.creationreadingassistant.ui.screen.reader.sheets.BookInfoSheet
import com.creationreadingassistant.ui.screen.reader.sheets.DictionarySheet
import com.creationreadingassistant.ui.screen.reader.sheets.InspirationPage
import com.creationreadingassistant.ui.screen.reader.sheets.NOTE_KIND_BOOKMARK
import com.creationreadingassistant.ui.screen.reader.sheets.NotesSheet
import com.creationreadingassistant.ui.screen.reader.sheets.ProgressSheet
import com.creationreadingassistant.ui.screen.reader.sheets.RulesSheet
import com.creationreadingassistant.ui.screen.reader.sheets.SearchSheet
import com.creationreadingassistant.ui.screen.reader.sheets.SettingsSheet
import com.creationreadingassistant.ui.screen.reader.sheets.ThemePage
import com.creationreadingassistant.ui.screen.reader.sheets.TocSheet
import com.creationreadingassistant.ui.screen.reader.sheets.highlightNavigationTarget
import com.creationreadingassistant.ui.screen.reader.sheets.noteNavigationTarget
import com.creationreadingassistant.ui.screen.reader.sheets.tocNoteEntries
import com.creationreadingassistant.ui.screen.reader.sheets.readerTocEntries
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.window.LocalDialogSystemBarsEffect
import com.creationreadingassistant.feature.reader.locator.EpubLocatorMapping
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanStatus
import com.creationreadingassistant.ui.viewmodel.matchesSession

/**
 * READER-PAGES-v1：升级为**全屏附页**的入口集合（合同 §3）。
 * 这些分支分派到 [ReaderPageDialogHost] 完整页面；其余弹层继续现有 ModalBottomSheet。
 */
internal val READER_PAGE_SHEETS: Set<ReaderSheet> = setOf(
    ReaderSheet.TOC,
    ReaderSheet.SETTINGS,
    ReaderSheet.THEME,
    ReaderSheet.INSPIRATION,
)

/** READER-PAGES-v1：THEME 预览快照的最大字符数（合同 P2：开页时捕获 ≤800 字符）。 */
private const val READER_PAGE_PREVIEW_MAX_CHARS = 800

/**
 * 弹层所需文档内容与章节信息（B1 状态袋分组：文档域）。
 */
internal data class ReaderSheetDocumentState(
    val epubBook: EpubBook?,
    val epubDocument: ReaderDocument?,
    val markdownDocument: ReaderDocument?,
    val txtStreamingDocument: PlainTextDocument?,
    val plainContent: String,
    val chapterIndex: Int,
    val txtChapterIndex: Int,
    val txtChapterTitles: List<String>,
    val currentChapterTitle: String,
    val contentText: String,
    val isTxt: Boolean,
    val chapterStartOffsets: List<Int>,
    val chapterTitles: List<String>,
    val chapterWordCountLabels: List<String>,
    val bookIndex: BookIndex?,
    val txtTocRuleId: String,
    val txtRulePreviews: Map<String, List<TxtChapterDetector.Chapter>>,
    val txtRuleScanStatus: TxtRuleScanStatus?,
)

/**
 * 弹层所需书籍元信息（B1 状态袋分组：书籍信息域）。
 */
internal data class ReaderSheetBookMetaState(
    val bookTitle: String,
    val bookAuthor: String?,
    val bookOriginalFile: String?,
)

/**
 * 弹层所需阅读统计与进度（B1 状态袋分组：统计域）。
 */
internal data class ReaderSheetStatsState(
    val progressPercent: Float,
    val activeReadingMs: Long,
    val savedBookReadingMs: Long,
    val estimatedRemainingMs: Long,
    val readerSpeed: Int,
    val inspirationsCount: Int,
    val bookmarksCount: Int,
    val documentWordCount: Int,
)

/**
 * 弹层所需 UI 交互状态（B1 状态袋分组：UI 域）。
 */
internal data class ReaderSheetUiState(
    val selectedText: String,
    /** **有效**阅读设置（全局叠加本书覆盖），阅读器与设置面板共同消费。 */
    val readerSettings: ReaderSettings,
    val pagerEngineOn: Boolean,
    val searchQuery: String,
    val recentChapters: List<Int>,
    val appDark: Boolean,
    val replacementAvailability: PagedReplacementAvailability = PagedReplacementAvailability.SOURCE_UNAVAILABLE,
    /** R3-P1：**全局**阅读设置（不含本书覆盖），书内编辑路由需要它做「还原全局原值」。 */
    val globalReaderSettings: ReaderSettings = ReaderSettings(),
    /** R3-P1：本书显式覆盖项；用于标注设置面板里「哪一项来自本书」。 */
    val bookReaderOverrides: PerBookOverrides = emptyMap(),
    /** R3-P1：书内设置的修改作用范围（本书 / 全局）。 */
    val settingsScope: ReaderSettingsScope = ReaderSettingsScope.BOOK,
    /** R3-X1：离线词典面板要查的词。 */
    val dictionaryWord: String = "",
)

/**
 * 阅读器底部弹层分发所需的只读展示数据。
 *
 * 从 [ReaderScreen] 主函数局部状态中提取，供 [ReaderSheetHost] 在各 sheet 分支内转发给
 * 具体 Sheet 组件。仅承载「读」数据；任何对主函数可变状态的写入都通过
 * [ReaderSheetHostCallbacks] 回调上抛，避免跨文件扩散状态耦合。
 *
 * B1 状态袋瘦身：原 31 个平铺字段按域分组为 4 个子对象（[document] 文档与章节 /
 * [bookMeta] 书籍信息 / [stats] 阅读统计 / [ui] UI 交互），纯结构搬运。
 */
internal data class ReaderSheetHostState(
    val document: ReaderSheetDocumentState,
    val bookMeta: ReaderSheetBookMetaState,
    val stats: ReaderSheetStatsState,
    val ui: ReaderSheetUiState,
)

/**
 * 阅读器底部弹层分发所需的回调集合。
 *
 * 每个回调对应一次「主函数可变状态写入」或「主函数局部副作用」。复杂分支
 * （目录跳章、TXT 规则切换、搜索跳转、导出书摘、保存灵感等）的完整逻辑体仍留在
 * [ReaderScreen] 主函数中定义，本接口只暴露其触发入口，从而保持 [ReaderSheetHost]
 * 为纯分发层。
 */
internal data class ReaderSheetHostCallbacks(
    val onDismiss: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenBookInfo: () -> Unit,
    val goToChapter: (Int) -> Unit,
    val seekToPercent: (Float) -> Unit,
    val jumpToPlainOffset: (Int) -> Unit,
    val showNotice: (String) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onClearSelectedText: () -> Unit,
    val onPickChapter: (Int) -> Unit,
    val onTxtRule: (String) -> Unit,
    val onCancelTxtScan: () -> Unit,
    /**
     * 标注回源（A01）：入参是**已带类型前缀的 typed 目标**
     * （`highlight:{id}` / `note:{id}` / `bookmark:{id}`），由
     * [com.creationreadingassistant.feature.annotations.navigationTargetId] 生成。
     *
     * 为什么收敛成一个「只收 typed 目标」的回调：旧设计按类型拆成
     * `onJumpToHighlight(rawId)` / `onJumpToBookmark(rawId)`，前者写裸 rawId，
     * 而裸 rawId 在阅读器侧走「先高亮、后笔记」的兼容回退 —— 同一 rawId 跨表存在时
     * 会跳错表。改成单一 typed 入参后，**结构上不存在**传裸 id 的入口。
     */
    val onJumpToTypedAnnotation: (String) -> Unit,
    /**
     * S07-F2.4：AI 解读存灵感。摘录文字与 locator **由调用方按打开时快照传入**，
     * 本层不得回读实时活动选区（后者会被 ReadingUnit dispose 清空）。
     */
    val onSaveAiExplainInspiration: (
        body: String,
        excerpt: String,
        locatorJson: String?,
        tags: List<String>,
        categoryIds: List<String>,
    ) -> Unit,
    /** 设置/主题变更前先持久化当前阅读进度（防重排回退到旧进度）。 */
    val onPersistProgress: () -> Unit,
    /**
     * READER-PAGES-v1：保存灵感——[entityId] 由附页按「本次编辑会话」持有（失败重试复用）。
     * 摘录文字与 locator 来自同一个打开时快照；本回调只派发落库，成功信号经
     * inputs.inspirationSaveOutcome 回流，附页收到后才清选区/关页/提示。
     */
    val onSaveInspiration: (
        entityId: String,
        title: String,
        body: String,
        excerpt: String,
        locatorJson: String?,
        tags: List<String>,
        categoryIds: List<String>,
    ) -> Unit,
    val onCreateCategory: (String) -> String,
    val onCreateTag: (String) -> String,
    /** R3-P1：切换书内设置的修改作用范围（本书 / 全局）。 */
    val onSettingsScopeChange: (ReaderSettingsScope) -> Unit = {},
)

/**
 * 阅读器底部弹层宿主：按 [sheet] 当前值分发到对应 Sheet 组件。
 *
 * 仅做路由分发与参数转发，不持有任何可变状态；所有展示数据来自 [state]，
 * 所有副作用通过 [sheetCallbacks] 上抛给 [ReaderScreen] 主函数。原逻辑 1:1 搬运自
 * ReaderScreen 主函数的 `sheet?.let { type -> ModalBottomSheet(...) { when (type) {...} } }` 块。
 *
 * G26：弹层容器由 `GlassModalBottomSheet` 改为普通 [ModalBottomSheet]。前者只是在后者上
 * 叠加 `glassWindowBlur`（反射 `Window.setBlurBehindRadius`，API31+ 对整窗实时模糊），
 * 弹层进/退场动画每帧都会让合成器重模糊，在 Redmi 上已实测造成 141ms 的 GPU 阻塞帧
 * （见 results/shelf-sort-performance-report.md §7.2~§7.4，书架排序下拉已按同一模式整改）。
 * 阅读器 sheet 直接覆盖在翻页帧路径之上，帧预算最敏感，故同样去除窗口模糊；
 * 除模糊外弹层的形状/容器色/拖拽把手/交互行为完全一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSheetHost(
    sheet: ReaderSheet?,
    sheetState: SheetState,
    searchSession: BookSearchSession,
    paper: ReaderPaperPalette,
    inputs: ReaderScreenInputs,
    callbacks: ReaderScreenCallbacks,
    settingsVm: SettingsViewModel,
    state: ReaderSheetHostState,
    sheetCallbacks: ReaderSheetHostCallbacks,
) {
    val bid = inputs.bookId ?: ""

    // S07-F2.4：长生命周期消费者一律读**打开时快照**，不回读实时活动选区
    //（后者会在 TTS 跟读导致 ReadingUnit dispose 时被清空）。
    // 快照真源在 VM 的 screenState；本层只读转发，不新增第二份状态。
    val consumerSnapshot = inputs.screenState.selectionSnapshot

    /**
     * S07-F2.4：随快照同源生成的 source locator（要求 7）。
     *
     * 摘录文字与坐标都由 [consumerSnapshot] 这一份不可变快照推出，禁止「文字取快照、
     * 坐标取已被清空的实时状态」。与落库高亮同源的 [computeLocatorJsonForSnapshot]
     * 保持一致的 source 坐标契约（不写显示偏移）。
     */
    fun snapshotLocator(): String? = computeLocatorJsonForSnapshot(
        epubBook = state.document.epubBook,
        chapterStartOffsets = state.document.chapterStartOffsets,
        snapshot = consumerSnapshot,
    )

    /**
     * R3-P1：书内编辑的唯一落盘入口（实现见同包顶层 [applyReaderEdit]）。
     *
     * 灯绳与设置面板共用同一套路由，避免两边分叉。
     */
    val applyReaderEdit: (ReaderSettings) -> Unit = { updated ->
        applyReaderEdit(
            settingsVm = settingsVm,
            bid = bid,
            scope = state.ui.settingsScope,
            global = state.ui.globalReaderSettings,
            displayed = state.ui.readerSettings,
            edited = updated,
            showNotice = sheetCallbacks.showNotice,
        )
    }

    // ── READER-PAGES-v1：全屏附页容器分派 ────────────────────────────────────
    // TOC / SETTINGS / THEME / INSPIRATION 分派到独立 Dialog 容器承载的完整页面；
    // 其余弹层继续现有 ModalBottomSheet。容器只包裹现有 reader owner，不新建第二套
    // reader/NavHost/ViewModel，也不 dispose 底层阅读正文（合同 §3）。
    val pageTarget = sheet?.takeIf { it in READER_PAGE_SHEETS }
    // THEME→SETTINGS 内层开关（合同 P2：Back 回 THEME 并保留分类/预览；附页只能从
    // THEME 根页关闭，随关闭复位是防御性的）。
    var themeInnerSettingsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(pageTarget) {
        if (pageTarget == null) themeInnerSettingsOpen = false
    }
    // TOC 索引附页的宿主持久状态：列表位置按 bookId+Tab 保存；首次打开定位一次。
    // 状态放在宿主（主组合）层：附页内容随容器进出组合，主组合才能跨开关保留。
    val tocListState = rememberSaveable(bid, key = "reader-toc-list-toc", saver = LazyListState.Saver) { LazyListState() }
    val tocBookmarkListState = rememberSaveable(bid, key = "reader-toc-list-bookmarks", saver = LazyListState.Saver) { LazyListState() }
    val tocNoteListState = rememberSaveable(bid, key = "reader-toc-list-notes", saver = LazyListState.Saver) { LazyListState() }
    var tocAutoLocated by rememberSaveable(bid, key = "reader-toc-auto-located") { mutableStateOf(false) }
    // THEME 预览快照：**开页时**按 bookId/章节捕获一次 ≤800 字符的稳定只读片段（合同 P2）。
    // pageTarget 必须参与 key：宿主常驻组合，若只按书/章为键，会在文档未加载的首帧
    // 捕获到空串并永久滞留；以 pageTarget 翻转为「开页」信号重新捕获。
    // 滑块拖动只改变渲染，不重捕获文字。
    val themePreviewText = remember(
        pageTarget,
        bid,
        state.document.chapterIndex,
        state.document.txtChapterIndex,
    ) { state.document.contentText.take(READER_PAGE_PREVIEW_MAX_CHARS) }

    ReaderPageDialogHost(
        target = pageTarget,
        paper = paper,
        onDismiss = sheetCallbacks.onDismiss,
    ) { page ->
        ReaderPaperTheme(paper) {
            when (page) {
                ReaderSheet.TOC -> {
                    val tocState = readerTocState(
                        epubTitles = state.document.epubBook?.chapters?.map { it.title },
                        markdownTitles = state.document.markdownDocument?.chapters?.map { it.title },
                        txtTitles = state.document.txtChapterTitles,
                        chapterIndex = state.document.chapterIndex,
                        txtChapterIndex = state.document.txtChapterIndex,
                    )
                    ReaderPageBackHandler(onBack = sheetCallbacks.onDismiss)
                    TocSheet(
                        entries = readerTocEntries(
                            titles = tocState.titles,
                            current = tocState.current,
                            recent = state.ui.recentChapters,
                            read = if (tocState.isChapteredDocument) inputs.readChapters.toSet() else emptySet(),
                            wordCountLabels = state.document.chapterWordCountLabels,
                        ),
                        current = tocState.current,
                        totalChapters = tocState.total,
                        onPick = sheetCallbacks.onPickChapter,
                        txtRules = if (state.document.isTxt) TxtChapterDetector.rules else emptyList(),
                        // P1-A：选中态从 Room 生效目录身份（profile.key）推导；
                        // 多规则组合（指纹 key）不命中任何单选 chip，显示为空。
                        selectedTxtRule = inputs.ruleSnapshot.effectiveTocProfile.key
                            .takeIf { key -> TxtChapterDetector.rules.any { it.id == key } }
                            .orEmpty(),
                        txtRulePreviews = state.document.txtRulePreviews,
                        txtRuleScanStatus = state.document.txtRuleScanStatus?.takeIf { it.matchesSession(bid) },
                        bookmarks = inputs.notes.filter { it.kind == NOTE_KIND_BOOKMARK },
                        onPickBookmark = { bm -> sheetCallbacks.onJumpToTypedAnnotation(noteNavigationTarget(bm)) },
                        onTxtRule = sheetCallbacks.onTxtRule,
                        onCancelTxtScan = sheetCallbacks.onCancelTxtScan,
                        onManageRules = { callbacks.onAction(ReaderAction.OpenSheet(ReaderSheet.RULES)) },
                        // EPUB 分页净化投影可用时也提供「替换净化」入口（TXT 由 txtRules 驱动同一入口）
                        showReplacementRulesEntry = !state.document.isTxt &&
                            readerReplacementCapability(state.ui.replacementAvailability) is
                            ReaderReplacementCapability.Available,
                        showReadStatus = tocState.isChapteredDocument,
                        showClearReadMarks = tocState.isChapteredDocument,
                        onClearReadMarks = {
                            callbacks.onAction(ReaderAction.ClearChapterReads(bid))
                            sheetCallbacks.showNotice("已清除已读标记")
                        },
                        // A01：笔记 Tab = 高亮 + 非书签笔记（数据源与既有 sheet 分支完全一致）。
                        notes = tocNoteEntries(
                            highlights = inputs.highlights,
                            notes = inputs.notes,
                        ),
                        // typed 回源：目标由条目自身携带，调用方不判断「该按哪张表跳」。
                        onPickNote = { entry ->
                            sheetCallbacks.onJumpToTypedAnnotation(entry.navigationTarget)
                        },
                        // 书内索引附页形态（READER-PAGES-v1 P1）
                        pageStyle = true,
                        bookTitle = state.bookMeta.bookTitle,
                        onClose = sheetCallbacks.onDismiss,
                        tocListState = tocListState,
                        bookmarkListState = tocBookmarkListState,
                        noteListState = tocNoteListState,
                        autoLocateCurrent = !tocAutoLocated,
                        onAutoLocated = { tocAutoLocated = true },
                    )
                }

                ReaderSheet.THEME -> ThemePage(
                    settings = state.ui.readerSettings,
                    appDark = state.ui.appDark,
                    scope = state.ui.settingsScope,
                    onScopeChange = sheetCallbacks.onSettingsScopeChange,
                    overrides = state.ui.bookReaderOverrides,
                    previewBookTitle = state.bookMeta.bookTitle,
                    previewChapterTitle = state.document.currentChapterTitle,
                    previewText = themePreviewText,
                    onBackground = { picked ->
                        sheetCallbacks.onPersistProgress()
                        // 记下用户这次选的纸属于哪一侧（亮纸 / 暗纸），供灯绳切换时还原；
                        // 选「跟随外观」不构成记忆，两侧都保持原值。
                        val remembered = ReaderDayNightToggle.rememberPaper(picked)
                        val current = state.ui.readerSettings
                        // R3-P1：纸张切换同样受作用范围约束，否则本书覆盖了纸张时这里点了没反应
                        applyReaderEdit(
                            current.copy(
                                background = picked,
                                dayNightLightPaper = remembered?.light ?: current.dayNightLightPaper,
                                dayNightDarkPaper = remembered?.dark ?: current.dayNightDarkPaper,
                            ),
                        )
                    },
                    onFontSize = { size ->
                        sheetCallbacks.onPersistProgress()
                        applyReaderEdit(state.ui.readerSettings.copy(fontSize = size))
                    },
                    onBrightness = { value ->
                        sheetCallbacks.onPersistProgress()
                        val current = state.ui.readerSettings
                        // 亮度是不可本书覆盖项，Router 保证其始终落全局；同时记忆上次固定亮度
                        applyReaderEdit(
                            current.copy(
                                brightness = value,
                                lastFixedBrightness = if (value >= 0) value else current.lastFixedBrightness,
                            ),
                        )
                    },
                    onSettingsChange = { updated ->
                        // 设置变更会触发分页重排：先落库当前进度，避免按旧进度恢复。
                        sheetCallbacks.onPersistProgress()
                        applyReaderEdit(updated)
                    },
                    onClose = sheetCallbacks.onDismiss,
                    innerSettingsOpen = themeInnerSettingsOpen,
                    onInnerSettingsOpenChange = { themeInnerSettingsOpen = it },
                    innerSettings = {
                        ReaderSettingsPageContent(
                            paper = paper,
                            state = state,
                            settingsVm = settingsVm,
                            bid = bid,
                            applyReaderEdit = applyReaderEdit,
                            sheetCallbacks = sheetCallbacks,
                            onRootBack = { themeInnerSettingsOpen = false },
                        )
                    },
                )

                ReaderSheet.SETTINGS -> ReaderSettingsPageContent(
                    paper = paper,
                    state = state,
                    settingsVm = settingsVm,
                    bid = bid,
                    applyReaderEdit = applyReaderEdit,
                    sheetCallbacks = sheetCallbacks,
                    onRootBack = sheetCallbacks.onDismiss,
                )

                ReaderSheet.INSPIRATION -> InspirationPage(
                    bookId = bid,
                    bookTitle = state.bookMeta.bookTitle,
                    chapterTitle = state.document.currentChapterTitle,
                    excerpt = consumerSnapshot?.text.orEmpty(),
                    progressPercent = state.stats.progressPercent,
                    categories = inputs.categories,
                    tags = inputs.tags,
                    saveOutcome = inputs.inspirationSaveOutcome,
                    onCreateCategory = sheetCallbacks.onCreateCategory,
                    onCreateTag = sheetCallbacks.onCreateTag,
                    onSave = { entityId, title, body, tags, categoryIds ->
                        // 文字与 locator 同源于打开时快照（要求 7）；entityId 由附页持有。
                        sheetCallbacks.onSaveInspiration(
                            entityId,
                            title,
                            body,
                            consumerSnapshot?.text.orEmpty(),
                            snapshotLocator(),
                            tags,
                            categoryIds,
                        )
                    },
                    onClose = sheetCallbacks.onDismiss,
                    onClearSelection = sheetCallbacks.onClearSelectedText,
                    onSaveNotice = sheetCallbacks.showNotice,
                )

                else -> Unit
            }
        }
    }

    sheet?.let { type ->
        if (type in READER_PAGE_SHEETS) return@let
        ModalBottomSheet(
            onDismissRequest = sheetCallbacks.onDismiss,
            sheetState = sheetState,
            containerColor = paper.bg,
            shape = LocalComponentSpec.current.sheetShape,
            dragHandle = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SheetHandle()
                    IconButton(
                        onClick = sheetCallbacks.onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭当前工作表")
                    }
                }
            },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            // M-BUG-11：弹层是独立 Window，不继承 Activity 窗口的系统栏策略——沉浸阅读时
            // 打开本面板会让隐藏的系统状态栏重新露出。这里把阅读器的策略写进本弹层窗口。
            LocalDialogSystemBarsEffect()
            ReaderPaperTheme(paper) {
            when (type) {
                ReaderSheet.RULES -> RulesSheet(
                    snapshot = inputs.ruleSnapshot,
                    previewText = state.document.contentText,
                    mutationResult = inputs.ruleMutationResult,
                    replacementCapability = readerReplacementCapability(state.ui.replacementAvailability),
                    initialReplaceText = consumerSnapshot?.text?.takeIf { it.isNotBlank() },
                    onCommand = { callbacks.onAction(ReaderAction.ExecuteRuleCommand(bid, it)) },
                    onBack = {
                        // 消费者结束：清快照（避免下次打开复用旧选区），并回收活动选区。
                        if (consumerSnapshot?.text?.isNotBlank() == true) {
                            callbacks.onAction(ReaderAction.ClearSelection)
                        }
                        callbacks.onAction(ReaderAction.ClearRuleMutationResult)
                        callbacks.onAction(ReaderAction.OpenSheet(ReaderSheet.TOC))
                    },
                )

                ReaderSheet.NOTES -> NotesSheet(
                    paper = paper,
                    bookTitle = state.bookMeta.bookTitle,
                    // 189-A：reader 内条目「临时查看来源」——回调来自 ReaderRoute
                    //（navController.navigate 到 temporary route）；触发时先关闭当前 sheet 避免遮挡新 reader，null = 不提供入口。
                    bookId = bid,
                    onInspectSourceTemporarily = callbacks.onInspectSourceTemporarily?.let { inspect ->
                        { route ->
                            sheetCallbacks.onDismiss()
                            inspect(route)
                        }
                    },
                    highlights = inputs.highlights,
                    notes = inputs.notes,
                    inspirations = inputs.inspirations,
                    onAddBookmark = {
                        // S09-A-F1：精确坐标与 locator 一律来自「确认属于当前这本书」的位置。
                        val fields = bookmarkPositionFields(
                            position = callbacks.pageIndexManager.position.value,
                            bookId = bid,
                            isEpub = state.document.epubBook != null,
                            chapterStartOffsets = state.document.chapterStartOffsets,
                        )
                        callbacks.onAction(
                            ReaderAction.AddBookmark(
                                bookId = bid,
                                // 百分比回退语义不变：locator 为空时 ReaderProgressEffects
                                // 会用这个 progress_percent 近似回跳，所以「没有精确位置」
                                // 不等于「书签废掉」。
                                offset = state.stats.progressPercent.toInt(),
                                title = state.document.currentChapterTitle.ifBlank { "正文" },
                                absOffset = fields.absOffset,
                                chapterIndex = fields.chapterIndex,
                                charOffset = fields.charOffset,
                                locatorJson = fields.locatorJson,
                            ),
                        )
                        sheetCallbacks.showNotice("已添加书签")
                    },
                    onDeleteHighlight = { h ->
                        callbacks.onAction(ReaderAction.DeleteHighlight(h.id))
                        sheetCallbacks.showNotice("已删除高亮")
                    },
                    onDeleteNote = { n ->
                        callbacks.onAction(ReaderAction.DeleteNote(n.id))
                        val label = if (n.kind == "bookmark") "书签" else "笔记"
                        sheetCallbacks.showNotice("已删除$label")
                    },
                    onChangeHighlightColor = { h, c ->
                        callbacks.onAction(ReaderAction.UpdateHighlightColor(h.id, c))
                    },
                    onEditHighlightNote = { h, note ->
                        callbacks.onAction(ReaderAction.UpdateHighlightNote(h.id, note))
                    },
                    onHighlightToNote = { h ->
                        callbacks.onAction(ReaderAction.ConvertHighlightToNote(h.id))
                        sheetCallbacks.showNotice("已转为笔记")
                    },
                    onHighlightToInspiration = { h ->
                        callbacks.onAction(ReaderAction.ConvertHighlightToInspiration(h.id))
                        sheetCallbacks.showNotice("已转为灵感")
                    },
                    // A01：高亮走 HIGHLIGHT，普通笔记走 NOTE，书签走 BOOKMARK ——
                    // 由各自的 typed 目标决定，不再让普通笔记借用书签回调
                    // （旧实现 onJumpToBookmark 收全部 notes，跨表同 rawId 会跳错表）。
                    onJumpToHighlight = { h ->
                        sheetCallbacks.onJumpToTypedAnnotation(highlightNavigationTarget(h))
                    },
                    onJumpToNote = { n ->
                        sheetCallbacks.onJumpToTypedAnnotation(noteNavigationTarget(n))
                    },
                    // 69-v4-B / 70-v1-A：记录系统 SAF 的实际结果（成功 URI / 取消 / 失败），
                    // 只记回执，不改导出行为；失败保留原始异常供仪器分类。
                    onExportCompleted = { uri -> ReaderExportReceipts.record(uri) },
                    onExportFailed = { error -> ReaderExportReceipts.recordFailure(error) },
                    onExportCancelled = { ReaderExportReceipts.recordCancelled() },
                )

                ReaderSheet.AI_ASSIST -> AiAssistSheet(
                    aiClient = callbacks.aiClient,
                    bookTitle = state.bookMeta.bookTitle,
                    chapterTitle = state.document.currentChapterTitle,
                    // S07-F2.4：从选区打开时固定用快照，正文选区被清后**不得**退化为整章正文。
                    contextText = consumerSnapshot?.text?.takeIf { it.isNotBlank() }
                        ?: state.document.contentText,
                )

                ReaderSheet.AI_EXPLAIN -> AiExplainSheet(
                    aiClient = callbacks.aiClient,
                    selectedText = consumerSnapshot?.text.orEmpty(),
                    bookTitle = state.bookMeta.bookTitle,
                    categories = inputs.categories,
                    tags = inputs.tags,
                    onCreateCategory = sheetCallbacks.onCreateCategory,
                    onCreateTag = sheetCallbacks.onCreateTag,
                    onSaveInspiration = { body, tags, categoryIds ->
                        // 文字与 locator 同源于打开时快照（要求 7）。
                        sheetCallbacks.onSaveAiExplainInspiration(
                            body,
                            consumerSnapshot?.text.orEmpty(),
                            snapshotLocator(),
                            tags,
                            categoryIds,
                        )
                    },
                )

                // R3-X1：离线词典查词面板（只在首选词典＝离线时由此入口打开）
                ReaderSheet.DICTIONARY -> DictionarySheet(
                    word = state.ui.dictionaryWord,
                    onDismiss = sheetCallbacks.onDismiss,
                )

                ReaderSheet.PROGRESS -> ProgressSheet(
                    epubBook = state.document.epubBook,
                    chapterIndex = state.document.chapterIndex,
                    currentChapterTitle = state.document.currentChapterTitle,
                    progressPercent = state.stats.progressPercent,
                    activeReadingMs = state.stats.activeReadingMs,
                    readerSpeed = state.stats.readerSpeed,
                    estimatedRemainingMs = state.stats.estimatedRemainingMs,
                    savedBookReadingMs = state.stats.savedBookReadingMs,
                    inspirationsCount = state.stats.inspirationsCount,
                    bookmarksCount = state.stats.bookmarksCount,
                    onChapter = { sheetCallbacks.goToChapter(it) },
                    onSeekPercent = { sheetCallbacks.seekToPercent(it) },
                    isTxt = state.document.isTxt,
                )

                ReaderSheet.SEARCH -> SearchSheet(
                    document = state.document.epubDocument ?: state.document.markdownDocument,
                    txtDocument = state.document.txtStreamingDocument,
                    plainContent = state.document.plainContent,
                    chapterStartOffsets = state.document.chapterStartOffsets,
                    chapterTitles = state.document.chapterTitles,
                    totalChars = state.document.bookIndex?.totalChars
                        ?: state.document.txtStreamingDocument?.totalChars
                        ?: state.document.markdownDocument?.totalChars
                        ?: 0,
                    isTxt = state.document.isTxt,
                    query = state.ui.searchQuery,
                    onQueryChange = sheetCallbacks.onSearchQueryChange,
                    session = searchSession,
                    // 点击具体搜索结果：选中后关闭 Sheet，让正文命中可见（上一处/下一处留在 Sheet 内）
                    onResultSelected = { sheetCallbacks.onDismiss() },
                )

                ReaderSheet.BOOK_INFO -> BookInfoSheet(
                    bookTitle = state.bookMeta.bookTitle,
                    bookAuthor = state.bookMeta.bookAuthor,
                    bookFormat = if (state.document.epubBook != null) "EPUB" else "TXT",
                    chapterCount = state.document.epubBook?.chapters?.size ?: 0,
                    wordCount = state.stats.documentWordCount,
                    currentChapterTitle = state.document.currentChapterTitle,
                    progressPercent = state.stats.progressPercent,
                    activeReadingMs = state.stats.activeReadingMs,
                    savedReadingMs = state.stats.savedBookReadingMs,
                    sessionsCount = inputs.sessions.size,
                    sourceFile = state.bookMeta.bookOriginalFile,
                    onOpenSettings = sheetCallbacks.onOpenSettings,
                    onDelete = {
                        callbacks.onAction(ReaderAction.DeleteBook(bid))
                        callbacks.onBack()
                    },
                )

                // READER-PAGES-v1：TOC / SETTINGS / THEME / INSPIRATION 已分派到全屏附页容器
                //（见上方 ReaderPageDialogHost），本弹层只剩短弹层分支；else 恒不达。
                else -> Unit
            }
            }
        }
    }
}

/**
 * READER-PAGES-v1：满高「阅读设置」页内容（直接 SETTINGS 入口与 THEME 内层共用）。
 *
 * 与旧底部弹层分支的差异只有两处：满高渲染（[SettingsSheet] `fullScreen = true`，
 * 解除旧 `maxHeightFraction = 0.62f` 约束）、根页 Back 走 [onRootBack]（直接入口=
 * 关附页回正文；THEME 内层=回 THEME）。稳定 lambda 束（T16 P1 / R3-P1）、
 * persistProgress-before-settings、单书覆盖/重置/预设语义逐字保留。
 */
@Composable
private fun ReaderSettingsPageContent(
    paper: ReaderPaperPalette,
    state: ReaderSheetHostState,
    settingsVm: SettingsViewModel,
    bid: String,
    applyReaderEdit: (ReaderSettings) -> Unit,
    sheetCallbacks: ReaderSheetHostCallbacks,
    onRootBack: () -> Unit,
) {
    // T16 P1 修复（「翻页效果」pill 行间歇吞点击）：自动翻页 / seek 期间 ReaderScreen
    // 每帧重组，ReaderSheetHost 因携带每帧重建的不稳定 state/callbacks 而无法被跳过，
    // 于是每帧都给 SettingsSheet 传入全新身份的 onSettingsChange / onBookInfo lambda，
    // 导致设置页（含 pill 行）每帧重组——pill 的命中节点在指针 down→up 之间被重建，
    // 手势被丢弃，表现为选中描边不动、底层值也不变（idle 时不 churn 故点击始终可靠）。
    // 修法：rememberUpdatedState 保证「始终调用最新实现」，remember 固化 lambda 身份，
    // 令 SettingsSheet 在 settings / paper 未变时可被 Compose 跳过，churn 期间 pill 节点保持稳定。
    val latestOnSettingsChange by rememberUpdatedState<(ReaderSettings) -> Unit>({ updated ->
        // 设置变更会触发分页重排：先落库当前进度，避免按旧进度恢复
        sheetCallbacks.onPersistProgress()
        // R3-P1：不再无条件写全局；按作用范围路由到全局 / 本书覆盖
        applyReaderEdit(updated)
    })
    val stableOnSettingsChange = remember { { updated: ReaderSettings -> latestOnSettingsChange(updated) } }
    val latestOnBookInfo by rememberUpdatedState(sheetCallbacks.onOpenBookInfo)
    val stableOnBookInfo = remember { { latestOnBookInfo() } }
    // R3-P1：本书覆盖操作回调。身份用 remember 固化，理由同上（避免 SettingsSheet
    // 因 lambda 每帧换新身份而无法被 Compose 跳过）。
    val latestClearOverride by rememberUpdatedState<(ReaderOverrideKey) -> Unit> { key ->
        settingsVm.clearOverride(bid, key)
    }
    val stableClearOverride = remember { { key: ReaderOverrideKey -> latestClearOverride(key) } }
    val latestClearAllOverrides by rememberUpdatedState<() -> Unit> { settingsVm.clearAllOverrides(bid) }
    val stableClearAllOverrides = remember { { latestClearAllOverrides() } }
    val latestApplyPreset by rememberUpdatedState<(ReadingPreset) -> Unit> { preset ->
        when (state.ui.settingsScope) {
            ReaderSettingsScope.BOOK -> settingsVm.applyPresetToBook(bid, preset)
            ReaderSettingsScope.GLOBAL -> settingsVm.applyPresetToGlobal(preset)
        }
    }
    val stableApplyPreset = remember { { preset: ReadingPreset -> latestApplyPreset(preset) } }
    val latestResetGlobalDefaults by rememberUpdatedState<() -> Unit> {
        settingsVm.resetGlobalReaderDefaults()
    }
    val stableResetGlobalDefaults = remember { { latestResetGlobalDefaults() } }
    val latestScopeChange by rememberUpdatedState<(ReaderSettingsScope) -> Unit> { scope ->
        sheetCallbacks.onSettingsScopeChange(scope)
    }
    val stableScopeChange = remember { { scope: ReaderSettingsScope -> latestScopeChange(scope) } }
    // 附页统一 Back 第一层：键盘先收；根页时回来源（子页 Back 由 SettingsSheet 自带的
    // BackHandler 处理——它在子组合里后注册，回调栈优先级更高，两层不会同时触发）。
    ReaderPageBackHandler(onBack = onRootBack)
    SettingsSheet(
        paper = paper,
        settings = state.ui.readerSettings,
        onSettingsChange = stableOnSettingsChange,
        onBookInfo = stableOnBookInfo,
        scope = state.ui.settingsScope,
        onScopeChange = stableScopeChange,
        overrides = state.ui.bookReaderOverrides,
        globalSettings = state.ui.globalReaderSettings,
        onClearOverride = stableClearOverride,
        onClearAllOverrides = stableClearAllOverrides,
        onApplyPreset = stableApplyPreset,
        onResetGlobalDefaults = stableResetGlobalDefaults,
        fullScreen = true,
        onRootBack = onRootBack,
    )
}

/**
 * 书签的精确回跳坐标（S09-A-F1）。
 *
 * 「无可用精确位置」只有一种形态：`absOffset = -1`、`chapterIndex = 0`、`charOffset = -1`、
 * `locatorJson = null`。这与修复前 `position == null` 时的形态**逐字段相同**，
 * 因此现有「locator 为空 → 用 progress_percent 近似回跳」的回退链完全不受影响。
 */
internal data class BookmarkPositionFields(
    val absOffset: Int,
    val chapterIndex: Int,
    val charOffset: Int,
    val locatorJson: String?,
)

/**
 * 从 [ReaderPageIndexManager] 的当前页位置推导书签坐标，**只接受属于当前这本书的位置**。
 *
 * 为什么必须有这道校验：[ReaderPagePosition] 是进程内单例状态，只在分页阅读分支被写入，
 * 且 `clear()` 目前没有调用点。于是「先读 A 书（分页）→ 再打开 B 书（滚动模式，
 * 或新书首帧尚未上报）」时，manager 里残留的仍是 **A 书**的 absStart / 页号 / 百分比。
 * 修复前 `onAddBookmark` 直接把它当作 B 书的坐标持久化，
 * 于是 B 书的书签会跳到 A 书的位置 —— 属跨书数据污染（P1）。
 *
 * 语义集中在本函数里，**不在各个字段上重复写判断**：`absOffset`、`chapterIndex`、
 * `charOffset`、`locatorJson` 全部由同一份过滤后的位置推导，
 * 避免出现「locator 过滤了、坐标没过滤」这类漂移。
 *
 * 不伪造：位置不可用时绝不构造 `absOffset = 0` 之类的假锚点，
 * 而是返回与 null 相同的回退形态，交给既有百分比回退链。
 * EPUB 的章偏移解析（[EpubLocatorMapping]）同样只在通过校验的位置上执行。
 */
internal fun bookmarkPositionFields(
    position: ReaderPagePosition?,
    bookId: String,
    isEpub: Boolean,
    chapterStartOffsets: List<Int>,
): BookmarkPositionFields {
    val owned = position?.takeIf { it.bookId == bookId } ?: return NO_EXACT_BOOKMARK_POSITION

    val abs = owned.absStart
    val (chapterIndex, charOffset) = if (isEpub) {
        EpubLocatorMapping.toChapterOffset(abs, chapterStartOffsets)
    } else {
        0 to abs
    }
    val locatorJson = if (isEpub) {
        LocatorBuilder.forPosition(abs, chapterIndex, charOffset, null)
    } else {
        LocatorBuilder.forPlain(abs, null)
    }
    return BookmarkPositionFields(
        absOffset = abs,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        locatorJson = locatorJson,
    )
}

/** 无可用精确位置时的书签字段形态：与修复前的 `position == null` 分支逐字段一致。 */
private val NO_EXACT_BOOKMARK_POSITION = BookmarkPositionFields(
    absOffset = -1,
    chapterIndex = 0,
    charOffset = -1,
    locatorJson = null,
)
