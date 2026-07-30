/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.reader.legado.layout

import local.creationReadingAssistant.reader.legado.model.NativeReaderSettings
import kotlin.math.max

/**
 * Resolves a stable reading column for phones, large phones and tablets.
 * Reader font sizes deliberately follow density rather than scaledDensity:
 * the app already exposes its own font-size control, so applying Android's
 * system font scale again would make pagination vary wildly between devices.
 */
data class ReaderPageGeometry(
    val contentLeftPx: Float,
    val contentTopPx: Float,
    val contentRightPx: Float,
    val contentBottomPx: Float,
    val footerBaselinePx: Float,
    val bodyTextSizePx: Float,
    val infoTextSizePx: Float,
    val lineAdvancePx: Float,
    val paragraphSpacingPx: Float,
) {
    val contentWidthPx: Int
        get() = max(1, (contentRightPx - contentLeftPx).toInt())

    val contentHeightPx: Int
        get() = max(1, (contentBottomPx - contentTopPx).toInt())
}

object ReaderPageGeometryResolver {
    fun resolve(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        settings: NativeReaderSettings,
        insetLeftPx: Int = 0,
        insetTopPx: Int = 0,
        insetRightPx: Int = 0,
        insetBottomPx: Int = 0,
    ): ReaderPageGeometry {
        val safeDensity = density.coerceAtLeast(0.5f)
        // Reading columns should stay optically centred even when a vendor
        // reports a one-sided gesture/cutout inset. Reserve the larger side on
        // both edges; otherwise the TXT page looks wider on one side.
        val horizontalSystemInset = max(
            insetLeftPx.coerceAtLeast(0),
            insetRightPx.coerceAtLeast(0),
        ).toFloat()
        val safeLeft = horizontalSystemInset
        val safeTop = insetTopPx.coerceAtLeast(0).toFloat()
        val safeRight = (widthPx - horizontalSystemInset.toInt()).coerceAtLeast(safeLeft.toInt() + 1).toFloat()
        val safeBottom = (heightPx - insetBottomPx.coerceAtLeast(0)).coerceAtLeast(safeTop.toInt() + 1).toFloat()
        val safeWidthDp = (safeRight - safeLeft) / safeDensity

        val requestedHorizontalDp = settings.horizontalPaddingDp.coerceIn(14f, 52f)
        val maximumColumnDp = when {
            safeWidthDp >= 720f -> 560f
            safeWidthDp >= 600f -> 520f
            safeWidthDp >= 480f -> 480f
            else -> safeWidthDp
        }
        val centeringInsetDp = ((safeWidthDp - maximumColumnDp) / 2f).coerceAtLeast(0f)
        val maximumMarginDp = max(14f, safeWidthDp * 0.19f)
        val horizontalDp = max(
            centeringInsetDp,
            requestedHorizontalDp,
        ).coerceIn(14f, maximumMarginDp)

        val requestedVerticalDp = settings.verticalPaddingDp.coerceIn(18f, 48f)
        val verticalDp = requestedVerticalDp

        val widthTextScale = when {
            safeWidthDp < 340f -> 0.94f
            safeWidthDp < 390f -> 0.98f
            safeWidthDp < 440f -> 1f
            else -> 1.03f
        }
        val bodyTextSizePx = settings.fontSizeSp.coerceIn(14f, 34f) * widthTextScale * safeDensity
        val infoTextSizePx = 11f * safeDensity
        val lineAdvancePx = bodyTextSizePx * settings.lineSpacingMultiplier.coerceIn(1.35f, 2.15f)
        val paragraphSpacingPx = settings.paragraphSpacingDp.coerceIn(0f, 24f) * safeDensity

        val contentLeft = safeLeft + horizontalDp * safeDensity
        val contentRight = safeRight - horizontalDp * safeDensity
        val contentTop = safeTop + verticalDp * safeDensity
        val footerBaseline = safeBottom - max(9f, verticalDp * 0.36f) * safeDensity
        val contentBottom = footerBaseline - infoTextSizePx - 9f * safeDensity

        return ReaderPageGeometry(
            contentLeftPx = contentLeft,
            contentTopPx = contentTop,
            contentRightPx = max(contentLeft + 1f, contentRight),
            contentBottomPx = max(contentTop + 1f, contentBottom),
            footerBaselinePx = footerBaseline,
            bodyTextSizePx = bodyTextSizePx,
            infoTextSizePx = infoTextSizePx,
            lineAdvancePx = lineAdvancePx,
            paragraphSpacingPx = paragraphSpacingPx,
        )
    }
}
