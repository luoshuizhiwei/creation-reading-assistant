package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.LegacyOffsetCodec
import com.creationreadingassistant.feature.reader.doc.MarkdownBlock
import com.creationreadingassistant.feature.reader.doc.MdInline
import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import kotlinx.serialization.json.Json
import java.time.Instant

/** 时间戳 ISO-8601 字符串。 */
internal fun nowIso(): String = Instant.now().toString()

/** 按阅读进度百分比推算最接近的章节索引（SE4 兜底定位用）。 */
internal fun progressToChapterIndex(book: EpubBook, progressPercent: Float?): Int {
    val size = book.chapters.size
    if (size <= 1) return 0
    if (progressPercent == null) return 0
    return ((progressPercent / 100f * size - 1).toInt()).coerceIn(0, size - 1)
}

/** 毫秒格式化为「X 小时 Y 分」（对照 web formatDuration）。 */
internal fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60_000).toInt()
    if (totalMin <= 0) return "不到 1 分钟"
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h} 小时 ${m} 分" else "${m} 分"
}

/** 构造标准灵感 payload，确保 InspirationViewModel 能正确解析来源（全字段对齐网页 I5）。 */
internal fun buildInspirationPayload(
    bid: String,
    bookTitle: String,
    chapterTitle: String,
    excerpt: String?,
    progressPercent: Float,
    tags: List<String> = emptyList(),
    categoryIds: List<String> = emptyList(),
    bookAuthor: String? = null,
): String {
    val payload = InspirationPayloadData(
        tags = tags,
        categoryIds = categoryIds,
        source = InspirationSourceInfo(
            bookId = bid.ifBlank { null },
            bookTitle = bookTitle.ifBlank { null },
            bookAuthor = bookAuthor?.takeIf { it.isNotBlank() },
            chapterTitle = chapterTitle.ifBlank { null },
            locationLabel = null,
            progressPercent = if (progressPercent > 0f) progressPercent else null,
            excerpt = excerpt?.takeIf { it.isNotBlank() },
        ),
    )
    return Json.encodeToString(InspirationPayloadData.serializer(), payload)
}

/** 从 locator_json 解析全局字符偏移（容错：不依赖完整 JSON 解析）。 */
internal fun parseLocatorOffset(json: String?): Int? = LocatorCodec.decode(json)?.legacyOffset

/**
 * 计算每章各渲染块（含图片，图片记为 -1）在全书文本中的全局字符偏移。
 * 与 [splitSentencesWithOffsets] 对 contentText 的切分一致：文本块按 "\n" 拼接。
 */
internal fun computeBlockGlobalOffsets(blocks: List<DocBlock>, chapterBase: Int): List<Int> =
    LegacyOffsetCodec.blockOffsets(
        blocks.map { (it as? DocBlock.Text)?.text?.length },
        chapterBase,
    )

/** 根据章节内偏移，返回包含该偏移的「渲染块」索引（用于导航精确滚动）。 */
internal fun blockIndexForChapterOffset(blocks: List<DocBlock>, inChapter: Int): Int? {
    var acc = 0
    var idx = -1
    blocks.forEachIndexed { i, block ->
        if (block is DocBlock.Text) {
            if (acc <= inChapter) idx = i
            acc += block.text.length + 1
        }
    }
    return idx.takeIf { it >= 0 }
}

/** EPUB 全书轻量索引：只记录每章起始偏移、标题与总字符数。 */
internal data class BookIndex(
    val chapterStartOffsets: List<Int>,
    val chapterTitles: List<String>,
    val totalChars: Int,
)

internal const val PLAIN_TEXT_CHUNK_CHARS = 3_000

internal data class PlainTextChunk(
    val startOffset: Int,
    val text: String,
)

/**
 * 按接近自然段的位置切分长文本。每个块单独排版，LazyColumn 只保留视口附近的
 * TextLayout，避免数 MB 小说在打开时生成数十万行的单一布局而触发 ANR/OOM。
 */
