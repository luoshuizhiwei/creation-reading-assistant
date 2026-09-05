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

    @Test
    fun `empty rules on EPUB and Markdown keep respective unavailable reasons instead of NO_EFFECTIVE_RULES`() {
        val epub = EpubChapterSource(
            titles = listOf("ch"),
            chapterStartOffsets = listOf(0),
            totalChars = 50,
            loadBlocks = { listOf(DocBlock.Text("body", isHeading = false)) },
        )
        val preparedEpub = preparePagedReplacement(epub, "epub", emptyList())
        assertEquals(
            "EPUB with empty rules must stay ESTIMATED_COORDINATES",
            PagedReplacementAvailability.ESTIMATED_COORDINATES,
            preparedEpub.availability,
        )

        val md = MarkdownChapterSource(MarkdownDocument("# Title\nBody"))
        val preparedMd = preparePagedReplacement(md, "md", emptyList())
        assertEquals(
            "Markdown with empty rules must stay NON_SOURCE_COORDINATES",
            PagedReplacementAvailability.NON_SOURCE_COORDINATES,
            preparedMd.availability,
        )
    }

    @Test
    fun `incomplete scope or estimated lengths source falls back to unprojected delegate`() {
        val estimatedSource = object : PagedChapterSource {
            override val chapterCount: Int = 1
            override val totalChars: Int = 100
            override val replacementCoordinateSpace: ReplacementCoordinateSpace = ReplacementCoordinateSpace.SOURCE
            override val chapterLengthsAreEstimated: Boolean = true
            override fun chapterTitle(index: Int) = "Est"
            override fun chapterStartAbs(index: Int) = 0
            override fun loadChapter(index: Int) = PagedChapterContent("text", emptyList())
        }
        val prepEst = preparePagedReplacement(estimatedSource, "b1", listOf(removeBodyRule()))
        assertEquals(PagedReplacementAvailability.ESTIMATED_COORDINATES, prepEst.availability)

        val incompleteSource = object : PagedChapterSource {
            override val chapterCount: Int = 1
            override val totalChars: Int = 100
            override val replacementCoordinateSpace: ReplacementCoordinateSpace = ReplacementCoordinateSpace.SOURCE
            override val replaceProjectionScopeIsComplete: Boolean = false
            override fun chapterTitle(index: Int) = "Inc"
            override fun chapterStartAbs(index: Int) = 0
            override fun loadChapter(index: Int) = PagedChapterContent("text", emptyList())
        }
        val prepInc = preparePagedReplacement(incompleteSource, "b2", listOf(removeBodyRule()))
        assertEquals(PagedReplacementAvailability.INCOMPLETE_SCOPE, prepInc.availability)
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
