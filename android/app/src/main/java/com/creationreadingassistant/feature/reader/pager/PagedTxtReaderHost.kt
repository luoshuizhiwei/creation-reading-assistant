package com.creationreadingassistant.feature.reader.pager

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.BatteryManager
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import com.creationreadingassistant.ui.components.SizedAsyncImage
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.ReaderFontManager
import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.layout.MarkdownStyleMap
import com.creationreadingassistant.feature.reader.layout.pageAccessibleText
import com.creationreadingassistant.feature.reader.layout.android.IcuBreakOracle
import com.creationreadingassistant.feature.reader.layout.android.PaintTextRuler
import com.creationreadingassistant.feature.reader.layout.isHeading
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt
import com.creationreadingassistant.data.settings.HeaderFooterItem
import com.creationreadingassistant.ui.theme.LocalReaderPaperPalette
import com.creationreadingassistant.ui.screen.reader.searchHighlightColor

/** 页眉/页脚渲染所需的分页快照 */
private data class PageInfo(
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val progressPercent: Float = 0f,
)

/**
 * 左右翻页宿主（pagerEngineMode = on 时替换原视图；TXT 与 EPUB 共用，
 * 差别都在 [PagedChapterSource] 后面。EPUB 首版为纯文本页，图片块暂不渲染）。
 *
 * 组成：视口测量 → [LayoutConfig] → [PagedReaderController]（后台整章排版）→
 * [PageCanvas]（逐簇绘制）→ 手势（左/右点按翻页、水平滑动翻页、中央点按呼出菜单）。
 *
 * P2 边界（后续阶段补）：无翻页动画（P4）、无选区/高亮/TTS 句高亮（P3）。
 *
 * @param jumpRequest 外部跳转请求（进度条拖动、目录跳章）。消费后置回 null。
 * @param onPositionChanged 页变化回调：全书偏移 + 进度百分比。
 *        调用方拿它做进度持久化与顶栏章节名显示。
 */
