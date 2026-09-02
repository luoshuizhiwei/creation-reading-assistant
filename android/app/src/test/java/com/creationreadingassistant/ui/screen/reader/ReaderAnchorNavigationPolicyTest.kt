package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAnchorNavigationPolicyTest {

    @Test
    fun `unchanged book and chapter allows resolved anchor to apply`() {
        assertTrue(
            canApplyResolvedReaderAnchor(
                requestBookId = "book-1",
                requestChapterIndex = 2,
                currentBookId = "book-1",
                currentChapterIndex = 2,
            ),
        )
    }

    @Test
    fun `manual chapter change discards delayed anchor`() {
        assertFalse(
            canApplyResolvedReaderAnchor(
                requestBookId = "book-1",
                requestChapterIndex = 2,
                currentBookId = "book-1",
                currentChapterIndex = 3,
            ),
        )
    }

    @Test
    fun `book switch discards delayed anchor even when chapter index matches`() {
        assertFalse(
            canApplyResolvedReaderAnchor(
                requestBookId = "book-1",
                requestChapterIndex = 2,
                currentBookId = "book-2",
                currentChapterIndex = 2,
            ),
        )
    }

    @Test
    fun `streaming anchor plan clamps stale locator to document bounds`() {
        assertEquals(
            StreamingAnchorWindowPlan(targetOffset = 0, windowStart = 0),
            streamingAnchorWindowPlan(locatorOffset = -40, totalChars = 9_000),
        )
        assertEquals(
            StreamingAnchorWindowPlan(targetOffset = 9_000, windowStart = 7_000),
            streamingAnchorWindowPlan(locatorOffset = 12_000, totalChars = 9_000),
        )
    }

    @Test
    fun `streaming anchor plan preserves valid target and bounded window start`() {
        assertEquals(
            StreamingAnchorWindowPlan(targetOffset = 1_234, windowStart = 0),
            streamingAnchorWindowPlan(locatorOffset = 1_234, totalChars = 9_000),
        )
        assertEquals(
            StreamingAnchorWindowPlan(targetOffset = 5_000, windowStart = 3_000),
            streamingAnchorWindowPlan(locatorOffset = 5_000, totalChars = 9_000),
        )
    }
}
