package com.creationreadingassistant.feature.reader.pager

import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import com.creationreadingassistant.ui.components.SizedAsyncImage
import com.creationreadingassistant.feature.reader.ReaderFontManager
import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.layout.MarkdownStyleMap
import com.creationreadingassistant.feature.reader.layout.android.IcuBreakOracle
import com.creationreadingassistant.feature.reader.layout.android.PaintTextRuler
import com.creationreadingassistant.feature.reader.layout.isHeading
import java.io.File
import kotlin.math.roundToInt
import com.creationreadingassistant.data.settings.HeaderFooterItem
import com.creationreadingassistant.ui.theme.LocalReaderPaperPalette
import com.creationreadingassistant.ui.screen.reader.searchHighlightColor

/**
 * 左右翻页宿主（pagerEngineMode = on 时替换原视图；TXT 与 EPUB 共用，
 * 差别都在 [PagedChapterSource] 后面。EPUB 首版为纯文本页，图片块暂不渲染）。
 *
 * 组成：视口测量 → [LayoutConfig] → [PagedReaderController]（后台整章排版）→
 * [PageCanvas]（逐簇绘制）→ 手势（左/右点按翻页、水平滑动翻页、中央点按呼出菜单）。
 *
 * P2 边界（后续阶段补）：无翻页动画（P4）、无选区/高亮/TTS 句高亮（P3）。
 *
 * 任务 #15 结构拆分：时钟 effect → [PagedReaderClockEffects]；页眉/页脚 →
 * [PagedReaderHeader]/[PagedReaderFooter]；控制器 effect 组 →
 * [PagedReaderControllerEffects]；页面渲染与手势 → [PagedReaderPageSurface]。
 * 纯结构搬运，effect key 与启动条件逐字保留。
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
    /** 长按选句回调：(显示文本, source 全书起点, source 全书半开终点)。空文本 = 选区清除 */
    onSelect: (String, Int, Int) -> Unit = { _, _, _ -> },
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
    PagedReaderClockEffects(currentTime = currentTime, batteryLevel = batteryLevel, context = context)

    // 位置锚点：当前页首字符的全书偏移。改字号/转屏/菜单导致重排后，靠它回到同一句。
    val anchor = remember(source) { mutableIntStateOf(initialOffset) }
    // 页眉/页脚需要的分页信息（章号、页号、总页数、进度）
    val pageInfo = remember { mutableStateOf(PageInfo()) }
    // 选区（章内偏移区间）。翻页/外部清除时撤掉。
    val selRange = remember { mutableStateOf<IntRange?>(null) }
    val turnRequest = remember { mutableIntStateOf(0) }
    // 自动翻页揭动画进度（0..1）：主函数持有，effect 组写、页面层读。
    val revealProgress = remember { mutableFloatStateOf(0f) }

    Column(modifier.fillMaxSize().padding(horizontal = pageMarginDp.dp)) {
        PagedReaderHeader(
            showReaderInfo = showReaderInfo,
            headerLeft = headerLeft,
            headerRight = headerRight,
            source = source,
            anchorValue = anchor.intValue,
            pageInfo = pageInfo.value,
            currentTime = currentTime.value,
            batteryLevel = batteryLevel.intValue,
            bookName = bookName,
            textColor = textColor,
        )

        // 顶部留白 = 页眉行高 + 固定小间距，与底部页脚（28dp）视觉对称；
        // 不再叠加完整页边距，避免「上宽下窄」的头重感。
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
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

                PagedReaderControllerEffects(
                    controller = controller,
                    anchor = anchor,
                    pageInfo = pageInfo,
                    onPositionChanged = onPositionChanged,
                    onPageIndexChanged = onPageIndexChanged,
                    bookId = bookId,
                    jumpRequest = jumpRequest,
                    selectionCleared = selectionCleared,
                    selRange = selRange,
                    externalTurnRequest = externalTurnRequest,
                    turnRequest = turnRequest,
                    autoPageIntervalMillis = autoPageIntervalMillis,
                    onAutoPagingFinished = onAutoPagingFinished,
                    revealProgressState = revealProgress,
                )

                PagedReaderPageSurface(
                    controller = controller,
                    cfg = cfg,
                    paint = paint,
                    headingPaint = headingPaint,
                    textColor = textColor,
                    selRange = selRange,
                    turnRequest = turnRequest,
                    revealProgress = revealProgress.floatValue,
                    tapZoneMode = tapZoneMode,
                    density = density,
                    horizontalInsetDp = horizontalInsetDp,
                    contentWidthDp = contentWidthDp,
                    autoPageIntervalMillis = autoPageIntervalMillis,
                    pageTurnEffect = pageTurnEffect,
                    pageBackground = pageBackground,
                    ttsRangeAbs = ttsRangeAbs,
                    ttsHighlightColor = ttsHighlightColor,
                    selectionColor = selectionColor,
                    persistentHighlights = persistentHighlights,
                    searchHitRangeAbs = searchHitRangeAbs,
                    searchHighlightColor = searchHighlightColor,
                    onSelect = onSelect,
                    onGesturePageTurn = onGesturePageTurn,
                    onToggleControls = onToggleControls,
                    onStopAutoPaging = onStopAutoPaging,
                )

                // 页脚信息行占位在 Column 外层，这里只负责正文
            }
        }

        PagedReaderFooter(
            showReaderInfo = showReaderInfo,
            footerLeft = footerLeft,
            footerRight = footerRight,
            source = source,
            anchorValue = anchor.intValue,
            pageInfo = pageInfo.value,
            currentTime = currentTime.value,
            batteryLevel = batteryLevel.intValue,
            bookName = bookName,
            textColor = textColor,
        )
    }
}

/**
 * 单页绘制：逐簇 drawText。
 *
 * 位置全部来自排版产物：x = [line.clusterX]，簇文本 = 章文本按
 * `lineParaOffsets + clusterStarts` 切片。**这里不做任何排版决策**，
 * 排版层算出什么就画什么 —— 这是「排版可单测、绘制只是搬运」分层的关键。
 */
@Composable
internal fun PageLayer(
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
                // StrictMode-safe：lastModified() 放到 IO 线程，避免组合期主线程 I/O。
                // 0L 兜底：Coil 仍会按 file data 的磁盘缓存命中；新文件算出真实时间戳后自动刷新。
                var imageModTs by remember(image.sourceKey) { mutableStateOf(0L) }
                LaunchedEffect(image.sourceKey) {
                    imageModTs = withContext(Dispatchers.IO) {
                        runCatching { imageFile.lastModified() }.getOrDefault(0L)
                    }
                }
                SizedAsyncImage(
                    data = imageFile,
                    cacheKey = "reader:${image.sourceKey}:$imageModTs",
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
