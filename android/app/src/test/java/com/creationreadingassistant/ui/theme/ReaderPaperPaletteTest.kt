package com.creationreadingassistant.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaperPaletteTest {
    @Test fun followMapsToActualApplicationBrightness() {
        assertTrue(paperPalette("follow", darkTheme = false).isLight)
        assertFalse(paperPalette("follow", darkTheme = true).isLight)
    }

    @Test fun fixedPaperDoesNotChangeWithApplicationTheme() {
        ReaderPaperOptions.map { it.key }.filter { it != "follow" }.forEach { key ->
            assertEquals(paperPalette(key, false), paperPalette(key, true))
        }
    }

    @Test fun sharedOptionsContainEverySupportedChoiceOnce() {
        assertEquals(
            listOf("follow", "white", "warm", "parchment", "celadon", "green", "sepia_dark", "ink_night", "night"),
            ReaderPaperOptions.map { it.key },
        )
        assertEquals(ReaderPaperOptions.size, ReaderPaperOptions.distinctBy { it.key }.size)
    }
}
