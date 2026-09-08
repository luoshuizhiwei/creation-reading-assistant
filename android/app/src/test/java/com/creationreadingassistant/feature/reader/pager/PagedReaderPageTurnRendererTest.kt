package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Test

class PagedReaderPageTurnRendererTest {
    @Test
    fun `manual reveal uses the outer reveal renderer instead of a different PageTurner effect`() {
        assertEquals(
            PagedReaderPageTurnRenderer.OUTER_REVEAL,
            pagedReaderPageTurnRenderer(pageTurnEffect = "reveal", autoPageIntervalMillis = null),
        )
    }

    @Test
    fun `automatic reveal remains owned by PageTurner`() {
        assertEquals(
            PagedReaderPageTurnRenderer.PAGE_TURNER,
            pagedReaderPageTurnRenderer(pageTurnEffect = "reveal", autoPageIntervalMillis = 20_000L),
        )
    }

    @Test
    fun `ordinary manual effects keep the existing PageTurner path`() {
        assertEquals(
            PagedReaderPageTurnRenderer.PAGE_TURNER,
            pagedReaderPageTurnRenderer(pageTurnEffect = "slide", autoPageIntervalMillis = null),
        )
    }

    @Test
    fun `manual reveal clips the target paper and content together above a retained current frame`() {
        assertEquals(
            ManualRevealLayerPolicy(
                keepsCurrentFrameVisible = true,
                clipsTargetPaperWithTargetContent = true,
                commitsPageAfterReveal = true,
            ),
            manualRevealLayerPolicy(),
        )
    }
}
