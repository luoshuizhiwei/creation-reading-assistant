package com.creationreadingassistant.ui.screen.reader

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.theme.paperPalette
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import com.creationreadingassistant.ui.viewmodel.ChapterLoadResult
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent
import com.creationreadingassistant.feature.reader.EpubParser
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.feature.reader.pager.EpubChapterSource
import com.creationreadingassistant.feature.reader.pager.MarkdownChapterSource
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.feature.reader.pager.TxtChapterSource
import com.creationreadingassistant.feature.reader.eyecare.EyeCareSchedule
import kotlinx.coroutines.launch
import java.util.UUID
import com.creationreadingassistant.ui.screen.reader.BookIndex
import com.creationreadingassistant.ui.screen.reader.ReaderScreenInputs
import com.creationreadingassistant.ui.screen.reader.ReaderScreenCallbacks
import com.creationreadingassistant.ui.screen.reader.buildBookIndex
import com.creationreadingassistant.ui.screen.reader.chunkIndexForOffset
import com.creationreadingassistant.ui.screen.reader.chunkPlainText
import com.creationreadingassistant.ui.screen.reader.PlainTextChunk
import com.creationreadingassistant.ui.screen.reader.computeBlockGlobalOffsets
import com.creationreadingassistant.ui.screen.reader.computeEpubSearch
import com.creationreadingassistant.ui.screen.reader.formatDuration
import com.creationreadingassistant.ui.screen.reader.nowIso
import com.creationreadingassistant.ui.screen.reader.progressToChapterIndex
import com.creationreadingassistant.ui.screen.reader.unitIndexForOffset
import com.creationreadingassistant.ui.screen.reader.tts.rememberTts
import com.creationreadingassistant.ui.screen.reader.tts.TtsSettingsSyncEffect
import com.creationreadingassistant.ui.screen.reader.tts.TtsResumeEffect
import com.creationreadingassistant.ui.screen.reader.tts.TtsReaderSyncEffect
import com.creationreadingassistant.ui.screen.reader.ReaderPlatformEffects
import com.creationreadingassistant.ui.screen.reader.ReaderSheetHost
import com.creationreadingassistant.ui.screen.reader.ReaderInteractionLayer
import com.creationreadingassistant.ui.screen.reader.ReaderContentHost
import com.creationreadingassistant.ui.screen.reader.buildReaderContentHostState
import com.creationreadingassistant.ui.screen.reader.buildReaderContentHostCallbacks
import com.creationreadingassistant.ui.screen.reader.buildReaderInteractionLayerState
import com.creationreadingassistant.ui.screen.reader.buildReaderInteractionLayerCallbacks
import com.creationreadingassistant.ui.screen.reader.buildReaderSheetHostState
import com.creationreadingassistant.ui.screen.reader.buildReaderSheetHostCallbacks
import com.creationreadingassistant.ui.screen.reader.ReaderProgressEffects
import com.creationreadingassistant.ui.screen.reader.ReaderRuntimeEffects
import com.creationreadingassistant.ui.screen.reader.rememberReaderProgress
import com.creationreadingassistant.ui.screen.reader.rememberPagerEngineState
import com.creationreadingassistant.ui.screen.reader.rememberReaderDerivedState
import com.creationreadingassistant.ui.screen.reader.ReaderScreenMutableHolders
import com.creationreadingassistant.ui.screen.reader.ReaderScaffold
import com.creationreadingassistant.ui.screen.reader.rememberReaderScreenMutableHolders

/**
 * 阅读器全屏页（1:1 复刻 mobile/ 的 MobileReaderView 布局）。
 *
 * 分区：顶部栏（可收起）/ 正文区（EPUB 按章节、TXT 降级滚动文本）/ 底部 TTS 播放条 /
 * 底部弹层（目录、笔记与标注、AI 助手、AI 解读、灵感速记、设置、进度）。
 *
 * 复用：EpubParser（经 EpubRepository.openEpub）、BookRepository、BookContentDao、
 * HighlightDao / NoteDao / InspirationDao / ReadingProgressDao、TextToSpeech。
 * 不重写解析与朗读逻辑；helper 均在本文件内。
 */



// 高亮 / 纸色统一走 ReaderPaperPalette（paperPalette(readerSettings.background, appDark)），
// 不再由本文件内联硬编码；5 色批注与夜读描边严格来自设计实施稿 §4 / §4.5（已冻结）。

// TTS 控制器、媒体会话、句子切分、高亮构建、rememberTts → reader/tts/ReaderTtsController.kt / ReaderTtsBar.kt

// nowIso, progressToChapterIndex, formatDuration, buildInspirationPayload → reader/ReaderHelpers.kt


