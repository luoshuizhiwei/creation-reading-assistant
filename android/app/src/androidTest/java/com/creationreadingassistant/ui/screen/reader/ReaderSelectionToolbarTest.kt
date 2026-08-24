package com.creationreadingassistant.ui.screen.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.theme.paperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderSelectionToolbarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setToolbar(
        showColorRow: Boolean = false,
        onPickColor: (String) -> Unit = {},
        onAiExplain: () -> Unit = {},
        onInspiration: () -> Unit = {},
        onNote: () -> Unit = {},
        onCopy: () -> Unit = {},
        onClear: () -> Unit = {},
        onSearch: () -> Unit = {},
    ) {
        composeRule.setContent {
            AppTheme {
                SelectionToolbar(
                    paper = paperPalette("white", darkTheme = false),
                    selectedText = "一段用于验证紧凑选区工具栏的正文",
                    showColorRow = showColorRow,
                    onToggleColor = {},
                    onPickColor = onPickColor,
                    onAiExplain = onAiExplain,
                    onInspiration = onInspiration,
                    onNote = onNote,
                    onCopy = onCopy,
                    onClear = onClear,
                    onSearch = onSearch,
                )
            }
        }
    }

    @Test
    fun primaryActions_areCompactAndSecondaryActionsStayInMoreMenu() {
        var noteClicked = false
        var copyClicked = false
        var searchClicked = false
        var clearClicked = false
        setToolbar(
            onNote = { noteClicked = true },
            onCopy = { copyClicked = true },
            onSearch = { searchClicked = true },
            onClear = { clearClicked = true },
        )

        composeRule.onNodeWithText("高亮").assertIsDisplayed()
        composeRule.onNodeWithText("笔记").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("复制").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("更多").assertIsDisplayed()
        composeRule.onNodeWithText("AI 解读").assertDoesNotExist()
        composeRule.onNodeWithText("记为灵感").assertDoesNotExist()
        composeRule.onNodeWithText("搜索").assertDoesNotExist()
        composeRule.onNodeWithText("取消选择").assertDoesNotExist()
        assertTrue(noteClicked)
        assertTrue(copyClicked)

        composeRule.onNodeWithText("更多").performClick()
        composeRule.onNodeWithText("AI 解读").assertIsDisplayed()
        composeRule.onNodeWithText("记为灵感").assertIsDisplayed()
        composeRule.onNodeWithText("搜索").assertIsDisplayed().performClick()
        assertTrue(searchClicked)

        composeRule.onNodeWithText("更多").performClick()
        composeRule.onNodeWithText("取消选择").assertIsDisplayed().performClick()
        assertTrue(clearClicked)
    }

    @Test
    fun colorPicker_isASecondaryStateWithNamedTouchTargets() {
        var picked = ""
        setToolbar(showColorRow = true, onPickColor = { picked = it })

        composeRule.onNodeWithText("选择高亮颜色").assertIsDisplayed()
        composeRule.onNodeWithText("返回").assertIsDisplayed()
        composeRule.onNodeWithText("更多").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("高亮颜色 yellow").assertIsDisplayed().performClick()

        assertEquals("yellow", picked)
    }
}
