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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.ReadingUnitCache
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.ui.screen.reader.ReaderChromeAction
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import com.creationreadingassistant.ui.screen.reader.tts.TtsController
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
 * 构造正文宿主所需的只读展示数据。字段值 1:1 来自 ReaderScreen 主函数的局部状态。
 */
internal fun buildReaderContentHostState(
    pagerEngineOn: Boolean,
    pagedSource: PagedChapterSource?,
    readerSettings: ReaderSettings,
    paper: ReaderPaperPalette,
    paperFg: Color,
    bid: String,
    txtTocRuleId: String,
    bookTitle: String,
    chapterStartOffsets: List<Int>,
    savedEpubOffsetInChapter: Int,
    visiblePlainOffset: Int,
    savedPlainOffset: Int,
    savedPlainPercent: Float,
    txtStreamingDocument: PlainTextDocument?,
    plainContent: String,
    pagedAbsOffset: Int,
    pagedPercent: Float,
    pendingInitialPosition: Boolean,
    epubBook: EpubBook?,
    markdownDocument: ReaderDocument?,
    chapterIndex: Int,
    txtChapterIndex: Int,
    isChapterLoading: Boolean,
    chapterBlocks: List<DocBlock>,
    epubListState: LazyListState,
    plainListState: LazyListState,
    chapterFade: Animatable<Float, AnimationVector1D>,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
    ttsSentenceRangeInChapter: Pair<Int, Int>?,
    focusBlockIndex: Int?,
    sentenceHighlightBg: Color,
    epubBringRequester: BringIntoViewRequester,
    readingUnits: List<ReadingUnit>,
    isTxt: Boolean,
    showTts: Boolean,
    tts: TtsController,
    selectedText: String,
    selectedGlobalOffset: Int,
    selectedRangeStart: Int,
    autoPagingActive: Boolean,
    autoPagingPaused: Boolean,
    pagedJumpRequest: MutableState<Int?>,
    pagedHardwareTurnRequest: MutableState<Int?>,
    unitCache: ReadingUnitCache,
    pageIndexStore: PageIndexStore,
    highlights: List<HighlightEntity>,
): ReaderContentHostState = ReaderContentHostState(
    pagerEngineOn = pagerEngineOn,
    pagedSource = pagedSource,
    readerSettings = readerSettings,
    paper = paper,
    paperFg = paperFg,
    bid = bid,
    txtTocRuleId = txtTocRuleId,
    bookTitle = bookTitle,
    chapterStartOffsets = chapterStartOffsets,
    savedEpubOffsetInChapter = savedEpubOffsetInChapter,
    visiblePlainOffset = visiblePlainOffset,
    savedPlainOffset = savedPlainOffset,
    savedPlainPercent = savedPlainPercent,
    txtStreamingDocument = txtStreamingDocument,
    plainContent = plainContent,
    pagedAbsOffset = pagedAbsOffset,
    pagedPercent = pagedPercent,
    pendingInitialPosition = pendingInitialPosition,
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
    sentenceHighlightBg = sentenceHighlightBg,
    epubBringRequester = epubBringRequester,
    readingUnits = readingUnits,
    isTxt = isTxt,
    showTts = showTts,
    tts = tts,
    selectedText = selectedText,
    selectedGlobalOffset = selectedGlobalOffset,
    selectedRangeStart = selectedRangeStart,
    autoPagingActive = autoPagingActive,
    autoPagingPaused = autoPagingPaused,
    pagedJumpRequest = pagedJumpRequest,
    pagedHardwareTurnRequest = pagedHardwareTurnRequest,
    unitCache = unitCache,
    pageIndexStore = pageIndexStore,
    highlights = highlights,
)

/**
 * 构造正文宿主所需的回调集合。lambda 体逐字搬运自 ReaderScreen，对局部 var 的写改为
 * setter 调用，局部 fun（[goToChapter] / [showNotice]）作为函数参数传入。
 */
