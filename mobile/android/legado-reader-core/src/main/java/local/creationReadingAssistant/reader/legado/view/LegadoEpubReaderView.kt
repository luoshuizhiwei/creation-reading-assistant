/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.reader.legado.view

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Html
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.text.HtmlCompat
import local.creationReadingAssistant.reader.legado.epub.EpubReaderDocument
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

    var menuListener: ReaderMenuListener? = null
    var locationListener: ReaderLocationListener? = null
    var selectionListener: ReaderSelectionListener? = null

    init {
        isClickable = true
        isFocusable = true
        applyPaintSettings()
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
    }

    fun updateSettings(settings: NativeReaderSettings) {
        val locator = currentLocator()
        this.settings = settings
        applyPaintSettings()
        rebuildChapter(locator.chapterIndex, locator.charOffset.toInt())
    }

    fun currentSettings(): NativeReaderSettings = settings

    fun currentChapterTitle(): String? = document?.chapters?.getOrNull(chapterIndex)?.title

    fun goToChapter(targetChapterIndex: Int, localOffset: Int = 0): Boolean {
        val book = document ?: return false
        if (book.chapters.isEmpty()) return false
        rebuildChapter(targetChapterIndex.coerceIn(0, book.chapters.lastIndex), localOffset.coerceAtLeast(0))
        absolutePageIndex = chapterIndex
        notifyLocation()
        invalidate()
        return true
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
        } else if (chapterIndex < book.chapters.lastIndex) {
            rebuildChapter(chapterIndex + 1, 0)
        } else {
            return false
        }
        absolutePageIndex += 1
        notifyLocation()
        invalidate()
        return true
    }

    fun goPrevious(): Boolean {
        if (pageIndexInChapter > 0) {
            pageIndexInChapter -= 1
        } else if (chapterIndex > 0) {
            rebuildChapter(chapterIndex - 1, Int.MAX_VALUE)
            pageIndexInChapter = pageStartYs.lastIndex.coerceAtLeast(0)
        } else {
            return false
        }
        absolutePageIndex = (absolutePageIndex - 1).coerceAtLeast(0)
        notifyLocation()
        invalidate()
        return true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0 && (width != oldWidth || height != oldHeight)) {
            val locator = currentLocator()
            rebuildChapter(locator.chapterIndex, locator.charOffset.toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val palette = paletteFor(settings.theme)
        canvas.drawColor(palette.background)
        val layout = chapterLayout ?: return
        val horizontalPadding = settings.horizontalPaddingDp * density
        val verticalPadding = settings.verticalPaddingDp * density
        val startY = pageStartYs.getOrElse(pageIndexInChapter) { 0 }
        val pageHeight = contentHeightPx()
        val nextPageStart = pageStartYs.getOrNull(pageIndexInChapter + 1)
        val visiblePageHeight = nextPageStart
            ?.let { (it - startY).coerceIn(1, pageHeight) }
            ?: pageHeight
        canvas.save()
        canvas.clipRect(
            horizontalPadding,
            verticalPadding,
            width - horizontalPadding,
            verticalPadding + visiblePageHeight,
        )
        canvas.translate(horizontalPadding, verticalPadding - startY)
        layout.draw(canvas)
        canvas.restore()

        val locator = currentLocator()
        infoPaint.color = palette.secondary
        infoPaint.textSize = sp(11f)
        val title = currentChapterTitle().orEmpty().take(24)
        canvas.drawText(title, horizontalPadding, height - 10f * density, infoPaint)
        val progressText = String.format("%.1f%%", locator.progressPercent)
        canvas.drawText(
            progressText,
            width - horizontalPadding - infoPaint.measureText(progressText),
            height - 10f * density,
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

    private fun rebuildChapter(targetChapterIndex: Int, localOffset: Int) {
        val book = document ?: return
        if (width <= 0 || height <= 0 || book.chapters.isEmpty()) {
            invalidate()
            return
        }
        chapterIndex = targetChapterIndex.coerceIn(0, book.chapters.lastIndex)
        val chapter = book.chapters[chapterIndex]
        chapterLayout = null
        chapterText = null
        val contentWidth = max(1, (width - settings.horizontalPaddingDp * density * 2).toInt())
        val chapterContent = runCatching { book.chapterContent(chapterIndex) }.getOrElse {
            local.creationReadingAssistant.reader.legado.epub.EpubChapterContent(
                text = "本章节解析失败，请尝试重新导入 EPUB。",
                html = "<p>本章节解析失败，请尝试重新导入 EPUB。</p>",
                containsImages = false,
            )
        }
        chapterText = HtmlCompat.fromHtml(
            chapterContent.html,
            HtmlCompat.FROM_HTML_MODE_COMPACT,
            Html.ImageGetter { source -> imageDrawable(book, chapter.href, source, contentWidth) },
            null,
        )
        val text = chapterText ?: return
        chapterLayout = buildStaticLayout(text, contentWidth)
        pageStartYs = calculatePageStarts(chapterLayout ?: return)
        val targetOffset = localOffset.coerceIn(0, text.length)
        val targetLine = chapterLayout?.getLineForOffset(targetOffset) ?: 0
        val targetY = chapterLayout?.getLineTop(targetLine) ?: 0
        pageIndexInChapter = pageStartYs.indexOfLast { it <= targetY }.coerceAtLeast(0)
        book.prefetchAround(chapterIndex)
        notifyLocation()
        invalidate()
    }

    private fun buildStaticLayout(text: Spanned, contentWidth: Int): StaticLayout {
        val spacingAdd = settings.paragraphSpacingDp * density * 0.15f
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(spacingAdd, settings.lineSpacingMultiplier)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                textPaint,
                contentWidth,
                Layout.Alignment.ALIGN_NORMAL,
                settings.lineSpacingMultiplier,
                spacingAdd,
                false,
            )
        }
    }

    private fun calculatePageStarts(layout: StaticLayout): List<Int> {
        if (layout.lineCount <= 0) return listOf(0)
        val result = arrayListOf(0)
        val pageHeight = contentHeightPx()
        var startY = 0
        var guard = 0
        while (startY + pageHeight < layout.height && guard < 100_000) {
            val probeLine = layout.getLineForVertical(startY + pageHeight)
            var nextY = layout.getLineTop(probeLine)
            if (nextY <= startY) nextY = layout.getLineBottom(probeLine)
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
    ): Drawable {
        val maxHeight = max(1, (contentHeightPx() * 0.72f).toInt())
        val imageSource = source ?: return transparentDrawable()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            book.openImageStream(chapterHref, imageSource)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return transparentDrawable()

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
        }.getOrNull() ?: return transparentDrawable()
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

    private fun transparentDrawable(): Drawable = ColorDrawable(Color.TRANSPARENT).apply {
        setBounds(0, 0, 1, 1)
    }

    private fun sentenceAt(x: Float, y: Float): TextSelection {
        val layout = chapterLayout ?: return TextSelection.EMPTY
        val text = chapterText ?: return TextSelection.EMPTY
        val horizontalPadding = settings.horizontalPaddingDp * density
        val verticalPadding = settings.verticalPaddingDp * density
        val startY = pageStartYs.getOrElse(pageIndexInChapter) { 0 }
        val localY = (startY + y - verticalPadding).toInt().coerceIn(0, layout.height.coerceAtLeast(1))
        val line = layout.getLineForVertical(localY)
        val localX = (x - horizontalPadding).coerceAtLeast(0f)
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

    private fun contentHeightPx(): Int = max(
        1,
        (height - settings.verticalPaddingDp * density * 2 - 18f * density).toInt(),
    )

    private fun applyPaintSettings() {
        textPaint.textSize = sp(settings.fontSizeSp)
        textPaint.isFakeBoldText = settings.textBold
        textPaint.color = paletteFor(settings.theme).text
        infoPaint.typeface = textPaint.typeface
    }

    private fun notifyLocation() {
        locationListener?.onLocationChanged(currentLocator())
    }

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics,
    )

    private data class Palette(val background: Int, val text: Int, val secondary: Int)

    private fun paletteFor(theme: ReaderTheme): Palette = when (theme) {
        ReaderTheme.WHITE -> Palette(Color.rgb(250, 250, 248), Color.rgb(32, 31, 29), Color.rgb(112, 108, 101))
        ReaderTheme.WARM -> Palette(Color.rgb(246, 236, 217), Color.rgb(43, 35, 29), Color.rgb(123, 101, 81))
        ReaderTheme.GREEN -> Palette(Color.rgb(220, 234, 215), Color.rgb(34, 47, 34), Color.rgb(85, 108, 84))
        ReaderTheme.NIGHT -> Palette(Color.rgb(20, 22, 24), Color.rgb(207, 210, 212), Color.rgb(132, 139, 143))
    }
}
