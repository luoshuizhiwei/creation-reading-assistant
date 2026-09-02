package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingMarkdownOffsetPolicyTest {

    private val document = MarkdownDocument("# One\nfirst\n# Two\nsecond")
    private val secondChapterOffset = document.chapters[1].startOffset

    @Test
    fun `target chapter loading keeps markdown offset pending`() {
        assertEquals(
            PendingMarkdownOffsetOutcome.Pending,
            pendingMarkdownOffsetOutcome(
                pendingOffset = secondChapterOffset,
                markdownDocument = document,
                pagerEngineOn = false,
                currentChapterIndex = 1,
                targetBlocksReady = false,
            ),
        )
    }

    @Test
    fun `target chapter ready consumes markdown offset once`() {
        assertEquals(
            PendingMarkdownOffsetOutcome.Consume(secondChapterOffset),
            pendingMarkdownOffsetOutcome(
                pendingOffset = secondChapterOffset,
                markdownDocument = document,
                pagerEngineOn = false,
                currentChapterIndex = 1,
                targetBlocksReady = true,
            ),
        )
    }

    @Test
    fun `manual navigation away discards stale markdown offset`() {
        assertEquals(
            PendingMarkdownOffsetOutcome.Discarded,
            pendingMarkdownOffsetOutcome(
                pendingOffset = secondChapterOffset,
                markdownDocument = document,
                pagerEngineOn = false,
                currentChapterIndex = 0,
                targetBlocksReady = true,
            ),
        )
    }

    @Test
    fun `switching to pager or losing document discards pending markdown offset`() {
        assertEquals(
            PendingMarkdownOffsetOutcome.Discarded,
            pendingMarkdownOffsetOutcome(
                pendingOffset = secondChapterOffset,
                markdownDocument = document,
                pagerEngineOn = true,
                currentChapterIndex = 1,
                targetBlocksReady = true,
            ),
        )
        assertEquals(
            PendingMarkdownOffsetOutcome.Discarded,
            pendingMarkdownOffsetOutcome(
                pendingOffset = secondChapterOffset,
                markdownDocument = null,
                pagerEngineOn = false,
                currentChapterIndex = 1,
                targetBlocksReady = true,
            ),
        )
    }
}
