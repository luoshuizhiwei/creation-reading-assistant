package com.creationreadingassistant.ui.screen.reader

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.ui.components.PaperNoise
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.ReadingUnitCache
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.ReaderPageIndexManager
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.ui.screen.reader.tts.TtsController
import com.creationreadingassistant.ui.screen.reader.ReaderChromeAction
import com.creationreadingassistant.ui.screen.reader.ReaderDocumentStatus
import com.creationreadingassistant.ui.screen.reader.ReaderScreenState
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Phase 8 结构拆分：把 [com.creationreadingassistant.ui.screen.ReaderScreen] 主函数里的
 * `ReaderPaperTheme(paper) { Scaffold { Box { ... } } }` 三段式布局（含 6 个 build* 调用、
 * drawWithContent 护眼滤镜、windowInsetsPadding(DisplayCutout)、Box 内 when 分支与三个 Host）
 * 整体逐字搬运到 [ReaderScaffold]。
 *
 * 行为保真要点：
 * - 已被拆出的状态对象（[ReaderProgressState] / [ReaderDerivedState] / [PagerEngineState]）原样透传，
 *   内部按原 `progressState.xxx` / `derived.xxx` / `pagerEngine.xxx` 解构，零改动。
 * - B2 后弹层/控件类 UI 状态（controlsVisible / showTts / selectedText / selectedGlobalOffset /
 *   selectedRangeStart / sheet / noteOpen / showReaderOverflow / showColorRow / searchQuery）
 *   不再持有本地副本，直接只读消费 inputs.screenState；写入全部经 onAction 回 VM。
 *   剩余的会话局部可变 var（autoPagingActive / runtimeError / pagedAbsOffset /
 *   pagedPercent / pendingInitialPosition / txtTocRuleId / pendingTxtRuleAnchorOffset /
 *   navFocusBlockIndex / pendingHighlightId）仍打包在 [ReaderScreenMutableHolders]，
 *   内部用 `var x by holders.xxxState` 重新委托，写操作直接落回
 *   ReaderScreen 持有的真实状态，与原 `var by` delegate 语义完全一致。
 * - context / scope / clipboard / onBack / onDocumentAction / onAction / onLoadChapterBlocks /
 *   pageIndexStore / highlights / bookTitle∘ 等从 inputs∘callbacks∘loadedBook 现场取，避免透传冗余。
 * - drawWithContent 护眼滤镜、Box 背景先于 padding 铺纸色、DisplayCutout 避让、三个 Host 的
 *   BoxScope.align 定位与调用顺序全部逐字保留，保证「显示菜单前后正文测量尺寸完全相同」。
 */
internal data class ReaderScreenMutableHolders(
    val autoPagingActiveState: MutableState<Boolean>,
    val runtimeErrorState: MutableState<String?>,
    val pendingInitialPositionState: MutableState<Boolean>,
    val txtTocRuleIdState: MutableState<String>,
    val pendingTxtRuleAnchorOffsetState: MutableIntState,
    val navFocusBlockIndexState: MutableState<Int?>,
    val pendingHighlightIdState: MutableState<String?>,
)

/**
 * 集中创建 [ReaderScreenMutableHolders] 的会话局部可变状态 holder，行为与原 ReaderScreen
 * 内联的 `val xState = remember { ... }` 完全一致（key / 初始值 / 类型逐字保真）：
 * - runtimeError / pendingInitialPosition 以 `bookId` 为 remember key（与原文一致）；
 * - txtTocRuleId 以 `bid` 为 key，初始值来自 ViewModel 的 txtTocRuleIdFromVm；
 * - pendingHighlightId 初始值来自 inputs.highlightId。
 * B2：弹层/控件类 UI 状态（原 10 个 holder）已改由 VM screenState 唯一持有，此处移除。
 */
