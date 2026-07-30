package com.creationreadingassistant.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderProgressRestoreTest {
    @Test
    fun `parses exact text offset from compatible progress json`() {
        assertEquals(123456, parseStoredAbsoluteOffset("""{"offset":123456}"""))
        assertEquals(42, parseStoredAbsoluteOffset("""{"chapter":3,"offset":42}"""))
    }

    @Test
    fun `invalid or missing progress falls back to start`() {
        assertEquals(0, parseStoredAbsoluteOffset(null))
        assertEquals(0, parseStoredAbsoluteOffset("{}"))
        assertEquals(0, parseStoredAbsoluteOffset("""{"offset":-1}"""))
    }
}
