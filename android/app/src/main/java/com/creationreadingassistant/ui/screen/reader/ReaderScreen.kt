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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.creationreadingassistant.ui.screen.reader.tts.TtsErrorNoticeEffect
import com.creationreadingassistant.ui.screen.reader.tts.TtsNoticeAdapter
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
    // ── 解构参数（B3 后仅保留主函数内实际消费的字段）──
    val bookId = inputs.bookId
    val highlightId = inputs.highlightId
    val sourceLocatorJson = inputs.sourceLocatorJson
    val documentUiState = inputs.documentUiState
    val screenState = inputs.screenState
    val highlights = inputs.highlights
    val notes = inputs.notes
    val inspirations = inputs.inspirations
    val sessions = inputs.sessions
    val txtTocRuleIdFromVm = inputs.txtTocRuleIdFromVm
    val chapterLoadResult = inputs.chapterLoadResult
    val txtRuleScanResult = inputs.txtRuleScanResult
    val onAction = callbacks.onAction
    val onBack = callbacks.onBack
    val anchorCacheStore = callbacks.anchorCacheStore
    val pagerHealth = callbacks.pagerHealthStore
    val context = LocalContext.current
    val mutableHolders = rememberReaderScreenMutableHolders(
        bookId = bookId,
        bid = bookId ?: "",
        txtTocRuleIdFromVm = txtTocRuleIdFromVm,
        highlightId = highlightId,
        sourceLocatorJson = sourceLocatorJson,
    )
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
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val snackbarHost = remember { SnackbarHostState() }
    val tts = rememberTts()
    // TTS 引擎错误 → 现有 notice（含「去设置」动作），不持有 Activity
    val ttsNotice = remember(context) { TtsNoticeAdapter(scope, snackbarHost, context) }
    TtsErrorNoticeEffect(tts = tts, adapter = ttsNotice)
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
    val bookSize = loadedBook?.sizeBytes ?: 0
    val savedPlainPercent = if (textContent != null) loadedBook.initialProgressPercent else 0f
    val savedPlainOffset = textContent?.initialAbsoluteOffset ?: markdownContent?.initialAbsoluteOffset ?: 0
    val savedEpubOffsetInChapter = epubContent?.initialOffsetInChapter ?: 0
    val savedTotalReadingMs = loadedBook?.savedReadingTimeMs ?: 0L
    val sessionStartProgress = loadedBook?.initialProgressPercent ?: 0f
    val isLoading = documentUiState.isLoading
    var runtimeError by mutableHolders.runtimeErrorState
    val error = runtimeError ?: documentUiState.errorMessage
    val savedEpubChapterIndex = epubContent?.initialChapterIndex
    val chapterIndexState = remember(bookId, savedEpubChapterIndex) {
        mutableIntStateOf(initialReaderChapterIndex(savedEpubChapterIndex))
    }
    var chapterIndex by chapterIndexState
    var pendingInitialPosition by mutableHolders.pendingInitialPositionState

    // B3：预加载 / 流式 TXT 状态声明与「文档装载 / 章节加载代次」2 个 LaunchedEffect
    // 抽到 reader/ReaderDocumentLoadEffects.kt，逐字保真（State-holder 模式）。
    val docLoad = rememberReaderDocumentLoadState(
        loadedBook = loadedBook,
        epubContent = epubContent,
        textContent = textContent,
        markdownDocument = markdownDocument,
        chapterLoadResult = chapterLoadResult,
        runtimeErrorState = mutableHolders.runtimeErrorState,
        pendingInitialPositionState = mutableHolders.pendingInitialPositionState,
        chapterIndexState = chapterIndexState,
    )
    val chapterBlocks = docLoad.chapterBlocks
    val isChapterLoading = docLoad.isChapterLoading
    val txtStreamingDocument = docLoad.txtStreamingDocument
    val txtStreamingFileIndex = docLoad.txtStreamingFileIndex

    // B2：screenState 来自 ViewModel（不可变 data class），是弹层/控件类 UI 状态的唯一真源。
    // 原 12 处「var x by remember + LaunchedEffect(screenState.x) { x = screenState.x }」双向同步
    // 已全部移除：UI 只读消费下列值，任何写入一律经 onAction(ReaderAction.Xxx) 回 VM。
    val controlsVisible = screenState.controlsVisible
    // 自动翻页是否运行只属于当前阅读会话；重进书籍不会擅自继续。
    var autoPagingActive by mutableHolders.autoPagingActiveState
    val lifecycleOwner = LocalLifecycleOwner.current
    val readerResumedState = remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    // 弹层打开时暂停「菜单自动隐藏」倒计时（否则调设置调到一半菜单没了）。
    // 原由 LaunchedEffect(sheet) 派生 sheetOpenGuard = sheet != null；sheet 现已是 VM 状态，
    // 直接纯派生，不再持有任何本地副本。
    val sheet = screenState.sheet
    val sheetOpenGuard = sheet != null
    val selectedText = screenState.selectedText
    // T1：记录选区起点在本书全局文本中的偏移，用于写入 locator_json（TXT=plainContent 偏移，EPUB=block 全局偏移）
    val selectedRangeStart = screenState.selectedRangeStart
    val selectedGlobalOffset = screenState.selectedGlobalOffset
    val showTts = screenState.showTts
    val searchQuery = screenState.searchQuery
    // R5：最近浏览章节（本会话记录，置顶于目录）；R8：顶栏「更多」菜单
    var recentChapters = remember { mutableStateListOf<Int>() }
    val showReaderOverflow = screenState.showReaderOverflow
    // R3：跨会话 TTS 续读句偏移（同步 effect 在 ReaderSessionEffects 内）
    val ttsResumeOffsetState = remember { mutableIntStateOf(0) }
    val ttsResumeChapterState = remember { mutableIntStateOf(-1) }

    // 阅读设置（来自持久化 SettingsStore，见 readerSettings）

    // 笔记对话框
    val noteOpen = screenState.noteOpen
    val noteBody = screenState.noteBody
    // 高亮颜色选择
    val showColorRow = screenState.showColorRow

    // J1.2：临时查阅期间离开/暂停阅读器不得把临时位置写回普通阅读进度。
    val temporaryInspection = inputs.navigationMode == ReaderNavigationMode.TEMPORARY
    val hasReturnableTarget = callbacks.hasReturnableTarget
    val onTemporaryReturn = callbacks.onTemporaryReturn

    // R2-J1.4：返回键按"临时层级优先"处理：先关弹层/菜单/选区，再处理临时返回，最后离开阅读器。
    // 这与正文导航解耦，避免误触返回直接丢失当前阅读上下文。
    // B2：所有写入改走 onAction（优先级分支与原逻辑逐一对应）：
    // 关弹层→CloseSheet；关笔记框→SetNoteOpen(false)；关更多菜单→SetShowOverflow(false)；
    // 清选区→ClearSelection（reducer 置 text=""、两偏移 -1、showColorRow=false，与原四行写入一致）；
    // 藏控件→ToggleControls(false)。
    // J1.4：临时查阅且存在返回目标时，先关闭临时 UI，再触发 onTemporaryReturn（LIFO 返回一层）。
    BackHandler(
        enabled = sheet != null ||
            noteOpen ||
            showReaderOverflow ||
            selectedText.isNotBlank() ||
            controlsVisible ||
            (temporaryInspection && hasReturnableTarget),
    ) {
        when {
            sheet != null -> onAction(ReaderAction.CloseSheet)
            noteOpen -> onAction(ReaderAction.SetNoteOpen(false))
            showReaderOverflow -> onAction(ReaderAction.SetShowOverflow(false))
            selectedText.isNotBlank() -> onAction(ReaderAction.ClearSelection)
            controlsVisible -> onAction(ReaderAction.ToggleControls(false))
            temporaryInspection && hasReturnableTarget -> onTemporaryReturn()
        }
    }

    // 阅读提醒 / 本次阅读计时（对照 web useReaderReminders + useReaderSession）
    val activeReadingMsState = remember { mutableLongStateOf(0L) }
    var activeReadingMs by activeReadingMsState
    // 用可变 State 持有最新设置，避免提醒计时器因设置变化反复重建 / 捕获旧值
    // （同步 effect 抽到 reader/ReaderSessionEffects.kt）
    val settingsRef = remember { mutableStateOf(readerSettings) }

    val bid = bookId ?: ""
    var replacementStartupNoticeShown by rememberSaveable(bid) { mutableStateOf(false) }
    var txtTocRuleId by mutableHolders.txtTocRuleIdState

    // 书内搜索会话按书持有：关闭/重开面板不清空 query/results/current hit；
    // 切书（bid 变化）自动重建，用户清空查询由会话内 onQueryChanged 处理。
    val searchSession = remember(bid) { BookSearchSession(bookKey = bid) }

    // SE4：从搜索结果跳转时携带的 highlightId（高亮或笔记），消费后置空避免重复触发
    var pendingHighlightId by mutableHolders.pendingHighlightIdState

    // 书内搜索：EPUB 不再常驻全本文本（会 OOM），改为搜索时按需逐章流式抽取（见 computeEpubSearch）；
    // 这里只暴露各章偏移与标题，供跳章 / 命中映射使用。
    val isTxt = epubBook == null && markdownDocument == null
    // Phase 7：纯计算派生状态抽到 reader/ReaderScreenDerivedState.kt，逐字保真。
    // readingUnits 的单一真相在文档构造层（fromFileIndex 构造时构建，首帧即就绪），
    // 组合层只读裁决（ReadingUnitsResolver），不再写回 txtStreamingDocument。
    // R1-S1：txtChapters 上提至此先于 units 计算（小文件滚动 units 须按章对齐切块，
    // 章节边界是切块输入）；pagerEngine 改为只读消费。
    val tocProfile = inputs.ruleSnapshot.effectiveTocProfile
    val derived = rememberReaderDerivedState(
        bookIndex = bookIndex,
        markdownDocument = markdownDocument,
        txtStreamingDocument = txtStreamingDocument,
        plainContent = plainContent,
        txtStreamingFileIndex = txtStreamingFileIndex,
        epubBook = epubBook,
        tocProfile = tocProfile,
        textContent = textContent,
    )
    val chapterStartOffsets = derived.chapterStartOffsets
    val readingUnits = derived.readingUnits
    val txtChapters = derived.txtChapters
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
        bookId = bid,
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
        tocProfile = tocProfile,
        replaceRules = inputs.ruleSnapshot.effectiveReplace,
        txtStreamingDocument = txtStreamingDocument,
        readingUnits = readingUnits,
        txtChapters = txtChapters,
    )
    val pagerEngineOn = pagerEngine.pagerEngineOn
    val pagedJumpRequest = pagerEngine.pagedJumpRequest
    val pagedHardwareTurnRequest = pagerEngine.pagedHardwareTurnRequest
    var pagedAbsOffset by pagerEngine.pagedAbsOffsetState
    var pagedPercent by pagerEngine.pagedPercentState
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
        pagedSource = pagedSource,
    )
    val plainListState = progressState.plainListState
    val epubListState = progressState.epubListState
    val visiblePlainOffset = progressState.visiblePlainOffset
    val contentText = progressState.contentText
    val txtChapterIndex = progressState.txtChapterIndex
    val currentChapterTitle = progressState.currentChapterTitle
    val progressPercent = progressState.progressPercent

    // B3：护眼调度 / 朗读句高亮 / 块全局偏移 / TTS 句定位 / 滚动焦点块派生组
    // 抽到 reader/ReaderEyeCareFocusState.kt，逐字保真（State-holder 模式）。
    val eyeCareFocus = rememberReaderEyeCareFocusState(
        readerSettings = readerSettings,
        paper = paper,
        showTts = showTts,
        tts = tts,
        epubBook = epubBook,
        contentText = contentText,
        chapterStartOffsets = chapterStartOffsets,
        chapterIndex = chapterIndex,
        chapterBlocks = chapterBlocks,
        navFocusBlockIndexState = mutableHolders.navFocusBlockIndexState,
    )

    val autoPagingPaused = !readerResumedState.value ||
        sheet != null ||
        noteOpen ||
        selectedText.isNotBlank() ||
        showTts ||
        isLoading ||
        error != null
    val autoPagingSupported = readerSettings.readerMode == "scroll" ||
        (pagerEngineOn && pagedSource != null)

    // ── B3：导航 / chrome 动作闭包组抽到 reader/ReaderActions.kt::buildReaderNavActions，
    // 各闭包体逐字保真（原局部 fun 每次重组重建，顶层 builder 语义一致）。──
    val nav = buildReaderNavActions(
        epubBook, markdownDocument, pagerEngineOn, chapterStartOffsets, pagedJumpRequest,
        chapterIndexState, tts, onAction, onBack, bid, selectedGlobalOffset, selectedText,
        selectedRangeStart, scope, snackbarHost, readingUnits, plainListState, loadedBook,
        error, pendingInitialPosition, pagerEngine.pagedAbsOffsetState, pagedSource, chapterIndex, epubListState,
        eyeCareFocus.blockGlobalOffsets, eyeCareFocus.chapterBase, chapterBlocks, bookIndex,
        visiblePlainOffset, txtStreamingDocument, plainContent, txtChapters,
        txtChapterIndex, contentText, ttsResumeChapterState, ttsResumeOffsetState, bookTitle,
        currentChapterTitle, context, showTts, mutableHolders.autoPagingActiveState, autoPagingSupported,
        epubDocument,
        ttsContentText = progressState.ttsContentText,
        onReturnToReading = onTemporaryReturn,
    )

    val persistOnLeave: () -> Unit = {
        if (!temporaryInspection) nav.persistCurrentProgress()
    }

    LaunchedEffect(pagerEngine.replacementAvailability, pagerEngineOn, replacementStartupNoticeShown) {
        readerReplacementStartupNoticeIfNeeded(
            availability = pagerEngine.replacementAvailability,
            pagerEngineOn = pagerEngineOn,
            alreadyShown = replacementStartupNoticeShown,
        )?.let { message ->
            replacementStartupNoticeShown = true
            nav.showNotice(message)
        }
    }
    val replaceProjectionNotice = pagerEngine.replaceProjectionNotice.value
    LaunchedEffect(replaceProjectionNotice) {
        val message = replaceProjectionNotice ?: return@LaunchedEffect
        nav.showNotice(message)
        pagerEngine.replaceProjectionNotice.value = null
    }

    // ── B3：会话级 Effects 聚合 → reader/ReaderSessionEffects.kt（设置同步 / TTS 同步与续读 /
    // 平台 Effects / 进度与位置 Effects / 运行时 Effects / TTS 跟读），调用实参逐字保真。──
    ReaderSessionEffects(
        inputs = inputs,
        callbacks = callbacks,
        progressState = progressState,
        derived = derived,
        pagerEngine = pagerEngine,
        holders = mutableHolders,
        docLoad = docLoad,
        bid = bid,
        readerSettings = readerSettings,
        isLoading = isLoading,
        error = error,
        loadedBook = loadedBook,
        chapterIndex = chapterIndex,
        epubBook = epubBook,
        markdownDocument = markdownDocument,
        plainContent = plainContent,
        chapterBlocks = chapterBlocks,
        savedPlainOffset = savedPlainOffset,
        savedPlainPercent = savedPlainPercent,
        savedEpubOffsetInChapter = savedEpubOffsetInChapter,
        chapterBase = eyeCareFocus.chapterBase,
        blockGlobalOffsets = eyeCareFocus.blockGlobalOffsets,
        bookIndex = bookIndex,
        recentChapters = recentChapters,
        activeReadingMsState = activeReadingMsState,
        currentMinuteState = eyeCareFocus.currentMinuteState,
        chapterIndexState = chapterIndexState,
        settingsRef = settingsRef,
        readerResumedState = readerResumedState,
        ttsResumeOffsetState = ttsResumeOffsetState,
        ttsResumeChapterState = ttsResumeChapterState,
        autoPagingPaused = autoPagingPaused,
        focusBlockIndex = eyeCareFocus.focusBlockIndex,
        epubBringRequester = eyeCareFocus.epubBringRequester,
        controlsVisible = controlsVisible,
        sheetOpenGuard = sheetOpenGuard,
        paperBg = paperBg,
        paperIsLight = paper.isLight,
        appDark = appDark,
        isTxt = isTxt,
        isChapterLoading = isChapterLoading,
        searchSession = searchSession,
        tts = tts,
        haptic = haptic,
        scope = scope,
        showNotice = nav.showNotice,
        goToChapter = nav.goToChapter,
        jumpToPlainOffset = nav.jumpToPlainOffset,
        jumpToMarkdownOffset = nav.jumpToMarkdownOffset,
        persistCurrentProgress = persistOnLeave,
        openTts = nav.openTts,
    )

    // ── 笔记对话框（已提取到 ReaderNoteDialog）──────────────────────
    if (noteOpen) {
        com.creationreadingassistant.ui.screen.reader.sheets.ReaderNoteDialog(
            selectedText = selectedText,
            noteBody = noteBody,
            onNoteBodyChange = { onAction(ReaderAction.SetNoteBody(it)) },
            onSave = {
                // B3：保存逻辑抽到 reader/ReaderActions.kt::saveReaderNote，逐字保真。
                saveReaderNote(
                    onAction, bid, selectedText, noteBody, currentChapterTitle,
                    progressPercent, nav.computeLocatorJson(),
                )
                nav.showNotice("已保存笔记")
            },
            onDismiss = { onAction(ReaderAction.SetNoteOpen(false)) },
        )
    }

    ReaderScaffold(
        progressState = progressState,
        derived = derived,
        pagerEngine = pagerEngine,
        paper = paper,
        eyeCareActive = eyeCareFocus.eyeCareActive,
        eyeFilterColor = eyeCareFocus.eyeFilterColor,
        paperTexture = appearance.paperTexture,
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
        blockGlobalOffsets = eyeCareFocus.blockGlobalOffsets,
        chapterBase = eyeCareFocus.chapterBase,
        ttsSentenceRangeInChapter = eyeCareFocus.ttsSentenceRangeInChapter,
        focusBlockIndex = eyeCareFocus.focusBlockIndex,
        sentenceHighlightBg = eyeCareFocus.sentenceHighlightBg,
        searchHighlightBg = eyeCareFocus.searchHighlightBg,
        epubBringRequester = eyeCareFocus.epubBringRequester,
        isTxt = isTxt,
        tts = tts,
        autoPagingPaused = autoPagingPaused,
        appDark = appDark,
        activeReadingMs = activeReadingMs,
        goToChapter = nav.goToChapter,
        syncPagedChapter = nav.syncPagedChapter,
        showNotice = nav.showNotice,
        seekToPercent = nav.seekToPercent,
        jumpToPlainOffset = nav.jumpToPlainOffset,
        handleChromeAction = nav.handleChromeAction,
        computeLocatorJson = nav.computeLocatorJson,
        onPersistProgress = persistOnLeave,
        inputs = inputs,
        callbacks = callbacks,
        settingsVm = settingsVm,
        sheetState = sheetState,
        searchSession = searchSession,
        holders = mutableHolders,
    )

}
