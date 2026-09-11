package com.creationreadingassistant.ui.screen.reader

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.EpubDocument
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.ui.screen.reader.tts.TtsAvailability
import com.creationreadingassistant.ui.screen.reader.tts.TtsEngineHost
import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsEngineId
import com.creationreadingassistant.ui.screen.reader.tts.TtsNoticeAction
import com.creationreadingassistant.ui.screen.reader.tts.TtsNoticePolicy
import com.creationreadingassistant.ui.screen.reader.tts.TtsPlayResult
import com.creationreadingassistant.ui.screen.reader.tts.ttsResumeStartAt
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Phase 3 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 主函数抽出的
 * 闭包辅助函数，改为顶层函数。本文件保留 chrome/TTS/笔记与导航动作组装器；
 * 进度与定位动作见 ReaderProgressActions.kt，音量键翻页见 ReaderVolumeTurn.kt。
 *
 * ## 行为保真原则
 *
 * - 只读依赖（val / 不可变入参）作为值参数传入；
 * - 已转换为 State-holder 的可变状态（chapterIndexState / autoPagingActiveState）直接传入
 *   MutableState，在函数内用 `.value` 读写，与原 `var x` 语义一致；
 * - 未转换的可变状态（showTts / controlsVisible / selectedText / sheet / searchQuery）
 *   通过 `onXxxChange: (T) -> Unit` setter 参数写入，ReaderScreen 传 `{ xxx = it }`；
 * - 互相调用的 fun（seekToPercent → goToChapter / jumpToPlainOffset；handleChromeAction →
 *   openTts）作为函数参数传入，避免循环依赖；
 * - 顶层 helper（[nowIso] / [unitIndexForOffset]）与本文件同包，直接调用。
 *
 * 这些是普通 `fun`（非 @Composable），由 ReaderScreen 在组合期调用并读取最新状态值，
 * 与原内联 fun 行为一致。
 */

/** 打开 TTS：从当前正文（或跨会话续读位置）开始。R1：API 33+ 运行时申请通知权限。 */
@Suppress("LongParameterList")
internal fun openTts(
    contentText: String,
    epubBook: EpubBook?,
    isMarkdown: Boolean,
    ttsResumeChapter: Int,
    chapterIndex: Int,
    ttsResumeOffset: Int,
    tts: TtsEngineHost,
    bookTitle: String,
    currentChapterTitle: String,
    context: Context,
    onShowTtsChange: (Boolean) -> Unit,
    showNotice: (String) -> Unit,
    pagerEngineOn: Boolean = false,
    pagedSource: PagedChapterSource? = null,
) {
    if (contentText.isBlank()) {
        showNotice("当前没有可朗读的文字。")
        return
    }
    // R1：API 33+ 运行时申请通知权限，否则锁屏媒体控制无法显示
    (context as? Activity)?.let { act ->
        com.creationreadingassistant.ui.components.NotificationPermission.requestIfNeeded(
            act, com.creationreadingassistant.ui.components.NotificationPermission.REQUEST_TTS,
        )
    }
    val resumeAt = ttsResumeStartAt(
        isEpub = epubBook != null,
        isMarkdown = isMarkdown,
        ttsResumeChapter = ttsResumeChapter,
        chapterIndex = chapterIndex,
        ttsResumeOffset = ttsResumeOffset,
    )
    // EPUB 分页：续读偏移持久化是 source 章内口径，播放文本是 display 空间（净化投影
    // 活跃时），播放前逆映射回 display；其余路径恒等。
    val playStart = if (epubBook != null && pagerEngineOn) {
        ttsSourceLocalToDisplayLocal(pagedSource, chapterIndex, resumeAt)
    } else {
        resumeAt
    }
    val result0 = tts.play(contentText, bookTitle, currentChapterTitle.ifBlank { "正文" }, playStart)
    AppLog.debug("TtsOpen", "play#1 engine=${tts.engineId} avail=${tts.availability} result=$result0")
    // 系统语音引擎不可用（ROM 缺失/损坏 TTS 引擎）时自动改用神经语音重试一次：
    // 否则错误态够不到 TTS 栏里的引擎选择器，形成死锁。EDGE 失败会自行回退 SYSTEM
    // 并给出明确错误；两端都坏时不循环（策略 EDGE -> null）。
    var result: TtsPlayResult = result0
    if (result0 is TtsPlayResult.Rejected &&
        TtsNoticePolicy.shouldAutoSwitchOnReject(tts.availability)
    ) {
        val switched = TtsNoticePolicy.autoSwitchEngineOnUnavailable(tts.engineId)
        if (switched != null) {
            tts.switchEngine(switched)
            result = tts.play(contentText, bookTitle, currentChapterTitle.ifBlank { "正文" }, playStart)
            AppLog.debug("TtsOpen", "play#2 engine=$switched avail=${tts.availability} result=$result")
            if (result is TtsPlayResult.Accepted) {
                showNotice("系统语音引擎不可用，已改用${switched.displayLabel}朗读（需联网）。")
            }
        }
    }
    if (result is TtsPlayResult.Rejected) {
        // 提示策略：被动 init failure 静默；用户主动打开听书且引擎不可用时恰好提示一次
        //（snackbar 带去设置）并进程内 reinitialize；未就绪显示可解释原因；主动错误不被吞。
        when (val decision = TtsNoticePolicy.onOpenTtsRejected(tts.availability)) {
            TtsNoticeAction.None -> Unit
            TtsNoticeAction.ReinitializeWithNotice -> {
                tts.notifyUnavailable()
                tts.reinitialize()
            }
            is TtsNoticeAction.ShowMessage -> showNotice(decision.message)
        }
        return
    }
    onShowTtsChange(true)
}