// BookIndex, PlainTextChunk, chunkPlainText, chunkIndexForOffset, unitIndexForOffset, buildBookIndex → reader/ReaderHelpers.kt


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    inputs: ReaderScreenInputs,
    callbacks: ReaderScreenCallbacks,
) {
    // ── 解构参数 ──
    val bookId = inputs.bookId
    val highlightId = inputs.highlightId
    val documentUiState = inputs.documentUiState
    val screenState = inputs.screenState
    val highlights = inputs.highlights
    val notes = inputs.notes
    val inspirations = inputs.inspirations
    val categories = inputs.categories
    val tags = inputs.tags
    val sessions = inputs.sessions
    val txtTocRuleIdFromVm = inputs.txtTocRuleIdFromVm
    val chapterLoadResult = inputs.chapterLoadResult
    val txtRuleScanResult = inputs.txtRuleScanResult
    val onLoadChapterBlocks = callbacks.onLoadChapterBlocks
    val onExtractChapterText = callbacks.onExtractChapterText
    val onAction = callbacks.onAction
    val onDocumentAction = callbacks.onDocumentAction
    val onBack = callbacks.onBack
    val settingsStore = callbacks.settingsStore
    val aiClient = callbacks.aiClient
    val pageIndexStore = callbacks.pageIndexStore
    val anchorCacheStore = callbacks.anchorCacheStore
    val pagerHealth = callbacks.pagerHealthStore
    val context = LocalContext.current
    val mutableHolders = rememberReaderScreenMutableHolders(screenState, bookId, bookId ?: "", txtTocRuleIdFromVm, highlightId)
    val settingsVm: SettingsViewModel = hiltViewModel()
    val readerSettings by settingsVm.reader.collectAsStateWithLifecycle()
    // 外观模式（system/light/dark）用于「跟随外观」纸张映射：浅色外壳→白纸，深色外壳→夜读。
    val appearance by settingsVm.appearance.collectAsStateWithLifecycle()
    val appDark = when (appearance.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    // 阅读器纸张调色板与外壳浅/深解耦；正文与 chrome 共同跟随 paper 的 light/dark。
    val paper = paperPalette(readerSettings.background, appDark)
    val paperBg = paper.bg
    val paperFg = paper.fg
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val clipboard = LocalClipboardManager.current
    val snackbarHost = remember { SnackbarHostState() }
    val tts = rememberTts()
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── 文档状态只从 ReaderViewModel 输入；页面不再打开 URI 或拥有文件资源。────────
    val loadedBook = documentUiState.loadedBook
    val epubContent = loadedBook?.content as? ReaderLoadedContent.Epub
    val textContent = loadedBook?.content as? ReaderLoadedContent.Text
    val markdownContent = loadedBook?.content as? ReaderLoadedContent.Markdown
    val epubBook = epubContent?.book
    val epubDocument = epubContent?.document
    val markdownDocument = markdownContent?.document
    val bookIndex = remember(epubBook) { epubBook?.let(::buildBookIndex) }
    val plainContent = textContent?.fullText.orEmpty()
    val bookTitle = loadedBook?.title ?: "未命名书籍"
    val bookAuthor = loadedBook?.author
    val bookOriginalFile = loadedBook?.originalFileName
    val bookSize = loadedBook?.sizeBytes ?: 0
    val savedPlainPercent = if (textContent != null) loadedBook.initialProgressPercent else 0f
    val savedPlainOffset = textContent?.initialAbsoluteOffset ?: markdownContent?.initialAbsoluteOffset ?: 0
    val savedEpubOffsetInChapter = epubContent?.initialOffsetInChapter ?: 0
    val savedTotalReadingMs = loadedBook?.savedReadingTimeMs ?: 0L
    val sessionStartProgress = loadedBook?.initialProgressPercent ?: 0f
    val isLoading = documentUiState.isLoading
    var runtimeError by mutableHolders.runtimeErrorState
    val error = runtimeError ?: documentUiState.errorMessage
    val chapterIndexState = remember(bookId) { mutableIntStateOf(0) }
    var chapterIndex by chapterIndexState
    var pendingInitialPosition by mutableHolders.pendingInitialPositionState

    // 预加载状态（由 ViewModel 章节加载代次管理，主线程只读，杜绝主线程 Zip I/O 导致的 ANR / OOM）
    val chapterBlocksState = remember { mutableStateOf<List<DocBlock>>(emptyList()) }
    var chapterBlocks by chapterBlocksState
    val isChapterLoadingState = remember { mutableStateOf(false) }
    var isChapterLoading by isChapterLoadingState

    // 流式 TXT 大文件状态（统一加载器按实际字节数分流，plainContent 为空串）
    val txtStreamingDocumentState = remember { mutableStateOf<PlainTextDocument?>(null) }
    var txtStreamingDocument by txtStreamingDocumentState
    val txtStreamingFileIndexState = remember { mutableStateOf<TxtFileIndex?>(null) }
    var txtStreamingFileIndex by txtStreamingFileIndexState

    LaunchedEffect(loadedBook) {
        runtimeError = null
        isChapterLoading = false
        pendingInitialPosition = loadedBook != null
        chapterIndex = epubContent?.initialChapterIndex ?: 0
        chapterBlocks = epubContent?.initialChapterBlocks.orEmpty()
        txtStreamingDocument = textContent?.streamingDocument
        txtStreamingFileIndex = textContent?.fileIndex
        // Markdown 滚动模式首章直接同步装载；分页模式由 pagedSource 按需读取
        if (markdownDocument != null) {
            chapterBlocks = markdownDocument.blocks(0)
        }
    }

    // R6：观察 ViewModel 章节加载结果
    LaunchedEffect(chapterLoadResult) {
        when (val r = chapterLoadResult) {
            is ChapterLoadResult.Loading -> {
                isChapterLoading = true
                chapterBlocks = emptyList()
            }
            is ChapterLoadResult.Loaded -> {
                isChapterLoading = false
                chapterBlocks = r.blocks
            }
            is ChapterLoadResult.Error -> {
                isChapterLoading = false
                runtimeError = r.message
            }
            null -> Unit
        }
    }

    // screenState 来自 ViewModel（不可变 data class）
    var controlsVisible by mutableHolders.controlsVisibleState
    LaunchedEffect(screenState.controlsVisible) { controlsVisible = screenState.controlsVisible }
    // 自动翻页是否运行只属于当前阅读会话；重进书籍不会擅自继续。
    var autoPagingActive by mutableHolders.autoPagingActiveState
    val lifecycleOwner = LocalLifecycleOwner.current
    var readerResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    var sheetOpenGuard by remember { mutableStateOf(screenState.sheetOpenGuard) }
    LaunchedEffect(screenState.sheetOpenGuard) { sheetOpenGuard = screenState.sheetOpenGuard }

    var selectedText by mutableHolders.selectedTextState
    LaunchedEffect(screenState.selectedText) { selectedText = screenState.selectedText }
    // T1：记录选区起点在本书全局文本中的偏移，用于写入 locator_json（TXT=plainContent 偏移，EPUB=block 全局偏移）
    var selectedRangeStart by mutableHolders.selectedRangeStartState
    LaunchedEffect(screenState.selectedRangeStart) { selectedRangeStart = screenState.selectedRangeStart }
    var selectedGlobalOffset by mutableHolders.selectedGlobalOffsetState
    LaunchedEffect(screenState.selectedGlobalOffset) { selectedGlobalOffset = screenState.selectedGlobalOffset }
    var sheet by mutableHolders.sheetValueState
    LaunchedEffect(screenState.sheet) { sheet = screenState.sheet }
    // 弹层打开时暂停「菜单自动隐藏」倒计时（否则调设置调到一半菜单没了）
    LaunchedEffect(sheet) {
        sheetOpenGuard = sheet != null
        onAction(
            sheet?.let(ReaderAction::OpenSheet)
                ?: ReaderAction.CloseSheet,
        )
    }
    var showTts by mutableHolders.showTtsState
    LaunchedEffect(screenState.showTts) { showTts = screenState.showTts }
    var searchQuery by mutableHolders.searchQueryState
    LaunchedEffect(screenState.searchQuery) { searchQuery = screenState.searchQuery }
    // R5：最近浏览章节（本会话记录，置顶于目录）；R8：顶栏「更多」菜单
    var recentChapters = remember { mutableStateListOf<Int>() }
    var showReaderOverflow by mutableHolders.showReaderOverflowState
    LaunchedEffect(screenState.showReaderOverflow) { showReaderOverflow = screenState.showReaderOverflow }
    // R3：跨会话 TTS 续读句偏移
    var ttsResumeOffset by remember { mutableIntStateOf(0) }
    var ttsResumeChapter by remember { mutableIntStateOf(-1) }

    // 阅读设置（来自持久化 SettingsStore，见 readerSettings）

    // 笔记对话框
    var noteOpen by mutableHolders.noteOpenState
    LaunchedEffect(screenState.noteOpen) { noteOpen = screenState.noteOpen }
    var noteBody by remember { mutableStateOf(screenState.noteBody) }
    LaunchedEffect(screenState.noteBody) { noteBody = screenState.noteBody }
    // 高亮颜色选择
    var showColorRow by mutableHolders.showColorRowState
    LaunchedEffect(screenState.showColorRow) { showColorRow = screenState.showColorRow }

    // 返回键按“临时层级优先”处理：先关弹层/菜单/选区，再离开阅读器。
    // 这与正文导航解耦，避免误触返回直接丢失当前阅读上下文。
    BackHandler(
        enabled = sheet != null ||
            noteOpen ||
            showReaderOverflow ||
            selectedText.isNotBlank() ||
            controlsVisible,
    ) {
        when {
            sheet != null -> sheet = null
            noteOpen -> noteOpen = false
            showReaderOverflow -> showReaderOverflow = false
            selectedText.isNotBlank() -> {
                selectedText = ""
                selectedRangeStart = -1
                selectedGlobalOffset = -1
                showColorRow = false
            }
            controlsVisible -> controlsVisible = false
        }
    }

    // 阅读提醒 / 本次阅读计时（对照 web useReaderReminders + useReaderSession）
    val activeReadingMsState = remember { mutableLongStateOf(0L) }
    var activeReadingMs by activeReadingMsState
    // 用可变 State 持有最新设置，避免提醒计时器因设置变化反复重建 / 捕获旧值
    val settingsRef = remember { mutableStateOf(readerSettings) }
    LaunchedEffect(readerSettings) { settingsRef.value = readerSettings }

    // TTS 高级项：首次将持久化的音调/音量/音色/定时停止载入控制器 → TtsSettingsSyncEffect
    TtsSettingsSyncEffect(tts = tts, readerSettings = readerSettings)

    val bid = bookId ?: ""
    var txtTocRuleId by mutableHolders.txtTocRuleIdState
    LaunchedEffect(bid, txtTocRuleIdFromVm) {
        txtTocRuleId = txtTocRuleIdFromVm
    }

    // R3：跨会话 TTS 续读 → TtsResumeEffect
    TtsResumeEffect(
        tts = tts,
        bookId = bid,
        settingsStore = settingsStore,
        isEpub = epubBook != null,
        chapterIndex = chapterIndex,
        onResumeOffsetChanged = { ttsResumeOffset = it },
        onResumeChapterChanged = { ttsResumeChapter = it },
    )

    // SE4：从搜索结果跳转时携带的 highlightId（高亮或笔记），消费后置空避免重复触发
    var pendingHighlightId by mutableHolders.pendingHighlightIdState

    // 书内搜索：EPUB 不再常驻全本文本（会 OOM），改为搜索时按需逐章流式抽取（见 computeEpubSearch）；
    // 这里只暴露各章偏移与标题，供跳章 / 命中映射使用。
    val isTxt = epubBook == null && markdownDocument == null
    // Phase 7：纯计算派生状态抽到 reader/ReaderScreenDerivedState.kt，逐字保真。
    // LaunchedEffect(txtStreamingDocument, readingUnits) 生命周期与原位一致（同一 Composition 子组合）。
    val derived = rememberReaderDerivedState(
        bookIndex = bookIndex,
        markdownDocument = markdownDocument,
        txtStreamingDocument = txtStreamingDocument,
        plainContent = plainContent,
        txtStreamingFileIndex = txtStreamingFileIndex,
    )
    val chapterStartOffsets = derived.chapterStartOffsets
    val chapterTitles = derived.chapterTitles
    val readingUnits = derived.readingUnits
    val unitCache = derived.unitCache
    // 暂时保留 plainChunks 用于进度/跳转兼容（Phase C 将完全替换）
    val plainChunks: List<PlainTextChunk> = remember(plainContent, readingUnits) {
        when {
            plainContent.isNotEmpty() -> chunkPlainText(plainContent)
            readingUnits.isNotEmpty() -> {
                // 流式模式：从 readingUnits 构建轻量 chunk 列表（只含偏移和长度，不含文本）
                readingUnits.map { unit ->
                    PlainTextChunk(unit.charStart, "")
                }
            }
            else -> emptyList()
        }
    }

    // Phase 6：分页引擎状态抽到 reader/ReaderPagerEngineState.kt，逐字保真。
    // DisposableEffect / LaunchedEffect / produceState 生命周期与原位一致（同一 Composition 子组合）。
    // 可写状态以 State-holder 形式传出（pagedAbsOffsetState / pagedPercentState），
    // 用 `var by` 还原原 delegate 语义，下游读写零改动。
    val pagerEngine = rememberPagerEngineState(
        epubBook = epubBook,
        epubDocument = epubDocument,
        markdownDocument = markdownDocument,
        bookIndex = bookIndex,
        readerSettings = readerSettings,
        pagerHealth = pagerHealth,
        isLoading = isLoading,
        error = error,
        textContent = textContent,
        plainContent = plainContent,
        txtTocRuleId = txtTocRuleId,
        txtStreamingDocument = txtStreamingDocument,
    )
    val pagerEngineOn = pagerEngine.pagerEngineOn
    val pagedJumpRequest = pagerEngine.pagedJumpRequest
    val pagedHardwareTurnRequest = pagerEngine.pagedHardwareTurnRequest
    var pagedAbsOffset by pagerEngine.pagedAbsOffsetState
    var pagedPercent by pagerEngine.pagedPercentState
    val txtChapters = pagerEngine.txtChapters
    val txtRulePreviews = pagerEngine.txtRulePreviews
    val pagedSource = pagerEngine.pagedSource

    // Phase 5：进度计算抽到 reader/ReaderProgressComputations.kt，逐字保真。
    // 中间态（plainListSnapshot / streamingContentText / plainPercent / epubPercent / 阅读统计中间量）
    // 保留在 rememberReaderProgress 内部，不外泄；下游只消费解构出的最终值。
    val progressState = rememberReaderProgress(
        epubBook = epubBook,
        markdownDocument = markdownDocument,
        chapterIndex = chapterIndex,
        readingUnits = readingUnits,
        pagerEngineOn = pagerEngineOn,
        pagedAbsOffset = pagedAbsOffset,
        pagedPercent = pagedPercent,
        plainContent = plainContent,
        txtStreamingDocument = txtStreamingDocument,
        chapterBlocks = chapterBlocks,
        txtChapters = txtChapters,
        chapterStartOffsets = chapterStartOffsets,
        sessions = sessions,
        bookSize = bookSize,
        savedTotalReadingMs = savedTotalReadingMs,
        sessionStartProgress = sessionStartProgress,
        activeReadingMs = activeReadingMs,
        notes = notes,
        inspirationsCount = inspirations.size,
        bookIndex = bookIndex,
    )
    val plainListState = progressState.plainListState
    val epubListState = progressState.epubListState
    val visiblePlainOffset = progressState.visiblePlainOffset
    val contentText = progressState.contentText
    val txtChapterIndex = progressState.txtChapterIndex
    val chapterFade = progressState.chapterFade
    val chapterFadeKey = progressState.chapterFadeKey
    val currentChapterTitle = progressState.currentChapterTitle
    val progressPercent = progressState.progressPercent
    val chapterProgress = progressState.chapterProgress
    val documentWordCount = progressState.documentWordCount
    val savedBookReadingMs = progressState.savedBookReadingMs
    val readerSpeed = progressState.readerSpeed
    val estimatedRemainingMs = progressState.estimatedRemainingMs
    val bookmarksCount = progressState.bookmarksCount
    val inspirationsCount = progressState.inspirationsCount

    val effectivePaperBg = paperBg

    // 护眼时间 currentMinute 由 ReaderRuntimeEffects 每分钟写入
    val currentMinuteState = remember { mutableIntStateOf(0) }
    var currentMinute by currentMinuteState
    val eyeCareActive = readerSettings.eyeCareFilterEnabled ||
        (readerSettings.eyeCareScheduleEnabled && EyeCareSchedule.isActive(
            currentMinute,
            readerSettings.eyeCareStartMinute,
            readerSettings.eyeCareEndMinute,
        ))
    val eyeRgb = remember(readerSettings.eyeCareTemperature) {
        EyeCareSchedule.rgbForKelvin(readerSettings.eyeCareTemperature)
    }
    val eyeFilterColor = Color(eyeRgb.first, eyeRgb.second, eyeRgb.third)

    // 朗读句高亮背景色（与 TXT 保持一致）：跟随纸张强调色（§4.3 accent @0.22）。
    val sentenceHighlightBg = paper.accent.copy(alpha = 0.22f)

    // T1/T2：当前章节各渲染块在全书文本中的全局偏移；以及 TTS 当前句在章节内的定位
    val chapterBase = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
    val blockGlobalOffsets = remember(epubBook, chapterStartOffsets, chapterIndex, chapterBlocks) {
        if (epubBook != null) computeBlockGlobalOffsets(chapterBlocks, chapterBase) else emptyList()
    }
    val ttsSentenceRangeInChapter = if (showTts && tts.status != "idle" && epubBook != null && contentText.isNotBlank()) {
        tts.currentSentenceRange
    } else null
    val ttsSentenceBlockIndex = remember(blockGlobalOffsets, ttsSentenceRangeInChapter) {
        if (ttsSentenceRangeInChapter != null) {
            val s = ttsSentenceRangeInChapter.first
            var idx = -1
            for (i in blockGlobalOffsets.indices) {
                val o = blockGlobalOffsets[i]
                if (o >= 0 && o <= s) idx = i else if (o > s) break
            }
            idx
        } else null
    }
    // 滚动聚焦块：优先 TTS 当前句，否则导航精准定位（T1 跳转用）
    var navFocusBlockIndex by mutableHolders.navFocusBlockIndexState
    val focusBlockIndex = ttsSentenceBlockIndex ?: navFocusBlockIndex
    val epubBringRequester = remember { BringIntoViewRequester() }

    /** 依据当前选区生成 locator_json（T1）。 */
    fun computeLocatorJson(): String? = com.creationreadingassistant.ui.screen.reader.computeLocatorJson(
        epubBook, selectedGlobalOffset, chapterStartOffsets, selectedText, selectedRangeStart,
    )

    fun showNotice(msg: String) {
        scope.launch { snackbarHost.showSnackbar(msg) }
    }

    // R6：pendingTxtRuleAnchorOffset 跨 effect 共享，由 ReaderRuntimeEffects 消费
    var pendingTxtRuleAnchorOffset by mutableHolders.pendingTxtRuleAnchorOffsetState

    val autoPagingPaused = !readerResumed ||
        sheet != null ||
        noteOpen ||
        selectedText.isNotBlank() ||
        showTts ||
        isLoading ||
        error != null
    val autoPagingSupported = readerSettings.readerMode == "scroll" ||
        (pagerEngineOn && pagedSource != null)

    fun goToChapter(i: Int) = com.creationreadingassistant.ui.screen.reader.goToChapter(
        i, epubBook, markdownDocument, pagerEngineOn, chapterStartOffsets,
        pagedJumpRequest, chapterIndexState, tts, onAction, bid,
    )

    /** TXT 跳转统一入口：分页引擎开着走翻页定位，否则滚动列表。两条路都以全书字符偏移为准。 */
    fun jumpToPlainOffset(offset: Int) = com.creationreadingassistant.ui.screen.reader.jumpToPlainOffset(
        offset, pagerEngineOn, readingUnits, scope, plainListState, pagedJumpRequest,
    )

    fun persistCurrentProgress() = com.creationreadingassistant.ui.screen.reader.persistCurrentProgress(
        bid, loadedBook, error, pendingInitialPosition, epubBook, pagerEngineOn,
        pagedAbsOffset, pagedSource, chapterIndex, epubListState, blockGlobalOffsets,
        chapterBase, chapterBlocks, bookIndex, chapterStartOffsets, visiblePlainOffset,
        markdownDocument, txtStreamingDocument, plainContent, onAction,
    )

    // ── 平台 Effects（常亮、沉浸、亮度、窗口底色、音量键、自动隐藏、生命周期）────
    ReaderPlatformEffects(
        keepAwake = readerSettings.keepAwake,
        immersiveMode = readerSettings.immersiveMode,
        controlsVisible = controlsVisible,
        paperIsLight = paper.isLight,
        appDark = appDark,
        readerBrightness = if (readerSettings.brightness < 0) -1 else readerSettings.brightness.coerceIn(5, 100),
        paperBgColor = paperBg,
        volumeKeyPaging = readerSettings.volumeKeyPaging,
        onVolumeUp = {
            if (!readerSettings.volumeKeyPaging) false
            else if (screenState.showTts && !readerSettings.volumeKeyPagingDuringTts) false
            else {
                when {
                    pagerEngineOn -> pagedHardwareTurnRequest.value = -1
                    epubBook != null || markdownDocument != null -> goToChapter(chapterIndex - 1)
                    else -> scope.launch {
                        val amount = plainListState.layoutInfo.viewportSize.height * 0.88f * -1
                        plainListState.animateScrollBy(amount)
                    }
                }
                true
            }
        },
        onVolumeDown = {
            if (!readerSettings.volumeKeyPaging) false
            else if (screenState.showTts && !readerSettings.volumeKeyPagingDuringTts) false
            else {
                when {
                    pagerEngineOn -> pagedHardwareTurnRequest.value = 1
                    epubBook != null || markdownDocument != null -> goToChapter(chapterIndex + 1)
                    else -> scope.launch {
                        val amount = plainListState.layoutInfo.viewportSize.height * 0.88f * 1
                        plainListState.animateScrollBy(amount)
                    }
                }
                true
            }
        },
        onReaderResumed = { readerResumed = it },
        onPersistProgress = ::persistCurrentProgress,
        controlsVisibleForAutoHide = controlsVisible,
        autoHideSeconds = readerSettings.autoHideSeconds,
        sheetOpenGuard = sheetOpenGuard,
        onAutoHide = { controlsVisible = false },
    )

    // R6：进度滑块跳转（TXT 定位到百分比；EPUB 跳到对应章节；分页引擎按全书偏移精确定位）
    fun seekToPercent(p: Float) = com.creationreadingassistant.ui.screen.reader.seekToPercent(
        p, epubBook, pagerEngineOn, bookIndex, txtStreamingDocument, plainContent,
        pagedJumpRequest, ::goToChapter, ::jumpToPlainOffset,
    )

    // 章节内进度跳转：将章节内百分比转换为全书绝对偏移后定位
    fun seekToChapterPercent(p: Float) = com.creationreadingassistant.ui.screen.reader.seekToChapterPercent(
        p, epubBook, chapterStartOffsets, chapterIndex, contentText, pagerEngineOn,
        txtChapters, txtChapterIndex, plainContent, pagedJumpRequest, ::jumpToPlainOffset,
    )

    // ── 会话计时 / 阅读提醒 / 进度持久化 / 位置恢复 / 高亮精确定位 ──────────────
    // Phase 2：8 个 LaunchedEffect 抽到 reader/ReaderProgressEffects.kt，逐字保真。
    // 6 个可变状态以 State-holder 形式传入（读 .value 拿当前快照，避免 effect 体捕获旧值）；
    // 局部 fun（goToChapter/jumpToPlainOffset/showNotice）与挂起回调作为函数参数传入。
    ReaderProgressEffects(
        bid = bid,
        isLoading = isLoading,
        error = error,
        loadedBook = loadedBook,
        chapterIndex = chapterIndex,
        epubBook = epubBook,
        readingUnits = readingUnits,
        pagerEngineOn = pagerEngineOn,
        plainListState = plainListState,
        epubListState = epubListState,
        txtStreamingDocument = txtStreamingDocument,
        plainContent = plainContent,
        markdownDocument = markdownDocument,
        pagedSource = pagedSource,
        savedPlainOffset = savedPlainOffset,
        savedPlainPercent = savedPlainPercent,
        chapterBlocks = chapterBlocks,
        savedEpubOffsetInChapter = savedEpubOffsetInChapter,
        chapterBase = chapterBase,
        blockGlobalOffsets = blockGlobalOffsets,
        highlights = highlights,
        notes = notes,
        bookIndex = bookIndex,
        chapterStartOffsets = chapterStartOffsets,
        anchorCacheStore = anchorCacheStore,
        recentChapters = recentChapters,
        activeReadingMsState = activeReadingMsState,
        pagedAbsOffsetState = pagerEngine.pagedAbsOffsetState,
        pagedPercentState = pagerEngine.pagedPercentState,
        pendingInitialPositionState = mutableHolders.pendingInitialPositionState,
        pendingHighlightIdState = mutableHolders.pendingHighlightIdState,
        navFocusBlockIndexState = mutableHolders.navFocusBlockIndexState,
        settingsRef = settingsRef,
        pagedJumpRequest = pagedJumpRequest,
        onAction = onAction,
        showNotice = ::showNotice,
        goToChapter = ::goToChapter,
        jumpToPlainOffset = ::jumpToPlainOffset,
        onLoadChapterBlocks = onLoadChapterBlocks,
        onExtractChapterText = onExtractChapterText,
    )

    // ── 运行时 effect：章节淡入 / 护眼时间 / 焦点滚动 / TXT 规则 / 自动翻页 / 触感 ──
    // Phase 4：6 个 LaunchedEffect 抽到 reader/ReaderRuntimeEffects.kt，逐字保真。
    // autoPagingActiveState 既是 key 又在 while 循环里被读 / 被写：用 .value 既作 key
    // （组合期快照读取，变化即重启）又作读写入口，与原 `var by` delegate 行为一致。
    ReaderRuntimeEffects(
        chapterFadeKey = chapterFadeKey,
        chapterFade = chapterFade,
        readerSettings = readerSettings,
        currentMinuteState = currentMinuteState,
        focusBlockIndex = focusBlockIndex,
        epubBringRequester = epubBringRequester,
        txtRuleScanResult = txtRuleScanResult,
        pendingTxtRuleAnchorOffsetState = mutableHolders.pendingTxtRuleAnchorOffsetState,
        pagedJumpRequest = pagedJumpRequest,
        txtStreamingDocumentState = txtStreamingDocumentState,
        txtStreamingFileIndexState = txtStreamingFileIndexState,
        autoPagingActiveState = mutableHolders.autoPagingActiveState,
        autoPagingPaused = autoPagingPaused,
        epubListState = epubListState,
        plainListState = plainListState,
        epubBook = epubBook,
        showNotice = ::showNotice,
        haptic = haptic,
    )

    // 打开 TTS：从当前正文（或跨会话续读位置）开始
    fun openTts() = com.creationreadingassistant.ui.screen.reader.openTts(
        contentText, epubBook, ttsResumeChapter, chapterIndex, ttsResumeOffset,
        tts, bookTitle, currentChapterTitle, context,
        onShowTtsChange = { showTts = it }, showNotice = ::showNotice,
    )

    // R2：朗读时把正文跟到当前句 → TtsReaderSyncEffect
    TtsReaderSyncEffect(
        tts = tts,
        showTts = showTts,
        isTxt = isTxt,
        plainContent = plainContent,
        txtStreamingDocument = txtStreamingDocument,
        visiblePlainOffset = visiblePlainOffset,
        jumpToPlainOffset = ::jumpToPlainOffset,
        pagerEngineOn = pagerEngineOn,
        isEpub = epubBook != null,
        pagedJumpTo = { pagedJumpRequest.value = it },
        chapterStartOffsets = chapterStartOffsets,
        chapterIndex = chapterIndex,
    )

    // ── 笔记对话框（已提取到 ReaderNoteDialog）──────────────────────
    if (noteOpen) {
        com.creationreadingassistant.ui.screen.reader.sheets.ReaderNoteDialog(
            selectedText = selectedText,
            noteBody = noteBody,
            onNoteBodyChange = { noteBody = it },
            onSave = {
                // 同高亮保存：先快照局部变量，避免下面同步清空后读到空串
                val snapshotText = selectedText
                val snapshotBody = noteBody
                val snapshotLocator = computeLocatorJson()
                onAction(ReaderAction.SaveNote(
                    NoteEntity(
                        id = UUID.randomUUID().toString(),
                        book_id = bid.ifBlank { null },
                        title = (snapshotBody.ifBlank { snapshotText }).take(40),
                        body = snapshotBody,
                        excerpt = snapshotText.takeIf { it.isNotBlank() },
                        chapter_title = currentChapterTitle.ifBlank { null },
                        progress_percent = progressPercent,
                        kind = "note",
                        locator_json = snapshotLocator,
                        payload = "{}",
                        created_at = nowIso(),
                        device_id = null,
                        revision = 1,
                        updated_at = nowIso(),
                        deleted_at = null,
                    ),
                ))
                noteBody = ""
                noteOpen = false
                selectedText = ""
                showNotice("已保存笔记")
            },
            onDismiss = { noteOpen = false },
        )
    }

    fun handleChromeAction(action: ReaderChromeAction) = com.creationreadingassistant.ui.screen.reader.handleChromeAction(
        action, showTts, tts, mutableHolders.autoPagingActiveState, autoPagingSupported,
        onShowTtsChange = { showTts = it },
        onControlsVisibleChange = { controlsVisible = it },
        onSelectedTextChange = { selectedText = it },
        onSheetChange = { sheet = it },
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack, openTts = ::openTts, showNotice = ::showNotice,
    )


    ReaderScaffold(
        progressState = progressState,
        derived = derived,
        pagerEngine = pagerEngine,
        paper = paper,
        eyeCareActive = eyeCareActive,
        eyeFilterColor = eyeFilterColor,
        readerSettings = readerSettings,
        snackbarHost = snackbarHost,
        isLoading = isLoading,
        isChapterLoading = isChapterLoading,
        error = error,
        loadedBook = loadedBook,
        bid = bid,
        savedEpubOffsetInChapter = savedEpubOffsetInChapter,
        savedPlainOffset = savedPlainOffset,
        savedPlainPercent = savedPlainPercent,
        txtStreamingDocument = txtStreamingDocument,
        plainContent = plainContent,
        textContent = textContent,
        epubBook = epubBook,
        markdownDocument = markdownDocument,
        epubDocument = epubDocument,
        bookIndex = bookIndex,
        recentChapters = recentChapters,
        chapterIndex = chapterIndex,
        chapterBlocks = chapterBlocks,
        blockGlobalOffsets = blockGlobalOffsets,
        chapterBase = chapterBase,
        ttsSentenceRangeInChapter = ttsSentenceRangeInChapter,
        focusBlockIndex = focusBlockIndex,
        sentenceHighlightBg = sentenceHighlightBg,
        epubBringRequester = epubBringRequester,
        isTxt = isTxt,
        tts = tts,
        autoPagingPaused = autoPagingPaused,
        appDark = appDark,
        activeReadingMs = activeReadingMs,
        goToChapter = ::goToChapter,
        showNotice = ::showNotice,
        seekToPercent = ::seekToPercent,
        jumpToPlainOffset = ::jumpToPlainOffset,
        handleChromeAction = ::handleChromeAction,
        computeLocatorJson = ::computeLocatorJson,
        inputs = inputs,
        callbacks = callbacks,
        settingsVm = settingsVm,
        sheetState = sheetState,
        holders = mutableHolders,
    )

}
