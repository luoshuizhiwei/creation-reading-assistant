package com.creationreadingassistant.ui.layout

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTokensTest {
    @Test
    fun defaultSpacingFollowsFourPointRhythm() {
        val tokens = DefaultLayoutTokens

        assertEquals(4.dp, tokens.microGap)
        assertEquals(8.dp, tokens.relatedGap)
        assertEquals(12.dp, tokens.contentGap)
        assertEquals(20.dp, tokens.sectionGap)
        assertEquals(16.dp, tokens.pageHorizontal)
        assertEquals(24.dp, tokens.pageHorizontalWide)
    }

    @Test
    fun interactiveRowsMeetMinimumTouchTarget() {
        val tokens = DefaultLayoutTokens

        assertEquals(48.dp, tokens.minimumTouchTarget)
        assertTrue(tokens.singleLineRowHeight >= tokens.minimumTouchTarget)
        assertTrue(tokens.supportingRowHeight >= tokens.minimumTouchTarget)
        assertTrue(tokens.topBarHeight >= tokens.minimumTouchTarget)
    }
}