@Composable
internal fun rememberReaderScreenMutableHolders(
    bookId: String?,
    bid: String,
    txtTocRuleIdFromVm: String,
    highlightId: String?,
): ReaderScreenMutableHolders {
    val autoPagingActiveState = remember { mutableStateOf(false) }
    val runtimeErrorState = remember(bookId) { mutableStateOf<String?>(null) }
    val pendingInitialPositionState = remember(bookId) { mutableStateOf(true) }
    val txtTocRuleIdState = remember(bid) { mutableStateOf(txtTocRuleIdFromVm) }
    val pendingTxtRuleAnchorOffsetState = remember { mutableIntStateOf(-1) }
    val navFocusBlockIndexState = remember { mutableStateOf<Int?>(null) }
    val pendingHighlightIdState = remember { mutableStateOf(highlightId) }
    return ReaderScreenMutableHolders(
        autoPagingActiveState = autoPagingActiveState,
        runtimeErrorState = runtimeErrorState,
        pendingInitialPositionState = pendingInitialPositionState,
        txtTocRuleIdState = txtTocRuleIdState,
        pendingTxtRuleAnchorOffsetState = pendingTxtRuleAnchorOffsetState,
        navFocusBlockIndexState = navFocusBlockIndexState,
        pendingHighlightIdState = pendingHighlightIdState,
    )
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ReaderScaffold(
    // ── 已拆出的状态对象（逐字原样透传）──
    progressState: ReaderProgressState,
    derived: ReaderDerivedState,
    pagerEngine: PagerEngineState,
    // ── 只读展示值（逐字原样传入）──
    paper: ReaderPaperPalette,
    eyeCareActive: Boolean,
    eyeFilterColor: Color,
    readerSettings: ReaderSettings,
    snackbarHost: SnackbarHostState,
    isLoading: Boolean,
    isChapterLoading: Boolean,
    error: String?,
    loadedBook: ReaderLoadedBook?,
    bid: String,
    savedEpubOffsetInChapter: Int,
    savedPlainOffset: Int,
    savedPlainPercent: Float,
    txtStreamingDocument: PlainTextDocument?,
    plainContent: String,
    textContent: ReaderLoadedContent.Text?,
    epubBook: EpubBook?,
    markdownDocument: ReaderDocument?,
    epubDocument: ReaderDocument?,
    bookIndex: BookIndex?,
    recentChapters: SnapshotStateList<Int>,
    chapterIndex: Int,
    chapterBlocks: List<DocBlock>,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
    ttsSentenceRangeInChapter: Pair<Int, Int>?,
    focusBlockIndex: Int?,
    sentenceHighlightBg: Color,
    epubBringRequester: BringIntoViewRequester,
    isTxt: Boolean,
    tts: TtsController,
    autoPagingPaused: Boolean,
    appDark: Boolean,
    activeReadingMs: Long,
    goToChapter: (Int) -> Unit,
    showNotice: (String) -> Unit,
    seekToPercent: (Float) -> Unit,
    jumpToPlainOffset: (Int) -> Unit,
    handleChromeAction: (ReaderChromeAction) -> Unit,
    computeLocatorJson: () -> String?,
    inputs: ReaderScreenInputs,
    callbacks: ReaderScreenCallbacks,
    settingsVm: SettingsViewModel,
    sheetState: SheetState,
    // ── 可变状态 holder 包（块内写操作落回 ReaderScreen 的真实状态）──
    holders: ReaderScreenMutableHolders,
) {
    // 已拆出状态对象解构（与原 progressState.xxx / derived.xxx / pagerEngine.xxx 读取一致）
    val plainListState = progressState.plainListState
    val epubListState = progressState.epubListState
    val visiblePlainOffset = progressState.visiblePlainOffset
    val contentText = progressState.contentText
    val txtChapterIndex = progressState.txtChapterIndex
    val chapterFade = progressState.chapterFade
    val currentChapterTitle = progressState.currentChapterTitle
    val progressPercent = progressState.progressPercent
    val chapterProgress = progressState.chapterProgress
    val documentWordCount = progressState.documentWordCount
    val savedBookReadingMs = progressState.savedBookReadingMs
    val readerSpeed = progressState.readerSpeed
    val estimatedRemainingMs = progressState.estimatedRemainingMs
    val bookmarksCount = progressState.bookmarksCount
    val inspirationsCount = progressState.inspirationsCount

    val chapterStartOffsets = derived.chapterStartOffsets
    val chapterTitles = derived.chapterTitles
    val readingUnits = derived.readingUnits
    val unitCache = derived.unitCache

    val pagerEngineOn = pagerEngine.pagerEngineOn
    val pagedJumpRequest = pagerEngine.pagedJumpRequest
    val pagedHardwareTurnRequest = pagerEngine.pagedHardwareTurnRequest
    val pagedAbsOffsetState = pagerEngine.pagedAbsOffsetState
    val pagedPercentState = pagerEngine.pagedPercentState
    val txtChapters = pagerEngine.txtChapters
    val txtRulePreviews = pagerEngine.txtRulePreviews
    val pagedSource = pagerEngine.pagedSource

    // 现场从 inputs / callbacks / loadedBook 取的值（与原 ReaderScreen 局部取值一致）
    val context: Context = LocalContext.current
    val scope: CoroutineScope = rememberCoroutineScope()
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val onBack: () -> Unit = callbacks.onBack
    val onDocumentAction: (ReaderAction) -> Unit = callbacks.onDocumentAction
    val onAction: (ReaderAction) -> Unit = callbacks.onAction
    val onLoadChapterBlocks: suspend (String, Int) -> List<DocBlock> = callbacks.onLoadChapterBlocks
    val pageIndexStore: PageIndexStore = callbacks.pageIndexStore
    val highlights: List<HighlightEntity> = inputs.highlights
    val bookTitle: String = loadedBook?.title ?: "未命名书籍"
    val bookAuthor: String? = loadedBook?.author
    val bookOriginalFile: String? = loadedBook?.originalFileName
    val paperFg: Color = paper.fg

    // 可变 var 从 holder 重新委托：写操作直接落回 ReaderScreen 持有的真实状态。
    var autoPagingActive by holders.autoPagingActiveState
    var runtimeError by holders.runtimeErrorState
    var pagedAbsOffset by pagedAbsOffsetState
    var pagedPercent by pagedPercentState
    var pendingInitialPosition by holders.pendingInitialPositionState
    var txtTocRuleId by holders.txtTocRuleIdState
    var pendingTxtRuleAnchorOffset by holders.pendingTxtRuleAnchorOffsetState
    var navFocusBlockIndex by holders.navFocusBlockIndexState
    var pendingHighlightId by holders.pendingHighlightIdState

    // B2：弹层/控件类 UI 状态直接只读消费 VM 的 screenState（唯一真源），不再持有本地副本。
    val screenState = inputs.screenState
    val controlsVisible = screenState.controlsVisible
    val showTts = screenState.showTts
    val selectedText = screenState.selectedText
    val selectedGlobalOffset = screenState.selectedGlobalOffset
    val selectedRangeStart = screenState.selectedRangeStart
    val sheet = screenState.sheet
    val noteOpen = screenState.noteOpen
    val showReaderOverflow = screenState.showReaderOverflow
    val showColorRow = screenState.showColorRow
    val searchQuery = screenState.searchQuery

    ReaderPaperTheme(paper) {
    Scaffold(
        modifier = Modifier.drawWithContent {
            drawContent()
            if (eyeCareActive) {
                drawRect(
                    color = paper.fg,
                    alpha = readerSettings.eyeCareIntensity.coerceIn(0, 100) / 100f,
                    blendMode = BlendMode.Multiply,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) {
        // Scaffold padding intentionally ignored: reader is full-screen immersive.
        // Background (paper.bg) must fill behind system bars; content uses
        // windowInsetsPadding(displayCutout) for notch avoidance instead.
        Box(
            Modifier
                .fillMaxSize()
                // 背景必须在 padding 之前铺：沉浸模式藏掉系统栏后，腾出的区域
                // 也要是纸色，否则那里露出的是窗口底色（黑条）
                .background(paper.bg)
                .semantics {
                    if (!isLoading && !isChapterLoading && error == null && loadedBook != null) {
                        contentDescription = "阅读正文已就绪"
                    }
                }
                // 沉浸时内容延伸进了刘海区（SHORT_EDGES），正文要让开打孔摄像头那一条；
                // 纸色背景仍然铺满整屏（在 padding 之前），所以让出来的部分不是黑边
                // 始终避让挖孔区域：无论是否沉浸模式，正文都不会被刘海/打孔遮挡
                .windowInsetsPadding(WindowInsets.displayCutout),
        ) {
            // 纸张层次：极淡上亮下暗渐变 + 中性灰度轻噪点，叠在纸色之上、正文之下；
            // 守对比度红线——绝不改 paper.fg，纹理 alpha≤0.04，亮/暗纸自适应。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(lerp(paper.bg, Color.White, 0.04f), lerp(paper.bg, Color.Black, 0.03f))))
                    .background(PaperNoise.brush(), alpha = 0.04f),
            )
            when {
                isLoading || error != null -> ReaderDocumentStatus(
                    isLoading = isLoading,
                    errorMessage = error,
                    foreground = paperFg,
                    onBack = onBack,
                    onRetry = {
                        runtimeError = null
                        onDocumentAction(ReaderAction.Retry)
                    },
                )

                else -> ReaderContentHost(
                    state = buildReaderContentHostState(
                        // B1 状态袋瘦身：按域组装 4 个分组对象，字段值与原平铺传参 1:1。
                        settings = ReaderContentSettings(
                            readerSettings = readerSettings,
                            paper = paper,
                            paperFg = paperFg,
                            sentenceHighlightBg = sentenceHighlightBg,
                        ),
                        selection = ReaderSelectionState(
                            selectedText = selectedText,
                            selectedGlobalOffset = selectedGlobalOffset,
                            selectedRangeStart = selectedRangeStart,
                        ),
                        paging = ReaderPagingState(
                            pagerEngineOn = pagerEngineOn,
                            pagedSource = pagedSource,
                            pagedAbsOffset = pagedAbsOffset,
                            pagedPercent = pagedPercent,
                            pendingInitialPosition = pendingInitialPosition,
                            savedEpubOffsetInChapter = savedEpubOffsetInChapter,
                            visiblePlainOffset = visiblePlainOffset,
                            savedPlainOffset = savedPlainOffset,
                            savedPlainPercent = savedPlainPercent,
                            autoPagingActive = autoPagingActive,
                            autoPagingPaused = autoPagingPaused,
                            pagedJumpRequest = pagedJumpRequest,
                            pagedHardwareTurnRequest = pagedHardwareTurnRequest,
                            pageIndexStore = pageIndexStore,
                            pageIndexManager = callbacks.pageIndexManager,
                        ),
                        source = ReaderContentSourceState(
                            bid = bid,
                            txtTocRuleId = txtTocRuleId,
                            bookTitle = bookTitle,
                            chapterStartOffsets = chapterStartOffsets,
                            txtStreamingDocument = txtStreamingDocument,
                            plainContent = plainContent,
                            epubBook = epubBook,
                            markdownDocument = markdownDocument,
                            chapterIndex = chapterIndex,
                            txtChapterIndex = txtChapterIndex,
                            isChapterLoading = isChapterLoading,
                            chapterBlocks = chapterBlocks,
                            epubListState = epubListState,
                            plainListState = plainListState,
                            chapterFade = chapterFade,
                            blockGlobalOffsets = blockGlobalOffsets,
                            chapterBase = chapterBase,
                            ttsSentenceRangeInChapter = ttsSentenceRangeInChapter,
                            focusBlockIndex = focusBlockIndex,
                            epubBringRequester = epubBringRequester,
                            readingUnits = readingUnits,
                            isTxt = isTxt,
                            showTts = showTts,
                            tts = tts,
                            unitCache = unitCache,
                            highlights = highlights,
                        ),
                    ),
                callbacks = buildReaderContentHostCallbacks(
                    onAction = onAction,
                    onPagedAbsOffsetChange = { pagedAbsOffset = it },
                    onPagedPercentChange = { pagedPercent = it },
                    onPendingInitialPositionChange = { pendingInitialPosition = it },
                    onAutoPagingActiveChange = { autoPagingActive = it },
                    goToChapter = goToChapter,
                    showNotice = showNotice,
                    ),
                )
            }

            // 正文覆盖层（进度条 / 顶栏 / 底栏+TTS / 选中工具条）→ reader/ReaderInteractionLayer.kt
            // 所有覆盖层用 BoxScope.align 定位，显示/隐藏不改变正文容器尺寸（不触发无关分页/重排）。
            ReaderInteractionLayer(
                state = buildReaderInteractionLayerState(
                    controlsVisible = controlsVisible,
                    selectedText = selectedText,
                    showColorRow = showColorRow,
                    showTts = showTts,
                    showReaderOverflow = showReaderOverflow,
                    autoPagingActive = autoPagingActive,
                    progressPercent = progressPercent,
                    bookTitle = bookTitle,
                    currentChapterTitle = currentChapterTitle,
                    chapterProgress = chapterProgress,
                    chapterIndex = chapterIndex,
                    epubBook = epubBook,
                    isLoading = isLoading,
                    error = error,
                    readerSettings = readerSettings,
                    paper = paper,
                ),
                callbacks = buildReaderInteractionLayerCallbacks(
                    chapterIndex = chapterIndex,
                    settingsVm = settingsVm,
                    tts = tts,
                    selectedText = selectedText,
                    bid = bid,
                    currentChapterTitle = currentChapterTitle,
                    progressPercent = progressPercent,
                    clipboard = clipboard,
                    onAction = onAction,
                    handleChromeAction = handleChromeAction,
                    seekToChapterPercent = seekToPercent,
                    goToChapter = goToChapter,
                    computeLocatorJson = computeLocatorJson,
                    showNotice = showNotice,
                ),
                tts = tts,
            )

            // 底部弹层（不新建路由，内部状态切换）→ 抽出到 reader/ReaderSheetHost.kt
            ReaderSheetHost(
                sheet = sheet,
                sheetState = sheetState,
                paper = paper,
                inputs = inputs,
                callbacks = callbacks,
                settingsVm = settingsVm,
                state = buildReaderSheetHostState(
                    epubBook = epubBook,
                    epubDocument = epubDocument,
                    txtStreamingDocument = txtStreamingDocument,
                    plainContent = plainContent,
                    chapterIndex = chapterIndex,
                    txtChapterIndex = txtChapterIndex,
                    txtChapters = txtChapters,
                    currentChapterTitle = currentChapterTitle,
                    progressPercent = progressPercent,
                    bookTitle = bookTitle,
                    bookAuthor = bookAuthor,
                    bookOriginalFile = bookOriginalFile,
                    selectedText = selectedText,
                    contentText = contentText,
                    readerSettings = readerSettings,
                    activeReadingMs = activeReadingMs,
                    savedBookReadingMs = savedBookReadingMs,
                    estimatedRemainingMs = estimatedRemainingMs,
                    readerSpeed = readerSpeed,
                    inspirationsCount = inspirationsCount,
                    bookmarksCount = bookmarksCount,
                    documentWordCount = documentWordCount,
                    isTxt = isTxt,
                    searchQuery = searchQuery,
                    chapterStartOffsets = chapterStartOffsets,
                    chapterTitles = chapterTitles,
                    bookIndex = bookIndex,
                    txtTocRuleId = txtTocRuleId,
                    txtRulePreviews = txtRulePreviews,
                    recentChapters = recentChapters,
                    appDark = appDark,
                ),
                sheetCallbacks = buildReaderSheetHostCallbacks(
                    epubBook = epubBook,
                    txtChapters = txtChapters,
                    visiblePlainOffset = visiblePlainOffset,
                    textContent = textContent,
                    txtStreamingDocument = txtStreamingDocument,
                    bid = bid,
                    pagerEngineOn = pagerEngineOn,
                    plainContent = plainContent,
                    chapterStartOffsets = chapterStartOffsets,
                    bookTitle = bookTitle,
                    highlights = highlights,
                    context = context,
                    selectedText = selectedText,
                    currentChapterTitle = currentChapterTitle,
                    progressPercent = progressPercent,
                    bookAuthor = bookAuthor,
                    recentChapters = recentChapters,
                    pagedJumpRequest = pagedJumpRequest,
                    onTxtTocRuleIdChange = { txtTocRuleId = it },
                    onPendingTxtRuleAnchorOffsetChange = { pendingTxtRuleAnchorOffset = it },
                    onNavFocusBlockIndexChange = { navFocusBlockIndex = it },
                    onPendingHighlightIdChange = { pendingHighlightId = it },
                    goToChapter = goToChapter,
                    seekToPercent = seekToPercent,
                    jumpToPlainOffset = jumpToPlainOffset,
                    showNotice = showNotice,
                    onLoadChapterBlocks = onLoadChapterBlocks,
                    onAction = onAction,
                    scope = scope,
                ),
            )
        }
    }
    }
}
