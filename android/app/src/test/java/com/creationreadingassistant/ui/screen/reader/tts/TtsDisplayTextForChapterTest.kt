package com.creationreadingassistant.ui.screen.reader.tts

import com.creationreadingassistant.ui.screen.reader.ttsDisplayTextForChapter
import com.creationreadingassistant.ui.screen.reader.ttsDisplayTextReadyForChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsDisplayTextForChapterTest {

    @Test
    fun `returns fallback content when loaded pair is null`() {
        assertEquals("fallback", ttsDisplayTextForChapter(null, 0, "fallback"))
        assertTrue(ttsDisplayTextReadyForChapter(null, 0))
    }

    @Test
    fun `returns loaded text when chapter matches`() {
        val loaded = 2 to "Chapter 2 Display Text"
        assertEquals("Chapter 2 Display Text", ttsDisplayTextForChapter(loaded, 2, "fallback"))
        assertTrue(ttsDisplayTextReadyForChapter(loaded, 2))
    }

    @Test
    fun `returns fallback and reports not ready when chapter mismatches during transition`() {
        val loaded = 1 to "Chapter 1 Old Display Text"
        assertEquals("fallback", ttsDisplayTextForChapter(loaded, 2, "fallback"))
        assertFalse(ttsDisplayTextReadyForChapter(loaded, 2))
    }
}