internal fun buildReaderContentHostCallbacks(
    controlsVisible: Boolean,
    onControlsVisibleChange: (Boolean) -> Unit,
    onPagedAbsOffsetChange: (Int) -> Unit,
    onPagedPercentChange: (Float) -> Unit,
    onPendingInitialPositionChange: (Boolean) -> Unit,
    onSelectedTextChange: (String) -> Unit,
    onSelectedGlobalOffsetChange: (Int) -> Unit,
    onSelectedRangeStartChange: (Int) -> Unit,
    onAutoPagingActiveChange: (Boolean) -> Unit,
    goToChapter: (Int) -> Unit,
    showNotice: (String) -> Unit,
): ReaderContentHostCallbacks = ReaderContentHostCallbacks(
    onPagedPositionChanged = { off, pct, chapterToGo ->
        onPagedAbsOffsetChange(off)
        onPagedPercentChange(pct)
        onPendingInitialPositionChange(false)
        if (chapterToGo != null) goToChapter(chapterToGo)
    },
    onToggleControls = { onControlsVisibleChange(!controlsVisible) },
    onHideControls = { onControlsVisibleChange(false) },
    onSelect = { text, globalOffset, rangeStart ->
        onSelectedTextChange(text)
        onSelectedGlobalOffsetChange(globalOffset)
        onSelectedRangeStartChange(rangeStart)
    },
    onAutoPagingFinished = {
        onAutoPagingActiveChange(false)
        showNotice("已读到书末")
    },
    onGoToChapter = { goToChapter(it) },
)

/**
 * 构造覆盖层所需的只读展示数据。`isFirstChapter` / `isLastChapter` / `isEpub` /
 * `showProgressBar` 在此处由传入的原始值计算，与原内联构造完全一致。
 */
internal fun buildReaderInteractionLayerState(
    controlsVisible: Boolean,
    selectedText: String,
    showColorRow: Boolean,
    showTts: Boolean,
    showReaderOverflow: Boolean,
    autoPagingActive: Boolean,
    progressPercent: Float,
    bookTitle: String,
    currentChapterTitle: String,
    chapterProgress: Float,
    chapterIndex: Int,
    epubBook: EpubBook?,
    isLoading: Boolean,
    error: String?,
    readerSettings: ReaderSettings,
    paper: ReaderPaperPalette,
): ReaderInteractionLayerState = ReaderInteractionLayerState(
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
    isFirstChapter = chapterIndex <= 0,
    isLastChapter = epubBook == null || chapterIndex >= epubBook!!.chapters.lastIndex,
    isEpub = epubBook != null,
    isLoading = isLoading,
    error = error,
    showProgressBar = readerSettings.showProgressBar,
    paper = paper,
)

/**
 * 构造覆盖层所需的回调集合。`onPickColor` 的完整高亮保存逻辑逐字搬运：locator 快照、
 * 先存局部 `snapshotText` 再清选区、[HighlightEntity] 字段、[nowIso] 时间戳全部保持原样。
 */
@Suppress("LongParameterList")
internal fun buildReaderInteractionLayerCallbacks(
    chapterIndex: Int,
    settingsVm: SettingsViewModel,
    tts: TtsController,
    showColorRow: Boolean,
    selectedText: String,
    bid: String,
    currentChapterTitle: String,
    progressPercent: Float,
    clipboard: ClipboardManager,
    onAction: (ReaderAction) -> Unit,
    onShowReaderOverflowChange: (Boolean) -> Unit,
    onShowTtsChange: (Boolean) -> Unit,
    onShowColorRowChange: (Boolean) -> Unit,
    onSheetChange: (ReaderSheet?) -> Unit,
    onNoteOpenChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectedTextChange: (String) -> Unit,
    onSelectedGlobalOffsetChange: (Int) -> Unit,
    onSelectedRangeStartChange: (Int) -> Unit,
    handleChromeAction: (ReaderChromeAction) -> Unit,
    seekToChapterPercent: (Float) -> Unit,
    goToChapter: (Int) -> Unit,
    computeLocatorJson: () -> String?,
    showNotice: (String) -> Unit,
): ReaderInteractionLayerCallbacks = ReaderInteractionLayerCallbacks(
    onOverflowExpandedChange = { onShowReaderOverflowChange(it) },
    onChromeAction = handleChromeAction,
    onSeekChapterPercent = { seekToChapterPercent(it) },
    onPrevChapter = { goToChapter(chapterIndex - 1) },
    onNextChapter = { goToChapter(chapterIndex + 1) },
    onPersistTts = { p, v, id, t ->
        settingsVm.updateReader {
            copy(
                ttsPitch = p,
                ttsVolume = v,
                ttsVoiceId = id,
                ttsTimedStopMinutes = t,
            )
        }
    },
    onCloseTts = { tts.stop(); onShowTtsChange(false) },
    onToggleColor = { onShowColorRowChange(!showColorRow) },
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
                payload = "{}",
                created_at = nowIso(),
                device_id = null,
                revision = 1,
                updated_at = nowIso(),
                deleted_at = null,
            ),
        ))
        onShowColorRowChange(false)
        onSelectedTextChange("")
        onSelectedGlobalOffsetChange(-1)
        onSelectedRangeStartChange(-1)
        showNotice("已高亮")
    },
    onAiExplain = { onSheetChange(ReaderSheet.AI_EXPLAIN) },
    onInspiration = { onSheetChange(ReaderSheet.INSPIRATION) },
    onNote = { onNoteOpenChange(true) },
    onCopy = { clipboard.setText(AnnotatedString(selectedText)); showNotice("已复制") },
    onSearch = { onSearchQueryChange(selectedText); onSheetChange(ReaderSheet.SEARCH) },
    onClearSelection = { onSelectedTextChange(""); onShowColorRowChange(false); onSelectedGlobalOffsetChange(-1); onSelectedRangeStartChange(-1) },
)

