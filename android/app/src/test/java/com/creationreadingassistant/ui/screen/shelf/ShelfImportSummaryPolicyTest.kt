package com.creationreadingassistant.ui.screen.shelf

import com.creationreadingassistant.ui.viewmodel.ImportBatchUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfImportSummaryPolicyTest {
    @Test
    fun `completed summary reports duplicate files`() {
        val batch = ImportBatchUiState(succeeded = 0, duplicates = 1, failed = 0)

        assertEquals(
            "导入完成：成功 0 本，重复 1 本，失败 0 本",
            importBatchSnackbarMessage(batch),
        )
    }

    @Test
    fun `stopped summary reports completed duplicate and remaining counts`() {
        val batch = ImportBatchUiState(succeeded = 2, duplicates = 1, stopped = 3)

        assertEquals(
            "导入已停止：成功 2 本，重复 1 本，未处理 3 本",
            importBatchSnackbarMessage(batch),
        )
    }
}
