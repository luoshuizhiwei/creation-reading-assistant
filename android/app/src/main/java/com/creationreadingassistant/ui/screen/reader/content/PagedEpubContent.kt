package com.creationreadingassistant.ui.screen.reader.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import com.creationreadingassistant.ui.screen.reader.RenderMarkdownChapter
import com.creationreadingassistant.ui.screen.reader.readerCenterTapToToggle
import com.creationreadingassistant.ui.screen.reader.tts.buildSentenceHighlighted
import com.creationreadingassistant.ui.theme.MotionTokens
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 旧 EPUB 分页宿主使用单层内容实现的轻量翻页效果。 */
internal enum class LegacyEpubPageTurnEffect {
    NONE,
    FADE,
    SLIDE,
    COVER,
}

internal fun legacyEpubPageTurnEffect(value: String): LegacyEpubPageTurnEffect = when (value) {
    "fade" -> LegacyEpubPageTurnEffect.FADE
    "slide" -> LegacyEpubPageTurnEffect.SLIDE
    "cover" -> LegacyEpubPageTurnEffect.COVER
    else -> LegacyEpubPageTurnEffect.NONE
}

/**
 * EPUB 翻页模式视图（从 ReaderScreen.kt 拆出，纯结构搬运，不改语义）。
 *
 * 单章分页渲染 + 三区/五区点击翻页。翻页效果只作用于当前章节的单层 Composition，
 * 不复制整章组件树（守住 OOM 内存纪律）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PagedEpubView(
    blocks: List<DocBlock>,
    fontSize: Float,
    lineHeight: Float,
    fontWeightBold: Boolean,
    pageMargin: Float,
    paperFg: Color,
    tapZoneMode: String,
    pageTurnEffect: String,
    chapterIndex: Int,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onSelectBlock: (String, Int) -> Unit,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
    ttsSentenceRangeInChapter: Pair<Int, Int>?,
    focusBlockIndex: Int?,
    sentenceHighlightBg: Color,
    /** 统一搜索命中高亮底色（P1-B）：分页与滚动共用，随纸自适应。 */
    searchHighlightBg: Color,
    bringRequester: BringIntoViewRequester,
    searchHitRangeAbs: Pair<Int, Int>?,
    fontFamily: FontFamily = FontFamily.Default,
    /**
     * 注入的 LazyListState（S2：搜索命中聚焦滚动定位同一状态；调用方可在消费
     * SearchScrollFocusRequest 后 scrollToItem 命中块）。默认自建，保持原行为。
     */
    listState: LazyListState = rememberLazyListState(),
) {
    val reducedMotion = rememberReducedMotion()
    val contentAlpha = remember { Animatable(1f) }
    val contentOffset = remember { Animatable(0f) }
    val configuredEffect = legacyEpubPageTurnEffect(pageTurnEffect)
    var contentWidthPx by remember { mutableStateOf(0f) }
    var previousChapter by remember { mutableStateOf(chapterIndex) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(chapterIndex, configuredEffect, reducedMotion, contentWidthPx) {
        if (firstRun) {
            firstRun = false
            previousChapter = chapterIndex
            contentAlpha.snapTo(1f)
            contentOffset.snapTo(0f)
            return@LaunchedEffect
        }
        val direction = when {
            chapterIndex > previousChapter -> 1f
            chapterIndex < previousChapter -> -1f
            else -> 0f
        }
        previousChapter = chapterIndex
        if (reducedMotion || direction == 0f || configuredEffect == LegacyEpubPageTurnEffect.NONE) {
            contentAlpha.snapTo(1f)
            contentOffset.snapTo(0f)
            return@LaunchedEffect
        }
        when (configuredEffect) {
            LegacyEpubPageTurnEffect.FADE -> {
                contentOffset.snapTo(0f)
                contentAlpha.snapTo(0.35f)
                contentAlpha.animateTo(1f, tween(durationMillis = MotionTokens.Fast))
            }

            LegacyEpubPageTurnEffect.SLIDE,
            LegacyEpubPageTurnEffect.COVER,
            -> {
                // 只移动当前章节的单层 Composition；不同时保留前后两章，避免大章 OOM。
                contentAlpha.snapTo(1f)
                contentOffset.snapTo(direction)
                contentOffset.animateTo(0f, tween(durationMillis = MotionTokens.Fast))
            }

            LegacyEpubPageTurnEffect.NONE -> Unit
        }
    }
    val haptic = rememberHaptic(reducedMotion)
    val onPrevHaptic: () -> Unit = { haptic(HapticFeedbackType.TextHandleMove); onPrev() }
    val onNextHaptic: () -> Unit = { haptic(HapticFeedbackType.TextHandleMove); onNext() }
    // 父级 tap observation：左/右（及 five-zone 上/下）翻页分区点击优先消费；
    // 中央空白等未消费轻点统一唤出/隐藏菜单，不覆盖正文选择（Text 点击仍走选区）。
    Box(
        Modifier
            .fillMaxSize()
            .readerCenterTapToToggle(onCenterTap = onToggleControls),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .padding(horizontal = pageMargin.dp, vertical = pageMargin.dp)
                .onSizeChanged { contentWidthPx = it.width.toFloat() }
                .graphicsLayer {
                    alpha = contentAlpha.value
                    translationX = contentOffset.value * contentWidthPx
                    clip = configuredEffect == LegacyEpubPageTurnEffect.COVER
                },
        ) {
            val content: @Composable () -> Unit = {
                PagedChapterContent(
                    blocks = blocks,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeightBold = fontWeightBold,
                    paperFg = paperFg,
                    onSelectBlock = onSelectBlock,
                    blockGlobalOffsets = blockGlobalOffsets,
                    chapterBase = chapterBase,
                    ttsSentenceRangeInChapter = ttsSentenceRangeInChapter,
                    focusBlockIndex = focusBlockIndex,
                    sentenceHighlightBg = sentenceHighlightBg,
                    searchHighlightBg = searchHighlightBg,
                    bringRequester = bringRequester,
                    searchHitRangeAbs = searchHitRangeAbs,
                    fontFamily = fontFamily,
                    listState = listState,
                )
            }
            // 不在这里同时保留新旧整章 Composition。旧 AnimatedContent/Crossfade
            // 会在大章节翻页时让两章文本布局同时驻留，显著放大峰值内存。
            // 翻页动效后续应基于轻量截图/页面缓存实现，而不是复制整章组件树。
            content()
        }
        // 点击翻页分区：three-zone=左右边缘；five-zone=再加上下边缘（对照 web tapZoneMode）
        if (tapZoneMode == "five-zone") {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(0.12f).fillMaxWidth().clickable(enabled = canPrev) { onPrevHaptic() },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (canPrev) Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上一章", tint = paperFg.copy(alpha = 0.3f))
                }
                Row(Modifier.weight(0.76f).fillMaxWidth()) {
                    Box(
                        Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canPrev) { onPrevHaptic() },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (canPrev) Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上一章", tint = paperFg.copy(alpha = 0.3f))
                    }
                    Box(
                        Modifier
                            .weight(0.68f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onToggleControls() },
                    )
                    Box(
                        Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canNext) { onNextHaptic() },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        if (canNext) Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下一章", tint = paperFg.copy(alpha = 0.3f))
                    }
                }
                Box(
                    Modifier.weight(0.12f).fillMaxWidth().clickable(enabled = canNext) { onNextHaptic() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (canNext) Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下一章", tint = paperFg.copy(alpha = 0.3f))
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canPrev) { onPrevHaptic() },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (canPrev) Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上一章", tint = paperFg.copy(alpha = 0.3f))
                }
                Box(
                    Modifier
                        .weight(0.68f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onToggleControls() },
                )
                Box(
                    Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canNext) { onNextHaptic() },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (canNext) Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下一章", tint = paperFg.copy(alpha = 0.3f))
                }
            }
        }
    }
}