/**
 * 构造底部弹层分发所需的只读展示数据。`txtChapterTitles` 由 [txtChapters] 现场映射，
 * `recentChapters` 由 [SnapshotStateList] 现场转 `List`，与原内联构造一致。
 */
@Suppress("LongParameterList")
internal fun buildReaderSheetHostState(
    epubBook: EpubBook?,
    epubDocument: ReaderDocument?,
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
    recentChapters: SnapshotStateList<Int>,
    appDark: Boolean,
): ReaderSheetHostState = ReaderSheetHostState(
    epubBook = epubBook,
    epubDocument = epubDocument,
    txtStreamingDocument = txtStreamingDocument,
    plainContent = plainContent,
    chapterIndex = chapterIndex,
    txtChapterIndex = txtChapterIndex,
    txtChapterTitles = txtChapters.map { it.title },
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
    recentChapters = recentChapters.toList(),
    appDark = appDark,
)

/**
 * 构造底部弹层分发所需的回调集合。`onPickChapter` / `onTxtRule` / `onSearchJump` /
 * `onExportHighlights` / `onSaveAiExplainInspiration` / `onSaveInspiration` /
 * `onCreateCategory` / `onCreateTag` 的完整逻辑体逐字搬运：
 * - `recentChapters` 直接传 [SnapshotStateList] 引用，`.add/.remove` 保持原行为；
 * - `pagedJumpRequest` 直接传 [MutableState] 引用，`.value =` 写保持原行为；
 * - `onSearchJump` 的 `scope.launch { onLoadChapterBlocks(...) }` 与
 *   [blockIndexForChapterOffset] 调用保持原样。
 */
