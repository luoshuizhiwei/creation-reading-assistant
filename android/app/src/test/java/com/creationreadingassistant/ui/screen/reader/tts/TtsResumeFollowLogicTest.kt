package com.creationreadingassistant.ui.screen.reader.tts

import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P1-B：Markdown 必须像 EPUB 一样按章跟踪 TTS 续读与跟读；
 * TXT 保持原有（无章号、始终用保存偏移）语义，不得回归。
 */
class TtsResumeFollowLogicTest {

    // ── 续读章号跟踪 ────────────────────────────────────────────

    @Test
    fun `markdown saves chapter index like epub`() {
        assertEquals(2, ttsResumeChapterFor(isEpub = false, isMarkdown = true, chapterIndex = 2))
        assertEquals(2, ttsResumeChapterFor(isEpub = true, isMarkdown = false, chapterIndex = 2))
    }

    @Test
    fun `txt keeps legacy no-chapter resume tracking`() {
        assertEquals(-1, ttsResumeChapterFor(isEpub = false, isMarkdown = false, chapterIndex = 3))
    }

    // ── 续读起点（openTts resumeAt）──────────────────────────────

    @Test
    fun `txt always resumes from saved offset`() {
        assertEquals(
            42,
            ttsResumeStartAt(isEpub = false, isMarkdown = false, ttsResumeChapter = 3, chapterIndex = 0, ttsResumeOffset = 42),
        )
    }

    @Test
    fun `epub resumes only when saved chapter matches current`() {
        assertEquals(
            42,
            ttsResumeStartAt(isEpub = true, isMarkdown = false, ttsResumeChapter = 2, chapterIndex = 2, ttsResumeOffset = 42),
        )
        assertEquals(
            0,
            ttsResumeStartAt(isEpub = true, isMarkdown = false, ttsResumeChapter = 2, chapterIndex = 5, ttsResumeOffset = 42),
        )
    }

    @Test
    fun `markdown resumes only when saved chapter matches current`() {
        assertEquals(
            42,
            ttsResumeStartAt(isEpub = false, isMarkdown = true, ttsResumeChapter = 2, chapterIndex = 2, ttsResumeOffset = 42),
        )
        assertEquals(
            0,
            ttsResumeStartAt(isEpub = false, isMarkdown = true, ttsResumeChapter = 2, chapterIndex = 5, ttsResumeOffset = 42),
        )
    }

    // ── 跟读偏移映射（TTS 句偏移 → 全书偏移）─────────────────────

    @Test
    fun `markdown paged follow maps chapter-local sentence to global offset`() {
        val chapterStartOffsets = listOf(0, 1000, 2000)
        assertEquals(
            1234,
            ttsFollowGlobalOffset(
                isTxt = false, isMarkdown = true, isEpub = false, pagerEngineOn = true,
                plainContent = "", txtStreamingDocument = null, visiblePlainOffset = 0,
                sentenceStart = 234, chapterStartOffsets = chapterStartOffsets, chapterIndex = 1,
            ),
        )
    }

    @Test
    fun `markdown scroll has no per-sentence follow target`() {
        assertNull(
            ttsFollowGlobalOffset(
                isTxt = false, isMarkdown = true, isEpub = false, pagerEngineOn = false,
                plainContent = "", txtStreamingDocument = null, visiblePlainOffset = 0,
                sentenceStart = 234, chapterStartOffsets = listOf(0), chapterIndex = 0,
            ),
        )
    }

    @Test
    fun `plain txt follow stays at sentence offset in full text`() {
        assertEquals(
            234,
            ttsFollowGlobalOffset(
                isTxt = true, isMarkdown = false, isEpub = false, pagerEngineOn = false,
                plainContent = "x".repeat(1000), txtStreamingDocument = null, visiblePlainOffset = 0,
                sentenceStart = 234, chapterStartOffsets = emptyList(), chapterIndex = 0,
            ),
        )
    }

    @Test
    fun `streaming txt follow uses visible window base`() {
        val streaming = PlainTextDocument("x".repeat(10_000))
        assertEquals(
            5_100,
            ttsFollowGlobalOffset(
                isTxt = true, isMarkdown = false, isEpub = false, pagerEngineOn = false,
                plainContent = "", txtStreamingDocument = streaming, visiblePlainOffset = 5_000,
                sentenceStart = 100, chapterStartOffsets = emptyList(), chapterIndex = 0,
            ),
        )
        assertNull(
            ttsFollowGlobalOffset(
                isTxt = true, isMarkdown = false, isEpub = false, pagerEngineOn = false,
                plainContent = "", txtStreamingDocument = streaming, visiblePlainOffset = 9_500,
                sentenceStart = 1_000, chapterStartOffsets = emptyList(), chapterIndex = 0,
            ),
        )
    }

    @Test
    fun `epub paged follow keeps chapter base plus sentence start`() {
        assertEquals(
            2_010,
            ttsFollowGlobalOffset(
                isTxt = false, isMarkdown = false, isEpub = true, pagerEngineOn = true,
                plainContent = "", txtStreamingDocument = null, visiblePlainOffset = 0,
                sentenceStart = 10, chapterStartOffsets = listOf(0, 2000), chapterIndex = 1,
            ),
        )
    }
}
