package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressPreviewPolicyTest {
    @Test
    fun `dragging always keeps the preview observable`() {
        assertFalse(
            seekPreviewHasConverged(
                isDragging = true,
                seekFinished = false,
                startPercent = 10f,
                targetPercent = 63f,
                chapterProgress = 63f,
            ),
        )
    }

    @Test
    fun `synchronous target convergence uses an observable linger instead of a single frame`() {
        assertTrue(
            seekPreviewHasConverged(
                isDragging = false,
                seekFinished = true,
                startPercent = 10f,
                targetPercent = 63f,
                chapterProgress = 63f,
            ),
        )
        assertTrue(SEEK_PREVIEW_LINGER_MILLIS >= 500L)
    }

    @Test
    fun `unrelated passive progress does not dismiss a pending seek`() {
        assertFalse(
            seekPreviewHasConverged(
                isDragging = false,
                seekFinished = true,
                startPercent = 10f,
                targetPercent = 63f,
                chapterProgress = 32f,
            ),
        )
    }

    @Test
    fun `cancelled or rejected seek uses a bounded cleanup timeout`() {
        assertFalse(
            seekPreviewHasConverged(
                isDragging = false,
                seekFinished = true,
                startPercent = 10f,
                targetPercent = 63f,
                chapterProgress = 10f,
            ),
        )
        assertEquals(1_500L, seekPreviewTimeoutMillis(seekFinished = true))
        assertEquals(null, seekPreviewTimeoutMillis(seekFinished = false))
    }
}
