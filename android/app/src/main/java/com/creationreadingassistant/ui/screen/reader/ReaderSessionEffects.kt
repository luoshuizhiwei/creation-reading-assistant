package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.ui.screen.reader.tts.TtsController
import com.creationreadingassistant.ui.screen.reader.tts.TtsReaderSyncEffect
import com.creationreadingassistant.ui.screen.reader.tts.TtsResumeEffect
import com.creationreadingassistant.ui.screen.reader.tts.TtsSettingsSyncEffect
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import kotlinx.coroutines.CoroutineScope

/**
 * B3 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 主函数抽出的
 * 「会话级 Effects」聚合：设置引用同步 / TXT 规则 id 同步 / TTS 设置同步 / TTS 续读 /
 * 平台 Effects（常亮、沉浸、亮度、窗口底色、音量键、自动隐藏、生命周期）/
 * 进度与位置 Effects（[ReaderProgressEffects]）/ 运行时 Effects（[ReaderRuntimeEffects]）/
 * TTS 跟读（[TtsReaderSyncEffect]）。
 *
 * 每个 effect 调用实参逐字搬运自 ReaderScreen，不改任何时序与参数；可变状态以
 * State-holder 形式传入（读写 .value / .intValue，落回主函数持有的真实状态）。
 * 已拆出的状态袋（[ReaderProgressState] / [ReaderDerivedState] / [PagerEngineState] /
 * [ReaderScreenMutableHolders] / [ReaderDocumentLoadState]）原样透传并在块内解构，
 * 与 ReaderScaffold 的取值方式一致。
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderSessionEffects(
    // ── 已拆出的状态袋（逐字原样透传）──
    inputs: ReaderScreenInputs,
    callbacks: ReaderScreenCallbacks,
    progressState: ReaderProgressState,
    derived: ReaderDerivedState,
    pagerEngine: PagerEngineState,
    holders: ReaderScreenMutableHolders,
    docLoad: ReaderDocumentLoadState,
    // ── 只读展示值（逐字原样传入）──
    bid: String,
    readerSettings: ReaderSettings,
    isLoading: Boolean,
    error: String?,
    loadedBook: ReaderLoadedBook?,
    chapterIndex: Int,
    epubBook: EpubBook?,
    markdownDocument: com.creationreadingassistant.feature.reader.doc.ReaderDocument?,
    plainContent: String,
    chapterBlocks: List<DocBlock>,
    savedPlainOffset: Int,
    savedPlainPercent: Float,
    savedEpubOffsetInChapter: Int,
    chapterBase: Int,
    blockGlobalOffsets: List<Int>,
    bookIndex: BookIndex?,
    recentChapters: SnapshotStateList<Int>,
    activeReadingMsState: MutableLongState,
    currentMinuteState: MutableIntState,
    settingsRef: MutableState<ReaderSettings>,
    readerResumedState: MutableState<Boolean>,
    ttsResumeOffsetState: MutableIntState,
    ttsResumeChapterState: MutableIntState,
    autoPagingPaused: Boolean,
    focusBlockIndex: Int?,
    epubBringRequester: BringIntoViewRequester,
    controlsVisible: Boolean,
    sheetOpenGuard: Boolean,
    paperBg: Color,
    paperIsLight: Boolean,
    appDark: Boolean,
    isTxt: Boolean,
    tts: TtsController,
    haptic: (HapticFeedbackType) -> Unit,
    scope: CoroutineScope,
    showNotice: (String) -> Unit,
    goToChapter: (Int) -> Unit,
    jumpToPlainOffset: (Int) -> Unit,
    persistCurrentProgress: () -> Unit,
) {
    // 状态袋解构（与原 ReaderScreen 局部取值一致）
    val screenState = inputs.screenState
    val highlights: List<HighlightEntity> = inputs.highlights
    val notes = inputs.notes
    val txtTocRuleIdFromVm = inputs.txtTocRuleIdFromVm
    val txtRuleScanResult = inputs.txtRuleScanResult
    val onLoadChapterBlocks = callbacks.onLoadChapterBlocks
    val onExtractChapterText = callbacks.onExtractChapterText
    val onAction = callbacks.onAction
    val anchorCacheStore = callbacks.anchorCacheStore
    val settingsStore = callbacks.settingsStore

    val plainListState: LazyListState = progressState.plainListState
    val epubListState: LazyListState = progressState.epubListState
    val visiblePlainOffset = progressState.visiblePlainOffset
    val contentText = progressState.contentText
    val txtChapterIndex = progressState.txtChapterIndex
    val chapterFade = progressState.chapterFade
    val chapterFadeKey = progressState.chapterFadeKey

    val chapterStartOffsets = derived.chapterStartOffsets
    val readingUnits = derived.readingUnits

    val pagerEngineOn = pagerEngine.pagerEngineOn
    val pagedJumpRequest = pagerEngine.pagedJumpRequest
    val pagedHardwareTurnRequest = pagerEngine.pagedHardwareTurnRequest
    val txtChapters = pagerEngine.txtChapters
    val pagedSource = pagerEngine.pagedSource

    val txtStreamingDocument = docLoad.txtStreamingDocument

    // 用可变 State 持有最新设置，避免提醒计时器因设置变化反复重建 / 捕获旧值
    LaunchedEffect(readerSettings) { settingsRef.value = readerSettings }

    // TTS 高级项：首次将持久化的音调/音量/音色/定时停止载入控制器
    TtsSettingsSyncEffect(tts = tts, readerSettings = readerSettings)

    LaunchedEffect(bid, txtTocRuleIdFromVm) {
        holders.txtTocRuleIdState.value = txtTocRuleIdFromVm
    }

    // R3：跨会话 TTS 续读
    TtsResumeEffect(
        tts = tts,
        bookId = bid,
        settingsStore = settingsStore,
        isEpub = epubBook != null,
        chapterIndex = chapterIndex,
        onResumeOffsetChanged = { ttsResumeOffsetState.intValue = it },
        onResumeChapterChanged = { ttsResumeChapterState.intValue = it },
    )

    // ── 平台 Effects（常亮、沉浸、亮度、窗口底色、音量键、自动隐藏、生命周期）────
    ReaderPlatformEffects(
        keepAwake = readerSettings.keepAwake,
        immersiveMode = readerSettings.immersiveMode,
        controlsVisible = controlsVisible,
        paperIsLight = paperIsLight,
        appDark = appDark,
        readerBrightness = if (readerSettings.brightness < 0) -1 else readerSettings.brightness.coerceIn(5, 100),
        paperBgColor = paperBg,
        volumeKeyPaging = readerSettings.volumeKeyPaging,
        // 音量键翻页统一处理 → reader/ReaderActions.kt::readerVolumeKeyTurn
        onVolumeUp = {
            readerVolumeKeyTurn(
                -1, readerSettings, screenState.showTts, pagerEngineOn, pagedHardwareTurnRequest,
                epubBook, markdownDocument, chapterIndex, goToChapter, scope, plainListState,
            )
        },
        onVolumeDown = {
            readerVolumeKeyTurn(
                1, readerSettings, screenState.showTts, pagerEngineOn, pagedHardwareTurnRequest,
                epubBook, markdownDocument, chapterIndex, goToChapter, scope, plainListState,
            )
        },
        onReaderResumed = { readerResumedState.value = it },
        onPersistProgress = persistCurrentProgress,
        controlsVisibleForAutoHide = controlsVisible,
        autoHideSeconds = readerSettings.autoHideSeconds,
        sheetOpenGuard = sheetOpenGuard,
        onAutoHide = { onAction(ReaderAction.ToggleControls(false)) },
    )

    // ── 会话计时 / 阅读提醒 / 进度持久化 / 位置恢复 / 高亮精确定位 ──────────────
    // 6 个可变状态以 State-holder 形式传入（读 .value 拿当前快照，避免 effect 体捕获旧值）
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
        pendingInitialPositionState = holders.pendingInitialPositionState,
        pendingHighlightIdState = holders.pendingHighlightIdState,
        navFocusBlockIndexState = holders.navFocusBlockIndexState,
        settingsRef = settingsRef,
        pagedJumpRequest = pagedJumpRequest,
        onAction = onAction,
        showNotice = showNotice,
        goToChapter = goToChapter,
        jumpToPlainOffset = jumpToPlainOffset,
        onLoadChapterBlocks = onLoadChapterBlocks,
        onExtractChapterText = onExtractChapterText,
    )

    // ── 运行时 effect：章节淡入 / 护眼时间 / 焦点滚动 / TXT 规则 / 自动翻页 / 触感 ──
    ReaderRuntimeEffects(
        chapterFadeKey = chapterFadeKey,
        chapterFade = chapterFade,
        readerSettings = readerSettings,
        currentMinuteState = currentMinuteState,
        focusBlockIndex = focusBlockIndex,
        epubBringRequester = epubBringRequester,
        txtRuleScanResult = txtRuleScanResult,
        pendingTxtRuleAnchorOffsetState = holders.pendingTxtRuleAnchorOffsetState,
        pagedJumpRequest = pagedJumpRequest,
        txtStreamingDocumentState = docLoad.txtStreamingDocumentState,
        txtStreamingFileIndexState = docLoad.txtStreamingFileIndexState,
        autoPagingActiveState = holders.autoPagingActiveState,
        autoPagingPaused = autoPagingPaused,
        epubListState = epubListState,
        plainListState = plainListState,
        epubBook = epubBook,
        showNotice = showNotice,
        haptic = haptic,
    )

    // R2：朗读时把正文跟到当前句
    TtsReaderSyncEffect(
        tts = tts,
        showTts = screenState.showTts,
        isTxt = isTxt,
        plainContent = plainContent,
        txtStreamingDocument = txtStreamingDocument,
        visiblePlainOffset = visiblePlainOffset,
        jumpToPlainOffset = jumpToPlainOffset,
        pagerEngineOn = pagerEngineOn,
        isEpub = epubBook != null,
        pagedJumpTo = { pagedJumpRequest.value = it },
        chapterStartOffsets = chapterStartOffsets,
        chapterIndex = chapterIndex,
    )
}
