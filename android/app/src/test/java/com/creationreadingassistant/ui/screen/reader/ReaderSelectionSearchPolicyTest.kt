package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.ui.viewmodel.ReaderAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSelectionSearchPolicyTest {

    @Test
    fun `selection search snapshots query before clearing selection and opening the sheet`() {
        assertEquals(
            listOf(
                ReaderAction.SetSearchQuery("待搜索的选区"),
                ReaderAction.ClearSelection,
                ReaderAction.OpenSheet(ReaderSheet.SEARCH),
            ),
            readerSelectionSearchActions("待搜索的选区"),
        )
    }
}
