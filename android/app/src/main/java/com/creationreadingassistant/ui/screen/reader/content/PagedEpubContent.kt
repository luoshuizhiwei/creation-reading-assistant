package com.creationreadingassistant.ui.screen.reader.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
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
import com.creationreadingassistant.feature.reader.pager.ReaderTapZoneGeometry
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
    REVEAL,
}

internal fun legacyEpubPageTurnEffect(value: String): LegacyEpubPageTurnEffect = when (value) {
    "fade" -> LegacyEpubPageTurnEffect.FADE
    "slide" -> LegacyEpubPageTurnEffect.SLIDE
    "cover" -> LegacyEpubPageTurnEffect.COVER
    "reveal" -> LegacyEpubPageTurnEffect.REVEAL
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
    tapActionPreset: String = com.creationreadingassistant.feature.reader.pager.TapActionPreset.STANDARD,
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
    val contentRevealProgress = remember { Animatable(1f) }
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
            contentRevealProgress.snapTo(1f)
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
            contentRevealProgress.snapTo(1f)
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

            LegacyEpubPageTurnEffect.REVEAL -> {
                // 旧 EPUB 宿主只保留一份章节 Composition；用从上向下的裁切显露新章，
                // 不退化为 none/fade/cover，也不同时常驻新旧两章而放大大章峰值内存。
                contentAlpha.snapTo(1f)
                contentOffset.snapTo(0f)
                contentRevealProgress.snapTo(0f)
                contentRevealProgress.animateTo(1f, tween(durationMillis = MotionTokens.Fast))
            }

            LegacyEpubPageTurnEffect.NONE -> Unit
        }
    }
    val haptic = rememberHaptic(reducedMotion)
    val onPrevHaptic: () -> Unit = { haptic(HapticFeedbackType.TextHandleMove); onPrev() }
    val onNextHaptic: () -> Unit = { haptic(HapticFeedbackType.TextHandleMove); onNext() }
    // 父级 tap observation：左/右（及 five-zone 上/下）翻页分区点击优先消费；
    // 中央空白等未消费轻点统一唤出/隐藏菜单，不覆盖正文选择（Text 点击仍走选区）。
    // 父级 tap observation 已下移到 LegacyEpubTapArbiter（S09-B-F2）：它只负责兜住
    // overlay 漏下来的轻点，挂在 overlay 的包装层上等价、且父子结构可被测试渲染。
    Box(Modifier.fillMaxSize()) {
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
                }
                .then(
                    if (configuredEffect == LegacyEpubPageTurnEffect.REVEAL) {
                        Modifier.drawWithContent {
                            clipRect(bottom = size.height * contentRevealProgress.value) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
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
        // 点击翻页：整层仲裁（父级中央兜底 + 分区 overlay）抽成 LegacyEpubTapArbiter，
        // 生产与 androidTest 渲染同一份父子结构，测试才能覆盖"边界漏点"这类跨层缺陷
        // （S09-B-F1 P2-2 / S09-B-F2）。
        LegacyEpubTapArbiter(
            tapZoneMode = tapZoneMode,
            tapActionPreset = tapActionPreset,
            canPrev = canPrev,
            canNext = canNext,
            onPrev = onPrevHaptic,
            onNext = onNextHaptic,
            onToggleControls = onToggleControls,
            iconTint = paperFg.copy(alpha = 0.3f),
        )
    }
}

/**
 * legacy EPUB 分页的**完整点击仲裁层**：父级中央兜底 + [LegacyEpubTapZoneOverlay] 分区热区。
 *
 * 为什么要有这一层（S09-B-F2）：修复前父级 [readerCenterTapToToggle] 挂在
 * [PagedEpubView] 最外层的 Box 上，测试只能单独渲染 overlay —— 于是"边界点击完全静默"
 * 这条结论并没有真正被测到，而它恰好是错的：overlay 里不可用的分区用
 * `clickable(enabled = false)`，**禁用的 clickable 不消费轻点**，那一下会漏到父层观察器，
 * 被当成"点了中央空白"，结果第一页点左区 / 最后一页点右区会错误地切换控制栏。
 *
 * 现在父子两层一起构成生产入口：[PagedEpubView] 只调用本函数，androidTest 渲染的也是本函数，
 * 所以父级仲裁一旦重新失效（漏点、双触发），跨层断言会直接变红。
 *
 * 事件流（三层，子先于父）：
 * 1. 可用边缘分区 → 子层 clickable 消费 → 只回调 [onPrev] / [onNext]，父层看到 down 已消费 → 跳过；
 * 2. 不可用边缘分区 → 子层静默 clickable 消费 → 什么都不回调，父层同样跳过（修复点）；
 * 3. 中央区 → 子层 clickable 消费 → 只回调一次 [onToggleControls]。
 */
@Composable
internal fun LegacyEpubTapArbiter(
    tapZoneMode: String,
    tapActionPreset: String,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .readerCenterTapToToggle(onCenterTap = onToggleControls),
    ) {
        LegacyEpubTapZoneOverlay(
            tapZoneMode = tapZoneMode,
            tapActionPreset = tapActionPreset,
            canPrev = canPrev,
            canNext = canNext,
            onPrev = onPrev,
            onNext = onNext,
            onToggleControls = onToggleControls,
            iconTint = iconTint,
        )
    }
}

