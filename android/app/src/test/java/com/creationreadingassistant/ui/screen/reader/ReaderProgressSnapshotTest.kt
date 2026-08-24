package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.mutableIntStateOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderProgressSnapshotTest {

    @Test
    fun `progress snapshot reads the latest mutable state when persistence runs`() {
        val pagedOffset = mutableIntStateOf(0)
        val chapterIndex = mutableIntStateOf(0)

        pagedOffset.intValue = 325
        chapterIndex.intValue = 1

        assertEquals(
            ReaderProgressSnapshot(pagedAbsOffset = 325, chapterIndex = 1),
            currentReaderProgressSnapshot(pagedOffset, chapterIndex),
        )
    }
}
