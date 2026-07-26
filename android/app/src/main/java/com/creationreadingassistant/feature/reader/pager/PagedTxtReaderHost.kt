package com.creationreadingassistant.feature.reader.pager

import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.layout.android.IcuBreakOracle
import com.creationreadingassistant.feature.reader.layout.android.PaintTextRuler
import kotlin.math.abs

/**
 * TXT 左右翻页宿主（pagerEngineMode = on 时替换滚动视图）。
 *
 * 组成：视口测量 → [LayoutConfig] → [TxtPagedController]（后台整章排版）→
 * [PageCanvas]（逐簇绘制）→ 手势（左/右点按翻页、水平滑动翻页、中央点按呼出菜单）。
 *
 * P2 边界（后续阶段补）：无翻页动画（P4）、无选区/高亮/TTS 句高亮（P3）。
 *
 * @param jumpRequest 外部跳转请求（进度条拖动、目录跳章）。消费后置回 null。
 * @param onPositionChanged 页变化回调：全书偏移 + 进度百分比。
 *        调用方拿它做进度持久化与顶栏章节名显示。
 */
@Composable
fun PagedTxtReaderHost(
    fullText: String,
    chapters: List<DocChapter>,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    paragraphSpacing: Float,
    pageMarginDp: Float,
    fontWeightBold: Boolean,
    showReaderInfo: Boolean,
    textColor: Color,
    initialOffset: Int,
    jumpRequest: MutableState<Int?>,
    onPositionChanged: (absOffset: Int, percent: Float) -> Unit,
    onToggleControls: () -> Unit,
    store: PageIndexStore?,
    contentKey: String,
    modifier: Modifier = Modifier,
    /** TTS 当前句（全书偏移区间），null = 不朗读 */
    ttsRangeAbs: Pair<Int, Int>? = null,
    /** 长按选句回调：(选中文本, 全书起始偏移)。空文本 = 选区清除 */
    onSelect: (String, Int) -> Unit = { _, _ -> },
    /** 外部（工具条动作后）已清空选区的信号，host 据此撤掉选区底色 */
    selectionCleared: Boolean = true,
    selectionColor: Color = Color(0x40365B7E),
    ttsHighlightColor: Color = Color(0x33365B7E),
) {
    val density = LocalDensity.current
    val fontPx = with(density) { fontSizeSp.sp.toPx() }

    // 绘制与测量必须用同一支 Paint 的配置，否则测出来的宽度与画出来的不一致
    val paint = remember(fontPx, fontWeightBold, textColor) {
        TextPaint().apply {
            isAntiAlias = true
            textSize = fontPx
            color = textColor.toArgb()
            isFakeBoldText = fontWeightBold
        }
    }
    val headingPaint = remember(paint) { TextPaint(paint).apply { isFakeBoldText = true } }
    val typefaceKey = remember(paint) {
        PaintTextRuler.typefaceKeyOf(paint.typeface, paint.textSize, paint.letterSpacing) +
            "|b=$fontWeightBold"
    }
    val ruler = remember(paint, typefaceKey) { PaintTextRuler(paint, typefaceKey) }
    val oracle = remember { IcuBreakOracle() }
    val scope = rememberCoroutineScope()

    // 位置锚点：当前页首字符的全书偏移。改字号/转屏/切菜单导致重排后，靠它回到同一句。
    val anchor = remember(fullText) { mutableIntStateOf(initialOffset) }

    Column(modifier.fillMaxSize().padding(horizontal = pageMarginDp.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()

                val cfg = remember(widthPx, heightPx, fontPx, lineHeightMultiplier, paragraphSpacing, typefaceKey) {
                    LayoutConfig(
                        contentWidthPx = widthPx,
                        contentHeightPx = heightPx,
                        fontSizePx = fontPx,
                        lineHeightMultiplier = lineHeightMultiplier,
                        // 设置里的段距是 0.8/1.1/1.5 的倍率档，映射到 em 值
                        paragraphSpacingEm = 0.4f * paragraphSpacing,
                        typefaceKey = typefaceKey,
                    )
                }

                val controller = remember(fullText, chapters, cfg, ruler) {
                    TxtPagedController(
                        fullText = fullText,
                        chapters = chapters,
                        cfg = cfg,
                        ruler = ruler,
                        oracle = oracle,
                        scope = scope,
                        store = store,
                        contentKey = contentKey,
                    )
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
                LaunchedEffect(selectionCleared) { if (selectionCleared) selRange.value = null }

                val page = controller.currentPage
                if (page == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
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
                    PageCanvas(
                        page = page,
                        chapterText = controller.chapterText,
                        cfg = cfg,
                        paint = paint,
                        headingPaint = headingPaint,
                        underlays = listOf(ttsHighlightColor to ttsRects, selectionColor to selRects),
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(controller) {
                                detectTapGestures(
                                    onLongPress = { offset ->
                                        // 长按选中一句，交给外部工具条做高亮/笔记/灵感
                                        val ch = PageSelection.offsetAt(page, cfg, offset.x, offset.y)
                                        val sent = PageSelection.sentenceAround(controller.chapterText, ch)
                                        if (!sent.isEmpty()) {
                                            selRange.value = sent
                                            onSelect(
                                                controller.chapterText.substring(sent.first, sent.last + 1),
                                                chStart + sent.first,
                                            )
                                        }
                                    },
                                    onTap = { offset ->
                                        if (selRange.value != null) {
                                            // 有选区时，任何点按先撤选区，不翻页
                                            selRange.value = null
                                            onSelect("", -1)
                                        } else {
                                            when {
                                                offset.x < size.width / 3f -> controller.prevPage()
                                                offset.x > size.width * 2f / 3f -> controller.nextPage()
                                                else -> onToggleControls()
                                            }
                                        }
                                    },
                                )
                            }
                            .pointerInput(controller) {
                                // 水平滑动翻页：松手时按累计位移方向翻。动画留给 P4。
                                var dragTotal = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { dragTotal = 0f },
                                    onDragEnd = {
                                        val threshold = with(density) { 48.dp.toPx() }
                                        if (abs(dragTotal) > threshold) {
                                            selRange.value = null
                                            if (dragTotal < 0) controller.nextPage() else controller.prevPage()
                                        }
                                    },
                                ) { _, dragAmount -> dragTotal += dragAmount }
                            },
                    )
                }

                // 页脚信息行占位在 Column 外层，这里只负责正文
            }
        }

        // 页脚：章节名 + 页号。安静阅读信息关闭时只留空隙保持正文位置稳定。
        Row(
            Modifier.fillMaxWidth().height(28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val footerColor = textColor.copy(alpha = 0.45f)
            val controllerInfo = footerInfo(fullText, chapters, anchor.intValue)
            if (showReaderInfo) {
                Text(
                    controllerInfo.first,
                    fontSize = 11.sp,
                    color = footerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    controllerInfo.second,
                    fontSize = 11.sp,
                    color = footerColor,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/** 页脚左右两栏：章节名 / 全书进度。页号在排版完成前拿不到，用全书百分比代替更稳。 */
private fun footerInfo(fullText: String, chapters: List<DocChapter>, absOffset: Int): Pair<String, String> {
    val title = chapters.lastOrNull { it.startOffset <= absOffset }?.title ?: ""
    val percent = if (fullText.isEmpty()) 0f else (absOffset * 100f / fullText.length).coerceIn(0f, 100f)
    return title to "%.1f%%".format(percent)
}

/**
 * 单页绘制：逐簇 drawText。
 *
 * 位置全部来自排版产物：x = [line.clusterX]，簇文本 = 章文本按
 * `lineParaOffsets + clusterStarts` 切片。**这里不做任何排版决策**，
 * 排版层算出什么就画什么 —— 这是「排版可单测、绘制只是搬运」分层的关键。
 */
@Composable
private fun PageCanvas(
    page: ChapterPaginator.Page,
    chapterText: String,
    cfg: LayoutConfig,
    paint: TextPaint,
    headingPaint: TextPaint,
    modifier: Modifier = Modifier,
    /** 文字底下的色块层（TTS 句高亮、选区），先画色块再画字 */
    underlays: List<Pair<Color, List<com.creationreadingassistant.feature.reader.layout.PageHitTest.Rect>>> = emptyList(),
) {
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
                val isHeading = line.role == BlockRole.HEADING
                val p = if (isHeading) headingPaint else paint
                val boxH = if (isHeading) cfg.lineHeightPx * cfg.headingScale else cfg.lineHeightPx
                val top = page.lineTops.getOrElse(li) { 0f }
                // 字形在行框内垂直居中
                val baseline = top + (boxH - glyphH) / 2f - fm.ascent

                for (j in 0 until line.clusterCount) {
                    val s = paraOff + line.clusterStarts[j]
                    val e = paraOff + line.clusterStarts[j + 1]
                    if (e <= s || s < 0 || e > chapterText.length) continue
                    native.drawText(chapterText, s, e, line.clusterX[j], baseline, p)
                }
            }
        }
    }
}
