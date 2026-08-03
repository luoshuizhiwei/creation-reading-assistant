package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.ui.screen.shelf.ShelfSortMode
import com.creationreadingassistant.ui.screen.shelf.ShelfStatusFilter
import com.creationreadingassistant.ui.screen.shelf.ShelfViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfSessionStateTest {

    @Test
    fun `clear filters preserves long lived sort and view preferences`() {
        val cleared = ShelfSessionState(
            viewMode = ShelfViewMode.LIST,
            sortMode = ShelfSortMode.TITLE,
            statusFilter = ShelfStatusFilter.READING,
            selectedShelfId = "shelf-1",
            selectedCategoryId = "category-1",
            selectedTagIds = setOf("tag-1", "tag-2"),
        ).clearFilters()

        assertEquals(ShelfViewMode.LIST, cleared.viewMode)
        assertEquals(ShelfSortMode.TITLE, cleared.sortMode)
        assertEquals(ShelfStatusFilter.ALL, cleared.statusFilter)
        assertEquals("", cleared.selectedShelfId)
        assertEquals("", cleared.selectedCategoryId)
        assertTrue(cleared.selectedTagIds.isEmpty())
    }

    @Test
    fun `safe stop keeps current item and counts only the remaining queue`() {
        assertEquals(3, remainingImportCount(total = 5, currentIndex = 2, currentCompleted = false))
        assertEquals(2, remainingImportCount(total = 5, currentIndex = 2, currentCompleted = true))
        assertEquals(0, remainingImportCount(total = 1, currentIndex = 0, currentCompleted = true))
    }

    @Test
    fun `stop request only changes a running batch`() {
        assertTrue(ImportBatchUiState(isRunning = true).markStopRequested().stopRequested)
        assertFalse(ImportBatchUiState(isRunning = false).markStopRequested().stopRequested)
    }
}