internal fun chunkPlainText(
    content: String,
    targetChars: Int = PLAIN_TEXT_CHUNK_CHARS,
): List<PlainTextChunk> {
    if (content.isEmpty()) return emptyList()
    val chunks = ArrayList<PlainTextChunk>((content.length / targetChars) + 1)
    var start = 0
    while (start < content.length) {
        var end = (start + targetChars).coerceAtMost(content.length)
        if (end < content.length) {
            val searchEnd = (end + 512).coerceAtMost(content.length)
            val newline = content.indexOf('\n', startIndex = end)
            if (newline in end until searchEnd) end = newline + 1
        }
        if (end <= start) end = (start + targetChars).coerceAtMost(content.length)
        chunks += PlainTextChunk(startOffset = start, text = content.substring(start, end))
        start = end
    }
    return chunks
}

internal fun chunkIndexForOffset(chunks: List<PlainTextChunk>, offset: Int): Int {
    if (chunks.isEmpty()) return 0
    var low = 0
    var high = chunks.lastIndex
    var result = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (chunks[mid].startOffset <= offset) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

internal fun unitIndexForOffset(units: List<ReadingUnit>, offset: Int): Int {
    if (units.isEmpty()) return 0
    var lo = 0
    var hi = units.lastIndex
    var result = 0
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (units[mid].charStart <= offset) {
            result = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return result
}

/**
 * 只使用 EPUB 目录阶段已取得的 ZIP 条目大小构建轻量索引。
 *
 * 旧实现会在打开书籍时逐章解压并抽取全书文本。大书会长时间占用 IO，
 * 多次进出阅读器还可能叠加多个不可及时取消的扫描任务，最终触发 ANR/OOM。
 * ZIP 解压后字节数通常大于可见字符数，适合作为单调递增的定位偏移估算。
 */
internal fun buildBookIndex(book: EpubBook): BookIndex {
    // 公式已抽到 LegacyOffsetCodec 并由单测逐值锁死。这里只保留一处调用：
    // 所有历史高亮/笔记的 locator 都按这套公式算出，线上必须只有一份实现，
    // 否则将来换偏移基准时无从比对。
    val lengths = book.chapters.map { it.estimatedTextLength }
    return BookIndex(
        chapterStartOffsets = LegacyOffsetCodec.chapterStartOffsets(lengths),
        chapterTitles = book.chapters.map { it.title },
        totalChars = LegacyOffsetCodec.totalChars(lengths),
    )
}

// ── Markdown 渲染辅助 ──────────────────────────────────────────────

/** 从行内节点列表提取纯文本。 */
private fun extractInlineText(inlines: List<MdInline>): String = buildString {
    inlines.forEach { extractInlineInto(it, this) }
}

private fun extractInlineInto(inline: MdInline, sb: StringBuilder) {
    when (inline) {
        is MdInline.Text -> sb.append(inline.text)
        is MdInline.Code -> sb.append(inline.text)
        is MdInline.Strong -> inline.children.forEach { extractInlineInto(it, sb) }
        is MdInline.Emphasis -> inline.children.forEach { extractInlineInto(it, sb) }
        is MdInline.Strikethrough -> inline.children.forEach { extractInlineInto(it, sb) }
        is MdInline.Link -> inline.children.forEach { extractInlineInto(it, sb) }
        is MdInline.Image -> sb.append(inline.alt)
        is MdInline.HardLineBreak -> sb.append('\n')
        is MdInline.SoftLineBreak -> sb.append(' ')
    }
}

/** 将行内节点列表构建为带样式的 [AnnotatedString]，含 TTS 句子高亮。 */
private fun buildMarkdownAnnotated(
    inlines: List<MdInline>,
    fontSize: Float,
    paperFg: Color,
    globalOffset: Int = -1,
    chapterBase: Int = 0,
    ttsSentenceRange: Pair<Int, Int>? = null,
    sentenceHighlightBg: Color = Color.Transparent,
): AnnotatedString {
    val text = extractInlineText(inlines)
    return AnnotatedString.Builder(text).apply {
        var pos = 0
        inlines.forEach { inline -> pos = applyInlineStyles(inline, pos, fontSize, paperFg) }
        // TTS 句子高亮叠加
        if (ttsSentenceRange != null && globalOffset >= 0) {
            val localStart = ttsSentenceRange.first - (globalOffset - chapterBase)
            val localEnd = ttsSentenceRange.second - (globalOffset - chapterBase)
            if (localStart < text.length && localEnd > 0 && localEnd > localStart) {
                addStyle(
                    SpanStyle(background = sentenceHighlightBg),
                    localStart.coerceAtLeast(0),
                    localEnd.coerceAtMost(text.length),
                )
            }
        }
    }.toAnnotatedString()
}

/** 递归应用行内样式，返回消费后的文本偏移。 */
private fun AnnotatedString.Builder.applyInlineStyles(
    inline: MdInline,
    start: Int,
    fontSize: Float,
    paperFg: Color,
): Int = when (inline) {
    is MdInline.Text -> start + inline.text.length
    is MdInline.Code -> {
        val end = start + inline.text.length
        addStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = (fontSize * 0.9).sp,
                background = Color.LightGray.copy(alpha = 0.2f),
            ),
            start,
            end,
        )
        end
    }
    is MdInline.Strong -> {
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + extractInlineText(inline.children).length)
        var pos = start
        inline.children.forEach { pos = applyInlineStyles(it, pos, fontSize, paperFg) }
        pos
    }
    is MdInline.Emphasis -> {
        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, start + extractInlineText(inline.children).length)
        var pos = start
        inline.children.forEach { pos = applyInlineStyles(it, pos, fontSize, paperFg) }
        pos
    }
    is MdInline.Strikethrough -> {
        addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, start + extractInlineText(inline.children).length)
        var pos = start
        inline.children.forEach { pos = applyInlineStyles(it, pos, fontSize, paperFg) }
        pos
    }
    is MdInline.Link -> {
        addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, start + extractInlineText(inline.children).length)
        var pos = start
        inline.children.forEach { pos = applyInlineStyles(it, pos, fontSize, paperFg) }
        pos
    }
    is MdInline.Image -> start + inline.alt.length
    is MdInline.HardLineBreak -> start + 1
    is MdInline.SoftLineBreak -> start + 1
}

