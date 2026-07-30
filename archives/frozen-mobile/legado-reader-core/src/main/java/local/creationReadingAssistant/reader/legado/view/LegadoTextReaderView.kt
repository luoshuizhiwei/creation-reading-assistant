/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package local.creationReadingAssistant.reader.legado.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextUtils
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import local.creationReadingAssistant.reader.legado.model.NativeReaderSettings
import local.creationReadingAssistant.reader.legado.model.ReaderLocator
import local.creationReadingAssistant.reader.legado.model.ReaderTheme
import local.creationReadingAssistant.reader.legado.layout.ReaderPageGeometry
import local.creationReadingAssistant.reader.legado.layout.ReaderPageGeometryResolver
import local.creationReadingAssistant.reader.legado.text.TextPage
import local.creationReadingAssistant.reader.legado.text.TextPaginationConfig
import local.creationReadingAssistant.reader.legado.text.TextPaginator
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max

fun interface ReaderMenuListener {
    fun onToggleRequested()
}

fun interface ReaderLocationListener {
    fun onLocationChanged(locator: ReaderLocator)
}

fun interface ReaderSelectionListener {
    fun onTextSelected(text: String, locator: ReaderLocator)
}

/** Native Canvas text reader with deterministic offset-based pagination. */
class LegadoTextReaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val infoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var settings = NativeReaderSettings()
    private var content = ""
    private var title = ""
    private var requestedStartOffset = 0
    private var paginator: TextPaginator? = null
    private var currentPage = TextPage(0, 0, emptyList())
    private var currentPageIndex = 0
    private val previousPageStarts = ArrayDeque<Int>()
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
    fun setDocument(title: String, content: String, startOffset: Int = 0, startPageIndex: Int = 0) {
        this.title = title
        this.content = content
        requestedStartOffset = startOffset.coerceIn(0, content.length)
        currentPageIndex = startPageIndex.coerceAtLeast(0)
        previousPageStarts.clear()
        rebuildPaginator(requestedStartOffset)
    }

    fun updateSettings(settings: NativeReaderSettings) {
        val anchor = currentPage.startOffset
        this.settings = settings
        applyPaintSettings()
        setBackgroundColor(backgroundColor())
        previousPageStarts.clear()
        rebuildPaginator(anchor)
    }

    fun setSystemInsets(left: Int, top: Int, right: Int, bottom: Int) {
        if (insetLeft == left && insetTop == top && insetRight == right && insetBottom == bottom) return
        val anchor = if (currentPage.isEmpty) requestedStartOffset else currentPage.startOffset
        insetLeft = left.coerceAtLeast(0)
        insetTop = top.coerceAtLeast(0)
        insetRight = right.coerceAtLeast(0)
        insetBottom = bottom.coerceAtLeast(0)
        rebuildPaginator(anchor)
    }

    fun currentLocator(): ReaderLocator {
        val length = content.length.coerceAtLeast(1)
        val progress = if (content.isNotEmpty() && currentPage.endOffset >= content.length) {
            100.0
        } else {
            currentPage.startOffset.toDouble() / length.toDouble() * 100.0
        }
        return ReaderLocator(
            charOffset = currentPage.startOffset.toLong(),
            pageIndex = currentPageIndex,
            progressPercent = progress.coerceIn(0.0, 100.0),
        )
    }

    fun currentSettings(): NativeReaderSettings = settings

    fun goToOffset(offset: Int): Boolean {
        if (content.isEmpty()) return false
        previousPageStarts.clear()
        rebuildPaginator(offset.coerceIn(0, content.length))
        currentPageIndex = (offset.toDouble() / content.length.coerceAtLeast(1).toDouble() * 1000.0).toInt()
        notifyLocation()
        return true
    }

    fun backgroundColor(): Int = paletteFor(settings.theme).background

    fun primaryTextColor(): Int = paletteFor(settings.theme).text

    fun secondaryTextColor(): Int = paletteFor(settings.theme).secondary

    fun goNext(): Boolean {
        val nextStart = currentPage.endOffset
        if (content.isEmpty() || nextStart <= currentPage.startOffset || nextStart >= content.length) return false
        previousPageStarts.addLast(currentPage.startOffset)
        currentPage = paginator?.pageStartingAt(nextStart) ?: return false
        currentPageIndex += 1
        notifyLocation()
        invalidate()
        return true
    }

    fun goPrevious(): Boolean {
        val target = if (previousPageStarts.isNotEmpty()) {
            previousPageStarts.removeLast()
        } else {
            val estimated = max(64, currentPage.endOffset - currentPage.startOffset)
            paginator?.previousPageBefore(currentPage.startOffset, estimated)?.startOffset ?: return false
        }
        currentPage = paginator?.pageStartingAt(target) ?: return false
        currentPageIndex = (currentPageIndex - 1).coerceAtLeast(0)
        notifyLocation()
        invalidate()
        return true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0 && (width != oldWidth || height != oldHeight)) {
            rebuildPaginator(if (currentPage.isEmpty) requestedStartOffset else currentPage.startOffset)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val palette = paletteFor(settings.theme)
        canvas.drawColor(palette.background)
        if (content.isEmpty()) return

        val geometry = pageGeometry()
        textPaint.textSize = geometry.bodyTextSizePx
        val metrics = textPaint.fontMetrics
        val baseLineOffset = -metrics.ascent
        var baseline = geometry.contentTopPx + baseLineOffset
        textPaint.color = palette.text
        val contentClip = canvas.save()
        canvas.clipRect(
            geometry.contentLeftPx,
            geometry.contentTopPx,
            geometry.contentRightPx,
            geometry.contentBottomPx,
        )
        for (line in currentPage.lines) {
            canvas.drawText(line.text, geometry.contentLeftPx + line.indentPx, baseline, textPaint)
            baseline += geometry.lineAdvancePx
            if (line.paragraphEnd) baseline += geometry.paragraphSpacingPx
        }
        canvas.restoreToCount(contentClip)

        infoPaint.color = palette.secondary
        infoPaint.textSize = geometry.infoTextSizePx
        val progress = currentLocator().progressPercent
        val progressText = String.format("%.1f%%", progress)
        val progressWidth = infoPaint.measureText(progressText)
        val titleWidth = (geometry.contentWidthPx - progressWidth - 18f * density).coerceAtLeast(1f)
        val footerTitle = TextUtils.ellipsize(title, infoPaint, titleWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(footerTitle, geometry.contentLeftPx, geometry.footerBaselinePx, infoPaint)
        canvas.drawText(progressText, geometry.contentRightPx - progressWidth, geometry.footerBaselinePx, infoPaint)
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
                    val selectedLine = lineAt(event.y)
                    val selectedText = selectedLine?.text?.trim().orEmpty()
                    if (selectedText.isNotBlank() && selectedLine != null) {
                        selectionListener?.onTextSelected(
                            selectedText,
                            currentLocator().copy(charOffset = selectedLine.startOffset.toLong()),
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

    private fun rebuildPaginator(anchorOffset: Int) {
        if (width <= 0 || height <= 0 || content.isEmpty()) {
            invalidate()
            return
        }
        val geometry = pageGeometry()
        textPaint.textSize = geometry.bodyTextSizePx
        val config = TextPaginationConfig(
            contentWidthPx = geometry.contentWidthPx,
            contentHeightPx = geometry.contentHeightPx,
            lineHeightPx = geometry.lineAdvancePx,
            paragraphSpacingPx = geometry.paragraphSpacingPx,
            indentPx = if (settings.chineseTypography) geometry.bodyTextSizePx * 2f else 0f,
        )
        paginator = TextPaginator(content, textPaint, config)
        currentPage = paginator?.pageStartingAt(anchorOffset) ?: TextPage(0, 0, emptyList())
        notifyLocation()
        invalidate()
    }

    private fun lineAt(y: Float) = run {
        val geometry = pageGeometry()
        var top = geometry.contentTopPx
        currentPage.lines.firstOrNull { line ->
            val bottom = top + geometry.lineAdvancePx + if (line.paragraphEnd) geometry.paragraphSpacingPx else 0f
            val matches = y in top..bottom
            top = bottom
            matches
        }
    }

    private fun applyPaintSettings() {
        textPaint.textSize = settings.fontSizeSp.coerceIn(14f, 34f) * density
        textPaint.isFakeBoldText = settings.textBold
        infoPaint.typeface = textPaint.typeface
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

    private fun notifyLocation() {
        locationListener?.onLocationChanged(currentLocator())
    }

    private data class Palette(val background: Int, val text: Int, val secondary: Int)

    private fun paletteFor(theme: ReaderTheme): Palette = when (theme) {
        ReaderTheme.WHITE -> Palette(Color.rgb(250, 250, 248), Color.rgb(32, 31, 29), Color.rgb(112, 108, 101))
        ReaderTheme.WARM -> Palette(Color.rgb(246, 236, 217), Color.rgb(43, 35, 29), Color.rgb(123, 101, 81))
        ReaderTheme.GREEN -> Palette(Color.rgb(220, 234, 215), Color.rgb(34, 47, 34), Color.rgb(85, 108, 84))
        ReaderTheme.NIGHT -> Palette(Color.rgb(20, 22, 24), Color.rgb(207, 210, 212), Color.rgb(132, 139, 143))
    }
}
