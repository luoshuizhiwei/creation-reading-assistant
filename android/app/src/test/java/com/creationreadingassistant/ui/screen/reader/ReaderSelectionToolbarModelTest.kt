package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReaderSelectionToolbarModelTest {

    @Test
    fun `primary row contains exactly four frequent actions`() {
        assertEquals(
            listOf("高亮", "浏览器", "复制", "更多"),
            selectionPrimaryActions.map { it.label },
        )
        assertFalse(selectionPrimaryActions.any { it.label == "添加批注" })
    }

    @Test
    fun `secondary actions live in more menu with explicit cancel wording`() {
        assertEquals(
            listOf("字典", "添加批注", "替换", "书内搜索", "AI 解读", "记为灵感", "取消选择"),
            selectionMoreActions.map { it.label },
        )
        assertFalse(selectionMoreActions.any { it.label == "清除" })
    }
}
