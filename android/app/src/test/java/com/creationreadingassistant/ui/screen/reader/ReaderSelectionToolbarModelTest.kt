package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReaderSelectionToolbarModelTest {

    @Test
    fun `primary row contains exactly four frequent actions`() {
        assertEquals(
            listOf("高亮", "记为灵感", "复制", "更多"),
            selectionPrimaryActions.map { it.label },
        )
        // 产品规划无独立笔记概念：笔记动作不再出现在选中工具条
        assertFalse(selectionPrimaryActions.any { it.label == "笔记" })
    }

    @Test
    fun `secondary actions live in more menu with explicit cancel wording`() {
        assertEquals(
            listOf("AI 解读", "搜索", "取消选择"),
            selectionMoreActions.map { it.label },
        )
        assertFalse(selectionMoreActions.any { it.label == "清除" })
    }
}