/**
 * 处理顶栏/底栏 chrome 动作。逐字搬运自 ReaderScreen 的 when 分支。
 *
 * B2：原对本地副本 var 的 setter 写入改走 [ReaderAction]（VM 唯一真源）：
 * onShowTtsChange → SetShowTts；onControlsVisibleChange → ToggleControls(visible)；
 * onSheetChange → OpenSheet；onSearchQueryChange → SetSearchQuery；
 * onSelectedTextChange("") → ClearSelection（工具条以 selectedText 非空门控，
 * 附带重置偏移/showColorRow 无可观测差异）。
 */
@Suppress("LongParameterList")
internal fun handleChromeAction(
    action: ReaderChromeAction,
    showTts: Boolean,
    tts: TtsEngineHost,
    autoPagingActiveState: MutableState<Boolean>,
    autoPagingSupported: Boolean,
    onAction: (ReaderAction) -> Unit,
    onBack: () -> Unit,
    openTts: () -> Unit,
    showNotice: (String) -> Unit,
    onReturnToReading: () -> Unit = {},
) {
    when (action) {
        ReaderChromeAction.Back -> onBack()
        ReaderChromeAction.ReturnToReading -> onReturnToReading()
        ReaderChromeAction.ToggleTts -> {
            if (showTts) {
                tts.stop()
                onAction(ReaderAction.SetShowTts(false))
            } else {
                autoPagingActiveState.value = false
                openTts()
            }
        }
        is ReaderChromeAction.OpenSheet -> {
            // 打开搜索面板不得清空查询：query/results/current hit 由 BookSearchSession
            // 按书保留，只有用户清空查询或切书才会清空。
            onAction(ReaderAction.OpenSheet(action.sheet))
        }
        ReaderChromeAction.ToggleAutoPaging -> {
            AppLog.debug("AutoPagingDebug", "toggle: active=${autoPagingActiveState.value} supported=$autoPagingSupported")
            if (autoPagingActiveState.value) {
                autoPagingActiveState.value = false
                onAction(ReaderAction.ToggleControls(true))
            } else if (!autoPagingSupported) {
                AppLog.debug("AutoPagingDebug", "branch: unsupported")
                showNotice("左右翻页模式需先开启新分页引擎")
            } else {
                AppLog.debug("AutoPagingDebug", "branch: activate")
                tts.stop()
                onAction(ReaderAction.SetShowTts(false))
                onAction(ReaderAction.ClearSelection)
                autoPagingActiveState.value = true
                onAction(ReaderAction.ToggleControls(false))
            }
        }
    }
}

/**
 * B3：笔记对话框保存动作（原 ReaderScreen onSave lambda 体逐字搬运）。
 * 先快照选区/正文/locator 再派发 SaveNote，随后清空 noteBody / 关对话框 / 清选区，
 * 与原顺序一致（ClearSelection 附带重置两偏移/showColorRow，工具条以 selectedText
 * 非空门控，无可观测差异）。
 */
