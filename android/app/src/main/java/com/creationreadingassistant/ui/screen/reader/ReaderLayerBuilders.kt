@file:OptIn(ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.reader

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.Clipboard
import com.creationreadingassistant.ui.util.copyText
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.settings.PerBookOverrides
import com.creationreadingassistant.data.settings.ReaderDayNightToggle
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.ReaderSettingsScope
import com.creationreadingassistant.data.settings.SelectionActionSettings
import com.creationreadingassistant.data.settings.SelectionActions
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.feature.reader.pager.PagedReplacementAvailability
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.ui.screen.reader.ReaderChromeAction
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import com.creationreadingassistant.ui.screen.reader.tts.TtsEngineHost
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent
import com.creationreadingassistant.ui.viewmodel.PendingTxtRuleAnchor
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanStatus
import java.util.UUID

/**
 * 阅读器三层（正文宿主 / 覆盖层 / 底部弹层）State 与 Callbacks 的纯构造函数集合。
 *
 * Phase 1 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 主函数中抽出
 * 6 处「层 State/Callbacks 构造」逻辑，逐字搬运每个 lambda 体，不改任何行为。读捕获的
 * 局部 val/var 作为值参数传入；写捕获的局部 var 改为 `onXxxChange: (T) -> Unit` setter
 * 参数（ReaderScreen 传 `{ xxx = it }`）；调用的局部 fun 作为对应类型的函数参数传入
 * （ReaderScreen 传 `::funName`）。
 *
 * 这些 builder 是普通 `fun`（非 @Composable），每次 recomposition 由 ReaderScreen 调用并
 * 读取最新状态值，与原内联构造行为一致。顶层 helper（[nowIso] / [buildInspirationPayload]
 * / [blockIndexForChapterOffset]）与本文件同包，直接调用。
 */

/**
 * 构造正文宿主所需的只读展示数据。B1 状态袋瘦身：原 49 个平铺参数按域分组为
 * 4 个子对象（[ReaderContentSettings] / [ReaderSelectionState] / [ReaderPagingState] /
 * [ReaderContentSourceState]）接收并组装，字段值仍 1:1 来自 ReaderScreen 主函数的局部状态。
 */
internal fun buildReaderContentHostState(
    settings: ReaderContentSettings,
    selection: ReaderSelectionState,
    paging: ReaderPagingState,
    source: ReaderContentSourceState,
): ReaderContentHostState = ReaderContentHostState(
    settings = settings,
    selection = selection,
    paging = paging,
    source = source,
)

/**
 * 构造正文宿主所需的回调集合。lambda 体逐字搬运自 ReaderScreen，对局部 var 的写改为
 * setter 调用，局部 fun（[goToChapter] / [showNotice]）作为函数参数传入。
 *
 * B2：控件显隐与选区写入改走 [ReaderAction]（VM 唯一真源）。onToggleControls 用
 * 无参 [ReaderAction.ToggleControls]（VM 侧取反），不捕获任何快照，天然规避
 * PagedReaderHost pointerInput(controller) 冻结闭包导致的「只能关、不能开」问题。
 */
internal fun buildReaderContentHostCallbacks(
    onAction: (ReaderAction) -> Unit,
    onPagedAbsOffsetChange: (Int) -> Unit,
    onPagedPercentChange: (Float) -> Unit,
    onPendingInitialPositionChange: (Boolean) -> Unit,
    onAutoPagingActiveChange: (Boolean) -> Unit,
    goToChapter: (Int) -> Unit,
    syncPagedChapter: (Int) -> Unit,
    showNotice: (String) -> Unit,
    onSearchScrollFocusRequestConsumed: () -> Unit,
    onPersistProgress: () -> Unit,
    onPlainTextUserScrollInput: () -> Unit,
): ReaderContentHostCallbacks {
    var lastPersistAt = 0L
    return ReaderContentHostCallbacks(
    onPagedPositionChanged = { off, pct, chapterToGo ->
        onPagedAbsOffsetChange(off)
        onPagedPercentChange(pct)
        onPendingInitialPositionChange(false)
        // 翻页节流保存（问题5 补全）：跨章立即落库；同章 ≥5 秒存一次，防频繁写库。
        // 进程被杀/切后台被回收时，最多丢最近 5 秒内的翻页，不再回到上次暂停的旧进度。
        val now = android.os.SystemClock.elapsedRealtime()
        if (chapterToGo != null || now - lastPersistAt >= THROTTLE_SAVE_MS) {
            lastPersistAt = now
            onPersistProgress()
        }
        // 被动跨章同步：只更新章状态，不写 jumpRequest / 章首进度，
        // 避免把已停在正确页位（上一章末页）的阅读器拽回章首。
        if (chapterToGo != null) syncPagedChapter(chapterToGo)
    },
    onToggleControls = { onAction(ReaderAction.ToggleControls()) },
    // 翻页后立即隐藏菜单：走状态机 PageTurn，与 autoHideSeconds 是否 0 无关。
    onHideControls = { onAction(ReaderAction.PageTurn) },
    // S07-F2：空文本的 onSelect 就是「撤掉选区」的信号（单击/双击撤选、滚动单元回收、
    // 以及分页表面在提交翻页时的主动通知）。它必须收敛到唯一的完整清除语义
    // ClearSelection，而不是走 SetSelectedText 只复位四个字段、把 showColorRow 留在打开态。
    onSelect = { text, globalOffset, rangeStart, sourceEnd ->
        if (text.isBlank()) {
            onAction(ReaderAction.ClearSelection)
        } else {
            onAction(ReaderAction.SetSelectedText(text, rangeStart, globalOffset, sourceEnd))
        }
    },
    onAutoPagingFinished = {
        onAutoPagingActiveChange(false)
        showNotice("已读到书末")
    },
    onStopAutoPaging = { onAutoPagingActiveChange(false) },
    onGoToChapter = { goToChapter(it) },
    onPlainTextUserScrollInput = onPlainTextUserScrollInput,
    onSearchScrollFocusRequestConsumed = onSearchScrollFocusRequestConsumed,
)
}

/** 翻页节流保存间隔：同章 ≥ 5 秒落库一次；跨章立即落库。 */
private const val THROTTLE_SAVE_MS = 5_000L

/**
 * 「复制」动作的用户提示。
 *
 * [Clipboard.copyText] 的返回值就是**内容是否真的进了剪贴板**：空白选区直接返回 false
 * （且不触碰剪贴板，见 [copyableSelection]），系统剪贴板服务异常也会降级为 false。
 * 忽略它并无条件提示「已复制」，会在内容根本没写进去时给出一次虚假成功 ——
 * 用户不会再去重新复制，等于静默丢内容。
 */
internal fun readerCopyNotice(succeeded: Boolean): String =
    if (succeeded) "已复制" else "复制失败，请重试"

/**
 * 构造覆盖层所需的只读展示数据。`isFirstChapter` / `isLastChapter` / `isEpub` / `isMarkdown` /
 * `showProgressBar` 在此处由传入的原始值计算，与原内联构造完全一致。
 */
internal fun buildReaderInteractionLayerState(
    controlsVisible: Boolean,
    selectedText: String,
    showColorRow: Boolean,
    canCreateReplaceRule: Boolean,
    showTts: Boolean,
    showReaderOverflow: Boolean,
    autoPagingActive: Boolean,
    autoPageSpeed: Int,
    progressPercent: Float,
    bookTitle: String,
    currentChapterTitle: String,
    chapterProgress: Float,
    chapterIndex: Int,
    epubBook: EpubBook?,
    isMarkdown: Boolean,
    isLoading: Boolean,
    error: String?,
    readerSettings: ReaderSettings,
    paper: ReaderPaperPalette,
    temporaryInspection: Boolean = false,
    hasReturnableTarget: Boolean = false,
    selectionActions: SelectionActionSettings = SelectionActionSettings(),
    /** R3-X1：AI 是否已配置可用；未配置时「AI 解读」不会进入选区工具条。 */
    aiConfigured: Boolean = true,
    /**
     * M-BUG-08：TXT 书的当前章序号与章总数。
     *
     * 仅当「既非 EPUB 也非 Markdown」（即纯 TXT）时参与上一章/下一章按钮的边界判定——
     * EPUB 与 Markdown 仍走各自的索引口径，行为不变。
     */
    txtChapterIndex: Int = 0,
    txtChapterCount: Int = 0,
    /** 外壳当前是否深色；仅用于解析 `follow` 纸张的明暗（一键日/夜灯绳的当前态）。 */
    appDark: Boolean = false,
): ReaderInteractionLayerState = run {
    // M-BUG-08：章节导航口径。**纯 TXT**（既非 EPUB 也非 Markdown）用 txt 索引/总数，
    // 修掉原先 `isLastChapter = epubBook == null || …` 导致 TXT 恒为末章、两个按钮都禁用的问题。
    // EPUB 沿用 chapterIndex / epubBook.chapters（原本就正确）；Markdown 的章总数此处不可得，
    // 维持原有行为，不回退、不新增风险。
    val usesTxtChapterNav = epubBook == null && !isMarkdown
    val navChapterIndex = if (usesTxtChapterNav) txtChapterIndex else chapterIndex
    val navChapterCount = if (usesTxtChapterNav) txtChapterCount else (epubBook?.chapters?.size ?: 0)
    // 口径转换：[readerChapterNavBounds] 返回「能否上/下一章」，而 [ReaderInteractionLayerState]
    // 的字段是「是否首章/末章」，消费方按 `enabled = !isFirstChapter` 取反。两者语义相反，
    // 直接透传会让中间章同时被判为首章+末章 → 两个按钮一起禁用、点击穿透到翻页手势。
    val navBounds = readerChapterEdgeFlags(navChapterIndex, navChapterCount)
    ReaderInteractionLayerState(
    controlsVisible = controlsVisible,
    selectedText = selectedText,
    showColorRow = showColorRow,
    canCreateReplaceRule = canCreateReplaceRule,
    selectionActions = selectionActions,
    aiConfigured = aiConfigured,
    showTts = showTts,
    showReaderOverflow = showReaderOverflow,
    autoPagingActive = autoPagingActive,
    autoPageSpeed = autoPageSpeed,
    progressPercent = progressPercent,
    bookTitle = bookTitle,
    currentChapterTitle = currentChapterTitle,
    chapterProgress = chapterProgress,
    isFirstChapter = navBounds.first,
    isLastChapter = navBounds.second,
    isEpub = epubBook != null,
    isMarkdown = isMarkdown,
    isLoading = isLoading,
    error = error,
    showProgressBar = readerSettings.showProgressBar,
    paper = paper,
    temporaryInspection = temporaryInspection,
    hasReturnableTarget = hasReturnableTarget,
    // 一键日/夜灯绳的当前态：按**纸张明暗**判定（`follow` 交给外壳解析），
    // 与用户实际看到的内容一致，而不是外壳的 themeMode。
    isNight = ReaderDayNightToggle.isNight(readerSettings.background, appDark),
    )
}

/**
 * 构造覆盖层所需的回调集合。`onPickColor` 的完整高亮保存逻辑逐字搬运：locator 快照、
 * 先存局部 `snapshotText` 再清选区、[HighlightEntity] 字段、[nowIso] 时间戳全部保持原样。
 *
 * B2：原对本地副本 var 的 setter 写入全部改走 [ReaderAction]（VM 唯一真源）：
 * - onOverflowExpandedChange → SetShowOverflow；onCloseTts → SetShowTts(false)；
 * - onToggleColor → ToggleColorRow（VM 侧取反，不捕获 showColorRow 快照）；
 * - onPickColor / onClearSelection 尾部的「清选区+关颜色行」与 [ReaderAction.ClearSelection]
 *   reducer 语义完全一致（text=""、两个偏移 -1、showColorRow=false），改为单个 action。
 */
@Suppress("LongParameterList")
internal fun buildReaderInteractionLayerCallbacks(
    chapterIndex: Int,
    settingsVm: SettingsViewModel,
    tts: TtsEngineHost,
    selectedText: String,
    selectedSourceLength: Int?,
    context: Context,
    bid: String,
    currentChapterTitle: String,
    progressPercent: Float,
    clipboard: Clipboard,
    onAction: (ReaderAction) -> Unit,
    handleChromeAction: (ReaderChromeAction) -> Unit,
    seekToChapterPercent: (Float) -> Unit,
    goToChapter: (Int) -> Unit,
    onAutoPageSpeedChange: (Int) -> Unit,
    computeLocatorJson: () -> String?,
    showNotice: (String) -> Unit,
    /** R3-X1：选区动作与查询目标配置（决定查询地址与「字典」动作的去向）。 */
    selectionActions: SelectionActionSettings = SelectionActionSettings(),
    /** R3-X1：首选词典＝离线时，用选中文字打开阅读器内词典面板。 */
    onOpenDictionary: (String) -> Unit = {},
    /**
     * M-BUG-08：纯 TXT 的章节导航素材。
     *
     * 当 [usesTxtChapterNav] 为 true 时，上一章/下一章改为按 [txtChapters] 的章首偏移
     * 走 [jumpToPlainOffset]——因为 `goToChapter` 对「非 EPUB 且非 Markdown」直接 return，
     * 点下去不会有任何反应。
     */
    usesTxtChapterNav: Boolean = false,
    txtChapters: List<com.creationreadingassistant.feature.reader.doc.DocChapter> = emptyList(),
    txtChapterIndex: Int = 0,
    jumpToPlainOffset: (Int) -> Unit = {},
    /**
     * M-BUG-08：「上一章」专用跳章——落到目标章**末尾**。
     * 非 TXT（EPUB/Markdown）路径用它，对齐 md3 `moveToPrevChapter(toLast = true)`。
     */
    goToPrevChapter: (Int) -> Unit = {},
    /**
     * 拉灯绳：一键切换日间 / 夜间（阅读纸张 + 应用外壳联动）。
     *
     * 实现在 [com.creationreadingassistant.ui.screen.reader.ReaderScaffold]（那里同时持有
     * 有效设置、全局设置、作用范围与 settingsVm），本层只透传触发入口。
     */
    onToggleDayNight: () -> Unit = {},
): ReaderInteractionLayerCallbacks = ReaderInteractionLayerCallbacks(
    onToggleDayNight = onToggleDayNight,
    onOverflowExpandedChange = { onAction(ReaderAction.SetShowOverflow(it)) },
    onChromeAction = handleChromeAction,
    onProgressScrubberInteractionStarted = { onAction(ReaderAction.ProgressScrubberInteractionStarted) },
    onSeekChapterPercent = { seekToChapterPercent(it) },
    onPrevChapter = {
        // M-BUG-08：「上一章」统一落到**上一章末尾**（对齐 md3 moveToPrevChapter(toLast = true)）。
        if (usesTxtChapterNav) {
            // S07-F2：TXT 路径走 jumpToPlainOffset，不经过 LoadChapter，必须显式清选区；
            // 无可跳目标时（首章 / 目录未识别）什么都不发生，也就**不清**。
            txtScrollChapterTargetOffset(-1, txtChapters, txtChapterIndex)?.let { target ->
                onAction(ReaderAction.ClearSelection)
                jumpToPlainOffset(target)
            }
        } else {
            goToPrevChapter(chapterIndex - 1)
        }
    },
    onNextChapter = {
        if (usesTxtChapterNav) {
            txtScrollChapterTargetOffset(1, txtChapters, txtChapterIndex)?.let { target ->
                onAction(ReaderAction.ClearSelection)
                jumpToPlainOffset(target)
            }
        } else {
            goToChapter(chapterIndex + 1)
        }
    },
    onAutoPageSpeedChange = onAutoPageSpeedChange,
    onPersistTts = { r, p, v, id, t, e ->
        settingsVm.updateReader {
            copy(
                ttsSpeechRate = r,
                ttsPitch = p,
                ttsVolume = v,
                ttsVoiceId = id,
                ttsTimedStopMinutes = t,
                ttsEngine = e,
            )
        }
    },
    onCloseTts = { tts.stop(); onAction(ReaderAction.SetShowTts(false)) },
    onToggleColor = { onAction(ReaderAction.ToggleColorRow) },
    onPickColor = { color ->
        val locator = computeLocatorJson()
        // 先快照局部变量：下面马上把 selectedText 清空，
        // ViewModel IO 协程晚一步才读的话高亮就存成空串。
        val snapshotText = selectedText
        onAction(ReaderAction.SaveHighlight(
            HighlightEntity(
                id = UUID.randomUUID().toString(),
                book_id = bid,
                text = snapshotText,
                note = null,
                color = color,
                chapter_title = currentChapterTitle.ifBlank { null },
                progress_percent = progressPercent,
                locator_json = locator,
                payload = buildHighlightPayload(selectedSourceLength),
                created_at = nowIso(),
                device_id = null,
                revision = 1,
                updated_at = nowIso(),
                deleted_at = null,
            ),
        ))
        onAction(ReaderAction.ClearSelection)
        showNotice("已高亮")
    },
    onAiExplain = { onAction(ReaderAction.OpenSheet(ReaderSheet.AI_EXPLAIN)) },
    onInspiration = { onAction(ReaderAction.OpenSheet(ReaderSheet.INSPIRATION)) },
    onNote = { onAction(ReaderAction.SetNoteOpen(true)) },
    onCopy = {
        val copied = clipboard.copyText(selectedText, label = "reader_selection")
        showNotice(readerCopyNotice(copied))
    },
    onBrowser = {
        context.executeSelectionBrowser(
            selectedText = selectedText,
            template = selectionActions.browserUrlTemplate,
            showNotice = showNotice,
        )
    },
    onDictionary = {
        context.executeSelectionDictionary(
            selectedText = selectedText,
            mode = selectionActions.dictionaryMode,
            template = selectionActions.dictionaryUrlTemplate,
            onOpenOffline = onOpenDictionary,
            showNotice = showNotice,
        )
    },
    onReplace = { onAction(ReaderAction.OpenSheet(ReaderSheet.RULES)) },
    onSearch = {
        readerSelectionSearchActions(selectedText).forEach(onAction)
    },
    onClearSelection = { onAction(ReaderAction.ClearSelection) },
)

/**
 * 构造底部弹层分发所需的只读展示数据。`txtChapterTitles` 由 [txtChapters] 现场映射，
 * `recentChapters` 由 [SnapshotStateList] 现场转 `List`，与原内联构造一致。
 */
@Suppress("LongParameterList")
internal fun buildReaderSheetHostState(pagerReplacementAvailability: PagedReplacementAvailability, epubBook: EpubBook?,
    epubDocument: ReaderDocument?,
    markdownDocument: ReaderDocument?,
    txtStreamingDocument: PlainTextDocument?,
    plainContent: String,
    chapterIndex: Int,
    txtChapterIndex: Int,
    txtChapters: List<DocChapter>,
    currentChapterTitle: String,
    progressPercent: Float,
    bookTitle: String,
    bookAuthor: String?,
    bookOriginalFile: String?,
    selectedText: String,
    contentText: String,
    readerSettings: ReaderSettings,
    pagerEngineOn: Boolean,
    activeReadingMs: Long,
    savedBookReadingMs: Long,
    estimatedRemainingMs: Long,
    readerSpeed: Int,
    inspirationsCount: Int,
    bookmarksCount: Int,
    documentWordCount: Int,
    isTxt: Boolean,
    searchQuery: String,
    chapterStartOffsets: List<Int>,
    chapterTitles: List<String>,
    bookIndex: BookIndex?,
    txtTocRuleId: String,
    txtRulePreviews: Map<String, List<TxtChapterDetector.Chapter>>,
    txtRuleScanStatus: TxtRuleScanStatus?,
    recentChapters: SnapshotStateList<Int>,
    appDark: Boolean,
    globalReaderSettings: ReaderSettings,
    bookReaderOverrides: PerBookOverrides,
    settingsScope: ReaderSettingsScope,
    dictionaryWord: String,
): ReaderSheetHostState = ReaderSheetHostState(
    // B1 状态袋瘦身：按域组装 4 个分组对象，字段值与原平铺 1:1。
    document = ReaderSheetDocumentState(
        epubBook = epubBook,
        epubDocument = epubDocument,
        markdownDocument = markdownDocument,
        txtStreamingDocument = txtStreamingDocument,
        plainContent = plainContent,
        chapterIndex = chapterIndex,
        txtChapterIndex = txtChapterIndex,
        txtChapterTitles = txtChapters.map { it.title },
        currentChapterTitle = currentChapterTitle,
        contentText = contentText,
        isTxt = isTxt,
        chapterStartOffsets = chapterStartOffsets,
        chapterTitles = chapterTitles,
        chapterWordCountLabels = readerTocWordCountLabels(
            when {
                epubBook != null -> epubDocument?.chapters.orEmpty()
                markdownDocument != null -> markdownDocument.chapters
                else -> txtChapters
            },
        ),
        bookIndex = bookIndex,
        txtTocRuleId = txtTocRuleId,
        txtRulePreviews = txtRulePreviews,
        txtRuleScanStatus = txtRuleScanStatus,
    ),
    bookMeta = ReaderSheetBookMetaState(
        bookTitle = bookTitle,
        bookAuthor = bookAuthor,
        bookOriginalFile = bookOriginalFile,
    ),
    stats = ReaderSheetStatsState(
        progressPercent = progressPercent,
        activeReadingMs = activeReadingMs,
        savedBookReadingMs = savedBookReadingMs,
        estimatedRemainingMs = estimatedRemainingMs,
        readerSpeed = readerSpeed,
        inspirationsCount = inspirationsCount,
        bookmarksCount = bookmarksCount,
        documentWordCount = documentWordCount,
    ),
    ui = ReaderSheetUiState(
        selectedText = selectedText,
        readerSettings = readerSettings,
        pagerEngineOn = pagerEngineOn,
        searchQuery = searchQuery,
        recentChapters = recentChapters.toList(),
        appDark = appDark,
        replacementAvailability = pagerReplacementAvailability,
        globalReaderSettings = globalReaderSettings,
        bookReaderOverrides = bookReaderOverrides,
        settingsScope = settingsScope,
        dictionaryWord = dictionaryWord,
    ),
)

/**
 * 构造底部弹层分发所需的回调集合。`onPickChapter` / `onTxtRule` /
 * `onExportHighlights` / `onSaveAiExplainInspiration` / `onSaveInspiration` /
 * `onCreateCategory` / `onCreateTag` 的完整逻辑体逐字搬运：
 * - `recentChapters` 直接传 [SnapshotStateList] 引用，`.add/.remove` 保持原行为；
 * - `pagedJumpRequest` 直接传 [MutableState] 引用，`.value =` 写保持原行为；
 *
 * B2：原对本地副本 var 的 setter 写入改走 [ReaderAction]：onSheetChange(null) →
 * CloseSheet，onSheetChange(x) → OpenSheet(x)，onSearchQueryChange → SetSearchQuery，
 * onSaveInspiration 尾部的 onSelectedTextChange("") → ClearSelection（选区已消费完毕，
 * 附带重置偏移/showColorRow 无可观测差异）。
 *
 * 搜索跳转已从本层移除：命中选择/上一处/下一处由 SearchSheet 直接驱动
 * BookSearchSession（ReaderScreen 持有的 seam），统一 target 的消费在
 * SearchHitNavigationEffect（reader/ReaderSearchEffects.kt）完成，不再在此拼 offset。
 */
@Suppress("LongParameterList")
internal fun buildReaderSheetHostCallbacks(
    epubBook: EpubBook?,
    markdownDocument: ReaderDocument?,
    txtChapters: List<DocChapter>,
    visiblePlainOffset: Int,
    textContent: ReaderLoadedContent.Text?,
    txtStreamingDocument: PlainTextDocument?,
    bid: String,
    bookTitle: String,
    highlights: List<HighlightEntity>,
    context: Context,
    selectedText: String,
    currentChapterTitle: String,
    progressPercent: Float,
    bookAuthor: String?,
    recentChapters: SnapshotStateList<Int>,
    pagedJumpRequest: MutableState<Int?>,
    onTxtTocRuleIdChange: (String) -> Unit,
    onPendingTxtRuleAnchorOffsetChange: (PendingTxtRuleAnchor) -> Unit,
    onPendingHighlightIdChange: (String?) -> Unit,
    goToChapter: (Int) -> Unit,
    seekToPercent: (Float) -> Unit,
    jumpToPlainOffset: (Int) -> Unit,
    showNotice: (String) -> Unit,
    onAction: (ReaderAction) -> Unit,
    onCancelTxtScan: () -> Unit,
    onPersistProgress: () -> Unit,
    onSettingsScopeChange: (ReaderSettingsScope) -> Unit,
    /** R5-I2：当前选区的 source locator（与高亮同源）；灵感/摘录落库据此携带精确回源坐标。 */
    computeLocatorJson: () -> String? = { null },
): ReaderSheetHostCallbacks = ReaderSheetHostCallbacks(
    onDismiss = { onAction(ReaderAction.CloseSheet) },
    onOpenSettings = { onAction(ReaderAction.OpenSheet(ReaderSheet.SETTINGS)) },
    onOpenBookInfo = { onAction(ReaderAction.OpenSheet(ReaderSheet.BOOK_INFO)) },
    goToChapter = { goToChapter(it) },
    seekToPercent = { seekToPercent(it) },
    jumpToPlainOffset = { jumpToPlainOffset(it) },
    showNotice = { showNotice(it) },
    onSearchQueryChange = { onAction(ReaderAction.SetSearchQuery(it)) },
    onClearSelectedText = { onAction(ReaderAction.ClearSelection) },
    onPickChapter = {
        if (!recentChapters.contains(it)) {
            recentChapters.add(0, it)
            if (recentChapters.size > 5) recentChapters.removeAt(recentChapters.lastIndex)
        }
        when (
            val target = readerTocPickTarget(
                isEpub = epubBook != null,
                isMarkdown = markdownDocument != null,
                txtChapters = txtChapters,
                selectedIndex = it,
            )
        ) {
            // Chapter 分支经 goToChapter → LoadChapter，清选区已在 VM 侧收敛。
            is ReaderTocPickTarget.Chapter -> goToChapter(target.index)
            // S07-F2：纯 TXT 走 jumpToPlainOffset，不经过 LoadChapter，必须显式清选区。
            is ReaderTocPickTarget.PlainOffset -> {
                onAction(ReaderAction.ClearSelection)
                jumpToPlainOffset(target.offset)
            }
            // 没有任何可跳目标（未识别出目录等）→ 什么都没发生，不得清选区。
            ReaderTocPickTarget.None -> Unit
        }
        onAction(ReaderAction.CloseSheet)
    },
    onTxtRule = { ruleId ->
        onTxtTocRuleIdChange(ruleId)
        // P1-A：快速单选收敛到 Room（单一状态源）。归一化绑定后由统一的
        // 重扫策略（TxtRuleRescanPolicy）按新 profile.key 重新识别当前 TXT，
        // 保留当前 source 绝对偏移；这里不再直接发起扫描，避免与 Room 身份分叉。
        onAction(ReaderAction.ExecuteRuleCommand(bid, RuleCommand.SelectSingleTocRule(ruleId)))
        onAction(ReaderAction.SaveTxtTocRule(bid, ruleId))
    },
    onCancelTxtScan = onCancelTxtScan,
    onPersistProgress = onPersistProgress,
    // A01：标注回源统一收**已带类型前缀的 typed 目标**。
    //
    // 旧实现拆成两条回调：高亮写裸 rawId、书签写 bookmark:{id}。裸 rawId 在阅读器侧
    // 走「先高亮、后笔记」的兼容回退，因此普通笔记若借道书签回调、或高亮与笔记同 rawId，
    // 就会跳错表。收敛成单一 typed 入参后，调用方无法再传裸 id。
    // 无 locator 的历史记录仍由 SE4 按其 locator / progress 降级，不伪造偏移。
    onJumpToTypedAnnotation = { typedTarget ->
        onPendingHighlightIdChange(typedTarget)
        onAction(ReaderAction.CloseSheet)
    },
    // 书摘/笔记/书签/灵感的导出（SAF 写 .md + 分享）已内聚在 NotesSheet，
    // 不再经由回调上抛组装。
    /**
     * S07-F2.4：AI 解读 / 灵感是长生命周期消费者——摘录文字与 locator **由弹层按打开时
     * 快照传入**，二者同源，不得回读实时活动选区（后者会被 ReadingUnit dispose 清空）。
     */
    onSaveAiExplainInspiration = { body, excerpt, locatorJson, tags, categoryIds ->
        val snapshotText = excerpt
        onAction(
            ReaderAction.SaveInspiration(
                InspirationEntity(
                    id = UUID.randomUUID().toString(),
                    title = "AI 解读：${snapshotText.take(24)}",
                    body = body,
                    type = "note",
                    status = "inbox",
                    source_book_id = bid.ifBlank { null },
                    payload = buildInspirationPayload(bid, bookTitle, currentChapterTitle, snapshotText, progressPercent, tags, categoryIds, bookAuthor = bookAuthor, locatorJson = locatorJson),
                    created_at = nowIso(),
                    device_id = null,
                    revision = 1,
                    updated_at = nowIso(),
                    deleted_at = null,
                ),
            ),
        )
        onAction(ReaderAction.CloseSheet)
        showNotice("已存入灵感")
    },
    onSaveInspiration = { entityId, title, body, excerpt, locatorJson, tags, categoryIds ->
        // READER-PAGES-v1：只负责按**附页持有的稳定 id**构造实体并派发落库；
        // 「清选区 / 关页 / 成功提示」一律等 VM 的落库完成信号（InspirationSaveOutcome，
        // 按 entityId 匹配）到达后才由附页执行——不允许再提前报成功。
        // 失败重试由附页复用同一 entityId（upsert 覆盖同条，不重复写入）。
        // 文字与 locator 都来自弹层传入的同一份快照（要求 7）。
        val snapshotText = excerpt
        onAction(
            ReaderAction.SaveInspiration(
                InspirationEntity(
                    id = entityId,
                    title = title,
                    body = body,
                    type = "note",
                    status = "inbox",
                    source_book_id = bid.ifBlank { null },
                    payload = buildInspirationPayload(bid, bookTitle, currentChapterTitle, snapshotText, progressPercent, tags, categoryIds, bookAuthor = bookAuthor, locatorJson = locatorJson),
                    created_at = nowIso(),
                    device_id = null,
                    revision = 1,
                    updated_at = nowIso(),
                    deleted_at = null,
                ),
            ),
        )
    },
    onCreateCategory = { name ->
        val id = "mobile-category-${UUID.randomUUID()}"
        // 69-v2-B：把同一 id 交给事件，弹层选中值与最终落库行保持一致。
        onAction(ReaderAction.CreateCategory(id = id, name = name))
        id
    },
    onCreateTag = { name ->
        val id = "mobile-tag-${UUID.randomUUID()}"
        onAction(ReaderAction.CreateTag(name))
        id
    },
    onSettingsScopeChange = onSettingsScopeChange,
)
