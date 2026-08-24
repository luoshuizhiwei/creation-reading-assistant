package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.data.settings.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderViewportProfileTest {
    @Test
    fun referencePhoneKeepsTheSavedReaderPreference() {
        val profile = readerViewportProfile(
            widthDp = 411f,
            heightDp = 860f,
            preferredFontSizeSp = 25f,
            preferredPageMarginDp = 22f,
        )

        assertEquals(1f, profile.scale, 0.001f)
        assertEquals(25f, profile.fontSizeSp, 0.001f)
        assertEquals(22f, profile.pageMarginDp, 0.001f)
    }

    @Test
    fun compactPhoneReducesDensityOfLargeReaderTextWithoutChangingItsRole() {
        val profile = readerViewportProfile(
            widthDp = 320f,
            heightDp = 640f,
            preferredFontSizeSp = 25f,
            preferredPageMarginDp = 22f,
        )

        assertTrue(profile.scale < 1f)
        assertTrue(profile.fontSizeSp in 21f..23f)
        assertTrue(profile.pageMarginDp < 22f)
        assertTrue(profile.contentTopPaddingDp in 24f..32f)
    }

    @Test
    fun wideViewportGrowsModeratelyAndStillHonorsPreferenceBounds() {
        val profile = readerViewportProfile(
            widthDp = 600f,
            heightDp = 960f,
            preferredFontSizeSp = 25f,
            preferredPageMarginDp = 22f,
        )

        assertTrue(profile.scale > 1f)
        assertEquals(27f, profile.fontSizeSp, 0.01f)
        assertEquals(23.76f, profile.pageMarginDp, 0.01f)
        assertTrue(profile.fontSizeSp <= 40f)
    }

    @Test
    fun landscapeUsesTheShortestSideSoLongWidthDoesNotInflateText() {
        val portrait = readerViewportProfile(360f, 800f, 25f, 22f)
        val landscape = readerViewportProfile(800f, 360f, 25f, 22f)

        assertEquals(portrait, landscape)
    }

    @Test
    fun adaptingTheViewportDoesNotChangeBehavioralReaderSettings() {
        val original = ReaderSettings(
            readerMode = "scroll",
            pagerEngineMode = "off",
            autoPageSpeed = 8,
            showReaderInfo = false,
        )

        val adapted = original.forReaderViewport(320f, 640f)

        assertEquals(original.readerMode, adapted.readerMode)
        assertEquals(original.pagerEngineMode, adapted.pagerEngineMode)
        assertEquals(original.autoPageSpeed, adapted.autoPageSpeed)
        assertEquals(original.showReaderInfo, adapted.showReaderInfo)
        assertTrue(adapted.fontSize < original.fontSize)
    }

    @Test
    fun pagedMetaRowsGrowForAccessibilityFontScale() {
        assertEquals(24f, pagedReaderHeaderHeightDp(1f), 0.001f)
        assertEquals(28f, pagedReaderFooterHeightDp(1f), 0.001f)
        assertTrue(pagedReaderHeaderHeightDp(2f) > pagedReaderHeaderHeightDp(1f))
        assertTrue(pagedReaderFooterHeightDp(2f) > pagedReaderFooterHeightDp(1f))
    }
}