@Suppress("LongParameterList")
internal fun saveReaderNote(
    onAction: (ReaderAction) -> Unit,
    bid: String,
    selectedText: String,
    noteBody: String,
    currentChapterTitle: String,
    progressPercent: Float,
    locatorJson: String?,
) {
    // 同高亮保存：先快照局部变量，避免下面同步清空后读到空串
    val snapshotText = selectedText
    val snapshotBody = noteBody
    val snapshotLocator = locatorJson
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
    onAction(ReaderAction.SetNoteBody(""))
    onAction(ReaderAction.SetNoteOpen(false))
    onAction(ReaderAction.ClearSelection)
}

/**
 * B3 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 主函数抽出的
 * 「导航 / chrome 动作闭包组」。原局部 fun 均为普通闭包（非 @Composable），每次重组重建；
 * 改为顶层 builder 后仍每次重组重建闭包，捕获的只读值与 State-holder 引用与原语义一致。
 * 互相调用的 fun（seekToPercent → goToChapter / jumpToPlainOffset；handleChromeAction →
 * openTts）在 builder 内部直接引用，与原局部 fun 互调关系一致。
 */
@Suppress("LongParameterList")
internal data class ReaderNavActions(
    val computeLocatorJson: () -> String?,
    val showNotice: (String) -> Unit,
    val goToChapter: (Int) -> Unit,
    val syncPagedChapter: (Int) -> Unit,
    val jumpToPlainOffset: (Int) -> Unit,
    val jumpToMarkdownOffset: (Int) -> Unit,
    val persistCurrentProgress: () -> Unit,
    val seekToPercent: (Float) -> Unit,
    val seekToChapterPercent: (Float) -> Unit,
    val openTts: () -> Unit,
    val handleChromeAction: (ReaderChromeAction) -> Unit,
)

