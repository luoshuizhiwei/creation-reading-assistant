package com.creationreadingassistant.ui.screen

import com.creationreadingassistant.ui.screen.reader.ReaderScreenState
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScreenStateTest {
    @Test
    fun startsWithControlsVisibleAndNoTransientOverlay() {
        val state = ReaderScreenState()

        assertTrue(state.controlsVisible)
        assertNull(state.sheet)
        assertTrue(state.selectedText.isEmpty())
        assertFalse(state.showTts)
        assertFalse(state.noteOpen)
    }

    @Test
    fun sheetAndSelectionStateRemainIndependent() {
        val state = ReaderScreenState(
            sheet = ReaderSheet.SEARCH,
            searchQuery = "测试",
            selectedText = "选区",
            selectedRangeStart = 42,
        )

        assertEquals(ReaderSheet.SEARCH, state.sheet)
        assertEquals("测试", state.searchQuery)
        assertEquals("选区", state.selectedText)
        assertEquals(42, state.selectedRangeStart)
    }
}
