package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderAlignment
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderCell
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderContent
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderSpan
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderUnit

/**
 * 单元渲染文本 = canonical 文本按 [MarkdownRenderUnit.canonicalRange] 原样切片
 * （列表 marker 由调用方按内容前缀，不进入切片文本）。不复制文本、不猜偏移。
 */
private fun unitText(canonicalText: String, unit: MarkdownRenderUnit): String =
    canonicalText.substring(unit.canonicalRange.first, unit.canonicalRange.last + 1)

/**
 * 将统一模型的行内 spans 构建为带样式的 [AnnotatedString]。
 *
 * [text] 是单元/单元格的 canonical 切片（列表项可带 [prefix] marker，如 `• ` /
 * `3. ` / `[x] `）；span 的 [MarkdownRenderSpan.start]/[end] 相对该文本载体的
 * canonical 起点，直接落到 [text] 上。TTS 句子高亮与搜索命中局部区间同为 canonical
 * 局部坐标，叠加时统一平移 [prefix] 长度。
 */
private fun buildMarkdownAnnotated(
    text: String,
    spans: List<MarkdownRenderSpan>,
    fontSize: Float,
    paperFg: Color,
    prefix: String = "",
    ttsLocal: IntRange? = null,
    sentenceHighlightBg: Color = Color.Transparent,
    searchLocal: IntRange? = null,
    searchHighlightBg: Color = Color.Transparent,
): AnnotatedString {
    val fullText = prefix + text
    if (spans.isEmpty() && ttsLocal == null && searchLocal == null) return AnnotatedString(fullText)
    val shift = prefix.length
    return AnnotatedString.Builder(fullText).apply {
        spans.forEach { span ->
            when (span) {
                is MarkdownRenderSpan.Code -> addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = (fontSize * 0.9).sp,
                        // 用 paperFg 叠低 alpha 当代码块底色，跟随 paper palette 变化；
                        // 不用 Color.LightGray（固定 #D3D3D3 在夜读纸上叠 alpha 会显灰白浑浊）。
                        background = paperFg.copy(alpha = 0.08f),
                    ),
                    shift + span.start,
                    shift + span.end,
                )

                is MarkdownRenderSpan.Strong -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    shift + span.start,
                    shift + span.end,
                )

                is MarkdownRenderSpan.Emphasis -> addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic),
                    shift + span.start,
                    shift + span.end,
                )

                is MarkdownRenderSpan.Strikethrough -> addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                    shift + span.start,
                    shift + span.end,
                )

                is MarkdownRenderSpan.Link -> addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline),
                    shift + span.start,
                    shift + span.end,
                )

                is MarkdownRenderSpan.Plain,
                is MarkdownRenderSpan.Image -> Unit // 叶子文本无样式；图片以 alt 文本参与朗读/搜索
            }
        }
        // TTS 句子高亮叠加
        if (ttsLocal != null && ttsLocal.last > ttsLocal.first) {
            addStyle(
                SpanStyle(background = sentenceHighlightBg),
                shift + ttsLocal.first.coerceAtLeast(0),
                shift + ttsLocal.last.coerceAtMost(text.length),
            )
        }
        // 搜索命中临时高亮叠加（局部区间由 markdownScrollSearchHits 依 parser 范围算出）
        if (searchLocal != null && searchLocal.last > searchLocal.first) {
            val s = searchLocal.first.coerceIn(0, text.length)
            val e = searchLocal.last.coerceIn(0, text.length)
            if (e > s) {
                addStyle(
                    SpanStyle(background = searchHighlightBg),
                    shift + s,
                    shift + e,
                )
            }
        }
    }.toAnnotatedString()
}

/** TTS 句子区间（章内 canonical 坐标）→ 单元渲染文本内的局部区间。 */
private fun ttsLocalRange(
    ttsSentenceRange: Pair<Int, Int>?,
    globalOffset: Int,
    chapterBase: Int,
    textLength: Int,
): IntRange? {
    if (ttsSentenceRange == null || globalOffset < 0) return null
    val localStart = ttsSentenceRange.first - (globalOffset - chapterBase)
    val localEnd = ttsSentenceRange.second - (globalOffset - chapterBase)
    if (localStart >= textLength || localEnd <= 0 || localEnd <= localStart) return null
    return localStart.coerceAtLeast(0) until localEnd.coerceAtMost(textLength)
}

