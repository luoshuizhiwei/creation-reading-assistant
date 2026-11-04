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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * S07-F3：非分页 / 旧 EPUB 滚动模式「整块选择」的源半开区间。
 *
 * 坐标链（从构建来源追到保存，不靠变量名推断）：
 * `blockGlobalOffsets[i]` ← [computeBlockGlobalOffsets]（`ReaderTextIndex.kt:18`）
 *   ← `LegacyOffsetCodec.blockOffsets(blocks.map { text.length }, chapterBase)`
 *   = chapterBase + Σ(前面每个文本块 length + 1)；图片块记 -1。
 * `ReaderDocument` 核心不变式（`ReaderDocument.kt:47`）：
 *   text(i) == blocks(i).filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }
 *
 * 因此第 i 个文本块的半开源区间 = [gOff, gOff + block.text.length)。
 * 终点**不含**块间 "\n"，恰好落在文本末字符之后，与 `epubChapterEndOffset`
 * （`ReaderScrollPosition.kt:63`）既有口径 start + text.length 完全一致。
 *
 * 这个偏移空间是「旧版混合量纲」（章起点来自 ZIP 字节估算），但它**正是**
 * `LocatorBuilder.forEpub` / `LocatorCodec` 保存与回读所用的同一空间，
 * 因此起点可靠、可与既有历史高亮对齐。禁止把它改写成真实字符偏移。
 */

/** 文本块的源半开区间；坐标未知（gOff < 0）、空文本或空白文本返回 null。 */
internal fun epubTextBlockSourceRange(block: DocBlock.Text, gOff: Int): IntRange? {
    if (gOff < 0) return null
    if (block.text.isBlank()) return null
    val start = gOff
    val end = gOff + block.text.length
    return if (end > start) start until end else null
}

/**
 * 任意块 → 源区间。图片等非文本块**恒为 null**：不得生成伪选区。
 * 即使调用方误传非负 gOff 也不生成。
 */
internal fun epubBlockSourceRange(block: DocBlock, gOff: Int): IntRange? = when (block) {
    is DocBlock.Text -> epubTextBlockSourceRange(block, gOff)
    is DocBlock.Image -> null
    is DocBlock.Markdown -> null
}

/**
 * S07-F3.1 任务 C：非分页 / 旧 EPUB **持久高亮投影**的纯函数。
 *
 * 输入：文本块半开区间 `[blockStart, blockStart + blockLength)`（`epubTextBlockSourceRange`
 * 的同源产物）与已保存高亮的 `(absStart, absLength)`。输出：**该块内的局部**半开区间。
 *
 * 硬约束：
 * - **没有文本字段** —— 不可能做 selectedText 搜索 / indexOf / 首次匹配；
 * - 重复段落靠各自 `gOff` 区分，绝不串块；
 * - 跨两个文本块的高亮，两块各自渲染**自己的**交集（本函数一次只算一块）；
 * - `gOff < 0`（坐标未知）、空文本、负长度、零长度、非法坐标一律 null；
 * - 图片 / Markdown 等非文本块在本层之前就被 `epubBlockSourceRange` 挡掉（返回 null），
 *   这里对 `blockLength <= 0` 再兜一层。
 *
 * 坐标空间：与 `epubTextBlockSourceRange` 完全相同（LegacyOffsetCodec 章起点 +
 * Σ(前块 length + 1)），也是 `LocatorBuilder.forEpub` / `LocatorCodec` 的保存与回读空间。
 */
internal fun epubBlockHighlightLocalRange(
    blockStart: Int,
    blockLength: Int,
    absStart: Int,
    absLength: Int,
): IntRange? {
    if (blockStart < 0) return null
    if (blockLength <= 0) return null
    if (absStart < 0) return null
    if (absLength <= 0) return null
    val blockEnd = blockStart.toLong() + blockLength.toLong()
    val absEnd = absStart.toLong() + absLength.toLong()
    val start = maxOf(blockStart.toLong(), absStart.toLong())
    val end = minOf(blockEnd, absEnd)
    if (end <= start) return null
    // 相对块起点的局部区间；转换后必然落在 [0, blockLength]，Long 已排除溢出
    return (start - blockStart).toInt() until (end - blockStart).toInt()
}

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
            tapActionPreset = settings.readerSettings.tapActionPreset,
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
            onSelectBlock = { text, off -> callbacks.onSelect(text, off, -1, -1) },
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
                            // S07-F3.1 任务 C：非分页 EPUB 的持久高亮重新渲染。
                            // 纯坐标求交（[epubBlockHighlightLocalRange]），无文本搜索；
                            // 不可渲染（坐标未知 / 不在本块 / 非法）只跳过，不删不改数据库。
                            //
                            // 在 [buildSentenceHighlighted] 的**结果之上**叠一层，不改那个函数
                            // （它属于 tts 包，不在本片允许清单内），因此 TTS 句高亮与搜索命中
                            // 的既有行为完全不动。
                            val blockHighlights = remember(gOff, block.text.length, s.highlights) {
                                s.highlights.mapNotNull { h ->
                                    val start = parseLocatorOffset(h.locator_json) ?: return@mapNotNull null
                                    val length = highlightSourceLength(h.payload, h.text.length)
                                    if (length <= 0) return@mapNotNull null
                                    val local = epubBlockHighlightLocalRange(
                                        blockStart = gOff,
                                        blockLength = block.text.length,
                                        absStart = start,
                                        absLength = length,
                                    ) ?: return@mapNotNull null
                                    local to (h.color ?: "yellow")
                                }
                            }
                            val text = remember(ann, blockHighlights, settings.paper) {
                                if (blockHighlights.isEmpty()) {
                                    ann
                                } else {
                                    AnnotatedString.Builder(ann).apply {
                                        blockHighlights.forEach { (local, colorKey) ->
                                            // 复用**纸张高亮色**，不复用 searchHighlightBg：
                                            // 否则持久高亮会与搜索命中语义互相冒充。
                                            addStyle(
                                                SpanStyle(background = settings.paper.highlight(colorKey)),
                                                local.first,
                                                (local.last + 1).coerceAtMost(ann.length),
                                            )
                                        }
                                    }.toAnnotatedString()
                                }
                            }
                            Text(
                                text = text,
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
                                    // S07-F3：整块选择提交**同一映射结果**——
                                    // 文字、起点、源终点三者同源。此前 sourceEnd 恒为 -1，
                                    // 使高亮/笔记只能靠文本长度猜终点。
                                    .clickable {
                                        val range = epubTextBlockSourceRange(block, gOff)
                                        if (range == null) {
                                            // 坐标不可靠：不得发出可保存的伪坐标，
                                            // 轻点回落到中央点击（唤出/隐藏控制栏）。
                                            callbacks.onToggleControls()
                                        } else {
                                            callbacks.onSelect(block.text, range.first, -1, range.last + 1)
                                        }
                                    },
                            )
                        }

                        is DocBlock.Image -> {
                            val imageFile = remember(block.path) { java.io.File(block.path) }
                            // StrictMode-safe：lastModified() 放到 IO 线程，避免组合期主线程 I/O。
                            var imageModTs by remember(block.path) { mutableStateOf(0L) }
                            LaunchedEffect(block.path) {
                                imageModTs = withContext(Dispatchers.IO) {
                                    runCatching { imageFile.lastModified() }.getOrDefault(0L)
                                }
                            }
                            val imageRequest = rememberViewportImageRequest(
                                data = imageFile,
                                cacheKey = "reader:${block.path}:$imageModTs",
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