/**
 * 分区热区的边界无障碍标签（S09-B-F3）。
 *
 * 必须按"这个分区实际映射到哪个动作"来选，不能按物理左右 —— left_hand / both_next 下
 * 左右含义是反的。
 */
internal const val LEGACY_EPUB_BOUNDARY_FIRST = "已到第一章"
internal const val LEGACY_EPUB_BOUNDARY_LAST = "已到最后一章"

/** 分区热区容器的 testTag：让测试能定位到生产的热区本身，而不是另造一个假节点。 */
internal const val LEGACY_EPUB_ZONE_LEFT = "legacy-epub-zone-left"
internal const val LEGACY_EPUB_ZONE_RIGHT = "legacy-epub-zone-right"
internal const val LEGACY_EPUB_ZONE_TOP = "legacy-epub-zone-top"
internal const val LEGACY_EPUB_ZONE_BOTTOM = "legacy-epub-zone-bottom"

/**
 * 给分区热区挂点击：可用时正常分发，不可用时**触摸照消费、语义只报边界**。
 *
 * 触摸层与无障碍层是分开的两件事，修复前两处都不对：
 * - `clickable(enabled = false)`：不消费轻点 → 漏给父层 [readerCenterTapToToggle]，
 *   翻页翻不动还顺手切换控制栏（S09-B-F2 的缺陷），因此不得恢复；
 * - 空的 enabled `clickable(onClick = {})`：确实消费了，但会暴露一个"没有名称、
 *   看起来可激活、激活后什么都不发生"的匿名节点（S09-B-F3 的缺陷）。
 *
 * 现在不可用的分区：
 * - 触摸层用 `pointerInput + detectTapGestures(onTap = {})` —— 只消费不分发。
 *   经 1.7.6 产物字节码核对，`detectTapGestures` 在 `awaitFirstDown` 之后立即
 *   `PointerInputChange.consume()`，父层观察器于是走 `down.isConsumed` 分支返回，
 *   与 clickable 用的是同一条消费路径；
 * - 无障碍层用 `semantics { contentDescription = …; disabled() }` —— 有名称的 disabled
 *   边界节点，**不挂任何 OnClick**，TalkBack / Switch Access / 键盘都不会把它当成可激活项。
 */
@Composable
private fun Modifier.legacyEpubTapZone(
    available: Boolean,
    boundaryDescription: String,
    tag: String,
    onTurn: () -> Unit,
): Modifier = if (available) {
    this.testTag(tag).clickable { onTurn() }
} else {
    this.testTag(tag)
        .semantics {
            contentDescription = boundaryDescription
            disabled()
        }
        .pointerInput(Unit) {
            detectTapGestures(onTap = { /* 消费这一次轻点，但不触发任何动作 */ })
        }
}

/**
 * legacy EPUB 分页的点击翻页热区 overlay（仲裁层的子层）。
 *
 * 从 [PagedEpubView] 的内联块**逐行原样提取**，派发语义未改。目的是让 androidTest 能渲染
 * 生产实现本身、按 [ReaderTapZoneGeometry] 推出的真实坐标 `performTouchInput { click(...) }`，
 * 并断言 [onPrev] / [onNext] / [onToggleControls] 的实际调用次数 —— 提取前这段埋在
 * `PagedEpubView` 深处，测试点不到它，"点击区域有测试"实际只剩预览卡的尺寸断言。
 *
 * 分区占比取自 [ReaderTapZoneGeometry]，与自研分页引擎、设置预览卡同一事实源；
 * 本组件不得再写自己的比例。
 *
 * **生产入口是 [LegacyEpubTapArbiter]**（它负责父级中央兜底）。本函数虽然可见，
 * 但除历史兼容外不要单独使用：单独渲染 overlay 会漏掉父子两层的仲裁关系，
 * S09-B-F2 那个"边界轻点穿透到父层切换控制栏"的缺陷正是这样被遮住的。
 */
