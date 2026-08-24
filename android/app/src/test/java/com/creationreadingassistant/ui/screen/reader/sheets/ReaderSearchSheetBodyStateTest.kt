package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.ui.screen.reader.BookSearchPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSearchSheetBodyStateTest {

    @Test
    fun `blank query shows prompt instead of an empty result list`() {
        assertEquals(
            SearchSheetBodyState.PROMPT,
            searchSheetBodyState("", BookSearchPhase.IDLE, resultCount = 0),
        )
    }

    @Test
    fun `searching and cancelled are not reported as no results`() {
        assertEquals(
            SearchSheetBodyState.SEARCHING,
            searchSheetBodyState("测试", BookSearchPhase.SEARCHING, resultCount = 0),
        )
        assertEquals(
            SearchSheetBodyState.CANCELLED,
            searchSheetBodyState("测试", BookSearchPhase.CANCELLED, resultCount = 0),
        )
    }

    @Test
    fun `completed search distinguishes zero results from a populated list`() {
        assertEquals(
            SearchSheetBodyState.EMPTY_RESULTS,
            searchSheetBodyState("测试", BookSearchPhase.COMPLETED, resultCount = 0),
        )
        assertEquals(
            SearchSheetBodyState.RESULTS,
            searchSheetBodyState("测试", BookSearchPhase.COMPLETED, resultCount = 3),
        )
    }
}
