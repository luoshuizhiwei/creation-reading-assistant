package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfAdaptiveLayoutPolicyTest {
    @Test
    fun `phone widths keep stable three-column shelf`() {
        assertEquals(3, shelfGridColumnCount(320.dp))
        assertEquals(3, shelfGridColumnCount(393.dp))
        assertEquals(3, shelfGridColumnCount(430.dp))
    }

    @Test
    fun `tablet shelf grows to four then five columns`() {
        assertEquals(4, shelfGridColumnCount(600.dp))
        assertEquals(5, shelfGridColumnCount(720.dp))
    }

    @Test
    fun `large tablet caps readable shelf at five columns`() {
        assertEquals(5, shelfGridColumnCount(840.dp))
        assertEquals(5, shelfGridColumnCount(1200.dp))
    }
}
