package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderFormatLabelTest {

    @Test
    fun `markdown reader is labeled MD instead of TXT`() {
        assertEquals("MD", readerFormatLabel(isEpub = false, isMarkdown = true))
    }

    @Test
    fun `epub and plain text labels remain stable`() {
        assertEquals("EPUB", readerFormatLabel(isEpub = true, isMarkdown = false))
        assertEquals("TXT", readerFormatLabel(isEpub = false, isMarkdown = false))
    }
}
