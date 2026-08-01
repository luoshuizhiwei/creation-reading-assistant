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
        listOf("white", "warm", "green", "night").forEach { key ->
            assertEquals(paperPalette(key, false), paperPalette(key, true))
        }
    }

    @Test fun sharedOptionsContainEverySupportedChoiceOnce() {
        assertEquals(listOf("follow", "white", "warm", "green", "night"), ReaderPaperOptions.map { it.key })
        assertEquals(ReaderPaperOptions.size, ReaderPaperOptions.distinctBy { it.key }.size)
    }
}
