package local.creationReadingAssistant.reader.legado.layout

import local.creationReadingAssistant.reader.legado.model.NativeReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageGeometryTest {
    @Test
    fun `keeps a useful column on compact phones`() {
        val geometry = ReaderPageGeometryResolver.resolve(
            widthPx = 320 * 3,
            heightPx = 640 * 3,
            density = 3f,
            settings = NativeReaderSettings(),
            insetTopPx = 24 * 3,
            insetBottomPx = 24 * 3,
        )

        assertTrue(geometry.contentWidthPx / 3f >= 270f)
        assertTrue(geometry.contentHeightPx / 3f >= 500f)
        assertTrue(geometry.contentTopPx >= 24 * 3)
        assertTrue(geometry.contentBottomPx < geometry.footerBaselinePx)
    }

    @Test
    fun `caps the reading line on wide displays`() {
        val geometry = ReaderPageGeometryResolver.resolve(
            widthPx = 800 * 2,
            heightPx = 1200 * 2,
            density = 2f,
            settings = NativeReaderSettings(),
        )

        assertTrue(geometry.contentWidthPx / 2f <= 560.5f)
        assertTrue(geometry.contentLeftPx > 100 * 2)
    }

    @Test
    fun `reader typography ignores Android scaled density`() {
        val geometry = ReaderPageGeometryResolver.resolve(
            widthPx = 390 * 3,
            heightPx = 844 * 3,
            density = 3f,
            settings = NativeReaderSettings(fontSizeSp = 20f),
        )

        assertEquals(60f, geometry.bodyTextSizePx, 0.01f)
        assertEquals(34f * 3f, geometry.lineAdvancePx, 0.01f)
    }

    @Test
    fun `reserves cutout and gesture insets`() {
        val geometry = ReaderPageGeometryResolver.resolve(
            widthPx = 430 * 3,
            heightPx = 900 * 3,
            density = 3f,
            settings = NativeReaderSettings(),
            insetLeftPx = 8 * 3,
            insetTopPx = 32 * 3,
            insetRightPx = 6 * 3,
            insetBottomPx = 30 * 3,
        )

        assertTrue(geometry.contentLeftPx > 8 * 3)
        assertTrue(geometry.contentTopPx > 32 * 3)
        assertTrue(geometry.footerBaselinePx < (900 - 30) * 3)
    }

    @Test
    fun `keeps the reading column centred with a one sided system inset`() {
        val geometry = ReaderPageGeometryResolver.resolve(
            widthPx = 390 * 3,
            heightPx = 844 * 3,
            density = 3f,
            settings = NativeReaderSettings(),
            insetLeftPx = 0,
            insetRightPx = 12 * 3,
        )

        val leftMargin = geometry.contentLeftPx
        val rightMargin = 390 * 3 - geometry.contentRightPx
        assertEquals(leftMargin, rightMargin, 0.01f)
    }

    @Test
    fun `maximum in-app font remains usable on a small phone with system insets`() {
        val geometry = ReaderPageGeometryResolver.resolve(
            widthPx = 320 * 3,
            heightPx = 568 * 3,
            density = 3f,
            settings = NativeReaderSettings(
                fontSizeSp = 34f,
                lineSpacingMultiplier = 2.15f,
                horizontalPaddingDp = 52f,
                verticalPaddingDp = 48f,
            ),
            insetTopPx = 28 * 3,
            insetBottomPx = 24 * 3,
        )

        assertTrue(geometry.contentWidthPx > geometry.bodyTextSizePx * 3f)
        assertTrue(geometry.contentHeightPx > geometry.lineAdvancePx * 3f)
        assertTrue(geometry.contentTopPx >= 28 * 3)
        assertTrue(geometry.contentBottomPx < geometry.footerBaselinePx)
    }
}
