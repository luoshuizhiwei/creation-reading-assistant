package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel

/**
 * ReaderContentHost 分支 3：Markdown（markdownDocument != null，滚动渲染）。
 *
 * 从 [ReaderContentHost] 的 `when` 分支纯结构搬运而来，搜索滚动聚焦 effect
 * 的 key 与消费时序逐字保留。
 */
@Suppress("LongMethod")
@Composable
internal fun ReaderContentHostMarkdownBranch(
    state: ReaderContentHostState,
    callbacks: ReaderContentHostCallbacks,
    readerFontFamily: FontFamily,
) {
    val settings = state.settings
    val s = state.source
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