@Suppress("LongParameterList")
internal fun buildReaderSheetHostCallbacks(
    epubBook: EpubBook?,
    txtChapters: List<DocChapter>,
    visiblePlainOffset: Int,
    textContent: ReaderLoadedContent.Text?,
    txtStreamingDocument: PlainTextDocument?,
    bid: String,
    pagerEngineOn: Boolean,
    plainContent: String,
    chapterStartOffsets: List<Int>,
    bookTitle: String,
    highlights: List<HighlightEntity>,
    context: Context,
    selectedText: String,
    currentChapterTitle: String,
    progressPercent: Float,
    bookAuthor: String?,
    recentChapters: SnapshotStateList<Int>,
    pagedJumpRequest: MutableState<Int?>,
    onSheetChange: (ReaderSheet?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectedTextChange: (String) -> Unit,
    onTxtTocRuleIdChange: (String) -> Unit,
    onPendingTxtRuleAnchorOffsetChange: (Int) -> Unit,
    onNavFocusBlockIndexChange: (Int?) -> Unit,
    onPendingHighlightIdChange: (String?) -> Unit,
    goToChapter: (Int) -> Unit,
    seekToPercent: (Float) -> Unit,
    jumpToPlainOffset: (Int) -> Unit,
    showNotice: (String) -> Unit,
    onLoadChapterBlocks: suspend (String, Int) -> List<DocBlock>,
    onAction: (ReaderAction) -> Unit,
    scope: CoroutineScope,
): ReaderSheetHostCallbacks = ReaderSheetHostCallbacks(
    onDismiss = { onSheetChange(null) },
    onOpenSettings = { onSheetChange(ReaderSheet.SETTINGS) },
    onOpenBookInfo = { onSheetChange(ReaderSheet.BOOK_INFO) },
    goToChapter = { goToChapter(it) },
    seekToPercent = { seekToPercent(it) },
    jumpToPlainOffset = { jumpToPlainOffset(it) },
    showNotice = { showNotice(it) },
    onSearchQueryChange = onSearchQueryChange,
    onClearSelectedText = { onSelectedTextChange("") },
    onPickChapter = {
        if (!recentChapters.contains(it)) {
            recentChapters.add(0, it)
            if (recentChapters.size > 5) recentChapters.removeAt(recentChapters.lastIndex)
        }
        if (epubBook != null) {
            goToChapter(it)
        } else {
            txtChapters.getOrNull(it)?.let { c -> jumpToPlainOffset(c.startOffset) }
        }
        onSheetChange(null)
    },
    onTxtRule = { ruleId ->
        val anchorOffset = visiblePlainOffset
        onTxtTocRuleIdChange(ruleId)
        val streamingTempFilePath = textContent?.ownedTempFile?.absolutePath
        if (txtStreamingDocument != null && streamingTempFilePath != null) {
            onPendingTxtRuleAnchorOffsetChange(anchorOffset)
            onAction(ReaderAction.ScanTxtTocRule(streamingTempFilePath!!, ruleId))
        } else {
            pagedJumpRequest.value = anchorOffset
        }
        onAction(ReaderAction.SaveTxtTocRule(bid, ruleId))
    },
    onSearchJump = { result ->
        if (result.chapterIndex >= 0 && epubBook != null) {
            goToChapter(result.chapterIndex)
            val globalOffset = chapterStartOffsets.getOrElse(result.chapterIndex) { 0 } +
                result.charOffset
            if (pagerEngineOn) {
                pagedJumpRequest.value = globalOffset
            } else {
                scope.launch {
                    val blocks = onLoadChapterBlocks(bid, result.chapterIndex)
                    onNavFocusBlockIndexChange(blockIndexForChapterOffset(blocks, result.charOffset))
                }
            }
        } else if (plainContent.isNotEmpty() || txtStreamingDocument != null) {
            val globalOffset = chapterStartOffsets.getOrElse(result.chapterIndex.coerceAtLeast(0)) { 0 } +
                result.charOffset
            jumpToPlainOffset(globalOffset)
        }
        onSheetChange(null)
    },
    onJumpToHighlight = { id ->
        onPendingHighlightIdChange(id)
        onSheetChange(null)
    },
    onExportHighlights = {
        val sb = StringBuilder()
        sb.appendLine("# 《${bookTitle}》书摘")
        highlights.groupBy { it.chapter_title ?: "" }.forEach { (chapter, items) ->
            sb.appendLine()
            sb.appendLine("## ${if (chapter.isBlank()) "未分类" else chapter}")
            items.forEachIndexed { i, h ->
                sb.appendLine("${i + 1}. ${h.text}")
                h.note?.takeIf { it.isNotBlank() }?.let { sb.appendLine("   批注：$it") }
            }
        }
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TITLE, "《${bookTitle}》书摘")
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString())
        context.startActivity(Intent.createChooser(intent, "导出书摘"))
    },
    onSaveAiExplainInspiration = { body, tags, categoryIds ->
        val snapshotText = selectedText
        onAction(
            ReaderAction.SaveInspiration(
                InspirationEntity(
                    id = UUID.randomUUID().toString(),
                    title = "AI 解读：${snapshotText.take(24)}",
                    body = body,
                    type = "note",
                    status = "inbox",
                    source_book_id = bid.ifBlank { null },
                    payload = buildInspirationPayload(bid, bookTitle, currentChapterTitle, snapshotText, progressPercent, tags, categoryIds, bookAuthor = bookAuthor),
                    created_at = nowIso(),
                    device_id = null,
                    revision = 1,
                    updated_at = nowIso(),
                    deleted_at = null,
                ),
            ),
        )
        onSheetChange(null)
        showNotice("已存入灵感")
    },
    onSaveInspiration = { title, body, tags, categoryIds ->
        val snapshotText = selectedText
        onAction(
            ReaderAction.SaveInspiration(
                InspirationEntity(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    body = body,
                    type = "note",
                    status = "inbox",
                    source_book_id = bid.ifBlank { null },
                    payload = buildInspirationPayload(bid, bookTitle, currentChapterTitle, snapshotText, progressPercent, tags, categoryIds, bookAuthor = bookAuthor),
                    created_at = nowIso(),
                    device_id = null,
                    revision = 1,
                    updated_at = nowIso(),
                    deleted_at = null,
                ),
            ),
        )
        onSelectedTextChange("")
        onSheetChange(null)
        showNotice("已保存灵感，并记录来源阅读位置")
    },
    onCreateCategory = { name ->
        val id = "mobile-category-${UUID.randomUUID()}"
        onAction(ReaderAction.CreateCategory(name))
        id
    },
    onCreateTag = { name ->
        val id = "mobile-tag-${UUID.randomUUID()}"
        onAction(ReaderAction.CreateTag(name))
        id
    },
)
