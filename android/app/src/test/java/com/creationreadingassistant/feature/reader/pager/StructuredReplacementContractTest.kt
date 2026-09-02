package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * EPUB/Markdown replacement stays closed until rendering and persistence share source coordinates.
 * These tests intentionally describe the current boundary rather than approximating a projection.
 */
class StructuredReplacementContractTest {

    @Test
    fun `EPUB source declares estimated coordinates and keeps extracted text unchanged`() {
        val source = EpubChapterSource(
            titles = listOf("chapter"),
            chapterStartOffsets = listOf(0),
            totalChars = 128,
            loadBlocks = {
                listOf(
                    DocBlock.Text("heading", isHeading = true),
                    DocBlock.Text("body", isHeading = false),
                )
            },
        )

        val prepared = preparePagedReplacement(source, "epub", listOf(removeBodyRule()))

        assertEquals(ReplacementCoordinateSpace.ESTIMATED, source.replacementCoordinateSpace)
        assertEquals(PagedReplacementAvailability.ESTIMATED_COORDINATES, prepared.availability)
        assertEquals("heading\nbody", prepared.source.loadChapterText(0))
    }

    @Test
    fun `Markdown source declares canonical display coordinates and keeps markup-derived text unchanged`() {
        val markdownSource = "# Heading\nA **bold** sentence."
        val document = MarkdownDocument(markdownSource)
        val source = MarkdownChapterSource(document)

        val prepared = preparePagedReplacement(source, "markdown", listOf(removeBodyRule()))

        assertEquals(ReplacementCoordinateSpace.CANONICAL_DISPLAY, source.replacementCoordinateSpace)
        assertEquals(PagedReplacementAvailability.NON_SOURCE_COORDINATES, prepared.availability)
        assertEquals(document.text(0), prepared.source.loadChapterText(0))
        assertNotEquals(markdownSource, document.text(0))
    }

    @Test
    fun `Markdown visible offsets can map to source but paged persistence is not source based yet`() {
        val sourceText = "Before **visible** after"
        val chapter = MarkdownParser.parse(sourceText)
        val canonicalStart = chapter.canonicalText.indexOf("visible")
        val sourceStart = sourceText.indexOf("visible")

        assertEquals(sourceStart, chapter.offsetMap.toSource(canonicalStart))
        assertEquals(canonicalStart, chapter.offsetMap.toCanonical(sourceStart))
        assertNotEquals(sourceText.length, chapter.canonicalText.length)
    }

    private fun removeBodyRule() = ReplaceRule(
        id = "replace-body",
        name = "remove body",
        pattern = "body",
        replacement = "",
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )
}