@Composable
internal fun LegacyEpubTapZoneOverlay(
    tapZoneMode: String,
    tapActionPreset: String,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    val (leftWeight, centerWeight, rightWeight) = ReaderTapZoneGeometry.horizontalWeights
    val (topWeight, middleWeight, bottomWeight) = ReaderTapZoneGeometry.verticalWeights
    val isLeftAdvance = tapActionPreset == com.creationreadingassistant.feature.reader.pager.TapActionPreset.LEFT_HAND ||
        tapActionPreset == com.creationreadingassistant.feature.reader.pager.TapActionPreset.BOTH_NEXT
    val isRightAdvance = tapActionPreset != com.creationreadingassistant.feature.reader.pager.TapActionPreset.LEFT_HAND

    val leftAction = if (isLeftAdvance) onNext else onPrev
    val leftCan = if (isLeftAdvance) canNext else canPrev
    val leftIcon = if (isLeftAdvance) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp
    val leftDesc = if (isLeftAdvance) "下一章" else "上一章"

    val rightAction = if (isRightAdvance) onNext else onPrev
    val rightCan = if (isRightAdvance) canNext else canPrev
    val rightIcon = if (isRightAdvance) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp
    val rightDesc = if (isRightAdvance) "下一章" else "上一章"

    // 边界提示文案必须跟随"该分区实际映射到哪个动作"，不能按物理左右硬编码：
    // left_hand 下左区是下一章、右区是上一章；both_next 下两侧都是下一章。
    val leftBoundaryDescription =
        if (isLeftAdvance) LEGACY_EPUB_BOUNDARY_LAST else LEGACY_EPUB_BOUNDARY_FIRST
    val rightBoundaryDescription =
        if (isRightAdvance) LEGACY_EPUB_BOUNDARY_LAST else LEGACY_EPUB_BOUNDARY_FIRST

    if (tapZoneMode == "five-zone") {
        Column(modifier.fillMaxSize()) {
            Box(
                Modifier.weight(topWeight).fillMaxWidth()
                    .legacyEpubTapZone(canPrev, LEGACY_EPUB_BOUNDARY_FIRST, LEGACY_EPUB_ZONE_TOP) { onPrev() },
                contentAlignment = Alignment.TopCenter,
            ) {
                if (canPrev) Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上一章", tint = iconTint)
            }
            Row(Modifier.weight(middleWeight).fillMaxWidth()) {
                Box(
                    Modifier.weight(leftWeight).fillMaxSize()
                        .legacyEpubTapZone(leftCan, leftBoundaryDescription, LEGACY_EPUB_ZONE_LEFT) { leftAction() },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (leftCan) Icon(leftIcon, contentDescription = leftDesc, tint = iconTint)
                }
                Box(
                    Modifier
                        .weight(centerWeight)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onToggleControls() },
                )
                Box(
                    Modifier.weight(rightWeight).fillMaxSize()
                        .legacyEpubTapZone(rightCan, rightBoundaryDescription, LEGACY_EPUB_ZONE_RIGHT) { rightAction() },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (rightCan) Icon(rightIcon, contentDescription = rightDesc, tint = iconTint)
                }
            }
            Box(
                Modifier.weight(bottomWeight).fillMaxWidth()
                    .legacyEpubTapZone(canNext, LEGACY_EPUB_BOUNDARY_LAST, LEGACY_EPUB_ZONE_BOTTOM) { onNext() },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (canNext) Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下一章", tint = iconTint)
            }
        }
    } else {
        Row(modifier.fillMaxSize()) {
            Box(
                Modifier.weight(leftWeight).fillMaxSize()
                    .legacyEpubTapZone(leftCan, leftBoundaryDescription, LEGACY_EPUB_ZONE_LEFT) { leftAction() },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (leftCan) Icon(leftIcon, contentDescription = leftDesc, tint = iconTint)
            }
            Box(
                Modifier
                    .weight(centerWeight)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onToggleControls() },
            )
            Box(
                Modifier.weight(rightWeight).fillMaxSize()
                    .legacyEpubTapZone(rightCan, rightBoundaryDescription, LEGACY_EPUB_ZONE_RIGHT) { rightAction() },
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (rightCan) Icon(rightIcon, contentDescription = rightDesc, tint = iconTint)
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
    var viewingImagePath by remember { mutableStateOf<String?>(null) }
    var viewingFootnote by remember { mutableStateOf<Pair<String, String>?>(null) }
    val paperPalette = com.creationreadingassistant.ui.theme.LocalReaderPaperPalette.current

    if (viewingImagePath != null) {
        EpubImageViewerDialog(
            imagePath = viewingImagePath!!,
            onDismiss = { viewingImagePath = null },
        )
    }

    if (viewingFootnote != null) {
        EpubFootnoteDialog(
            footnoteLabel = viewingFootnote!!.first,
            footnoteContent = viewingFootnote!!.second,
            paperPalette = paperPalette,
            onDismiss = { viewingFootnote = null },
        )
    }

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
                        modifier = Modifier.fillMaxWidth().clickable {
                            // 检测是否包含注脚标记 [1], [注1], 【1】, ① 等
                            val fnRegex = Regex("(\\[(?:注?\\d+|\\d+)\\]|【\\d+】|[①-⑩])")
                            val match = fnRegex.find(block.text)
                            if (match != null) {
                                val fnRef = match.value
                                // 提取该章节中的后续可能解释说明
                                val explanation = blocks.filterIsInstance<DocBlock.Text>()
                                    .map { it.text }
                                    .firstOrNull { it.startsWith(fnRef) || it.contains("注：") || it.contains("注释：") }
                                    ?: "当前段落标记注脚：$fnRef"
                                viewingFootnote = fnRef to explanation
                            } else {
                                onSelectBlock(block.text, gOff)
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
                        contentDescription = "点击查看大图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewingImagePath = block.path },
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
