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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
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
import com.creationreadingassistant.ui.viewmodel.PendingTxtRuleAnchor
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
    val pendingTxtRuleAnchorState: MutableState<PendingTxtRuleAnchor?>,
    val navFocusBlockIndexState: MutableState<Int?>,
    val pendingHighlightIdState: MutableState<String?>,
    /**
     * 搜索滚动聚焦的一次性请求（P1 修复）：与 navFocusBlockIndexState（SE4
     * 高亮/笔记恢复等非搜索用途）分离，按书 remember，切书自动清空；
     * ReaderContentHost 消费后经回调 ack 清空。
     */
    val searchScrollFocusRequestState: MutableState<SearchScrollFocusRequest?>,
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
    // P1-A：pending anchor 绑定书会话；切书自动清空，防止旧书锚点被新书扫描消费
    val pendingTxtRuleAnchorState = remember(bid) { mutableStateOf<PendingTxtRuleAnchor?>(null) }
    val navFocusBlockIndexState = remember { mutableStateOf<Int?>(null) }
    val pendingHighlightIdState = remember { mutableStateOf(highlightId) }
    // P1：搜索滚动聚焦请求按书持有；切书自动清空，跨书残留由消费侧身份匹配兜底丢弃
    val searchScrollFocusRequestState = remember(bid) { mutableStateOf<SearchScrollFocusRequest?>(null) }
    return ReaderScreenMutableHolders(
        autoPagingActiveState = autoPagingActiveState,
        runtimeErrorState = runtimeErrorState,
        pendingInitialPositionState = pendingInitialPositionState,
        txtTocRuleIdState = txtTocRuleIdState,
        pendingTxtRuleAnchorState = pendingTxtRuleAnchorState,
        navFocusBlockIndexState = navFocusBlockIndexState,
        pendingHighlightIdState = pendingHighlightIdState,
        searchScrollFocusRequestState = searchScrollFocusRequestState,
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
    paperTexture: Boolean,
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
    searchHighlightBg: Color,
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
    searchSession: BookSearchSession,
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

    // ── 边缘亮度手势 HUD（起点同款：左/右边缘上下滑调亮度 → 中央弹 HUD 显示百分比）──
    // 手势开始时 true，结束 false；HUD 自己会在结束后延迟 1.2s 再淡出。
    var brightnessHudVisible by remember { mutableStateOf(false) }

    // 可变 var 从 holder 重新委托：写操作直接落回 ReaderScreen 持有的真实状态。
    var autoPagingActive by holders.autoPagingActiveState
    var runtimeError by holders.runtimeErrorState
    var pagedAbsOffset by pagedAbsOffsetState
    var pagedPercent by pagedPercentState
    var pendingInitialPosition by holders.pendingInitialPositionState
    var txtTocRuleId by holders.txtTocRuleIdState
    var pendingTxtRuleAnchor by holders.pendingTxtRuleAnchorState
    var pendingHighlightId by holders.pendingHighlightIdState

    // B2：弹层/控件类 UI 状态直接只读消费 VM 的 screenState（唯一真源），不再持有本地副本。
    val screenState = inputs.screenState
    val controlsVisible = screenState.controlsVisible
    val showTts = screenState.showTts
    val selectedText = screenState.selectedText
    val selectedGlobalOffset = screenState.selectedGlobalOffset
    val selectedRangeStart = screenState.selectedRangeStart
    val selectedSourceEnd = screenState.selectedSourceEnd
    val sheet = screenState.sheet
    val noteOpen = screenState.noteOpen
    val showReaderOverflow = screenState.showReaderOverflow
    val showColorRow = screenState.showColorRow
    val searchQuery = screenState.searchQuery
    // 搜索命中临时高亮（全书字符区间，含首不含尾）；随 currentTarget 变化，null = 无高亮
    val searchHitRangeAbs = searchSession.currentTarget?.let {
        it.absoluteRange.first to it.absoluteRange.last + 1
    }

    ReaderPaperTheme(paper) {
    Scaffold(
        modifier = Modifier
            .drawWithContent {
                drawContent()
                // ── 1/2：护眼色温滤镜（暖色 Multiply 叠层，OLED 纯黑兼容降强度）──
                if (eyeCareActive) {
                    val base = readerSettings.eyeCareIntensity.coerceIn(0, 100) / 100f
                    val intensity = if (readerSettings.eyeCareOledBlackCompat && !paper.isLight) {
                        base * 0.45f
                    } else base
                    drawRect(
                        color = eyeFilterColor,
                        alpha = intensity,
                        blendMode = BlendMode.Multiply,
                    )
                }
                // ── 2/2：额外压暗遮罩（补 0–4% 范围的屏幕亮度缺口）──
                // readerBrightness ≥ 5 时此层完全透明；0–4 时用黑色 SrcOver 继续压暗，
                // 达到「拉到 0% 真的很暗」的效果。置于护眼滤镜之后，保证压暗同时色温仍然生效。
                val dimming = ReaderWindowPolicy.overlayDimmingAlpha(readerSettings.brightness)
                if (dimming > 0f) {
                    drawRect(
                        color = Color.Black,
                        alpha = dimming,
                    )
                }
            }
            // ── 边缘上下滑调亮度手势（左/右 18dp 窄条，起点同款）──
            // 只有按下命中边缘窄条时才进入亮度调节，其余位置完全不拦截事件，
            // 让三区点击翻页、长按选区、滚动照常工作。
            .readerBrightnessEdgeGesture(
                readerBrightness = readerSettings.brightness,
                lastFixedBrightness = readerSettings.lastFixedBrightness,
                onBrightnessChange = { next ->
                    settingsVm.updateReader { copy(brightness = next) }
                },
                onGestureActiveChange = { brightnessHudVisible = it },
            ),
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
            // 噪点纹理受外观设置「纸张纹理」开关控制（渐变恒在）。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(lerp(paper.bg, Color.White, 0.04f), lerp(paper.bg, Color.Black, 0.03f))))
                    .then(if (paperTexture) Modifier.background(PaperNoise.brush(), alpha = 0.04f) else Modifier),
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
                            searchHighlightBg = searchHighlightBg,
                        ),
                        selection = ReaderSelectionState(
                            selectedText = selectedText,
                            selectedGlobalOffset = selectedGlobalOffset,
                            selectedRangeStart = selectedRangeStart,
                            selectedSourceEnd = selectedSourceEnd,
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
                            txtTocProfileKey = inputs.ruleSnapshot.effectiveTocProfile.key,
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
                            highlights = highlights,
                            searchHitRangeAbs = searchHitRangeAbs,
                            searchScrollFocusRequest = holders.searchScrollFocusRequestState.value,
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
                    onSearchScrollFocusRequestConsumed = {
                        holders.searchScrollFocusRequestState.value = null
                    },
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
                    autoPageSpeed = readerSettings.autoPageSpeed,
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
                    selectedSourceLength = when {
                        selectedSourceEnd < 0 -> null
                        selectedRangeStart >= 0 -> selectedSourceEnd - selectedRangeStart
                        selectedGlobalOffset >= 0 -> selectedSourceEnd - selectedGlobalOffset
                        else -> null
                    }?.takeIf { it >= 0 },
                    bid = bid,
                    currentChapterTitle = currentChapterTitle,
                    progressPercent = progressPercent,
                    clipboard = clipboard,
                    onAction = onAction,
                    handleChromeAction = handleChromeAction,
                    seekToChapterPercent = seekToPercent,
                    goToChapter = goToChapter,
                    onAutoPageSpeedChange = { speed ->
                        settingsVm.updateReader {
                            copy(autoPageSpeed = clampAutoPageSpeed(speed))
                        }
                    },
                    computeLocatorJson = computeLocatorJson,
                    showNotice = showNotice,
                ),
                tts = tts,
            )

            // 底部弹层（不新建路由，内部状态切换）→ 抽出到 reader/ReaderSheetHost.kt
            ReaderSheetHost(
                sheet = sheet,
                sheetState = sheetState,
                searchSession = searchSession,
                paper = paper,
                inputs = inputs,
                callbacks = callbacks,
                settingsVm = settingsVm,
                state = buildReaderSheetHostState(pagerReplacementAvailability = pagerEngine.replacementAvailability,
                    epubBook = epubBook,
                    epubDocument = epubDocument,
                    markdownDocument = markdownDocument,
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
                    pagerEngineOn = pagerEngineOn,
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
                    txtRuleScanStatus = inputs.txtRuleScanStatus,
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
                    onPendingTxtRuleAnchorOffsetChange = { pendingTxtRuleAnchor = it },
                    onPendingHighlightIdChange = { pendingHighlightId = it },
                    goToChapter = goToChapter,
                    seekToPercent = seekToPercent,
                    jumpToPlainOffset = jumpToPlainOffset,
                    showNotice = showNotice,
                    onAction = onAction,
                    onCancelTxtScan = { onAction(ReaderAction.CancelTxtTocScan) },
                ),
            )

            // 最上层：边缘上下滑调亮度 HUD；松手后延迟 1.2s 再淡出
            ReaderBrightnessHUD(
                visible = brightnessHudVisible,
                brightness = readerSettings.brightness,
            )
        }
    }
    }
}

