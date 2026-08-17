package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.creationreadingassistant.feature.reader.pager.AutoPagingTiming
import com.creationreadingassistant.feature.reader.pager.PagedReaderHost
import com.creationreadingassistant.feature.reader.pager.ReaderPagePosition

/**
 * ReaderContentHost 分支 1：试验分页引擎（pagerEngineOn && pagedSource != null）。
 * TXT 与 EPUB 共用排版/手势/高亮链路。
 *
 * 从 [ReaderContentHost] 的 `when` 分支纯结构搬运而来，参数构造逐字保留。
 */
@Suppress("LongMethod")
@Composable
internal fun ReaderContentHostPagedBranch(
    state: ReaderContentHostState,
    callbacks: ReaderContentHostCallbacks,
) {
    val settings = state.settings
    val selectionState = state.selection
    val paging = state.paging
    val s = state.source
    val pagedSource = requireNotNull(paging.pagedSource) { "分页分支前置守卫已保证 pagedSource 非空" }
    // 试验分页引擎：TXT 与 EPUB 共用排版/手势/高亮链路。
    // EPUB 首版只排文字块；图片分页与四档动画仍按 P4 后续刀次推进。
    PagedReaderHost(
        source = pagedSource,
        fontSizeSp = settings.readerSettings.fontSize,
        lineHeightMultiplier = settings.readerSettings.lineHeight,
        paragraphSpacing = settings.readerSettings.paragraphSpacing,
        pageMarginDp = settings.readerSettings.pageMargin,
        fontWeightBold = settings.readerSettings.fontWeightBold,
        customFontPath = settings.readerSettings.customFontPath,
        showReaderInfo = settings.readerSettings.showReaderInfo,
        chineseTypography = settings.readerSettings.chineseTypography,
        tapZoneMode = settings.readerSettings.tapZoneMode,
        pageTurnEffect = settings.readerSettings.pageTurnEffect,
        textColor = settings.paperFg,
        pageBackground = settings.paper.bg,
        headerLeft = settings.readerSettings.headerLeft,
        headerRight = settings.readerSettings.headerRight,
        footerLeft = settings.readerSettings.footerLeft,
        footerRight = settings.readerSettings.footerRight,
        bookName = s.bookTitle,
        initialOffset = when {
            paging.pagedAbsOffset >= 0 -> paging.pagedAbsOffset
            s.epubBook != null ->
                s.chapterStartOffsets.getOrElse(s.chapterIndex) { 0 } + paging.savedEpubOffsetInChapter
            paging.visiblePlainOffset > 0 -> paging.visiblePlainOffset
            paging.savedPlainOffset > 0 -> paging.savedPlainOffset
            else -> (paging.savedPlainPercent.coerceIn(0f, 100f) / 100f * (s.txtStreamingDocument?.totalChars ?: s.plainContent.length)).toInt()
        },
        jumpRequest = paging.pagedJumpRequest,
        externalTurnRequest = paging.pagedHardwareTurnRequest,
        onPositionChanged = { off, pct ->
            val chapterToGo = if (s.epubBook != null || s.markdownDocument != null) {
                val ci = paging.pagedSource?.chapterIndexFor(off) ?: s.chapterIndex
                if (ci != s.chapterIndex) ci else null
            } else null
            callbacks.onPagedPositionChanged(off, pct, chapterToGo)
        },
        onToggleControls = { callbacks.onToggleControls() },
        onPageIndexChanged = { b, ci, pi, pc, start, end, pct ->
            paging.pageIndexManager.update(
                ReaderPagePosition(
                    bookId = b,
                    chapterIndex = ci,
                    pageIndex = pi,
                    pageCount = pc,
                    absStart = start,
                    absEnd = end,
                    percent = pct,
                ),
            )
        },
        onGesturePageTurn = { callbacks.onHideControls() },
        store = paging.pageIndexStore,
        bookId = s.bid,
        contentKey = when {
            s.epubBook != null -> s.bid
            s.markdownDocument != null -> "${s.bid}|md"
            else -> "${s.bid}|toc=${s.txtTocProfileKey}"
        },
        ttsRangeAbs = if (s.showTts && s.tts.status != "idle") {
            if (s.epubBook != null) {
                val base = s.chapterStartOffsets.getOrElse(s.chapterIndex) { 0 }
                (base + s.tts.currentSentenceRange.first) to
                    (base + s.tts.currentSentenceRange.second)
            } else if (s.markdownDocument != null) {
                // Markdown：tts.currentSentenceRange 是章内规范文本偏移，转全书偏移
                val base = paging.pagedSource?.chapterStartAbs(s.chapterIndex) ?: 0
                (base + s.tts.currentSentenceRange.first) to
                    (base + s.tts.currentSentenceRange.second)
            } else if (s.txtStreamingDocument != null) {
                // 流式 TXT：tts.currentSentenceRange 是章内偏移，需加章起始偏移转全书偏移
                val base = s.chapterStartOffsets.getOrElse(s.txtChapterIndex) { 0 }
                (base + s.tts.currentSentenceRange.first) to
                    (base + s.tts.currentSentenceRange.second)
            } else {
                s.tts.currentSentenceRange
            }
        } else {
            null
        },
        onSelect = { text, absStart ->
            if (s.epubBook != null) {
                callbacks.onSelect(text, absStart, -1)
            } else {
                callbacks.onSelect(text, -1, absStart)
            }
        },
        selectionCleared = selectionState.selectedText.isBlank(),
        selectionColor = settings.paper.accent.copy(alpha = 0.30f),
        ttsHighlightColor = settings.sentenceHighlightBg,
        searchHitRangeAbs = s.searchHitRangeAbs,
        searchHighlightColor = settings.searchHighlightBg,
        persistentHighlights = remember(s.highlights) {
            s.highlights.mapNotNull { h ->
                val start = parseLocatorOffset(h.locator_json) ?: return@mapNotNull null
                val len = h.text.length
                if (len <= 0) return@mapNotNull null
                (start until start + len) to
                    settings.paper.highlight(h.color ?: "yellow")
            }
        },
        autoPageIntervalMillis = if (paging.autoPagingActive && !paging.autoPagingPaused) {
            AutoPagingTiming.pageIntervalMillis(settings.readerSettings.autoPageSpeed)
        } else {
            null
        },
        onAutoPagingFinished = { callbacks.onAutoPagingFinished() },
        onStopAutoPaging = callbacks.onStopAutoPaging,
    )
}
