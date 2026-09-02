package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownSelectionPositionTest {

    @Test
    fun `whole document markdown unit keeps its canonical global offset`() {
        assertEquals(42, markdownUnitGlobalOffset(blockGlobalOffset = 0, canonicalStart = 42))
    }

    @Test
    fun `streaming markdown unit adds its chapter base`() {
        assertEquals(1_234, markdownUnitGlobalOffset(blockGlobalOffset = 1_000, canonicalStart = 234))
    }

    @Test
    fun `unknown or invalid source coordinate is never persisted`() {
        assertEquals(-1, markdownUnitGlobalOffset(blockGlobalOffset = -1, canonicalStart = 20))
        assertEquals(-1, markdownUnitGlobalOffset(blockGlobalOffset = 20, canonicalStart = -1))
    }
}
