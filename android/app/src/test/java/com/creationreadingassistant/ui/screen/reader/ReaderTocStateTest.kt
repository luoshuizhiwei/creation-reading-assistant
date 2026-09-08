package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.DocChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTocStateTest {

    @Test
    fun `markdown uses document chapters and current chapter index`() {
        val state = readerTocState(
            epubTitles = null,
            markdownTitles = listOf("第一节", "第二节", "第三节"),
            txtTitles = emptyList(),
            chapterIndex = 1,
            txtChapterIndex = 0,
        )

        assertEquals(listOf("第一节", "第二节", "第三节"), state.titles)
        assertEquals(1, state.current)
        assertEquals(3, state.total)
        assertTrue(state.isChapteredDocument)
    }

    @Test
    fun `markdown toc pick uses chapter navigation instead of txt offsets`() {
        val target = readerTocPickTarget(
            isEpub = false,
            isMarkdown = true,
            txtChapters = listOf(DocChapter(0, "TXT", 120, 80)),
            selectedIndex = 0,
        )

        assertEquals(ReaderTocPickTarget.Chapter(0), target)
    }

    @Test
    fun `txt toc pick keeps source offset navigation`() {
        val target = readerTocPickTarget(
            isEpub = false,
            isMarkdown = false,
            txtChapters = listOf(DocChapter(0, "TXT", 120, 80)),
            selectedIndex = 0,
        )

        assertEquals(ReaderTocPickTarget.PlainOffset(120), target)
    }

    @Test
    fun `chapter word count labels preserve exact and estimated semantics`() {
        val labels = readerTocWordCountLabels(
            listOf(
                DocChapter(0, "短章", 0, 842),
                DocChapter(1, "长章", 843, 12_340),
                DocChapter(2, "EPUB 估算章", 13_184, 25_000, charCountIsEstimated = true),
            ),
        )

        assertEquals(listOf("842字", "1.2万字", "约2.5万字"), labels)
    }
}
