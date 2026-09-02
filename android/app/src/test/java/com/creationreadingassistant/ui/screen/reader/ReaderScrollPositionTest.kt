package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScrollPositionTest {

    @Test
    fun `startup list position is not persisted before initial restoration completes`() {
        assertFalse(canPersistPlainScrollPosition(plainScrollPositionRestored = false))
        assertTrue(canPersistPlainScrollPosition(plainScrollPositionRestored = true))
        assertFalse(canPersistPagedPosition(initialPositionPending = true))
        assertTrue(canPersistPagedPosition(initialPositionPending = false))
    }

    @Test
    fun `immediate save reads the current lazy list unit instead of an old composed offset`() {
        val state = LazyListState(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0)
        val units = listOf(
            ReadingUnit(0, 0, "", 0, 100),
            ReadingUnit(1, 0, "", 100, 100),
        )

        assertEquals(100, currentPlainListOffset(state, units, fallbackOffset = 0))
    }

    @Test
    fun `short document at end reports the full document instead of zero`() {
        assertEquals(
            12,
            visibleScrollOffset(
                firstVisibleItemIndex = 0,
                firstVisibleItemOffset = 0,
                firstVisibleItemSize = 2400,
                itemStartOffsets = listOf(0),
                itemLengths = listOf(12),
                endReached = true,
                endOffset = 12,
            ),
        )
        assertEquals(100f, scrollProgressPercent(12, 12, endReached = true), 0.001f)
    }

    @Test
    fun `partially visible item preserves its source coordinate`() {
        assertEquals(
            125,
            visibleScrollOffset(
                firstVisibleItemIndex = 1,
                firstVisibleItemOffset = -50,
                firstVisibleItemSize = 200,
                itemStartOffsets = listOf(0, 100),
                itemLengths = listOf(100, 100),
                endReached = false,
                endOffset = 0,
            ),
        )
        assertEquals(25f, chapterProgressPercent(125, 100, 100, endReached = false), 0.001f)
    }

    @Test
    fun `chapter navigation supports markdown and txt chapter lists`() {
        val first = readerChapterNavigation(currentIndex = 0, chapterCount = 3)
        val middle = readerChapterNavigation(currentIndex = 1, chapterCount = 3)
        val last = readerChapterNavigation(currentIndex = 2, chapterCount = 3)

        assertFalse(first.canGoPrevious)
        assertTrue(first.canGoNext)
        assertTrue(middle.canGoPrevious)
        assertTrue(middle.canGoNext)
        assertTrue(last.canGoPrevious)
        assertFalse(last.canGoNext)
    }
}
