package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import org.junit.Assert.*
import org.junit.Test

class MarkdownVisibleSourceOffsetTest {
    @Test fun laterChapterUsesGlobalSourceRatherThanTxtZero() {
        val doc = MarkdownDocument("# First\n\nFirst paragraph.\n\n# Second\n\nTarget paragraph.")
        val chapter = (doc.blocks(1).single() as DocBlock.Markdown).chapter
        val offset = markdownVisibleSourceOffset(chapter, doc.chapters[1].startOffset, true, 1, 0f)
        assertNotNull(offset)
        assertTrue(offset!! >= doc.chapters[1].startOffset)
        assertTrue(offset < doc.totalChars)
        assertEquals("Target paragraph.", doc.renderCanonicalText(1).substring(offset).trim())
    }
    @Test fun streamedChapterAddsBaseAndClampsWithinVisibleUnit() {
        val chapter = MarkdownParser.parse("# Second\n\nTarget paragraph.")
        val range = MarkdownRenderModel.flatten(chapter)[1].canonicalRange
        val base = 730
        assertEquals(base + range.first, markdownVisibleSourceOffset(chapter, base, false, 1, -1f))
        assertEquals(base + range.last, markdownVisibleSourceOffset(chapter, base, false, 1, 2f))
        assertNull(markdownVisibleSourceOffset(chapter, base, false, 99, 0f))
    }
}
