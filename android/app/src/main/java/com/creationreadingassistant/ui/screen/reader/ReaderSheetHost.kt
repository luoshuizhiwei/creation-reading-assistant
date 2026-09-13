package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.creationreadingassistant.data.settings.PerBookOverrides
import com.creationreadingassistant.data.settings.ReaderOverrideKey
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.ReaderSettingsRouter
import com.creationreadingassistant.data.settings.ReaderSettingsScope
import com.creationreadingassistant.data.settings.ReadingPreset
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.pager.PagedReplacementAvailability
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import com.creationreadingassistant.ui.screen.reader.sheets.AiAssistSheet
import com.creationreadingassistant.ui.screen.reader.sheets.AiExplainSheet
import com.creationreadingassistant.ui.screen.reader.sheets.BookInfoSheet
import com.creationreadingassistant.ui.screen.reader.sheets.DictionarySheet
import com.creationreadingassistant.ui.screen.reader.sheets.InspirationSheet
import com.creationreadingassistant.ui.screen.reader.sheets.NotesSheet
import com.creationreadingassistant.ui.screen.reader.sheets.ProgressSheet
import com.creationreadingassistant.ui.screen.reader.sheets.RulesSheet
import com.creationreadingassistant.ui.screen.reader.sheets.SearchSheet
import com.creationreadingassistant.ui.screen.reader.sheets.SettingsSheet
import com.creationreadingassistant.ui.screen.reader.sheets.ThemeSheet
import com.creationreadingassistant.ui.screen.reader.sheets.TocSheet
import com.creationreadingassistant.ui.screen.reader.sheets.readerTocEntries
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.feature.reader.locator.EpubLocatorMapping
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanStatus
import com.creationreadingassistant.ui.viewmodel.matchesSession

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
    val onJumpToHighlight: (String) -> Unit,
    val onJumpToBookmark: (String) -> Unit,
    val onSaveAiExplainInspiration: (body: String, tags: List<String>, categoryIds: List<String>) -> Unit,
    /** 设置/主题变更前先持久化当前阅读进度（防重排回退到旧进度）。 */
    val onPersistProgress: () -> Unit,
    val onSaveInspiration: (title: String, body: String, tags: List<String>, categoryIds: List<String>) -> Unit,
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

    /**
     * R3-P1：书内编辑的唯一落盘入口。
     *
     * 设置面板操作的是**有效设置**（全局 + 本书覆盖），本次改动该落到哪一层由
     * [ReaderSettingsRouter] 按用户选择的 [ReaderSettingsScope] 决定：
     * 不可覆盖项永远进全局；「本书」范围下可覆盖项只写本书覆盖（绝不污染全局）。
     */
    val applyReaderEdit: (ReaderSettings) -> Unit = { updated ->
        val plan = ReaderSettingsRouter.plan(
            scope = state.ui.settingsScope,
            global = state.ui.globalReaderSettings,
            displayed = state.ui.readerSettings,
            edited = updated,
        )
        if (plan.globalDiffersFrom(state.ui.globalReaderSettings)) {
            settingsVm.updateReader { plan.global }
        }
        val hasBookScopedChange =
            plan.overridesToSet.isNotEmpty() || plan.overridesToClear.isNotEmpty()
        if (bid.isNotBlank()) {
            plan.overridesToSet.forEach { (key, value) -> settingsVm.setOverride(bid, key, value) }
            plan.overridesToClear.forEach { key -> settingsVm.clearOverride(bid, key) }
        } else if (hasBookScopedChange && state.ui.settingsScope == ReaderSettingsScope.BOOK) {
            // 「只改本书」但没有 bookId 时，覆盖项无处可写。以前这里是静默丢弃：用户看着
            // 设置已经变了，重进面板又弹回原值，且没有任何解释。宁可明确说没保存，
            // 也不要把一次未落盘的编辑伪装成成功。
            // 作用范围＝全局时不提示：那部分改动已经写进全局设置，并没有丢。
            sheetCallbacks.showNotice("无法确定当前书籍，本次「只改本书」的调整未保存")
        }
    }

    sheet?.let { type ->
        ModalBottomSheet(
            onDismissRequest = sheetCallbacks.onDismiss,
            sheetState = sheetState,
            containerColor = paper.bg,
            shape = LocalComponentSpec.current.sheetShape,
            dragHandle = { SheetHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            ReaderPaperTheme(paper) {
            when (type) {
                ReaderSheet.TOC -> {
                    val tocState = readerTocState(
                        epubTitles = state.document.epubBook?.chapters?.map { it.title },
                        markdownTitles = state.document.markdownDocument?.chapters?.map { it.title },
                        txtTitles = state.document.txtChapterTitles,
                        chapterIndex = state.document.chapterIndex,
                        txtChapterIndex = state.document.txtChapterIndex,
                    )
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
                        bookmarks = inputs.notes.filter { it.kind == "bookmark" },
                        onPickBookmark = { bm -> sheetCallbacks.onJumpToBookmark(bm.id) },
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
                    )
                }

                ReaderSheet.RULES -> RulesSheet(
                    snapshot = inputs.ruleSnapshot,
                    previewText = state.document.contentText,
                    mutationResult = inputs.ruleMutationResult,
                    replacementCapability = readerReplacementCapability(state.ui.replacementAvailability),
                    initialReplaceText = state.ui.selectedText.takeIf { it.isNotBlank() },
                    onCommand = { callbacks.onAction(ReaderAction.ExecuteRuleCommand(bid, it)) },
                    onBack = {
                        if (state.ui.selectedText.isNotBlank()) {
                            callbacks.onAction(ReaderAction.ClearSelection)
                        }
                        callbacks.onAction(ReaderAction.ClearRuleMutationResult)
                        callbacks.onAction(ReaderAction.OpenSheet(ReaderSheet.TOC))
                    },
                )

                ReaderSheet.NOTES -> NotesSheet(
                    paper = paper,
                    bookTitle = state.bookMeta.bookTitle,
                    highlights = inputs.highlights,
                    notes = inputs.notes,
                    inspirations = inputs.inspirations,
                    onAddBookmark = {
                        val pos = callbacks.pageIndexManager.position.value
                        val cso = state.document.chapterStartOffsets
                        val isEpub = state.document.epubBook != null
                        val abs = pos?.absStart ?: -1
                        val (ci, co) = if (pos != null && isEpub) {
                            EpubLocatorMapping.toChapterOffset(pos.absStart, cso)
                        } else {
                            0 to (pos?.absStart ?: -1)
                        }
                        val locatorJson = if (pos != null) {
                            if (isEpub) LocatorBuilder.forPosition(abs, ci, co, null)
                            else LocatorBuilder.forPlain(abs, null)
                        } else null
                        callbacks.onAction(
                            ReaderAction.AddBookmark(
                                bookId = bid,
                                offset = state.stats.progressPercent.toInt(),
                                title = state.document.currentChapterTitle.ifBlank { "正文" },
                                absOffset = abs,
                                chapterIndex = ci,
                                charOffset = co,
                                locatorJson = locatorJson,
                            ),
                        )
                        sheetCallbacks.showNotice("已添加书签")
                    },
                    onDeleteHighlight = { h ->
                        callbacks.onAction(ReaderAction.DeleteHighlight(h.id))
                    },
                    onDeleteNote = { n ->
                        callbacks.onAction(ReaderAction.DeleteNote(n.id))
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
                    onJumpToHighlight = { h -> sheetCallbacks.onJumpToHighlight(h.id) },
                    onJumpToBookmark = { n -> sheetCallbacks.onJumpToBookmark(n.id) },
                )

                ReaderSheet.AI_ASSIST -> AiAssistSheet(
                    aiClient = callbacks.aiClient,
                    bookTitle = state.bookMeta.bookTitle,
                    chapterTitle = state.document.currentChapterTitle,
                    contextText = state.ui.selectedText.ifBlank { state.document.contentText },
                )

                ReaderSheet.AI_EXPLAIN -> AiExplainSheet(
                    aiClient = callbacks.aiClient,
                    selectedText = state.ui.selectedText,
                    bookTitle = state.bookMeta.bookTitle,
                    categories = inputs.categories,
                    tags = inputs.tags,
                    onCreateCategory = sheetCallbacks.onCreateCategory,
                    onCreateTag = sheetCallbacks.onCreateTag,
                    onSaveInspiration = { body, tags, categoryIds ->
                        sheetCallbacks.onSaveAiExplainInspiration(body, tags, categoryIds)
                    },
                )

                ReaderSheet.INSPIRATION -> InspirationSheet(
                    bookTitle = state.bookMeta.bookTitle,
                    chapterTitle = state.document.currentChapterTitle,
                    excerpt = state.ui.selectedText,
                    progressPercent = state.stats.progressPercent,
                    categories = inputs.categories,
                    tags = inputs.tags,
                    onCreateCategory = sheetCallbacks.onCreateCategory,
                    onCreateTag = sheetCallbacks.onCreateTag,
                    onSave = { title, body, tags, categoryIds ->
                        sheetCallbacks.onSaveInspiration(title, body, tags, categoryIds)
                    },
                )

                ReaderSheet.SETTINGS -> {
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
                    )
                }

                // R3-X1：离线词典查词面板（只在首选词典＝离线时由此入口打开）
                ReaderSheet.DICTIONARY -> DictionarySheet(
                    word = state.ui.dictionaryWord,
                    onDismiss = sheetCallbacks.onDismiss,
                )

                ReaderSheet.THEME -> ThemeSheet(
                    background = state.ui.readerSettings.background,
                    appDark = state.ui.appDark,
                    onBackground = {
                        sheetCallbacks.onPersistProgress()
                        // R3-P1：纸张切换同样受作用范围约束，否则本书覆盖了纸张时这里点了没反应
                        applyReaderEdit(state.ui.readerSettings.copy(background = it))
                    },
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
            }
            }
        }
    }
}
