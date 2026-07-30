/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.reader.legado.view

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Html
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextUtils
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.text.HtmlCompat
import local.creationReadingAssistant.reader.legado.epub.EpubReaderDocument
import local.creationReadingAssistant.reader.legado.layout.ReaderPageGeometry
import local.creationReadingAssistant.reader.legado.layout.ReaderPageGeometryResolver
import local.creationReadingAssistant.reader.legado.model.NativeReaderSettings
import local.creationReadingAssistant.reader.legado.model.ReaderLocator
import local.creationReadingAssistant.reader.legado.model.ReaderTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Native EPUB surface backed by Legado's EPUB parser and Android StaticLayout.
 * It paginates one spine chapter at a time, so large books never create one
 * book-sized layout or WebView DOM.
 */
class LegadoEpubReaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val infoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var settings = NativeReaderSettings()
    private var document: EpubReaderDocument? = null
    private var chapterIndex = 0
    private var chapterText: Spanned? = null
    private var chapterLayout: StaticLayout? = null
    private var pageStartYs = listOf(0)
    private var pageIndexInChapter = 0
    private var absolutePageIndex = 0
    private var requestedChapterIndex = 0
    private var requestedCharOffset = 0
    private var requestedPageIndex = 0
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var insetLeft = 0
    private var insetTop = 0
    private var insetRight = 0
    private var insetBottom = 0

    var menuListener: ReaderMenuListener? = null
    var locationListener: ReaderLocationListener? = null
    var selectionListener: ReaderSelectionListener? = null

    init {
        isClickable = true
        isFocusable = true
        applyPaintSettings()
        setBackgroundColor(backgroundColor())
    }

    @JvmOverloads
    fun setDocument(
        document: EpubReaderDocument,
        startChapterIndex: Int = 0,
        startCharOffset: Int = 0,
        startPageIndex: Int = 0,
    ) {
        this.document = document
        requestedChapterIndex = startChapterIndex.coerceIn(0, max(0, document.chapters.lastIndex))
        requestedCharOffset = startCharOffset.coerceAtLeast(0)
        requestedPageIndex = startPageIndex.coerceAtLeast(0)
        absolutePageIndex = requestedPageIndex
        rebuildChapter(requestedChapterIndex, requestedCharOffset)
        if (chapterLayout == null && (width <= 0 || height <= 0)) {
            post { rebuildChapter(requestedChapterIndex, requestedCharOffset) }
        }
    }

    fun updateSettings(settings: NativeReaderSettings) {
        val locator = currentLocator()
        this.settings = settings
        applyPaintSettings()
        setBackgroundColor(backgroundColor())
        rebuildChapter(locator.chapterIndex, locator.charOffset.toInt())
    }

    fun setSystemInsets(left: Int, top: Int, right: Int, bottom: Int) {
        if (insetLeft == left && insetTop == top && insetRight == right && insetBottom == bottom) return
        val locator = currentLocator()
        insetLeft = left.coerceAtLeast(0)
        insetTop = top.coerceAtLeast(0)
        insetRight = right.coerceAtLeast(0)
        insetBottom = bottom.coerceAtLeast(0)
        rebuildChapter(locator.chapterIndex, locator.charOffset.toInt())
    }

    fun currentSettings(): NativeReaderSettings = settings

    fun backgroundColor(): Int = paletteFor(settings.theme).background

    fun currentChapterTitle(): String? = document?.chapters?.getOrNull(chapterIndex)?.title

    fun goToChapter(targetChapterIndex: Int, localOffset: Int = 0): Boolean {
        val book = document ?: return false
        if (book.chapters.isEmpty()) return false
        absolutePageIndex = targetChapterIndex.coerceIn(0, book.chapters.lastIndex)
        return rebuildChapter(absolutePageIndex, localOffset.coerceAtLeast(0))
    }

    fun currentLocator(): ReaderLocator {
        val book = document ?: return ReaderLocator()
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return ReaderLocator()
        val layout = chapterLayout
        val startY = pageStartYs.getOrElse(pageIndexInChapter) { 0 }
        val line = layout?.getLineForVertical(startY) ?: 0
        val localOffset = layout?.getLineStart(line) ?: 0
        val chapterFraction = if (pageStartYs.isEmpty()) 0.0 else {
            pageIndexInChapter.toDouble() / pageStartYs.size.toDouble()
        }
        val progress = if (
            chapterIndex == book.chapters.lastIndex &&
            pageIndexInChapter == pageStartYs.lastIndex
        ) {
            100.0
        } else {
            (chapterIndex.toDouble() + chapterFraction) /
                book.chapters.size.coerceAtLeast(1).toDouble() * 100.0
        }
        return ReaderLocator(
            chapterIndex = chapter.index,
            charOffset = localOffset.toLong(),
            pageIndex = absolutePageIndex,
            progressPercent = progress.coerceIn(0.0, 100.0),
            epubHref = chapter.href,
        )
    }

    fun goNext(): Boolean {
        val book = document ?: return false
        if (pageIndexInChapter + 1 < pageStartYs.size) {
            pageIndexInChapter += 1
            absolutePageIndex += 1
            notifyLocation()
            invalidate()
            return true
        } else if (chapterIndex < book.chapters.lastIndex) {
            val previousAbsolutePageIndex = absolutePageIndex
            absolutePageIndex += 1
            if (rebuildChapter(chapterIndex + 1, 0)) return true
            absolutePageIndex = previousAbsolutePageIndex
            return false
        } else {
            return false
        }
    }

    fun goPrevious(): Boolean {
        if (pageIndexInChapter > 0) {
            pageIndexInChapter -= 1
            absolutePageIndex = (absolutePageIndex - 1).coerceAtLeast(0)
            notifyLocation()
            invalidate()
            return true
        } else if (chapterIndex > 0) {
            val previousAbsolutePageIndex = absolutePageIndex
            absolutePageIndex = (absolutePageIndex - 1).coerceAtLeast(0)
            if (rebuildChapter(chapterIndex - 1, Int.MAX_VALUE)) return true
            absolutePageIndex = previousAbsolutePageIndex
            return false
        } else {
            return false
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0 && (width != oldWidth || height != oldHeight || chapterLayout == null)) {
            val locator = currentLocator()
            rebuildChapter(locator.chapterIndex, locator.charOffset.toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val palette = paletteFor(settings.theme)
        canvas.drawColor(palette.background)
        val layout = chapterLayout
        if (layout == null) {
            infoPaint.color = palette.secondary
            infoPaint.textSize = 13f * density
            val message = "正在排版…"
            canvas.drawText(message, (width - infoPaint.measureText(message)) / 2f, height * 0.5f, infoPaint)
            return
        }
        val geometry = pageGeometry()
        val startY = pageStartYs.getOrElse(pageIndexInChapter) { 0 }
        val pageHeight = geometry.contentHeightPx
        val nextPageStart = pageStartYs.getOrNull(pageIndexInChapter + 1)
        val visiblePageHeight = nextPageStart
            ?.let { (it - startY).coerceIn(1, pageHeight) }
            ?: pageHeight
        canvas.save()
        canvas.clipRect(
            geometry.contentLeftPx,
            geometry.contentTopPx,
            geometry.contentRightPx,
            geometry.contentTopPx + visiblePageHeight,
        )
        canvas.translate(geometry.contentLeftPx, geometry.contentTopPx - startY)
        layout.draw(canvas)
        canvas.restore()

        val locator = currentLocator()
        infoPaint.color = palette.secondary
        infoPaint.textSize = geometry.infoTextSizePx
        val progressText = String.format("%.1f%%", locator.progressPercent)
        val progressWidth = infoPaint.measureText(progressText)
        val titleWidth = (geometry.contentWidthPx - progressWidth - 18f * density).coerceAtLeast(1f)
        val title = TextUtils.ellipsize(currentChapterTitle().orEmpty(), infoPaint, titleWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(title, geometry.contentLeftPx, geometry.footerBaselinePx, infoPaint)
        canvas.drawText(
            progressText,
            geometry.contentRightPx - progressWidth,
            geometry.footerBaselinePx,
            infoPaint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downAt = event.eventTime
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val dx = event.x - downX
                val dy = event.y - downY
                val elapsed = event.eventTime - downAt
                val threshold = 42f * density
                if (elapsed >= 600 && abs(dx) < threshold && abs(dy) < threshold) {
                    val selected = sentenceAt(event.x, event.y)
                    if (selected.text.isNotBlank()) {
                        selectionListener?.onTextSelected(
                            selected.text,
                            currentLocator().copy(charOffset = selected.startOffset.toLong()),
                        )
                    }
                    performClick()
                    return true
                }
                if (elapsed <= 700 && abs(dx) >= threshold && abs(dx) > abs(dy) * 1.2f) {
                    if (dx < 0) goNext() else goPrevious()
                    return true
                }
                if (abs(dx) < threshold && abs(dy) < threshold) {
                    when {
                        event.x < width * 0.30f -> goPrevious()
                        event.x > width * 0.70f -> goNext()
                        else -> menuListener?.onToggleRequested()
                    }
                    performClick()
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun rebuildChapter(targetChapterIndex: Int, localOffset: Int): Boolean {
        val book = document ?: return false
        if (width <= 0 || height <= 0 || book.chapters.isEmpty()) {
            invalidate()
            return false
        }
        chapterIndex = targetChapterIndex.coerceIn(0, book.chapters.lastIndex)
        val chapter = book.chapters[chapterIndex]
        chapterLayout = null
        chapterText = null
        val geometry = pageGeometry()
        textPaint.textSize = geometry.bodyTextSizePx
        val contentWidth = geometry.contentWidthPx
        val chapterContent = runCatching { book.chapterContent(chapterIndex) }.getOrElse {
            local.creationReadingAssistant.reader.legado.epub.EpubChapterContent(
                text = "本章节解析失败，请尝试重新导入 EPUB。",
                html = "<p>本章节解析失败，请尝试重新导入 EPUB。</p>",
                containsImages = false,
            )
        }
        chapterText = normalizeChapterTypography(
            HtmlCompat.fromHtml(
                chapterContent.html,
                HtmlCompat.FROM_HTML_MODE_COMPACT,
                Html.ImageGetter { source -> imageDrawable(book, chapter.href, source, contentWidth) },
                null,
            ),
        )
        val text = chapterText ?: return false
        chapterLayout = buildStaticLayout(text, contentWidth)
        pageStartYs = calculatePageStarts(chapterLayout ?: return false)
        val targetOffset = localOffset.coerceIn(0, text.length)
        val targetLine = chapterLayout?.getLineForOffset(targetOffset) ?: 0
        val targetY = chapterLayout?.getLineTop(targetLine) ?: 0
        pageIndexInChapter = pageStartYs.indexOfLast { it <= targetY }.coerceAtLeast(0)
        book.prefetchAround(chapterIndex)
        notifyLocation()
        invalidate()
        return true
    }

    private fun buildStaticLayout(text: Spanned, contentWidth: Int): StaticLayout {
        val geometry = pageGeometry()
        val fontMetricsHeight = (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent).coerceAtLeast(1f)
        val spacingMultiplier = (geometry.lineAdvancePx / fontMetricsHeight).coerceAtLeast(1f)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(0f, spacingMultiplier)
                // HIGH_QUALITY re-optimizes whole paragraphs and is visibly
                // expensive at chapter boundaries. SIMPLE is deterministic
                // and much better suited to interactive novel pagination.
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                textPaint,
                contentWidth,
                Layout.Alignment.ALIGN_NORMAL,
                spacingMultiplier,
                0f,
                false,
            )
        }
    }

    private fun calculatePageStarts(layout: StaticLayout): List<Int> {
        if (layout.lineCount <= 0) return listOf(0)
        val result = arrayListOf(0)
        val pageHeight = pageGeometry().contentHeightPx
        var startY = 0
        var guard = 0
        while (startY + pageHeight < layout.height && guard < 100_000) {
            val probeLine = layout.getLineForVertical(startY + pageHeight)
            val lineTop = layout.getLineTop(probeLine)
            val lineBottom = layout.getLineBottom(probeLine)
            val nextY = when {
                lineBottom > startY + pageHeight -> lineBottom
                lineTop <= startY -> lineBottom
                else -> lineTop
            }
            if (nextY <= startY) break
            result += nextY
            startY = nextY
            guard += 1
        }
        return result
    }

    private fun imageDrawable(
        book: EpubReaderDocument,
        chapterHref: String,
        source: String?,
        contentWidth: Int,
    ): Drawable? {
        val maxHeight = max(1, (pageGeometry().contentHeightPx * 0.72f).toInt())
        val imageSource = source ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            book.openImageStream(chapterHref, imageSource)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= contentWidth ||
            bounds.outHeight / (sampleSize * 2) >= maxHeight
        ) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = runCatching {
            book.openImageStream(chapterHref, imageSource)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        }.getOrNull() ?: return null
        val scale = min(
            1f,
            min(contentWidth.toFloat() / bitmap.width.coerceAtLeast(1), maxHeight.toFloat() / bitmap.height.coerceAtLeast(1)),
        )
        return BitmapDrawable(resources, bitmap).apply {
            setBounds(
                0,
                0,
                max(1, (bitmap.width * scale).toInt()),
                max(1, (bitmap.height * scale).toInt()),
            )
        }
    }

    private fun normalizeChapterTypography(source: Spanned): Spanned {
        val text = SpannableStringBuilder(source)
        while (text.isNotEmpty() && text.first().isWhitespace()) text.delete(0, 1)
        while (text.isNotEmpty() && text.last().isWhitespace()) text.delete(text.length - 1, text.length)
        text.getSpans(0, text.length, RelativeSizeSpan::class.java).forEach { span ->
            val size = span.sizeChange
            if (size > 1.55f) {
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                val flags = text.getSpanFlags(span)
                text.removeSpan(span)
                text.setSpan(RelativeSizeSpan(1.55f), start, end, flags)
            }
        }
        val bodyTextSizePx = pageGeometry().bodyTextSizePx
        text.getSpans(0, text.length, AbsoluteSizeSpan::class.java).forEach { span ->
            val spanSize = span.size.toFloat()
            if (spanSize > bodyTextSizePx * 1.55f) {
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                val flags = text.getSpanFlags(span)
                text.removeSpan(span)
                text.setSpan(RelativeSizeSpan(1.55f), start, end, flags)
                text.setSpan(StyleSpan(Typeface.BOLD), start, end, flags)
            }
        }
        // 中文排版优化：为每个段落首行增加 2em 缩进，与 TXT 保持一致。
        if (settings.chineseTypography) {
            applyFirstLineIndent(text, (bodyTextSizePx * 2f).toInt())
        }
        return text
    }

    private fun applyFirstLineIndent(text: SpannableStringBuilder, indentPx: Int) {
        if (indentPx <= 0 || text.isEmpty()) return
        var index = 0
        while (index < text.length) {
            val paragraphStart = index
            while (index < text.length && text[index] != '\n') index++
            val paragraphEnd = index
            val firstNonWhitespace = (paragraphStart until paragraphEnd).firstOrNull { !text[it].isWhitespace() } ?: paragraphStart
            if (firstNonWhitespace < paragraphEnd) {
                text.setSpan(
                    LeadingMarginSpan.Standard(indentPx, 0),
                    firstNonWhitespace,
                    paragraphEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (index < text.length && text[index] == '\n') index++
        }
    }

    private fun sentenceAt(x: Float, y: Float): TextSelection {
        val layout = chapterLayout ?: return TextSelection.EMPTY
        val text = chapterText ?: return TextSelection.EMPTY
        val geometry = pageGeometry()
        val startY = pageStartYs.getOrElse(pageIndexInChapter) { 0 }
        val localY = (startY + y - geometry.contentTopPx).toInt().coerceIn(0, layout.height.coerceAtLeast(1))
        val line = layout.getLineForVertical(localY)
        val localX = (x - geometry.contentLeftPx).coerceAtLeast(0f)
        val offset = layout.getOffsetForHorizontal(line, localX).coerceIn(0, text.length)
        val separators = "。！？!?；;\n"
        var start = offset
        while (start > 0 && text[start - 1] !in separators) start -= 1
        var end = offset
        while (end < text.length && text[end] !in separators) end += 1
        if (end < text.length) end += 1
        return TextSelection(
            text = text.subSequence(start, end).toString().trim().take(800),
            startOffset = start,
        )
    }

    private data class TextSelection(val text: String, val startOffset: Int) {
        companion object {
            val EMPTY = TextSelection("", 0)
        }
    }

    private fun applyPaintSettings() {
        textPaint.textSize = settings.fontSizeSp.coerceIn(14f, 34f) * density
        textPaint.isFakeBoldText = settings.textBold
        textPaint.color = paletteFor(settings.theme).text
        infoPaint.typeface = textPaint.typeface
    }

    private fun notifyLocation() {
        locationListener?.onLocationChanged(currentLocator())
    }

    private fun pageGeometry(): ReaderPageGeometry = ReaderPageGeometryResolver.resolve(
        widthPx = width,
        heightPx = height,
        density = density,
        settings = settings,
        insetLeftPx = insetLeft,
        insetTopPx = insetTop,
        insetRightPx = insetRight,
        insetBottomPx = insetBottom,
    )

    private data class Palette(val background: Int, val text: Int, val secondary: Int)

    private fun paletteFor(theme: ReaderTheme): Palette = when (theme) {
        ReaderTheme.WHITE -> Palette(Color.rgb(250, 250, 248), Color.rgb(32, 31, 29), Color.rgb(112, 108, 101))
        ReaderTheme.WARM -> Palette(Color.rgb(246, 236, 217), Color.rgb(43, 35, 29), Color.rgb(123, 101, 81))
        ReaderTheme.GREEN -> Palette(Color.rgb(220, 234, 215), Color.rgb(34, 47, 34), Color.rgb(85, 108, 84))
        ReaderTheme.NIGHT -> Palette(Color.rgb(20, 22, 24), Color.rgb(207, 210, 212), Color.rgb(132, 139, 143))
    }
}