@Composable
fun PagedReaderHost(
    source: PagedChapterSource,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    paragraphSpacing: Float,
    pageMarginDp: Float,
    fontWeightBold: Boolean,
    showReaderInfo: Boolean,
    /** 自定义正文字体路径（空 = 系统字体）。路径必须进排版签名，换字体后页索引失效重排。 */
    customFontPath: String = "",
    /** 「中文排版优化」开关：关掉则不做两端对齐（禁则与标点挤压保留 —— 那是正确性不是风格）。 */
    chineseTypography: Boolean,
    /** 点按分区：three-zone 左右边缘；five-zone 追加上=上一页、下=下一页。 */
    tapZoneMode: String,
    /** 翻页效果：none 直接切页；fade 柔和淡入（Crossfade 单页画布，无位移不残影）。 */
    pageTurnEffect: String,
    textColor: Color,
    initialOffset: Int,
    jumpRequest: MutableState<Int?>,
    externalTurnRequest: MutableState<Int?>,
    onPositionChanged: (absOffset: Int, percent: Float) -> Unit,
    onPageIndexChanged: (bookId: String, chapterIndex: Int, pageIndex: Int, pageCount: Int, absStart: Int, absEnd: Int, percent: Float) -> Unit = { _, _, _, _, _, _, _ -> },
    bookId: String = "",
    onToggleControls: () -> Unit,
    onGesturePageTurn: () -> Unit,
    store: PageIndexStore?,
    contentKey: String,
    modifier: Modifier = Modifier,
    /** TTS 当前句（全书偏移区间），null = 不朗读 */
    ttsRangeAbs: Pair<Int, Int>? = null,
    /** 长按选句回调：(选中文本, 全书起始偏移)。空文本 = 选区清除 */
    onSelect: (String, Int) -> Unit = { _, _ -> },
    /** 外部（工具条动作后）已清空选区的信号，host 据此撤掉选区底色 */
    selectionCleared: Boolean = true,
    /** 已存高亮（全书偏移区间 + 已带透明度的颜色），在页面上常驻绘制 */
    persistentHighlights: List<Pair<IntRange, Color>> = emptyList(),
    selectionColor: Color = LocalReaderPaperPalette.current.selectionScrim,
    ttsHighlightColor: Color = LocalReaderPaperPalette.current.ttsSentenceScrim,
    /** 搜索命中临时高亮（全书偏移区间，含首不含尾），null = 无当前命中。不落库。 */
    searchHitRangeAbs: Pair<Int, Int>? = null,
    /** 搜索命中高亮底色（与滚动路径同色约定）。 */
    searchHighlightColor: Color = searchHighlightColor(LocalReaderPaperPalette.current),
    /** 非 null 时按该间隔自动翻到下一页；到全书末页后回调并停止。 */
    autoPageIntervalMillis: Long? = null,
    onAutoPagingFinished: () -> Unit = {},
    /** 自动翻页期间用户点按（任意分区动作）时调用，用于停止自动翻页。 */
    onStopAutoPaging: () -> Unit = {},
    /** 页面图层本身透明：自动翻页揭动画叠加时用它铺底，避免两层文字互相透叠。 */
    pageBackground: Color = Color.Transparent,
    /** 页眉左侧内容条目 */
    headerLeft: HeaderFooterItem = HeaderFooterItem.CHAPTER_TITLE,
    /** 页眉右侧内容条目 */
    headerRight: HeaderFooterItem = HeaderFooterItem.NONE,
    /** 页脚左侧内容条目 */
    footerLeft: HeaderFooterItem = HeaderFooterItem.CHAPTER_TITLE,
    /** 页脚右侧内容条目 */
    footerRight: HeaderFooterItem = HeaderFooterItem.PROGRESS,
    /** 书名（用于页眉/页脚显示） */
    bookName: String = "",
) {
    val density = LocalDensity.current
    val fontPx = with(density) { fontSizeSp.sp.toPx() }

    // 绘制与测量必须用同一支 Paint 的配置，否则测出来的宽度与画出来的不一致
    val paint = remember(fontPx, fontWeightBold, textColor, customFontPath) {
        TextPaint().apply {
            isAntiAlias = true
            textSize = fontPx
            color = textColor.toArgb()
            isFakeBoldText = fontWeightBold
            if (customFontPath.isNotBlank()) {
                typeface = ReaderFontManager.loadTypeface(customFontPath) ?: Typeface.DEFAULT
            }
        }
    }
    val headingPaint = remember(paint) { TextPaint(paint).apply { isFakeBoldText = true } }
    val typefaceKey = remember(paint, customFontPath) {
        PaintTextRuler.typefaceKeyOf(paint.typeface, paint.textSize, paint.letterSpacing, customFontPath) +
            "|b=$fontWeightBold"
    }
    val ruler = remember(paint, typefaceKey) { PaintTextRuler(paint, typefaceKey) }
    val oracle = remember { IcuBreakOracle() }
    val scope = rememberCoroutineScope()

    // ── 动态数据：时间 & 电量 ──
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentTime = remember { mutableStateOf(formatCurrentTime()) }
    val batteryLevel = remember { mutableIntStateOf(getBatteryLevel(context)) }
    // 每分钟刷新时间
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentTime.value = formatCurrentTime()
        }
    }
    // 每 30 秒刷新电量
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            batteryLevel.intValue = getBatteryLevel(context)
        }
    }

    // 位置锚点：当前页首字符的全书偏移。改字号/转屏/菜单导致重排后，靠它回到同一句。
    val anchor = remember(source) { mutableIntStateOf(initialOffset) }
    // 页眉/页脚需要的分页信息（章号、页号、总页数、进度）
    val pageInfo = remember { mutableStateOf(PageInfo()) }

    Column(modifier.fillMaxSize().padding(horizontal = pageMarginDp.dp)) {
        // 页眉
        if (showReaderInfo && (headerLeft != HeaderFooterItem.NONE || headerRight != HeaderFooterItem.NONE)) {
            Row(
                Modifier.fillMaxWidth().height(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val headerColor = textColor.copy(alpha = 0.45f)
                Text(
                    resolveItemText(headerLeft, source, anchor.intValue, pageInfo.value, currentTime.value, batteryLevel.intValue, bookName),
                    fontSize = 11.sp,
                    color = headerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    resolveItemText(headerRight, source, anchor.intValue, pageInfo.value, currentTime.value, batteryLevel.intValue, bookName),
                    fontSize = 11.sp,
                    color = headerColor,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = pageMarginDp.dp)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // 平板横屏仍保持中文正文每行不超过约 42 字，超出的空间左右留白。
                val widthPx = minOf(constraints.maxWidth.toFloat(), fontPx * 42f)
                val heightPx = constraints.maxHeight.toFloat()
                val horizontalInsetDp = with(density) {
                    ((constraints.maxWidth.toFloat() - widthPx) / 2f).coerceAtLeast(0f).toDp()
                }
                val contentWidthDp = with(density) { widthPx.toDp() }

                val cfg = remember(widthPx, heightPx, fontPx, lineHeightMultiplier, paragraphSpacing, typefaceKey, chineseTypography) {
                    LayoutConfig(
                        contentWidthPx = widthPx,
                        contentHeightPx = heightPx,
                        fontSizePx = fontPx,
                        lineHeightMultiplier = lineHeightMultiplier,
                        // 设置里的段距是 0.8/1.1/1.5 的倍率档，映射到 em 值
                        paragraphSpacingEm = 0.4f * paragraphSpacing,
                        justify = chineseTypography,
                        typefaceKey = typefaceKey,
                    )
                }

                val controller = remember(source, cfg, ruler) {
                    PagedReaderController(
                        source = source,
                        cfg = cfg,
                        ruler = ruler,
                        oracle = oracle,
                        scope = scope,
                        store = store,
                        contentKey = contentKey,
                    )
                }

                DisposableEffect(controller) {
                    onDispose { controller.close() }
                }

                // 新 controller（首开 / 配置变化重建）→ 回到锚点所在句
                LaunchedEffect(controller) { controller.open(anchor.intValue) }

                // 页变化 → 上报进度；锚点只在「用户真的离开了锚点所在页」时才移动。
                // 改字号/转屏引发的重排会让页首前移，若无条件把锚点改成新页首，
                // 连续几次重排锚点就会一路往回漂（每次退小半页）。
                LaunchedEffect(controller) {
                    snapshotFlow { Triple(controller.chapterIndex, controller.pageIndex, controller.pageCount) }
                        .collect { (_, _, count) ->
                            if (count > 0 && !controller.isLayingOut) {
                                val range = controller.currentPageRangeAbs
                                if (range != null && anchor.intValue !in range) {
                                    anchor.intValue = range.first
                                }
                                onPositionChanged(controller.currentPageStartAbs, controller.progressPercent)
                                onPageIndexChanged(
                                    bookId,
                                    controller.chapterIndex,
                                    controller.pageIndex,
                                    controller.pageCount,
                                    controller.currentPageStartAbs,
                                    controller.currentPageRangeAbs?.last ?: controller.currentPageStartAbs,
                                    controller.progressPercent,
                                )
                                pageInfo.value = PageInfo(
                                    chapterIndex = controller.chapterIndex,
                                    pageIndex = controller.pageIndex,
                                    pageCount = controller.pageCount,
                                    progressPercent = controller.progressPercent,
                                )
                            }
                        }
                }

                // 外部跳转（进度条 / 目录）
                LaunchedEffect(jumpRequest.value) {
                    val j = jumpRequest.value ?: return@LaunchedEffect
                    controller.open(j)
                    jumpRequest.value = null
                }

                // 选区（章内偏移区间）。翻页/外部清除时撤掉。
                val selRange = remember { mutableStateOf<IntRange?>(null) }
                val turnRequest = remember { mutableIntStateOf(0) }
                val finishAutoPaging by rememberUpdatedState(onAutoPagingFinished)
                val stopAutoPaging by rememberUpdatedState(onStopAutoPaging)
                LaunchedEffect(selectionCleared) { if (selectionCleared) selRange.value = null }
                LaunchedEffect(externalTurnRequest.value) {
                    val direction = externalTurnRequest.value ?: return@LaunchedEffect
                    turnRequest.intValue = direction
                    externalTurnRequest.value = null
                }
                // 自动翻页揭动画进度（0..1）。remember 存活跨 effect 重启：
                // 暂停（interval→null）再恢复（interval 恢复）时从原进度续跑（冻结续跑）。
                val revealer = remember { AutoRevealProgress() }
                var revealProgress by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(controller, autoPageIntervalMillis) {
                    val interval = autoPageIntervalMillis ?: return@LaunchedEffect
                    if (interval <= 0L) return@LaunchedEffect
                    AppLog.debug("AutoPagingDebug", "effect start: interval=$interval")
                    var previousFrame = withFrameNanos { it }
                    // 手动翻页 / 跨章 / 自动提交后（页或章变化）从新页从头揭
                    var lastSeen = controller.chapterIndex to controller.pageIndex
                    var layoutCount = 0
                    while (true) {
                        val frame = withFrameNanos { it }
                        val now = controller.chapterIndex to controller.pageIndex
                        if (now != lastSeen) {
                            revealer.reset()
                            lastSeen = now
                        }
                        // 排版中不推进揭动画（当前页尚未稳定），帧时间交给 250ms 上限兜底
                        if (controller.isLayingOut) {
                            layoutCount++
                            revealProgress = revealer.value
                            continue
                        }
                        val elapsed = frame - previousFrame
                        previousFrame = frame
                        if (revealer.advance(elapsed, interval)) {
                            AppLog.debug("AutoPagingDebug", "turn: interval=$interval elapsed=$elapsed layoutSkip=$layoutCount progress=${revealer.value}")
                            layoutCount = 0
                            if (!controller.canGoNext) {
                                finishAutoPaging()
                                break
                            } else if (controller.frameAt(1) != null) {
                                turnRequest.intValue = 1
                            } else {
                                // 相邻章尚未预排完成时直接发起加载，不让自动翻页空等一整个周期。
                                controller.nextPage()
                            }
                        }
                        revealProgress = revealer.value
                    }
                }

                val page = controller.currentPage
                if (page == null) {
                    val message = controller.loadError
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (message == null) {
                                    // 与既有「正在加载章节」约定一致：加载框给明确的加载语义
                                    Modifier.semantics { contentDescription = "正在加载正文" }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (message == null) {
                            CircularProgressIndicator()
                        } else {
                            Text(message, color = textColor)
                        }
                    }
                } else {
                    // TTS 当前句：全书偏移 → 章内偏移
                    val chStart = controller.currentChapterStartAbs
                    val ttsRects = remember(page, ttsRangeAbs, chStart) {
                        val r = ttsRangeAbs ?: return@remember emptyList()
                        PageSelection.rectsForRange(page, cfg, r.first - chStart, r.second - chStart)
                    }
                    val selRects = remember(page, selRange.value) {
                        val r = selRange.value ?: return@remember emptyList()
                        PageSelection.rectsForRange(page, cfg, r.first, r.last + 1)
                    }
                    val selectionHandles = remember(page, selRange.value) {
                        val r = selRange.value ?: return@remember null
                        PageSelection.calculateSelectionHandles(page, cfg, r)
                    }
                    // 已存高亮：常驻底色。放最底层，TTS 句与活动选区盖在其上。
                    val hlUnderlays = remember(page, persistentHighlights, chStart) {
                        persistentHighlights.mapNotNull { (range, color) ->
                            val rects = PageSelection.rectsForRange(
                                page, cfg, range.first - chStart, range.last + 1 - chStart,
                            )
                            if (rects.isEmpty()) null else color to rects
                        }
                    }
                    // 搜索命中：命中所在章未加载时 page==null 不绘制；跳转完成后
                    // 本页渲染时才有 rects，天然满足「先完成跳转/加载，再显示高亮」。
                    val searchRects = remember(page, searchHitRangeAbs, chStart) {
                        val r = searchHitRangeAbs ?: return@remember emptyList()
                        PageSelection.rectsForRange(page, cfg, r.first - chStart, r.second - chStart)
                    }
                    val underlays = hlUnderlays + listOf(
                        searchHighlightColor to searchRects,
                        ttsHighlightColor to ttsRects,
                        selectionColor to selRects,
                    )
                    // 手势协程只随 controller 重启，闭包捕获的组合期快照会冻结在首次触摸前
                    // （对抗性复核反编译 compose-ui 1.7 证实：key 不变时 update 不重启协程）。
                    // 因此分区模式经 rememberUpdatedState 透传，页面/章起点在闭包内现读 controller。
                    val tapZone by rememberUpdatedState(tapZoneMode)
                    val handleHitRadiusPx = with(density) { 24.dp.toPx() }
                    val gestures = Modifier
                            .pointerInput(controller) {
                                // 选区把手拖拽：down 命中把手圆点附近才接管（消费后续事件），
                                // 其余点按/翻页手势不受影响。拖拽中实时钳制区间并同步外部待保存选区。
                                // 注意：不能用 awaitEachGesture —— 长按选句时 down 已被 tap 手势消费，
                                // awaitEachGesture 会因此永久退出；这里用常驻循环保持存活。
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val page = controller.currentPage ?: continue
                                        val r = selRange.value ?: continue
                                        if (r.isEmpty()) continue
                                        val handles = PageSelection.calculateSelectionHandles(page, cfg, r)
                                        if (!handles.isActive) continue
                                    val side = when {
                                        handles.left != null &&
                                            handleDist(down.position, Offset(handles.left!!.x, handles.left!!.y)) <= handleHitRadiusPx ->
                                            PageSelection.HandleSide.LEFT
                                        handles.right != null &&
                                            handleDist(down.position, Offset(handles.right!!.x, handles.right!!.y)) <= handleHitRadiusPx ->
                                            PageSelection.HandleSide.RIGHT
                                        else -> continue
                                    }
                                        var currentSel = r
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: break
                                            change.consume()
                                            val curPage = controller.currentPage ?: break
                                            // 拖拽中重排/跨章：放弃拖拽（翻页路径会清选区，不残留）
                                            if (curPage !== page) break
                                            currentSel = PageSelection.adjustHandle(
                                                curPage, cfg, currentSel, side, change.position.x, change.position.y,
                                            )
                                            selRange.value = currentSel
                                            val chStart = controller.currentChapterStartAbs
                                            onSelect(
                                                controller.chapterText.substring(currentSel.first, currentSel.last + 1),
                                                chStart + currentSel.first,
                                            )
                                            if (!change.pressed) break
                                        }
                                    }
                                }
                            }
                            .pointerInput(controller) {
                                detectTapGestures(
                                    onLongPress = press@{ offset ->
                                        // 长按选中一句，交给外部工具条做高亮/笔记/灵感。
                                        // 不可用外层捕获的 page/chStart：那是冻结快照，
                                        // 翻页后会按旧页几何选错句、跨章后偏移错位入库。
                                        val curPage = controller.currentPage ?: return@press
                                        val curChStart = controller.currentChapterStartAbs
                                        val ch = PageSelection.offsetAt(curPage, cfg, offset.x, offset.y)
                                        val sent = PageSelection.sentenceAround(controller.chapterText, ch)
                                        if (!sent.isEmpty()) {
                                            selRange.value = sent
                                            onSelect(
                                                controller.chapterText.substring(sent.first, sent.last + 1),
                                                curChStart + sent.first,
                                            )
                                        }
                                    },
                                    onTap = { offset ->
                                        if (selRange.value != null) {
                                            // 有选区时，任何点按先撤选区，不翻页
                                            selRange.value = null
                                            onSelect("", -1)
                                        } else {
                                            when (
                                                resolveReaderTapAction(
                                                    x = offset.x,
                                                    y = offset.y,
                                                    width = size.width.toFloat(),
                                                    height = size.height.toFloat(),
                                                    tapZoneMode = tapZone,
                                                    canPrevious = controller.frameAt(-1) != null,
                                                    canNext = controller.frameAt(1) != null,
                                                )
                                            ) {
                                                ReaderTapAction.PREVIOUS_PAGE -> {
                                                    stopAutoPaging()
                                                    onGesturePageTurn()
                                                    turnRequest.intValue = -1
                                                }
                                                ReaderTapAction.NEXT_PAGE -> {
                                                    stopAutoPaging()
                                                    onGesturePageTurn()
                                                    turnRequest.intValue = 1
                                                }
                                                ReaderTapAction.TOGGLE_CONTROLS -> {
                                                    stopAutoPaging()
                                                    onToggleControls()
                                                }
                                                ReaderTapAction.NONE -> Unit
                                            }
                                        }
                                    },
                                )
                            }
                    // 读取 revision 让相邻章预排完成后触发重组，跨章拖动立即拿到邻帧。
                    @Suppress("UNUSED_VARIABLE")
                    val cacheRevision = controller.cacheRevision
                    val frame = controller.frameAt(0) ?: return@BoxWithConstraints
                    val previous = controller.frameAt(-1)
                    val next = controller.frameAt(1)
                    PageTurner(
                        currentFrame = frame,
                        previousFrame = previous,
                        nextFrame = next,
                        effect = if (autoPageIntervalMillis != null) "reveal" else pageTurnEffect,
                        turnRequest = turnRequest.intValue,
                        onTurnRequestConsumed = { turnRequest.intValue = 0 },
                        onPrevious = {
                            selRange.value = null
                            controller.prevPage()
                        },
                        onNext = {
                            selRange.value = null
                            controller.nextPage()
                        },
                        revealProgress = revealProgress,
                        revealDividerColor = MaterialTheme.colorScheme.primary,
                        revealBackground = pageBackground,
                        modifier = Modifier
                            .offset(x = horizontalInsetDp)
                            .width(contentWidthDp)
                            .fillMaxHeight()
                            .semantics { contentDescription = "分页正文已就绪" }
                            .then(gestures),
                    ) { rendered, isCurrent ->
                        // 语义文本与绘制切片同源；非当前帧（动画过渡中的邻页）不暴露，
                        // 避免 TalkBack 读到上一页 stale 文本或动画中间帧的重复正文。
                        val accessibleText = remember(rendered.page, rendered.chapterText) {
                            pageAccessibleText(rendered.page, rendered.chapterText)
                        }
                        PageLayer(
                            page = rendered.page,
                            chapterText = rendered.chapterText,
                            cfg = cfg,
                            paint = paint,
                            headingPaint = headingPaint,
                            underlays = if (isCurrent) underlays else emptyList(),
                            handles = if (isCurrent) selectionHandles else null,
                            accessibleText = if (isCurrent) {
                                if (accessibleText.isEmpty()) "本页无正文" else accessibleText
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // 页脚信息行占位在 Column 外层，这里只负责正文
            }
        }

        // 页脚：可配置内容。安静阅读信息关闭时只留空隙保持正文位置稳定。
        Row(
            Modifier.fillMaxWidth().height(28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val footerColor = textColor.copy(alpha = 0.45f)
            if (showReaderInfo) {
                Text(
                    resolveItemText(footerLeft, source, anchor.intValue, pageInfo.value, currentTime.value, batteryLevel.intValue, bookName),
                    fontSize = 11.sp,
                    color = footerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    resolveItemText(footerRight, source, anchor.intValue, pageInfo.value, currentTime.value, batteryLevel.intValue, bookName),
                    fontSize = 11.sp,
                    color = footerColor,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/** 根据配置条目解析出对应的显示文本 */
private fun handleDist(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

private fun resolveItemText(
    item: HeaderFooterItem,
    source: PagedChapterSource,
    absOffset: Int,
    pageInfo: PageInfo,
    currentTime: String,
    batteryLevel: Int,
    bookName: String,
): String = when (item) {
    HeaderFooterItem.NONE -> ""
    HeaderFooterItem.CHAPTER_TITLE -> {
        if (source.chapterCount > 0) source.chapterTitle(source.chapterIndexFor(absOffset)) else ""
    }
    HeaderFooterItem.BOOK_NAME -> bookName
    HeaderFooterItem.TIME -> currentTime
    HeaderFooterItem.BATTERY -> if (batteryLevel >= 0) "$batteryLevel%" else ""
    HeaderFooterItem.PAGE_NUMBER -> if (pageInfo.pageCount > 0) "${pageInfo.pageIndex + 1}/${pageInfo.pageCount}" else ""
    HeaderFooterItem.PROGRESS -> {
        val total = source.totalChars
        val percent = if (total <= 0) 0f else (absOffset * 100f / total).coerceIn(0f, 100f)
        "%.1f%%".format(percent)
    }
}

private fun formatCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

private fun getBatteryLevel(context: android.content.Context): Int {
    val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val batteryStatus = context.registerReceiver(null, ifilter)
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
}

/**
 * 单页绘制：逐簇 drawText。
 *
 * 位置全部来自排版产物：x = [line.clusterX]，簇文本 = 章文本按
 * `lineParaOffsets + clusterStarts` 切片。**这里不做任何排版决策**，
 * 排版层算出什么就画什么 —— 这是「排版可单测、绘制只是搬运」分层的关键。
 */
@Composable
private fun PageLayer(
    page: ChapterPaginator.Page,
    chapterText: String,
    cfg: LayoutConfig,
    paint: TextPaint,
    headingPaint: TextPaint,
    modifier: Modifier = Modifier,
    /** TalkBack 朗读的当前页正文；null = 非当前页（动画过渡帧）不暴露文本。 */
    accessibleText: String? = null,
    /** 文字底下的色块层（TTS 句高亮、选区），先画色块再画字 */
    underlays: List<Pair<Color, List<com.creationreadingassistant.feature.reader.layout.PageHitTest.Rect>>> = emptyList(),
    /** 选区把手（仅当前页有选区时非空），画在文字与图片之上 */
    handles: PageSelection.Handles? = null,
) {
    val density = LocalDensity.current
    val paper = LocalReaderPaperPalette.current
    Box(
        modifier.then(
            if (accessibleText != null) {
                // 用 text 语义而非 contentDescription：正文是可读文本，不是控件的描述
                Modifier.semantics { text = AnnotatedString(accessibleText) }
            } else {
                Modifier
            }
        )
    ) {
        PageCanvas(
            page = page,
            chapterText = chapterText,
            cfg = cfg,
            paint = paint,
            headingPaint = headingPaint,
            underlays = underlays,
            modifier = Modifier.fillMaxSize(),
        )
        val imagePlaceholderColor = paper.imagePlaceholder
        page.images.forEach { image ->
            val width = with(density) { image.width.toDp() }
            val height = with(density) { image.height.toDp() }
            Box(
                Modifier
                    .offset { IntOffset(image.left.roundToInt(), image.top.roundToInt()) }
                    .size(width, height)
                    .background(imagePlaceholderColor),
            ) {
                val imageFile = remember(image.sourceKey) { File(image.sourceKey) }
                SizedAsyncImage(
                    data = imageFile,
                    cacheKey = "reader:${image.sourceKey}:${imageFile.lastModified()}",
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // 选区把手圆点：主题色填充 + 纸色描边，深浅两种纸面下都可见
        handles?.takeIf { it.isActive }?.let { h ->
            val handleColor = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize()) {
                val rPx = 9.dp.toPx()
                val ringPx = 2.dp.toPx()
                listOfNotNull(h.left, h.right).forEach { p ->
                    drawCircle(color = paper.bg, radius = rPx + ringPx, center = Offset(p.x, p.y))
                    drawCircle(color = handleColor, radius = rPx, center = Offset(p.x, p.y))
                }
            }
        }
    }
}

@Composable
private fun PageCanvas(
    page: ChapterPaginator.Page,
    chapterText: String,
    cfg: LayoutConfig,
    paint: TextPaint,
    headingPaint: TextPaint,
    modifier: Modifier = Modifier,
    underlays: List<Pair<Color, List<com.creationreadingassistant.feature.reader.layout.PageHitTest.Rect>>> = emptyList(),
) {
    val paper = LocalReaderPaperPalette.current
    val stylePaints = remember(paint, cfg) {
        Array(32) { mask ->
            TextPaint(paint).apply {
                if (mask and MarkdownStyleMap.BOLD != 0) isFakeBoldText = true
                if (mask and MarkdownStyleMap.ITALIC != 0) textSkewX = -0.25f
                if (mask and MarkdownStyleMap.CODE != 0) {
                    typeface = Typeface.MONOSPACE
                    textSize *= cfg.codeScale
                }
                if (mask and MarkdownStyleMap.LINK != 0) {
                    isUnderlineText = true
                    isFakeBoldText = true
                }
            }
        }
    }

    Canvas(modifier) {
        underlays.forEach { (color, rects) ->
            rects.forEach { r ->
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(r.left, r.top),
                    size = androidx.compose.ui.geometry.Size(r.right - r.left, r.bottom - r.top),
                )
            }
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val fm = paint.fontMetrics
            val glyphH = fm.descent - fm.ascent

            page.lines.forEachIndexed { li, line ->
                val paraOff = page.lineParaOffsets.getOrElse(li) { 0 }
                val isHeading = line.role.isHeading()
                val basePaint = if (isHeading) headingPaint else paint
                val boxH = when (line.role) {
                    BlockRole.HEADING -> cfg.lineHeightPx * cfg.headingScale
                    BlockRole.HEADING_1 -> cfg.lineHeightPx * cfg.headingScale * 1.15f
                    BlockRole.HEADING_2 -> cfg.lineHeightPx * cfg.headingScale * 1.10f
                    BlockRole.HEADING_3 -> cfg.lineHeightPx * cfg.headingScale * 1.05f
                    BlockRole.CODE_BLOCK -> cfg.lineHeightPx * cfg.codeScale
                    else -> cfg.lineHeightPx
                }
                val top = page.lineTops.getOrElse(li) { 0f }
                val baseline = top + (boxH - glyphH) / 2f - fm.ascent

                if (line.role == BlockRole.CODE_BLOCK) {
                    drawRect(
                        color = paper.codeBlockBg,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                        size = androidx.compose.ui.geometry.Size(cfg.contentWidthPx, boxH),
                    )
                }

                if (line.role == BlockRole.HORIZONTAL_RULE) {
                    drawLine(
                        color = paper.horizontalRule,
                        start = androidx.compose.ui.geometry.Offset(0f, baseline),
                        end = androidx.compose.ui.geometry.Offset(cfg.contentWidthPx, baseline),
                        strokeWidth = 2f,
                    )
                    return@forEachIndexed
                }

                line.listMarker?.let { marker ->
                    val markerW = basePaint.measureText(marker)
                    native.drawText(
                        marker, 0, marker.length,
                        line.clusterX.getOrElse(0) { 0f } - markerW,
                        baseline, basePaint,
                    )
                }

                val clusterStyles = line.clusterStyles
                for (j in 0 until line.clusterCount) {
                    val s = paraOff + line.clusterStarts[j]
                    val e = paraOff + line.clusterStarts[j + 1]
                    if (e <= s || s < 0 || e > chapterText.length) continue
                    val mask = clusterStyles?.getOrNull(j) ?: 0
                    val p = if (mask == 0) basePaint else stylePaints[mask]
                    native.drawText(chapterText, s, e, line.clusterX[j], baseline, p)
                    if (mask and MarkdownStyleMap.STRIKETHROUGH != 0) {
                        val left = line.clusterX[j]
                        val right = line.clusterX[j + 1]
                        native.drawLine(
                            left, baseline + fm.ascent * 0.35f,
                            right, baseline + fm.ascent * 0.35f,
                            p,
                        )
                    }
                }
            }
        }
    }
}
