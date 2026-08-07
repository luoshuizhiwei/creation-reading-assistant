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
    showNotice: (String) -> Unit,
): ReaderContentHostCallbacks = ReaderContentHostCallbacks(
    onPagedPositionChanged = { off, pct, chapterToGo ->
        onPagedAbsOffsetChange(off)
        onPagedPercentChange(pct)
        onPendingInitialPositionChange(false)
        if (chapterToGo != null) goToChapter(chapterToGo)
    },
    onToggleControls = { onAction(ReaderAction.ToggleControls()) },
    onHideControls = { onAction(ReaderAction.ToggleControls(false)) },
    onSelect = { text, globalOffset, rangeStart ->
        onAction(ReaderAction.SetSelectedText(text, rangeStart, globalOffset))
    },
    onAutoPagingFinished = {
        onAutoPagingActiveChange(false)
        showNotice("已读到书末")
    },
    onStopAutoPaging = { onAutoPagingActiveChange(false) },
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
    tts: TtsController,
    selectedText: String,
    bid: String,
    currentChapterTitle: String,
    progressPercent: Float,
    clipboard: ClipboardManager,
    onAction: (ReaderAction) -> Unit,
    handleChromeAction: (ReaderChromeAction) -> Unit,
    seekToChapterPercent: (Float) -> Unit,
    goToChapter: (Int) -> Unit,
    computeLocatorJson: () -> String?,
    showNotice: (String) -> Unit,
): ReaderInteractionLayerCallbacks = ReaderInteractionLayerCallbacks(
    onOverflowExpandedChange = { onAction(ReaderAction.SetShowOverflow(it)) },
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
                payload = "{}",
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
    onCopy = { clipboard.setText(AnnotatedString(selectedText)); showNotice("已复制") },
    onSearch = {
        onAction(ReaderAction.SetSearchQuery(selectedText))
        onAction(ReaderAction.OpenSheet(ReaderSheet.SEARCH))
    },
    onClearSelection = { onAction(ReaderAction.ClearSelection) },
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
    // B1 状态袋瘦身：按域组装 4 个分组对象，字段值与原平铺 1:1。
    document = ReaderSheetDocumentState(
        epubBook = epubBook,
        epubDocument = epubDocument,
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
        bookIndex = bookIndex,
        txtTocRuleId = txtTocRuleId,
        txtRulePreviews = txtRulePreviews,
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
        searchQuery = searchQuery,
        recentChapters = recentChapters.toList(),
        appDark = appDark,
    ),
)

/**
 * 构造底部弹层分发所需的回调集合。`onPickChapter` / `onTxtRule` / `onSearchJump` /
 * `onExportHighlights` / `onSaveAiExplainInspiration` / `onSaveInspiration` /
 * `onCreateCategory` / `onCreateTag` 的完整逻辑体逐字搬运：
 * - `recentChapters` 直接传 [SnapshotStateList] 引用，`.add/.remove` 保持原行为；
 * - `pagedJumpRequest` 直接传 [MutableState] 引用，`.value =` 写保持原行为；
 * - `onSearchJump` 的 `scope.launch { onLoadChapterBlocks(...) }` 与
 *   [blockIndexForChapterOffset] 调用保持原样。
 *
 * B2：原对本地副本 var 的 setter 写入改走 [ReaderAction]：onSheetChange(null) →
 * CloseSheet，onSheetChange(x) → OpenSheet(x)，onSearchQueryChange → SetSearchQuery，
 * onSaveInspiration 尾部的 onSelectedTextChange("") → ClearSelection（选区已消费完毕，
 * 附带重置偏移/showColorRow 无可观测差异）。
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
        if (epubBook != null) {
            goToChapter(it)
        } else {
            txtChapters.getOrNull(it)?.let { c -> jumpToPlainOffset(c.startOffset) }
        }
        onAction(ReaderAction.CloseSheet)
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
        onAction(ReaderAction.CloseSheet)
    },
    onJumpToHighlight = { id ->
        onPendingHighlightIdChange(id)
        onAction(ReaderAction.CloseSheet)
    },
    onJumpToBookmark = { id ->
        // 书签与高亮共用 SE4 的 locator 解析路径（pendingHighlightIdState 同时覆盖 notes）
        onPendingHighlightIdChange(id)
        onAction(ReaderAction.CloseSheet)
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
        onAction(ReaderAction.CloseSheet)
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
        onAction(ReaderAction.ClearSelection)
        onAction(ReaderAction.CloseSheet)
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
