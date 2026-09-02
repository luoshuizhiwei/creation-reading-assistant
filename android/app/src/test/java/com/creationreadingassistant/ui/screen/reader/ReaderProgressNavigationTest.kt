package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.mutableStateOf
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.domain.model.EpubChapter
import com.creationreadingassistant.feature.reader.doc.DocBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressNavigationTest {

    @Test
    fun `whole book target selects the chapter by character offset`() {
        val starts = listOf(0, 100, 1_000)

        assertEquals(0, chapterIndexForBookOffset(starts, targetOffset = 0, totalChars = 2_000))
        assertEquals(1, chapterIndexForBookOffset(starts, targetOffset = 999, totalChars = 2_000))
        assertEquals(2, chapterIndexForBookOffset(starts, targetOffset = 1_000, totalChars = 2_000))
        assertEquals(2, chapterIndexForBookOffset(starts, targetOffset = 2_000, totalChars = 2_000))
    }

    @Test
    fun `whole book target tolerates missing or incomplete indexes`() {
        assertNull(chapterIndexForBookOffset(emptyList(), targetOffset = 500, totalChars = 2_000))
        assertEquals(0, chapterIndexForBookOffset(listOf(100), targetOffset = 0, totalChars = 2_000))
    }

    @Test
    fun `epub chapter offset maps to the matching rendered text block`() {
        val blocks = listOf(
            DocBlock.Text("abcd"),
            DocBlock.Text("efghij"),
            DocBlock.Text("kl"),
        )

        assertEquals(0, blockIndexForChapterOffset(blocks, inChapter = 0))
        assertEquals(1, blockIndexForChapterOffset(blocks, inChapter = 5))
        assertEquals(2, blockIndexForChapterOffset(blocks, inChapter = 12))
    }

    @Test
    fun `legacy epub book progress uses character weighted chapter mapping`() {
        var chapter = -1

        seekToPercent(
            p = 50f,
            epubBook = epubBook(),
            markdownDocument = null,
            pagerEngineOn = false,
            bookIndex = BookIndex(listOf(0, 100, 1_000), emptyList(), totalChars = 2_000),
            txtStreamingDocument = null,
            plainContent = "",
            pagedJumpRequest = mutableStateOf(null),
            goToChapter = { chapter = it },
            jumpToPlainOffset = { error("EPUB must not use TXT scrolling") },
            jumpToMarkdownOffset = { error("EPUB must not use Markdown scrolling") },
        )

        assertEquals(2, chapter)
    }

    @Test
    fun `legacy epub chapter progress uses epub scrolling rather than txt scrolling`() {
        var epubOffset = -1
        var plainScrolled = false

        seekToChapterPercent(
            p = 50f,
            epubBook = epubBook(),
            markdownDocument = null,
            chapterStartOffsets = listOf(1_000),
            chapterIndex = 0,
            contentText = "x".repeat(100),
            pagerEngineOn = false,
            txtChapters = emptyList(),
            txtChapterIndex = 0,
            plainContent = "",
            pagedJumpRequest = mutableStateOf(null),
            jumpToPlainOffset = { plainScrolled = true },
            jumpToEpubOffset = { epubOffset = it },
            jumpToMarkdownOffset = { error("EPUB must not use Markdown scrolling") },
        )

        assertEquals(1_050, epubOffset)
        assertFalse(plainScrolled)
    }

    @Test
    fun `legacy scrolling does not persist before initial position restoration`() {
        assertFalse(canPersistLegacyScrollPosition(initialPositionPending = true))
        assertTrue(canPersistLegacyScrollPosition(initialPositionPending = false))
    }

    @Test
    fun `epub chapter end follows the final text block coordinate`() {
        val blocks = listOf(
            DocBlock.Text("abcd"),
            DocBlock.Text("efghij"),
            DocBlock.Image("", 0, 0),
            DocBlock.Text("kl"),
        )
        val endOffset = epubChapterEndOffset(
            blocks = blocks,
            blockGlobalOffsets = listOf(100, 105, -1, 112),
            chapterBase = 100,
        )

        assertEquals(114, endOffset)
        assertEquals(
            114,
            visibleScrollOffset(
                firstVisibleItemIndex = 0,
                firstVisibleItemOffset = 0,
                firstVisibleItemSize = 400,
                itemStartOffsets = listOf(100, 105, -1, 112),
                itemLengths = listOf(4, 6, 0, 2),
                endReached = true,
                endOffset = endOffset,
            ),
        )
    }

    private fun epubBook(): EpubBook = EpubBook(
        id = "test",
        title = "test",
        author = null,
        chapters = List(3) { index ->
            EpubChapter("chapter-$index", "chapter-$index.xhtml", "", "")
        },
        localUri = "",
        cachedEpubPath = "",
    )
}
