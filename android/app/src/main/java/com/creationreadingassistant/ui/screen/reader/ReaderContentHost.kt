@file:OptIn(ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.UnitTextLoader
import com.creationreadingassistant.feature.reader.doc.UnitTextState
import com.creationreadingassistant.feature.reader.pager.AutoPagingTiming
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.feature.reader.pager.PagedReaderHost
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.ReaderPageIndexManager
import com.creationreadingassistant.feature.reader.pager.ReaderPagePosition
import com.creationreadingassistant.ui.components.rememberViewportImageRequest
import com.creationreadingassistant.ui.screen.reader.content.PagedEpubView
import com.creationreadingassistant.ui.screen.reader.parseLocatorOffset
import com.creationreadingassistant.ui.screen.reader.RenderMarkdownChapter
import com.creationreadingassistant.ui.screen.reader.tts.TtsController
import com.creationreadingassistant.ui.screen.reader.tts.buildSentenceHighlighted
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.ShimmerBlock
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.data.local.entity.HighlightEntity
import kotlinx.coroutines.launch

/**
 * 阅读设置与纸张调色板（B1 状态袋分组：设置域）。
 */
internal data class ReaderContentSettings(
    val readerSettings: ReaderSettings,
    val paper: ReaderPaperPalette,
    val paperFg: Color,
    val sentenceHighlightBg: Color,
    /** 统一搜索命中高亮底色（P1-B）：TXT/EPUB/Markdown 滚动与分页共用，随纸自适应。 */
    val searchHighlightBg: Color,
)

/**
 * 当前选区相关状态（B1 状态袋分组：选区域）。
 */
internal data class ReaderSelectionState(
    val selectedText: String,
    val selectedGlobalOffset: Int,
    val selectedRangeStart: Int,
)

/**
 * 分页引擎 / 自动翻页 / 阅读位置恢复（B1 状态袋分组：分页域）。
 */
internal data class ReaderPagingState(
    val pagerEngineOn: Boolean,
    val pagedSource: PagedChapterSource?,
    val pagedAbsOffset: Int,
    val pagedPercent: Float,
    val pendingInitialPosition: Boolean,
    val savedEpubOffsetInChapter: Int,
    val visiblePlainOffset: Int,
    val savedPlainOffset: Int,
    val savedPlainPercent: Float,
    val autoPagingActive: Boolean,
    val autoPagingPaused: Boolean,
    val pagedJumpRequest: MutableState<Int?>,
    val pagedHardwareTurnRequest: MutableState<Int?>,
    val pageIndexStore: PageIndexStore,
    val pageIndexManager: ReaderPageIndexManager,
)

/**
 * 文档内容 / 章节渲染 / TTS 朗读状态（B1 状态袋分组：内容源域）。
 */
internal data class ReaderContentSourceState(
    val bid: String,
    /** TXT 目录身份（TxtTocProfile.key）：规则变化后分页索引缓存键随之失效。 */
    val txtTocProfileKey: String,
    val bookTitle: String,
    val chapterStartOffsets: List<Int>,
    val txtStreamingDocument: PlainTextDocument?,
    val plainContent: String,
    val epubBook: EpubBook?,
    val markdownDocument: ReaderDocument?,
    val chapterIndex: Int,
    val txtChapterIndex: Int,
    val isChapterLoading: Boolean,
    val chapterBlocks: List<DocBlock>,
    val epubListState: LazyListState,
    val plainListState: LazyListState,
    val chapterFade: Animatable<Float, AnimationVector1D>,
    val blockGlobalOffsets: List<Int>,
    val chapterBase: Int,
    val ttsSentenceRangeInChapter: Pair<Int, Int>?,
    val focusBlockIndex: Int?,
    val epubBringRequester: BringIntoViewRequester,
    val readingUnits: List<ReadingUnit>,
    val isTxt: Boolean,
    val showTts: Boolean,
    val tts: TtsController,
    val highlights: List<HighlightEntity>,
    /** 搜索命中临时高亮（全书字符区间，含首不含尾）；null = 无当前命中。 */
    val searchHitRangeAbs: Pair<Int, Int>?,
    /**
     * 搜索滚动聚焦的一次性请求（P1 修复）：带书/章/渲染单元/命中身份，
     * 仅当前书/章匹配时消费并滚动，消费后由 [ReaderContentHostCallbacks]
     * 的 onSearchScrollFocusRequestConsumed ack 清除；手动切章/切书的旧请求
     * 身份不匹配即丢弃，不得劫持导航。
     */
    val searchScrollFocusRequest: SearchScrollFocusRequest?,
)

/**
 * 正文内容宿主所需的只读展示数据。从 [com.creationreadingassistant.ui.screen.ReaderScreen] 主函数
 * 局部状态中提取，仅承载「读」数据；任何对主函数可变状态的写入都通过
 * [ReaderContentHostCallbacks] 回调上抛。
 *
 * B1 状态袋瘦身：原 48 个平铺字段按域分组为 4 个子对象（[settings] 设置+纸张 /
 * [selection] 选区 / [paging] 分页与位置 / [source] 内容源与渲染），纯结构搬运。
 *
 * 分页引擎、EPUB/Markdown/TXT 分支共用同一份 state；分支选择由 [ReaderPagingState.pagerEngineOn] /
 * [ReaderContentSourceState.epubBook] / [ReaderContentSourceState.markdownDocument] 决定，
 * 与 ReaderScreen 主函数 `when` 块的分支条件 1:1 对应。
 */
internal data class ReaderContentHostState(
    val settings: ReaderContentSettings,
    val selection: ReaderSelectionState,
    val paging: ReaderPagingState,
    val source: ReaderContentSourceState,
)

/**
 * 正文内容宿主所需的回调集合。每个回调对应一次「主函数可变状态写入」或「主函数局部副作用」，
 * 复杂逻辑（goToChapter / showNotice / persistCurrentProgress）仍留在 ReaderScreen 主函数中定义，
 * 本层只暴露触发入口，保持为纯渲染层。
 */
internal data class ReaderContentHostCallbacks(
    val onPagedPositionChanged: (offset: Int, percent: Float, chapterToGo: Int?) -> Unit,
    val onToggleControls: () -> Unit,
    val onHideControls: () -> Unit,
    val onSelect: (text: String, globalOffset: Int, rangeStart: Int) -> Unit,
    val onAutoPagingFinished: () -> Unit,
    val onStopAutoPaging: () -> Unit,
    val onGoToChapter: (chapterIndex: Int) -> Unit,
    /** ack：ReaderContentHost 消费（或丢弃）搜索滚动聚焦请求后清除持久状态。 */
    val onSearchScrollFocusRequestConsumed: () -> Unit,
)

/**
 * 正文内容宿主：按文档类型/分页模式分发到 PagedReaderHost / PagedEpubView / LazyColumn / BasicTextField。
 *
 * 从 ReaderScreen.kt 拆出，纯结构搬运，不改渲染语义。所有分支共用同一个 [BoxScope]-less
 * Composable（由 ReaderScreen 的 `Box` 容器直接调用），不自行创建 Scaffold 或 Box。
 *
 * 分支与原 `when` 块 1:1 对应：
 * 1. pagerEngineOn → PagedReaderHost（试验分页引擎，TXT/EPUB 共用）
 * 2. epubBook != null → PagedEpubView（翻页）或 LazyColumn（滚动）
 * 3. markdownDocument != null → RenderMarkdownChapter
 * 4. else → BasicTextField（TXT 流式/纯文本）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderContentHost(
    state: ReaderContentHostState,
    callbacks: ReaderContentHostCallbacks,
) {
    // B1 状态袋瘦身：按域解构分组对象，字段访问与原平铺语义 1:1。
    val settings = state.settings
    val selectionState = state.selection
    val paging = state.paging
    val s = state.source
    // 自定义正文字体（空 = 系统字体），非分页路径共用。
    val readerFontFamily = rememberReaderFontFamily(settings.readerSettings.customFontPath)
    when {
        paging.pagerEngineOn && paging.pagedSource != null -> {
            // 试验分页引擎：TXT 与 EPUB 共用排版/手势/高亮链路。
            // EPUB 首版只排文字块；图片分页与四档动画仍按 P4 后续刀次推进。
            PagedReaderHost(
                source = paging.pagedSource,
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

        s.epubBook != null -> {
            val book = s.epubBook!!
            val chapter = book.chapters.getOrNull(s.chapterIndex)
            // P1/S2：搜索滚动聚焦 —— 在 EPUB 分支顶层统一消费（legacy 翻页 PagedEpubView 与
            // 滚动 LazyColumn 共用同一 LazyListState）：身份匹配且渲染块就绪后 scrollToItem
            // 定位命中块；加载中保留 pending（不得提前 ack 丢请求）；跨书/跨章 stale 丢弃并
            // ack，永不滚动。TTS 句焦点（focusBlockIndex）不受影响，仍走 bringIntoView 链路。
            val pendingSearchFocusIndex = s.searchScrollFocusRequest
                ?.takeIf { it.bookKey == s.bid && it.chapterIndex == s.chapterIndex }
                ?.renderUnitIndex
            LaunchedEffect(
                s.chapterIndex,
                s.searchScrollFocusRequest,
                s.isChapterLoading,
                s.chapterBlocks,
            ) {
                val request = s.searchScrollFocusRequest ?: return@LaunchedEffect
                when (
                    val outcome = SearchScrollFocusConsumer(request).consume(
                        currentBookKey = s.bid,
                        currentChapterIndex = s.chapterIndex,
                        renderUnitsReady = !s.isChapterLoading && s.chapterBlocks.isNotEmpty(),
                    )
                ) {
                    is SearchScrollFocusOutcome.Scroll -> {
                        val unit = outcome.renderUnitIndex
                        if (unit != null && unit in s.chapterBlocks.indices) {
                            s.epubListState.scrollToItem(unit)
                        }
                        callbacks.onSearchScrollFocusRequestConsumed()
                    }

                    SearchScrollFocusOutcome.Discarded -> callbacks.onSearchScrollFocusRequestConsumed()
                    // 章节加载中：保留 pending，就绪后由本 effect（key 含 isChapterLoading/chapterBlocks）重试
                    SearchScrollFocusOutcome.Pending -> Unit
                }
            }
            if (s.isChapterLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "正在加载章节" },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = settings.paper.accent)
                }
            } else if (chapter == null || s.chapterBlocks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("本章暂无可读内容。", color = settings.paperFg)
                }
            } else if (settings.readerSettings.readerMode == "paged") {
                PagedEpubView(
                    blocks = s.chapterBlocks,
                    fontSize = settings.readerSettings.fontSize,
                    lineHeight = settings.readerSettings.lineHeight,
                    fontWeightBold = settings.readerSettings.fontWeightBold,
                    pageMargin = settings.readerSettings.pageMargin,
                    paperFg = settings.paperFg,
                    tapZoneMode = settings.readerSettings.tapZoneMode,
                    pageTurnEffect = settings.readerSettings.pageTurnEffect,
                    chapterIndex = s.chapterIndex,
                    canPrev = s.chapterIndex > 0,
                    canNext = s.chapterIndex < book.chapters.lastIndex,
                    onPrev = {
                        callbacks.onHideControls()
                        callbacks.onGoToChapter(s.chapterIndex - 1)
                    },
                    onNext = {
                        callbacks.onHideControls()
                        callbacks.onGoToChapter(s.chapterIndex + 1)
                    },
                    onToggleControls = { callbacks.onToggleControls() },
                    onSelectBlock = { text, off -> callbacks.onSelect(text, off, -1) },
                    blockGlobalOffsets = s.blockGlobalOffsets,
                    chapterBase = s.chapterBase,
                    ttsSentenceRangeInChapter = s.ttsSentenceRangeInChapter,
                    focusBlockIndex = s.focusBlockIndex,
                    sentenceHighlightBg = settings.sentenceHighlightBg,
                    searchHighlightBg = settings.searchHighlightBg,
                    bringRequester = s.epubBringRequester,
                    fontFamily = readerFontFamily,
                    searchHitRangeAbs = s.searchHitRangeAbs,
                    listState = s.epubListState,
                )
            } else {
                // 中央点击统一唤出/隐藏（父级 tap observation，不 consume、不覆盖子项）：
                // 正文段落点击（选区）优先，空白/间隔/图片等未消费轻点落到本观察器。
                Box(
                    Modifier
                        .fillMaxSize()
                        .readerCenterTapToToggle(onCenterTap = { callbacks.onToggleControls() }),
                ) {
                    LazyColumn(
                        state = s.epubListState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = settings.readerSettings.pageMargin.dp, vertical = settings.readerSettings.pageMargin.dp).graphicsLayer { alpha = s.chapterFade.value },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = s.chapterBlocks,
                            key = { index, block ->
                                when (block) {
                                    is DocBlock.Text -> "text-$index"
                                    is DocBlock.Image -> "image-$index-${block.path}"
                                    is DocBlock.Markdown -> "markdown-$index"
                                }
                            },
                        ) { idx, block ->
                            when (block) {
                                is DocBlock.Text -> {
                                    val gOff = s.blockGlobalOffsets.getOrElse(idx) { -1 }
                                    val ann = remember(
                                        block.text, gOff, s.chapterBase, s.ttsSentenceRangeInChapter,
                                        settings.sentenceHighlightBg, settings.searchHighlightBg, s.searchHitRangeAbs,
                                    ) {
                                        buildSentenceHighlighted(
                                            block.text, gOff, s.chapterBase, s.ttsSentenceRangeInChapter, settings.sentenceHighlightBg,
                                            searchRangeAbs = s.searchHitRangeAbs,
                                            searchBg = settings.searchHighlightBg,
                                        )
                                    }
                                    Text(
                                        text = ann,
                                        style = TextStyle(
                                            textAlign = TextAlign.Justify,
                                            lineHeight = (settings.readerSettings.fontSize * settings.readerSettings.lineHeight).sp,
                                            textIndent = if (block.isHeading) TextIndent.None else TextIndent(firstLine = (settings.readerSettings.fontSize * 2).sp),
                                            fontFamily = readerFontFamily,
                                        ),
                                        fontSize = settings.readerSettings.fontSize.sp,
                                        fontWeight = if (block.isHeading || settings.readerSettings.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                                        color = settings.paperFg,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (idx == s.focusBlockIndex) {
                                                    Modifier.bringIntoViewRequester(s.epubBringRequester)
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clickable {
                                                callbacks.onSelect(block.text, gOff, -1)
                                            },
                                    )
                                }

                                is DocBlock.Image -> {
                                    val imageFile = remember(block.path) { java.io.File(block.path) }
                                    val imageRequest = rememberViewportImageRequest(
                                        data = imageFile,
                                        cacheKey = "reader:${block.path}:${imageFile.lastModified()}",
                                    )
                                    AsyncImage(
                                        model = imageRequest,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                                    )
                                }

                                is DocBlock.Markdown -> {
                                    // EPUB 路径不会出现 Markdown 块
                                }
                            }
                        }
                    }
                }
            }
        }

        s.markdownDocument != null -> {
            val markdownBlock = s.chapterBlocks.filterIsInstance<DocBlock.Markdown>().firstOrNull()
            if (s.isChapterLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "正在加载章节" },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = settings.paper.accent)
                }
            } else if (markdownBlock == null) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("本章暂无可读内容。", color = settings.paperFg)
                }
            } else {
                // 搜索命中 → 当前滚动渲染文本单元上的精确局部高亮：
                // 全书绝对区间经 parser canonicalRange（原文 → 可见文本 offset mapping）换算；
                // 跨书 / 跨章 / 与本章 span 无交集 / mapping 不可用（无渲染文本单元）一律不高亮。
                // 坐标空间：小文件整本解析的块 canonicalRange 为全书全局（base=0）；
                // 流式逐章解析为章内局部（base=章节全书起点）。
                val chapterLength = s.markdownDocument?.chapters?.getOrNull(s.chapterIndex)?.charCount ?: 0
                val blocksAreGlobal = (s.markdownDocument as? MarkdownDocument)?.isWholeDocumentParse == true
                val searchHits = s.searchHitRangeAbs?.let { abs ->
                    markdownScrollSearchHits(
                        chapter = markdownBlock.chapter,
                        chapterBase = s.chapterBase,
                        chapterLength = chapterLength,
                        blockGlobalBase = if (blocksAreGlobal) 0 else s.chapterBase,
                        target = SearchHitTarget(
                            bookKey = s.bid,
                            chapterIndex = s.chapterIndex,
                            absoluteRange = abs.first until abs.second,
                            resultIndex = 0,
                        ),
                        currentBookKey = s.bid,
                        currentChapterIndex = s.chapterIndex,
                    )
                }.orEmpty()
                // P1-A：渲染单元与导航索引共用同一展平顺序（不能把整章当一个 LazyColumn
                // item 做“精确定位”）。
                // P1 修复：滚动定位不再用裸 Int?（旧 LaunchedEffect(chapterIndex,
                // focusBlockIndex) 会在手动切章后用旧 K scrollToItem 劫持导航），
                // 改为消费带身份的一次性 SearchScrollFocusRequest —— 仅当前书/章匹配时
                // 滚动，消费/丢弃后 ack 清除。
                val markdownUnits = remember(markdownBlock.chapter) {
                    MarkdownRenderModel.flatten(markdownBlock.chapter)
                }
                LaunchedEffect(s.chapterIndex, s.searchScrollFocusRequest, markdownUnits) {
                    val request = s.searchScrollFocusRequest ?: return@LaunchedEffect
                    when (
                        val outcome = SearchScrollFocusConsumer(request).consume(
                            currentBookKey = s.bid,
                            currentChapterIndex = s.chapterIndex,
                            renderUnitsReady = markdownUnits.isNotEmpty(),
                        )
                    ) {
                        is SearchScrollFocusOutcome.Scroll -> {
                            val unit = outcome.renderUnitIndex
                            if (unit != null && unit in markdownUnits.indices) {
                                s.epubListState.scrollToItem(unit)
                            }
                            callbacks.onSearchScrollFocusRequestConsumed()
                        }

                        SearchScrollFocusOutcome.Discarded -> callbacks.onSearchScrollFocusRequestConsumed()
                        // 渲染单元未就绪：保留 pending，不得提前 ack
                        SearchScrollFocusOutcome.Pending -> Unit
                    }
                }
                // 中央点击统一唤出/隐藏（父级 tap observation，不 consume）：Markdown
                // 滚动分支没有可点击子项，未消费轻点全部落到本观察器。
                Box(
                    Modifier
                        .fillMaxSize()
                        .readerCenterTapToToggle(onCenterTap = { callbacks.onToggleControls() }),
                ) {
                    LazyColumn(
                        state = s.epubListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = settings.readerSettings.pageMargin.dp, vertical = settings.readerSettings.pageMargin.dp),
                    ) {
                        itemsIndexed(
                            items = markdownUnits,
                            key = { _, unit -> MarkdownUnitVisualPolicy.unitKey(unit) },
                        ) { index, unit ->
                            // 还原递归渲染间距：顶层块间 4.dp，容器内 0.dp
                            val topGap = if (index > 0 && unit.topBlockIndex != markdownUnits[index - 1].topBlockIndex) {
                                4.dp
                            } else {
                                0.dp
                            }
                            RenderMarkdownUnit(
                                unit = unit,
                                canonicalText = markdownBlock.chapter.canonicalText,
                                fontSize = settings.readerSettings.fontSize,
                                lineHeight = settings.readerSettings.lineHeight,
                                paperFg = settings.paperFg,
                                fontFamily = readerFontFamily,
                                searchHits = searchHits,
                                searchHighlightBg = settings.searchHighlightBg,
                                topGap = topGap,
                            )
                        }
                    }
                }
            }
        }

        else -> {
            val txtDoc = s.txtStreamingDocument
            val reducedMotion = rememberReducedMotion()
            // 加载占位高度：按 3 行正文估算（与正文行高同源），sp → dp 需经当前密度
            val loadingPlaceholderHeight = with(LocalDensity.current) {
                (settings.readerSettings.fontSize * settings.readerSettings.lineHeight * 3).sp.toDp()
            }
            // 流式 TXT：组合路径只读 UnitTextLoader 的可观察状态，阻塞 readUnit
            // 在 loader 的 IO dispatcher 上执行；文档变化时 switchDocument 作废旧请求。
            val unitTextLoader = remember { UnitTextLoader() }
            val scope = rememberCoroutineScope()
            LaunchedEffect(txtDoc) {
                if (txtDoc != null) unitTextLoader.switchDocument(txtDoc)
            }
            // 单次轻点手势仲裁门：readOnly 文本域观察器按下时 claim，父级抬起时
            // consumeClaimIfAny；正文轻点走子路径，父层只负责空白/边距，避免双切换。
            val tapGate = remember { ReaderTapToggleGate() }
            // 中央点击统一唤出/隐藏（父级 tap observation，不 consume）：TXT 滚动分支的
            // 重试点击（UnitTextFailedItem）优先消费；readOnly 文本域内的中央短按由
            // 子观察器（readerTextFieldCenterTapToToggle）负责，父层保留空白/边距路径。
            Box(
                Modifier
                    .fillMaxSize()
                    .readerCenterTapToToggle(
                        onCenterTap = { callbacks.onToggleControls() },
                        gate = tapGate,
                    ),
            ) {
                LazyColumn(
                    state = s.plainListState,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = s.chapterFade.value },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = settings.readerSettings.pageMargin.dp,
                        vertical = settings.readerSettings.pageMargin.dp,
                    ),
                ) {
                    itemsIndexed(
                        items = s.readingUnits,
                        key = { _, unit -> unit.unitIndex },
                    ) { _, unit ->
                        if (txtDoc != null) {
                            // 异步加载：Loading/Loaded/Failed 状态可观察；快速切章/换书时
                            // 旧请求被 LaunchedEffect 取消 + switchDocument 代次守卫丢弃。
                            val unitState = unitTextLoader.stateFor(txtDoc, unit.unitIndex)
                            LaunchedEffect(txtDoc, unit) {
                                unitTextLoader.load(txtDoc, unit.unitIndex) { txtDoc.readUnit(unit) }
                            }
                            DisposableEffect(txtDoc, unit.unitIndex) {
                                onDispose { unitTextLoader.release(txtDoc, unit.unitIndex) }
                            }
                            when (val st = unitState.value) {
                                is UnitTextState.Loaded -> ReaderUnitTextItem(
                                    unitText = st.text,
                                    unit = unit,
                                    state = state,
                                    callbacks = callbacks,
                                    fontFamily = readerFontFamily,
                                    searchHitRangeAbs = s.searchHitRangeAbs,
                                    tapGate = tapGate,
                                )

                                UnitTextState.Loading -> {
                                    // 最小高度占位 + 加载语义：避免内容完成时零高→全高的跳动，
                                    // 高度按 3 行正文估算（与正文行高同源）。
                                    ShimmerBlock(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = settings.readerSettings.pageMargin.dp,
                                                vertical = 6.dp,
                                            )
                                            .height(loadingPlaceholderHeight)
                                            .semantics { contentDescription = "正在加载正文" },
                                        reducedMotion = reducedMotion,
                                    )
                                }

                                is UnitTextState.Failed -> UnitTextFailedItem(
                                    cause = st.cause,
                                    paperFg = settings.paperFg,
                                    // 仅显式点击触发重试；不靠滚走/重组碰运气
                                    onRetry = {
                                        scope.launch {
                                            unitTextLoader.retry(txtDoc, unit.unitIndex) {
                                                txtDoc.readUnit(unit)
                                            }
                                        }
                                    },
                                )
                            }
                        } else {
                            // 小文件 TXT：全文已在内存，组合阶段直接切片（无 I/O）
                            val unitText = remember(unit.unitIndex, s.plainContent) {
                                s.plainContent.substring(
                                    unit.charStart,
                                    (unit.charStart + unit.charCount).coerceAtMost(s.plainContent.length),
                                )
                            }
                            ReaderUnitTextItem(
                                unitText = unitText,
                                unit = unit,
                                state = state,
                                callbacks = callbacks,
                                fontFamily = readerFontFamily,
                                searchHitRangeAbs = s.searchHitRangeAbs,
                                tapGate = tapGate,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 读取单元失败项：原因摘要 + 显式「点击重试」，带可访问语义。 */
@Composable
private fun UnitTextFailedItem(
    cause: Throwable,
    paperFg: Color,
    onRetry: () -> Unit,
) {
    val summary = cause.message?.takeIf { it.isNotBlank() } ?: "未知错误"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRetry)
            .semantics { contentDescription = "内容加载失败：$summary，点击重试" }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "内容加载失败：$summary",
            style = MaterialTheme.typography.bodySmall,
            color = paperFg,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "点击重试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 滚动 TXT 单条 ReadingUnit 的纯渲染（TTS 高亮 + 选区 + BasicTextField）。
 * 文本由调用方提供（流式模式来自 [UnitTextLoader]，小文件模式来自内存切片）。
 */
@Composable
private fun ReaderUnitTextItem(
    unitText: String,
    unit: ReadingUnit,
    state: ReaderContentHostState,
    callbacks: ReaderContentHostCallbacks,
    fontFamily: FontFamily,
    searchHitRangeAbs: Pair<Int, Int>?,
    tapGate: ReaderTapToggleGate,
) {
    val settings = state.settings
    val selectionState = state.selection
    val paging = state.paging
    val s = state.source
    val ttsRange = if (s.showTts && s.tts.status != "idle" && s.isTxt) {
        if (s.txtStreamingDocument != null) {
            // 流式 TXT：contentText 是窗口，sentenceRange 是窗口内偏移
            // 加 visiblePlainOffset 转全书偏移后再与 unit.charStart 做差
            val base = paging.visiblePlainOffset
            (base + s.tts.currentSentenceRange.first) to (base + s.tts.currentSentenceRange.second)
        } else {
            s.tts.currentSentenceRange
        }
    } else {
        0 to 0
    }
    val localStart = (ttsRange.first - unit.charStart).coerceIn(0, unitText.length)
    val localEnd = (ttsRange.second - unit.charStart).coerceIn(0, unitText.length)
    val searchLocal = searchHitRangeAbs?.let {
        intersectTextRange(unit.charStart, unitText.length, it.first until it.second)
    }
    val annotated = remember(unitText, localStart, localEnd, settings.sentenceHighlightBg, settings.searchHighlightBg, searchLocal) {
        if (localEnd > localStart || searchLocal != null) {
            AnnotatedString.Builder(unitText).apply {
                if (localEnd > localStart) {
                    addStyle(
                        SpanStyle(background = settings.sentenceHighlightBg),
                        localStart,
                        localEnd,
                    )
                }
                if (searchLocal != null) {
                    addStyle(
                        SpanStyle(background = settings.searchHighlightBg),
                        searchLocal.first,
                        searchLocal.last + 1,
                    )
                }
            }.toAnnotatedString()
        } else {
            AnnotatedString(unitText)
        }
    }
    var selection by remember(unit.charStart) { mutableStateOf(TextRange.Zero) }
    BasicTextField(
        value = TextFieldValue(annotatedString = annotated, selection = selection),
        onValueChange = { value ->
            selection = value.selection
            if (value.selection != TextRange.Zero && value.selection.length > 0) {
                callbacks.onSelect(
                    unitText.substring(value.selection.start, value.selection.end),
                    -1,
                    unit.charStart + value.selection.start,
                )
            } else if (selectionState.selectedRangeStart in unit.charStart until (unit.charStart + unitText.length)) {
                callbacks.onSelect("", -1, -1)
            }
        },
        readOnly = true,
        textStyle = TextStyle(
            fontSize = settings.readerSettings.fontSize.sp,
            lineHeight = (settings.readerSettings.fontSize * settings.readerSettings.lineHeight).sp,
            color = settings.paperFg,
            textAlign = TextAlign.Justify,
            fontWeight = if (settings.readerSettings.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = fontFamily,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .readerTextFieldCenterTapToToggle(
                onCenterTap = { callbacks.onToggleControls() },
                gate = tapGate,
            ),
    )
}
