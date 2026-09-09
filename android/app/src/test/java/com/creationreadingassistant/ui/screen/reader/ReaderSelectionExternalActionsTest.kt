package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionExternalActionsTest {

    @Test
    fun `browser query is trimmed collapsed bounded and encoded`() {
        val input = "  Kotlin\n  Compose  " + "字".repeat(600)

        val normalized = normalizedSelectionQuery(input, 500)
        val url = selectionBrowserUrl(input)

        assertEquals(500, normalized.length)
        assertTrue(normalized.startsWith("Kotlin Compose"))
        assertTrue(url!!.startsWith("https://www.bing.com/search?q=Kotlin+Compose"))
    }

    @Test
    fun `dictionary lookup uses a shorter encoded query`() {
        val url = selectionDictionaryUrl("  一期一会 / once  ")

        assertEquals(
            "https://dict.youdao.com/result?word=%E4%B8%80%E6%9C%9F%E4%B8%80%E4%BC%9A+%2F+once&lang=auto",
            url,
        )
    }

    @Test
    fun `blank selection produces no external url`() {
        assertNull(selectionBrowserUrl(" \n "))
        assertNull(selectionDictionaryUrl(" \t "))
    }
}