/**
 * 渲染 [MarkdownParser.MarkdownChapter] 的所有块（整章 Column 形式，EPUB 遗留路径
 * 使用）。内部先经 [MarkdownRenderModel.flatten] 得到唯一扁平渲染模型，再按单元渲染，
 * 与滚动 LazyColumn 路径共享同一阅读顺序 / identity / canonical ranges。
 */
@Composable
internal fun RenderMarkdownChapter(
    chapter: MarkdownParser.MarkdownChapter,
    fontSize: Float,
    lineHeight: Float,
    paperFg: Color,
    blockGlobalOffset: Int = -1,
    chapterBase: Int = 0,
    ttsSentenceRange: Pair<Int, Int>? = null,
    sentenceHighlightBg: Color = Color.Transparent,
    onSelectBlock: ((String, Int) -> Unit)? = null,
    fontFamily: FontFamily = FontFamily.Default,
    searchHits: List<MarkdownScrollHit> = emptyList(),
    searchHighlightBg: Color = Color.Transparent,
) {
    val units = remember(chapter) { MarkdownRenderModel.flatten(chapter) }
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        units.forEachIndexed { index, unit ->
            // 结构 identity 用 unit.id（与滚动 LazyColumn key 同源），不能用下标：
            // 下标会随阅读顺序/重组漂移，导致滚动状态与高亮挂到错误单元。
            key(MarkdownUnitVisualPolicy.unitKey(unit)) {
                // 还原递归渲染间距：顶层块间 4.dp，容器内 0.dp
                val topGap = if (index > 0 && unit.topBlockIndex != units[index - 1].topBlockIndex) {
                    4.dp
                } else {
                    0.dp
                }
                RenderMarkdownUnit(
                    unit = unit,
                    canonicalText = chapter.canonicalText,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    paperFg = paperFg,
                    blockGlobalOffset = blockGlobalOffset,
                    chapterBase = chapterBase,
                    ttsSentenceRange = ttsSentenceRange,
                    sentenceHighlightBg = sentenceHighlightBg,
                    onSelectBlock = onSelectBlock,
                    fontFamily = fontFamily,
                    searchHits = searchHits,
                    searchHighlightBg = searchHighlightBg,
                    topGap = topGap,
                )
            }
        }
    }
}

/**
 * 渲染单个 Markdown 渲染单元（滚动模式 LazyColumn 项）。
 *
 * 只消费 [MarkdownRenderUnit]（doc 统一模型）与 canonical 文本切片，不读 AST；
 * 结构 identity 使用 [MarkdownRenderUnit.id]，组合阶段不做文件或网络 I/O。
 * 缩进按 [MarkdownRenderUnit.depth] 还原递归渲染的 blockquote/list 逐层 16.dp 内缩；
 * [topGap] 由调用方按「顶层块间 4.dp、容器内 0.dp」的原有间距传入。
 */
