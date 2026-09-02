package com.creationreadingassistant.ui.screen.reader.sheets

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderProgressSheetPolicyTest {

    @Test
    fun `progress sheet keeps the requested book percent instead of mapping it to a chapter`() {
        assertEquals(63.5f, progressSheetTargetPercent(63.5f))
    }

    @Test
    fun `progress sheet clamps an out of range book percent`() {
        assertEquals(0f, progressSheetTargetPercent(-1f))
        assertEquals(100f, progressSheetTargetPercent(101f))
    }
}
