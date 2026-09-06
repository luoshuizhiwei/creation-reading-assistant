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
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.ui.screen.reader.tts.TtsAutoNextChapterEffect
import com.creationreadingassistant.ui.screen.reader.tts.TtsEngineHost
import com.creationreadingassistant.ui.screen.reader.tts.TtsReaderSyncEffect
import com.creationreadingassistant.ui.screen.reader.tts.TtsResumeEffect
import com.creationreadingassistant.ui.screen.reader.tts.TtsSettingsSyncEffect
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent
import com.creationreadingassistant.ui.viewmodel.PendingTxtRuleAnchor
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
    chapterIndexState: MutableIntState,
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
    isChapterLoading: Boolean,
    searchSession: BookSearchSession,
    tts: TtsEngineHost,
    haptic: (HapticFeedbackType) -> Unit,
    scope: CoroutineScope,
    showNotice: (String) -> Unit,
    goToChapter: (Int) -> Unit,
    jumpToPlainOffset: (Int) -> Unit,
    persistCurrentProgress: () -> Unit,
    openTts: () -> Unit,
) {
    // 状态袋解构（与原 ReaderScreen 局部取值一致）
    val screenState = inputs.screenState
    val highlights: List<HighlightEntity> = inputs.highlights
    val notes = inputs.notes
    val txtTocRuleIdFromVm = inputs.txtTocRuleIdFromVm
    val txtRuleScanResult = inputs.txtRuleScanResult
    val ruleSnapshot = inputs.ruleSnapshot
    val ruleMutationResult = inputs.ruleMutationResult
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
    val readingActive = bid.isNotBlank() &&
        loadedBook?.id == bid &&
        readerResumedState.value &&
        !isLoading &&
        error == null

    // 加载状态或生命周期变化时立即开启/收口会话；每秒心跳由 ReaderProgressEffects 驱动。
    LaunchedEffect(bid, readingActive) {
        onAction(ReaderAction.UpdateReadingActivity(bid, readingActive, progressState.progressPercent))
    }

    // 用可变 State 持有最新设置，避免提醒计时器因设置变化反复重建 / 捕获旧值
    LaunchedEffect(readerSettings) { settingsRef.value = readerSettings }

    // TTS 高级项：首次将持久化的音调/音量/音色/定时停止载入控制器
    TtsSettingsSyncEffect(tts = tts, readerSettings = readerSettings)

    LaunchedEffect(bid, txtTocRuleIdFromVm) {
        holders.txtTocRuleIdState.value = txtTocRuleIdFromVm
    }

    // P1-A：规则写入成功后重新识别当前流式 TXT（多规则目录接入实际管线）。
    // 只处理会改变有效目录身份（profile.key）的 Success/Saved/Migrated（快速单选）；
    // REPLACE 净化、校验失败 / NotFound、非 TXT 与身份未变化的写入一律不触发；
    // 切书后快照 bookId 不匹配自动跳过。锚点保留用户当前 source 绝对偏移，
    // 重建完成后由 ReaderRuntimeEffects 恢复最近章节 / 位置。
    val currentTxtTocKey = docLoad.txtStreamingFileIndex?.detectedRuleId
        ?: (loadedBook?.content as? ReaderLoadedContent.Text)?.preDetectedRuleId
    LaunchedEffect(ruleMutationResult, ruleSnapshot.effectiveTocProfile.key, currentTxtTocKey) {
        if (!TxtRuleRescanPolicy.shouldRescanStreamingTxt(
                mutationResult = ruleMutationResult,
                snapshot = ruleSnapshot,
                bid = bid,
                isTxt = isTxt,
                currentTocKey = currentTxtTocKey,
            )
        ) {
            return@LaunchedEffect
        }
        if (ruleMutationResult is RuleMutationResult.Migrated) {
            // 快速单选（Migrated）不在规则管理面板展示反馈，消费后立即清除
            onAction(ReaderAction.ClearRuleMutationResult)
        }
        val textContent = loadedBook?.content as? ReaderLoadedContent.Text ?: return@LaunchedEffect
        if (textContent.streamingDocument == null) return@LaunchedEffect
        // 可重扫的 backing/source（直接 file:// 源文件或 cache 临时副本）——
        // ownedTempFile 只描述 release 时可删除的资源，直接源文件场景下恒为 null。
        val sourcePath = textContent.sourceFile?.absolutePath ?: return@LaunchedEffect
        val profileKey = ruleSnapshot.effectiveTocProfile.key
        holders.pendingTxtRuleAnchorState.value = PendingTxtRuleAnchor(bid, profileKey, visiblePlainOffset)
        onAction(ReaderAction.RescanTxtToc(bid, sourcePath, profileKey))
    }

    // R3：跨会话 TTS 续读
    TtsResumeEffect(
        tts = tts,
        bookId = bid,
        settingsStore = settingsStore,
        isEpub = epubBook != null,
        isMarkdown = markdownDocument != null,
        chapterIndex = chapterIndex,
        onResumeOffsetChanged = { ttsResumeOffsetState.intValue = it },
        onResumeChapterChanged = { ttsResumeChapterState.intValue = it },
        pagerEngineOn = pagerEngineOn,
        pagedSource = pagedSource,
    )

    // 听书连续朗读：EPUB / Markdown 按章接续（播放文本=章文本，翻章语义明确）。
    // TXT 的播放文本是整本/窗口，双语义下自动接续容易整本重读，暂不启用（count=0 即禁用）。
    TtsAutoNextChapterEffect(
        tts = tts,
        showTts = screenState.showTts,
        chapterIndex = chapterIndex,
        chapterCount = when {
            epubBook != null -> epubBook.chapters.size
            markdownDocument != null -> markdownDocument.chapters.size
            else -> 0
        },
        contentReady = contentText.isNotBlank() && !isChapterLoading && progressState.ttsContentTextReady,
        goToChapter = goToChapter,
        replayTts = openTts,
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
        screenOrientation = readerSettings.screenOrientation,
        // 音量键翻页统一处理 → reader/ReaderActions.kt::readerVolumeKeyTurn
        // 消费成功后派发 ReaderAction.PageTurn：
        // - 分页引擎路径：PageTurner.onPageTurned 会再派发一次（同结果，无副作用）；
        // - 非分页引擎路径（EPUB/TXT 滚动列表、或整章跳转）：PageTurner 不参与，必须在这里派发才会 hide chrome。
        onVolumeUp = {
            val consumed = readerVolumeKeyTurn(
                -1, readerSettings, screenState.showTts, pagerEngineOn, pagedHardwareTurnRequest,
                epubBook, markdownDocument, chapterIndex, goToChapter, scope, plainListState, epubListState,
            )
            if (consumed) onAction(ReaderAction.PageTurn)
            consumed
        },
        onVolumeDown = {
            val consumed = readerVolumeKeyTurn(
                1, readerSettings, screenState.showTts, pagerEngineOn, pagedHardwareTurnRequest,
                epubBook, markdownDocument, chapterIndex, goToChapter, scope, plainListState, epubListState,
            )
            if (consumed) onAction(ReaderAction.PageTurn)
            consumed
        },
        onReaderResumed = { resumed ->
            readerResumedState.value = resumed
            onAction(
                ReaderAction.UpdateReadingActivity(
                    bookId = bid,
                    active = bid.isNotBlank() && loadedBook?.id == bid && resumed && !isLoading && error == null,
                    progressPercent = progressState.progressPercent,
                ),
            )
        },
        onPersistProgress = persistCurrentProgress,
        controlsVisibleForAutoHide = controlsVisible,
        autoHideSeconds = readerSettings.autoHideSeconds,
        sheetOpenGuard = sheetOpenGuard,
        // 自动隐藏到点走状态机 AutoHideElapsed（与翻页隐藏同结果、不同事件语义）。
        onAutoHide = { onAction(ReaderAction.AutoHideElapsed(readerSettings.autoHideSeconds)) },
    )

    // ── 会话计时 / 阅读提醒 / 进度持久化 / 位置恢复 / 高亮精确定位 ──────────────
    // 6 个可变状态以 State-holder 形式传入（读 .value 拿当前快照，避免 effect 体捕获旧值）
    ReaderProgressEffects(
        bid = bid,
        isLoading = isLoading,
        error = error,
        readingActive = readingActive,
        progressPercent = progressState.progressPercent,
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

    // ── 书内搜索：统一 target 消费（命中选择 / 上一处 / 下一处）──────────
    // 文档上下文（章文档/纯文本）+ 渲染模式 + 阅读器状态一次性接入导航模块；
    // 章切换、paged jump、scroll block 定位、stale guard 与 TXT/EPUB/Markdown
    // 分派全部集中在 ReaderSearchEffects.kt 的 SearchHitNavigationExecutor。
    SearchHitNavigationEffect(
        searchNav = buildSearchHitNavigation(
            session = searchSession,
            bid = bid,
            chaptered = epubBook != null || markdownDocument != null,
            pagerEngineOn = pagerEngineOn,
            chapterStartOffsets = chapterStartOffsets,
            readingUnits = readingUnits,
            pagedJumpRequest = pagedJumpRequest,
            chapterIndexState = chapterIndexState,
            plainListState = plainListState,
            searchScrollFocusRequestState = holders.searchScrollFocusRequestState,
            markdownBlocksGlobal = (markdownDocument as? MarkdownDocument)?.isWholeDocumentParse == true,
            goToChapter = goToChapter,
            onLoadChapterBlocks = onLoadChapterBlocks,
        ),
    )

    // ── 运行时 effect：章节淡入 / 护眼时间 / 焦点滚动 / TXT 规则 / 自动翻页 / 触感 ──
    ReaderRuntimeEffects(
        chapterFadeKey = chapterFadeKey,
        chapterFade = chapterFade,
        readerSettings = readerSettings,
        currentMinuteState = currentMinuteState,
        focusBlockIndex = focusBlockIndex,
        epubBringRequester = epubBringRequester,
        bookId = bid,
        txtRuleScanResult = txtRuleScanResult,
        pendingTxtRuleAnchorState = holders.pendingTxtRuleAnchorState,
        pagerEngineOn = pagerEngineOn,
        pagedJumpRequest = pagedJumpRequest,
        jumpToPlainOffset = jumpToPlainOffset,
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
        isMarkdown = markdownDocument != null,
        plainContent = plainContent,
        txtStreamingDocument = txtStreamingDocument,
        visiblePlainOffset = visiblePlainOffset,
        jumpToPlainOffset = jumpToPlainOffset,
        pagerEngineOn = pagerEngineOn,
        isEpub = epubBook != null,
        pagedJumpTo = { pagedJumpRequest.value = it },
        chapterStartOffsets = chapterStartOffsets,
        chapterIndex = chapterIndex,
        pagedSource = pagedSource,
    )
}
