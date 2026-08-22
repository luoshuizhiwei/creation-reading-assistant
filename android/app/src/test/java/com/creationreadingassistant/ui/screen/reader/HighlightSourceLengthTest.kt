package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightSourceLengthTest {

    @Test
    fun `projected selection stores source length and legacy payload falls back to text length`() {
        val payload = buildHighlightPayload(sourceLength = 12)

        assertEquals(12, highlightSourceLength(payload, fallbackTextLength = 5))
        assertEquals(5, highlightSourceLength("{}", fallbackTextLength = 5))
        assertEquals(5, highlightSourceLength("not-json", fallbackTextLength = 5))
        assertEquals("{}", buildHighlightPayload(sourceLength = null))
    }
}
