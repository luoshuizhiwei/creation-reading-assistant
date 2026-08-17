@file:OptIn(ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.ui.components.rememberViewportImageRequest
import com.creationreadingassistant.ui.screen.reader.content.PagedEpubView
import com.creationreadingassistant.ui.screen.reader.tts.buildSentenceHighlighted

/**
 * ReaderContentHost 分支 2：EPUB（epubBook != null）。
 * 翻页走 [PagedEpubView]，滚动走 LazyColumn；搜索滚动聚焦在分支顶层统一消费。
 *
 * 从 [ReaderContentHost] 的 `when` 分支纯结构搬运而来，effect key 与渲染语义逐字保留。
 */
@Suppress("LongMethod")
@Composable
internal fun ReaderContentHostEpubBranch(
    state: ReaderContentHostState,
    callbacks: ReaderContentHostCallbacks,
    readerFontFamily: FontFamily,
) {
    val settings = state.settings
    val s = state.source
    val book = requireNotNull(s.epubBook) { "EPUB 分支前置守卫已保证 epubBook 非空" }
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