/** 渲染 [MarkdownParser.MarkdownChapter] 的所有块。 */
@Composable
fun RenderMarkdownChapter(
    chapter: MarkdownParser.MarkdownChapter,
    fontSize: Float,
    lineHeight: Float,
    paperFg: Color,
    blockGlobalOffset: Int = -1,
    chapterBase: Int = 0,
    ttsSentenceRange: Pair<Int, Int>? = null,
    sentenceHighlightBg: Color = Color.Transparent,
    onSelectBlock: ((String, Int) -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        chapter.blocks.forEach { block ->
            RenderMarkdownBlock(
                block, fontSize, lineHeight, paperFg,
                blockGlobalOffset, chapterBase, ttsSentenceRange, sentenceHighlightBg, onSelectBlock,
            )
        }
    }
}

@Composable
private fun RenderMarkdownBlock(
    block: MarkdownBlock,
    fontSize: Float,
    lineHeight: Float,
    paperFg: Color,
    blockGlobalOffset: Int = -1,
    chapterBase: Int = 0,
    ttsSentenceRange: Pair<Int, Int>? = null,
    sentenceHighlightBg: Color = Color.Transparent,
    onSelectBlock: ((String, Int) -> Unit)? = null,
) {
    val gOff = if (blockGlobalOffset >= 0) blockGlobalOffset + block.canonicalRange.start else -1
    when (block) {
        is MarkdownBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.5f; 2 -> 1.3f; 3 -> 1.15f; else -> 1.05f
            }
            val annotated = buildMarkdownAnnotated(
                block.inlines, fontSize, paperFg, gOff, chapterBase, ttsSentenceRange, sentenceHighlightBg,
            )
            Text(
                text = annotated,
                fontSize = (fontSize * scale).sp,
                fontWeight = FontWeight.Bold,
                color = paperFg,
                lineHeight = (fontSize * scale * lineHeight).sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .then(if (onSelectBlock != null) Modifier.clickable { onSelectBlock(extractInlineText(block.inlines), gOff) } else Modifier),
            )
        }
        is MarkdownBlock.Paragraph -> {
            val annotated = buildMarkdownAnnotated(
                block.inlines, fontSize, paperFg, gOff, chapterBase, ttsSentenceRange, sentenceHighlightBg,
            )
            Text(
                text = annotated,
                fontSize = fontSize.sp,
                color = paperFg,
                lineHeight = (fontSize * lineHeight).sp,
                modifier = Modifier.fillMaxWidth()
                    .then(if (onSelectBlock != null) Modifier.clickable { onSelectBlock(extractInlineText(block.inlines), gOff) } else Modifier),
            )
        }
        is MarkdownBlock.FencedCodeBlock -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.LightGray.copy(alpha = 0.15f))
                    .padding(8.dp)
                    .then(if (onSelectBlock != null) Modifier.clickable { onSelectBlock(block.content, gOff) } else Modifier),
            ) {
                Text(
                    text = block.content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (fontSize * 0.85).sp,
                    color = paperFg,
                    lineHeight = (fontSize * 0.85 * lineHeight).sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        is MarkdownBlock.IndentedCodeBlock -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.LightGray.copy(alpha = 0.15f))
                    .padding(8.dp)
                    .then(if (onSelectBlock != null) Modifier.clickable { onSelectBlock(block.content, gOff) } else Modifier),
            ) {
                Text(
                    text = block.content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (fontSize * 0.85).sp,
                    color = paperFg,
                    lineHeight = (fontSize * 0.85 * lineHeight).sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        is MarkdownBlock.HorizontalRule -> {
            Divider(
                color = paperFg.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        is MarkdownBlock.BlockQuote -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
            ) {
                block.blocks.forEach {
                    RenderMarkdownBlock(it, fontSize, lineHeight, paperFg, blockGlobalOffset, chapterBase, ttsSentenceRange, sentenceHighlightBg, onSelectBlock)
                }
            }
        }
        is MarkdownBlock.UnorderedList -> {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
                block.items.forEach { item ->
                    item.forEach {
                        RenderMarkdownBlock(it, fontSize, lineHeight, paperFg, blockGlobalOffset, chapterBase, ttsSentenceRange, sentenceHighlightBg, onSelectBlock)
                    }
                }
            }
        }
        is MarkdownBlock.OrderedList -> {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
                block.items.forEachIndexed { _, item ->
                    item.forEach {
                        RenderMarkdownBlock(it, fontSize, lineHeight, paperFg, blockGlobalOffset, chapterBase, ttsSentenceRange, sentenceHighlightBg, onSelectBlock)
                    }
                }
            }
        }
        is MarkdownBlock.TaskList -> {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
                block.items.forEach { taskItem ->
                    taskItem.blocks.forEach {
                        RenderMarkdownBlock(it, fontSize, lineHeight, paperFg, blockGlobalOffset, chapterBase, ttsSentenceRange, sentenceHighlightBg, onSelectBlock)
                    }
                }
            }
        }
        is MarkdownBlock.Table -> {
            val tableScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(tableScroll)
                    .then(if (onSelectBlock != null) Modifier.clickable { onSelectBlock("[Table]", gOff) } else Modifier),
            ) {
                // 表头
                if (block.header.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        block.header.forEach { cell ->
                            TableCellContent(cell, fontSize, lineHeight, paperFg, bold = true)
                        }
                    }
                    Divider(color = paperFg.copy(alpha = 0.3f))
                }
                // 数据行
                block.rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { cell ->
                            TableCellContent(cell, fontSize, lineHeight, paperFg, bold = false)
                        }
                    }
                    Divider(color = paperFg.copy(alpha = 0.12f))
                }
            }
        }
    }
}

@Composable
private fun TableCellContent(
    cell: MarkdownBlock.Table.TableCell,
    fontSize: Float,
    lineHeight: Float,
    paperFg: Color,
    bold: Boolean,
) {
    val textAlign = when (cell.alignment) {
        MarkdownBlock.Table.TableAlignment.LEFT -> TextAlign.Left
        MarkdownBlock.Table.TableAlignment.CENTER -> TextAlign.Center
        MarkdownBlock.Table.TableAlignment.RIGHT -> TextAlign.Right
        MarkdownBlock.Table.TableAlignment.NONE -> TextAlign.Left
    }
    val cellText = extractInlineText(cell.inlines)
    Text(
        text = cellText,
        fontSize = (fontSize * 0.85).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = textAlign,
        color = paperFg,
        lineHeight = (fontSize * 0.85 * lineHeight).sp,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