/**
 * 章节正文内容（LazyColumn + itemsIndexed），PagedEpubView 的内容主体。
 * 从 ReaderScreen.kt 拆出，纯结构搬运，不改渲染语义。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PagedChapterContent(
    blocks: List<DocBlock>,
    fontSize: Float,
    lineHeight: Float,
    fontWeightBold: Boolean,
    paperFg: Color,
    onSelectBlock: (String, Int) -> Unit,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
    ttsSentenceRangeInChapter: Pair<Int, Int>?,
    focusBlockIndex: Int?,
    sentenceHighlightBg: Color,
    searchHighlightBg: Color,
    bringRequester: BringIntoViewRequester,
    searchHitRangeAbs: Pair<Int, Int>?,
    fontFamily: FontFamily = FontFamily.Default,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = blocks,
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
                    val gOff = blockGlobalOffsets.getOrElse(idx) { -1 }
                    val ann = remember(
                        block.text, gOff, chapterBase, ttsSentenceRangeInChapter, sentenceHighlightBg, searchHighlightBg, searchHitRangeAbs,
                    ) {
                        buildSentenceHighlighted(
                            block.text, gOff, chapterBase, ttsSentenceRangeInChapter, sentenceHighlightBg,
                            searchRangeAbs = searchHitRangeAbs,
                            searchBg = searchHighlightBg,
                        )
                    }
                    Text(
                        text = ann,
                        style = TextStyle(
                            textAlign = TextAlign.Justify,
                            lineHeight = (fontSize * lineHeight).sp,
                            textIndent = if (block.isHeading) TextIndent.None else TextIndent(firstLine = (fontSize * 2).sp),
                            fontFamily = fontFamily,
                        ),
                        fontSize = fontSize.sp,
                        fontWeight = if (block.isHeading || fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                        color = paperFg,
                        modifier = Modifier.fillMaxWidth().clickable { onSelectBlock(block.text, gOff) },
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
                        contentScale = ContentScale.FillWidth,
                    )
                }
                is DocBlock.Markdown -> {
                    RenderMarkdownChapter(
                        chapter = block.chapter,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        paperFg = paperFg,
                        blockGlobalOffset = blockGlobalOffsets.getOrElse(idx) { -1 },
                        chapterBase = chapterBase,
                        ttsSentenceRange = ttsSentenceRangeInChapter,
                        sentenceHighlightBg = sentenceHighlightBg,
                        onSelectBlock = { text, gOff -> onSelectBlock(text, gOff) },
                        fontFamily = fontFamily,
                    )
                }
            }
        }
    }
}