@Composable
internal fun RenderMarkdownUnit(
    unit: MarkdownRenderUnit,
    canonicalText: String,
    fontSize: Float,
    lineHeight: Float,
    paperFg: Color,
    blockGlobalOffset: Int = -1,
    chapterBase: Int = 0,
    ttsSentenceRange: Pair<Int, Int>? = null,
    sentenceHighlightBg: Color = Color.Transparent,
    onSelectBlock: ((String, Int) -> Unit)? = null,
    fontFamily: FontFamily = FontFamily.Default,
    searchHits: List<MarkdownScrollHit> = emptyList(),
    searchHighlightBg: Color = Color.Transparent,
    topGap: Dp = 0.dp,
) {
    val gOff = if (blockGlobalOffset >= 0) blockGlobalOffset + unit.canonicalRange.start else -1
    val modifier = Modifier
        .fillMaxWidth()
        .quoteRail(unit.blockquoteDepth, paperFg)
        .padding(start = MarkdownUnitVisualPolicy.TEXT_INDENT_PER_DEPTH_DP.dp * unit.depth, top = topGap)
    val text = unitText(canonicalText, unit)
    val searchLocal = searchHits.firstOrNull { it.canonicalStart == unit.canonicalRange.first }?.localRange
    val ttsLocal = ttsLocalRange(ttsSentenceRange, gOff, chapterBase, text.length)

    when (val content = unit.content) {
        is MarkdownRenderContent.Heading -> {
            val scale = when (content.level) {
                1 -> 1.5f; 2 -> 1.3f; 3 -> 1.15f; else -> 1.05f
            }
            Text(
                text = buildMarkdownAnnotated(
                    text, content.spans, fontSize, paperFg,
                    ttsLocal = ttsLocal, sentenceHighlightBg = sentenceHighlightBg,
                    searchLocal = searchLocal, searchHighlightBg = searchHighlightBg,
                ),
                fontSize = (fontSize * scale).sp,
                fontWeight = FontWeight.Bold,
                color = paperFg,
                lineHeight = (fontSize * scale * lineHeight).sp,
                fontFamily = fontFamily,
                modifier = modifier.padding(vertical = 4.dp)
                    .then(if (onSelectBlock != null) Modifier.clickable { onSelectBlock(text, gOff) } else Modifier),
            )
        }

        is MarkdownRenderContent.Paragraph -> {
            Text(
                text = buildMarkdownAnnotated(
                    text, content.spans, fontSize, paperFg,
                    ttsLocal = ttsLocal, sentenceHighlightBg = sentenceHighlightBg,
                    searchLocal = searchLocal, searchHighlightBg = searchHighlightBg,
                ),
                fontSize = fontSize.sp,
                color = paperFg,
                lineHeight = (fontSize * lineHeight).sp,
                fontFamily = fontFamily,
                modifier = modifier
                    .then(if (onSelectBlock != null) Modifier.clickable { onSelectBlock(text, gOff) } else Modifier),
            )
        }

        is MarkdownRenderContent.ListItem -> {
            Text(
                text = buildMarkdownAnnotated(
                    text, content.spans, fontSize, paperFg,
                    prefix = content.marker,
                    ttsLocal = ttsLocal, sentenceHighlightBg = sentenceHighlightBg,
                    searchLocal = searchLocal, searchHighlightBg = searchHighlightBg,
                ),
                fontSize = fontSize.sp,
                color = paperFg,
                lineHeight = (fontSize * lineHeight).sp,
                fontFamily = fontFamily,
                modifier = modifier
                    .then(if (onSelectBlock != null) Modifier.clickable { onSelectBlock(text, gOff) } else Modifier),
            )
        }

        is MarkdownRenderContent.TaskItem -> {
            val marker = if (content.checked) "[x] " else "[ ] "
            Text(
                text = buildMarkdownAnnotated(
                    text, content.spans, fontSize, paperFg,
                    prefix = marker,
                    ttsLocal = ttsLocal, sentenceHighlightBg = sentenceHighlightBg,
                    searchLocal = searchLocal, searchHighlightBg = searchHighlightBg,
                ),
                fontSize = fontSize.sp,
                color = paperFg,
                lineHeight = (fontSize * lineHeight).sp,
                fontFamily = fontFamily,
                modifier = modifier
                    .then(if (onSelectBlock != null) Modifier.clickable { onSelectBlock(text, gOff) } else Modifier),
            )
        }

        is MarkdownRenderContent.CodeBlock -> {
            // 代码块底色同上：paperFg.copy(alpha) 跟随纸色，避免 LightGray 在夜读纸上浑浊。
            // 语言标签是纯展示：与代码 Text 分离，不进入 canonical 文本 / 搜索 / TTS /
            // 选区 offset；searchLocal 只作用于代码文本本身。
            val languageLabel = MarkdownUnitVisualPolicy.codeLanguageLabel(content.language)
            Column(
                modifier = modifier
                    .background(paperFg.copy(alpha = 0.06f))
                    .padding(8.dp)
                    .then(if (onSelectBlock != null) Modifier.clickable { onSelectBlock(text, gOff) } else Modifier),
            ) {
                if (languageLabel != null) {
                    Text(
                        text = languageLabel,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (fontSize * 0.7).sp,
                        color = paperFg.copy(alpha = 0.5f),
                        lineHeight = (fontSize * 0.7 * lineHeight).sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    text = markdownCodeAnnotated(text, searchLocal, searchHighlightBg),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (fontSize * 0.85).sp,
                    color = paperFg,
                    lineHeight = (fontSize * 0.85 * lineHeight).sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is MarkdownRenderContent.Table -> {
            val tableScroll = rememberScrollState()
            Column(
                modifier = modifier
                    .horizontalScroll(tableScroll)
                    .then(if (onSelectBlock != null) Modifier.clickable { onSelectBlock("[Table]", gOff) } else Modifier),
            ) {
                // 表头
                if (content.header.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        content.header.forEach { cell ->
                            TableCellContent(
                                cell, canonicalText, fontSize, lineHeight, paperFg, bold = true, fontFamily = fontFamily,
                                searchHits = searchHits, searchHighlightBg = searchHighlightBg,
                            )
                        }
                    }
                    HorizontalDivider(color = paperFg.copy(alpha = 0.3f))
                }
                // 数据行
                content.rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { cell ->
                            TableCellContent(
                                cell, canonicalText, fontSize, lineHeight, paperFg, bold = false, fontFamily = fontFamily,
                                searchHits = searchHits, searchHighlightBg = searchHighlightBg,
                            )
                        }
                    }
                    HorizontalDivider(color = paperFg.copy(alpha = 0.12f))
                }
            }
        }

        MarkdownRenderContent.HorizontalRule -> {
            HorizontalDivider(
                color = paperFg.copy(alpha = 0.3f),
                modifier = modifier.padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * 引用轨道：blockquote 深度 > 0 时在单元内容左缘之外画竖向 rail。
 *
 * 用 [Modifier.drawBehind] 实现 —— 不拦截点击、不影响文本选区；颜色 = paperFg 叠低
 * alpha（随纸色，不引入第二套主题）。轨道几何与「不遮字」不变式由
 * [MarkdownUnitVisualPolicy] 纯策略锁定（JVM 测试），本函数只负责按策略画出来。
 */
private fun Modifier.quoteRail(blockquoteDepth: Int, paperFg: Color): Modifier {
    if (blockquoteDepth <= 0) return this
    val offsets = MarkdownUnitVisualPolicy.quoteRailOffsetsDp(blockquoteDepth)
    val railWidth = MarkdownUnitVisualPolicy.QUOTE_RAIL_WIDTH_DP
    val railAlpha = MarkdownUnitVisualPolicy.QUOTE_RAIL_ALPHA
    return this.then(
        Modifier.drawBehind {
            val railWidthPx = railWidth.dp.toPx()
            val color = paperFg.copy(alpha = railAlpha)
            offsets.forEach { offsetDp ->
                drawRect(
                    color = color,
                    topLeft = Offset(offsetDp.dp.toPx(), 0f),
                    size = Size(railWidthPx, size.height),
                )
            }
        },
    )
}

/** 渲染表格单元格（列语义：对齐 + 表头加粗 + 行内 spans）。 */
@Composable
private fun TableCellContent(
    cell: MarkdownRenderCell,
    canonicalText: String,
    fontSize: Float,
    lineHeight: Float,
    paperFg: Color,
    bold: Boolean,
    fontFamily: FontFamily = FontFamily.Default,
    searchHits: List<MarkdownScrollHit> = emptyList(),
    searchHighlightBg: Color = Color.Transparent,
) {
    val textAlign = when (cell.alignment) {
        MarkdownRenderAlignment.LEFT -> TextAlign.Left
        MarkdownRenderAlignment.CENTER -> TextAlign.Center
        MarkdownRenderAlignment.RIGHT -> TextAlign.Right
        MarkdownRenderAlignment.NONE -> TextAlign.Left
    }
    val cellText = canonicalText.substring(cell.canonicalRange.first, cell.canonicalRange.last + 1)
    val searchLocal = if (cellText.isNotEmpty()) {
        searchHits.firstOrNull { it.canonicalStart == cell.canonicalRange.first }?.localRange
    } else {
        null
    }
    Text(
        text = buildMarkdownAnnotated(
            cellText, cell.spans, fontSize, paperFg,
            searchLocal = searchLocal, searchHighlightBg = searchHighlightBg,
        ),
        fontSize = (fontSize * 0.85).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = textAlign,
        color = paperFg,
        lineHeight = (fontSize * 0.85 * lineHeight).sp,
        fontFamily = fontFamily,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 为无行内结构的渲染文本（代码块 / 表格单元格）叠加搜索命中背景。 */
private fun markdownCodeAnnotated(
    text: String,
    searchLocal: IntRange?,
    searchHighlightBg: Color,
): AnnotatedString {
    if (searchLocal == null || searchLocal.last <= searchLocal.first) return AnnotatedString(text)
    val s = searchLocal.first.coerceIn(0, text.length)
    val e = searchLocal.last.coerceIn(0, text.length)
    if (e <= s) return AnnotatedString(text)
    return AnnotatedString.Builder(text).apply {
        addStyle(SpanStyle(background = searchHighlightBg), s, e)
    }.toAnnotatedString()
}