@Suppress("LongParameterList")
internal fun buildReaderNavActions(
    epubBook: EpubBook?,
    markdownDocument: ReaderDocument?,
    pagerEngineOn: Boolean,
    chapterStartOffsets: List<Int>,
    pagedJumpRequest: MutableState<Int?>,
    chapterIndexState: MutableIntState,
    tts: TtsEngineHost,
    onAction: (ReaderAction) -> Unit,
    onBack: () -> Unit,
    bid: String,
    selectedGlobalOffset: Int,
    selectedText: String,
    selectedRangeStart: Int,
    scope: CoroutineScope,
    snackbarHost: SnackbarHostState,
    readingUnits: List<ReadingUnit>,
    plainListState: LazyListState,
    loadedBook: ReaderLoadedBook?,
    error: String?,
    pendingInitialPosition: Boolean,
    pagedAbsOffsetState: MutableIntState,
    pagedSource: PagedChapterSource?,
    chapterIndex: Int,
    epubListState: LazyListState,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
    chapterBlocks: List<DocBlock>,
    bookIndex: BookIndex?,
    visiblePlainOffset: Int,
    txtStreamingDocument: PlainTextDocument?,
    plainContent: String,
    txtChapters: List<com.creationreadingassistant.feature.reader.doc.DocChapter>,
    txtChapterIndex: Int,
    contentText: String,
    ttsResumeChapterState: MutableIntState,
    ttsResumeOffsetState: MutableIntState,
    bookTitle: String,
    currentChapterTitle: String,
    context: Context,
    showTts: Boolean,
    autoPagingActiveState: MutableState<Boolean>,
    autoPagingSupported: Boolean,
    epubDocument: EpubDocument? = null,
    /** TTS 朗读文本：EPUB 分页投影活跃时是 display 文本，其余与 [contentText] 相同。 */
    ttsContentText: String = contentText,
    /** R2-J1.4：返回阅读处回调（临时查阅 LIFO 返回一层）。 */
    onReturnToReading: () -> Unit = {},
): ReaderNavActions {
    val showNoticeFn: (String) -> Unit = { msg ->
        scope.launch { snackbarHost.showSnackbar(msg) }
    }
    val goToChapterFn: (Int) -> Unit = { i ->
        goToChapter(
            i, epubBook, markdownDocument, pagerEngineOn, chapterStartOffsets,
            pagedJumpRequest, chapterIndexState, tts, onAction, bid,
        )
    }
    // 翻页跨章的被动章号同步：只更新 ViewModel 章状态（章号/TTS/章节块加载），
    // 不写 jumpRequest、不写章首进度，避免把已停在正确页位（上一章末页）的
    // 阅读器拽回章首。
    val syncPagedChapterFn: (Int) -> Unit = { i ->
        goToChapter(
            i, epubBook, markdownDocument, pagerEngineOn, chapterStartOffsets,
            pagedJumpRequest, chapterIndexState, tts, onAction, bid,
            jumpToStart = false,
        )
    }
    val jumpToPlainOffsetFn: (Int) -> Unit = { offset ->
        jumpToPlainOffset(
            offset, pagerEngineOn, readingUnits, scope, plainListState, pagedJumpRequest,
        )
    }
    val jumpToEpubOffsetFn: (Int) -> Unit = { absoluteOffset ->
        val itemIndex = blockGlobalOffsets
            .indexOfLast { it in 0..absoluteOffset }
            .coerceAtLeast(0)
        scope.launch { epubListState.scrollToItem(itemIndex) }
    }
    val jumpToMarkdownOffsetFn: (Int) -> Unit = { absoluteOffset ->
        val document = markdownDocument
        if (document != null) {
            val (targetChapter, inChapter) = document.locate(absoluteOffset)
            if (targetChapter != chapterIndex) {
                // The chapter load is asynchronous. Keep this branch conservative rather than
                // applying a stale LazyList index to the previous chapter.
                goToChapterFn(targetChapter)
            } else {
                val markdownBlock = chapterBlocks.filterIsInstance<DocBlock.Markdown>().firstOrNull()
                val targetUnit = markdownBlock?.let {
                    markdownRenderUnitIndexForChapterOffset(
                        chapter = it.chapter,
                        inChapter = inChapter,
                        chapterBase = chapterBase,
                        blocksGlobal = (document as? com.creationreadingassistant.feature.reader.doc.MarkdownDocument)
                            ?.isWholeDocumentParse == true,
                    )
                }
                if (targetUnit != null) scope.launch { epubListState.scrollToItem(targetUnit) }
            }
        }
    }
    val openTtsFn: () -> Unit = {
        openTts(
            ttsContentText, epubBook, markdownDocument != null,
            ttsResumeChapterState.intValue, chapterIndex, ttsResumeOffsetState.intValue,
            tts, bookTitle, currentChapterTitle, context,
            onShowTtsChange = { onAction(ReaderAction.SetShowTts(it)) }, showNotice = showNoticeFn,
            pagerEngineOn = pagerEngineOn, pagedSource = pagedSource,
        )
    }
    return ReaderNavActions(
        computeLocatorJson = {
            computeLocatorJson(
                epubBook, selectedGlobalOffset, chapterStartOffsets, selectedText, selectedRangeStart,
            )
        },
        showNotice = showNoticeFn,
        goToChapter = goToChapterFn,
        syncPagedChapter = syncPagedChapterFn,
        jumpToPlainOffset = jumpToPlainOffsetFn,
        jumpToMarkdownOffset = jumpToMarkdownOffsetFn,
        persistCurrentProgress = {
            val progressSnapshot = currentReaderProgressSnapshot(
                pagedAbsOffsetState = pagedAbsOffsetState,
                chapterIndexState = chapterIndexState,
            )
            persistCurrentProgress(
                bid, loadedBook, error, pendingInitialPosition, epubBook, pagerEngineOn,
                progressSnapshot.pagedAbsOffset, pagedSource, progressSnapshot.chapterIndex,
                epubListState, blockGlobalOffsets,
                chapterBase, chapterBlocks, bookIndex, chapterStartOffsets, visiblePlainOffset,
                markdownDocument, txtStreamingDocument, plainContent, epubDocument, onAction,
            )
        },
        seekToPercent = { p ->
            seekToPercent(
                p, epubBook, markdownDocument, pagerEngineOn, bookIndex, txtStreamingDocument, plainContent,
                pagedJumpRequest, goToChapterFn, jumpToPlainOffsetFn, jumpToMarkdownOffsetFn, epubDocument,
            )
        },
        seekToChapterPercent = { p ->
            seekToChapterPercent(
                p, epubBook, markdownDocument, chapterStartOffsets, chapterIndex, contentText, pagerEngineOn,
                txtChapters, txtChapterIndex, plainContent, pagedJumpRequest, jumpToPlainOffsetFn,
                jumpToEpubOffsetFn, jumpToMarkdownOffsetFn,
            )
        },
        openTts = openTtsFn,
        handleChromeAction = { action ->
            handleChromeAction(
                action, showTts, tts, autoPagingActiveState, autoPagingSupported,
                onAction = onAction,
                onBack = onBack, openTts = openTtsFn, showNotice = showNoticeFn,
            )
        },
    )
}
